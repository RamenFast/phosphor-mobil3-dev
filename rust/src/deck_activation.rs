use std::sync::atomic::{AtomicU8, Ordering};

const PREPARED: u8 = 0;
const PAUSED: u8 = 1;
const PLAYING: u8 = 2;

/// Media controls can change transport only after the current request publishes the deck.
pub(crate) struct DeckActivation(AtomicU8);

impl DeckActivation {
    pub(crate) fn new(published: bool, paused: bool) -> Self {
        Self(AtomicU8::new(if !published {
            PREPARED
        } else if paused {
            PAUSED
        } else {
            PLAYING
        }))
    }

    pub(crate) fn playing(&self) -> bool {
        self.0.load(Ordering::Acquire) == PLAYING
    }

    pub(crate) fn publish(&self, paused: bool) {
        self.0
            .store(if paused { PAUSED } else { PLAYING }, Ordering::Release);
    }

    pub(crate) fn set_paused(&self, paused: bool) {
        let _ = self
            .0
            .fetch_update(Ordering::AcqRel, Ordering::Acquire, |state| {
                (state != PREPARED).then_some(if paused { PAUSED } else { PLAYING })
            });
    }

    pub(crate) fn toggle(&self) -> bool {
        self.0
            .fetch_update(Ordering::AcqRel, Ordering::Acquire, |state| match state {
                PREPARED => None,
                PAUSED => Some(PLAYING),
                _ => Some(PAUSED),
            })
            .is_ok_and(|previous| previous == PAUSED)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn prepared_deck_ignores_old_media_controls() {
        let gate = DeckActivation::new(false, true);
        gate.set_paused(false);
        assert!(!gate.playing());
        assert!(!gate.toggle());
        assert!(!gate.playing());
        gate.set_paused(true);
        assert!(!gate.toggle());
    }

    #[test]
    fn only_explicit_publication_can_authorize_prepared_audio() {
        let gate = DeckActivation::new(false, true);
        gate.publish(false);
        assert!(gate.playing());
        gate.set_paused(true);
        assert!(!gate.playing());
        gate.set_paused(false);
        assert!(gate.playing());
    }

    #[test]
    fn paused_publication_preserves_user_intent() {
        let gate = DeckActivation::new(false, true);
        gate.publish(true);
        assert!(!gate.playing());
        assert!(gate.toggle());
        assert!(gate.playing());
        assert!(!gate.toggle());
        assert!(!gate.playing());
    }

    #[test]
    fn a_replacement_never_inherits_old_authorization() {
        let old = DeckActivation::new(true, false);
        let replacement = DeckActivation::new(false, true);
        assert!(old.playing());
        old.set_paused(false);
        assert!(!replacement.playing());
    }
}
