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
}

#[cfg_attr(not(target_os = "android"), allow(dead_code))]
impl StereoWindow {
    pub(crate) const fn new() -> Self {
        Self {
            peak: None,
            started_ms: 0,
            remote_boundary: None,
            capture_owner: 0,
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
        if owner == 0 || meter.capture_owner != owner || !active.load(Ordering::Relaxed)
            || epoch.load(Ordering::Acquire) != read_epoch
        {
            return false;
        }
        ring.push_interleaved(samples);
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
            let (ring, meter, active, epoch) = (ring.clone(), meter.clone(), active.clone(), epoch.clone());
            std::thread::spawn(move || {
                let read_epoch = epoch.load(Ordering::Acquire);
                began_tx.send(read_epoch).unwrap();
                finish_rx.recv_timeout(wait).unwrap();
                publish_capture_read(&ring, &meter, &active, &epoch, owner, read_epoch, &[0.9, -0.9])
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
        assert!(publish_capture_read(&ring, &meter, &active, &epoch, current_owner,
            epoch.load(Ordering::Acquire), &[0.25, -0.5]));
        assert_eq!(ring.lock().unwrap().take_stereo_samples(), vec![0.25, -0.5]);
    }

    #[test]
    fn held_epoch4_read_cannot_publish_after_resume_epoch5() { held_read(false); }

    #[test]
    fn held_read_cannot_publish_to_replacement_even_at_same_epoch() { held_read(true); }

    #[test]
    fn current_epoch_cannot_resurrect_inactive_or_non_capture_source() {
        let ring = Mutex::new(phosphor_audio::SampleRing::new(48_000));
        let meter = Mutex::new(StereoWindow::new());
        let active = AtomicBool::new(false);
        let epoch = AtomicU64::new(5);
        let owner = with_stereo_window(&ring, &meter, |_, meter| meter.activate_capture());
        assert!(!publish_capture_read(&ring, &meter, &active, &epoch, owner, 5, &[1.0, 1.0]));
        assert!(!active.load(Ordering::Relaxed));
        with_stereo_window(&ring, &meter, |_, meter| {
            *meter = StereoWindow::new();
            active.store(true, Ordering::Relaxed);
        });
        for candidate in [0, owner] {
            assert!(!publish_capture_read(&ring, &meter, &active, &epoch, candidate, 5, &[1.0, 1.0]));
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
            let (ring, meter, active, epoch) = (ring.clone(), meter.clone(), active.clone(), epoch.clone());
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

/// Autosize holds through silence. Sounding frames retain the desktop constants:
/// instant peak attack, 0.999 release, 0.92 headroom, 0.01 floor, 0.1..6 target,
/// and a 0.05 effective-gain glide.
#[cfg_attr(not(target_os = "android"), allow(dead_code))]
pub(crate) struct AutoGain {
    enabled: bool,
    peak: f32,
    effective: f32,
    reset_open: u64,
}

#[cfg_attr(not(target_os = "android"), allow(dead_code))]
impl AutoGain {
    pub(crate) fn new(manual_gain: f32) -> Self {
        Self {
            enabled: false,
            peak: 0.0,
            effective: manual_gain.clamp(0.1, 7.0),
            reset_open: 0,
        }
    }

    pub(crate) fn set_auto(&mut self, on: bool, manual_gain: f32) -> f32 {
        self.enabled = on;
        if !on {
            self.effective = manual_gain.clamp(0.1, 7.0);
        }
        self.effective
    }

    pub(crate) fn set_manual(&mut self, gain: f32) -> f32 {
        self.enabled = false;
        // Manual gain reaches 7 while automatic gain stays within the desktop 0.1..6 range.
        self.effective = gain.clamp(0.1, 7.0);
        self.effective
    }

    pub(crate) fn enabled(&self) -> bool {
        self.enabled
    }

    pub(crate) fn new_local_item(&mut self, proven_open: u64, current_open: u64) {
        if proven_open != 0 && proven_open == current_open && proven_open != self.reset_open {
            self.peak = 0.0;
            self.reset_open = proven_open;
        }
    }

    pub(crate) fn update(&mut self, peak: f32) -> Option<f32> {
        if !self.enabled {
            return None;
        }
        if !peak.is_finite() || peak < 0.02 {
            return Some(self.effective);
        }
        self.peak = peak.max(self.peak * 0.999);
        let target = (0.92 / self.peak.max(0.01)).clamp(0.1, 6.0);
        self.effective += (target - self.effective) * 0.05;
        Some(self.effective)
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

    #[test]
    fn auto_gain_uses_desktop_attack_and_glide_constants() {
        let mut ag = super::AutoGain::new(1.0);
        ag.set_auto(true, 1.0);
        let effective = ag.update(0.5).unwrap();
        // target = 0.92 / 0.5 = 1.84; glide = 1 + (1.84 - 1) * 0.05.
        assert!((effective - 1.042).abs() < 1e-6, "effective={effective}");
    }

    #[test]
    fn auto_gain_releases_peak_slowly_and_clamps_target() {
        let mut ag = super::AutoGain::new(1.0);
        ag.set_auto(true, 1.0);
        let loud = ag.update(20.0).unwrap(); // target clamps to 0.1
        assert!((loud - 0.955).abs() < 1e-6, "loud={loud}");
        let released = ag.update(0.02).unwrap(); // Only sounding frames release the tracked peak.
        let expected_target = (0.92_f32 / (20.0_f32 * 0.999)).clamp(0.1, 6.0);
        let expected = loud + (expected_target - loud) * 0.05;
        assert!((released - expected).abs() < 1e-6, "released={released}");

        ag.new_local_item(1, 1);
        let quiet = ag.update(0.02).unwrap(); // New quiet track's target clamps to 6.
        assert!((quiet - (released + (6.0 - released) * 0.05)).abs() < 1e-6);
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
            for gain in [0.1, 1.0, 6.0, 7.0] {
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
        assert!(render.contains("raw_peak.map_or(0.0, crate::engine::StereoPeak::max)"));
        assert!(render.contains("compute_scope_frame(&mut computer, &samples"));
        assert!(!include_str!("jni_glue.rs").contains("take_stereo_samples"));
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
    fn auto_gain_holds_dead_space_and_releases_only_sounding_frames() {
        let mut gain = super::AutoGain::new(1.0);
        gain.set_auto(true, 1.0);
        for _ in 0..100 {
            gain.update(0.8);
        }
        let (peak, effective) = (gain.peak, gain.effective);
        for _ in 0..10_000 {
            for silent in [0.0, 0.019999, -1.0, f32::NAN, f32::INFINITY] {
                assert_eq!(gain.update(silent), Some(effective));
                assert_eq!(gain.peak, peak);
            }
        }
        gain.update(0.02);
        assert_eq!(gain.peak, peak * 0.999);
        let expected = effective + ((0.92 / (peak * 0.999)) - effective) * 0.05;
        assert!((gain.effective - expected).abs() < 1e-6);
        gain.update(0.8);
        assert_eq!(gain.peak, 0.8);
    }

    #[test]
    fn auto_gain_only_current_new_item_resets_peak_without_jumping_gain() {
        let mut gain = super::AutoGain::new(1.0);
        gain.set_auto(true, 1.0);
        gain.update(0.8);
        let effective = gain.effective;
        for (proof, current) in [(0, 0), (1, 2), (1, 0)] {
            gain.new_local_item(proof, current);
            assert_eq!(gain.peak, 0.8);
        }
        gain.set_auto(true, 1.0); // Repeated restore is not a track.
        assert_eq!(gain.peak, 0.8);
        gain.new_local_item(2, 2);
        assert_eq!(gain.peak, 0.0);
        assert_eq!(gain.effective, effective);
        gain.update(0.25);
        gain.new_local_item(2, 2); // Duplicate TrackStarted cannot re-reset.
        assert_eq!(gain.peak, 0.25);
        gain.new_local_item(2, 3); // Delayed old render command cannot reset a newer item.
        assert_eq!(gain.peak, 0.25);
        gain.new_local_item(3, 3);
        assert_eq!(gain.peak, 0.0);
        assert_eq!(gain.set_manual(7.0), 7.0);
        gain.set_auto(true, 7.0);
        gain.update(0.0);
        assert_eq!(gain.effective, 7.0); // Silence still holds the manual landing.
        for _ in 0..10_000 {
            gain.update(0.02);
        }
        assert!((gain.effective - 6.0).abs() < 0.0001);
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

    #[test]
    fn manual_gain_disarms_auto_and_lands_exactly() {
        let mut ag = super::AutoGain::new(1.0);
        ag.set_auto(true, 1.0);
        ag.update(0.25);
        assert!(ag.enabled());
        assert_eq!(ag.set_manual(2.4), 2.4);
        assert!(!ag.enabled());
        assert_eq!(ag.update(0.01), None);
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
