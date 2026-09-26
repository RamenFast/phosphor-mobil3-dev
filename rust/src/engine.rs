//! Host-testable engine glue. No JNI, no Android — plain `cargo test` covers this.

use phosphor_dsp::{Computer, Mode};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};

/// One complete, finite stereo tap window, before display gain or DSP.
#[derive(Clone, Copy, Debug, PartialEq)]
#[cfg_attr(not(target_os = "android"), allow(dead_code))]
pub(crate) struct StereoPeak {
    pub left: f32,
    pub right: f32,
}

#[cfg_attr(not(target_os = "android"), allow(dead_code))]
impl StereoPeak {
    /// Compact malformed frames out of the same vector the DSP will consume.
    pub(crate) fn prepare(samples: &mut Vec<f32>) -> Option<Self> {
        let mut peak = Self {
            left: 0.0,
            right: 0.0,
        };
        let mut kept = 0;
        for frame in 0..samples.len() / 2 {
            let (left, right) = (samples[frame * 2], samples[frame * 2 + 1]);
            if !left.is_finite() || !right.is_finite() {
                continue;
            }
            peak.left = peak.left.max(left.abs());
            peak.right = peak.right.max(right.abs());
            samples[kept] = left;
            samples[kept + 1] = right;
            kept += 2;
        }
        samples.truncate(kept);
        (kept != 0).then_some(peak)
    }

    pub(crate) fn max(self) -> f32 {
        self.left.max(self.right)
    }

    pub(crate) fn json(self) -> serde_json::Value {
        let dbfs = |peak: f32| (peak > 0.0).then(|| 20.0 * peak.log10());
        serde_json::json!({
            "left": self.left, "right": self.right,
            "left_dbfs": dbfs(self.left), "right_dbfs": dbfs(self.right),
        })
    }
}

/// The foreground 500ms stats read consumes this bounded window, not a second audio tap.
#[derive(Default)]
#[cfg_attr(not(target_os = "android"), allow(dead_code))]
pub(crate) struct StereoWindow {
    peak: Option<StereoPeak>,
    started_ms: u64,
    remote_boundary: Option<Arc<()>>,
    capture_owner: u64,
    pub(crate) local_owner: u64,
    pub(crate) admitted_frames: u64,
    pub(crate) rejected_epoch_frames: u64,
    pub(crate) consumed_frames: u64,
}

#[derive(Clone, Copy, serde::Serialize)]
pub(crate) struct SignalPathSnapshot {
    capture_owner: u64,
    local_owner: u64,
    admitted_stereo_frames: u64,
    rejected_epoch_stereo_frames: u64,
    consumed_stereo_frames: u64,
}

#[cfg_attr(not(target_os = "android"), allow(dead_code))]
impl StereoWindow {
    pub(crate) const fn new() -> Self {
        Self {
            peak: None,
            started_ms: 0,
            remote_boundary: None,
            capture_owner: 0,
            local_owner: 0,
            admitted_frames: 0,
            rejected_epoch_frames: 0,
            consumed_frames: 0,
        }
    }

    pub(crate) fn activate_capture(&mut self) -> u64 {
        static NEXT: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(1);
        self.capture_owner = NEXT
            .fetch_update(Ordering::Relaxed, Ordering::Relaxed, |id| id.checked_add(1))
            .unwrap_or(0);
        self.capture_owner
    }

    pub(crate) fn clear_visual_measurement(&mut self) {
        self.peak = None;
        self.started_ms = 0;
    }

    pub(crate) fn signal_json(&self) -> serde_json::Value {
        serde_json::to_value(self.signal_snapshot()).unwrap_or(serde_json::Value::Null)
    }

    pub(crate) fn signal_snapshot(&self) -> SignalPathSnapshot {
        SignalPathSnapshot {
            capture_owner: self.capture_owner,
            local_owner: self.local_owner,
            admitted_stereo_frames: self.admitted_frames,
            rejected_epoch_stereo_frames: self.rejected_epoch_frames,
            consumed_stereo_frames: self.consumed_frames,
        }
    }

    pub(crate) fn observe(&mut self, peak: Option<StereoPeak>, now_ms: u64) {
        let Some(peak) = peak else {
            return;
        };
        if now_ms.saturating_sub(self.started_ms) > 500 {
            self.peak = None;
        }
        self.peak = Some(match self.peak {
            Some(old) => StereoPeak {
                left: old.left.max(peak.left),
                right: old.right.max(peak.right),
            },
            None => {
                self.started_ms = now_ms;
                peak
            }
        });
    }

    pub(crate) fn take(&mut self, now_ms: u64) -> Option<StereoPeak> {
        self.peak
            .take()
            .filter(|_| now_ms.saturating_sub(self.started_ms) <= 500)
    }
}

/// The producer and source boundaries use this same lock order. A drained old batch
/// finishes publication before a boundary can clear it, never after that clear.
#[cfg_attr(not(target_os = "android"), allow(dead_code))]
pub(crate) fn with_stereo_window<T>(
    ring: &std::sync::Mutex<phosphor_audio::SampleRing>,
    meter: &std::sync::Mutex<StereoWindow>,
    work: impl FnOnce(&mut phosphor_audio::SampleRing, &mut StereoWindow) -> T,
) -> T {
    let mut ring = ring.lock().unwrap();
    let mut meter = meter.lock().unwrap();
    work(&mut ring, &mut meter)
}

/// Read ordering only. The epoch belongs to the producer before its blocking read.
#[cfg_attr(not(target_os = "android"), allow(dead_code))]
pub(crate) fn publish_capture_read(
    ring: &Mutex<phosphor_audio::SampleRing>,
    meter: &Mutex<StereoWindow>,
    active: &AtomicBool,
    epoch: &std::sync::atomic::AtomicU64,
    owner: u64,
    read_epoch: u64,
    samples: &[f32],
) -> bool {
    with_stereo_window(ring, meter, |ring, meter| {
        if owner == 0 || meter.capture_owner != owner || !active.load(Ordering::Relaxed) {
            return false;
        }
        if epoch.load(Ordering::Acquire) != read_epoch {
            meter.rejected_epoch_frames = meter
                .rejected_epoch_frames
                .saturating_add((samples.len() / 2) as u64);
            return false;
        }
        ring.push_interleaved(samples);
        meter.admitted_frames = meter
            .admitted_frames
            .saturating_add((samples.len() / 2) as u64);
        true
    })
}

#[cfg(test)]
mod capture_fence_tests {
    use super::*;
    use std::sync::{atomic::AtomicU64, mpsc};
    use std::time::Duration;

    fn held_read(replace_owner: bool) {
        let ring = Arc::new(Mutex::new(phosphor_audio::SampleRing::new(48_000)));
        let meter = Arc::new(Mutex::new(StereoWindow::new()));
        let active = Arc::new(AtomicBool::new(true));
        let epoch = Arc::new(AtomicU64::new(4));
        let owner = with_stereo_window(&ring, &meter, |_, meter| meter.activate_capture());
        let (began_tx, began_rx) = mpsc::sync_channel(1);
        let (finish_tx, finish_rx) = mpsc::sync_channel(1);
        let wait = Duration::from_secs(2);
        let producer = {
            let (ring, meter, active, epoch) =
                (ring.clone(), meter.clone(), active.clone(), epoch.clone());
            std::thread::spawn(move || {
                let read_epoch = epoch.load(Ordering::Acquire);
                began_tx.send(read_epoch).unwrap();
                finish_rx.recv_timeout(wait).unwrap();
                publish_capture_read(
                    &ring,
                    &meter,
                    &active,
                    &epoch,
                    owner,
                    read_epoch,
                    &[0.9, -0.9],
                )
            })
        };
        assert_eq!(began_rx.recv_timeout(wait).unwrap(), 4);
        let current_owner = with_stereo_window(&ring, &meter, |ring, meter| {
            ring.clear_pending();
            if replace_owner {
                // Deliberately leave the epoch unchanged to prove independent ownership.
                *meter = StereoWindow::new();
                meter.activate_capture()
            } else {
                epoch.fetch_add(1, Ordering::AcqRel);
                meter.clear_visual_measurement();
                owner
            }
        });
        finish_tx.send(()).unwrap();
        assert!(!producer.join().unwrap());
        assert!(ring.lock().unwrap().take_stereo_samples().is_empty());
        assert!(publish_capture_read(
            &ring,
            &meter,
            &active,
            &epoch,
            current_owner,
            epoch.load(Ordering::Acquire),
            &[0.25, -0.5]
        ));
        assert_eq!(ring.lock().unwrap().take_stereo_samples(), vec![0.25, -0.5]);
    }

    #[test]
    fn held_epoch4_read_cannot_publish_after_resume_epoch5() {
        held_read(false);
    }

    #[test]
    fn held_read_cannot_publish_to_replacement_even_at_same_epoch() {
        held_read(true);
    }

    #[test]
    fn current_epoch_cannot_resurrect_inactive_or_non_capture_source() {
        let ring = Mutex::new(phosphor_audio::SampleRing::new(48_000));
        let meter = Mutex::new(StereoWindow::new());
        let active = AtomicBool::new(false);
        let epoch = AtomicU64::new(5);
        let owner = with_stereo_window(&ring, &meter, |_, meter| meter.activate_capture());
        assert!(!publish_capture_read(
            &ring,
            &meter,
            &active,
            &epoch,
            owner,
            5,
            &[1.0, 1.0]
        ));
        assert!(!active.load(Ordering::Relaxed));
        with_stereo_window(&ring, &meter, |_, meter| {
            *meter = StereoWindow::new();
            active.store(true, Ordering::Relaxed);
        });
        for candidate in [0, owner] {
            assert!(!publish_capture_read(
                &ring,
                &meter,
                &active,
                &epoch,
                candidate,
                5,
                &[1.0, 1.0]
            ));
        }
        assert!(ring.lock().unwrap().take_stereo_samples().is_empty());
    }

    #[test]
    fn publication_waits_for_ring_lock_then_rechecks_epoch() {
        let ring = Arc::new(Mutex::new(phosphor_audio::SampleRing::new(48_000)));
        let meter = Arc::new(Mutex::new(StereoWindow::new()));
        let active = Arc::new(AtomicBool::new(true));
        let epoch = Arc::new(AtomicU64::new(4));
        let owner = with_stereo_window(&ring, &meter, |_, meter| meter.activate_capture());
        let mut guard = ring.lock().unwrap();
        let (started_tx, started_rx) = mpsc::sync_channel(1);
        let producer = {
            let (ring, meter, active, epoch) =
                (ring.clone(), meter.clone(), active.clone(), epoch.clone());
            std::thread::spawn(move || {
                started_tx.send(()).unwrap();
                publish_capture_read(&ring, &meter, &active, &epoch, owner, 4, &[1.0, 1.0])
            })
        };
        started_rx.recv_timeout(Duration::from_secs(2)).unwrap();
        epoch.fetch_add(1, Ordering::AcqRel);
        guard.clear_pending();
        drop(guard);
        assert!(!producer.join().unwrap());
        assert!(ring.lock().unwrap().take_stereo_samples().is_empty());
    }
}

