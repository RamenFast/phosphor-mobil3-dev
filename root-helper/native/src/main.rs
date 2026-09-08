#[cfg(target_os = "android")]
fn main() {
    std::process::exit(platform::run());
}
#[cfg(not(target_os = "android"))]
fn main() {
    eprintln!("Android-only fixed launcher. Fix: use the Gradle debug probe.");
    std::process::exit(2);
}
#[cfg(any(target_os = "android", test))]
#[allow(dead_code)]
mod platform;
