//! All JNI entry points. Nothing here contains logic — it converts, delegates, and logs.

use jni::objects::JClass;
use jni::sys::jstring;
use jni::JNIEnv;
use std::sync::Once;

static INIT: Once = Once::new();

fn ensure_init() {
    INIT.call_once(|| {
        android_logger::init_once(
            android_logger::Config::default()
                .with_max_level(log::LevelFilter::Debug)
                .with_tag("phosphor-mobil3"),
        );
        // Panics must land in logcat, not vanish with the process.
        std::panic::set_hook(Box::new(|info| {
            log::error!("rust panic: {info}");
        }));
        log::info!("phosphor-mobil3-core initialized");
    });
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_surfaceCreated(
    mut env: JNIEnv,
    _class: JClass,
    surface: jni::objects::JObject,
    width: jni::sys::jint,
    height: jni::sys::jint,
    density: jni::sys::jfloat,
) {
    ensure_init();
    let window = unsafe {
        ndk::native_window::NativeWindow::from_surface(
            env.get_native_interface().cast(),
            surface.as_raw().cast(),
        )
    };
    let Some(window) = window else {
        log::error!("ANativeWindow_fromSurface returned null");
        return;
    };
    let _ = crate::render::sender().send(crate::render::Cmd::SurfaceCreated {
        window: crate::render::SendWindow(window),
        width: width.max(1) as u32,
        height: height.max(1) as u32,
        density,
    });
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_surfaceChanged(
    _env: JNIEnv,
    _class: JClass,
    width: jni::sys::jint,
    height: jni::sys::jint,
) {
    let _ = crate::render::sender().send(crate::render::Cmd::SurfaceChanged {
        width: width.max(1) as u32,
        height: height.max(1) as u32,
    });
}

/// BLOCKS until the render thread has dropped the wgpu Surface + window ref — Android
/// invalidates the ANativeWindow the moment the Kotlin callback returns.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_surfaceDestroyed(
    _env: JNIEnv,
    _class: JClass,
) {
    let (ack_tx, ack_rx) = std::sync::mpsc::sync_channel(0);
    if crate::render::sender()
        .send(crate::render::Cmd::SurfaceDestroyed { ack: ack_tx })
        .is_ok()
    {
        // 2 s guard: never wedge the UI thread forever if the render thread died.
        if ack_rx
            .recv_timeout(std::time::Duration::from_secs(2))
            .is_err()
        {
            log::error!("surfaceDestroyed ack timeout — render thread unhealthy");
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_setRenderPaused(
    _env: JNIEnv,
    _class: JClass,
    paused: jni::sys::jboolean,
) {
    let _ = crate::render::sender().send(crate::render::Cmd::Paused(paused != 0));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_deckOpen(
    mut env: JNIEnv,
    _class: JClass,
    path: jni::objects::JString,
) -> jni::sys::jboolean {
    ensure_init();
    let path: String = env.get_string(&path).map(|s| s.into()).unwrap_or_default();
    match crate::deck::open(&path) {
        Ok(()) => 1,
        Err(e) => {
            log::error!("deckOpen({path}): {e}");
            0
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_deckToggle(
    _env: JNIEnv,
    _class: JClass,
) -> jni::sys::jboolean {
    crate::deck::toggle() as jni::sys::jboolean
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_deckPositionMs(
    _env: JNIEnv,
    _class: JClass,
) -> jni::sys::jlong {
    (crate::deck::position_micros() / 1000) as jni::sys::jlong
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_deckSetPaused(
    _env: JNIEnv,
    _class: JClass,
    paused: jni::sys::jboolean,
) {
    crate::deck::set_paused(paused != 0);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_deckSeekMs(
    _env: JNIEnv,
    _class: JClass,
    ms: jni::sys::jlong,
) -> jni::sys::jboolean {
    match crate::deck::seek_ms(ms.max(0) as u64) {
        Ok(()) => 1,
        Err(e) => {
            log::error!("deckSeekMs: {e}");
            0
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_deckClose(
    _env: JNIEnv,
    _class: JClass,
) {
    crate::deck::close();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_deckMetadata(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let json = crate::deck::metadata_json();
    match env.new_string(&json) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_deckCoverArt(
    env: JNIEnv,
    _class: JClass,
) -> jni::sys::jbyteArray {
    match crate::deck::cover_art() {
        Some(data) => match env.byte_array_from_slice(&data) {
            Ok(arr) => arr.into_raw(),
            Err(_) => std::ptr::null_mut(),
        },
        None => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_setMode(
    _env: JNIEnv,
    _class: JClass,
    index: jni::sys::jint,
) {
    let _ = crate::render::sender().send(crate::render::Cmd::SetMode(index.max(0) as u8));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_currentMode(
    _env: JNIEnv,
    _class: JClass,
) -> jni::sys::jint {
    crate::render::CURRENT_MODE.load(std::sync::atomic::Ordering::Relaxed) as jni::sys::jint
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_setBeamColor(
    _env: JNIEnv,
    _class: JClass,
    index: jni::sys::jint,
) {
    let _ = crate::render::sender().send(crate::render::Cmd::SetBeamColor(index.max(0) as u8));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_setTargetFps(
    _env: JNIEnv,
    _class: JClass,
    fps: jni::sys::jint,
) {
    let _ = crate::render::sender().send(crate::render::Cmd::SetTargetFps(fps));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_remoteConnect(
    mut env: JNIEnv,
    _class: JClass,
    host: jni::objects::JString,
    port: jni::sys::jint,
    audio: jni::sys::jboolean,
    geometry: jni::sys::jboolean,
) -> jni::sys::jboolean {
    ensure_init();
    let host: String = env.get_string(&host).map(|s| s.into()).unwrap_or_default();
    crate::remote::connect(&host, port.max(0) as u16, audio != 0, geometry != 0)
        as jni::sys::jboolean
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_remoteTransport(
    mut env: JNIEnv,
    _class: JClass,
    cmd: jni::objects::JString,
) {
    let cmd: String = env.get_string(&cmd).map(|s| s.into()).unwrap_or_default();
    crate::remote::transport(&cmd);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_remoteMetadata(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    match env.new_string(crate::remote::metadata_json()) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_remoteDisconnect(
    _env: JNIEnv,
    _class: JClass,
) {
    crate::remote::disconnect();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_setOversample(
    _env: JNIEnv,
    _class: JClass,
    n: jni::sys::jint,
) {
    let _ = crate::render::sender().send(crate::render::Cmd::SetOversample(n.clamp(1, 8) as u8));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_pushCaptureSamples(
    env: JNIEnv,
    _class: JClass,
    samples: jni::objects::JFloatArray,
    count: jni::sys::jint,
) {
    let count = count.max(0) as usize;
    let mut buf = vec![0f32; count];
    if env.get_float_array_region(&samples, 0, &mut buf).is_ok() {
        crate::deck::push_capture(&buf);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_setRingActive(
    _env: JNIEnv,
    _class: JClass,
    active: jni::sys::jboolean,
) {
    ensure_init();
    crate::deck::set_ring_active(active != 0);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_selfTest(
    mut env: JNIEnv,
    _class: JClass,
    files_dir: jni::objects::JString,
) -> jstring {
    ensure_init();
    let dir: String = env
        .get_string(&files_dir)
        .map(|s| s.into())
        .unwrap_or_default();
    let report = crate::selftest::run(&dir);
    log::info!("selftest: {report}");
    match env.new_string(&report) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_engineInfo(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    ensure_init();
    let info = crate::engine::engine_info();
    log::info!("engineInfo: {info}");
    match env.new_string(&info) {
        Ok(s) => s.into_raw(),
        Err(e) => {
            log::error!("engineInfo new_string failed: {e}");
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_setGain(
    _env: JNIEnv,
    _class: JClass,
    gain: jni::sys::jfloat,
) {
    let _ = crate::render::sender().send(crate::render::Cmd::SetGain(gain));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_setGainAuto(
    _env: JNIEnv,
    _class: JClass,
    on: jni::sys::jboolean,
) {
    let _ = crate::render::sender().send(crate::render::Cmd::SetGainAuto(on != 0));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_setGlow(
    _env: JNIEnv,
    _class: JClass,
    persistence: jni::sys::jfloat,
) {
    let _ = crate::render::sender().send(crate::render::Cmd::SetGlow(persistence));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_orbitBy(
    _env: JNIEnv,
    _class: JClass,
    dyaw: jni::sys::jfloat,
    dpitch: jni::sys::jfloat,
) {
    let _ = crate::render::sender().send(crate::render::Cmd::OrbitBy(dyaw, dpitch));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_dollyBy(
    _env: JNIEnv,
    _class: JClass,
    delta: jni::sys::jfloat,
) {
    let _ = crate::render::sender().send(crate::render::Cmd::DollyBy(delta));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_setReducedMotion(
    _env: JNIEnv,
    _class: JClass,
    reduced: jni::sys::jboolean,
) {
    let _ = crate::render::sender().send(crate::render::Cmd::SetReducedMotion(reduced != 0));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_gainNow(
    _env: JNIEnv,
    _class: JClass,
) -> jni::sys::jfloat {
    crate::render::GAIN_MILLI.load(std::sync::atomic::Ordering::Relaxed) as f32 / 1000.0
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_gainAutoNow(
    _env: JNIEnv,
    _class: JClass,
) -> jni::sys::jboolean {
    crate::render::GAIN_AUTO.load(std::sync::atomic::Ordering::Relaxed) as jni::sys::jboolean
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_scopeSilent(
    _env: JNIEnv,
    _class: JClass,
) -> jni::sys::jboolean {
    crate::render::NO_SIGNAL.load(std::sync::atomic::Ordering::Relaxed) as jni::sys::jboolean
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_setFocus(
    _env: JNIEnv,
    _class: JClass,
    focus: jni::sys::jfloat,
) {
    let _ = crate::render::sender().send(crate::render::Cmd::SetFocus(focus));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_setCustomBeam(
    env: JNIEnv,
    _class: JClass,
    rgb: jni::objects::JFloatArray, // 9 floats: 3 slots × RGB (linear 0..1)
    count: jni::sys::jint,
) {
    let mut buf = [0f32; 9];
    if env.get_float_array_region(&rgb, 0, &mut buf).is_err() {
        return;
    }
    let colors = [
        [buf[0], buf[1], buf[2]],
        [buf[3], buf[4], buf[5]],
        [buf[6], buf[7], buf[8]],
    ];
    // Grid follows slot 0 at reduced saturation (the desktop's custom-grid default idea).
    let grid = [buf[0] * 0.85, buf[1] * 0.85, buf[2] * 0.85];
    let _ = crate::render::sender().send(crate::render::Cmd::SetCustomBeam {
        colors,
        count: count.clamp(0, 3) as u8,
        grid,
    });
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_setBeamCycle(
    _env: JNIEnv,
    _class: JClass,
    seconds: jni::sys::jfloat,
    per_track: jni::sys::jboolean,
) {
    let _ = crate::render::sender().send(crate::render::Cmd::SetBeamCycle {
        seconds,
        per_track: per_track != 0,
    });
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_cycleAdvance(
    _env: JNIEnv,
    _class: JClass,
) {
    let _ = crate::render::sender().send(crate::render::Cmd::CycleAdvance);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_beamColorNow(
    _env: JNIEnv,
    _class: JClass,
) -> jni::sys::jint {
    crate::render::BEAM_RGB.load(std::sync::atomic::Ordering::Relaxed) as jni::sys::jint
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_setBeamEnergy(
    _env: JNIEnv,
    _class: JClass,
    energy: jni::sys::jfloat,
) {
    let _ = crate::render::sender().send(crate::render::Cmd::SetBeamEnergy(energy));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_setViewRotation(
    _env: JNIEnv,
    _class: JClass,
    quadrant: jni::sys::jint,
) {
    let _ = crate::render::sender()
        .send(crate::render::Cmd::SetViewRotation((quadrant.rem_euclid(4)) as u8));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_setGeomFx(
    _env: JNIEnv,
    _class: JClass,
    kind: jni::sys::jint,
) {
    let _ = crate::render::sender().send(crate::render::Cmd::SetGeomFx(kind.clamp(0, 4) as u8));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_setGeomAmount(
    _env: JNIEnv,
    _class: JClass,
    amount: jni::sys::jfloat,
) {
    let _ = crate::render::sender().send(crate::render::Cmd::SetGeomAmount(amount));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_setBloomPull(
    _env: JNIEnv,
    _class: JClass,
    pull: jni::sys::jfloat,
) {
    let _ = crate::render::sender().send(crate::render::Cmd::SetBloomPull(pull));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_setGrid(
    _env: JNIEnv,
    _class: JClass,
    on: jni::sys::jboolean,
) {
    let _ = crate::render::sender().send(crate::render::Cmd::SetGrid(on != 0));
}

// ── Bridge v2 surface ────────────────────────────────────────────────────────

fn jstr(env: &JNIEnv, s: String) -> jstring {
    env.new_string(s)
        .map(|x| x.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_remoteStatus(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    jstr(&env, crate::remote::status_json())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_remoteScopeCtl(
    mut env: JNIEnv,
    _class: JClass,
    verb: jni::objects::JString,
    value: jni::objects::JString,
) {
    let verb: String = env.get_string(&verb).map(|s| s.into()).unwrap_or_default();
    let value: String = env.get_string(&value).map(|s| s.into()).unwrap_or_default();
    crate::remote::scope_ctl(&verb, &value);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_remoteSetStreams(
    _env: JNIEnv,
    _class: JClass,
    audio: jni::sys::jboolean,
    geometry: jni::sys::jboolean,
) {
    crate::remote::set_streams(audio != 0, geometry != 0);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_remoteSetMuted(
    _env: JNIEnv,
    _class: JClass,
    muted: jni::sys::jboolean,
) {
    crate::remote::set_muted(muted != 0);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_remoteSetLatencyMode(
    _env: JNIEnv,
    _class: JClass,
    mode: jni::sys::jint,
) {
    // Invalid Kotlin values choose the ear-verified safe policy; do not let an
    // integer narrowing wrap (for example 256 -> tight).
    let mode = match mode {
        0 => 0,
        1 => 1,
        _ => 2,
    };
    crate::remote::set_latency_mode(mode);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_remoteSeekMs(
    _env: JNIEnv,
    _class: JClass,
    ms: jni::sys::jlong,
) {
    crate::remote::seek_ms(ms.max(0) as u64);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_remoteRequestSources(
    _env: JNIEnv,
    _class: JClass,
) {
    crate::remote::request_sources();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_remoteSources(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    jstr(&env, crate::remote::sources_json())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_remoteChooseSource(
    mut env: JNIEnv,
    _class: JClass,
    id: jni::objects::JString,
) {
    let id: String = env.get_string(&id).map(|s| s.into()).unwrap_or_default();
    crate::remote::choose_source(&id);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_remoteBrowse(
    mut env: JNIEnv,
    _class: JClass,
    root: jni::objects::JString,
    path: jni::objects::JString,
) {
    let root: String = env.get_string(&root).map(|s| s.into()).unwrap_or_default();
    let path: String = env.get_string(&path).map(|s| s.into()).unwrap_or_default();
    crate::remote::browse(&root, &path);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_remoteListing(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    jstr(&env, crate::remote::listing_json())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_remotePlayFile(
    mut env: JNIEnv,
    _class: JClass,
    root: jni::objects::JString,
    path: jni::objects::JString,
) {
    let root: String = env.get_string(&root).map(|s| s.into()).unwrap_or_default();
    let path: String = env.get_string(&path).map(|s| s.into()).unwrap_or_default();
    crate::remote::play_file(&root, &path);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_remoteStopFile(
    _env: JNIEnv,
    _class: JClass,
) {
    crate::remote::stop_file();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_remoteRequestArt(
    mut env: JNIEnv,
    _class: JClass,
    id: jni::objects::JString,
) {
    let id: String = env.get_string(&id).map(|s| s.into()).unwrap_or_default();
    crate::remote::request_art(&id);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_remoteArt(
    env: JNIEnv,
    _class: JClass,
) -> jni::sys::jbyteArray {
    match crate::remote::art_bytes() {
        Some(bytes) => match env.byte_array_from_slice(&bytes) {
            Ok(a) => a.into_raw(),
            Err(_) => std::ptr::null_mut(),
        },
        None => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_remoteMetaGeneration(
    _env: JNIEnv,
    _class: JClass,
) -> jni::sys::jint {
    crate::remote::meta_generation() as jni::sys::jint
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_remoteArtGeneration(
    _env: JNIEnv,
    _class: JClass,
) -> jni::sys::jint {
    crate::remote::art_generation() as jni::sys::jint
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_remoteSourcesGeneration(
    _env: JNIEnv,
    _class: JClass,
) -> jni::sys::jint {
    crate::remote::sources_generation() as jni::sys::jint
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_remoteListingGeneration(
    _env: JNIEnv,
    _class: JClass,
) -> jni::sys::jint {
    crate::remote::listing_generation() as jni::sys::jint
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_scopeStats(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let fps = crate::render::FPS_X10.load(std::sync::atomic::Ordering::Relaxed) as f32 / 10.0;
    let segs = crate::render::SEGS_LAST.load(std::sync::atomic::Ordering::Relaxed);
    match env.new_string(format!(r#"{{"fps":{fps:.1},"segs":{segs}}}"#)) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}