/// One remote attempt, distinct even when reconnect reuses the Link generation.
/// Generic source boundaries replace StereoWindow and invalidate both identities.
#[cfg_attr(not(target_os = "android"), allow(dead_code))]
pub(crate) struct RemoteScopeLease {
    before: Arc<()>,
    owner: Arc<()>,
}

#[cfg_attr(not(target_os = "android"), allow(dead_code))]
impl RemoteScopeLease {
    pub(crate) fn prepare(
        ring: &Mutex<phosphor_audio::SampleRing>,
        meter: &Mutex<StereoWindow>,
    ) -> Self {
        with_stereo_window(ring, meter, |_, meter| Self {
            before: meter
                .remote_boundary
                .get_or_insert_with(|| Arc::new(()))
                .clone(),
            owner: Arc::new(()),
        })
    }

    fn matches(meter: &StereoWindow, identity: &Arc<()>) -> bool {
        meter
            .remote_boundary
            .as_ref()
            .is_some_and(|id| Arc::ptr_eq(id, identity))
    }

    pub(crate) fn activate(
        &self,
        ring: &Mutex<phosphor_audio::SampleRing>,
        meter: &Mutex<StereoWindow>,
        active: &AtomicBool,
        live: impl FnOnce() -> bool,
    ) -> bool {
        with_stereo_window(ring, meter, |ring, meter| {
            if !live() || !Self::matches(meter, &self.before) {
                return false;
            }
            ring.clear_pending();
            *meter = StereoWindow::new();
            meter.remote_boundary = Some(self.owner.clone());
            active.store(true, Ordering::Relaxed);
            true
        })
    }

    pub(crate) fn ingest(
        &self,
        ring: &Mutex<phosphor_audio::SampleRing>,
        meter: &Mutex<StereoWindow>,
        samples: &[f32],
        live: impl FnOnce() -> bool,
    ) -> bool {
        with_stereo_window(ring, meter, |ring, meter| {
            if !live() || !Self::matches(meter, &self.owner) {
                return false;
            }
            ring.push_interleaved(samples);
            meter.admitted_frames = meter
                .admitted_frames
                .saturating_add((samples.len() / 2) as u64);
            true
        })
    }

    pub(crate) fn retire(
        &self,
        ring: &Mutex<phosphor_audio::SampleRing>,
        meter: &Mutex<StereoWindow>,
        active: &AtomicBool,
    ) -> bool {
        with_stereo_window(ring, meter, |ring, meter| {
            if !Self::matches(meter, &self.owner) {
                return false;
            }
            ring.clear_pending();
            *meter = StereoWindow::new();
            active.store(false, Ordering::Relaxed);
            true
        })
    }

    pub(crate) fn signal_json(&self, meter: &Mutex<StereoWindow>) -> Option<serde_json::Value> {
        let snapshot = {
            let meter = meter.try_lock().ok()?;
            Self::matches(&meter, &self.owner).then(|| meter.signal_snapshot())?
        };
        serde_json::to_value(snapshot).ok()
    }
}

/// Fixed metadata at the existing relay receive boundary, before mute and zero-fill.
#[derive(Default, Clone)]
#[cfg_attr(not(target_os = "android"), allow(dead_code))]
pub(crate) struct SignalAggregate {
    start: Option<u64>,
    measured: Option<u64>,
    positive: Option<u64>,
    frames: u64,
    valid: u64,
    invalid: u64,
    squares: [f64; 2],
    peaks: [f64; 2],
    rails: [u64; 2],
}

#[cfg_attr(not(target_os = "android"), allow(dead_code))]
impl SignalAggregate {
    pub(crate) fn observe(&mut self, samples: &[f32], now: u64) {
        self.observe_with_rail(samples, now, 1.0);
    }

    pub(crate) fn observe_wire_pcm16(&mut self, samples: &[f32], now: u64) {
        self.observe_with_rail(samples, now, 32767.0 / 32768.0);
    }

    fn observe_with_rail(&mut self, samples: &[f32], now: u64, positive_rail: f64) {
        if self
            .start
            .is_none_or(|start| now < start || now - start >= 500)
        {
            self.start = Some(now);
            self.measured = None;
            self.valid = 0;
            self.invalid = 0;
            self.squares = [0.0; 2];
            self.peaks = [0.0; 2];
            self.rails = [0; 2];
        }
        if !samples.is_empty() {
            self.positive = Some(now);
        }
        self.frames = self.frames.saturating_add((samples.len() / 2) as u64);
        self.invalid = self.invalid.saturating_add((samples.len() % 2) as u64);
        for frame in samples.chunks_exact(2) {
            if !frame.iter().all(|s| s.is_finite()) {
                self.invalid = self.invalid.saturating_add(2);
                continue;
            }
            self.valid = self.valid.saturating_add(1);
            self.measured = Some(now);
            for (channel, value) in frame.iter().enumerate() {
                let value = f64::from(*value);
                self.squares[channel] += value * value;
                self.peaks[channel] = self.peaks[channel].max(value.abs());
                if value >= positive_rail || value <= -1.0 {
                    self.rails[channel] = self.rails[channel].saturating_add(1);
                }
            }
        }
    }

    pub(crate) fn json(&self, now: u64) -> serde_json::Value {
        let age = |at: Option<u64>| at.and_then(|at| now.checked_sub(at));
        serde_json::json!({"input_stereo_frames": self.frames, "valid_frames": self.valid,
            "invalid_samples": self.invalid, "positive_age_ms": age(self.positive), "level_age_ms": age(self.measured),
            "channels": (self.valid > 0).then(|| (0..2).map(|c| serde_json::json!({
                "samples": self.valid, "rms": (self.squares[c] / self.valid as f64).sqrt(),
                "peak": self.peaks[c], "full_scale": self.rails[c]})).collect::<Vec<_>>())})
    }
}

/// Callback-only scalar counters. No allocation, clock read, lock, or JSON on the output thread.
#[derive(Default)]
#[cfg_attr(not(target_os = "android"), allow(dead_code))]
pub(crate) struct SignalOutput {
    sequence: std::sync::atomic::AtomicU64,
    popped: std::sync::atomic::AtomicU64,
    finalized: std::sync::atomic::AtomicU64,
    zero_filled: std::sync::atomic::AtomicU64,
}

#[cfg(test)]
mod signal_tests {
    use super::*;
    use std::sync::atomic::AtomicU64;

    #[test]
    fn wire_pcm16_decoder_and_meter_preserve_both_rails_without_counting_neighbors() {
        let media = crate::bridge_core::SessionMedia::default();
        let samples = [i16::MAX, i16::MIN, i16::MAX - 1, i16::MIN + 1, 0, 0];
        let payload: Vec<u8> = samples.into_iter().flat_map(i16::to_le_bytes).collect();
        let crate::bridge_core::RemoteMedia::Audio(decoded) =
            media.accept(b'A', &payload, 100).unwrap()
        else {
            panic!("Valid stereo PCM16 must decode as audio");
        };
        assert!(decoded[0] < 1.0);
        assert_eq!(decoded[1], -1.0);
        let mut meter = SignalAggregate::default();
        meter.observe_wire_pcm16(&decoded, 100);
        let value = meter.json(100);
        assert_eq!(value["valid_frames"], 3);
        assert_eq!(value["channels"][0]["full_scale"], 1);
        assert_eq!(value["channels"][1]["full_scale"], 1);
        assert_eq!(value["channels"][0]["peak"], f64::from(i16::MAX) / 32768.0);
        assert_eq!(value["channels"][1]["peak"], 1.0);
    }

    #[test]
    fn signal_window_silence_invalid_and_full_scale_are_distinct() {
        let mut meter = SignalAggregate::default();
        assert!(meter.json(0)["channels"].is_null());
        meter.observe(&[0.0, 0.0], 0);
        assert_eq!(meter.json(0)["channels"][0]["rms"], 0.0);
        meter.observe(&[1.0, -1.0, f32::NAN, 0.0, 0.4], 10);
        let value = meter.json(20);
        assert_eq!(value["valid_frames"], 2);
        assert_eq!(value["invalid_samples"], 3);
        assert_eq!(value["channels"][0]["full_scale"], 1);
        assert_eq!(value["positive_age_ms"], 10);
        meter.observe(&[f32::INFINITY, 0.0], 500);
        assert!(meter.json(500)["channels"].is_null());
        assert_eq!(meter.json(500)["input_stereo_frames"], 4);
    }

    #[test]
    fn signal_window_finite_unequal_channels_and_staleness() {
        let mut meter = SignalAggregate::default();
        meter.observe(&[0.5, 0.25, -0.5, -0.25], 100);
        let first = meter.json(101);
        assert_eq!(first["channels"][0]["rms"], 0.5);
        assert_eq!(first["channels"][1]["peak"], 0.25);
        assert_eq!(meter.json(5000)["level_age_ms"], 4900);
        assert_eq!(meter.json(101), first); // reads do not reset
        assert!(meter.json(99)["level_age_ms"].is_null());
    }

    #[test]
    fn signal_capture_epoch_rejection_preserves_input_owner_not_admission() {
        let ring = Mutex::new(phosphor_audio::SampleRing::new(48_000));
        let meter = Mutex::new(StereoWindow::new());
        let active = AtomicBool::new(true);
        let epoch = AtomicU64::new(2);
        let owner = meter.lock().unwrap().activate_capture();
        assert!(!publish_capture_read(
            &ring,
            &meter,
            &active,
            &epoch,
            owner,
            1,
            &[1.0, 1.0]
        ));
        assert!(publish_capture_read(
            &ring,
            &meter,
            &active,
            &epoch,
            owner,
            2,
            &[0.0, 0.0]
        ));
        let first = meter.lock().unwrap().signal_json();
        assert_eq!(first["admitted_stereo_frames"], 1);
        assert_eq!(first["rejected_epoch_stereo_frames"], 1);
        assert_eq!(first["consumed_stereo_frames"], 0);
        meter.lock().unwrap().clear_visual_measurement();
        assert_eq!(meter.lock().unwrap().signal_json(), first);
        *meter.lock().unwrap() = StereoWindow::new();
        let next = meter.lock().unwrap().activate_capture();
        assert_ne!(owner, next);
        assert!(!publish_capture_read(
            &ring,
            &meter,
            &active,
            &epoch,
            owner,
            2,
            &[1.0, 1.0]
        ));
        assert_eq!(
            meter.lock().unwrap().signal_json()["admitted_stereo_frames"],
            0
        );
    }

    #[test]
    fn signal_output_counts_real_pops_separately_from_synthetic_zeros() {
        let output = SignalOutput::default();
        output.observe(0, 100);
        output.observe(80, 100);
        let value = output.json();
        assert_eq!(value["popped_stereo_frames"], 80);
        assert_eq!(value["finalized_stereo_frames"], 200);
        assert_eq!(value["zero_filled_stereo_frames"], 120);
        assert_eq!(output.json(), value);
    }
}

