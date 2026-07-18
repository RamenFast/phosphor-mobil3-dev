//! The deck (M2): phosphor-audio's decode→resample→gapless pipeline, output through
//! oboe/AAudio. The oboe callback pops `AudibleRing` exactly where the desktop PipeWire
//! stream did; the scope taps the same `SampleRing` — sample-locked picture by
//! construction. Pause follows the desktop law: stop popping (backpressure freezes the
//! decoder mid-sample); the stream keeps running with silence.

use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, OnceLock, mpsc};

use oboe::{
    AudioOutputCallback, AudioOutputStreamSafe, AudioStream, AudioStreamAsync,
    AudioStreamBuilder, DataCallbackResult, Output, PerformanceMode, SharingMode, Stereo,
    Usage,
};
use phosphor_audio::playback::{
    AudibleRing, PlayerCommand, PlayerConfig, PlayerSession, spawn_player,
};
use phosphor_audio::ring::SampleRing;

pub const RATE: u32 = 48_000;

/// The one scope feed. Every source (deck now; capture/mic in M4) pushes here and the
/// render thread drains it.
pub fn scope_ring() -> &'static Arc<Mutex<SampleRing>> {
    static SCOPE: OnceLock<Arc<Mutex<SampleRing>>> = OnceLock::new();
    SCOPE.get_or_init(|| Arc::new(Mutex::new(SampleRing::new(RATE))))
}

/// True while the scope ring (deck or capture/mic, not the demo feeder) owns the beam.
pub static DECK_ACTIVE: AtomicBool = AtomicBool::new(false);

/// Capture/mic ingest (M4): Kotlin AudioRecord pushes ~10 ms interleaved-stereo chunks
/// into the same ring the deck feeds — one seam, every source.
pub fn push_capture(samples: &[f32]) {
    scope_ring().lock().unwrap().push_interleaved(samples);
}

pub fn set_ring_active(active: bool) {
    if active {
        scope_ring().lock().unwrap().clear_pending();
    }
    DECK_ACTIVE.store(active, Ordering::Relaxed);
    log::info!("scope ring active: {active}");
}

static DECK: Mutex<Option<Deck>> = Mutex::new(None);

struct DeckOutput {
    audible: Arc<AudibleRing>,
    paused: Arc<AtomicBool>,
    scratch: Vec<f32>,
}

impl AudioOutputCallback for DeckOutput {
    type FrameType = (f32, Stereo);

    fn on_audio_ready(
        &mut self,
        _stream: &mut dyn AudioOutputStreamSafe,
        frames: &mut [(f32, f32)],
    ) -> DataCallbackResult {
        let need = frames.len() * 2;
        self.scratch.resize(need, 0.0);
        let got = if self.paused.load(Ordering::Relaxed) {
            0 // pause = don't pop; backpressure freezes the decoder (desktop law)
        } else {
            self.audible.pop_into(&mut self.scratch)
        };
        self.scratch[got..need].fill(0.0);
        for (i, frame) in frames.iter_mut().enumerate() {
            frame.0 = self.scratch[2 * i];
            frame.1 = self.scratch[2 * i + 1];
        }
        DataCallbackResult::Continue
    }
}

pub struct Deck {
    path: String,
    session: PlayerSession,
    stream: AudioStreamAsync<Output, DeckOutput>,
    paused: Arc<AtomicBool>,
    _events_rx: mpsc::Receiver<phosphor_audio::AudioEvent>,
}

// PlayerSession is channels+Arcs; the oboe stream handle is safe to move with the deck.
unsafe impl Send for Deck {}

pub fn open(path: &str) -> Result<(), String> {
    open_at(path, 0.0)
}

pub fn open_at(path: &str, seek_seconds: f64) -> Result<(), String> {
    close();

    let audible = AudibleRing::new(RATE);
    let paused = Arc::new(AtomicBool::new(false));
    let (events_tx, events_rx) = mpsc::channel();

    let config = PlayerConfig {
        path: PathBuf::from(path),
        seek_seconds,
        loop_forever: false,
        vacuum: false,
        pipe_rate: RATE,
    };
    let session = spawn_player(config, scope_ring().clone(), Some(audible.clone()), events_tx);

    let callback = DeckOutput { audible, paused: paused.clone(), scratch: Vec::new() };
    let mut stream = AudioStreamBuilder::default()
        .set_performance_mode(PerformanceMode::LowLatency)
        .set_sharing_mode(SharingMode::Shared)
        .set_usage(Usage::Media)
        .set_sample_rate(RATE as i32)
        .set_format::<f32>()
        .set_channel_count::<Stereo>()
        .set_callback(callback)
        .open_stream()
        .map_err(|e| format!("oboe open: {e}"))?;
    stream.start().map_err(|e| format!("oboe start: {e}"))?;

    scope_ring().lock().unwrap().clear_pending();
    DECK_ACTIVE.store(true, Ordering::Relaxed);
    *DECK.lock().unwrap() =
        Some(Deck { path: path.to_owned(), session, stream, paused, _events_rx: events_rx });
    log::info!("deck open: {path} @ {seek_seconds}s");
    Ok(())
}

pub fn set_paused(paused: bool) {
    if let Some(deck) = DECK.lock().unwrap().as_ref() {
        deck.paused.store(paused, Ordering::Relaxed);
        log::info!("deck paused: {paused}");
    }
}

pub fn seek_ms(ms: u64) -> Result<(), String> {
    let (path, was_paused) = {
        let guard = DECK.lock().unwrap();
        let Some(deck) = guard.as_ref() else { return Err("no deck loaded".into()) };
        (deck.path.clone(), deck.paused.load(Ordering::Relaxed))
    };
    // The desktop seeks by restarting decode at the offset; same here.
    open_at(&path, ms as f64 / 1000.0)?;
    set_paused(was_paused);
    Ok(())
}

pub fn metadata_json() -> String {
    let guard = DECK.lock().unwrap();
    let Some(deck) = guard.as_ref() else { return "{}".into() };
    let meta = deck.session.shared.current_metadata.lock().unwrap().clone();
    serde_json::json!({
        "path": deck.path,
        "title": meta.title,
        "artist": meta.artist,
        "album": meta.album,
        "duration_ms": meta.duration.map(|s| (s * 1000.0) as u64),
    })
    .to_string()
}

pub fn cover_art() -> Option<Vec<u8>> {
    let guard = DECK.lock().unwrap();
    let deck = guard.as_ref()?;
    let cover = deck.session.shared.current_cover.lock().unwrap();
    cover.as_ref().map(|c| c.data.clone())
}

/// Returns the new playing state (true = playing).
pub fn toggle() -> bool {
    let guard = DECK.lock().unwrap();
    let Some(deck) = guard.as_ref() else { return false };
    let now_paused = !deck.paused.load(Ordering::Relaxed);
    deck.paused.store(now_paused, Ordering::Relaxed);
    log::info!("deck paused: {now_paused}");
    !now_paused
}

pub fn position_micros() -> u64 {
    DECK.lock()
        .unwrap()
        .as_ref()
        .map(|d| d.session.shared.position_micros.load(Ordering::Relaxed))
        .unwrap_or(0)
}

pub fn close() {
    if let Some(mut deck) = DECK.lock().unwrap().take() {
        let _ = deck.session.control.send(PlayerCommand::Stop);
        let _ = deck.stream.stop();
        if let Some(t) = deck.session.thread.take() {
            let _ = t.join();
        }
    }
    DECK_ACTIVE.store(false, Ordering::Relaxed);
}
