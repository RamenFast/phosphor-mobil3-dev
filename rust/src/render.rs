//! The render thread — owns wgpu (Instance/Adapter/Device), the Android surface, the
//! GpuRenderer, and (for M1) the synthetic feeder through the real DSP.
//!
//! Lifecycle contract (crash class #1 on Android): `surfaceDestroyed` on the Kotlin side
//! BLOCKS until this thread has dropped the wgpu Surface and the NativeWindow ref —
//! Android invalidates the window the moment the callback returns. The GpuRenderer and
//! its decay textures survive across surface loss (the beam remembers backgrounding).

use std::sync::OnceLock;
use std::sync::mpsc;

use ndk::native_window::NativeWindow;
use phosphor_dsp::{Computer, Mode};
use phosphor_proto::settings::Settings;

use crate::engine::Feeder;

pub enum Cmd {
    SurfaceCreated { window: SendWindow, width: u32, height: u32, density: f32 },
    SurfaceChanged { width: u32, height: u32 },
    SurfaceDestroyed { ack: mpsc::SyncSender<()> },
    Paused(bool),
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
    // Held so the ANativeWindow outlives the wgpu Surface built on it. Field order is
    // drop order: Surface first, then the window ref.
    _window: SendWindow,
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
    let mut feeder = Feeder::new();
    let mut fps_frames: u32 = 0;
    let mut fps_t0 = std::time::Instant::now();

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
                    match bring_up(&mut gpu, window, width, height) {
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
                                    }
                                    Err(e) => log::error!("GpuRenderer::new_for_surface: {e}"),
                                },
                            }
                            active = Some(a);
                            feeder.reset();
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
            }
            continue;
        }

        // Draw one frame (FIFO present blocks to vsync — this IS the pacing).
        let (Some(a), Some(g), Some(r)) = (active.as_mut(), gpu.as_ref(), renderer.as_mut())
        else {
            continue;
        };

        let samples = if crate::deck::DECK_ACTIVE.load(std::sync::atomic::Ordering::Relaxed) {
            let s = crate::deck::scope_ring().lock().unwrap().take_stereo_samples();
            feeder.reset(); // demo feeder restarts clean if the deck closes
            s
        } else {
            feeder.frame_samples()
        };
        let w = a.config.width as f32;
        let h = a.config.height as f32;
        let segments = computer.compute(&samples, w, h);
        r.advance(segments);

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

        fps_frames += 1;
        let elapsed = fps_t0.elapsed().as_secs_f64();
        if elapsed >= 1.0 {
            log::info!(
                "fps {:.1} ({} frames, {} segs last frame)",
                fps_frames as f64 / elapsed,
                fps_frames,
                segments.len()
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
) -> wgpu::SurfaceConfiguration {
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
        present_mode: wgpu::PresentMode::Fifo,
        alpha_mode: caps.alpha_modes[0],
        view_formats: vec![],
        desired_maximum_frame_latency: 2,
    };
    surface.configure(&g.device, &config);
    config
}

fn bring_up(
    gpu: &mut Option<Gpu>,
    window: SendWindow,
    width: u32,
    height: u32,
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
        let config = configure(&g, &surface, width, height);
        *gpu = Some(g);
        return Ok(Active { surface, config, _window: window });
    }
    let g = gpu.as_ref().unwrap();
    let surface = create_surface(&g.instance, &window)?;
    let config = configure(g, &surface, width, height);
    Ok(Active { surface, config, _window: window })
}
