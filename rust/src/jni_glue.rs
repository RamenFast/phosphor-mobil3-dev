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