#[cfg_attr(not(target_os = "android"), allow(dead_code))]
impl SignalOutput {
    pub(crate) fn observe(&self, popped: usize, finalized: usize) {
        self.sequence.fetch_add(1, Ordering::AcqRel);
        for (counter, count) in [
            (&self.popped, popped),
            (&self.finalized, finalized),
            (&self.zero_filled, finalized.saturating_sub(popped)),
        ] {
            let _ = counter.fetch_update(Ordering::Relaxed, Ordering::Relaxed, |n| {
                Some(n.saturating_add(count as u64))
            });
        }
        self.sequence.fetch_add(1, Ordering::Release);
    }
    pub(crate) fn json(&self) -> serde_json::Value {
        let before = self.sequence.load(Ordering::Acquire);
        if before & 1 != 0 {
            return serde_json::Value::Null;
        }
        let popped = self.popped.load(Ordering::Relaxed);
        let finalized = self.finalized.load(Ordering::Relaxed);
        let zero_filled = self.zero_filled.load(Ordering::Relaxed);
        if before != self.sequence.load(Ordering::Acquire) {
            return serde_json::Value::Null;
        }
        serde_json::json!({"popped_stereo_frames": popped, "finalized_stereo_frames": finalized,
            "zero_filled_stereo_frames": zero_filled})
    }
}

#[cfg_attr(not(target_os = "android"), allow(dead_code))]
pub(crate) fn grid_angle(mode: Mode, remote_geometry: bool, remote_mode: Option<Mode>) -> f32 {
    let actual = if remote_geometry {
        remote_mode
    } else {
        Some(mode)
    };
    if actual == Some(Mode::Xy45) {
        std::f32::consts::FRAC_PI_4
    } else {
        0.0
    }
}

const AUTO_GAIN_MIN: f32 = 0.1;
/// One size scale for AUTO and manual gain. Manual, archives and presets share this ceiling.
pub(crate) const GAIN_MAX: f32 = 64.0;
const AUTO_GAIN_MAX: f32 = GAIN_MAX;
const AUTO_FRAME_MIN: f32 = 0.1;
const AUTO_FRAME_MAX: f32 = 20.0;
const AUTO_FRAME_FILL: f32 = 0.80;
/// Level is the 80th percentile of recent batch peaks, so clicks and taps do not resize the view.
const LEVEL_WINDOW_SECONDS: f32 = 0.25;
const LEVEL_PERCENTILE: f32 = 0.80;
const LEVEL_RELEASE_SECONDS: f32 = 1.0;
/// Gain glides both ways: quick enough to stop sustained clipping, gentle when growing.
const GAIN_ATTACK_SECONDS: f32 = 0.08;
/// When the trace is several times too large, shrink faster so sustained loud sound settles quickly.
const GAIN_FAST_ATTACK_SECONDS: f32 = 0.03;
const FAST_ATTACK_RATIO: f32 = 3.0;
const GAIN_RELEASE_SECONDS: f32 = 0.6;
/// Below this batch RMS the input is digital silence and AUTO holds its size.
const SILENCE_RMS: f32 = 0.000_01;
const MAX_UPDATE_SECONDS: f32 = 0.1;

#[derive(Clone, Copy, Debug)]
struct FrameActivity {
    peak: f32,
    rms: f32,
}

impl FrameActivity {
    fn measure(samples: &[f32]) -> Option<Self> {
        if samples.len() < 2
            || !samples.len().is_multiple_of(2)
            || samples.iter().any(|v| !v.is_finite())
        {
            return None;
        }
        let mut peak = 0.0_f32;
        let mut energy = 0.0_f64;
        for sample in samples {
            peak = peak.max(sample.abs());
            energy += (*sample as f64) * (*sample as f64);
        }
        let rms = (energy / samples.len() as f64).sqrt() as f32;
        Some(Self { peak, rms })
    }
}

/// Mobile-local automatic visual framing. It observes raw samples but never rewrites PCM.
#[cfg_attr(not(target_os = "android"), allow(dead_code))]
pub(crate) struct AutoGain {
    enabled: bool,
    level: f32,
    history: std::collections::VecDeque<(f32, f32)>,
    history_seconds: f32,
    effective: f32,
    frame_scale: f32,
    view_scale: f32,
    pending_seconds: f32,
    reset_open: u64,
}

#[cfg_attr(not(target_os = "android"), allow(dead_code))]
impl AutoGain {
    pub(crate) fn new(manual_gain: f32) -> Self {
        Self {
            enabled: false,
            level: 0.0,
            history: std::collections::VecDeque::new(),
            history_seconds: 0.0,
            effective: clamp_gain(manual_gain),
            frame_scale: 1.0,
            view_scale: 1.0,
            pending_seconds: 0.0,
            reset_open: 0,
        }
    }

    pub(crate) fn rebase_time(&mut self) {
        self.pending_seconds = 0.0;
    }

    /// Turning AUTO off keeps the current size; the caller adopts the returned gain as manual.
    pub(crate) fn set_auto(&mut self, on: bool, manual_gain: f32) -> f32 {
        self.rebase_time();
        if !on && !self.enabled {
            self.effective = clamp_gain(manual_gain);
        }
        self.enabled = on;
        self.effective
    }

    pub(crate) fn set_manual(&mut self, gain: f32) -> f32 {
        self.rebase_time();
        self.enabled = false;
        self.effective = clamp_gain(gain);
        self.effective
    }

    pub(crate) fn set_frame_scale(&mut self, scale: f32) -> f32 {
        self.frame_scale = if scale.is_finite() {
            scale.clamp(AUTO_FRAME_MIN, AUTO_FRAME_MAX)
        } else {
            1.0
        };
        self.frame_scale
    }

    pub(crate) fn set_mode(&mut self, mode: Mode) {
        self.view_scale = if matches!(mode, Mode::Xy45 | Mode::XySwirl) {
            std::f32::consts::FRAC_1_SQRT_2
        } else {
            1.0
        };
    }

    pub(crate) fn enabled(&self) -> bool {
        self.enabled
    }

    pub(crate) fn new_local_item(&mut self, proven_open: u64, current_open: u64) {
        if proven_open != 0 && proven_open == current_open && proven_open != self.reset_open {
            self.level = 0.0;
            self.history.clear();
            self.history_seconds = 0.0;
            self.reset_open = proven_open;
        }
    }

    fn percentile_level(&self) -> f32 {
        let mut entries: Vec<(f32, f32)> = self.history.iter().copied().collect();
        entries.sort_by(|a, b| a.0.total_cmp(&b.0));
        let wanted = self.history_seconds * LEVEL_PERCENTILE;
        let mut covered = 0.0;
        for (peak, seconds) in &entries {
            covered += seconds;
            if covered >= wanted {
                return *peak;
            }
        }
        entries.last().map_or(0.0, |entry| entry.0)
    }

    pub(crate) fn update(&mut self, samples: &[f32], elapsed_seconds: f32) -> Option<f32> {
        if !self.enabled {
            return None;
        }
        let dt = if elapsed_seconds.is_finite() {
            elapsed_seconds.clamp(0.0, MAX_UPDATE_SECONDS)
        } else {
            0.0
        };
        // Empty presentation drains keep their time for the next actual input batch.
        self.pending_seconds = (self.pending_seconds + dt).min(MAX_UPDATE_SECONDS);
        let Some(frame) = FrameActivity::measure(samples) else {
            return Some(self.effective);
        };
        let seconds = std::mem::take(&mut self.pending_seconds);
        if seconds <= 0.0 {
            return Some(self.effective);
        }
        self.history.push_back((frame.peak, seconds));
        self.history_seconds += seconds;
        while self.history.len() > 1
            && self.history_seconds - self.history[0].1 >= LEVEL_WINDOW_SECONDS
        {
            let (_, old) = self.history.pop_front().unwrap_or((0.0, 0.0));
            self.history_seconds -= old;
        }
        let current = self.percentile_level();
        let release = (-seconds / LEVEL_RELEASE_SECONDS).exp();
        self.level = current.max(self.level * release);
        if frame.rms < SILENCE_RMS || self.level <= 0.0 {
            return Some(self.effective);
        }
        let fill = AUTO_FRAME_FILL * self.frame_scale * self.view_scale;
        let ceiling = AUTO_GAIN_MAX * self.frame_scale.max(1.0);
        let target = (fill / self.level).clamp(AUTO_GAIN_MIN, ceiling);
        let tau = if target * FAST_ATTACK_RATIO < self.effective {
            GAIN_FAST_ATTACK_SECONDS
        } else if target < self.effective {
            GAIN_ATTACK_SECONDS
        } else {
            GAIN_RELEASE_SECONDS
        };
        self.effective += (target - self.effective) * (1.0 - (-seconds / tau).exp());
        self.effective = self.effective.clamp(AUTO_GAIN_MIN, ceiling);
        Some(self.effective)
    }
}

pub(crate) fn clamp_gain(gain: f32) -> f32 {
    if gain.is_finite() {
        gain.clamp(AUTO_GAIN_MIN, GAIN_MAX)
    } else {
        1.0
    }
}

/// Proof the engine crates link and run: synthesize one frame of a Lissajous
/// figure, run it through the real DSP, report what came out.
pub fn engine_info() -> String {
    let mut computer = Computer::new();
    computer.set_sample_rate(48_000, 1);
    computer.mode = Mode::Xy;

    // 1/60 s of a 3:2 Lissajous at 220 Hz — stereo interleaved, like the scope feed.
    let frames = 800usize;
    let mut samples = Vec::with_capacity(frames * 2);
    for n in 0..frames {
        let t = n as f32 / 48_000.0;
        samples.push((std::f32::consts::TAU * 220.0 * 3.0 * t).sin() * 0.8);
        samples.push((std::f32::consts::TAU * 220.0 * 2.0 * t).sin() * 0.8);
    }
    let segments = computer.compute(&samples, 1080.0, 1080.0).len();

    serde_json::json!({
        "engine": "phosphor",
        "mode": computer.mode.name(),
        "segments": segments,
        "beam_presets": phosphor_beam::THEME_PRESETS.len(),
    })
    .to_string()
}

/// Host-testable synthetic scope feed: an evolving, phase-continuous Lissajous figure
/// in the same sample domain as a real track.
pub struct Feeder {
    t0: std::time::Instant,
    last: f64,
    phase_x: f64,
    phase_y: f64,
}

/// Configure the desktop DSP's real polyphase reconstruction rate. The input feed stays
/// 48 kHz; `factor` adds samples inside `Computer` instead of splitting one captured window
/// into several independently decayed renderer advances (the "2-3 circles" defect).
#[cfg_attr(not(target_os = "android"), allow(dead_code))]
pub(crate) fn set_reconstruction_rate(computer: &mut Computer, factor: u32) -> u32 {
    let factor = factor.clamp(1, 8);
    computer.set_sample_rate(48_000, factor);
    factor
}

