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
}
pub static DISPLAY: LazyLock<Mutex<History<RetainedFrame>>> =
    LazyLock::new(|| Mutex::new(History::default()));

#[cfg(target_os = "android")]
fn wake() {
    let _ = crate::render::sender().send(crate::render::Cmd::DisplayDirty);
}
#[cfg(target_os = "android")]
pub fn set_paused(paused: bool) {
    if DISPLAY.lock().unwrap().paused == paused {
        return;
    }
    if !paused {
        crate::render::fresh_visual_ingress();
    }
    let dropped = {
        let mut s = DISPLAY.lock().unwrap();
        if s.paused == paused {
            return;
        }
        if paused {
            s.pin();
            None
        } else {
            s.paused = false;
            s.inspection = Inspection::default();
            s.pinned.take()
        }
    };
    drop(dropped);
    wake();
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
}

#[cfg(target_os = "android")]
pub fn observe_transport(paused: bool) {
    let change = {
        let mut s = DISPLAY.lock().unwrap();
        let old = s.transport_paused.replace(paused);
        old != Some(paused) && (paused || old == Some(true))
    };
    if change {
        set_paused(paused);
    }
}
