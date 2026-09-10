//! All JNI entry points. Nothing here contains logic — it converts, delegates, and logs.

use jni::JNIEnv;
use jni::objects::JClass;
use jni::sys::jstring;
use std::sync::Once;

static INIT: Once = Once::new();
static WINDOWS: std::sync::Mutex<Vec<crate::surface_lifecycle::Retirement>> =
    std::sync::Mutex::new(Vec::new());
static INSTRUMENT_REQUESTS: std::sync::LazyLock<std::sync::Mutex<crate::instrument::RequestBook>> =
    std::sync::LazyLock::new(|| std::sync::Mutex::new(crate::instrument::RequestBook::default()));

fn instrument_outcome(outcome: crate::instrument::Outcome) -> jni::sys::jint {
    match outcome {
        crate::instrument::Outcome::Pending => 0,
        crate::instrument::Outcome::Committed => 1,
        crate::instrument::Outcome::Cancelled => 2,
        crate::instrument::Outcome::Rejected => 3,
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_requestInstrument(
    mut env: JNIEnv, _class: JClass, json: jni::objects::JString,
) -> jni::sys::jlong {
    let Ok(length) = env.call_method(&json, "length", "()I", &[]).and_then(|v| v.i()) else { return -1; };
    if length < 0 || length as usize > crate::instrument::MAX_SETUP_BYTES { return -1; }
    let Ok(text) = env.get_string(&json) else { return -1; };
    let text: String = text.into();
    let Ok(setup) = crate::instrument::Setup::decode(&text) else { return -1; };
    let deadline = std::time::Instant::now() + std::time::Duration::from_millis(750);
    let Ok((id, request)) = INSTRUMENT_REQUESTS.lock().unwrap().reserve(setup, deadline) else { return -2; };
    if crate::render::sender().send(crate::render::Cmd::ApplyInstrument(request)).is_err() {
        INSTRUMENT_REQUESTS.lock().unwrap().release(id);
        return -3;
    }
    id as jni::sys::jlong
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_awaitInstrument(
    _env: JNIEnv, _class: JClass, id: jni::sys::jlong,
) -> jni::sys::jint {
    let request = INSTRUMENT_REQUESTS.lock().unwrap().get(id as u64);
    request.map(|request| instrument_outcome(request.wait())).unwrap_or(4)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_cancelInstrument(
    _env: JNIEnv, _class: JClass, id: jni::sys::jlong,
) -> jni::sys::jint {
    let request = INSTRUMENT_REQUESTS.lock().unwrap().get(id as u64);
    request.map(|request| instrument_outcome(request.cancel())).unwrap_or(4)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_releaseInstrument(
    _env: JNIEnv, _class: JClass, id: jni::sys::jlong,
) -> jni::sys::jboolean {
    INSTRUMENT_REQUESTS.lock().unwrap().release(id as u64) as jni::sys::jboolean
}

fn retire_surface() -> bool {
    let mut retirements = std::mem::take(&mut *WINDOWS.lock().unwrap());
    for life in &retirements { life.cancel(); }
    let (ack, result) = std::sync::mpsc::sync_channel(0);
    let sent = crate::render::sender()
        .send(crate::render::Cmd::SurfaceDestroyed { ack })
        .is_ok();
    // Channel loss is not acknowledgement. The separate resource signal still proves drop.
    let clean = match retirements.pop() {
        Some(life) => life.barrier(result),
        None => result.recv().is_ok(),
    };
    for life in retirements { life.wait(); }
    sent && clean
}

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
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_attachSurface(
    env: JNIEnv,
    _class: JClass,
    surface: jni::objects::JObject,
    width: jni::sys::jint,
    height: jni::sys::jint,
    density: jni::sys::jfloat,
    transparent: jni::sys::jboolean,
) -> jni::sys::jint {
    ensure_init();
    let window = unsafe {
        ndk::native_window::NativeWindow::from_surface(
            env.get_native_interface().cast(),
            surface.as_raw().cast(),
        )
    };
    let Some(window) = window else {
        log::error!("ANativeWindow_fromSurface returned null");
        return -1;
    };
    let window = crate::surface_lifecycle::Retiring::new(crate::render::SendWindow(window));
    let retirement = window.retirement.clone();
    {
        let mut windows = WINDOWS.lock().unwrap();
        windows.retain(|life| !life.dropped());
        windows.push(retirement.clone());
    }
    let (ack, result) = std::sync::mpsc::sync_channel(1);
    let _ = crate::render::sender().send(crate::render::Cmd::SurfaceCreated {
        window,
        width: width.max(1) as u32,
        height: height.max(1) as u32,
        density,
        transparent: transparent != 0,
        ack,
    });
    match retirement.attached(result, std::time::Duration::from_secs(2)) {
        Some(mode) => mode,
        _ => {
            // A deadline cancels publication, not resource ownership. A driver stall
            // can delay this required window barrier beyond the UI's deadline.
            retire_surface();
            -1
        }
    }
}

/// BLOCKS until the render thread has dropped the wgpu Surface + window ref — Android
/// invalidates the ANativeWindow the moment the Kotlin callback returns.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_surfaceDestroyed(
    _env: JNIEnv,
    _class: JClass,
) -> jni::sys::jboolean {
    u8::from(retire_surface())
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
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_deckValidate(
    mut env: JNIEnv,
    _class: JClass,
    path: jni::objects::JString,
) -> jni::sys::jboolean {
    ensure_init();
    let path: String = env.get_string(&path).map(|s| s.into()).unwrap_or_default();
    match crate::deck::validate(&path) {
        Ok(()) => 1,
        Err(error) => {
            log::warn!("deckValidate({path}): {error}");
            0
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_deckOpen(
    mut env: JNIEnv,
    _class: JClass,
    path: jni::objects::JString,
) -> jni::sys::jboolean {
    ensure_init();
    let path: String = env.get_string(&path).map(|s| s.into()).unwrap_or_default();
    match crate::deck::prepare(&path) {
        Ok(()) => 1,
        Err(e) => {
            log::error!("deckOpen({path}): {e}");
            0
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_deckPublish(
    _env: JNIEnv,
    _class: JClass,
    paused: jni::sys::jboolean,
) {
    crate::deck::publish(paused != 0);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_deckOpenIdentity(
    _env: JNIEnv,
    _class: JClass,
) -> jni::sys::jlong {
    crate::deck::open_identity() as jni::sys::jlong
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_confirmLocalItem(
    _env: JNIEnv,
    _class: JClass,
    open_id: jni::sys::jlong,
) {
    if open_id > 0 {
        let _ = crate::render::sender().send(crate::render::Cmd::NewLocalItem(open_id as u64));
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
    match crate::deck::prepare_seek_ms(ms.max(0) as u64) {
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
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_deckPollEvent(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    crate::deck::poll_event_json()
        .and_then(|json| env.new_string(json).ok())
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
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
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_captureReadEpoch(
    _env: JNIEnv,
    _class: JClass,
) -> jni::sys::jlong {
    crate::pause::visual_epoch() as jni::sys::jlong
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_pushCaptureRead(
    env: JNIEnv,
    _class: JClass,
    samples: jni::objects::JFloatArray,
    count: jni::sys::jint,
    owner: jni::sys::jlong,
    read_epoch: jni::sys::jlong,
) {
    let Ok(length) = env.get_array_length(&samples) else { return; };
    if count <= 0 || count > length { return; }
    let mut buf = vec![0f32; count as usize];
    if env.get_float_array_region(&samples, 0, &mut buf).is_ok() {
        crate::engine::publish_capture_read(
            crate::deck::scope_ring(), &crate::render::RAW_STEREO,
            &crate::deck::DECK_ACTIVE, &crate::pause::VISUAL_EPOCH,
            owner as u64, read_epoch as u64, &buf,
        );
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_setRingActive(
    _env: JNIEnv,
    _class: JClass,
    active: jni::sys::jboolean,
) -> jni::sys::jlong {
    ensure_init();
    crate::deck::set_ring_active(active != 0) as jni::sys::jlong
}

#[cfg(debug_assertions)]
#[path = "../../root-helper/native/src/signal.rs"]
mod root_signal;

/// Debug-only, read-only fixed 0.5s aggregate. Caller proves fresh generation and ingress first.
#[cfg(debug_assertions)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_RootCaptureChecks_nativeSnapshot(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let ring = crate::deck::scope_ring().lock().unwrap();
    let rate = ring.sample_rate();
    let history = ring.copy_history(0.5);
    let summary = root_signal::summarize(&history);
    let json = serde_json::json!({"sample_rate":rate, "frames":summary.frames, "rms":summary.rms,
        "frequency_hz":summary.frequency, "duplicated_mono":summary.duplicated});
    env.new_string(json.to_string()).map(|s|s.into_raw()).unwrap_or(std::ptr::null_mut())
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
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_setLight(
    env: JNIEnv, _class: JClass, rgb: jni::objects::JFloatArray,
    mask: jni::sys::jint, preset: jni::sys::jint, seconds: jni::sys::jfloat,
    track: jni::sys::jboolean, generated: jni::sys::jboolean,
    shuffle: jni::sys::jboolean, random: jni::sys::jboolean,
    min: jni::sys::jfloat, max: jni::sys::jfloat,
    deleted: jni::sys::jint,
) -> jni::sys::jboolean {
    let Ok(len) = env.get_array_length(&rgb) else { return 0; };
    if !(0..=18).contains(&len) || len % 3 != 0 || !(0..=63).contains(&mask) || !(0..=8).contains(&preset) {
        return 0;
    }
    let mut buf = vec![0f32; len as usize];
    if env.get_float_array_region(&rgb, 0, &mut buf).is_err() { return 0; }
    let settings = crate::light_cycle::LightSettings {
        colors: buf.chunks_exact(3).map(|v| [v[0], v[1], v[2]]).collect(),
        selected_mask: mask as u8, preset: preset as u8, seconds, per_track: track != 0,
        generated_auto: generated != 0, shuffle: shuffle != 0, random_interval: random != 0,
        interval_min: min, interval_max: max,
    };
    if !settings.valid() || !(-1..=5).contains(&deleted) { return 0; }
    let deleted = (deleted >= 0).then_some(deleted as usize);
    crate::render::sender().send(crate::render::Cmd::SetLight(settings, deleted)).is_ok() as jni::sys::jboolean
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_rollLight(
    _env: JNIEnv, _class: JClass,
) -> jni::sys::jboolean {
    crate::render::sender().send(crate::render::Cmd::RollLight).is_ok() as jni::sys::jboolean
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
    let _ = crate::render::sender().send(crate::render::Cmd::SetViewRotation(
        (quadrant.rem_euclid(4)) as u8,
    ));
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
    let stats = serde_json::json!({
        "fps": fps, "segs": segs, "grid_data": crate::render::take_stereo_stats(),
    });
    match env.new_string(stats.to_string()) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_signalObservation(
    env: JNIEnv,
    _cls: JClass,
) -> jstring {
    let scope = crate::render::RAW_STEREO.try_lock().ok().map(|meter| meter.signal_snapshot());
    let value = serde_json::json!({"scope": scope, "local": crate::deck::signal_json(),
        "relay": crate::remote::signal_json()});
    env.new_string(value.to_string()).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_setDisplayPaused(
    _env: JNIEnv,
    _class: JClass,
    paused: jni::sys::jboolean,
) {
    crate::pause::set_paused(paused != 0);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_invalidateHeldFrame(
    _env: JNIEnv,
    _class: JClass,
) {
    crate::pause::invalidate();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_displayPauseState(
    _env: JNIEnv,
    _class: JClass,
) -> jni::sys::jint {
    let s = crate::pause::DISPLAY.lock().unwrap();
    i32::from(s.paused) | (i32::from(s.black) << 1) | (i32::from(s.pinned.is_some()) << 2)
        | (i32::from(s.present_pending()) << 3)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_setPauseBlack(
    _env: JNIEnv,
    _class: JClass,
    black: jni::sys::jboolean,
) {
    crate::pause::DISPLAY.lock().unwrap().black = black != 0;
    let _ = crate::render::sender().send(crate::render::Cmd::DisplayDirty);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_hdrObservation(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    match env.new_string(crate::render::hdr_report()) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_setHdrRequested(
    _env: JNIEnv,
    _class: JClass,
    requested: jni::sys::jboolean,
    api: jni::sys::jint,
) {
    crate::render::set_hdr_requested(requested != 0, api);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_inspectHeld(
    _env: JNIEnv,
    _class: JClass,
    dx: f32,
    dy: f32,
    scale: f32,
    reset: jni::sys::jboolean,
) {
    {
        let mut s = crate::pause::DISPLAY.lock().unwrap();
        if !s.paused {
            return;
        }
        s.inspection = if reset != 0 {
            Default::default()
        } else {
            phosphor_render_gpu::Inspection {
                zoom: s.inspection.zoom * scale,
                pan: [s.inspection.pan[0] + dx, s.inspection.pan[1] + dy],
            }
            .bounded()
        };
    }
    let _ = crate::render::sender().send(crate::render::Cmd::DisplayDirty);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_phosphor_mobil3_PhosphorNative_observeTransportPaused(
    _env: JNIEnv,
    _class: JClass,
    paused: jni::sys::jboolean,
) {
    crate::pause::observe_transport(paused != 0);
}
