//! CPU ownership of the last application present. No driver calls under this lock.
use phosphor_render_gpu::{Inspection, RetainedFrame};
use std::sync::{Arc, LazyLock, Mutex};

pub static VISUAL_EPOCH: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(0);
pub fn visual_epoch() -> u64 {
    VISUAL_EPOCH.load(std::sync::atomic::Ordering::Acquire)
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct FrameToken {
    pub generation: u64,
    pub visual_revision: u64,
}

/// The render owner acknowledges only after clearing both live energy sides.
#[derive(Default)]
pub struct EnergyEpoch(Option<FrameToken>);
impl EnergyEpoch {
    pub fn needs_clear(&self, token: FrameToken) -> bool {
        self.0 != Some(token)
    }
    pub fn cleared(&mut self, token: FrameToken) {
        self.0 = Some(token);
    }
}

pub struct History<T> {
    pub generation: u64,
    pub visual_revision: u64,
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
            visual_revision: 0,
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
    pub fn frame_token(&self) -> FrameToken {
        FrameToken {
            generation: self.generation,
            visual_revision: self.visual_revision,
        }
    }
    pub fn retire(&mut self) -> Self {
        let next = Self {
            generation: self.generation.wrapping_add(1),
            visual_revision: self.visual_revision.wrapping_add(1),
            black: self.black,
            ..Self::default()
        };
        std::mem::replace(self, next)
    }
    pub fn pin(&mut self) {
        if !self.paused {
            self.pinned = self.committed.clone();
            self.paused = true;
        }
    }
    pub fn commit(&mut self, token: FrameToken, image: Arc<T>) -> Option<Arc<T>> {
        if token != self.frame_token() || self.paused {
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
            self.visual_revision = self.visual_revision.wrapping_add(1);
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
            VISUAL_EPOCH.store(s.visual_revision, std::sync::atomic::Ordering::Release);
            ring.clear_pending();
            meter.clear_visual_measurement();
        }
        result
    });
    drop(dropped);
    if changed {
        wake();
    }
}
#[cfg(target_os = "android")]
pub fn invalidate() {
    let old = crate::render::with_stereo_window(|ring, meter| {
        let mut s = DISPLAY.lock().unwrap();
        let old = s.retire();
        VISUAL_EPOCH.store(s.visual_revision, std::sync::atomic::Ordering::Release);
        ring.clear_pending();
        meter.clear_visual_measurement();
        old
    });
    drop(old);
    wake();
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn pause_pins_committed_not_later_submission() {
        let mut h = History::default();
        h.commit(h.frame_token(), Arc::new("A"));
        h.pin();
        assert_eq!(
            h.commit(h.frame_token(), Arc::new("B")).as_deref(),
            Some(&"B")
        );
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
        assert!(
            h.commit(
                FrameToken {
                    generation: 3,
                    visual_revision: 0
                },
                Arc::new("old")
            )
            .is_some()
        );
        assert!(h.committed.is_none());
        h.pin();
        assert!(h.pinned.is_none());
    }
    #[test]
    fn initial_transport_observation_is_not_intentional_pause() {
        for initial in [true, false] {
            let mut h = History::default();
            h.commit(h.frame_token(), Arc::new("A"));
            assert!(!h.transition(Some(0), initial, true, true).0);
            assert!(!h.paused);
            assert!(h.pinned.is_none());
            assert_eq!(h.transport_paused, Some(initial));
        }
    }
    #[test]
    fn only_observed_edges_change_display_pause() {
        let mut h = History::default();
        h.commit(h.frame_token(), Arc::new("A"));
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
        h.commit(h.frame_token(), Arc::new("new"));
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
        h.commit(h.frame_token(), Arc::new("A"));
        assert!(h.transition(None, true, false, true).0);
        assert!(!h.transition(Some(0), false, true, true).0);
        assert!(h.paused);
        assert_eq!(h.pinned.as_deref(), Some(&"A"));
    }

    fn in_flight_frame(retire: bool) {
        use std::sync::mpsc;
        use std::time::Duration;
        let history = Arc::new(Mutex::new(History::default()));
        {
            let mut h = history.lock().unwrap();
            let token = h.frame_token();
            h.commit(token, Arc::new("presented A"));
        }
        let (snapshot_tx, snapshot_rx) = mpsc::sync_channel(1);
        let (release_tx, release_rx) = mpsc::sync_channel(1);
        let render_history = history.clone();
        let render = std::thread::spawn(move || {
            let token = render_history.lock().unwrap().frame_token();
            snapshot_tx.send(token).unwrap();
            release_rx.recv_timeout(Duration::from_secs(2)).unwrap();
            render_history
                .lock()
                .unwrap()
                .commit(token, Arc::new("in-flight stale A"))
        });
        let old = snapshot_rx.recv_timeout(Duration::from_secs(2)).unwrap();
        let current = {
            let mut h = history.lock().unwrap();
            if retire {
                drop(h.retire());
            } else {
                h.transition(None, true, false, true);
                h.transition(None, false, false, true);
            }
            h.frame_token()
        };
        assert_ne!(old.visual_revision, current.visual_revision);
        assert_eq!(old.generation != current.generation, retire);
        release_tx.send(()).unwrap();
        assert_eq!(
            render.join().unwrap().as_deref(),
            Some(&"in-flight stale A")
        );
        let mut energy = EnergyEpoch::default();
        energy.cleared(old);
        assert!(energy.needs_clear(current));
        energy.cleared(current);
        assert!(!energy.needs_clear(current));
        let mut h = history.lock().unwrap();
        h.commit(current, Arc::new("fresh B"));
        h.pin();
        assert_eq!(h.pinned.as_deref(), Some(&"fresh B"));
    }

    #[test]
    fn render_snapshot_before_hold_resume_cannot_commit_after_resume() {
        in_flight_frame(false);
    }

    #[test]
    fn render_snapshot_before_source_retirement_cannot_commit_to_replacement() {
        in_flight_frame(true);
    }

    #[test]
    fn energy_reset_tracks_repeated_resume_and_retirement_not_pause_or_inspection() {
        let mut h = History::<()>::default();
        let mut energy = EnergyEpoch::default();
        for _ in 0..3 {
            let token = h.frame_token();
            assert!(energy.needs_clear(token));
            energy.cleared(token);
            h.pin();
            assert!(!energy.needs_clear(h.frame_token()));
            h.black = !h.black;
            assert!(!energy.needs_clear(h.frame_token()));
            h.transition(None, false, false, true);
            assert!(energy.needs_clear(h.frame_token()));
        }
        let token = h.frame_token();
        energy.cleared(token);
        drop(h.retire());
        assert!(energy.needs_clear(h.frame_token()));
        assert!(h.committed.is_none() && h.pinned.is_none());
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
