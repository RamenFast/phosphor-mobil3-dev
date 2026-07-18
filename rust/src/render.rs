//! The render thread — owns wgpu (Instance/Adapter/Device), the Android surface, the
//! GpuRenderer, and the scope's motion envelopes (warm-up, tube-flip, resting beam).
//!
//! Lifecycle contract (crash class #1 on Android): `surfaceDestroyed` on the Kotlin side
//! BLOCKS until this thread has dropped the wgpu Surface and the NativeWindow ref —
//! Android invalidates the window the moment the callback returns. The GpuRenderer and
//! its decay textures survive across surface loss (the beam remembers backgrounding).

use std::sync::OnceLock;
use std::sync::atomic::{AtomicBool, AtomicU32, Ordering};
use std::sync::mpsc;

use ndk::native_window::NativeWindow;
use phosphor_dsp::{Computer, Mode};
use phosphor_proto::settings::Settings;

pub enum Cmd {
    SurfaceCreated { window: SendWindow, width: u32, height: u32, density: f32 },
    SurfaceChanged { width: u32, height: u32 },
    SurfaceDestroyed { ack: mpsc::SyncSender<()> },
    Paused(bool),
    SetMode(u8),
    SetBeamColor(u8),
    /// -1 = unlimited (Immediate present, may exceed the panel), 0 = panel vsync (Fifo),
    /// N > 0 = cap to N fps (Immediate present + frame limiter; N above the panel rate is
    /// honored — it just tears past the display's refresh).
    SetTargetFps(i32),
    /// Beam-rate multiplier: sub-steps of DSP+advance per displayed frame with dt-correct
    /// decay (1 = 120, 2 = 240, 4 = 480 effective integration rate).
    SetOversample(u8),
    /// Deflection gain (the figure swelling under a thumb). Clamped 0.05..16.
    SetGain(f32),
    /// Phosphor persistence 0..0.98 (glow / trail length).
    SetGlow(f32),
    /// Orbit the 3D camera by deltas (radians). 2D modes ignore it silently.
    OrbitBy(f32, f32),
    /// Dolly the 3D camera by a delta (positive = pull back). Clamped 1.6..8.
    DollyBy(f32),
    /// Remove-animations accessibility: hard cuts instead of envelopes.
    SetReducedMotion(bool),
    /// Beam focus in px (the desktop slider, 0.3..3.0) — smaller = sharper.
    SetFocus(f32),
    /// Beam brightness budget (the desktop "Beam" slider, 1.0..30.0).
    SetBeamEnergy(f32),
    /// Graticule on/off (desktop grid_enabled).
    SetGrid(bool),
    /// Custom beam light: 1–3 color slots + grid color. count==0 returns to presets.
    SetCustomBeam { colors: [[f32; 3]; 3], count: u8, grid: [f32; 3] },
    /// Cycle timing: seconds per color→color leg; per_track advances only on CycleAdvance.
    SetBeamCycle { seconds: f32, per_track: bool },
    /// A track boundary passed (Kotlin's metadata listener) — advance a per-track cycle.
    CycleAdvance,
}

pub const MODE_COUNT: u8 = 11;

fn mode_from_index(i: u8) -> Mode {
    match i % MODE_COUNT {
        0 => Mode::Xy,
        1 => Mode::Xy45,
        2 => Mode::XySwirl,
        3 => Mode::XyDots,
        4 => Mode::XyzTakens,
        5 => Mode::Helix,
        6 => Mode::Waveform,
        7 => Mode::Ring,
        8 => Mode::Spectrum,
        9 => Mode::SpectrumRadial,
        _ => Mode::Tunnel,
    }
}

/// Live mode index, readable from any thread for the status band.
pub static CURRENT_MODE: std::sync::atomic::AtomicU8 = std::sync::atomic::AtomicU8::new(0);
/// Live gain ×1000 (readable for the band readout / gesture ribbon).
pub static GAIN_MILLI: AtomicU32 = AtomicU32::new(1000);
/// True when an active source has been silent past the sleep window (resting beam is up).
pub static NO_SIGNAL: AtomicBool = AtomicBool::new(false);
/// Live beam color, packed 0xRRGGBB (for accent_follows_beam chrome breathing).
pub static BEAM_RGB: AtomicU32 = AtomicU32::new(0x6bff8c);

