//! All JNI entry points. Nothing here contains logic — it converts, delegates, and logs.

use jni::JNIEnv;
use jni::objects::JClass;
use jni::sys::jstring;
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
        if ack_rx.recv_timeout(std::time::Duration::from_secs(2)).is_err() {
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
) -> jni::sys::jboolean {
    ensure_init();
    let host: String = env.get_string(&host).map(|s| s.into()).unwrap_or_default();
    match crate::remote::connect(&host, port.max(0) as u16) {
        Ok(()) => 1,
        Err(e) => {
            log::error!("remoteConnect: {e}");
            0
        }
    }
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
