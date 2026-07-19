//! The render thread — owns wgpu (Instance/Adapter/Device), the Android surface, the
//! GpuRenderer, and the scope's motion envelopes (warm-up, tube-flip, resting beam).
//!
//! Lifecycle contract (crash class #1 on Android): `surfaceDestroyed` on the Kotlin side
//! BLOCKS until this thread has dropped the wgpu Surface and the NativeWindow ref —
//! Android invalidates the window the moment the callback returns. The GpuRenderer and
//! its decay textures survive across surface loss (the beam remembers backgrounding).

use std::sync::atomic::{AtomicBool, AtomicU32, Ordering};
use std::sync::mpsc;
use std::sync::OnceLock;

use ndk::native_window::NativeWindow;
use phosphor_dsp::{Computer, Mode};
use phosphor_proto::settings::Settings;

pub enum Cmd {
    SurfaceCreated {
        window: SendWindow,
        width: u32,
        height: u32,
        density: f32,
    },
    SurfaceChanged {
        width: u32,
        height: u32,
    },
    SurfaceDestroyed {
        ack: mpsc::SyncSender<()>,
    },
    Paused(bool),
    SetMode(u8),
    SetBeamColor(u8),
    /// -1 = unlimited (Immediate present, may exceed the panel), 0 = panel vsync (Fifo),
    /// N > 0 = cap to N fps (Immediate present + frame limiter; N above the panel rate is
    /// honored — it just tears past the display's refresh).
    SetTargetFps(i32),
    /// DSP reconstruction multiplier. Input stays 48 kHz; 1/2/4 reconstruct at
    /// 48/96/192 kHz while preserving one decay/deposit per displayed frame.
    SetOversample(u8),
    /// Deflection gain (the figure swelling under a thumb). Clamped 0.1..6.
    SetGain(f32),
    /// Desktop-parity autosize. Manual SetGain always disarms it.
    SetGainAuto(bool),
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
    /// Bottom-overscroll beam bloom, normalized 0..1. This scales segment deposit energy;
    /// the renderer's real P7 flash/glow textures own the visible release tail.
    SetBloomPull(f32),
    /// View rotation in quadrants (0..3, CCW screen-relative). UI-PLACEMENT-LOCKED
    /// mode pins the Activity and rotates the BEAM to gravity instead — the chrome
    /// physically cannot move. DSP path only; remote geometry keeps its own frame.
    SetViewRotation(u8),
    /// Geometry FX stage (0 off · 1 kaleido · 2 spin · 3 tunnel · 4 pulse). Applies to
    /// locally computed beams only, BEFORE SetViewRotation's quarter-turn remap. NEVER
    /// mirrored to the desktop — these are phone-local tags the protocol doesn't know.
    SetGeomFx(u8),
    /// Geometry FX depth 0..1.
    SetGeomAmount(f32),
    /// Graticule on/off (desktop grid_enabled).
    SetGrid(bool),
    /// Custom beam light: 1–3 color slots + grid color. count==0 returns to presets.
    SetCustomBeam {
        colors: [[f32; 3]; 3],
        count: u8,
        grid: [f32; 3],
    },
    /// Cycle timing: seconds per color→color leg; per_track advances only on CycleAdvance.
    SetBeamCycle {
        seconds: f32,
        per_track: bool,
    },
    /// A track boundary passed (Kotlin's metadata listener) — advance a per-track cycle.
    CycleAdvance,
    /// Remote geometry mode: draw the desktop's decimated beam, bypassing the DSP.
    GeometryActive(bool),
    /// One desktop tap frame (normalized 0..1 points in trace space).
    GeometryFrame(GeomFrame),
}