fn pack_rgb(c: [f32; 3]) -> u32 {
    let ch = |v: f32| (v.clamp(0.0, 1.0).powf(1.0 / 2.2) * 255.0) as u32;
    (ch(c[0]) << 16) | (ch(c[1]) << 8) | ch(c[2])
}

/// ndk's NativeWindow is a refcounted ANativeWindow; the NDK API is thread-safe, so
/// moving the ref onto the render thread is sound. The wrapper says so explicitly.
pub struct SendWindow(pub NativeWindow);
unsafe impl Send for SendWindow {}

static SENDER: OnceLock<mpsc::Sender<Cmd>> = OnceLock::new();

pub fn sender() -> &'static mpsc::Sender<Cmd> {
    SENDER.get_or_init(|| {
        let (tx, rx) = mpsc::channel();
        std::thread::Builder::new()
            .name("phosphor-render".into())
            .spawn(move || render_thread(rx))
            .expect("spawn render thread");
        tx
    })
}

struct Gpu {
    instance: wgpu::Instance,
    adapter: wgpu::Adapter,
    device: wgpu::Device,
    queue: wgpu::Queue,
}

struct Active {
    surface: wgpu::Surface<'static>,
    config: wgpu::SurfaceConfiguration,
    present_caps: Vec<wgpu::PresentMode>,
    // Held so the ANativeWindow outlives the wgpu Surface built on it. Field order is
    // drop order: Surface first, then the window ref.
    _window: SendWindow,
}

/// The tube-flip: trace collapses to a horizontal line (70 ms), the mode switches at the
/// pinch, then re-blooms (110 ms). 180 ms of switching functions on a real scope.
struct Flip {
    t0: std::time::Instant,
    pending_mode: u8,
    switched: bool,
}
const FLIP_COLLAPSE: f32 = 0.070;
const FLIP_BLOOM: f32 = 0.110;

/// Thermionic warm-up: a point blooms at center with a faint thermal flicker, then the
/// trace unfurls — ≤1.2 s, and it IS the loading time. Cold start only.
const WARMUP_SECS: f32 = 1.2;

/// Silent-source sleep window before the resting beam appears.
const REST_AFTER_SECS: f32 = 2.0;

fn smoothstep(t: f32) -> f32 {
    let t = t.clamp(0.0, 1.0);
    t * t * (3.0 - 2.0 * t)
}

/// Fifo for panel-vsync (target 0). Anything else wants Immediate (uncapped or
/// higher-than-panel with a software limiter); Mailbox is the fallback, Fifo the last.
fn present_mode_for(target_fps: i32, caps: &[wgpu::PresentMode]) -> wgpu::PresentMode {
    if target_fps == 0 {
        return wgpu::PresentMode::Fifo;
    }
    for want in [wgpu::PresentMode::Immediate, wgpu::PresentMode::Mailbox] {
        if caps.contains(&want) {
            return want;
        }
    }
    wgpu::PresentMode::Fifo
}