/// One display frame consumes one contiguous tap window and produces one beam deposit.
/// Keeping this host-testable guards the Android render loop's sample-window contract.
#[cfg_attr(not(target_os = "android"), allow(dead_code))]
pub(crate) fn compute_scope_frame(
    computer: &mut Computer,
    samples: &[f32],
    width: f32,
    height: f32,
) -> Vec<[f32; 5]> {
    computer.compute(samples, width, height).to_vec()
}

/// Final deposit transform used by the Android renderer's existing tube envelopes.
/// Geometry changes preserve segment energy. Only the startup cathode envelope scales it.
#[cfg_attr(not(target_os = "android"), allow(dead_code))]
pub(crate) fn map_deposit_segment(
    segment: &[f32; 5],
    width: f32,
    height: f32,
    scale_xy: f32,
    scale_y: f32,
    brightness: f32,
) -> [f32; 5] {
    let cx = width * 0.5;
    let cy = height * 0.5;
    [
        cx + (segment[0] - cx) * scale_xy,
        cy + (segment[1] - cy) * scale_xy * scale_y,
        cx + (segment[2] - cx) * scale_xy,
        cy + (segment[3] - cy) * scale_xy * scale_y,
        segment[4] * brightness,
    ]
}

/// Geometry FX stage: a 2D transform on this frame's beam segments, applied AFTER dsp
/// compute and BEFORE the view-rotation quarter-turn remap + deposit, so it composes
/// with every mode. `kind`: 0 off · 1 kaleido · 2 spin · 3 tunnel · 4 pulse.
/// `amount` 0..1 is depth; `phase` is the render loop's accumulated spin/twist clock
/// (radians); `env` is the fast-attack audio envelope. Phone-local by design — remote
/// desktop frames and the resting dot never pass through here.
#[cfg_attr(not(target_os = "android"), allow(dead_code))]
pub(crate) fn apply_geom_fx(
    segs: &[[f32; 5]],
    w: f32,
    h: f32,
    kind: u8,
    amount: f32,
    phase: f32,
    env: f32,
) -> Vec<[f32; 5]> {
    let a = amount.clamp(0.0, 1.0);
    if kind == 0 || a <= 0.0 || segs.is_empty() {
        return segs.to_vec();
    }
    let (cx, cy) = (w * 0.5, h * 0.5);
    let s = 0.5 * w.min(h);
    let e = env.clamp(0.0, 1.25);
    let norm = |x: f32, y: f32| ((x - cx) / s, (y - cy) / s);
    let denorm = |u: f32, v: f32| (u * s + cx, v * s + cy);
    let rot = |u: f32, v: f32, c: f32, sn: f32| (u * c - v * sn, u * sn + v * c);

    match kind {
        1 => {
            // KALEIDO: dihedral replication — n rotated copies plus their mirror family,
            // intensity scaled to near-conserve total deposited light.
            let n = 2 + (a * 3.999) as usize; // 2..5 sectors
            let mirror = segs.len() * 2 * n <= 24_000; // perf cap: drop mirrors, keep rotations
            let copies = if mirror { 2 * n } else { n };
            let gain = (copies as f32).powf(-0.6);
            let mut out = Vec::with_capacity(segs.len() * copies);
            for k in 0..n {
                let alpha = std::f32::consts::TAU * k as f32 / n as f32;
                let (c, sn) = (alpha.cos(), alpha.sin());
                for seg in segs {
                    let (u1, v1) = norm(seg[0], seg[1]);
                    let (u2, v2) = norm(seg[2], seg[3]);
                    let i = seg[4] * gain;
                    let (ru1, rv1) = rot(u1, v1, c, sn);
                    let (ru2, rv2) = rot(u2, v2, c, sn);
                    let (x1, y1) = denorm(ru1, rv1);
                    let (x2, y2) = denorm(ru2, rv2);
                    out.push([x1, y1, x2, y2, i]);
                    if mirror {
                        let (mu1, mv1) = rot(u1, -v1, c, sn);
                        let (mu2, mv2) = rot(u2, -v2, c, sn);
                        let (x1, y1) = denorm(mu1, mv1);
                        let (x2, y2) = denorm(mu2, mv2);
                        out.push([x1, y1, x2, y2, i]);
                    }
                }
            }
            out
        }
        2 => {
            // SPIN: rotate the whole figure by the accumulated phase (the loop integrates
            // an audio-reactive rate into `phase`; the transform itself is pure).
            let (c, sn) = (phase.cos(), phase.sin());
            segs.iter()
                .map(|seg| {
                    let (u1, v1) = norm(seg[0], seg[1]);
                    let (u2, v2) = norm(seg[2], seg[3]);
                    let (ru1, rv1) = rot(u1, v1, c, sn);
                    let (ru2, rv2) = rot(u2, v2, c, sn);
                    let (x1, y1) = denorm(ru1, rv1);
                    let (x2, y2) = denorm(ru2, rv2);
                    [x1, y1, x2, y2, seg[4]]
                })
                .collect()
        }
        3 => {
            // TUNNEL: polar warp with the unit circle as fixed point — the center pinches
            // out to a ring mouth, plus a center-weighted breathing twist.
            let breathe = (0.6 * phase).sin();
            segs.iter()
                .map(|seg| {
                    let mut pts = [(seg[0], seg[1]), (seg[2], seg[3])];
                    for p in &mut pts {
                        let (u, v) = norm(p.0, p.1);
                        let r = (u * u + v * v).sqrt();
                        let theta = v.atan2(u);
                        let r2 = (1.0 - a) * r + a * (0.30 + 0.70 * r * r);
                        let t2 = theta + a * (1.0 - r.min(1.0)) * breathe;
                        *p = denorm(r2 * t2.cos(), r2 * t2.sin());
                    }
                    [pts[0].0, pts[0].1, pts[1].0, pts[1].1, seg[4]]
                })
                .collect()
        }
        _ => {
            // PULSE: audio-pumped zoom — instant attack rides the envelope's release.
            let z = 1.0 + 0.6 * a * e;
            segs.iter()
                .map(|seg| {
                    let (u1, v1) = norm(seg[0], seg[1]);
                    let (u2, v2) = norm(seg[2], seg[3]);
                    let (x1, y1) = denorm(u1 * z, v1 * z);
                    let (x2, y2) = denorm(u2 * z, v2 * z);
                    [x1, y1, x2, y2, seg[4]]
                })
                .collect()
        }
    }
}

impl Feeder {
    pub fn new() -> Feeder {
        Feeder {
            t0: std::time::Instant::now(),
            last: 0.0,
            phase_x: 0.0,
            phase_y: 0.0,
        }
    }
    pub fn reset(&mut self) {
        self.t0 = std::time::Instant::now();
        self.last = 0.0;
    }
    /// Interleaved stereo at 48 kHz covering the wall-clock time since the last call.
    pub fn frame_samples(&mut self) -> Vec<f32> {
        let now = self.t0.elapsed().as_secs_f64();
        let dt = (now - self.last).clamp(0.0, 0.05);
        self.last = now;
        let n = ((dt * 48_000.0) as usize).clamp(64, 4096);

        // A slowly morphing ratio walks the figure through the Lissajous family.
        let base = 220.0 * std::f64::consts::TAU / 48_000.0;
        let ratio = 1.5 + 0.5 * (now * 0.11).sin();
        let mut out = Vec::with_capacity(n * 2);
        for _ in 0..n {
            self.phase_x += base * ratio;
            self.phase_y += base;
            out.push((self.phase_x.sin() * 0.75) as f32);
            out.push((self.phase_y.sin() * 0.75) as f32);
        }
        out
    }
}

impl Default for Feeder {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use phosphor_audio::SampleRing;
    use phosphor_dsp::{Computer, Mode};

    #[test]
    fn render_deposit_energy_stays_exact_across_geometry_and_surface_sizes() {
        // Exercise real DSP segments through the renderer's final deposit transform.
        let mut computer = Computer::new();
        super::set_reconstruction_rate(&mut computer, 4);
        computer.mode = Mode::Xy;
        computer.beam_energy = 13.5;
        let samples: Vec<f32> = (0..480)
            .flat_map(|n| {
                let phase = std::f32::consts::TAU * 440.0 * n as f32 / 48_000.0;
                [phase.sin() * 0.7, phase.cos() * 0.7]
            })
            .collect();
        let segments = super::compute_scope_frame(&mut computer, &samples, 1080.0, 1920.0);
        assert!(!segments.is_empty());
        assert!(segments.iter().any(|s| s[4] > 0.0));
        for (width, height) in [(1080.0, 1920.0), (1920.0, 1080.0), (480.0, 270.0)] {
            for step in 0..=100 {
                let scale = step as f32 / 100.0;
                for segment in &segments {
                    let mapped =
                        super::map_deposit_segment(segment, width, height, 1.0, scale, 1.0);
                    assert_eq!(mapped[4].to_bits(), segment[4].to_bits());
                }
            }
        }
        assert_eq!(computer.beam_energy, 13.5);
    }

    #[test]
    fn render_deposit_retains_cathode_envelope_and_nondefault_energy() {
        let segment = [10.0, 20.0, 30.0, 40.0, 2.75];
        for step in 0..=100 {
            let brightness = step as f32 / 100.0;
            let mapped = super::map_deposit_segment(&segment, 100.0, 200.0, 0.5, 0.25, brightness);
            assert_eq!(&mapped[..4], &[30.0, 90.0, 40.0, 92.5]);
            assert_eq!(mapped[4], 2.75 * brightness);
        }
        assert_eq!(
            super::map_deposit_segment(&segment, 100.0, 200.0, 1.0, 1.0, 1.0),
            segment
        );
    }

