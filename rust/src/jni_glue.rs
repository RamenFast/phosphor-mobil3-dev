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