fn render_thread(rx: mpsc::Receiver<Cmd>) {
    let mut gpu: Option<Gpu> = None;
    let mut renderer: Option<phosphor_render_gpu::GpuRenderer> = None;
    let mut active: Option<Active> = None;
    let mut paused = false;

    let defaults = Settings::default();
    let mut computer = Computer::new();
    computer.set_sample_rate(48_000, 1);
    computer.mode = Mode::Xy;
    let mut fps_frames: u32 = 0;
    let mut fps_t0 = std::time::Instant::now();
    let mut beam_color: usize = 0;
    let mut target_fps: i32 = 0; // 0 = panel vsync
    let mut oversample: u32 = 1; // beam-rate multiplier (1× = 120, 2× = 240, 4× = 480)
    let mut base_persistence = defaults.persistence;
    let mut last_present = std::time::Instant::now();

    // Camera mirror (Computer's camera is crate-private; set_camera takes absolutes).
    let (mut cam_yaw, mut cam_pitch, mut cam_dolly) = (0.55_f64, 0.35_f64, 3.0_f64);

    // Motion envelopes.
    let mut reduced_motion = false;
    let mut warmup: Option<std::time::Instant> = None;
    let mut flip: Option<Flip> = None;
    let mut silent_since: Option<std::time::Instant> = None;
    let mut rest_phase: f32 = 0.0;

    // Custom light + cycle: colors lerp slot→slot over `cycle_secs` per leg (timer mode)
    // or advance one leg per track boundary (per-track mode; exempt from the guard).
    let mut custom_colors: [[f32; 3]; 3] = [[0.42, 1.0, 0.55]; 3];
    let mut custom_count: u8 = 0; // 0 = presets active
    let mut custom_grid: [f32; 3] = [0.35, 1.0, 0.45];
    let mut cycle_secs: f32 = 3.0;
    let mut cycle_per_track = false;
    let mut cycle_t0 = std::time::Instant::now();
    let mut cycle_leg: usize = 0;

    loop {
        // Idle (no surface, or paused): block on the channel. Active: drain then draw.
        let cmd = if active.is_none() || paused {
            match rx.recv() {
                Ok(c) => Some(c),
                Err(_) => return,
            }
        } else {
            match rx.try_recv() {
                Ok(c) => Some(c),
                Err(mpsc::TryRecvError::Empty) => None,
                Err(mpsc::TryRecvError::Disconnected) => return,
            }
        };

        if let Some(cmd) = cmd {
            match cmd {
                Cmd::SurfaceCreated { window, width, height, density } => {
                    // Drop any previous surface first — two swapchains on one
                    // ANativeWindow is a Vulkan conflict.
                    active = None;
                    match bring_up(&mut gpu, window, width, height, target_fps) {
                        Ok(a) => {
                            let g = gpu.as_ref().unwrap();
                            match renderer.as_mut() {
                                Some(r) => {
                                    if let Err(e) = r.resize(width, height) {
                                        log::error!("renderer resize: {e}");
                                    }
                                }
                                None => match phosphor_render_gpu::GpuRenderer::new_for_surface(
                                    &g.adapter,
                                    g.device.clone(),
                                    g.queue.clone(),
                                    width,
                                    height,
                                    2,
                                    a.config.format,
                                ) {
                                    Ok(mut r) => {
                                        r.beam_focus = defaults.beam_focus;
                                        r.persistence = defaults.persistence;
                                        r.display_scale = density;
                                        r.theme = phosphor_beam::THEME_PRESETS[0].1;
                                        renderer = Some(r);
                                        // Cold start: the cathode warms.
                                        if !reduced_motion {
                                            warmup = Some(std::time::Instant::now());
                                        }
                                    }
                                    Err(e) => log::error!("GpuRenderer::new_for_surface: {e}"),
                                },
                            }
                            active = Some(a);
                            log::info!("surface up {width}x{height} density {density}");
                        }
                        Err(e) => log::error!("surface bring-up failed: {e}"),
                    }
                }
                Cmd::SurfaceChanged { width, height } => {
                    if let (Some(a), Some(g)) = (active.as_mut(), gpu.as_ref()) {
                        a.config.width = width.max(1);
                        a.config.height = height.max(1);
                        a.surface.configure(&g.device, &a.config);
                        if let Some(r) = renderer.as_mut() {
                            if let Err(e) = r.resize(width, height) {
                                log::error!("renderer resize: {e}");
                            }
                        }
                    }
                }
                Cmd::SurfaceDestroyed { ack } => {
                    active = None;
                    let _ = ack.send(());
                    log::info!("surface torn down");
                }
                Cmd::Paused(p) => {
                    paused = p;
                    log::info!("render paused: {p}");
                }
                Cmd::SetMode(i) => {
                    if reduced_motion || active.is_none() {
                        computer.mode = mode_from_index(i);
                        CURRENT_MODE.store(i % MODE_COUNT, Ordering::Relaxed);
                        log::info!("mode: {}", computer.mode.name());
                    } else {
                        // The tube-flip owns the switch; it lands at the collapse point.
                        flip = Some(Flip {
                            t0: std::time::Instant::now(),
                            pending_mode: i % MODE_COUNT,
                            switched: false,
                        });
                    }
                }
                Cmd::SetBeamColor(i) => {
                    beam_color = (i as usize) % phosphor_beam::THEME_PRESETS.len();
                    if let Some(r) = renderer.as_mut() {
                        r.theme = phosphor_beam::THEME_PRESETS[beam_color].1;
                    }
                    log::info!("beam color: {}", phosphor_beam::THEME_PRESETS[beam_color].0);
                }
                Cmd::SetTargetFps(fps) => {
                    if fps != target_fps {
                        target_fps = fps;
                        if let (Some(a), Some(g)) = (active.as_mut(), gpu.as_ref()) {
                            a.config.present_mode = present_mode_for(target_fps, &a.present_caps);
                            a.surface.configure(&g.device, &a.config);
                        }
                        log::info!("target fps: {target_fps}");
                    }
                }
                Cmd::SetOversample(n) => {
                    oversample = (n as u32).clamp(1, 8);
                    log::info!("beam oversample: {oversample}x");
                }
                Cmd::SetGain(g) => {
                    // Desktop parity: shell.rs clamps gain to 0.1..6.0.
                    computer.gain = g.clamp(0.1, 6.0);
                    GAIN_MILLI.store((computer.gain * 1000.0) as u32, Ordering::Relaxed);
                }
                Cmd::SetBeamEnergy(e) => {
                    // Desktop parity: the "Beam" slider, 1.0..30.0.
                    computer.beam_energy = e.clamp(1.0, 30.0);
                }
                Cmd::SetGrid(on) => {
                    if let Some(r) = renderer.as_mut() {
                        r.grid_enabled = on;
                    }
                }
                Cmd::SetGlow(p) => {
                    base_persistence = p.clamp(0.0, 0.98);
                    if let Some(r) = renderer.as_mut() {
                        r.persistence = base_persistence;
                    }
                }
                Cmd::OrbitBy(dy, dp) => {
                    cam_yaw += dy as f64;
                    cam_pitch = (cam_pitch + dp as f64).clamp(-1.45, 1.45);
                    computer.set_camera(Some(cam_yaw), Some(cam_pitch), None);
                }
                Cmd::DollyBy(d) => {
                    cam_dolly = (cam_dolly + d as f64).clamp(1.6, 8.0);
                    computer.set_camera(None, None, Some(cam_dolly));
                }
                Cmd::SetFocus(f) => {
                    if let Some(r) = renderer.as_mut() {
                        r.beam_focus = f.clamp(0.3, 3.0);
                        log::info!("beam focus: {:.2}", r.beam_focus);
                    }
                }
                Cmd::SetCustomBeam { colors, count, grid } => {
                    custom_colors = colors;
                    custom_count = count.min(3);
                    custom_grid = grid;
                    cycle_leg = 0;
                    cycle_t0 = std::time::Instant::now();
                    if custom_count == 0 {
                        if let Some(r) = renderer.as_mut() {
                            r.theme = phosphor_beam::THEME_PRESETS[beam_color].1;
                        }
                    }
                    log::info!("custom beam: {} colors", custom_count);
                }
                Cmd::SetBeamCycle { seconds, per_track } => {
                    cycle_secs = seconds.clamp(0.1, 60.0);
                    cycle_per_track = per_track;
                    cycle_t0 = std::time::Instant::now();
                    log::info!("beam cycle: {cycle_secs}s per_track={cycle_per_track}");
                }
                Cmd::CycleAdvance => {
                    if custom_count >= 2 && cycle_per_track {
                        cycle_leg = (cycle_leg + 1) % custom_count as usize;
                        cycle_t0 = std::time::Instant::now();
                    }
                }
                Cmd::SetReducedMotion(rm) => {
                    reduced_motion = rm;
                    if rm {
                        // Land any in-flight envelope instantly.
                        if let Some(f) = flip.take() {
                            computer.mode = mode_from_index(f.pending_mode);
                            CURRENT_MODE.store(f.pending_mode, Ordering::Relaxed);
                        }
                        warmup = None;
                    }
                    log::info!("reduced motion: {rm}");
                }
            }
            continue;
        }

        // Draw one frame (FIFO present blocks to vsync — this IS the pacing).
        let (Some(a), Some(g), Some(r)) = (active.as_mut(), gpu.as_ref(), renderer.as_mut())
        else {
            continue;
        };

        let source_active = crate::deck::DECK_ACTIVE.load(Ordering::Relaxed);
        let samples = if source_active {
            crate::deck::scope_ring().lock().unwrap().take_stereo_samples()
        } else {
            Vec::new()
        };

        // Resting-beam bookkeeping: an idle stage (no source) rests immediately; an
        // active-but-silent source rests after the sleep window.
        let resting = if samples.is_empty() {
            if source_active {
                let since = *silent_since.get_or_insert_with(std::time::Instant::now);
                since.elapsed().as_secs_f32() > REST_AFTER_SECS
            } else {
                true
            }
        } else {
            silent_since = None;
            false
        };
        NO_SIGNAL.store(resting && source_active, Ordering::Relaxed);

        let w = a.config.width as f32;
        let h = a.config.height as f32;

        // Envelope factors for this frame.
        let now = std::time::Instant::now();
        let mut scale_xy = 1.0_f32; // warm-up: whole figure grows from the center point
        let mut scale_y = 1.0_f32; // tube-flip: vertical collapse / re-bloom
        let mut brightness = 1.0_f32;
        if let Some(t0) = warmup {
            let t = t0.elapsed().as_secs_f32() / WARMUP_SECS;
            if t >= 1.0 {
                warmup = None;
            } else {
                scale_xy = smoothstep(t);
                // faint thermal flicker while the cathode heats — settles as t → 1
                let flick = (t * 61.0).sin() * (t * 17.0).cos();
                brightness = (0.35 + 0.65 * t) * (1.0 - 0.12 * (1.0 - t) * flick.abs());
            }
        }
        if let Some(f) = flip.as_mut() {
            let t = f.t0.elapsed().as_secs_f32();
            if t < FLIP_COLLAPSE {
                scale_y *= 1.0 - smoothstep(t / FLIP_COLLAPSE);
            } else {
                if !f.switched {
                    computer.mode = mode_from_index(f.pending_mode);
                    CURRENT_MODE.store(f.pending_mode, Ordering::Relaxed);
                    log::info!("mode: {} (tube-flip)", computer.mode.name());
                    f.switched = true;
                }
                let bt = (t - FLIP_COLLAPSE) / FLIP_BLOOM;
                if bt >= 1.0 {
                    flip = None;
                } else {
                    scale_y *= smoothstep(bt);
                }
            }
        }
        let transform_active = scale_xy < 1.0 || scale_y < 1.0 || brightness < 1.0;

        // Custom light: static color, or the cycle lerping slot→slot. Timer mode loops
        // continuously; per-track mode fades one leg per CycleAdvance then holds.
        if custom_count >= 1 {
            let cur = if custom_count == 1 {
                custom_colors[0]
            } else {
                let t = (cycle_t0.elapsed().as_secs_f32() / cycle_secs).min(if cycle_per_track { 1.0 } else { f32::MAX });
                let (leg, frac) = if cycle_per_track {
                    (cycle_leg, t.min(1.0))
                } else {
                    let total = t + cycle_leg as f32;
                    let leg = (total as usize) % custom_count as usize;
                    (leg, total.fract())
                };
                let a = custom_colors[leg % custom_count as usize];
                let b = custom_colors[(leg + 1) % custom_count as usize];
                let s = smoothstep(frac);
                [
                    a[0] + (b[0] - a[0]) * s,
                    a[1] + (b[1] - a[1]) * s,
                    a[2] + (b[2] - a[2]) * s,
                ]
            };
            r.theme = phosphor_beam::Theme::custom(cur, custom_grid);
            BEAM_RGB.store(pack_rgb(cur), Ordering::Relaxed);
        } else {
            BEAM_RGB.store(
                pack_rgb(phosphor_beam::THEME_PRESETS[beam_color].1.beam_color),
                Ordering::Relaxed,
            );
        }

        // Beam-rate oversampling: integrate the trace over N sub-steps per displayed frame
        // (dt-correct decay so brightness is unchanged). The panel still shows 120 Hz, but
        // the beam is computed at 120·N — a smoother, more-recent trace ("beyond 120").
        let mut seg_count = 0usize;
        let mut advance =
            |r: &mut phosphor_render_gpu::GpuRenderer, segs: &[[f32; 5]], count: &mut usize| {
                *count += segs.len();
                if transform_active {
                    let cx = w * 0.5;
                    let cy = h * 0.5;
                    let mapped: Vec<[f32; 5]> = segs
                        .iter()
                        .map(|s| {
                            [
                                cx + (s[0] - cx) * scale_xy,
                                cy + (s[1] - cy) * scale_xy * scale_y,
                                cx + (s[2] - cx) * scale_xy,
                                cy + (s[3] - cy) * scale_xy * scale_y,
                                s[4] * brightness,
                            ]
                        })
                        .collect();
                    r.advance(&mapped);
                } else {
                    r.advance(segs);
                }
            };

        if resting {
            // The resting beam: a small breathing point at center — never a black mystery.
            rest_phase += 1.0 / 40.0;
            let i = 0.45 + 0.25 * (rest_phase * 0.8).sin();
            let cx = w * 0.5;
            let cy = h * 0.5;
            let d = 1.6_f32 * r.display_scale.max(1.0);
            let dot: [[f32; 5]; 2] = [
                [cx - d, cy, cx + d, cy, i],
                [cx, cy - d, cx, cy + d, i],
            ];
            advance(r, &dot, &mut seg_count);
        } else if oversample <= 1 {
            let segments = computer.compute(&samples, w, h);
            let segments = segments.to_vec();
            advance(r, &segments, &mut seg_count);
        } else {
            let n = oversample as usize;
            r.persistence = base_persistence.powf(1.0 / n as f32);
            let frames = samples.len() / 2;
            let per = frames.div_ceil(n);
            for k in 0..n {
                let start = (k * per * 2).min(samples.len());
                let end = ((k + 1) * per * 2).min(samples.len());
                if start >= end {
                    break;
                }
                let segments = computer.compute(&samples[start..end], w, h);
                let segments = segments.to_vec();
                advance(r, &segments, &mut seg_count);
            }
            r.persistence = base_persistence;
        }

        let frame = match a.surface.get_current_texture() {
            Ok(f) => f,
            Err(wgpu::SurfaceError::Lost | wgpu::SurfaceError::Outdated) => {
                a.surface.configure(&g.device, &a.config);
                continue;
            }
            Err(e) => {
                log::error!("get_current_texture: {e}");
                std::thread::sleep(std::time::Duration::from_millis(8));
                continue;
            }
        };
        let view = frame
            .texture
            .create_view(&wgpu::TextureViewDescriptor::default());
        let mut encoder = g.device.create_command_encoder(&wgpu::CommandEncoderDescriptor {
            label: Some("phosphor-frame"),
        });
        r.composite_into(&mut encoder, &view, (0.0, 0.0, w, h), Some(wgpu::Color::BLACK));
        g.queue.submit([encoder.finish()]);
        frame.present();

        // Software frame limiter for capped targets (target > 0). Fifo self-paces at 0;
        // unlimited (-1) never sleeps.
        if target_fps > 0 {
            let budget = std::time::Duration::from_secs_f64(1.0 / target_fps as f64);
            let elapsed = last_present.elapsed();
            if elapsed < budget {
                std::thread::sleep(budget - elapsed);
            }
        }
        last_present = now;

        fps_frames += 1;
        let elapsed = fps_t0.elapsed().as_secs_f64();
        if elapsed >= 1.0 {
            log::info!(
                "fps {:.1} ({} frames, {} segs last frame, {}x beam)",
                fps_frames as f64 / elapsed,
                fps_frames,
                seg_count,
                oversample
            );
            fps_frames = 0;
            fps_t0 = std::time::Instant::now();
        }
    }
}