    /// A pure 440 Hz quadrature circle, produced in 10 ms capture chunks and drained at
    /// 120 Hz, must stay phase-contiguous across window boundaries and reconstruct as one
    /// deposit per display frame. Renderer-side substeps once created overlapping circles
    /// and froze decay on empty ticks.
    #[test]
    fn pure_circle_render_path_reconstructs_at_selected_rate() {
        const RATE: usize = 48_000;
        const HZ: f64 = 440.0;
        const OVERSAMPLE: usize = 4;
        let total_frames = RATE / 2;
        let mut source = Vec::with_capacity(total_frames * 2);
        for n in 0..total_frames {
            let phase = std::f64::consts::TAU * HZ * n as f64 / RATE as f64;
            source.push(phase.sin() as f32);
            source.push(phase.cos() as f32);
        }

        let mut ring = SampleRing::new(RATE as u32);
        let mut computer = Computer::new();
        super::set_reconstruction_rate(&mut computer, OVERSAMPLE as u32);
        computer.mode = Mode::Xy;
        let mut pushed_frames = 0usize;
        let mut last_phase: Option<f64> = None;
        let expected_step = std::f64::consts::TAU * HZ / RATE as f64;
        let mut windows = Vec::new();
        let mut display_advances = 0usize;
        let mut segment_counts = Vec::new();

        // 100 Hz AudioRecord chunks drained by a 120 Hz display loop.
        for display_tick in 1..=60 {
            let producer_due = display_tick * RATE / 120;
            while pushed_frames + RATE / 100 <= producer_due {
                let next = pushed_frames + RATE / 100;
                ring.push_interleaved(&source[pushed_frames * 2..next * 2]);
                pushed_frames = next;
            }
            let samples = ring.take_stereo_samples();
            windows.push(samples.len() / 2);

            for pair in samples.chunks_exact(2) {
                let phase = (pair[0] as f64).atan2(pair[1] as f64);
                if let Some(previous) = last_phase {
                    let delta = (phase - previous).rem_euclid(std::f64::consts::TAU);
                    assert!(
                        (delta - expected_step).abs() < 2.0e-6,
                        "phase discontinuity: got {delta}, expected {expected_step}"
                    );
                }
                last_phase = Some(phase);
            }

            let segments = super::compute_scope_frame(&mut computer, &samples, 1000.0, 1000.0);
            display_advances += 1;
            segment_counts.push(segments.len());
        }

        let nonempty = windows.iter().filter(|&&n| n > 0).count();
        let first_nonempty_segments = segment_counts.iter().copied().find(|&n| n > 0).unwrap();
        println!(
            "phase_discontinuities=0 nonempty_windows={nonempty} pushed_frames={pushed_frames} \
             display_advances={display_advances} first_10ms_segments={first_nonempty_segments}",
        );
        assert_eq!(
            display_advances, 60,
            "active display cadence must never freeze on an empty tap"
        );
        assert_eq!(
            first_nonempty_segments, 1_919,
            "4x must reconstruct the contiguous 480-frame window to 1,920 points",
        );
    }

    #[test]
    fn engine_info_reports_segments() {
        let info = super::engine_info();
        let v: serde_json::Value = serde_json::from_str(&info).unwrap();
        assert_eq!(v["engine"], "phosphor");
        assert!(
            v["segments"].as_u64().unwrap() > 0,
            "DSP produced no segments: {info}"
        );
    }

    fn stereo_tone(peak: f32) -> Vec<f32> {
        (0..480)
            .flat_map(|n| {
                let phase = std::f32::consts::TAU * 440.0 * n as f32 / 48_000.0;
                [phase.sin() * peak, phase.cos() * peak]
            })
            .collect()
    }

    fn stationary_noise(peak: f32) -> Vec<f32> {
        let mut state = 0x1234_5678_u32;
        (0..960)
            .map(|_| {
                state = state.wrapping_mul(1_664_525).wrapping_add(1_013_904_223);
                ((state as f64 / u32::MAX as f64) as f32 * 2.0 - 1.0) * peak
            })
            .collect()
    }

    fn run_for(gain: &mut super::AutoGain, samples: &[f32], seconds: f32, hz: u32) -> f32 {
        for _ in 0..(seconds * hz as f32).round() as usize {
            gain.update(samples, 1.0 / hz as f32);
        }
        gain.effective
    }

    #[test]
    fn auto_size_quiet_sound_grows_smoothly_at_any_frame_rate() {
        for peak in [0.002, 0.005, 0.02] {
            let tone = stereo_tone(peak);
            let mut results = Vec::new();
            for hz in [60, 90, 120] {
                let mut gain = super::AutoGain::new(1.0);
                gain.set_auto(true, 1.0);
                results.push(run_for(&mut gain, &tone, 3.0, hz));
            }
            let expected = (super::AUTO_FRAME_FILL / peak).min(super::AUTO_GAIN_MAX);
            for value in &results {
                assert!(*value > expected * 0.9, "peak={peak} {results:?}");
            }
            let spread = results.iter().copied().fold(f32::MIN, f32::max)
                - results.iter().copied().fold(f32::MAX, f32::min);
            assert!(
                spread < expected * 0.02,
                "peak={peak} rate spread {results:?}"
            );
        }
    }

    #[test]
    fn auto_size_keeps_time_across_empty_render_drains() {
        let tone = stereo_tone(0.02);
        let mut results = Vec::new();
        for hz in [60, 90, 120, 240, 1_000] {
            let mut gain = super::AutoGain::new(1.0);
            gain.set_auto(true, 1.0);
            let mut published = 0;
            for frame in 1..=(2 * hz) {
                let batches = frame * 100 / hz;
                let input = if batches > published { &tone[..] } else { &[] };
                gain.update(input, 1.0 / hz as f32);
                published = batches;
            }
            results.push(gain.effective);
        }
        let spread = results.iter().copied().fold(f32::MIN, f32::max)
            - results.iter().copied().fold(f32::MAX, f32::min);
        assert!(
            spread < 1.0,
            "empty-drain cadence spread={spread} {results:?}"
        );
        assert!(results.iter().all(|value| *value > 30.0), "{results:?}");
    }

    #[test]
    fn auto_size_holds_through_silence_and_invalid_input() {
        let mut gain = super::AutoGain::new(1.0);
        gain.set_auto(true, 1.0);
        let settled = run_for(&mut gain, &stereo_tone(0.05), 3.0, 120);
        for samples in [
            Vec::new(),
            vec![f32::NAN, 0.0, 0.0, 0.0, 0.0, 0.0],
            vec![0.0; 960],
        ] {
            let held = run_for(&mut gain, &samples, 10.0, 120);
            assert!(
                (held - settled).abs() < 0.01,
                "held={held} settled={settled}"
            );
        }
    }

    /// Ben's field report: the view collapsed for about half a second with no input change.
    #[test]
    fn auto_size_ignores_clicks_and_short_transients() {
        let quiet = stereo_tone(0.01);
        let mut gain = super::AutoGain::new(1.0);
        gain.set_auto(true, 1.0);
        let settled = run_for(&mut gain, &quiet, 4.0, 100);
        let mut lowest = settled;
        for step in 0..200 {
            let mut batch = quiet.clone();
            if step % 50 == 10 {
                batch[200] = 0.9; // a tap on the phone body
                batch[201] = -0.7;
            }
            lowest = lowest.min(gain.update(&batch, 0.01).unwrap());
        }
        assert!(
            lowest > settled * 0.8,
            "click shrank view {settled} -> {lowest}"
        );
    }

    #[test]
    fn auto_size_stays_steady_through_speech_like_sound() {
        // 4 Hz syllables with pauses and colored room noise, like a voice near the mic.
        let mut state = 7_u32;
        let mut noise = 0.0_f32;
        let mut gain = super::AutoGain::new(1.0);
        gain.set_auto(true, 1.0);
        let mut values = Vec::new();
        for step in 0..1_000 {
            let t = step as f32 * 0.01;
            let envelope = ((t * 4.0 * std::f32::consts::TAU).sin().max(0.0)).powf(0.5);
            let batch: Vec<f32> = (0..480)
                .flat_map(|n| {
                    state = state.wrapping_mul(1_664_525).wrapping_add(1_013_904_223);
                    let white = (state as f32 / u32::MAX as f32) * 2.0 - 1.0;
                    noise = noise * 0.95 + white * 0.05;
                    let phase = std::f32::consts::TAU * 180.0 * (step * 480 + n) as f32 / 48_000.0;
                    let voice = phase.sin() * 0.03 * envelope;
                    [voice + noise * 0.002, voice * 0.9 + noise * 0.002]
                })
                .collect();
            let value = gain.update(&batch, 0.01).unwrap();
            if step >= 300 {
                values.push(value);
            }
        }
        let mean = values.iter().sum::<f32>() / values.len() as f32;
        let min = values.iter().copied().fold(f32::MAX, f32::min);
        let max = values.iter().copied().fold(f32::MIN, f32::max);
        assert!(
            min > mean * 0.75 && max < mean * 1.25,
            "pumping {min} {mean} {max}"
        );
    }

    #[test]
    fn auto_size_settles_a_loud_entrance_and_recovers_quiet() {
        let quiet = stereo_tone(0.005);
        let loud = stereo_tone(0.8);
        let fill = super::AUTO_FRAME_FILL;
        let mut gain = super::AutoGain::new(1.0);
        gain.set_auto(true, 1.0);
        run_for(&mut gain, &quiet, 4.0, 120);
        let after_quarter = run_for(&mut gain, &loud, 0.35, 120);
        assert!(
            after_quarter * 0.8 < fill * 1.3,
            "loud still oversized: {after_quarter}"
        );
        let settled = run_for(&mut gain, &loud, 1.0, 120);
        assert!(
            (settled * 0.8 - fill).abs() < 0.05,
            "loud settled {settled}"
        );
        // From very loud to a whisper (160x quieter) the view regrows visibly, then fully in ~6 s.
        let two_seconds = run_for(&mut gain, &quiet, 2.0, 120);
        let five_seconds = run_for(&mut gain, &quiet, 4.0, 120);
        assert!(two_seconds > 3.0, "quiet recovery too slow: {two_seconds}");
        assert!(
            five_seconds > 50.0,
            "quiet recovery incomplete: {five_seconds}"
        );
        let mut previous = five_seconds;
        run_for(&mut gain, &loud, 0.02, 120);
        for _ in 0..60 {
            let next = gain.update(&loud, 1.0 / 120.0).unwrap();
            assert!(
                next <= previous * 1.01 && next > previous * 0.5,
                "no instant collapse: {previous} -> {next}"
            );
            previous = next;
        }
    }

    #[test]
    fn auto_size_is_bounded_by_the_shared_gain_scale() {
        let mut gain = super::AutoGain::new(1.0);
        gain.set_auto(true, 1.0);
        for count in 1..32 {
            gain.update(&vec![0.000_02; count * 2], 1.0 / 120.0);
        }
        let noise = stationary_noise(0.0005);
        let value = run_for(&mut gain, &noise, 10.0, 120);
        assert!(value <= super::AUTO_GAIN_MAX, "{value}");
        let mut small = super::AutoGain::new(1.0);
        small.set_auto(true, 1.0);
        run_for(&mut small, &stereo_tone(0.5), 2.0, 120);
        run_for(&mut small, &[], 10.0, 120);
        let before = small.effective;
        let after = small.update(&stereo_tone(0.001), 1.0 / 120.0).unwrap();
        assert!(
            after < before * 1.2,
            "starvation cannot be borrowed into one jump: {before} {after}"
        );
        gain.set_frame_scale(4.0);
        assert!(run_for(&mut gain, &stereo_tone(0.0005), 10.0, 120) > super::AUTO_GAIN_MAX);
    }

