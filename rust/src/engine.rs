//! Host-testable engine glue. No JNI, no Android — plain `cargo test` covers this.

use phosphor_dsp::{Computer, Mode};

/// Desktop-v3 autosize law, kept host-testable here rather than buried in the
/// Android render loop. Constants and update order are verbatim from shell.rs:
/// instant peak attack, 0.999 release, 0.92 headroom, 0.01 floor, 0.1..6 target,
/// and a 0.05 effective-gain glide.
#[cfg_attr(not(target_os = "android"), allow(dead_code))]
pub(crate) struct AutoGain {
    enabled: bool,
    peak: f32,
    effective: f32,
}

#[cfg_attr(not(target_os = "android"), allow(dead_code))]
impl AutoGain {
    pub(crate) fn new(manual_gain: f32) -> Self {
        Self {
            enabled: false,
            peak: 0.0,
            effective: manual_gain.clamp(0.1, 7.0),
        }
    }

    pub(crate) fn set_auto(&mut self, on: bool, manual_gain: f32) -> f32 {
        self.enabled = on;
        if on {
            // Re-measure from the next sound; the glide starts wherever it was.
            self.peak = 0.0;
        } else {
            self.effective = manual_gain.clamp(0.1, 7.0);
        }
        self.effective
    }

    pub(crate) fn set_manual(&mut self, gain: f32) -> f32 {
        self.enabled = false;
        // Manual reaches 7 (Ben's ask); the AUTO target law below stays 0.1..6
        // desktop-verbatim.
        self.effective = gain.clamp(0.1, 7.0);
        self.effective
    }

    pub(crate) fn enabled(&self) -> bool {
        self.enabled
    }

    pub(crate) fn update(&mut self, peak: f32) -> Option<f32> {
        if !self.enabled {
            return None;
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

/// Synthetic scope feed for M1: an evolving Lissajous figure, phase-continuous across
/// frames, sample-domain identical to what a real track would push. Host-testable.
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

#[cfg(test)]
mod tests {
    use phosphor_audio::SampleRing;
    use phosphor_dsp::{Computer, Mode};

    /// The accuracy regression (2026-07-18): a pure 440 Hz quadrature circle, produced in
    /// 10 ms/100 Hz capture chunks and drained at 120 Hz exactly like the phone, must be
    /// phase-contiguous across every window boundary AND reconstruct as ONE deposit per
    /// display frame — the old renderer-side substep loop split it into N separately
    /// decayed passes (Ben's "2-3 circles out of sync") and froze decay on empty ticks.
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
        assert_eq!(display_advances, 60, "active display cadence must never freeze on an empty tap");
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
        let released = ag.update(0.0).unwrap(); // tracked peak is 20 * 0.999
        let expected_target = (0.92_f32 / (20.0_f32 * 0.999)).clamp(0.1, 6.0);
        let expected = loud + (expected_target - loud) * 0.05;
        assert!((released - expected).abs() < 1e-6, "released={released}");

        ag.set_auto(true, 1.0);
        let quiet = ag.update(0.0).unwrap(); // 0.01 floor -> target clamps to 6
        assert!((quiet - (released + (6.0 - released) * 0.05)).abs() < 1e-6);
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