fn create_surface(
    instance: &wgpu::Instance,
    window: &SendWindow,
) -> Result<wgpu::Surface<'static>, String> {
    use wgpu::rwh::{
        AndroidDisplayHandle, AndroidNdkWindowHandle, RawDisplayHandle, RawWindowHandle,
    };
    let ptr = std::ptr::NonNull::new(window.0.ptr().as_ptr().cast())
        .ok_or("null ANativeWindow")?;
    let raw_window_handle = RawWindowHandle::AndroidNdk(AndroidNdkWindowHandle::new(ptr));
    let raw_display_handle = RawDisplayHandle::Android(AndroidDisplayHandle::new());
    unsafe {
        instance.create_surface_unsafe(wgpu::SurfaceTargetUnsafe::RawHandle {
            raw_display_handle,
            raw_window_handle,
        })
    }
    .map_err(|e| format!("create_surface: {e}"))
}

fn configure(
    g: &Gpu,
    surface: &wgpu::Surface<'_>,
    width: u32,
    height: u32,
    target_fps: i32,
) -> (wgpu::SurfaceConfiguration, Vec<wgpu::PresentMode>) {
    let caps = surface.get_capabilities(&g.adapter);
    let format = caps
        .formats
        .iter()
        .find(|f| f.is_srgb())
        .copied()
        .unwrap_or(caps.formats[0]);
    let config = wgpu::SurfaceConfiguration {
        usage: wgpu::TextureUsages::RENDER_ATTACHMENT,
        format,
        width: width.max(1),
        height: height.max(1),
        present_mode: present_mode_for(target_fps, &caps.present_modes),
        alpha_mode: caps.alpha_modes[0],
        view_formats: vec![],
        desired_maximum_frame_latency: 2,
    };
    surface.configure(&g.device, &config);
    (config, caps.present_modes)
}

