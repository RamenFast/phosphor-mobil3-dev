//! The deck (M2): phosphor-audio's decode→resample→gapless pipeline, output through
//! oboe/AAudio. The oboe callback pops `AudibleRing` exactly where the desktop PipeWire
//! stream did; the scope taps the same `SampleRing` — sample-locked picture by
//! construction. Pause follows the desktop law: stop popping (backpressure freezes the
//! decoder mid-sample); the stream keeps running with silence.

use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, OnceLock, mpsc};

use oboe::{
    AudioOutputCallback, AudioOutputStreamSafe, AudioStream, AudioStreamAsync, AudioStreamBuilder,
    DataCallbackResult, Output, PerformanceMode, SharingMode, Stereo, Usage,
};
use phosphor_audio::playback::{
    AudibleRing, PlayerConfig, PlayerSession, spawn_player, validate_track,
};
use phosphor_audio::ring::SampleRing;

use crate::deck_activation::DeckActivation;
use crate::deck_close::close_session;
use crate::deck_events::DeckTerminal;

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

pub fn set_ring_active(active: bool) -> u64 {
    crate::pause::invalidate();
    set_ring_state(active)
}

fn set_ring_state(active: bool) -> u64 {
    let owner = crate::render::with_stereo_window(|ring, meter| {
        if active {
            ring.clear_pending();
        }
        *meter = crate::engine::StereoWindow::new();
        DECK_ACTIVE.store(active, Ordering::Relaxed);
        if active { meter.activate_capture() } else { 0 }
    });
    log::info!("scope ring active: {active}");
    owner
}

static DECK: Mutex<Option<Deck>> = Mutex::new(None);

struct DeckOutput {
    audible: Arc<AudibleRing>,
    activation: Arc<DeckActivation>,
    terminal: Arc<DeckTerminal>,
    scratch: Vec<f32>,
}

impl AudioOutputCallback for DeckOutput {
    type FrameType = (f32, Stereo);

    fn on_error_before_close(
        &mut self,
        _stream: &mut dyn AudioOutputStreamSafe,
        _error: oboe::Error,
    ) {
        self.terminal.output_error();
    }

    fn on_audio_ready(
        &mut self,
        _stream: &mut dyn AudioOutputStreamSafe,
        frames: &mut [(f32, f32)],
    ) -> DataCallbackResult {
        let need = frames.len() * 2;
        self.scratch.resize(need, 0.0);
        let got = self
            .terminal
            .pop_output(&self.audible, &self.activation, &mut self.scratch);
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
    open: crate::deck_events::LocalOpen,
    session: PlayerSession,
    stream: AudioStreamAsync<Output, DeckOutput>,
    activation: Arc<DeckActivation>,
    terminal: Arc<DeckTerminal>,
    events_rx: mpsc::Receiver<phosphor_audio::AudioEvent>,
}

impl crate::deck_events::EventSource for Deck {
    fn events(&self) -> &mpsc::Receiver<phosphor_audio::AudioEvent> {
        &self.events_rx
    }

    fn path(&self) -> &str {
        &self.path
    }

    fn open_id(&self) -> u64 {
        self.open.id
    }

