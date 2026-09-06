use phosphor_audio::AudioEvent;
use phosphor_audio::playback::AudibleRing;
use std::sync::atomic::{AtomicU8, Ordering};
use std::sync::{Mutex, mpsc};

use crate::deck_activation::DeckActivation;

/// A seek reopens the decoder, so paths cannot identify a native publication.
pub(crate) struct LocalOpen {
    pub(crate) id: u64,
    published: bool,
}

impl LocalOpen {
    pub(crate) fn new(published: bool) -> Self {
        static NEXT: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(1);
        Self {
            id: NEXT.fetch_add(1, Ordering::Relaxed),
            published,
        }
    }
    pub(crate) fn publish(&mut self) {
        self.published = true;
    }
    pub(crate) fn accepts(&self, id: u64) -> bool {
        self.published && id != 0 && id == self.id
    }
}

const EOF: u8 = 1;
const DRAINED: u8 = 2;
const OUTPUT_ERROR: u8 = 4;

/// Per-deck output truth. The callback adds only atomic operations to the ring path.
#[derive(Default)]
pub(crate) struct DeckTerminal(AtomicU8);

impl DeckTerminal {
    pub(crate) fn allows_item_confirmation(&self) -> bool {
        self.event().is_none()
    }

    fn decoder_ended(&self) {
        self.0.fetch_or(EOF, Ordering::Release);
    }

    pub(crate) fn output_error(&self) {
        self.0.fetch_or(OUTPUT_ERROR, Ordering::Release);
    }

    pub(crate) fn pop_output(
        &self,
        audible: &AudibleRing,
        activation: &DeckActivation,
        out: &mut [f32],
    ) -> usize {
        if !activation.playing() {
            return 0;
        }
        // Observe EOF before the pop. An earlier underrun cannot prove the final tail empty.
        let ended = self.0.load(Ordering::Acquire) & EOF != 0;
        let got = audible.pop_into(out);
        if ended && got == 0 && !out.is_empty() {
            self.0.fetch_or(DRAINED, Ordering::Release);
        }
        got
    }

    fn event(&self) -> Option<&'static str> {
        let state = self.0.load(Ordering::Acquire);
        if state & OUTPUT_ERROR != 0 {
            Some("output_error")
        } else if state & DRAINED != 0 {
            Some("playback_drained")
        } else {
            None
        }
    }
}

/// The deck owns this receiver. Empty, contended and disconnected polls never wait.
pub(crate) fn poll_event_json(deck: &Mutex<Option<impl EventSource>>) -> Option<String> {
    let guard = deck.try_lock().ok()?;
    let source = guard.as_ref()?;
    if let Some(event) = source.terminal().event() {
        return Some(
            serde_json::json!({"event": event, "path": source.path(), "open_id": source.open_id()})
                .to_string(),
        );
    }
    let event = source.events().try_recv().ok()?;
    match event {
        AudioEvent::TrackStarted { path } => Some(
            serde_json::json!({
                "event": "track_started", "path": path, "open_id": source.open_id(),
            })
            .to_string(),
        ),
        AudioEvent::PlaybackEnded => {
            source.terminal().decoder_ended();
            Some(
                serde_json::json!({
                    "event": "playback_ended", "path": source.path(), "open_id": source.open_id(),
                })
                .to_string(),
            )
        }
        _ => None,
    }
}

pub(crate) trait EventSource {
    fn events(&self) -> &mpsc::Receiver<AudioEvent>;
    fn path(&self) -> &str;
    fn open_id(&self) -> u64;
    fn terminal(&self) -> &DeckTerminal;
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn auto_gain_native_identity_rejects_prepared_replaced_and_delayed_commands() {
        let mut old = LocalOpen::new(false);
        let mut gain = crate::engine::AutoGain::new(1.0);
        gain.set_auto(true, 1.0);
        gain.update(0.8);
        assert!(!old.accepts(old.id));
        old.publish(); // Paused publication also has a real identity, without claiming output flow.
        assert!(old.accepts(old.id));
        let mut replacement = LocalOpen::new(false);
        assert_ne!(old.id, replacement.id);
        assert!(!replacement.accepts(old.id));
        assert!(!replacement.accepts(replacement.id));
        replacement.publish();
        assert!(!replacement.accepts(0));
        assert!(!replacement.accepts(old.id));
        assert!(replacement.accepts(replacement.id));
        let terminal = DeckTerminal::default();
        assert!(terminal.allows_item_confirmation());
        terminal.output_error();
        assert!(!terminal.allows_item_confirmation());
        if replacement.accepts(old.id) {
            panic!("retired proof accepted");
        }
        gain.new_local_item(replacement.id, replacement.id);
        let next = gain.update(0.02).unwrap();
        assert!(next > 1.0); // New quiet item uses its own target rather than the retired loud peak.
        let deck = include_str!("deck.rs");
        let guarded = deck
            .split("fn with_published_open(")
            .nth(1)
            .unwrap()
            .split("// PlayerSession")
            .next()
            .unwrap();
        assert!(guarded.contains("let guard = DECK.lock().unwrap()"));
        assert!(guarded.contains("deck.open.accepts(id)"));
        assert!(guarded.contains("deck.terminal.allows_item_confirmation()"));
        assert!(
            include_str!("render.rs")
                .contains("with_published_open(id, || auto_gain.new_local_item(id, id))")
        );
    }

