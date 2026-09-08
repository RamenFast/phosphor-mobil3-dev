//! CPU ownership of the last application present. No driver calls under this lock.
use phosphor_render_gpu::{Inspection, RetainedFrame};
use std::sync::{Arc, LazyLock, Mutex};

pub static VISUAL_EPOCH: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(0);
pub fn visual_epoch() -> u64 {
    VISUAL_EPOCH.load(std::sync::atomic::Ordering::Acquire)
}

pub struct History<T> {
    pub generation: u64,
    pub serial: u64,
    pub paused: bool,
    pub black: bool,
    pub transport_paused: Option<bool>,
    pub inspection: Inspection,
    pub committed: Option<Arc<T>>,
    pub pinned: Option<Arc<T>>,
}
impl<T> Default for History<T> {
    fn default() -> Self {
        Self {
            generation: 0,
            serial: 0,
            paused: false,
            black: false,
            transport_paused: None,
            inspection: Inspection::default(),
            committed: None,
            pinned: None,
        }
    }
}
impl<T> History<T> {
    pub fn pin(&mut self) {
        if !self.paused {
            self.pinned = self.committed.clone();
            self.paused = true;
        }
    }
    pub fn commit(&mut self, generation: u64, image: Arc<T>) -> Option<Arc<T>> {
        if generation != self.generation || self.paused {
            return Some(image);
        }
        self.serial = self.serial.wrapping_add(1);
        self.committed.replace(image)
    }
    pub fn transition(
        &mut self,
        generation: Option<u64>,
        paused: bool,
        observation: bool,
        live: bool,
    ) -> (bool, Option<Arc<T>>) {
        if !live || generation.is_some_and(|g| g != self.generation) {
            return (false, None);
        }
        if observation {
            let previous = self.transport_paused.replace(paused);
            if previous.is_none() || previous == Some(paused) {
                return (false, None);
            }
        }
        if self.paused == paused {
            return (false, None);
        }
        if paused {
            self.pin();
            (true, None)
        } else {
            self.paused = false;
            self.inspection = Inspection::default();
            (true, self.pinned.take())
        }
    }
}
pub static DISPLAY: LazyLock<Mutex<History<RetainedFrame>>> =
    LazyLock::new(|| Mutex::new(History::default()));

#[cfg(target_os = "android")]
fn wake() {
    let _ = crate::render::sender().send(crate::render::Cmd::DisplayDirty);
}
#[cfg(target_os = "android")]
pub fn set_paused(paused: bool) {
    apply_transition(None, paused, false, || true);
}

#[cfg(target_os = "android")]
fn apply_transition(
    generation: Option<u64>,
    paused: bool,
    observation: bool,
    live: impl FnOnce() -> bool,
) {
    let (changed, dropped) = crate::render::with_stereo_window(|ring, meter| {
        let mut s = DISPLAY.lock().unwrap();
        let result = s.transition(generation, paused, observation, live());
        if result.0 && !paused {
            VISUAL_EPOCH.fetch_add(1, std::sync::atomic::Ordering::AcqRel);
            ring.clear_pending();
            meter.clear_visual_measurement();
        }
        result
    });
    drop(dropped);
    if changed {
        if !paused {
            crate::render::finish_visual_reset();
        }
        wake();
    }
}
#[cfg(target_os = "android")]
pub fn invalidate() {
    crate::render::fresh_visual_ingress();
    let old = {
        let mut s = DISPLAY.lock().unwrap();
        let mut next = History::default();
        next.generation = s.generation.wrapping_add(1);
        next.black = s.black;
        std::mem::replace(&mut *s, next)
    };
    drop(old);
    wake();
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn pause_pins_committed_not_later_submission() {
        let mut h = History::default();
        h.commit(0, Arc::new("A"));
        h.pin();
        assert_eq!(h.commit(0, Arc::new("B")).as_deref(), Some(&"B"));
        assert_eq!(h.pinned.as_deref(), Some(&"A"));
        assert_eq!(h.serial, 1);
        h.black = true;
        h.black = false;
        assert_eq!(h.pinned.as_deref(), Some(&"A"));
    }
    #[test]
    fn stale_source_cannot_publish() {
        let mut h = History::default();
        h.generation = 4;
        assert!(h.commit(3, Arc::new("old")).is_some());
        assert!(h.committed.is_none());
        h.pin();
        assert!(h.pinned.is_none());
    }
    #[test]
    fn initial_transport_observation_is_not_intentional_pause() {
        for initial in [true, false] {
            let mut h = History::default();
            h.commit(0, Arc::new("A"));
            assert!(!h.transition(Some(0), initial, true, true).0);
            assert!(!h.paused);
            assert!(h.pinned.is_none());
            assert_eq!(h.transport_paused, Some(initial));
        }
    }
    #[test]
    fn only_observed_edges_change_display_pause() {
        let mut h = History::default();
        h.commit(0, Arc::new("A"));
        h.transition(Some(0), false, true, true);
        assert!(h.transition(Some(0), true, true, true).0);
        assert_eq!(h.pinned.as_deref(), Some(&"A"));
        assert!(!h.transition(Some(0), true, true, true).0);
        let (changed, dropped) = h.transition(Some(0), false, true, true);
        assert!(changed);
        assert_eq!(dropped.as_deref(), Some(&"A"));
        assert!(!h.paused);
        assert!(h.pinned.is_none());
    }
    #[test]
    fn old_generation_and_retired_session_cannot_change_new_history() {
        let mut h = History::default();
        h.generation = 5;
        h.commit(5, Arc::new("new"));
        h.transition(Some(5), false, true, true);
        assert!(!h.transition(Some(4), true, true, true).0);
        assert!(!h.transition(Some(5), true, true, false).0);
        assert_eq!(h.transport_paused, Some(false));
        assert!(!h.paused);
        assert!(h.pinned.is_none());
        assert!(h.transition(Some(5), true, true, true).0);
        assert_eq!(h.pinned.as_deref(), Some(&"new"));
    }
    #[test]
    fn initial_observation_preserves_an_explicit_display_pause() {
        let mut h = History::default();
        h.commit(0, Arc::new("A"));
        assert!(h.transition(None, true, false, true).0);
        assert!(!h.transition(Some(0), false, true, true).0);
        assert!(h.paused);
        assert_eq!(h.pinned.as_deref(), Some(&"A"));
    }
}

#[cfg(target_os = "android")]
pub fn observe_transport(paused: bool) {
    apply_transition(None, paused, true, || true);
}

#[cfg(target_os = "android")]
pub fn source_generation() -> u64 {
    DISPLAY.lock().unwrap().generation
}

#[cfg(target_os = "android")]
pub fn observe_transport_from(generation: u64, paused: bool, live: impl FnOnce() -> bool) {
    apply_transition(Some(generation), paused, true, live);
}
