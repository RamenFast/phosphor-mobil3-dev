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
