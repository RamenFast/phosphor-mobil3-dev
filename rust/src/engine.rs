//! Host-testable engine glue. No JNI, no Android — plain `cargo test` covers this.

use phosphor_dsp::{Computer, Mode};

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

impl Feeder {
    pub fn new() -> Feeder {
        Feeder { t0: std::time::Instant::now(), last: 0.0, phase_x: 0.0, phase_y: 0.0 }
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
    #[test]
    fn engine_info_reports_segments() {
        let info = super::engine_info();
        let v: serde_json::Value = serde_json::from_str(&info).unwrap();
        assert_eq!(v["engine"], "phosphor");
        assert!(v["segments"].as_u64().unwrap() > 0, "DSP produced no segments: {info}");
    }
}
