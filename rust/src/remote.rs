//! Remote source v2 (the Tailscale bridge, phone side). Speaks protocol v2 to
//! `phosphor-relay` (docs/BRIDGE.md): W/H handshake, K heartbeats both ways, A audio,
//! G geometry (drawn via the DSP-bypass path), M metadata w/ position/seek, S sources,
//! L listings, R art, E fix-bearing errors.
//!
//! Division of labor (the reconciled law): RUST owns link mechanics — connect timeout,
//! watchdog (3 s stalled / 10 s dead), reconnect backoff, generation-guarded threads,
//! the oboe route-change restart. The Android SERVICE owns policy — when to connect,
//! when to give up, and every surface the OS sees.

use std::net::{TcpStream, ToSocketAddrs};
use std::sync::atomic::{AtomicBool, AtomicU8, AtomicU32, AtomicU64, Ordering};
use std::sync::{Arc, Mutex, OnceLock};
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use crate::bridge_core::{self, WriterCmd};
use crate::deck::{RATE, scope_ring};
use crate::spsc::{
    AdaptiveJitter, BlockRing, LATENCY_MODE_BALANCED, LATENCY_MODE_SAFE, LATENCY_MODE_TIGHT,
    ScopeChunkRing, ScopeChunkSink, ScopeChunkTap,
};
use oboe::{
    AudioOutputCallback, AudioOutputStreamSafe, AudioStream, AudioStreamAsync, AudioStreamBuilder,
    DataCallbackResult, Error as OboeError, Output, PerformanceMode, SharingMode, Stereo, Usage,
};

// ── Link state (JNI-visible via status_json) ─────────────────────────────────
pub const ST_IDLE: u8 = 0;
pub const ST_CONNECTING: u8 = 1;
pub const ST_STREAMING: u8 = 2;
pub const ST_STALLED: u8 = 3;
pub const ST_RECONNECTING: u8 = 4;
pub const ST_FAILED: u8 = 5;

fn state_name(s: u8) -> &'static str {
    match s {
        ST_CONNECTING => "connecting",
        ST_STREAMING => "streaming",
        ST_STALLED => "stalled",
        ST_RECONNECTING => "reconnecting",
        ST_FAILED => "failed",
        _ => "idle",
    }
}

#[derive(Default)]
struct Slots {
    welcome: Mutex<String>,
    meta: Mutex<String>,
    sources: Mutex<String>,
    listing: Mutex<String>,
    error: Mutex<String>, // last E frame json (or link error {error,fix})
    art: Mutex<Option<Vec<u8>>>,
    art_id: Mutex<String>,
    /// Desktop scope truth off the K stats (mode/theme/gain/auto) — the band's
    /// honesty source while VISUALIZER feeds the beam.
    scope: Mutex<String>,
}

struct Link {
    host: Mutex<String>,
    port: AtomicU32,
    cfg_audio: AtomicBool,
    cfg_geometry: AtomicBool,
    /// User latency policy, last-wins from JNI. The RT callback samples it on
    /// every burst, so a mode change needs no stream teardown.
    latency_mode: AtomicU8,
    muted: AtomicBool,
    state: AtomicU8,
    quit: AtomicBool,
    generation: AtomicU64,
    /// The live session's shared handle. POINTER-SWAP-ONLY MUTEX (the law): no
    /// syscall, no I/O, nothing blocking ever runs while this is held — lock,
    /// clone the Arc, unlock, then act. Violating this recreates finding 2.
    current: Mutex<Option<Arc<SessionShared>>>,
    /// Threads that outlived their bounded join (each one is a bug receipt).
    leaked_threads: AtomicU32,
    /// Mailbox to the single long-lived control thread (the sole session
    /// owner). Guarded because std's Sender is !Sync; the lock is held for a
    /// clone only.
    ctrl: Mutex<Option<std::sync::mpsc::Sender<LinkCmd>>>,
    slots: Slots,
    meta_gen: AtomicU32,
    sources_gen: AtomicU32,
    listing_gen: AtomicU32,
    art_gen: AtomicU32,
    rx_bytes: AtomicU64,
    rx_a: AtomicU64,
    rx_g: AtomicU64,
    last_rx_ms: AtomicU64,
    // Audio-latency instrumentation (mirrored from the ring by the watchdog
    // tick; ask-2 receipts + the Nerd HUD bridge line read these).
    audio_buf_ms: AtomicU64,
    audio_skips: AtomicU64,
    audio_skip_ms: AtomicU64,
    a_drops: AtomicU64,
    /// Visual-only chunks overwritten by the callback's lossy scope SPSC.
    /// Audible audio is never delayed to preserve these frames.
    scope_drops: AtomicU64,
    /// Adaptive target is stored in frames (the controller's native unit) and
    /// converted only on status reads. Underruns are process-lifetime events,
    /// not session-local, so reconnects cannot erase a bad-path receipt.
    audio_target_frames: AtomicU32,
    audio_underruns: AtomicU64,
}

fn link() -> &'static Link {
    static L: OnceLock<Link> = OnceLock::new();
    L.get_or_init(|| Link {
        host: Mutex::new(String::new()),
        port: AtomicU32::new(45777),
        cfg_audio: AtomicBool::new(true),
        cfg_geometry: AtomicBool::new(false),
        latency_mode: AtomicU8::new(LATENCY_MODE_SAFE),
        muted: AtomicBool::new(false),
        state: AtomicU8::new(ST_IDLE),
        quit: AtomicBool::new(true),
        generation: AtomicU64::new(0),
        current: Mutex::new(None),
        leaked_threads: AtomicU32::new(0),
        ctrl: Mutex::new(None),
        slots: Slots::default(),
        meta_gen: AtomicU32::new(0),
        sources_gen: AtomicU32::new(0),
        listing_gen: AtomicU32::new(0),
        art_gen: AtomicU32::new(0),
        rx_bytes: AtomicU64::new(0),
        rx_a: AtomicU64::new(0),
        rx_g: AtomicU64::new(0),
        last_rx_ms: AtomicU64::new(0),
        audio_buf_ms: AtomicU64::new(0),
        audio_skips: AtomicU64::new(0),
        audio_skip_ms: AtomicU64::new(0),
        a_drops: AtomicU64::new(0),
        scope_drops: AtomicU64::new(0),
        audio_target_frames: AtomicU32::new(RATE / 4),
        audio_underruns: AtomicU64::new(0),
    })
}

/// Wall clock — K payloads and logs ONLY. Liveness math uses monotonic_ms():
/// a wall-clock step must never kill a live link or immortalize a dead one
/// (audit finding 13).
fn now_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0)
}

/// Process-epoch monotonic milliseconds (CLOCK_MONOTONIC — immune to clock steps).
fn monotonic_ms() -> u64 {
    static EPOCH: OnceLock<Instant> = OnceLock::new();
    EPOCH.get_or_init(Instant::now).elapsed().as_millis() as u64
}

/// Poison-tolerant lock: panic="abort" is release-only and Ben daily-drives the
/// debug APK — a panicked session thread must not cascade lock panics onto the
/// main thread through these mutexes.
fn plock<T>(m: &Mutex<T>) -> std::sync::MutexGuard<'_, T> {
    m.lock().unwrap_or_else(|p| p.into_inner())
}

