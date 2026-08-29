use phosphor_audio::playback::{PlayerCommand, PlayerSession};

pub(crate) fn close_session(session: &mut PlayerSession, stop_stream: impl FnOnce()) {
    // The decoder can be blocked pushing into a full audible ring while pause has
    // stopped the only consumer. Close the ring first so Stop and join cannot deadlock.
    if let Some(audible) = session.audible.as_ref() {
        audible.close();
    }
    let _ = session.control.send(PlayerCommand::Stop);
    stop_stream();
    if let Some(thread) = session.thread.take() {
        let _ = thread.join();
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use phosphor_audio::playback::{AudibleRing, PlaybackShared};
    use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
    use std::sync::{Arc, Mutex, mpsc};

    #[test]
    fn close_unblocks_full_audible_ring_before_join() {
        // At this deliberately tiny rate, the ring's minimum is one 1024-frame
        // stereo block. Fill it, then park the session thread on one more push.
        let audible = AudibleRing::new(1_000);
        assert!(audible.push_blocking(&vec![0.0; 2_048]));

        let (started_tx, started_rx) = mpsc::channel();
        let completed = Arc::new(AtomicBool::new(false));
        let completed_on_thread = completed.clone();
        let audible_on_thread = audible.clone();
        let thread = std::thread::spawn(move || {
            started_tx.send(()).unwrap();
            assert!(!audible_on_thread.push_blocking(&[0.0, 0.0]));
            completed_on_thread.store(true, Ordering::Relaxed);
        });
        started_rx.recv().unwrap();

        let (control, control_rx) = mpsc::channel();
        let audible_at_stream_stop = audible.clone();
        let mut session = PlayerSession {
            control,
            shared: Arc::new(PlaybackShared {
                position_micros: AtomicU64::new(0),
                current_metadata: Mutex::new(Default::default()),
                current_cover: Mutex::new(None),
            }),
            audible: Some(audible),
            vacuum: false,
            thread: Some(thread),
        };

        close_session(&mut session, || {
            assert!(matches!(control_rx.try_recv(), Ok(PlayerCommand::Stop)));
            assert!(!audible_at_stream_stop.push_blocking(&[0.0, 0.0]));
        });

        assert!(completed.load(Ordering::Relaxed));
        assert!(session.thread.is_none());
    }
}