    #[test]
    fn auto_gain_track_started_and_terminal_events_carry_native_open_identity() {
        struct Identified(mpsc::Receiver<AudioEvent>, DeckTerminal, LocalOpen);
        impl EventSource for Identified {
            fn events(&self) -> &mpsc::Receiver<AudioEvent> {
                &self.0
            }
            fn path(&self) -> &str {
                "/same.flac"
            }
            fn open_id(&self) -> u64 {
                self.2.id
            }
            fn terminal(&self) -> &DeckTerminal {
                &self.1
            }
        }
        let mut identities = Vec::new();
        for _ in 0..2 {
            let (tx, rx) = mpsc::channel();
            let identity = LocalOpen::new(false);
            let id = identity.id;
            identities.push(id);
            let source = Mutex::new(Some(Identified(rx, DeckTerminal::default(), identity)));
            for event in [
                AudioEvent::TrackStarted {
                    path: "/same.flac".into(),
                },
                AudioEvent::PlaybackEnded,
            ] {
                tx.send(event).unwrap();
                let json: serde_json::Value =
                    serde_json::from_str(&poll_event_json(&source).unwrap()).unwrap();
                assert_eq!(json["open_id"].as_u64(), Some(id));
                assert_eq!(json["path"], "/same.flac");
            }
            source.lock().unwrap().as_ref().unwrap().1.output_error();
            let json: serde_json::Value =
                serde_json::from_str(&poll_event_json(&source).unwrap()).unwrap();
            assert_eq!(json["open_id"].as_u64(), Some(id));
            assert_eq!(json["event"], "output_error");
        }
        assert_ne!(identities[0], identities[1]);
    }

    struct Source(mpsc::Receiver<AudioEvent>, DeckTerminal);
    impl EventSource for Source {
        fn events(&self) -> &mpsc::Receiver<AudioEvent> {
            &self.0
        }
        fn path(&self) -> &str {
            "/music/current.flac"
        }
        fn open_id(&self) -> u64 {
            1
        }
        fn terminal(&self) -> &DeckTerminal {
            &self.1
        }
    }

    #[test]
    fn deck_events_nonblocking_sole_consumption_and_exact_paths() {
        let (tx, rx) = mpsc::channel();
        let deck = Mutex::new(Some(Source(rx, DeckTerminal::default())));
        assert_eq!(poll_event_json(&deck), None);
        tx.send(AudioEvent::TrackStarted {
            path: "/music/current.flac".into(),
        })
        .unwrap();
        tx.send(AudioEvent::PlaybackEnded).unwrap();
        let first: serde_json::Value =
            serde_json::from_str(&poll_event_json(&deck).unwrap()).unwrap();
        assert_eq!(first["event"], "track_started");
        assert_eq!(first["path"], "/music/current.flac");
        let ended: serde_json::Value =
            serde_json::from_str(&poll_event_json(&deck).unwrap()).unwrap();
        assert_eq!(ended["event"], "playback_ended");
        assert_eq!(ended["path"], "/music/current.flac");
        assert_eq!(poll_event_json(&deck), None);
        drop(tx);
        assert_eq!(poll_event_json(&deck), None);
    }

    #[test]
    fn deck_events_contended_or_absent_deck_never_waits() {
        let (_tx, rx) = mpsc::channel();
        let deck = Mutex::new(Some(Source(rx, DeckTerminal::default())));
        let held = deck.lock().unwrap();
        assert_eq!(poll_event_json(&deck), None);
        drop(held);
        *deck.lock().unwrap() = None;
        assert_eq!(poll_event_json(&deck), None);
    }

    fn event_name(deck: &Mutex<Option<Source>>) -> Option<String> {
        poll_event_json(deck).map(|json| {
            serde_json::from_str::<serde_json::Value>(&json).unwrap()["event"]
                .as_str()
                .unwrap()
                .to_owned()
        })
    }

    #[test]
    fn terminal_eof_requires_a_post_event_empty_output_pop() {
        let (tx, rx) = mpsc::channel();
        let deck = Mutex::new(Some(Source(rx, DeckTerminal::default())));
        let ring = AudibleRing::new(48_000);
        let activation = DeckActivation::new(true, false);
        let mut out = [0.0; 4];
        let pop = |out: &mut [f32]| {
            deck.lock()
                .unwrap()
                .as_ref()
                .unwrap()
                .1
                .pop_output(&ring, &activation, out)
        };
        assert_eq!(pop(&mut out), 0); // An underrun before the decoder's final flush is not EOF.
        assert!(ring.push_blocking(&[0.25, -0.25]));
        tx.send(AudioEvent::PlaybackEnded).unwrap();
        assert_eq!(event_name(&deck).as_deref(), Some("playback_ended"));
        assert_eq!(event_name(&deck), None);
        assert_eq!(pop(&mut out), 2);
        assert_eq!(&out[..2], &[0.25, -0.25]);
        assert_eq!(event_name(&deck), None); // The last callback still carries the tail.
        assert_eq!(pop(&mut []), 0); // A zero-frame callback proves nothing.
        assert_eq!(event_name(&deck), None);
        assert_eq!(pop(&mut out), 0);
        assert_eq!(event_name(&deck).as_deref(), Some("playback_drained"));
        // Terminal truth remains available after the channel disconnects.
        drop(tx);
        assert_eq!(event_name(&deck).as_deref(), Some("playback_drained"));
    }