    #[test]
    fn auto_size_quiet_sound_enlarges_actual_xy_and_waveform_geometry() {
        fn extent(segments: &[[f32; 5]], axis: usize) -> f32 {
            let coordinates: Vec<f32> = segments
                .iter()
                .flat_map(|s| [s[axis], s[axis + 2]])
                .collect();
            coordinates.iter().copied().fold(f32::MIN, f32::max)
                - coordinates.iter().copied().fold(f32::MAX, f32::min)
        }
        let quiet = stereo_tone(0.01);
        let loud = stereo_tone(0.8);
        for scale in [0.25, 1.0] {
            let mut gain = super::AutoGain::new(1.0);
            gain.set_frame_scale(scale);
            gain.set_auto(true, 1.0);
            let effective = run_for(&mut gain, &quiet, 4.0, 120);
            for mode in [Mode::Xy, Mode::Waveform] {
                let mut computer = Computer::new();
                computer.mode = mode;
                computer.gain = 1.0;
                let small = super::compute_scope_frame(&mut computer, &quiet, 1080.0, 1920.0);
                computer.gain = effective;
                let large = super::compute_scope_frame(&mut computer, &quiet, 1080.0, 1920.0);
                let axis = if mode == Mode::Xy { 0 } else { 1 };
                assert!(extent(&large, axis) > extent(&small, axis) + 90.0);
            }
            let settled = run_for(&mut gain, &loud, 1.5, 120);
            let mut computer = Computer::new();
            computer.mode = Mode::Xy;
            computer.gain = settled;
            let framed = super::compute_scope_frame(&mut computer, &loud, 1080.0, 1920.0);
            assert!(
                framed
                    .iter()
                    .all(|s| s[0] >= 0.0 && s[0] <= 1080.0 && s[2] >= 0.0 && s[2] <= 1080.0)
            );
        }
    }

    #[test]
    fn auto_size_rotated_xy_keeps_channel_corners_inside_with_margin() {
        let samples: Vec<f32> = (0..480)
            .flat_map(|n| {
                let left = if n % 4 < 2 { 0.02 } else { -0.02 };
                let right = if n % 2 == 0 { 0.02 } else { -0.02 };
                [left, right]
            })
            .collect();
        for mode in [Mode::Xy45, Mode::XySwirl] {
            for factor in [1, 2, 4, 8] {
                let mut gain = super::AutoGain::new(1.0);
                gain.set_mode(mode);
                gain.set_auto(true, 1.0);
                run_for(&mut gain, &stereo_tone(0.02), 6.0, 120);
                let mut computer = Computer::new();
                computer.mode = mode;
                super::set_reconstruction_rate(&mut computer, factor);
                computer.gain = gain.effective;
                for _ in 0..20 {
                    let segments =
                        super::compute_scope_frame(&mut computer, &samples, 1080.0, 1920.0);
                    assert!(
                        segments.iter().all(|s| s[0] >= 65.0
                            && s[0] <= 1015.0
                            && s[2] >= 65.0
                            && s[2] <= 1015.0),
                        "mode={} factor={factor} lost rotation margin",
                        mode.name()
                    );
                }
            }
        }
    }

    #[test]
    fn auto_size_new_item_forgets_level_without_jumping() {
        let loud = stereo_tone(0.8);
        let mut gain = super::AutoGain::new(1.0);
        gain.set_auto(true, 1.0);
        run_for(&mut gain, &loud, 1.0, 60);
        let effective = gain.effective;
        for (proof, current) in [(0, 0), (1, 2), (1, 0)] {
            gain.new_local_item(proof, current);
            assert!(gain.level > 0.5);
        }
        gain.new_local_item(2, 2);
        assert_eq!(gain.level, 0.0);
        assert_eq!(gain.effective, effective);
        run_for(&mut gain, &loud, 0.1, 60);
        gain.new_local_item(2, 2);
        assert!(gain.level > 0.5, "same item cannot reset twice");
        gain.new_local_item(3, 3);
        assert_eq!(gain.level, 0.0);
    }

    #[test]
    fn auto_frame_scale_has_exact_bounds_and_changes_target_fill() {
        let tone = stereo_tone(0.05);
        for (requested, expected) in [
            (f32::NAN, 1.0),
            (0.0, super::AUTO_FRAME_MIN),
            (0.25, 0.25),
            (1.0, 1.0),
            (2.0, 2.0),
            (8.0, 8.0),
            (99.0, super::AUTO_FRAME_MAX),
        ] {
            let mut gain = super::AutoGain::new(1.0);
            assert_eq!(gain.set_frame_scale(requested), expected);
            gain.set_auto(true, 1.0);
            let settled = run_for(&mut gain, &tone, 8.0, 120);
            let expected_gain = (super::AUTO_FRAME_FILL * expected / 0.05).clamp(
                super::AUTO_GAIN_MIN,
                super::AUTO_GAIN_MAX * expected.max(1.0),
            );
            assert!(
                (settled - expected_gain).abs() < expected_gain * 0.02,
                "requested={requested} settled={settled} expected={expected_gain}"
            );
        }
    }

    #[test]
    fn leaving_auto_keeps_size_and_manual_gain_lands_exactly() {
        let mut ag = super::AutoGain::new(1.0);
        ag.set_auto(true, 1.0);
        let auto_size = run_for(&mut ag, &stereo_tone(0.02), 4.0, 120);
        assert!(auto_size > 30.0);
        assert_eq!(
            ag.set_auto(false, 1.0),
            auto_size,
            "AUTO off keeps the current size"
        );
        assert!(!ag.enabled());
        assert_eq!(ag.update(&stereo_tone(0.01), 1.0 / 60.0), None);
        assert_eq!(ag.set_manual(2.4), 2.4);
        assert_eq!(ag.set_manual(500.0), super::GAIN_MAX);
        assert_eq!(
            ag.set_auto(false, 7.0),
            7.0,
            "already manual: adopt the manual value"
        );
    }

    #[test]
    fn stereo_peak_raw_channels_dbfs_and_one_shared_tap() {
        for (input, expected) in [
            (vec![0.0, 0.0, -0.5, 0.0], (0.5, 0.0)),
            (vec![0.0, -0.25, 0.0, 0.125], (0.0, 0.25)),
            (vec![-1.0, 0.25, 0.5, -0.125], (1.0, 0.25)),
        ] {
            let mut ring = SampleRing::new(48_000);
            ring.push_interleaved(&input);
            let mut samples = ring.take_stereo_samples();
            let peak = super::StereoPeak::prepare(&mut samples).unwrap();
            assert_eq!((peak.left, peak.right), expected);
            assert!(ring.take_stereo_samples().is_empty());
            let raw = peak.json();
            for (key, magnitude) in [("left", peak.left), ("right", peak.right)] {
                assert_eq!(raw[key].as_f64().unwrap(), magnitude as f64);
                let db = &raw[format!("{key}_dbfs")];
                if magnitude == 0.0 {
                    assert!(db.is_null());
                } else {
                    assert!(
                        (db.as_f64().unwrap() - 20.0 * (magnitude as f64).log10()).abs() < 1e-5
                    );
                }
            }
            for gain in [0.1, 1.0, 7.0, 64.0, 256.0] {
                let mut computer = Computer::new();
                computer.gain = gain;
                super::compute_scope_frame(&mut computer, &samples, 1080.0, 1920.0);
                assert_eq!(peak.json(), raw);
            }
        }
        // Android source boundary: the renderer, not this host test, owns the production tap.
        let render = include_str!("render.rs");
        assert_eq!(render.matches("take_stereo_samples()").count(), 1);
        assert!(render.contains("StereoPeak::prepare(&mut samples)"));
        assert!(render.contains("auto_gain.update(&samples"));
        assert!(render.contains("compute_scope_frame(&mut computer, &samples"));
        assert!(!include_str!("jni_glue.rs").contains("take_stereo_samples"));
    }

    #[test]
    fn every_shared_dsp_mode_is_finite_and_count_bounded_at_auto_ceiling() {
        let samples = stereo_tone(0.005);
        for mode in Mode::ALL {
            let mut computer = Computer::new();
            computer.mode = mode;
            // The deepest zoom: AUTO's ceiling at the closest framing preference.
            computer.gain = super::AUTO_GAIN_MAX * super::AUTO_FRAME_MAX;
            // Spectrum modes need a full FFT window and at least two compute calls.
            for _ in 0..20 {
                super::compute_scope_frame(&mut computer, &samples, 1080.0, 1920.0);
            }
            let segments = super::compute_scope_frame(&mut computer, &samples, 1080.0, 1920.0);
            assert!(!segments.is_empty(), "{} produced no geometry", mode.name());
            assert!(
                segments.len() <= 200_000,
                "{} produced {} segments",
                mode.name(),
                segments.len()
            );
            assert!(
                segments.iter().flatten().all(|value| value.is_finite()),
                "{} produced nonfinite geometry",
                mode.name()
            );
        }
    }

    #[test]
    fn stereo_peak_silence_no_data_finite_pairs_and_odd_tail_are_distinct() {
        let mut ring = SampleRing::new(48_000);
        ring.push_interleaved(&[0.5]);
        assert_eq!(
            super::StereoPeak::prepare(&mut ring.take_stereo_samples()),
            None
        );
        ring.push_interleaved(&[-0.25]);
        let completed = super::StereoPeak::prepare(&mut ring.take_stereo_samples()).unwrap();
        assert_eq!((completed.left, completed.right), (0.5, 0.25));
        for mut samples in [
            vec![],
            vec![0.8],
            vec![f32::NAN, 0.0],
            vec![0.0, f32::INFINITY],
        ] {
            assert_eq!(super::StereoPeak::prepare(&mut samples), None);
            assert!(samples.is_empty());
        }
        let mut samples = vec![0.0, 0.0];
        let silence = super::StereoPeak::prepare(&mut samples).unwrap();
        assert_eq!(silence.max(), 0.0);
        assert_eq!(silence.json()["left"], 0.0);
        assert!(silence.json()["left_dbfs"].is_null());
        let mut samples = vec![f32::NAN, 1.0, -0.5, 0.25, 0.0, f32::NEG_INFINITY, 100.0];
        let peak = super::StereoPeak::prepare(&mut samples).unwrap();
        assert_eq!(samples, [-0.5, 0.25]);
        assert_eq!((peak.left, peak.right), (0.5, 0.25));
        let over = super::StereoPeak {
            left: 2.0,
            right: f32::MIN_POSITIVE,
        }
        .json();
        assert!(over["left_dbfs"].as_f64().unwrap() > 6.0);
        assert!(over["right_dbfs"].as_f64().unwrap().is_finite());
    }

    #[test]
    fn stereo_peak_window_keeps_empty_presentation_gaps_but_expires_and_consumes() {
        let mut meter = super::StereoWindow::new();
        assert_eq!(meter.take(0), None);
        let left = super::StereoPeak {
            left: 0.5,
            right: 0.0,
        };
        let right = super::StereoPeak {
            left: 0.0,
            right: 0.25,
        };
        meter.observe(Some(left), 10);
        for frame in 2..60 {
            meter.observe(None, frame * 8);
        }
        meter.observe(Some(right), 490);
        assert_eq!(
            meter.take(500),
            Some(super::StereoPeak {
                left: 0.5,
                right: 0.25
            })
        );
        assert_eq!(meter.take(501), None);
        let zero = super::StereoPeak {
            left: 0.0,
            right: 0.0,
        };
        meter.observe(Some(zero), 520);
        assert_eq!(meter.take(1020), Some(zero));
        meter.observe(Some(left), 1100);
        assert_eq!(meter.take(1601), None);
        meter.observe(Some(left), 1700);
        meter.observe(Some(right), 2201);
        assert_eq!(meter.take(2201), Some(right)); // Hidden HUD cannot collect a lifetime peak.
    }