pub struct GeomFrame {
    pub points: Vec<[f32; 2]>,
    pub aspect: f32,
    pub intensity: f32,
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
/// Local renderer AUTO-GAIN truth for the settings chip and honesty band.
pub static GAIN_AUTO: AtomicBool = AtomicBool::new(false);
/// True when an active source has been silent past the sleep window (resting beam is up).
pub static NO_SIGNAL: AtomicBool = AtomicBool::new(false);
/// Live beam color, packed 0xRRGGBB (for accent_follows_beam chrome breathing).
pub static BEAM_RGB: AtomicU32 = AtomicU32::new(0x6bff8c);
/// Nerd-HUD stats: measured fps ×10, and segments drawn in the last frame.
pub static FPS_X10: AtomicU32 = AtomicU32::new(0);
pub static SEGS_LAST: AtomicU32 = AtomicU32::new(0);

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

/// A perceptual lift of NEW beam energy. Keeping this upstream of `GpuRenderer::advance`
/// is the important part: both P7 layers receive the bloom (flash keep 0.50, glow coupled
/// at 0.85 with persistence-dependent decay) and therefore remember the pull physically.
fn bloom_energy_multiplier(pull: f32) -> f32 {
    1.0 + 1.8 * pull.clamp(0.0, 1.0).powf(0.82)
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
    crate::engine::set_reconstruction_rate(&mut computer, 1);
    computer.mode = Mode::Xy;
    let mut manual_gain = defaults.gain.clamp(0.1, 7.0);
    computer.gain = manual_gain;
    let mut auto_gain = crate::engine::AutoGain::new(manual_gain);
    let mut fps_frames: u32 = 0;
    let mut fps_t0 = std::time::Instant::now();
    let mut beam_color: usize = 0;
    let mut target_fps: i32 = 0; // 0 = panel vsync
    let mut oversample: u32 = 1; // DSP reconstruction multiplier (48/96/192 kHz)
    let mut view_rotation: u32 = 0; // quadrants; beam-to-gravity in UI-locked mode
    // Geometry FX state is loop-local (like view_rotation/oversample): the idle loop
    // drains commands before the first surface, so pre-surface restoreTuning is safe
    // without joining the renderer-creation mirror block below.
    let mut geom_fx: u8 = 0;
    let mut geom_amount: f32 = 0.6;
    let mut geom_phase: f32 = 0.0;
    let mut geom_env: f32 = 0.0;
    let mut geom_last = std::time::Instant::now();
    // Settings can arrive BEFORE the first surface (restoreTuning at app boot) — mirror
    // them so renderer creation applies the persisted truth, not defaults (Ben's field
    // receipt: grid pref needed a manual re-toggle after every install).
    let mut grid_on = true;
    let mut glow_persistence = defaults.persistence;
    let mut focus_px = defaults.beam_focus;
    let mut last_present = std::time::Instant::now();

    // Camera mirror (Computer's camera is crate-private; set_camera takes absolutes).
    let (mut cam_yaw, mut cam_pitch, mut cam_dolly) = (0.55_f64, 0.35_f64, 3.0_f64);

    // Motion envelopes.
    let mut reduced_motion = false;
    let mut warmup: Option<std::time::Instant> = None;
    let mut flip: Option<Flip> = None;
    let mut silent_since: Option<std::time::Instant> = None;
    let mut rest_phase: f32 = 0.0;
    let mut bloom_pull: f32 = 0.0;

    // Remote geometry (bridge visualizer mode): latest frame wins, decay keeps ticking.
    let mut geometry_active = false;
    let mut geom_frame: Option<GeomFrame> = None;

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
        // Idle (no surface, or paused): block on the channel. Active: drain ALL pending
        // commands then draw — 60 fps geometry frames must never back-queue behind vsync.
        let mut cmds: Vec<Cmd> = Vec::new();
        if active.is_none() || paused {
            match rx.recv() {
                Ok(c) => cmds.push(c),
                Err(_) => return,
            }
        } else {
            loop {
                match rx.try_recv() {
                    Ok(c) => cmds.push(c),
                    Err(mpsc::TryRecvError::Empty) => break,
                    Err(mpsc::TryRecvError::Disconnected) => return,
                }
            }
        }

        let had_cmds = !cmds.is_empty();
        for cmd in cmds.drain(..) {
            match cmd {
                Cmd::SurfaceCreated {
                    window,
                    width,
                    height,
                    density,
                } => {
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
                                        // Persisted truth, not defaults: these commands
                                        // may have arrived before the first surface.
                                        r.beam_focus = focus_px;
                                        r.persistence = glow_persistence;
                                        r.grid_enabled = grid_on;
                                        r.display_scale = density;
                                        r.theme = phosphor_beam::THEME_PRESETS
                                            [beam_color % phosphor_beam::THEME_PRESETS.len()].1;
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
                    oversample = crate::engine::set_reconstruction_rate(&mut computer, n as u32);
                    log::info!("beam oversample: {oversample}x");
                }
                Cmd::SetGeomFx(k) => {
                    geom_fx = k.min(4);
                    log::info!("geom fx: {geom_fx}");
                }
                Cmd::SetGeomAmount(v) => geom_amount = v.clamp(0.0, 1.0),
                Cmd::SetViewRotation(q) => {
                    view_rotation = (q % 4) as u32;
                    log::info!("view rotation: {}°", view_rotation * 90);
                }
                Cmd::SetGain(g) => {
                    // Manual range is 0.1..7 (Ben's ask — one past the desktop's 6);
                    // AUTO-GAIN still lands inside the desktop-verbatim 0.1..6 law.
                    manual_gain = g.clamp(0.1, 7.0);
                    computer.gain = auto_gain.set_manual(manual_gain);
                    GAIN_AUTO.store(false, Ordering::Relaxed);
                    GAIN_MILLI.store((computer.gain * 1000.0) as u32, Ordering::Relaxed);
                }
                Cmd::SetGainAuto(on) => {
                    computer.gain = auto_gain.set_auto(on, manual_gain);
                    GAIN_AUTO.store(on, Ordering::Relaxed);
                    GAIN_MILLI.store((computer.gain * 1000.0) as u32, Ordering::Relaxed);
                    log::info!("auto gain: {on}");
                }
                Cmd::SetBeamEnergy(e) => {
                    // Desktop parity: the "Beam" slider, 1.0..30.0.
                    computer.beam_energy = e.clamp(1.0, 30.0);
                }
                Cmd::SetBloomPull(pull) => {
                    bloom_pull = pull.clamp(0.0, 1.0);
                }
                Cmd::SetGrid(on) => {
                    grid_on = on;
                    if let Some(r) = renderer.as_mut() {
                        r.grid_enabled = on;
                    }
                }
                Cmd::SetGlow(p) => {
                    glow_persistence = p.clamp(0.0, 0.98);
                    if let Some(r) = renderer.as_mut() {
                        r.persistence = glow_persistence;
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
                    focus_px = f.clamp(0.3, 3.0);
                    if let Some(r) = renderer.as_mut() {
                        r.beam_focus = focus_px;
                        log::info!("beam focus: {:.2}", r.beam_focus);
                    }
                }
                Cmd::SetCustomBeam {
                    colors,
                    count,
                    grid,
                } => {
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
                Cmd::GeometryActive(on) => {
                    geometry_active = on;
                    if !on {
                        geom_frame = None;
                    }
                    log::info!("geometry mode: {on}");
                }
                Cmd::GeometryFrame(f) => {
                    geom_frame = Some(f); // latest wins
                }
            }
        }
        if had_cmds && (active.is_none() || paused) {
            continue;
        }

        // Draw one frame (FIFO present blocks to vsync — this IS the pacing).
        let (Some(a), Some(g), Some(r)) = (active.as_mut(), gpu.as_ref(), renderer.as_mut()) else {
            continue;
        };

        let source_active = crate::deck::DECK_ACTIVE.load(Ordering::Relaxed);
        let samples = if source_active {
            crate::deck::scope_ring()
                .lock()
                .unwrap()
                .take_stereo_samples()
        } else {
            Vec::new()
        };

        // One unconditional frame-peak fold feeds both autogain and the geometry FX
        // envelope (instant attack, ~100 ms release at 120 fps).
        let frame_peak = samples.iter().fold(0.0_f32, |p, s| p.max(s.abs()));
        geom_env = frame_peak.max(geom_env * 0.92);

        // Ported verbatim from desktop shell.rs: measure the raw source peak before
        // compute, then glide Computer.gain. Empty active frames still release the
        // peak slowly; remote geometry bypasses the local computer altogether.
        if source_active && !geometry_active && auto_gain.enabled() {
            if let Some(gain) = auto_gain.update(frame_peak) {
                computer.gain = gain;
                GAIN_MILLI.store((gain * 1000.0) as u32, Ordering::Relaxed);
            }
        }

        // Resting-beam bookkeeping: an idle stage (no source) rests immediately; an
        // active-but-silent source rests after the sleep window. Geometry mode IS the
        // signal — the local ring rests by design, never the display.
        let resting = if geometry_active {
            silent_since = None;
            false
        } else if samples.is_empty() {
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
        // The pull does not tint or blur chrome: it raises real segment energy before
        // deposition. When it returns to zero, energy already in the GPU textures keeps
        // decaying through phosphor-beam's two-layer P7 law.
        brightness *= bloom_energy_multiplier(bloom_pull);
        let transform_active = scale_xy < 1.0 || scale_y < 1.0 ||
            (brightness - 1.0).abs() > f32::EPSILON;

        // Custom light: static color, or the cycle lerping slot→slot. Timer mode loops
        // continuously; per-track mode fades one leg per CycleAdvance then holds.
        if custom_count >= 1 {
            let cur =
                if custom_count == 1 {
                    custom_colors[0]
                } else {
                    let t = (cycle_t0.elapsed().as_secs_f32() / cycle_secs)
                        .min(if cycle_per_track { 1.0 } else { f32::MAX });
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

        // The graticule zooms with the figure (Ben's ask): grid spacing rides the live
        // effective gain so a pinch reads as zooming the WORLD, not just amplifying the
        // trace. Clamped so the grid never degenerates into stripes or one giant cell.
        r.grid_spacing_fraction = (0.1125 * computer.gain).clamp(0.035, 0.55);

        // The DSP reconstructs the contiguous 48 kHz tap at the selected factor. One display
        // frame means one compute + one decay/deposit, matching desktop cadence — splitting
        // the drained window into N separately decayed deposits was the "2-3 circles out of
        // sync" defect (accuracy hunt, 2026-07-18), and freezing decay on empty ticks made
        // 120 Hz read as chunk-rate judder.
        let mut seg_count = 0usize;
        let advance =
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

        if geometry_active {
            // The desktop's beam, letterboxed into this panel; empty advances keep the
            // decay honest between frames.
            if let Some(f) = geom_frame.take() {
                let aspect = f.aspect.max(0.05);
                let rect_w = w.min(h * aspect);
                let rect_h = rect_w / aspect;
                let ox = (w - rect_w) * 0.5;
                let oy = (h - rect_h) * 0.5;
                let segs: Vec<[f32; 5]> = f
                    .points
                    .windows(2)
                    .map(|p| {
                        [
                            ox + p[0][0] * rect_w,
                            oy + p[0][1] * rect_h,
                            ox + p[1][0] * rect_w,
                            oy + p[1][1] * rect_h,
                            f.intensity,
                        ]
                    })
                    .collect();
                advance(r, &segs, &mut seg_count);
            } else {
                advance(r, &[], &mut seg_count);
            }
        } else if resting {
            // The resting beam: a small breathing point at center — never a black mystery.
            rest_phase += 1.0 / 40.0;
            let i = 0.45 + 0.25 * (rest_phase * 0.8).sin();
            let cx = w * 0.5;
            let cy = h * 0.5;
            let d = 1.6_f32 * r.display_scale.max(1.0);
            let dot: [[f32; 5]; 2] = [[cx - d, cy, cx + d, cy, i], [cx, cy - d, cx, cy + d, i]];
            advance(r, &dot, &mut seg_count);
        } else {
            // Beam-to-gravity: odd quadrants compute in the swapped space so the figure
            // keeps true aspect, then endpoints map by pure quarter-turns — no scaling.
            let (cw, ch) = if view_rotation % 2 == 1 { (h, w) } else { (w, h) };
            let mut segments = crate::engine::compute_scope_frame(&mut computer, &samples, cw, ch);
            // Geometry FX bends the freshly computed beam BEFORE the quarter-turn remap,
            // so it composes with every mode and every rotation lock. Local beams only —
            // remote geometry frames and the resting dot never reach this branch.
            if geom_fx != 0 && geom_amount > 0.0 {
                let dt = geom_last.elapsed().as_secs_f32().clamp(0.0, 0.05);
                geom_phase += dt * match geom_fx {
                    2 => geom_amount * (0.5 + 5.0 * geom_env.min(1.2)), // audio-whipped spin
                    _ => 0.6,                                          // tunnel breathing clock
                };
                segments =
                    crate::engine::apply_geom_fx(&segments, cw, ch, geom_fx, geom_amount, geom_phase, geom_env);
            }
            geom_last = std::time::Instant::now();
            if view_rotation == 0 {
                advance(r, &segments, &mut seg_count);
            } else {
                let rot = |x: f32, y: f32| -> (f32, f32) {
                    match view_rotation {
                        1 => (y, h - x),
                        2 => (w - x, h - y),
                        _ => (w - y, x),
                    }
                };
                let mapped: Vec<[f32; 5]> = segments
                    .iter()
                    .map(|s| {
                        let (ax, ay) = rot(s[0], s[1]);
                        let (bx, by) = rot(s[2], s[3]);
                        [ax, ay, bx, by, s[4]]
                    })
                    .collect();
                advance(r, &mapped, &mut seg_count);
            }
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
        let mut encoder = g
            .device
            .create_command_encoder(&wgpu::CommandEncoderDescriptor {
                label: Some("phosphor-frame"),
            });
        r.composite_into(
            &mut encoder,
            &view,
            (0.0, 0.0, w, h),
            Some(wgpu::Color::BLACK),
        );
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

        SEGS_LAST.store(seg_count as u32, Ordering::Relaxed);
        fps_frames += 1;
        let elapsed = fps_t0.elapsed().as_secs_f64();
        if elapsed >= 1.0 {
            FPS_X10.store(
                (fps_frames as f64 / elapsed * 10.0) as u32,
                Ordering::Relaxed,
            );
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

#[cfg(test)]
mod tests {
    use super::bloom_energy_multiplier;

    #[test]
    fn bloom_energy_is_clamped_continuous_and_monotonic() {
        assert_eq!(bloom_energy_multiplier(-1.0), 1.0);
        assert_eq!(bloom_energy_multiplier(0.0), 1.0);
        assert!((bloom_energy_multiplier(1.0) - 2.8).abs() < 1e-6);
        assert_eq!(bloom_energy_multiplier(2.0), bloom_energy_multiplier(1.0));

        let mut previous = bloom_energy_multiplier(0.0);
        for step in 1..=100 {
            let current = bloom_energy_multiplier(step as f32 / 100.0);
            assert!(current > previous);
            previous = current;
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
    let ptr = std::ptr::NonNull::new(window.0.ptr().as_ptr().cast()).ok_or("null ANativeWindow")?;
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
        let g = Gpu {
            instance,
            adapter,
            device,
            queue,
        };
        let (config, present_caps) = configure(&g, &surface, width, height, target_fps);
        log::info!("present modes: {present_caps:?}");
        *gpu = Some(g);
        return Ok(Active {
            surface,
            config,
            present_caps,
            _window: window,
        });
    }
    let g = gpu.as_ref().unwrap();
    let surface = create_surface(&g.instance, &window)?;
    let (config, present_caps) = configure(g, &surface, width, height, target_fps);
    Ok(Active {
        surface,
        config,
        present_caps,
        _window: window,
    })
}
