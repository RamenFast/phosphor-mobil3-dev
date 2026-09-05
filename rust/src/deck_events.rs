use phosphor_audio::AudioEvent;
use std::sync::{Mutex, mpsc};

/// The deck owns this receiver. Empty, contended and disconnected polls never wait.
pub(crate) fn poll_event_json(deck: &Mutex<Option<impl EventSource>>) -> Option<String> {
    let guard = deck.try_lock().ok()?;
    let source = guard.as_ref()?;
    let event = source.events().try_recv().ok()?;
    match event {
        AudioEvent::TrackStarted { path } => Some(
            serde_json::json!({
                "event": "track_started", "path": path,
            })
            .to_string(),
        ),
        AudioEvent::PlaybackEnded => Some(
            serde_json::json!({
                "event": "playback_ended", "path": source.path(),
            })
            .to_string(),
        ),
        _ => None,
    }
}

pub(crate) trait EventSource {
    fn events(&self) -> &mpsc::Receiver<AudioEvent>;
    fn path(&self) -> &str;
}

#[cfg(test)]
mod tests {
    use super::*;

    struct Source(mpsc::Receiver<AudioEvent>);
    impl EventSource for Source {
        fn events(&self) -> &mpsc::Receiver<AudioEvent> {
            &self.0
        }
        fn path(&self) -> &str {
            "/music/current.flac"
        }
    }

    #[test]
    fn deck_events_nonblocking_sole_consumption_and_exact_paths() {
        let (tx, rx) = mpsc::channel();
        let deck = Mutex::new(Some(Source(rx)));
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
        let deck = Mutex::new(Some(Source(rx)));
        let held = deck.lock().unwrap();
        assert_eq!(poll_event_json(&deck), None);
        drop(held);
        *deck.lock().unwrap() = None;
        assert_eq!(poll_event_json(&deck), None);
    }
}
