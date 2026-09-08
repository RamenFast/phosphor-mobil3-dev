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

use crate::surface_lifecycle::Retiring;
use ndk::native_window::NativeWindow;
use phosphor_dsp::{Computer, Mode};
use phosphor_proto::settings::Settings;

pub enum Cmd {
    SurfaceCreated {
        window: Retiring<SendWindow>,
        width: u32,
        height: u32,
        density: f32,
        transparent: bool,
        ack: mpsc::SyncSender<i32>,
    },
    SurfaceDestroyed {
        ack: mpsc::SyncSender<()>,
    },
    Paused(bool),
    DisplayDirty,
    SetMode(u8),
    SetBeamColor(u8),
    /// -1 = unlimited (Immediate present, may exceed the panel), 0 = panel vsync (Fifo),
    /// N > 0 = cap to N fps (Immediate present + frame limiter; N above the panel rate is
    /// honored — it just tears past the display's refresh).
    SetTargetFps(i32),
    /// DSP reconstruction multiplier. Input stays 48 kHz; 1/2/4 reconstruct at
    /// 48/96/192 kHz while preserving one decay/deposit per displayed frame.
    SetOversample(u8),
    /// Manual deflection gain (the figure swelling under a thumb). Clamped 0.1..7.
    SetGain(f32),
    /// Desktop-parity autosize. Manual SetGain always disarms it.
    SetGainAuto(bool),
    NewLocalItem(u64),
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
    /// One validated portable light snapshot, published atomically.
    SetLight(crate::light_cycle::LightSettings, Option<usize>),
    ApplyInstrument(std::sync::Arc<crate::instrument::Request>),
    RollLight,
    /// A track boundary passed (Kotlin's metadata listener) — advance a per-track cycle.
    CycleAdvance,
    /// Remote geometry mode: draw the desktop's decimated beam, bypassing the DSP.
    GeometryActive(bool),
    GeometryMode(crate::remote::GeometryMode),
}

pub struct GeomFrame {
    pub epoch: u64,
    pub owner: crate::remote::GeometryOwner,
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

pub(crate) static RAW_STEREO: std::sync::Mutex<crate::engine::StereoWindow> =
    std::sync::Mutex::new(crate::engine::StereoWindow::new());

fn meter_ms() -> u64 {
    static EPOCH: OnceLock<std::time::Instant> = OnceLock::new();
    EPOCH
        .get_or_init(std::time::Instant::now)
        .elapsed()
        .as_millis() as u64
}

pub(crate) fn with_stereo_window<T>(
    work: impl FnOnce(&mut phosphor_audio::SampleRing, &mut crate::engine::StereoWindow) -> T,
) -> T {
    crate::engine::with_stereo_window(crate::deck::scope_ring(), &RAW_STEREO, work)
}

pub(crate) fn take_stereo_stats() -> serde_json::Value {
    RAW_STEREO
        .lock()
        .unwrap()
        .take(meter_ms())
        .map(crate::engine::StereoPeak::json)
        .unwrap_or(serde_json::Value::Null)
}

fn pack_rgb(c: [f32; 3]) -> u32 {
    let ch = |v: f32| (v.clamp(0.0, 1.0).powf(1.0 / 2.2) * 255.0) as u32;
    (ch(c[0]) << 16) | (ch(c[1]) << 8) | ch(c[2])
}

/// ndk's NativeWindow is a refcounted ANativeWindow; the NDK API is thread-safe, so
/// moving the ref onto the render thread is sound. The wrapper says so explicitly.
pub struct SendWindow(pub NativeWindow);
unsafe impl Send for SendWindow {}

static GEOMETRY_LATEST: std::sync::Mutex<Option<GeomFrame>> = std::sync::Mutex::new(None);
pub fn geometry_frame(frame: GeomFrame) {
    let mut latest = GEOMETRY_LATEST.lock().unwrap();
    if frame.epoch == crate::pause::visual_epoch() && frame.owner.live() {
        *latest = Some(frame);
    }
}

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
    format: Option<wgpu::TextureFormat>,
}

