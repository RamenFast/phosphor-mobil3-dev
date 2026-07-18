//! On-device self-test (debug builds): deterministic offscreen render through the real
//! engine, written as selftest.json + selftest.png receipts into the app files dir.
//! No surface, no clock — same segments every run, so the receipt is comparable.

use std::io::Write;
use std::time::Instant;

use phosphor_dsp::{Computer, Mode};

const SIZE: u32 = 512;

pub fn run(files_dir: &str) -> String {
    match run_inner(files_dir) {
        Ok(v) => v,
        Err(e) => serde_json::json!({ "ok": false, "error": e }).to_string(),
    }
}

fn run_inner(files_dir: &str) -> Result<String, String> {
    let t0 = Instant::now();

    // Deterministic 3:2 Lissajous — one full figure, fixed sample count.
    let mut computer = Computer::new();
    computer.set_sample_rate(48_000, 1);
    computer.mode = Mode::Xy;
    let n = 4800;
    let mut samples = Vec::with_capacity(n * 2);
    for i in 0..n {
        let t = i as f64 / 48_000.0;
        samples.push(((std::f64::consts::TAU * 660.0 * t).sin() * 0.75) as f32);
        samples.push(((std::f64::consts::TAU * 440.0 * t).sin() * 0.75) as f32);
    }
    let segments: Vec<[f32; 5]> =
        computer.compute(&samples, SIZE as f32, SIZE as f32).to_vec();
    let t_dsp = t0.elapsed();

    let mut gpu = phosphor_render_gpu::GpuRenderer::new_offscreen(SIZE, SIZE, 2)?;
    gpu.theme = phosphor_beam::THEME_PRESETS[0].1;
    // Several advances build up glow exactly like consecutive frames would.
    for _ in 0..8 {
        gpu.advance(&segments);
    }
    let rgba = gpu.composite_and_read();
    let t_total = t0.elapsed();

    let lit = rgba.chunks_exact(4).filter(|p| p[0] > 8 || p[1] > 8 || p[2] > 8).count();
    // FNV-1a over the pixels: a stable fingerprint for cross-build comparison.
    let mut hash: u64 = 0xcbf29ce484222325;
    for b in &rgba {
        hash ^= *b as u64;
        hash = hash.wrapping_mul(0x100000001b3);
    }

    let png_path = format!("{files_dir}/selftest.png");
    write_png(&png_path, SIZE, SIZE, &rgba)?;

    let report = serde_json::json!({
        "ok": lit > 500,
        "size": SIZE,
        "segments": segments.len(),
        "lit_pixels": lit,
        "pixel_fnv1a": format!("{hash:016x}"),
        "dsp_ms": t_dsp.as_secs_f64() * 1e3,
        "total_ms": t_total.as_secs_f64() * 1e3,
        "png": png_path,
    })
    .to_string();

    let mut f = std::fs::File::create(format!("{files_dir}/selftest.json"))
        .map_err(|e| e.to_string())?;
    f.write_all(report.as_bytes()).map_err(|e| e.to_string())?;
    Ok(report)
}

fn write_png(path: &str, w: u32, h: u32, rgba: &[u8]) -> Result<(), String> {
    let file = std::fs::File::create(path).map_err(|e| e.to_string())?;
    let mut enc = png::Encoder::new(std::io::BufWriter::new(file), w, h);
    enc.set_color(png::ColorType::Rgba);
    enc.set_depth(png::BitDepth::Eight);
    let mut writer = enc.write_header().map_err(|e| e.to_string())?;
    writer.write_image_data(rgba).map_err(|e| e.to_string())?;
    Ok(())
}