    #[test]
    fn terminal_paused_tail_survives_drain_timeout_then_resumes_intact() {
        let (tx, rx) = mpsc::channel();
        let deck = Mutex::new(Some(Source(rx, DeckTerminal::default())));
        let ring = AudibleRing::new(48_000);
        let activation = DeckActivation::new(true, true);
        let mut out = [0.0; 4];
        assert!(ring.push_blocking(&[0.5, -0.5, 0.75, -0.75]));
        // Exercise the real drain timeout boundary without sleeping five seconds.
        // The shared decoder sends this same event after its bounded wait expires.
        ring.drain_wait(std::time::Duration::ZERO);
        tx.send(AudioEvent::PlaybackEnded).unwrap();
        assert_eq!(event_name(&deck).as_deref(), Some("playback_ended"));
        let pop = |out: &mut [f32]| {
            deck.lock()
                .unwrap()
                .as_ref()
                .unwrap()
                .1
                .pop_output(&ring, &activation, out)
        };
        for _ in 0..8 {
            assert_eq!(pop(&mut out), 0);
            assert_eq!(event_name(&deck), None);
        }
        activation.set_paused(false);
        assert_eq!(pop(&mut out), 4);
        assert_eq!(out, [0.5, -0.5, 0.75, -0.75]);
        assert_eq!(event_name(&deck), None);
        assert_eq!(pop(&mut out), 0);
        assert_eq!(event_name(&deck).as_deref(), Some("playback_drained"));
    }

    #[test]
    fn terminal_silent_active_output_and_underruns_are_not_completion() {
        let terminal = DeckTerminal::default();
        let ring = AudibleRing::new(48_000);
        let activation = DeckActivation::new(true, false);
        let mut out = [1.0; 4];
        assert!(ring.push_blocking(&[0.0; 4]));
        assert_eq!(terminal.pop_output(&ring, &activation, &mut out), 4);
        assert_eq!(out, [0.0; 4]);
        assert_eq!(terminal.event(), None);
        for _ in 0..8 {
            assert_eq!(terminal.pop_output(&ring, &activation, &mut out), 0);
            assert_eq!(terminal.event(), None);
        }
    }

    #[test]
    fn terminal_prepared_deck_cannot_drain_or_inherit_old_output_error() {
        let old = DeckTerminal::default();
        old.output_error();
        let (tx, rx) = mpsc::channel();
        let deck = Mutex::new(Some(Source(rx, DeckTerminal::default())));
        let ring = AudibleRing::new(48_000);
        let activation = DeckActivation::new(false, true);
        let mut out = [0.0; 4];
        assert!(ring.push_blocking(&[0.5; 4]));
        tx.send(AudioEvent::PlaybackEnded).unwrap();
        assert_eq!(event_name(&deck).as_deref(), Some("playback_ended"));
        let pop = |out: &mut [f32]| {
            deck.lock()
                .unwrap()
                .as_ref()
                .unwrap()
                .1
                .pop_output(&ring, &activation, out)
        };
        activation.set_paused(false);
        assert_eq!(pop(&mut out), 0);
        assert_eq!(event_name(&deck), None);
        old.decoder_ended();
        assert_eq!(event_name(&deck), None);
        activation.publish(false);
        assert_eq!(pop(&mut out), 4);
        assert_eq!(out, [0.5; 4]);
        assert_eq!(event_name(&deck), None);
        assert_eq!(pop(&mut out), 0);
        assert_eq!(event_name(&deck).as_deref(), Some("playback_drained"));
    }

    #[test]
    fn terminal_output_error_reaches_poll_without_eof_or_callback_progress() {
        let (_tx, rx) = mpsc::channel();
        let deck = Mutex::new(Some(Source(rx, DeckTerminal::default())));
        deck.lock().unwrap().as_ref().unwrap().1.output_error();
        assert_eq!(event_name(&deck).as_deref(), Some("output_error"));
        assert_eq!(event_name(&deck).as_deref(), Some("output_error"));

        // Host tests run the same helpers. Android owns delivery of the Oboe callback.
        let adapter = include_str!("deck.rs");
        let error = adapter
            .split("fn on_error_before_close(")
            .nth(1)
            .unwrap()
            .split("fn on_audio_ready(")
            .next()
            .unwrap();
        assert!(error.contains("self.terminal.output_error()"));
        let output = adapter
            .split("fn on_audio_ready(")
            .nth(1)
            .unwrap()
            .split("pub struct Deck")
            .next()
            .unwrap();
        assert!(output.contains(".pop_output("));
        assert!(!output.contains("pop_into("));
    }
}