    fn terminal(&self) -> &DeckTerminal {
        &self.terminal
    }
}

pub fn poll_event_json() -> Option<String> {
    crate::deck_events::poll_event_json(&DECK)
}

pub fn open_identity() -> u64 {
    DECK.lock().unwrap().as_ref().map_or(0, |deck| deck.open.id)
}

/// The render command must still belong to the currently published native open.
pub(crate) fn with_published_open(id: u64, apply: impl FnOnce()) {
    let guard = DECK.lock().unwrap();
    if guard
        .as_ref()
        .is_some_and(|deck| deck.open.accepts(id) && deck.terminal.allows_item_confirmation())
    {
        apply();
    }
}

// PlayerSession is channels+Arcs; the oboe stream handle is safe to move with the deck.
unsafe impl Send for Deck {}

pub fn open(path: &str) -> Result<(), String> {
    open_at(path, 0.0)
}

pub fn validate(path: &str) -> Result<(), String> {
    validate_track(Path::new(path))
}

pub fn open_at(path: &str, seek_seconds: f64) -> Result<(), String> {
    open_at_state(path, seek_seconds, false, false, false)
}

pub fn prepare(path: &str) -> Result<(), String> {
    open_at_state(path, 0.0, true, true, false)
}

fn open_at_state(
    path: &str,
    seek_seconds: f64,
    initially_paused: bool,
    prepared: bool,
    same_item: bool,
) -> Result<(), String> {
    validate(path)?;
    close_inner(!same_item);

    let audible = AudibleRing::new(RATE);
    let activation = Arc::new(DeckActivation::new(!prepared, initially_paused));
    let terminal = Arc::new(DeckTerminal::default());
    let (events_tx, events_rx) = mpsc::channel();

    let config = PlayerConfig {
        path: PathBuf::from(path),
        seek_seconds,
        loop_forever: false,
        vacuum: false,
        pipe_rate: RATE,
    };
    let mut session = spawn_player(
        config,
        scope_ring().clone(),
        Some(audible.clone()),
        events_tx,
    );

    let callback = DeckOutput {
        audible,
        activation: activation.clone(),
        terminal: terminal.clone(),
        scratch: Vec::new(),
    };
    let stream = AudioStreamBuilder::default()
        .set_performance_mode(PerformanceMode::LowLatency)
        .set_sharing_mode(SharingMode::Shared)
        .set_usage(Usage::Media)
        .set_sample_rate(RATE as i32)
        .set_format::<f32>()
        .set_channel_count::<Stereo>()
        .set_callback(callback)
        .open_stream();
    let mut stream = match stream {
        Ok(stream) => stream,
        Err(error) => {
            close_session(&mut session, || {});
            return Err(format!("oboe open: {error}"));
        }
    };
    if let Err(error) = stream.start() {
        close_session(&mut session, || {
            let _ = stream.stop();
        });
        return Err(format!("oboe start: {error}"));
    }

    crate::render::with_stereo_window(|ring, meter| {
        ring.clear_pending();
        *meter = crate::engine::StereoWindow::new();
        DECK_ACTIVE.store(!prepared, Ordering::Relaxed);
    });
    *DECK.lock().unwrap() = Some(Deck {
        path: path.to_owned(),
        open: crate::deck_events::LocalOpen::new(!prepared),
        session,
        stream,
        activation,
        terminal,
        events_rx,
    });
    log::info!("deck open: {path} @ {seek_seconds}s");
    Ok(())
}

pub fn set_paused(paused: bool) {
    if let Some(deck) = DECK.lock().unwrap().as_ref() {
        deck.activation.set_paused(paused);
        log::info!("deck paused: {paused}");
    }
}

pub fn publish(paused: bool) {
    if let Some(deck) = DECK.lock().unwrap().as_mut() {
        deck.open.publish();
        deck.activation.publish(paused);
        DECK_ACTIVE.store(true, Ordering::Relaxed);
    }
}

pub fn seek_ms(ms: u64) -> Result<(), String> {
    seek_at_state(ms, false)
}

pub fn prepare_seek_ms(ms: u64) -> Result<(), String> {
    seek_at_state(ms, true)
}

fn seek_at_state(ms: u64, prepare: bool) -> Result<(), String> {
    let (path, was_paused) = {
        let guard = DECK.lock().unwrap();
        let Some(deck) = guard.as_ref() else {
            return Err("no deck loaded".into());
        };
        (deck.path.clone(), !deck.activation.playing())
    };
    // The desktop seeks by restarting decode at the offset; same here.
    open_at_state(
        &path,
        ms as f64 / 1000.0,
        prepare || was_paused,
        prepare,
        true,
    )?;
    set_paused(prepare || was_paused);
    Ok(())
}

pub fn metadata_json() -> String {
    let guard = DECK.lock().unwrap();
    let Some(deck) = guard.as_ref() else {
        return "{}".into();
    };
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
    let Some(deck) = guard.as_ref() else {
        return false;
    };
    crate::pause::set_paused(deck.activation.playing());
    let playing = deck.activation.toggle();
    log::info!("deck playing: {playing}");
    playing
}

pub fn position_micros() -> u64 {
    DECK.lock()
        .unwrap()
        .as_ref()
        .map(|d| d.session.shared.position_micros.load(Ordering::Relaxed))
        .unwrap_or(0)
}

pub fn close() {
    close_inner(true);
}

fn close_inner(invalidate: bool) {
    let deck = { DECK.lock().unwrap().take() };
    if let Some(mut deck) = deck {
        close_session(&mut deck.session, || {
            let _ = deck.stream.stop();
        });
    }
    set_ring_state(false);
    // No retired decoder can publish after its join and this visual boundary.
    if invalidate {
        crate::pause::invalidate();
    }
}