fn set_link_error(err: &str, fix: &str) {
    *plock(&link().slots.error) =
        format!(r#"{{"error":{},"fix":{}}}"#, json_str(err), json_str(fix));
}

/// Per-session shared handle: the lock-free reachable state JNI threads and
/// session threads use to cancel and wake each other. `trip()` is idempotent,
/// callable from any thread, and never blocks — cancellation + the socket FIN
/// are the wakers every blocking point in the session observes.
struct SessionShared {
    #[allow(dead_code)]
    session_gen: u64,
    cancel: AtomicBool,
    /// Dedicated shutdown clone — dropping other clones never FINs; an explicit
    /// shutdown(Both) is the only reliable waker for blocked reads/writes.
    sock: TcpStream,
    writer_tx: std::sync::mpsc::SyncSender<WriterCmd>,
    hello_dirty: AtomicBool,
}

impl SessionShared {
    fn cancelled(&self) -> bool {
        self.cancel.load(Ordering::Relaxed)
    }
    fn trip(&self) {
        self.cancel.store(true, Ordering::SeqCst);
        let _ = self.sock.shutdown(std::net::Shutdown::Both);
    }
}

/// The session's joinable thread handles.
#[derive(Default)]
struct SessionParts {
    writer: Option<std::thread::JoinHandle<()>>,
    reader: Option<std::thread::JoinHandle<()>>,
    audio: Option<std::thread::JoinHandle<()>>,
    scope: Option<std::thread::JoinHandle<()>>,
    oboe: Option<std::thread::JoinHandle<()>>,
}

/// Commands to the control thread. Parameters ride the Link atomics (last-wins
/// by design — the mailbox is drained and only the newest intent matters).
enum LinkCmd {
    Connect,
    Disconnect,
}

/// The RAII session: lives ONLY on the control thread's stack; every exit path
/// (including a debug-build unwind) runs the ordered teardown via Drop.
struct Session {
    shared: Arc<SessionShared>,
    path: AudioPath,
    parts: SessionParts,
    out_slot: Arc<Mutex<Option<AudioStreamAsync<Output, RemoteOutput>>>>,
}

impl Drop for Session {
    fn drop(&mut self) {
        teardown_session(&self.shared, &self.path, &mut self.parts, &self.out_slot);
    }
}

fn json_str(s: &str) -> String {
    serde_json::to_string(s).unwrap_or_else(|_| "\"\"".into())
}

// ── The audio path (audit finding 10: lock-free on the RT callback) ──────────
// Two opaque endpoints around the SPSC BlockRing (rust/src/spsc.rs — the first
// implementation of ../phosphor/docs/dev/SPSC-RING-DESIGN.md): the worker
// pushes through AudioSink (park-timeout backpressure, no Condvar), the RT
// callback pops through AudioTap (no alloc/lock/syscall/log) and runs the
// catch-up policy as pure index math. Deliberately no trait: a vtable has no
// place on the RT path.

/// ~400 ms of elastic storage. Safe preserves the field-tuned 2026-07-18 policy
/// EXACTLY: 350 ms high-water, 250 ms continuously high, then cut to 250 ms.
/// The earlier 100 ms + 150 ms trigger sat inside normal wifi/Tailscale jitter
/// and audibly sliced ordinary playback 3-4×/song; sustained hysteresis is law.
///
/// Tight (80 ms) and balanced (150 ms) keep that hysteresis but scale the high
/// water to target + 100 ms. A zero-filled callback widens by 40 ms up to safe;
/// two clean minutes earn 20 ms back toward the selected floor. All time in the
/// controller is callback frames, never a callback-side clock. Honest limit:
/// this is reactive, not a network-delay estimator — startup silence is ignored
/// until the first audio arrives, and a genuinely starved path can still glitch
/// before the next wider target has buffer to spend.
const RING_FRAMES: usize = (RATE as usize) * 2 / 5;
const SCOPE_CHUNKS: usize = 64;
const SCOPE_CHUNK_SAMPLES: usize = 16_384;

#[derive(Clone)]
struct AudioPath {
    ring: Arc<BlockRing>,
    scope: Arc<ScopeChunkRing>,
}

impl AudioPath {
    fn new(_rate: u32) -> Self {
        Self {
            ring: BlockRing::new(RING_FRAMES),
            // 64 × 8192-frame chunks is intentionally extravagant headroom:
            // visual loss is preferable to one callback ever waiting.
            scope: ScopeChunkRing::new(SCOPE_CHUNKS, SCOPE_CHUNK_SAMPLES),
        }
    }
    fn sink(&self) -> AudioSink {
        AudioSink(self.ring.clone())
    }
    /// One tap per oboe stream; streams never overlap (the supervisor installs
    /// a replacement only after AAudio closed the old one), so the single-
    /// consumer discipline holds by usage.
    fn tap(&self) -> AudioTap {
        AudioTap(self.ring.clone())
    }
    fn scope_sink(&self) -> ScopeSink {
        ScopeSink(self.scope.sink())
    }
    fn scope_tap(&self) -> ScopeTap {
        ScopeTap(self.scope.tap())
    }
    fn close(&self) {
        self.ring.close();
    }
    /// Post-close teardown drain (single-threaded context — both hot sides are
    /// already exiting): purge any ghost audio a reconnect must never replay.
    fn clear(&self) {
        self.ring.skip_to_latest(0);
    }
    fn buffered_ms(&self) -> u64 {
        (self.ring.buffered_frames() as u64) * 1000 / RATE as u64
    }
    fn skips(&self) -> (u64, u64) {
        (
            self.ring.skips.load(Ordering::Relaxed),
            self.ring.skipped_frames.load(Ordering::Relaxed) * 1000 / RATE as u64,
        )
    }
    fn scope_drops(&self) -> u64 {
        self.scope.dropped_chunks()
    }
}

struct AudioSink(Arc<BlockRing>);
impl AudioSink {
    /// Blocking push; returns false once the path is closed (teardown's waker).
    fn push(&mut self, samples: &[f32]) -> bool {
        self.0.push_blocking(samples)
    }
}

struct AudioTap(Arc<BlockRing>);
impl AudioTap {
    fn pop_into(&mut self, out: &mut [f32]) -> usize {
        self.0.pop_into(out)
    }
    /// RT-safe sustained catch-up. The controller supplies the current scaled
    /// threshold and returns a keep-count only after continuous excess.
    fn catch_up(&mut self, jitter: &mut AdaptiveJitter, frames_this_cb: usize) {
        if let Some(keep_frames) = jitter.catch_up(self.0.buffered_frames(), frames_this_cb) {
            self.0.skip_to_latest(keep_frames);
        }
    }
}

struct ScopeSink(ScopeChunkSink);
impl ScopeSink {
    /// RT-safe lossy publish of the samples finalized for this callback.
    fn push(&mut self, samples: &[f32]) {
        self.0.push(samples);
    }
}

struct ScopeTap(ScopeChunkTap);
impl ScopeTap {
    fn pop_into(&mut self, out: &mut [f32]) -> usize {
        self.0.pop_into(out)
    }
}

// ── Oboe output (with mute + route-change restart) ───────────────────────────
struct RemoteOutput {
    tap: AudioTap,
    scope: ScopeSink,
    scratch: Vec<f32>,
    jitter: AdaptiveJitter,
    latency_mode: &'static AtomicU8,
    effective_target_frames: &'static AtomicU32,
    underruns: &'static AtomicU64,
    audio_enabled: &'static AtomicBool,
    geometry_enabled: &'static AtomicBool,
    /// The stream is opened before H/W by lifecycle law. Empty callbacks before
    /// the first audio are startup, not network underruns, and must not teach an
    /// adaptive mode all the way to safe before playback even exists.
    playback_started: bool,
    muted: Arc<AtomicBool>,
    restart_tx: std::sync::mpsc::Sender<()>,
}

impl AudioOutputCallback for RemoteOutput {
    type FrameType = (f32, Stereo);
    fn on_audio_ready(
        &mut self,
        _s: &mut dyn AudioOutputStreamSafe,
        frames: &mut [(f32, f32)],
    ) -> DataCallbackResult {
        // RT path law (audit finding 10): no alloc, no lock, no syscall, no
        // log. Scratch is preallocated at open; a burst beyond it (absurd)
        // plays silence for the tail rather than allocating.
        let need = (frames.len() * 2).min(self.scratch.len());
        self.jitter
            .set_mode(self.latency_mode.load(Ordering::Relaxed));
        self.tap.catch_up(&mut self.jitter, frames.len()); // index math only
        let got = self.tap.pop_into(&mut self.scratch[..need]);
        self.scratch[got..need].fill(0.0);
        let audio_enabled = self.audio_enabled.load(Ordering::Relaxed);
        if audio_enabled {
            if got > 0 {
                self.playback_started = true;
            }
            if self.playback_started {
                if got < need {
                    self.underruns.fetch_add(1, Ordering::Relaxed);
                    self.jitter.observe_underrun();
                } else {
                    self.jitter.observe_clean(frames.len());
                }
            }
        } else {
            // Re-arming avoids charging deliberate remoteSetStreams(audio=false)
            // silence as an outage when audio is enabled again.
            self.playback_started = false;
        }
        self.effective_target_frames
            .store(self.jitter.target_frames() as u32, Ordering::Relaxed);
        // Instant local mute: keep draining (no stale-buffer buildup), emit silence.
        let muted = self.muted.load(Ordering::Relaxed);
        for (i, f) in frames.iter_mut().enumerate() {
            if muted || 2 * i + 1 >= need {
                f.0 = 0.0;
                f.1 = 0.0;
            } else {
                f.0 = self.scratch[2 * i];
                f.1 = self.scratch[2 * i + 1];
            }
            if 2 * i + 1 < need {
                // The scope publishes the exact post-mute/post-zero-fill output,
                // not the pre-jitter receive copy.
                self.scratch[2 * i] = f.0;
                self.scratch[2 * i + 1] = f.1;
            }
        }
        if audio_enabled && self.playback_started && !self.geometry_enabled.load(Ordering::Relaxed)
        {
            self.scope.push(&self.scratch[..need]);
        }
        DataCallbackResult::Continue
    }

    fn on_error_after_close(&mut self, _s: &mut dyn AudioOutputStreamSafe, error: OboeError) {
        // Buds connected / route died: AAudio kills the stream. Never reopen from the
        // callback — signal the supervisor.
        if matches!(error, OboeError::Disconnected) {
            let _ = self.restart_tx.send(());
        }
    }
}

fn open_output(
    path: &AudioPath,
    muted: Arc<AtomicBool>,
    restart_tx: std::sync::mpsc::Sender<()>,
) -> Result<AudioStreamAsync<Output, RemoteOutput>, String> {
    let l = link();
    let latency_mode = l.latency_mode.load(Ordering::Relaxed);
    let resume_target = l.audio_target_frames.load(Ordering::Relaxed) as usize;
    let mut out = AudioStreamBuilder::default()
        .set_performance_mode(PerformanceMode::LowLatency)
        .set_sharing_mode(SharingMode::Shared)
        .set_usage(Usage::Media)
        .set_sample_rate(RATE as i32)
        .set_format::<f32>()
        .set_channel_count::<Stereo>()
        .set_callback(RemoteOutput {
            tap: path.tap(),
            scope: path.scope_sink(),
            // Preallocated far above any real AAudio burst (8192 frames) — the
            // callback never resizes it (finding 10's alloc half).
            scratch: vec![0.0; 16384],
            jitter: AdaptiveJitter::new(RATE as usize, latency_mode, resume_target),
            latency_mode: &l.latency_mode,
            effective_target_frames: &l.audio_target_frames,
            underruns: &l.audio_underruns,
            audio_enabled: &l.cfg_audio,
            geometry_enabled: &l.cfg_geometry,
            playback_started: false,
            muted,
            restart_tx,
        })
        .open_stream()
        .map_err(|e| format!("oboe open: {e}"))?;
    out.start().map_err(|e| format!("oboe start: {e}"))?;
    Ok(out)
}

// ── Wire helpers ─────────────────────────────────────────────────────────────

/// Fail-fast enqueue onto the current session's writer thread (audit finding 2:
/// JNI callers NEVER touch a socket). Full queue or no session = drop + log —
/// transport is optimistic (the next M frame reconciles), and a full queue only
/// happens while the writer is already dying inside a bounded write.
fn enqueue(tag: u8, payload: &[u8]) -> bool {
    let cur = plock(&link().current).clone();
    match cur {
        Some(s) if !s.cancelled() => match s
            .writer_tx
            .try_send(WriterCmd::Frame(bridge_core::encode_frame(tag, payload)))
        {
            Ok(()) => true,
            Err(_) => {
                log::warn!(
                    "remote enqueue {} dropped (writer queue full/gone)",
                    tag as char
                );
                false
            }
        },
        _ => {
            log::debug!("remote enqueue {} dropped (link down)", tag as char);
            false
        }
    }
}

fn hello_json() -> String {
    let l = link();
    format!(
        r#"{{"proto":2,"client":"phosphor-mobil3/0.2.0","audio":{},"geometry":{},"geometry_fps":60}}"#,
        l.cfg_audio.load(Ordering::Relaxed),
        l.cfg_geometry.load(Ordering::Relaxed),
    )
}

// ── Public API (JNI-facing) ──────────────────────────────────────────────────

/// Ensure the single control thread exists; return a mailbox clone. One-time
/// spawn under the lock is the sanctioned exception to the pointer-lock law.
fn ensure_ctrl() -> Option<std::sync::mpsc::Sender<LinkCmd>> {
    let mut guard = plock(&link().ctrl);
    if guard.is_none() {
        let (tx, rx) = std::sync::mpsc::channel::<LinkCmd>();
        let spawned = std::thread::Builder::new()
            .name("phosphor-remote-ctl".into())
            .spawn(move || control_loop(rx))
            .is_ok();
        if !spawned {
            return None;
        }
        *guard = Some(tx);
    }
    guard.clone()
}

/// Begin (or retarget) the link. Non-blocking: desired state lands in atomics,
/// the current session is tripped, and the control thread (sole session owner)
/// does the rest. Observe via status_json(). Single intent bump — the audit-1
/// double-bump gap is gone.
pub fn connect(host: &str, port: u16, audio: bool, geometry: bool) -> bool {
    let l = link();
    *plock(&l.host) = host.to_string();
    l.port.store(port as u32, Ordering::Relaxed);
    l.cfg_audio.store(audio, Ordering::Relaxed);
    l.cfg_geometry.store(geometry, Ordering::Relaxed);
    // A NEW session always starts audible: the mute flag is per-playback POLICY
    // (the service re-asserts it on pause/focus events), not link state — a
    // stale mute from a torn-down session must never silence the next one
    // (field bug 2026-07-18: buds silent on a healthy streaming session).
    l.muted.store(false, Ordering::Relaxed);
    l.quit.store(false, Ordering::Relaxed);
    l.generation.fetch_add(1, Ordering::SeqCst); // ONE bump per intent
    l.state.store(ST_CONNECTING, Ordering::Relaxed);
    let cur = plock(&l.current).clone();
    if let Some(s) = cur {
        s.trip(); // retarget: retire the live session, never overlap it
    }
    match ensure_ctrl() {
        Some(tx) => tx.send(LinkCmd::Connect).is_ok(),
        None => false,
    }
}

pub fn disconnect() {
    let l = link();
    l.quit.store(true, Ordering::Relaxed);
    l.generation.fetch_add(1, Ordering::SeqCst); // ONE bump per intent
    // Clone under the pointer lock, trip OUTSIDE it (the no-I/O-under-lock law);
    // trip never blocks, so disconnect from the player looper can never ANR.
    let cur = plock(&l.current).clone();
    if let Some(s) = cur {
        s.trip();
    }
    if let Some(tx) = plock(&l.ctrl).clone() {
        let _ = tx.send(LinkCmd::Disconnect); // wake the control thread
    }
    l.state.store(ST_IDLE, Ordering::Relaxed);
    *l.slots.meta.lock().unwrap() = String::new();
    *l.slots.art.lock().unwrap() = None;
    *l.slots.art_id.lock().unwrap() = String::new();
    let _ = crate::render::sender().send(crate::render::Cmd::GeometryActive(false));
    crate::deck::DECK_ACTIVE.store(false, Ordering::Relaxed);
}

pub fn set_streams(audio: bool, geometry: bool) {
    let l = link();
    l.cfg_audio.store(audio, Ordering::Relaxed);
    l.cfg_geometry.store(geometry, Ordering::Relaxed);
    // Coalesced H: the dirty flag is unlosable even if the wake marker drops on
    // a full queue — the writer re-checks it every tick and rebuilds from the
    // cfg atomics, so the LATEST toggles always win.
    if let Some(s) = plock(&l.current).clone() {
        s.hello_dirty.store(true, Ordering::Relaxed);
        let _ = s.writer_tx.try_send(WriterCmd::Hello);
    }
    let _ = crate::render::sender().send(crate::render::Cmd::GeometryActive(geometry));
}

pub fn set_muted(m: bool) {
    link().muted.store(m, Ordering::Relaxed);
}

/// 0=tight, 1=balanced, 2=safe. Invalid values fail closed to safe. A live
/// callback observes the new mode on its next burst; no session restart and no
/// JNI-thread interaction with the audio stream are required.
pub fn set_latency_mode(mode: u8) {
    let l = link();
    let mode = AdaptiveJitter::normalize_mode(mode);
    let previous = l.latency_mode.swap(mode, Ordering::Relaxed);
    if previous != mode {
        l.audio_target_frames.store(
            AdaptiveJitter::floor_frames(RATE as usize, mode) as u32,
            Ordering::Relaxed,
        );
    }
}

pub fn transport(cmd: &str) {
    // Bare v1 verbs ride as JSON on v2 (the relay accepts both; JSON is canonical).
    let payload = if cmd.starts_with('{') {
        cmd.to_string()
    } else if let Some(ms) = cmd.strip_prefix("seek ") {
        format!(r#"{{"cmd":"seek","ms":{}}}"#, ms.trim())
    } else {
        format!(r#"{{"cmd":{}}}"#, json_str(cmd))
    };
    enqueue(b'T', payload.as_bytes());
    log::info!("remote transport: {payload}");
}

pub fn seek_ms(ms: u64) {
    enqueue(b'T', format!(r#"{{"cmd":"seek","ms":{ms}}}"#).as_bytes());
}

pub fn request_sources() {
    enqueue(b'Q', b"");
}

pub fn choose_source(id: &str) {
    enqueue(b'C', format!(r#"{{"id":{}}}"#, json_str(id)).as_bytes());
}

pub fn browse(root: &str, path: &str) {
    enqueue(
        b'B',
        format!(r#"{{"root":{},"path":{}}}"#, json_str(root), json_str(path)).as_bytes(),
    );
}

pub fn play_file(root: &str, path: &str) {
    enqueue(
        b'P',
        format!(r#"{{"root":{},"path":{}}}"#, json_str(root), json_str(path)).as_bytes(),
    );
}

pub fn stop_file() {
    enqueue(b'P', br#"{"action":"stop"}"#);
}

pub fn request_art(id: &str) {
    enqueue(b'R', format!(r#"{{"id":{}}}"#, json_str(id)).as_bytes());
}

/// Drive the DESKTOP scope over the bridge (mode/theme/ui/gain — Ben's
/// remote-render ask). The relay executes via phosphor's typed ctl grammar;
/// failures come back as fix-bearing E frames.
pub fn scope_ctl(verb: &str, value: &str) {
    enqueue(
        b'V',
        format!(
            r#"{{"verb":{},"value":{}}}"#,
            json_str(verb),
            json_str(value)
        )
        .as_bytes(),
    );
}

pub fn metadata_json() -> String {
    let m = link().slots.meta.lock().unwrap().clone();
    if m.is_empty() { "{}".into() } else { m }
}

pub fn sources_json() -> String {
    link().slots.sources.lock().unwrap().clone()
}

pub fn listing_json() -> String {
    link().slots.listing.lock().unwrap().clone()
}

pub fn art_bytes() -> Option<Vec<u8>> {
    link().slots.art.lock().unwrap().clone()
}

pub fn meta_generation() -> u32 {
    link().meta_gen.load(Ordering::Relaxed)
}
pub fn sources_generation() -> u32 {
    link().sources_gen.load(Ordering::Relaxed)
}
pub fn listing_generation() -> u32 {
    link().listing_gen.load(Ordering::Relaxed)
}
pub fn art_generation() -> u32 {
    link().art_gen.load(Ordering::Relaxed)
}

pub fn status_json() -> String {
    let l = link();
    let welcome = l.slots.welcome.lock().unwrap().clone();
    let error = l.slots.error.lock().unwrap().clone();
    let scope = plock(&l.slots.scope).clone();
    let latency_mode = l.latency_mode.load(Ordering::Relaxed);
    let latency_mode_name = match latency_mode {
        LATENCY_MODE_TIGHT => "tight",
        LATENCY_MODE_BALANCED => "balanced",
        _ => "safe",
    };
    let audio_target_ms = l.audio_target_frames.load(Ordering::Relaxed) as u64 * 1000 / RATE as u64;
    format!(
        r#"{{"state":{},"host":{},"port":{},"rx_bytes":{},"rx_a":{},"rx_g":{},"art_id":{},"meta_gen":{},"sources_gen":{},"listing_gen":{},"art_gen":{},"leaked_threads":{},"audio_buf_ms":{},"audio_skips":{},"audio_skip_ms":{},"a_drops":{},"scope_drops":{},"audio_latency_mode":{},"audio_target_ms":{},"audio_underruns":{},"scope":{},"welcome":{},"last_error":{}}}"#,
        json_str(state_name(l.state.load(Ordering::Relaxed))),
        json_str(&l.host.lock().unwrap()),
        l.port.load(Ordering::Relaxed),
        l.rx_bytes.load(Ordering::Relaxed),
        l.rx_a.load(Ordering::Relaxed),
        l.rx_g.load(Ordering::Relaxed),
        json_str(&l.slots.art_id.lock().unwrap()),
        l.meta_gen.load(Ordering::Relaxed),
        l.sources_gen.load(Ordering::Relaxed),
        l.listing_gen.load(Ordering::Relaxed),
        l.art_gen.load(Ordering::Relaxed),
        l.leaked_threads.load(Ordering::Relaxed),
        l.audio_buf_ms.load(Ordering::Relaxed),
        l.audio_skips.load(Ordering::Relaxed),
        l.audio_skip_ms.load(Ordering::Relaxed),
        l.a_drops.load(Ordering::Relaxed),
        l.scope_drops.load(Ordering::Relaxed),
        json_str(latency_mode_name),
        audio_target_ms,
        l.audio_underruns.load(Ordering::Relaxed),
        if scope.is_empty() {
            "null".to_string()
        } else {
            scope
        },
        if welcome.is_empty() {
            "null".into()
        } else {
            welcome
        },
        if error.is_empty() {
            "null".into()
        } else {
            error
        },
    )
}

// ── The control thread: ONE owner for every session, forever ─────────────────
// Sessions are created and destroyed sequentially on this thread; overlap is
// impossible by construction, which is what actually closes audit finding 1.

/// Interruptible wait: sleeps up to `dur` in slices, returning early with the
/// newest pending command (last-wins) or on quit.
fn wait_or_cmd(rx: &std::sync::mpsc::Receiver<LinkCmd>, dur: Duration) -> Option<LinkCmd> {
    let l = link();
    let t0 = Instant::now();
    let mut newest = None;
    loop {
        while let Ok(c) = rx.try_recv() {
            newest = Some(c);
        }
        if newest.is_some() || l.quit.load(Ordering::Relaxed) || t0.elapsed() >= dur {
            return newest;
        }
        std::thread::sleep(Duration::from_millis(120));
    }
}

fn control_loop(rx: std::sync::mpsc::Receiver<LinkCmd>) {
    let l = link();
    'idle: loop {
        let Ok(mut cmd) = rx.recv() else { return };
        while let Ok(c) = rx.try_recv() {
            cmd = c; // drain-coalesce: only the newest intent matters
        }
        if matches!(cmd, LinkCmd::Disconnect) {
            continue 'idle;
        }
        let mut backoff = 1u64;
        'sessions: loop {
            if l.quit.load(Ordering::Relaxed) {
                continue 'idle;
            }
            let my_gen = l.generation.load(Ordering::SeqCst); // snapshot, never bump
            let host = plock(&l.host).clone();
            let port = l.port.load(Ordering::Relaxed) as u16;
            let end = run_session(my_gen, &host, port);
            // Newest intent wins before any reconnect policy runs.
            let mut pending = None;
            while let Ok(c) = rx.try_recv() {
                pending = Some(c);
            }
            if l.quit.load(Ordering::Relaxed) || matches!(pending, Some(LinkCmd::Disconnect)) {
                continue 'idle;
            }
            if matches!(pending, Some(LinkCmd::Connect)) {
                backoff = 1; // fresh target, fresh ladder
                continue 'sessions;
            }
            match end {
                SessionEnd::Quit => continue 'idle,
                SessionEnd::V1Relay => {
                    l.state.store(ST_FAILED, Ordering::Relaxed);
                    set_link_error(
                        "relay speaks protocol v1",
                        &format!("upgrade it: scripts/relay-install.sh --host {host}"),
                    );
                    continue 'idle; // no point retrying a v1 peer
                }
                SessionEnd::Failed(e) => {
                    l.state.store(ST_RECONNECTING, Ordering::Relaxed);
                    set_link_error(
                        &e,
                        "reconnecting with backoff — check the relay/tailnet if this persists",
                    );
                    log::warn!("remote session ended ({e}); retrying in {backoff}s");
                    match wait_or_cmd(&rx, Duration::from_secs(backoff)) {
                        Some(LinkCmd::Disconnect) => continue 'idle,
                        Some(LinkCmd::Connect) => {
                            backoff = 1;
                            continue 'sessions;
                        }
                        None => {}
                    }
                    backoff = bridge_core::next_backoff(backoff);
                }
                SessionEnd::Healthy => {
                    backoff = 1; // a good run resets the ladder before the next drop
                    l.state.store(ST_RECONNECTING, Ordering::Relaxed);
                    // Never hot-loop a flapping peer.
                    match wait_or_cmd(&rx, Duration::from_secs(1)) {
                        Some(LinkCmd::Disconnect) => continue 'idle,
                        Some(LinkCmd::Connect) => continue 'sessions,
                        None => {}
                    }
                }
            }
        }
    }
}

enum SessionEnd {
    Quit,
    V1Relay,
    Failed(String),
    Healthy,
}

fn run_session(my_gen: u64, host: &str, port: u16) -> SessionEnd {
    let l = link();
    l.state.store(ST_CONNECTING, Ordering::Relaxed);

    let addr = match (host, port)
        .to_socket_addrs()
        .ok()
        .and_then(|mut a| a.next())
    {
        Some(a) => a,
        None => return SessionEnd::Failed(format!("resolve {host}: no address")),
    };
    let stream = match TcpStream::connect_timeout(&addr, Duration::from_secs(4)) {
        Ok(s) => s,
        Err(e) => return SessionEnd::Failed(format!("connect {host}:{port}: {e}")),
    };
    stream.set_nodelay(true).ok();
    // SO_SNDTIMEO 2 s: bounds every write the writer thread makes — a blackholed
    // peer turns into a writer-fatal within one frame, never an infinite park
    // (finding 2's other half; the writer thread is the first).
    stream.set_write_timeout(Some(Duration::from_secs(2))).ok();
    let read_stream = match stream.try_clone() {
        Ok(s) => s,
        Err(e) => return SessionEnd::Failed(format!("clone stream: {e}")),
    };

    // Audio plumbing FIRST (audit finding 9): the full local stack must stand
    // before the relay ever hears H. An oboe failure here returns with the
    // socket unpublished and un-greeted — it simply drops (FIN), and the relay
    // never creates pumps for a half-session.
    let path = AudioPath::new(RATE);
    let muted = Arc::new(AtomicBool::new(l.muted.load(Ordering::Relaxed)));
    let (restart_tx, restart_rx) = std::sync::mpsc::channel::<()>();
    let out = match open_output(&path, muted.clone(), restart_tx.clone()) {
        Ok(o) => o,
        Err(e) => return SessionEnd::Failed(e),
    };
    let out_slot: Arc<Mutex<Option<AudioStreamAsync<Output, RemoteOutput>>>> =
        Arc::new(Mutex::new(Some(out)));

    // The session's shared handle + its dedicated writer thread (audit finding
    // 2): from here on, the writer is the ONLY thread that touches the socket's
    // write half. JNI callers enqueue fail-fast; K self-generates in the writer
    // on its own 2 s cadence (a full queue can never starve liveness) and H is
    // coalesced through the dirty flag (unlosable).
    let shutdown_clone = match stream.try_clone() {
        Ok(s) => s,
        Err(e) => return SessionEnd::Failed(format!("clone shutdown handle: {e}")),
    };
    let (writer_tx, writer_rx) = std::sync::mpsc::sync_channel::<WriterCmd>(32);
    let shared = Arc::new(SessionShared {
        session_gen: my_gen,
        cancel: AtomicBool::new(false),
        sock: shutdown_clone,
        writer_tx,
        hello_dirty: AtomicBool::new(false),
    });

    // Publish under the generation gate (audit finding 1). A session that lost
    // the race while blocked in connect_timeout retires itself here instead of
    // stomping its successor's live link.
    {
        let mut cur = plock(&l.current);
        if l.quit.load(Ordering::Relaxed) || l.generation.load(Ordering::SeqCst) != my_gen {
            drop(cur);
            let _ = stream.shutdown(std::net::Shutdown::Both);
            *out_slot.lock().unwrap() = None;
            return SessionEnd::Quit;
        }
        if let Some(old) = cur.take() {
            old.trip(); // never silently orphan a predecessor's socket
        }
        *cur = Some(shared.clone());
    }

    // RAII from here: every return path (and any debug-build unwind) runs the
    // ordered teardown via Session::drop.
    let mut session = Session {
        shared: shared.clone(),
        path: path.clone(),
        parts: SessionParts::default(),
        out_slot: out_slot.clone(),
    };
    session.parts.writer = {
        let shared_w = shared.clone();
        let shared_f = shared.clone();
        std::thread::Builder::new()
            .name("phosphor-remote-writer".into())
            .spawn(move || {
                bridge_core::run_writer(
                    stream,
                    writer_rx,
                    &shared_w.cancel,
                    &shared_w.hello_dirty,
                    || bridge_core::encode_frame(b'H', hello_json().as_bytes()),
                    Duration::from_secs(2),
                    || {
                        bridge_core::encode_frame(
                            b'K',
                            format!(r#"{{"ts_ms":{}}}"#, now_ms()).as_bytes(),
                        )
                    },
                    move |e| {
                        // A dead write IS a dead session: trip FINs the socket,
                        // the reader wakes with EOF, the watchdog ends the run.
                        log::warn!("remote writer fatal: {e}");
                        shared_f.trip();
                    },
                )
            })
            .ok()
    };
    if session.parts.writer.is_none() {
        return SessionEnd::Failed("spawn writer thread".into());
    }

    // Route-change supervisor (audit findings 3 + 7): token-cancelled via
    // recv_timeout (it legitimately owns a restart_tx clone for reopened
    // streams, so channel-disconnect can never be its exit signal), coalesces
    // signal bursts, retries reopen on a bounded ladder, installs ONLY under
    // the slot lock with a post-open cancellation check, and on ladder
    // exhaustion trips the whole session — one audible reconnect, never
    // permanent silence behind a "streaming" state.
    session.parts.oboe = {
        let path = path.clone();
        let muted_flag = muted.clone();
        let out_slot = out_slot.clone();
        let restart_tx = restart_tx.clone();
        let shared_s = shared.clone();
        std::thread::Builder::new()
            .name("phosphor-remote-oboe".into())
            .spawn(move || {
                use std::sync::mpsc::RecvTimeoutError;
                const LADDER_MS: [u64; 6] = [0, 250, 500, 1000, 2000, 4000];
                'supervise: loop {
                    match restart_rx.recv_timeout(Duration::from_millis(250)) {
                        Ok(()) => {}
                        Err(RecvTimeoutError::Timeout) => {
                            if shared_s.cancelled() {
                                return;
                            }
                            continue;
                        }
                        Err(RecvTimeoutError::Disconnected) => return,
                    }
                    while restart_rx.try_recv().is_ok() {} // coalesce a burst
                    for (rung, wait) in LADDER_MS.iter().enumerate() {
                        if shared_s.cancelled() {
                            return;
                        }
                        if *wait > 0 {
                            let t0 = Instant::now();
                            while t0.elapsed() < Duration::from_millis(*wait) {
                                if shared_s.cancelled() {
                                    return;
                                }
                                match restart_rx.recv_timeout(Duration::from_millis(100)) {
                                    Ok(()) => {} // coalesce — this ladder run covers it
                                    Err(RecvTimeoutError::Disconnected) => return,
                                    Err(RecvTimeoutError::Timeout) => {}
                                }
                            }
                        }
                        match open_output(&path, muted_flag.clone(), restart_tx.clone()) {
                            Ok(new_out) => {
                                // Install gate UNDER the slot lock (finding 3's
                                // late-install race): teardown sets cancel BEFORE
                                // taking the slot, so an install that begins after
                                // teardown provably observes it; one that raced
                                // earlier gets taken and dropped by teardown.
                                let mut slot = plock(&out_slot);
                                if shared_s.cancelled() {
                                    drop(slot);
                                    drop(new_out); // outside the lock
                                    return;
                                }
                                *slot = Some(new_out);
                                drop(slot);
                                log::info!("remote oboe restarted after disconnect (rung {rung})");
                                continue 'supervise;
                            }
                            Err(e) => log::warn!("oboe reopen rung {rung} failed: {e}"),
                        }
                    }
                    log::error!(
                        "oboe reopen ladder exhausted — tripping session for a clean restart"
                    );
                    shared_s.trip();
                    return;
                }
            })
            .ok()
    };

    // Audio worker: bounded, drop-if-behind — audio can glitch, the scope never
    // stalls. Exits on cancel, on a closed ring (finding 5's waker), or when the
    // reader drops the sender. Depth 32 ≈ 320 ms: burst headroom so a TCP
    // batch never drops mid-burst frames (gaps!) — total latency is governed
    // by the ring's sustained catch-up, not this queue.
    let (audio_tx, audio_rx) = std::sync::mpsc::sync_channel::<Vec<f32>>(32);
    session.parts.audio = {
        let mut sink = path.sink();
        let shared_a = shared.clone();
        std::thread::Builder::new()
            .name("phosphor-remote-audio".into())
            .spawn(move || {
                while !shared_a.cancelled() {
                    match audio_rx.recv_timeout(Duration::from_millis(300)) {
                        Ok(buf) => {
                            if !sink.push(&buf) {
                                break; // path closed by teardown
                            }
                        }
                        Err(std::sync::mpsc::RecvTimeoutError::Timeout) => continue,
                        Err(_) => break,
                    }
                }
            })
            .ok()
    };

    // Scope worker: the oboe callback is the sole producer of finalized audio
    // chunks; this non-RT consumer is the only place that takes SampleRing's
    // mutex. It also drains-and-discards across stream-policy switches so a
    // later audio re-enable cannot surface stale visual history.
    session.parts.scope = {
        let mut tap = path.scope_tap();
        let mut buf = vec![0.0f32; path.scope.chunk_samples()];
        let shared_v = shared.clone();
        std::thread::Builder::new()
            .name("phosphor-remote-scope".into())
            .spawn(move || {
                while !shared_v.cancelled() {
                    let got = tap.pop_into(&mut buf);
                    if got == 0 {
                        std::thread::sleep(Duration::from_millis(1));
                        continue;
                    }
                    let l = link();
                    if l.cfg_audio.load(Ordering::Relaxed)
                        && !l.cfg_geometry.load(Ordering::Relaxed)
                    {
                        scope_ring().lock().unwrap().push_interleaved(&buf[..got]);
                    }
                }
            })
            .ok()
    };
    if session.parts.scope.is_none() {
        return SessionEnd::Failed("spawn remote scope worker".into());
    }

    // Reader thread: SO_RCVTIMEO 1 s + partial-progress reads, so teardown's
    // cancel is observed within a second even if a FIN goes missing.
    read_stream
        .set_read_timeout(Some(Duration::from_secs(1)))
        .ok();
    let reader_done = Arc::new(AtomicBool::new(false));
    let saw_w = Arc::new(AtomicBool::new(false));
    let saw_v1 = Arc::new(AtomicBool::new(false));
    session.parts.reader = {
        let done = reader_done.clone();
        let saw_w = saw_w.clone();
        let saw_v1 = saw_v1.clone();
        let shared_r = shared.clone();
        std::thread::Builder::new()
            .name("phosphor-remote".into())
            .spawn(move || {
                reader(shared_r, read_stream, audio_tx, saw_w, saw_v1);
                done.store(true, Ordering::Relaxed);
            })
            .ok()
    };

    // Policy flips gated on liveness (a disconnect that raced session setup must
    // not flip the deck on for a corpse).
    if l.quit.load(Ordering::Relaxed) || shared.cancelled() {
        return SessionEnd::Quit;
    }
    scope_ring().lock().unwrap().clear_pending();
    crate::deck::DECK_ACTIVE.store(true, Ordering::Relaxed);
    if l.cfg_geometry.load(Ordering::Relaxed) {
        let _ = crate::render::sender().send(crate::render::Cmd::GeometryActive(true));
    }
    l.last_rx_ms.store(monotonic_ms(), Ordering::Relaxed);
    log::info!("remote session up: {host}:{port} (gen {my_gen})");

    // Watchdog: K lives in the writer now; this loop only observes. Thresholds
    // are bridge_core's tested tables (3 s stalled / 10 s dead, monotonic).
    let mut was_streaming = false;
    loop {
        if l.quit.load(Ordering::Relaxed) || l.generation.load(Ordering::SeqCst) != my_gen {
            return SessionEnd::Quit;
        }
        if saw_v1.load(Ordering::Relaxed) {
            return SessionEnd::V1Relay;
        }
        if reader_done.load(Ordering::Relaxed) {
            // Audit finding 11: a run that reached streaming resets the backoff ladder
            // (report Healthy; the manager still reconnects, just without punishment).
            return if was_streaming {
                SessionEnd::Healthy
            } else {
                SessionEnd::Failed("link closed by peer".into())
            };
        }
        let quiet_ms = monotonic_ms().saturating_sub(l.last_rx_ms.load(Ordering::Relaxed));
        let st = l.state.load(Ordering::Relaxed);
        if st == ST_STREAMING {
            was_streaming = true;
        }
        match bridge_core::watchdog_action(quiet_ms, st == ST_STREAMING, st == ST_STALLED) {
            bridge_core::WatchdogAction::Dead => {
                return if was_streaming {
                    SessionEnd::Failed("10 s of silence — link presumed dead".into())
                } else {
                    SessionEnd::Failed("no welcome from the relay".into())
                };
            }
            bridge_core::WatchdogAction::MarkStalled => {
                l.state.store(ST_STALLED, Ordering::Relaxed);
            }
            bridge_core::WatchdogAction::MarkStreaming => {
                l.state.store(ST_STREAMING, Ordering::Relaxed);
            }
            bridge_core::WatchdogAction::Ok => {}
        }
        // Mirror the global mute onto this session's flag (JNI writes the global).
        muted.store(l.muted.load(Ordering::Relaxed), Ordering::Relaxed);
        // Mirror ring instrumentation into the JNI-readable atomics (1/tick).
        l.audio_buf_ms.store(path.buffered_ms(), Ordering::Relaxed);
        let (skips, skip_ms) = path.skips();
        l.audio_skips.store(skips, Ordering::Relaxed);
        l.audio_skip_ms.store(skip_ms, Ordering::Relaxed);
        l.scope_drops.store(path.scope_drops(), Ordering::Relaxed);
        std::thread::sleep(Duration::from_millis(250));
    }
}

/// Ordered, idempotent session teardown (audit findings 1, 3, 5). Every step's
/// position is load-bearing:
///   1 cancel+FIN gates all publishes and wakes blocked reads/writes
///   2 unpublish only OUR handle (Arc identity — never a successor's)
///   3 writer join (already unblocked by the FIN; 3 s > worst 2 s send timeout)
///   4 ring close+clear — unblocks a worker parked in push, purges ghost audio
///   5 reader join (FIN + 1 s SO_RCVTIMEO bound it)
///   6 audio worker join (300 ms recv + 200 ms push slice bound it)
///   7 scope worker join (1 ms poll bound; it never owns audible state)
///   8 supervisor join BEFORE the slot clear — else a mid-open supervisor could
///     resurrect a stream into an already-cleared slot (the late-install race)
///   9 take the stream under the lock, DROP IT OUTSIDE (oboe close can block
///     briefly while a callback drains — never under our lock)
/// Policy (DECK_ACTIVE / GeometryActive / meta) is NOT touched here — it lives
/// in disconnect() and terminal paths; the deck flag is shared with the local
/// deck and a deferred clear would stomp a freshly opened local session.
fn teardown_session(
    shared: &Arc<SessionShared>,
    path: &AudioPath,
    parts: &mut SessionParts,
    out_slot: &Arc<Mutex<Option<AudioStreamAsync<Output, RemoteOutput>>>>,
) {
    let l = link();
    shared.trip();
    {
        let mut cur = plock(&l.current);
        if matches!(cur.as_ref(), Some(c) if Arc::ptr_eq(c, shared)) {
            *cur = None;
        }
    }
    if let Some(h) = parts.writer.take() {
        bridge_core::bounded_join(
            h,
            "phosphor-remote-writer",
            Duration::from_secs(3),
            &l.leaked_threads,
        );
    }
    path.close();
    path.clear();
    if let Some(h) = parts.reader.take() {
        bridge_core::bounded_join(
            h,
            "phosphor-remote",
            Duration::from_secs(2),
            &l.leaked_threads,
        );
    }
    if let Some(h) = parts.audio.take() {
        bridge_core::bounded_join(
            h,
            "phosphor-remote-audio",
            Duration::from_secs(1),
            &l.leaked_threads,
        );
    }
    if let Some(h) = parts.scope.take() {
        bridge_core::bounded_join(
            h,
            "phosphor-remote-scope",
            Duration::from_secs(1),
            &l.leaked_threads,
        );
    }
    if let Some(h) = parts.oboe.take() {
        bridge_core::bounded_join(
            h,
            "phosphor-remote-oboe",
            Duration::from_secs(2),
            &l.leaked_threads,
        );
    }
    let stream = plock(out_slot).take();
    drop(stream); // outside the lock
}

fn reader(
    shared: Arc<SessionShared>,
    mut s: TcpStream,
    audio_tx: std::sync::mpsc::SyncSender<Vec<f32>>,
    saw_w: Arc<AtomicBool>,
    saw_v1: Arc<AtomicBool>,
) {
    let l = link();
    let mut hdr = [0u8; 5];
    let mut first = true;
    loop {
        // Partial-progress reads (bridge_core::read_fully): SO_RCVTIMEO wakes us
        // to observe cancellation without ever desyncing the frame stream.
        match bridge_core::read_fully(&mut s, &mut hdr, &shared.cancel) {
            Ok(true) => {}
            Ok(false) | Err(_) => break,
        }
        let tag = hdr[0];
        if first {
            first = false;
            if tag == b'W' {
                saw_w.store(true, Ordering::Relaxed);
            } else if tag == b'A' || tag == b'M' {
                saw_v1.store(true, Ordering::Relaxed);
                break;
            }
        }
        let len = u32::from_be_bytes([hdr[1], hdr[2], hdr[3], hdr[4]]) as usize;
        if len > 8 * 1024 * 1024 {
            log::error!("remote frame over 8 MiB cap — closing");
            break;
        }
        let mut payload = vec![0u8; len];
        match bridge_core::read_fully(&mut s, &mut payload, &shared.cancel) {
            Ok(true) => {}
            Ok(false) | Err(_) => break,
        }
        // Post-blocking-read gate (audit finding 1): a frame that arrived for a
        // retired session must not touch shared state — a reader that passed the
        // loop guard, then blocked, could otherwise publish one stale frame.
        if shared.cancelled() {
            break;
        }
        l.rx_bytes.fetch_add((5 + len) as u64, Ordering::Relaxed);
        l.last_rx_ms.store(monotonic_ms(), Ordering::Relaxed);
        match tag {
            b'W' => {
                if let Ok(txt) = String::from_utf8(payload) {
                    *l.slots.welcome.lock().unwrap() = txt;
                }
                l.state.store(ST_STREAMING, Ordering::Relaxed);
            }
            b'A' => {
                l.rx_a.fetch_add(1, Ordering::Relaxed);
                let mut f32buf = Vec::with_capacity(payload.len() / 2);
                for c in payload.chunks_exact(2) {
                    f32buf.push(i16::from_le_bytes([c[0], c[1]]) as f32 / 32768.0);
                }
                // No audible consumer exists in music-off/visualizer flows, so
                // receive-side scope feed is the explicit fallback. With audio
                // enabled the callback owns scope truth after jitter/zero-fill.
                if !l.cfg_audio.load(Ordering::Relaxed) && !l.cfg_geometry.load(Ordering::Relaxed) {
                    scope_ring().lock().unwrap().push_interleaved(&f32buf);
                }
                if audio_tx.try_send(f32buf).is_err() {
                    l.a_drops.fetch_add(1, Ordering::Relaxed);
                }
            }
            b'G' => {
                l.rx_g.fetch_add(1, Ordering::Relaxed);
                if let Ok(v) = serde_json::from_slice::<serde_json::Value>(&payload) {
                    let (tw, th) = v["trace_size"]
                        .as_array()
                        .and_then(|a| Some((a.first()?.as_f64()?, a.get(1)?.as_f64()?)))
                        .unwrap_or((1.0, 1.0));
                    let peak = v["peak"].as_f64().unwrap_or(0.6) as f32;
                    if let Some(line) = v["polyline"].as_array() {
                        let pts: Vec<[f32; 2]> = line
                            .iter()
                            .filter_map(|p| {
                                let a = p.as_array()?;
                                Some([
                                    (a.first()?.as_f64()? / tw.max(1.0)) as f32,
                                    (a.get(1)?.as_f64()? / th.max(1.0)) as f32,
                                ])
                            })
                            .collect();
                        if pts.len() >= 2 {
                            let _ = crate::render::sender().send(
                                crate::render::Cmd::GeometryFrame(crate::render::GeomFrame {
                                    points: pts,
                                    aspect: (tw / th.max(1.0)) as f32,
                                    intensity: (0.8 * peak.clamp(0.2, 1.0)).max(0.15),
                                }),
                            );
                        }
                    }
                }
            }
            b'M' => {
                if let Ok(txt) = String::from_utf8(payload) {
                    *l.slots.meta.lock().unwrap() = txt;
                    l.meta_gen.fetch_add(1, Ordering::Relaxed);
                }
            }
            b'S' => {
                if let Ok(txt) = String::from_utf8(payload) {
                    *l.slots.sources.lock().unwrap() = txt;
                    l.sources_gen.fetch_add(1, Ordering::Relaxed);
                }
            }
            b'L' => {
                if let Ok(txt) = String::from_utf8(payload) {
                    *l.slots.listing.lock().unwrap() = txt;
                    l.listing_gen.fetch_add(1, Ordering::Relaxed);
                }
            }
            b'R' => {
                if payload.len() >= 2 {
                    let hl = u16::from_be_bytes([payload[0], payload[1]]) as usize;
                    if payload.len() >= 2 + hl {
                        let id = serde_json::from_slice::<serde_json::Value>(&payload[2..2 + hl])
                            .ok()
                            .and_then(|v| v["id"].as_str().map(String::from))
                            .unwrap_or_default();
                        *l.slots.art.lock().unwrap() = Some(payload[2 + hl..].to_vec());
                        *l.slots.art_id.lock().unwrap() = id;
                        l.art_gen.fetch_add(1, Ordering::Relaxed);
                    }
                }
            }
            b'E' => {
                if let Ok(txt) = String::from_utf8(payload) {
                    log::warn!("relay error: {txt}");
                    *l.slots.error.lock().unwrap() = txt;
                }
            }
            b'K' => {
                // Heartbeat; while geometry streams it carries the desktop
                // scope's live truth (mode/gain/auto) for the honesty band.
                if let Ok(v) = serde_json::from_slice::<serde_json::Value>(&payload) {
                    *plock(&l.slots.scope) =
                        v.get("scope").map(|s| s.to_string()).unwrap_or_default();
                }
            }
            _ => {} // unknown: skipped (forward compatibility)
        }
    }
    log::info!("remote reader ended (gen {})", shared.session_gen);
}