    #[test]
    fn stereo_peak_source_boundary_clears_a_delayed_old_drained_batch() {
        use std::sync::{Arc, Mutex, mpsc};
        let ring = Arc::new(Mutex::new(SampleRing::new(48_000)));
        let meter = Arc::new(Mutex::new(super::StereoWindow::new()));
        ring.lock().unwrap().push_interleaved(&[0.75, 0.25]);
        let (drained_tx, drained_rx) = mpsc::channel();
        let (resume_tx, resume_rx) = mpsc::channel();
        let producer = {
            let (ring, meter) = (ring.clone(), meter.clone());
            std::thread::spawn(move || {
                super::with_stereo_window(&ring, &meter, |ring, meter| {
                    let mut samples = ring.take_stereo_samples();
                    let peak = super::StereoPeak::prepare(&mut samples);
                    drained_tx.send(()).unwrap();
                    resume_rx.recv().unwrap();
                    meter.observe(peak, 100);
                })
            })
        };
        drained_rx.recv().unwrap();
        let boundary = {
            let (ring, meter) = (ring.clone(), meter.clone());
            std::thread::spawn(move || {
                super::with_stereo_window(&ring, &meter, |ring, meter| {
                    ring.clear_pending();
                    *meter = super::StereoWindow::new();
                })
            })
        };
        resume_tx.send(()).unwrap();
        producer.join().unwrap();
        boundary.join().unwrap();
        assert_eq!(meter.lock().unwrap().take(101), None);
        // A replacement's measured zero must not inherit the retired loud peak.
        super::with_stereo_window(&ring, &meter, |ring, meter| {
            ring.push_interleaved(&[0.0, 0.0]);
            meter.observe(
                super::StereoPeak::prepare(&mut ring.take_stereo_samples()),
                110,
            );
        });
        assert_eq!(meter.lock().unwrap().take(120).unwrap().max(), 0.0);
        // Host helper coverage ends at these Android-owned adapters, checked as source strings.
        let render = include_str!("render.rs");
        let deck = include_str!("deck.rs");
        assert!(render.contains("= with_stereo_window(|ring, meter|"));
        assert!(deck.contains("crate::render::with_stereo_window(|ring, meter|"));
        assert!(deck.contains("*meter = crate::engine::StereoWindow::new()"));
        assert!(
            deck.split("pub fn close()")
                .nth(1)
                .unwrap()
                .contains("set_ring_state(false)")
        );
        assert!(include_str!("remote.rs").contains("self.scope.retire("));
    }

    fn delayed_remote_scope_ingest(adapter: &str) {
        use std::sync::{Arc, Mutex, mpsc};
        use std::time::Duration;
        let ring = Arc::new(Mutex::new(SampleRing::new(48_000)));
        let meter = Arc::new(Mutex::new(super::StereoWindow::new()));
        let active = super::AtomicBool::new(false);
        let cancel = Arc::new(super::AtomicBool::new(false));
        let lease = Arc::new(super::RemoteScopeLease::prepare(&ring, &meter));
        assert!(lease.activate(&ring, &meter, &active, || true));
        let (checked_tx, checked_rx) = mpsc::sync_channel(1);
        let (resume_tx, resume_rx) = mpsc::sync_channel(1);
        let (done_tx, done_rx) = mpsc::sync_channel(1);
        let wait = Duration::from_secs(5);
        let producer = {
            let (ring, meter, lease, cancel) =
                (ring.clone(), meter.clone(), lease.clone(), cancel.clone());
            std::thread::spawn(move || {
                assert!(!cancel.load(super::Ordering::SeqCst));
                checked_tx.send(()).unwrap();
                // This is upstream of ring acquisition, unlike the retained drain regression.
                resume_rx.recv_timeout(wait).unwrap();
                let accepted = lease.ingest(&ring, &meter, &[0.75, 0.25], || {
                    !cancel.load(super::Ordering::SeqCst)
                });
                done_tx.send(accepted).unwrap();
            })
        };
        checked_rx.recv_timeout(wait).unwrap();
        cancel.store(true, super::Ordering::SeqCst);
        assert!(lease.retire(&ring, &meter, &active));
        super::with_stereo_window(&ring, &meter, |ring, meter| {
            ring.clear_pending();
            *meter = super::StereoWindow::new();
            active.store(true, super::Ordering::Relaxed);
            ring.push_interleaved(&[0.0, 0.0]);
            meter.observe(
                super::StereoPeak::prepare(&mut ring.take_stereo_samples()),
                110,
            );
        });
        resume_tx.send(()).unwrap();
        assert!(!done_rx.recv_timeout(wait).unwrap());
        producer.join().unwrap(); // Completion is received before joining.
        super::with_stereo_window(&ring, &meter, |ring, meter| {
            let mut samples = ring.take_stereo_samples();
            assert!(samples.is_empty());
            meter.observe(super::StereoPeak::prepare(&mut samples), 120);
            assert_eq!(
                meter.take(120),
                Some(super::StereoPeak {
                    left: 0.0,
                    right: 0.0
                })
            );
        });
        assert!(active.load(super::Ordering::Relaxed));
        // Runtime proof above covers the production ingest guard. This only maps each adapter.
        assert!(include_str!("remote.rs").contains(adapter));
    }

    #[test]
    fn stereo_peak_remote_scope_worker_rejects_delayed_old_batch() {
        delayed_remote_scope_ingest("shared_v.push_scope(&buf[..got], epoch)");
    }

    #[test]
    fn stereo_peak_remote_reader_fallback_rejects_delayed_old_batch() {
        delayed_remote_scope_ingest("shared.push_scope(&f32buf, visual_epoch)");
    }

    #[test]
    fn stereo_peak_remote_retirement_clears_pending_and_measured_without_expiry() {
        let ring = super::Mutex::new(SampleRing::new(48_000));
        let meter = super::Mutex::new(super::StereoWindow::new());
        let active = super::AtomicBool::new(false);
        let lease = super::RemoteScopeLease::prepare(&ring, &meter);
        assert!(lease.activate(&ring, &meter, &active, || true));
        assert!(lease.ingest(&ring, &meter, &[0.75, 0.25], || true));
        super::with_stereo_window(&ring, &meter, |ring, meter| {
            meter.observe(
                super::StereoPeak::prepare(&mut ring.take_stereo_samples()),
                100,
            );
        });
        assert!(lease.ingest(&ring, &meter, &[0.5, 0.125], || true));
        assert!(lease.retire(&ring, &meter, &active));
        assert!(!active.load(super::Ordering::Relaxed));
        assert!(ring.lock().unwrap().take_stereo_samples().is_empty());
        assert_eq!(meter.lock().unwrap().take(101), None); // 1ms, not the 501ms expiry.
        assert!(!lease.ingest(&ring, &meter, &[0.75, 0.25], || true));
        assert!(!lease.activate(&ring, &meter, &active, || true));
        assert!(!lease.retire(&ring, &meter, &active));
    }

    #[test]
    fn stereo_peak_remote_late_cleanup_preserves_local_capture_and_mic() {
        for source in ["local", "capture", "mic"] {
            let ring = super::Mutex::new(SampleRing::new(48_000));
            let meter = super::Mutex::new(super::StereoWindow::new());
            let active = super::AtomicBool::new(false);
            let old = super::RemoteScopeLease::prepare(&ring, &meter);
            assert!(old.activate(&ring, &meter, &active, || true));
            // The actual deck open and generic set_ring_active boundaries use this reset.
            let measured = super::StereoPeak {
                left: 0.5,
                right: 0.125,
            };
            super::with_stereo_window(&ring, &meter, |ring, meter| {
                ring.clear_pending();
                *meter = super::StereoWindow::new();
                active.store(true, super::Ordering::Relaxed);
                meter.observe(Some(measured), 100);
                ring.push_interleaved(&[0.25, 0.0625]);
            });
            // Keep outer liveness true to prove identity rejects independently of cancellation.
            assert!(
                !old.ingest(&ring, &meter, &[0.75, 0.25], || true),
                "{source}"
            );
            assert!(!old.retire(&ring, &meter, &active), "{source}");
            assert!(active.load(super::Ordering::Relaxed), "{source}");
            assert_eq!(meter.lock().unwrap().take(101), Some(measured), "{source}");
            assert_eq!(
                ring.lock().unwrap().take_stereo_samples(),
                [0.25, 0.0625],
                "{source}"
            );
        }
    }

    #[test]
    fn stereo_peak_remote_same_generation_successor_keeps_its_lease_and_measurement() {
        let ring = super::Mutex::new(SampleRing::new(48_000));
        let meter = super::Mutex::new(super::StereoWindow::new());
        let active = super::AtomicBool::new(false);
        let generation = std::sync::atomic::AtomicU64::new(77);
        let live = || generation.load(super::Ordering::SeqCst) == 77;
        let old = super::RemoteScopeLease::prepare(&ring, &meter);
        assert!(old.activate(&ring, &meter, &active, live));
        assert!(old.retire(&ring, &meter, &active));
        let new = super::RemoteScopeLease::prepare(&ring, &meter);
        assert!(new.activate(&ring, &meter, &active, live));
        // Consumption and expiry must not retire the current session identity.
        let measured = super::StereoPeak {
            left: 0.5,
            right: 0.125,
        };
        for (observed, read) in [(100, 101), (200, 701)] {
            assert!(new.ingest(&ring, &meter, &[0.5, 0.125], live));
            super::with_stereo_window(&ring, &meter, |ring, meter| {
                meter.observe(
                    super::StereoPeak::prepare(&mut ring.take_stereo_samples()),
                    observed,
                );
                assert_eq!(
                    meter.take(read),
                    (read - observed <= 500).then_some(measured)
                );
            });
        }
        assert!(new.ingest(&ring, &meter, &[0.5, 0.125], live));
        super::with_stereo_window(&ring, &meter, |ring, meter| {
            meter.observe(
                super::StereoPeak::prepare(&mut ring.take_stereo_samples()),
                710,
            );
        });
        assert!(new.ingest(&ring, &meter, &[0.25, 0.0625], live));
        assert!(!old.ingest(&ring, &meter, &[0.75, 0.25], live));
        assert!(!old.retire(&ring, &meter, &active));
        assert!(!old.activate(&ring, &meter, &active, live));
        assert!(active.load(super::Ordering::Relaxed));
        assert_eq!(meter.lock().unwrap().take(711), Some(measured));
        assert_eq!(ring.lock().unwrap().take_stereo_samples(), [0.25, 0.0625]);
        assert_eq!(generation.load(super::Ordering::SeqCst), 77);
    }