struct Active {
    surface: wgpu::Surface<'static>,
    config: wgpu::SurfaceConfiguration,
    present_caps: Vec<wgpu::PresentMode>,
    // Held so the ANativeWindow outlives the wgpu Surface built on it. Field order is
    // drop order: Surface first, then the window ref.
    _window: Retiring<SendWindow>,
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
    let mut display_dirty = true;
    let mut spare_frame = None;
    let mut retained_presenter = None;
    let mut held_retries = 0u8;
    let mut retained_generation = 0;
    let mut energy_epoch = crate::pause::EnergyEpoch::default();

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
    // Settings can arrive before the first surface. Mirror them so renderer creation uses
    // persisted values instead of reverting to defaults.
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

    // Remote geometry (bridge visualizer mode): latest frame wins, decay keeps ticking.
    let mut geometry_active = false;
    let mut geometry_mode = None;
    let mut geom_frame: Option<GeomFrame> = None;

    let light_clock = std::time::Instant::now();
    let seed = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_nanos() as u64).unwrap_or(1);
    let mut light = crate::light_cycle::LightCycle::new(seed);

    loop {
        // Idle (no surface, or paused): block on the channel. Active: drain ALL pending
        // commands then draw — 60 fps geometry frames must never back-queue behind vsync.
        let mut cmds: Vec<Cmd> = Vec::new();
        let display_paused = crate::pause::DISPLAY.lock().unwrap().paused;
        if active.is_none() || paused || display_paused { light.suspend(); }
        if active.is_none() || paused || (display_paused && !display_dirty) {
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
                    transparent,
                    ack,
                } => {
                    display_dirty = true;
                    held_retries = 0;
                    if window.retirement.cancelled() {
                        continue;
                    }
                    // Drop any previous surface first — two swapchains on one
                    // ANativeWindow is a Vulkan conflict.
                    active = None;
                    match bring_up(&mut gpu, window, width, height, target_fps, transparent) {
                        Ok(a) => {
                            let g = gpu.as_ref().unwrap();
                            match renderer.as_mut() {
                                Some(r) => {
                                    if let Err(e) = r.resize(width, height) {
                                        log::error!("renderer resize: {e}");
                                        let _ = ack.send(-1);
                                        continue;
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
                                            [beam_color % phosphor_beam::THEME_PRESETS.len()]
                                        .1;
                                        renderer = Some(r);
                                        // Cold start: the cathode warms.
                                        if !reduced_motion {
                                            warmup = Some(std::time::Instant::now());
                                        }
                                    }
                                    Err(e) => log::error!("GpuRenderer::new_for_surface: {e}"),
                                },
                            }
                            let scope_alpha = crate::surface_policy::scope_alpha(
                                transparent,
                                a.config.alpha_mode,
                            );
                            let transparent_active = scope_alpha == 0.0;
                            if let Some(r) = renderer.as_mut() {
                                r.display_scale = density;
                                r.scope_alpha = scope_alpha;
                                if a._window
                                    .retirement
                                    .publish(&ack, i32::from(transparent_active))
                                {
                                    active = Some(a);
                                }
                            } else {
                                let _ = ack.send(-1);
                            }
                            log::info!("surface up {width}x{height} density {density}");
                        }
                        Err(e) => {
                            log::error!("surface bring-up failed: {e}");
                            let _ = ack.send(-1);
                        }
                    }
                }
                Cmd::SurfaceDestroyed { ack } => {
                    active = None;
                    let _ = ack.send(());
                    log::info!("surface torn down");
                }
                Cmd::DisplayDirty => {
                    let generation = crate::pause::DISPLAY.lock().unwrap().generation;
                    if generation != retained_generation {
                        spare_frame = None;
                        geom_frame = None;
                        retained_generation = generation;
                    }
                    display_dirty = true;
                    held_retries = 0;
                }
                Cmd::Paused(p) => {
                    display_dirty = true;
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
                    // Manual gain reaches 7. Automatic gain stays within the desktop 0.1..6 range.
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
                Cmd::NewLocalItem(id) => {
                    crate::deck::with_published_open(id, || auto_gain.new_local_item(id, id));
                }
                Cmd::SetBeamEnergy(e) => {
                    // Desktop parity: the "Beam" slider, 1.0..30.0.
                    computer.beam_energy = e.clamp(1.0, 30.0);
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
                Cmd::SetLight(settings, deleted) => {
                    if light.apply_edit(settings, light_clock.elapsed().as_secs_f64(), deleted) {
                        beam_color = light.settings().preset as usize;
                    }
                }
                Cmd::ApplyInstrument(request) => {
                    request.admit(!geometry_active, |setup| {
                        // The validated tuple changes before the next frame. No driver calls here.
                        flip = None;
                        computer.mode = mode_from_index(setup.mode);
                        CURRENT_MODE.store(setup.mode, Ordering::Relaxed);
                        manual_gain = setup.gain;
                        computer.gain = auto_gain.set_manual(manual_gain);
                        computer.gain = auto_gain.set_auto(setup.auto_gain, manual_gain);
                        GAIN_AUTO.store(setup.auto_gain, Ordering::Relaxed);
                        GAIN_MILLI.store((computer.gain * 1000.0) as u32, Ordering::Relaxed);
                        geom_fx = setup.geom_fx;
                        geom_amount = setup.geom_amount;
                        computer.beam_energy = setup.beam_energy;
                        glow_persistence = setup.glow;
                        focus_px = setup.focus;
                        grid_on = setup.grid;
                        oversample = crate::engine::set_reconstruction_rate(&mut computer, setup.oversample as u32);
                        let accepted = light.apply(setup.light.settings(), light_clock.elapsed().as_secs_f64());
                        debug_assert!(accepted);
                        beam_color = setup.light.preset as usize;
                        if let Some(r) = renderer.as_mut() {
                            r.persistence = glow_persistence;
                            r.beam_focus = focus_px;
                            r.grid_enabled = grid_on;
                            r.theme = phosphor_beam::THEME_PRESETS[beam_color].1;
                        }
                    });
                }
                Cmd::RollLight => light.roll(light_clock.elapsed().as_secs_f64()),
                Cmd::CycleAdvance => light.track(light_clock.elapsed().as_secs_f64()),
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
                    geometry_mode = None;
                    if !on {
                        geom_frame = None;
                    }
                    log::info!("geometry mode: {on}");
                }
                Cmd::GeometryMode(mode) => geometry_mode = Some(mode),
            }
        }
        if had_cmds && (active.is_none() || paused) {
            continue;
        }

        // Draw one frame (FIFO present blocks to vsync — this IS the pacing).
        let (Some(a), Some(g), Some(r)) = (active.as_mut(), gpu.as_ref(), renderer.as_mut()) else {
            continue;
        };

        let (display_paused, black, inspection, pinned, frame_token) = {
            let s = crate::pause::DISPLAY.lock().unwrap();
            (
                s.paused,
                s.black,
                s.inspection,
                s.pinned.clone(),
                s.frame_token(),
            )
        };
        if retained_generation != frame_token.generation {
            spare_frame = None;
            geom_frame = None;
            retained_generation = frame_token.generation;
        }
        if display_paused {
            if !display_dirty {
                continue;
            }
            display_dirty = false;
            let frame = match a.surface.get_current_texture() {
                Ok(f) => {
                    held_retries = 0;
                    f
                }
                Err(wgpu::SurfaceError::Lost | wgpu::SurfaceError::Outdated)
                    if held_retries < 1 =>
                {
                    held_retries += 1;
                    a.surface.configure(&g.device, &a.config);
                    display_dirty = true;
                    continue;
                }
                Err(e) => {
                    log::error!("held surface: {e}");
                    continue;
                }
            };
            let view = frame.texture.create_view(&Default::default());
            let mut encoder = g.device.create_command_encoder(&Default::default());
            if let Some(image) = pinned.as_ref().filter(|_| !black) {
                let presenter =
                    retained_presenter.get_or_insert_with(|| r.retained_presenter(a.config.format));
                r.present_retained(
                    presenter,
                    image,
                    &mut encoder,
                    &view,
                    [a.config.width, a.config.height],
                    r.scope_alpha,
                    inspection,
                );
            } else {
                let _pass = encoder.begin_render_pass(&wgpu::RenderPassDescriptor {
                    label: Some("BLACK or no held frame"),
                    color_attachments: &[Some(wgpu::RenderPassColorAttachment {
                        view: &view,
                        depth_slice: None,
                        resolve_target: None,
                        ops: wgpu::Operations {
                            load: wgpu::LoadOp::Clear(wgpu::Color::BLACK),
                            store: wgpu::StoreOp::Store,
                        },
                    })],
                    ..Default::default()
                });
            }
            g.queue.submit([encoder.finish()]);
            frame.present();
            FPS_X10.store(0, Ordering::Relaxed);
            SEGS_LAST.store(0, Ordering::Relaxed);
            continue;
        }
        if energy_epoch.needs_clear(frame_token) {
            r.clear_energy();
            geom_frame = None;
            computer.reset();
            geom_last = std::time::Instant::now();
            last_present = geom_last;
            silent_since = None;
            energy_epoch.cleared(frame_token);
        }
        if let Some(f) = GEOMETRY_LATEST.lock().unwrap().take() {
            if f.epoch == crate::pause::visual_epoch() && f.owner.live() {
                geom_frame = Some(f);
            }
        }
        if geom_frame
            .as_ref()
            .is_some_and(|f| f.epoch != crate::pause::visual_epoch() || !f.owner.live())
        {
            geom_frame = None;
        }
        let (source_active, samples, raw_peak) = with_stereo_window(|ring, meter| {
            let source_active = crate::deck::DECK_ACTIVE.load(Ordering::Relaxed);
            let mut samples = if source_active {
                ring.take_stereo_samples()
            } else {
                Vec::new()
            };
            let raw_peak = crate::engine::StereoPeak::prepare(&mut samples);
            if source_active {
                meter.consumed_frames = meter.consumed_frames.saturating_add((samples.len() / 2) as u64);
            }
            meter.observe(raw_peak, meter_ms());
            (source_active, samples, raw_peak)
        });

        // Meter, autosize and geometry share the same raw pre-gain stereo window.
        let frame_peak = raw_peak.map_or(0.0, crate::engine::StereoPeak::max);
        geom_env = frame_peak.max(geom_env * 0.92);

        // Empty and sub-threshold frames hold gain. Remote geometry bypasses local DSP.
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
        let transform_active =
            scale_xy < 1.0 || scale_y < 1.0 || (brightness - 1.0).abs() > f32::EPSILON;

        // This live-only observation never touches the retained HOLD image.
        if let Some((color, grid)) = light.observe(light_clock.elapsed().as_secs_f64()) {
            r.theme = phosphor_beam::Theme::custom(color, grid);
            BEAM_RGB.store(pack_rgb(color), Ordering::Relaxed);
        } else {
            r.theme = phosphor_beam::THEME_PRESETS[beam_color].1;
            BEAM_RGB.store(pack_rgb(r.theme.beam_color), Ordering::Relaxed);
        }

        // The graticule follows effective gain so a pinch zooms the scene instead of only
        // amplifying the trace. Clamping prevents stripes and oversized cells.
        r.grid_spacing_fraction = (0.1125 * computer.gain).clamp(0.035, 0.55);
        r.grid_angle = crate::engine::grid_angle(
            computer.mode,
            geometry_active,
            geometry_mode.as_ref().and_then(|m| m.current()),
        );

        // The DSP reconstructs the contiguous 48 kHz tap at the selected factor. Each display
        // frame performs one compute and one decay/deposit. Splitting a drained window into
        // multiple deposits creates overlapping traces, while skipping empty ticks freezes decay.
        let mut seg_count = 0usize;
        let advance = |r: &mut phosphor_render_gpu::GpuRenderer,
                       segs: &[[f32; 5]],
                       count: &mut usize| {
            *count += segs.len();
            if transform_active {
                let mapped: Vec<[f32; 5]> = segs
                    .iter()
                    .map(|s| {
                        crate::engine::map_deposit_segment(s, w, h, scale_xy, scale_y, brightness)
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
            let (cw, ch) = if view_rotation % 2 == 1 {
                (h, w)
            } else {
                (w, h)
            };
            let mut segments = crate::engine::compute_scope_frame(&mut computer, &samples, cw, ch);
            // Geometry FX bends the freshly computed beam BEFORE the quarter-turn remap,
            // so it composes with every mode and every rotation lock. Local beams only —
            // remote geometry frames and the resting dot never reach this branch.
            if geom_fx != 0 && geom_amount > 0.0 {
                let dt = geom_last.elapsed().as_secs_f32().clamp(0.0, 0.05);
                geom_phase += dt
                    * match geom_fx {
                        2 => geom_amount * (0.5 + 5.0 * geom_env.min(1.2)), // audio-whipped spin
                        _ => 0.6, // tunnel breathing clock
                    };
                segments = crate::engine::apply_geom_fx(
                    &segments,
                    cw,
                    ch,
                    geom_fx,
                    geom_amount,
                    geom_phase,
                    geom_env,
                );
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
        let retained = match r.retain_frame(&mut encoder, spare_frame.take()) {
            Ok(image) => Some(std::sync::Arc::new(image)),
            Err(e) => {
                log::error!("retain present: {e}");
                std::thread::sleep(std::time::Duration::from_millis(100));
                continue;
            }
        };
        r.composite_into(
            &mut encoder,
            &view,
            (0.0, 0.0, w, h),
            Some(wgpu::Color::BLACK),
        );
        g.queue.submit([encoder.finish()]);
        frame.present();
        if let Some(image) = retained {
            let old = crate::pause::DISPLAY
                .lock()
                .unwrap()
                .commit(frame_token, image);
            spare_frame = old.and_then(|image| std::sync::Arc::try_unwrap(image).ok());
        }

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

fn create_surface(
    instance: &wgpu::Instance,
    window: &Retiring<SendWindow>,
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
    transparent: bool,
) -> Result<(wgpu::SurfaceConfiguration, Vec<wgpu::PresentMode>), String> {
    let caps = surface.get_capabilities(&g.adapter);
    let format = if let Some(format) = g.format {
        if !caps.formats.contains(&format) {
            return Err("surface does not support retained renderer format".into());
        }
        format
    } else {
        caps.formats
            .iter()
            .find(|f| f.is_srgb())
            .copied()
            .or_else(|| caps.formats.first().copied())
            .ok_or("surface exposes no color format")?
    };
    let config = wgpu::SurfaceConfiguration {
        usage: wgpu::TextureUsages::RENDER_ATTACHMENT,
        format,
        width: width.max(1),
        height: height.max(1),
        present_mode: present_mode_for(target_fps, &caps.present_modes),
        alpha_mode: crate::surface_policy::alpha_mode(transparent, &caps.alpha_modes)
            .ok_or("no explicit compositor alpha mode")?,
        view_formats: vec![],
        desired_maximum_frame_latency: 2,
    };
    surface.configure(&g.device, &config);
    Ok((config, caps.present_modes))
}

fn bring_up(
    gpu: &mut Option<Gpu>,
    window: Retiring<SendWindow>,
    width: u32,
    height: u32,
    target_fps: i32,
    transparent: bool,
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
        let mut g = Gpu {
            instance,
            adapter,
            device,
            queue,
            format: None,
        };
        let (config, present_caps) =
            configure(&g, &surface, width, height, target_fps, transparent)?;
        g.format = Some(config.format);
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
    let (config, present_caps) = configure(g, &surface, width, height, target_fps, transparent)?;
    Ok(Active {
        surface,
        config,
        present_caps,
        _window: window,
    })
}
