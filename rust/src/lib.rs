//! phosphor-mobil3-core — the Rust side of the app: engine glue now, render thread,
//! deck, and sources as the milestones land. Host-testable logic stays cfg-free;
//! everything JNI lives behind `cfg(target_os = "android")` in `jni_glue`.

pub mod engine;
pub mod selftest;

#[cfg(target_os = "android")]
pub mod deck;

#[cfg(target_os = "android")]
pub mod remote;

#[cfg(target_os = "android")]
pub mod render;

#[cfg(target_os = "android")]
mod jni_glue;