    #[test]
    fn stereo_peak_remote_cancel_between_outer_check_and_locked_activation() {
        use std::sync::{Arc, Mutex, mpsc};
        use std::time::Duration;
        for cancellation in ["session", "quit", "generation"] {
            let ring = Arc::new(Mutex::new(SampleRing::new(48_000)));
            let meter = Arc::new(Mutex::new(super::StereoWindow::new()));
            let active = Arc::new(super::AtomicBool::new(false));
            let cancel = Arc::new(super::AtomicBool::new(false));
            let quit = Arc::new(super::AtomicBool::new(false));
            let generation = Arc::new(std::sync::atomic::AtomicU64::new(77));
            let lease = Arc::new(super::RemoteScopeLease::prepare(&ring, &meter));
            let (checked_tx, checked_rx) = mpsc::sync_channel(1);
            let (resume_tx, resume_rx) = mpsc::sync_channel(1);
            let (done_tx, done_rx) = mpsc::sync_channel(1);
            let wait = Duration::from_secs(5);
            let setup = {
                let (ring, meter, active, lease, cancel, quit, generation) = (
                    ring.clone(),
                    meter.clone(),
                    active.clone(),
                    lease.clone(),
                    cancel.clone(),
                    quit.clone(),
                    generation.clone(),
                );
                std::thread::spawn(move || {
                    let live = || {
                        !cancel.load(super::Ordering::SeqCst)
                            && !quit.load(super::Ordering::SeqCst)
                            && generation.load(super::Ordering::SeqCst) == 77
                    };
                    assert!(live());
                    checked_tx.send(()).unwrap();
                    resume_rx.recv_timeout(wait).unwrap();
                    done_tx
                        .send(lease.activate(&ring, &meter, &active, live))
                        .unwrap();
                })
            };
            checked_rx.recv_timeout(wait).unwrap();
            match cancellation {
                "session" => cancel.store(true, super::Ordering::SeqCst),
                "quit" => quit.store(true, super::Ordering::SeqCst),
                _ => generation.store(78, super::Ordering::SeqCst),
            }
            resume_tx.send(()).unwrap();
            assert!(!done_rx.recv_timeout(wait).unwrap(), "{cancellation}");
            setup.join().unwrap();
            assert!(!active.load(super::Ordering::Relaxed), "{cancellation}");
            assert_eq!(meter.lock().unwrap().take(1), None);
            assert!(!lease.retire(&ring, &meter, &active));
        }
    }

    #[test]
    fn stereo_peak_remote_setup_before_generic_replacement_cannot_activate_or_clear_it() {
        let ring = super::Mutex::new(SampleRing::new(48_000));
        let meter = super::Mutex::new(super::StereoWindow::new());
        let active = super::AtomicBool::new(false);
        let pending = super::RemoteScopeLease::prepare(&ring, &meter);
        let measured = super::StereoPeak {
            left: 0.0,
            right: 0.0,
        };
        super::with_stereo_window(&ring, &meter, |ring, meter| {
            ring.clear_pending();
            *meter = super::StereoWindow::new();
            active.store(true, super::Ordering::Relaxed);
            meter.observe(Some(measured), 100);
            ring.push_interleaved(&[0.0, 0.0]);
        });
        assert!(!pending.activate(&ring, &meter, &active, || true));
        assert!(!pending.retire(&ring, &meter, &active));
        assert!(active.load(super::Ordering::Relaxed));
        assert_eq!(meter.lock().unwrap().take(101), Some(measured));
        assert_eq!(ring.lock().unwrap().take_stereo_samples(), [0.0, 0.0]);
    }

    #[test]
    fn stereo_peak_remote_adapters_share_production_lease_guard() {
        let remote: String = include_str!("remote.rs")
            .chars()
            .filter(|ch| !ch.is_whitespace())
            .collect();
        assert!(remote.contains("self.scope.ingest("));
        assert!(remote.contains("shared_v.push_scope(&buf[..got],epoch)"));
        assert!(remote.contains("shared.push_scope(&f32buf,visual_epoch)"));
        assert!(remote.contains("shared.scope.activate("));
        assert!(remote.contains("self.scope.retire("));
        assert!(!remote.contains("push_interleaved("));
        assert!(!remote.contains("set_ring_active("));
        let setup = remote.split("fnrun_session(").nth(1).unwrap();
        assert!(
            setup.find("RemoteScopeLease::prepare").unwrap()
                < setup.find(".to_socket_addrs()").unwrap()
        );
        let teardown = remote.split("fnteardown_session(").nth(1).unwrap();
        assert!(
            teardown.find("shared.trip()").unwrap()
                < teardown.find("bridge_core::bounded_join(").unwrap()
        );
    }

    #[test]
    fn grid_angle_uses_actual_local_or_known_current_remote_mode_only() {
        for mode in Mode::ALL {
            let expected = if mode == Mode::Xy45 {
                std::f32::consts::FRAC_PI_4
            } else {
                0.0
            };
            assert_eq!(super::grid_angle(mode, false, Some(Mode::Xy45)), expected);
            assert_eq!(super::grid_angle(Mode::Xy45, true, Some(mode)), expected);
            assert_eq!(super::grid_angle(mode, true, None), 0.0);
        }
        let compact =
            |source: &str| -> String { source.chars().filter(|ch| !ch.is_whitespace()).collect() };
        let render = compact(include_str!("render.rs"));
        let assignment = "r.grid_angle=crate::engine::grid_angle(";
        let angle = render.find(assignment).unwrap();
        let flip = render.find("ifletSome(f)=flip.as_mut()").unwrap();
        assert!(angle > flip);
        assert!(render[flip..angle].contains("computer.mode=mode_from_index(f.pending_mode);"));
        let arguments = render[angle..]
            .split_once(';')
            .unwrap()
            .0
            .strip_prefix(assignment)
            .unwrap()
            .strip_suffix(')')
            .unwrap()
            .trim_end_matches(',');
        assert_eq!(
            arguments,
            "computer.mode,geometry_active,geometry_mode.as_ref().and_then(|m|m.current())"
        );
        let remote = compact(include_str!("remote.rs"));
        assert!(remote.contains("Arc::ptr_eq(s,&session)"));
        assert!(remote.contains("!session.cancelled()"));
        let command = remote
            .split_once("crate::render::Cmd::GeometryMode(")
            .unwrap()
            .1
            .split_once(';')
            .unwrap()
            .0;
        let fields = command
            .split_once("GeometryMode{")
            .unwrap()
            .1
            .split_once('}')
            .unwrap()
            .0
            .trim_end_matches(',');
        assert_eq!(fields, "session:Arc::downgrade(&shared),mode");
        // Protocol K and G have no shared mode revision. These are adapter checks, not GPU or relay execution.
    }

    // ---- geometry FX stage ----

    const W: f32 = 1000.0;
    const H: f32 = 1000.0;

    fn radius(x: f32, y: f32) -> f32 {
        let (u, v) = ((x - W / 2.0) / (W / 2.0), (y - H / 2.0) / (H / 2.0));
        (u * u + v * v).sqrt()
    }

    #[test]
    fn geom_fx_off_or_zero_amount_is_identity() {
        let segs = vec![[100.0, 200.0, 300.0, 400.0, 0.8]];
        assert_eq!(super::apply_geom_fx(&segs, W, H, 0, 1.0, 1.3, 0.5), segs);
        assert_eq!(super::apply_geom_fx(&segs, W, H, 2, 0.0, 1.3, 0.5), segs);
    }

    #[test]
    fn kaleido_replicates_dihedral_copies() {
        let segs = vec![[600.0, 500.0, 700.0, 500.0, 1.0]];
        let a = 0.999_f32; // n = 5 sectors -> 10 dihedral copies
        let out = super::apply_geom_fx(&segs, W, H, 1, a, 0.0, 0.0);
        assert_eq!(out.len(), 10);
        let gain = (10.0_f32).powf(-0.6);
        for seg in &out {
            assert!((seg[4] - gain).abs() < 1e-5, "intensity {}", seg[4]);
            assert!((radius(seg[0], seg[1]) - 0.2).abs() < 1e-4);
            assert!((radius(seg[2], seg[3]) - 0.4).abs() < 1e-4);
        }
    }

    #[test]
    fn spin_quarter_turn_maps_axes() {
        let segs = vec![[700.0, 500.0, 700.0, 500.0, 1.0]]; // (u,v) = (0.4, 0)
        let out = super::apply_geom_fx(&segs, W, H, 2, 1.0, std::f32::consts::FRAC_PI_2, 0.0);
        // phase = pi/2 rotates (0.4, 0) onto (0, 0.4) -> pixel (500, 700)
        assert!((out[0][0] - 500.0).abs() < 1e-2, "x={}", out[0][0]);
        assert!((out[0][1] - 700.0).abs() < 1e-2, "y={}", out[0][1]);
    }

    #[test]
    fn tunnel_unit_circle_is_fixed() {
        let a = 0.7_f32;
        // On the unit circle (r=1) at theta=0: radius must be invariant.
        let rim = vec![[1000.0, 500.0, 1000.0, 500.0, 1.0]];
        let out = super::apply_geom_fx(&rim, W, H, 3, a, 0.0, 0.0);
        assert!((radius(out[0][0], out[0][1]) - 1.0).abs() < 1e-4);
        // The exact center maps to the ring mouth at 0.30*a.
        let center = vec![[500.0, 500.0, 500.0, 500.0, 1.0]];
        let out = super::apply_geom_fx(&center, W, H, 3, a, 0.0, 0.0);
        assert!((radius(out[0][0], out[0][1]) - 0.30 * a).abs() < 1e-4);
    }

    #[test]
    fn pulse_zoom_scales_radius_with_env() {
        let a = 0.5_f32;
        let e = 1.0_f32;
        let segs = vec![[700.0, 500.0, 700.0, 500.0, 1.0]]; // r = 0.4
        let out = super::apply_geom_fx(&segs, W, H, 4, a, 0.0, e);
        let z = 1.0 + 0.6 * a * e;
        assert!((radius(out[0][0], out[0][1]) - 0.4 * z).abs() < 1e-4);
    }
}

#[cfg(test)]
mod pause_resume_tests {
    #[test]
    fn visual_flush_preserves_remote_owner_and_rejects_old_epoch() {
        use super::*;
        let ring = Mutex::new(phosphor_audio::SampleRing::new(48000));
        let meter = Mutex::new(StereoWindow::new());
        let active = AtomicBool::new(false);
        let lease = RemoteScopeLease::prepare(&ring, &meter);
        assert!(lease.activate(&ring, &meter, &active, || true));
        let epoch = std::sync::atomic::AtomicU64::new(3);
        let old = epoch.load(Ordering::Acquire);
        with_stereo_window(&ring, &meter, |ring, meter| {
            epoch.fetch_add(1, Ordering::AcqRel);
            ring.clear_pending();
            meter.clear_visual_measurement();
        });
        assert!(!lease.ingest(&ring, &meter, &[9.0, 9.0], || old
            == epoch.load(Ordering::Acquire)));
        assert!(lease.ingest(&ring, &meter, &[0.25, 0.5], || 4
            == epoch.load(Ordering::Acquire)));
        assert!(active.load(Ordering::Relaxed));
        assert_eq!(ring.lock().unwrap().take_stereo_samples(), [0.25, 0.5]);
    }
}
