//! phosphor-mobil3-core — the Rust side of the app: engine glue now, render thread,
//! deck, and sources as the milestones land. Host-testable logic stays cfg-free;
//! everything JNI lives behind `cfg(target_os = "android")` in `jni_glue`.

pub mod bridge_core;
#[cfg(any(target_os = "android", test))]
mod deck_activation;
#[cfg(any(target_os = "android", test))]
mod deck_close;
#[cfg(any(target_os = "android", test))]
mod deck_events;
pub mod engine;
pub mod selftest;
pub mod spsc;

#[cfg(target_os = "android")]
pub mod deck;

#[cfg(target_os = "android")]
pub mod remote;

#[cfg(target_os = "android")]
pub mod render;

#[cfg(target_os = "android")]
mod jni_glue;
