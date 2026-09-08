/// Fixed read-only 48k duplicated-mono history summary. No samples escape this value.
#[derive(Debug)]
pub struct Summary {
    pub frames: usize,
    pub rms: f64,
    pub frequency: f64,
    pub duplicated: bool,
}
pub fn summarize(pcm: &[f32]) -> Summary {
    let mut square = 0.0;
    let mut crossings = 0;
    let mut previous = 0.0;
    let mut duplicated = pcm.len() % 2 == 0;
    for pair in pcm.chunks_exact(2) {
        duplicated &= pair[0].is_finite() && pair[1].is_finite() && pair[0] == pair[1];
        square += f64::from(pair[0]).powi(2);
        if previous < 0.0 && pair[0] >= 0.0 {
            crossings += 1;
        }
        previous = pair[0];
    }
    let frames = pcm.len() / 2;
    Summary {
        frames,
        rms: if frames == 0 {
            0.0
        } else {
            (square / frames as f64).sqrt()
        },
        frequency: if frames == 0 {
            0.0
        } else {
            crossings as f64 * 48000.0 / frames as f64
        },
        duplicated,
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn downstream_tone_and_silence() {
        let tone: Vec<f32> = (0..24000)
            .flat_map(|i| {
                let v = (std::f64::consts::TAU * 997.0 * i as f64 / 48000.0).sin() as f32 * 0.02;
                [v, v]
            })
            .collect();
        let s = summarize(&tone);
        assert_eq!(s.frames, 24000);
        assert!(s.duplicated);
        assert!((s.frequency - 997.0).abs() < 3.0);
        assert!(s.rms > 0.014 && s.rms < 0.015);
        let silence = summarize(&vec![0.0; 48000]);
        assert_eq!(silence.rms, 0.0);
        assert_eq!(silence.frequency, 0.0);
    }
    #[test]
    fn downstream_rejects_channel_and_nonfinite_mismatch() {
        assert!(!summarize(&[0.2, 0.3]).duplicated);
        assert!(!summarize(&[f32::NAN, 0.0]).duplicated);
        assert!(!summarize(&[0.0]).duplicated);
        assert_eq!(summarize(&[]).frames, 0);
    }
}