fn bring_up(
    gpu: &mut Option<Gpu>,
    window: SendWindow,
    width: u32,
    height: u32,
    target_fps: i32,
) -> Result<Active, String> {
    if gpu.is_none() {
        let instance = wgpu::Instance::default();
        let surface = create_surface(&instance, &window)?;
        let adapter = pollster::block_on(instance.request_adapter(&wgpu::RequestAdapterOptions {
            power_preference: wgpu::PowerPreference::HighPerformance,
            compatible_surface: Some(&surface),
            ..Default::default()
        }))
        .map_err(|e| format!("no adapter: {e}"))?;
        let (device, queue) =
            pollster::block_on(adapter.request_device(&wgpu::DeviceDescriptor::default()))
                .map_err(|e| format!("no device: {e}"))?;
        log::info!("adapter: {:?}", adapter.get_info());
        let g = Gpu { instance, adapter, device, queue };
        let (config, present_caps) = configure(&g, &surface, width, height, target_fps);
        log::info!("present modes: {present_caps:?}");
        *gpu = Some(g);
        return Ok(Active { surface, config, present_caps, _window: window });
    }
    let g = gpu.as_ref().unwrap();
    let surface = create_surface(&g.instance, &window)?;
    let (config, present_caps) = configure(g, &surface, width, height, target_fps);
    Ok(Active { surface, config, present_caps, _window: window })
}
