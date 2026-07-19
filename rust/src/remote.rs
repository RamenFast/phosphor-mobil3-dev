//! Remote source v2 (the Tailscale bridge, phone side). Speaks protocol v2 to
//! `phosphor-relay` (docs/BRIDGE.md): W/H handshake, K heartbeats both ways, A audio,
//! G geometry (drawn via the DSP-bypass path), M metadata w/ position/seek, S sources,
//! L listings, R art, E fix-bearing errors.
//!
//! Division of labor (the reconciled law): RUST owns link mechanics — connect timeout,
//! watchdog (3 s stalled / 10 s dead), reconnect backoff, generation-guarded threads,
//! the oboe route-change restart. The Android SERVICE owns policy — when to connect,
//! when to give up, and every surface the OS sees.

use std::io::{Read, Write};
use std::net::{TcpStream, ToSocketAddrs};
use std::sync::atomic::{AtomicBool, AtomicU8, AtomicU32, AtomicU64, Ordering};
use std::sync::{Arc, Mutex, OnceLock};
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use oboe::{
    AudioOutputCallback, AudioOutputStreamSafe, AudioStream, AudioStreamAsync,
    AudioStreamBuilder, DataCallbackResult, Error as OboeError, Output, PerformanceMode,
    SharingMode, Stereo, Usage,
};
use phosphor_audio::playback::AudibleRing;

use crate::deck::{RATE, scope_ring};

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
}

struct Link {
    host: Mutex<String>,
    port: AtomicU32,
    cfg_audio: AtomicBool,
    cfg_geometry: AtomicBool,
    muted: AtomicBool,
    state: AtomicU8,
    quit: AtomicBool,
    generation: AtomicU64,
    /// Generation-tagged: publish/teardown only touch the socket when the tag
    /// matches, so a stale session can never stomp its successor's link
    /// (audit finding 1). disconnect() takes it unconditionally (user intent).
    writer: Mutex<Option<(u64, TcpStream)>>,
    slots: Slots,
    meta_gen: AtomicU32,
    sources_gen: AtomicU32,
    listing_gen: AtomicU32,
    art_gen: AtomicU32,
    rx_bytes: AtomicU64,
    rx_a: AtomicU64,
    rx_g: AtomicU64,
    last_rx_ms: AtomicU64,
}

fn link() -> &'static Link {
    static L: OnceLock<Link> = OnceLock::new();
    L.get_or_init(|| Link {
        host: Mutex::new(String::new()),
        port: AtomicU32::new(45777),
        cfg_audio: AtomicBool::new(true),
        cfg_geometry: AtomicBool::new(false),
        muted: AtomicBool::new(false),
        state: AtomicU8::new(ST_IDLE),
        quit: AtomicBool::new(true),
        generation: AtomicU64::new(0),
        writer: Mutex::new(None),
        slots: Slots::default(),
        meta_gen: AtomicU32::new(0),
        sources_gen: AtomicU32::new(0),
        listing_gen: AtomicU32::new(0),
        art_gen: AtomicU32::new(0),
        rx_bytes: AtomicU64::new(0),
        rx_a: AtomicU64::new(0),
        rx_g: AtomicU64::new(0),
        last_rx_ms: AtomicU64::new(0),
    })
}

/// Wall clock — K payloads and logs ONLY. Liveness math uses monotonic_ms():
/// a wall-clock step must never kill a live link or immortalize a dead one
/// (audit finding 13).
fn now_ms() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis() as u64).unwrap_or(0)
}

/// Process-epoch monotonic milliseconds (CLOCK_MONOTONIC — immune to clock steps).
fn monotonic_ms() -> u64 {
    static EPOCH: OnceLock<Instant> = OnceLock::new();
    EPOCH.get_or_init(Instant::now).elapsed().as_millis() as u64
}

fn set_link_error(err: &str, fix: &str) {
    *link().slots.error.lock().unwrap() =
        format!(r#"{{"error":{},"fix":{}}}"#, json_str(err), json_str(fix));
}

fn json_str(s: &str) -> String {
    serde_json::to_string(s).unwrap_or_else(|_| "\"\"".into())
}

// ── Oboe output (with mute + route-change restart) ───────────────────────────
struct RemoteOutput {
    audible: Arc<AudibleRing>,
    scratch: Vec<f32>,
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
        let need = frames.len() * 2;
        self.scratch.resize(need, 0.0);
        let got = self.audible.pop_into(&mut self.scratch);
        self.scratch[got..need].fill(0.0);
        // Instant local mute: keep draining (no stale-buffer buildup), emit silence.
        let muted = self.muted.load(Ordering::Relaxed);
        for (i, f) in frames.iter_mut().enumerate() {
            if muted {
                f.0 = 0.0;
                f.1 = 0.0;
            } else {
                f.0 = self.scratch[2 * i];
                f.1 = self.scratch[2 * i + 1];
            }
        }
        DataCallbackResult::Continue
    }

    fn on_error_after_close(
        &mut self,
        _s: &mut dyn AudioOutputStreamSafe,
        error: OboeError,
    ) {
        // Buds connected / route died: AAudio kills the stream. Never reopen from the
        // callback — signal the supervisor.
        if matches!(error, OboeError::Disconnected) {
            let _ = self.restart_tx.send(());
        }
    }
}

fn open_output(
    audible: Arc<AudibleRing>,
    muted: Arc<AtomicBool>,
    restart_tx: std::sync::mpsc::Sender<()>,
) -> Result<AudioStreamAsync<Output, RemoteOutput>, String> {
    let mut out = AudioStreamBuilder::default()
        .set_performance_mode(PerformanceMode::LowLatency)
        .set_sharing_mode(SharingMode::Shared)
        .set_usage(Usage::Media)
        .set_sample_rate(RATE as i32)
        .set_format::<f32>()
        .set_channel_count::<Stereo>()
        .set_callback(RemoteOutput {
            audible,
            scratch: Vec::new(),
            muted,
            restart_tx,
        })
        .open_stream()
        .map_err(|e| format!("oboe open: {e}"))?;
    out.start().map_err(|e| format!("oboe start: {e}"))?;
    Ok(out)
}

// ── Wire helpers ─────────────────────────────────────────────────────────────
fn send_frame(tag: u8, payload: &[u8]) -> bool {
    let mut w = link().writer.lock().unwrap();
    if let Some((_, s)) = w.as_mut() {
        let len = (payload.len() as u32).to_be_bytes();
        let ok = s.write_all(&[tag]).is_ok()
            && s.write_all(&len).is_ok()
            && s.write_all(payload).is_ok()
            && s.flush().is_ok();
        if !ok {
            log::warn!("remote send tag {} failed", tag as char);
        }
        ok
    } else {
        false
    }
}

fn send_hello() {
    let l = link();
    let h = format!(
        r#"{{"proto":2,"client":"phosphor-mobil3/0.2.0","audio":{},"geometry":{},"geometry_fps":60}}"#,
        l.cfg_audio.load(Ordering::Relaxed),
        l.cfg_geometry.load(Ordering::Relaxed),
    );
    send_frame(b'H', h.as_bytes());
}

// ── Public API (JNI-facing) ──────────────────────────────────────────────────

/// Begin (or retarget) the link. Non-blocking: spawns the manager; observe via
/// status_json(). Returns false only if the manager thread could not spawn.
pub fn connect(host: &str, port: u16, audio: bool, geometry: bool) -> bool {
    let l = link();
    // Retire any prior session wholesale.
    disconnect();
    *l.host.lock().unwrap() = host.to_string();
    l.port.store(port as u32, Ordering::Relaxed);
    l.cfg_audio.store(audio, Ordering::Relaxed);
    l.cfg_geometry.store(geometry, Ordering::Relaxed);
    l.quit.store(false, Ordering::Relaxed);
    let my_gen = l.generation.fetch_add(1, Ordering::SeqCst) + 1;
    l.state.store(ST_CONNECTING, Ordering::Relaxed);
    std::thread::Builder::new()
        .name("phosphor-remote-mgr".into())
        .spawn(move || manager(my_gen))
        .is_ok()
}

pub fn disconnect() {
    let l = link();
    l.quit.store(true, Ordering::Relaxed);
    l.generation.fetch_add(1, Ordering::SeqCst); // invalidates all session threads
    if let Some((_, s)) = l.writer.lock().unwrap().take() {
        let _ = s.shutdown(std::net::Shutdown::Both);
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
    send_hello();
    let _ = crate::render::sender().send(crate::render::Cmd::GeometryActive(geometry));
}

pub fn set_muted(m: bool) {
    link().muted.store(m, Ordering::Relaxed);
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
    send_frame(b'T', payload.as_bytes());
    log::info!("remote transport: {payload}");
}

pub fn seek_ms(ms: u64) {
    send_frame(b'T', format!(r#"{{"cmd":"seek","ms":{ms}}}"#).as_bytes());
}

pub fn request_sources() {
    send_frame(b'Q', b"");
}

pub fn choose_source(id: &str) {
    send_frame(b'C', format!(r#"{{"id":{}}}"#, json_str(id)).as_bytes());
}

pub fn browse(root: &str, path: &str) {
    send_frame(
        b'B',
        format!(r#"{{"root":{},"path":{}}}"#, json_str(root), json_str(path)).as_bytes(),
    );
}

pub fn play_file(root: &str, path: &str) {
    send_frame(
        b'P',
        format!(r#"{{"root":{},"path":{}}}"#, json_str(root), json_str(path)).as_bytes(),
    );
}

pub fn stop_file() {
    send_frame(b'P', br#"{"action":"stop"}"#);
}

pub fn request_art(id: &str) {
    send_frame(b'R', format!(r#"{{"id":{}}}"#, json_str(id)).as_bytes());
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
    format!(
        r#"{{"state":{},"host":{},"port":{},"rx_bytes":{},"rx_a":{},"rx_g":{},"art_id":{},"meta_gen":{},"sources_gen":{},"listing_gen":{},"art_gen":{},"welcome":{},"last_error":{}}}"#,
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
        if welcome.is_empty() { "null".into() } else { welcome },
        if error.is_empty() { "null".into() } else { error },
    )
}

// ── The manager: connect → session → watchdog → reconnect, generation-guarded ──
fn manager(my_gen: u64) {
    let l = link();
    let mut backoff = 1u64;
    loop {
        if l.quit.load(Ordering::Relaxed) || l.generation.load(Ordering::SeqCst) != my_gen {
            return;
        }
        let host = l.host.lock().unwrap().clone();
        let port = l.port.load(Ordering::Relaxed) as u16;
        match run_session(my_gen, &host, port) {
            SessionEnd::Quit => return,
            SessionEnd::V1Relay => {
                l.state.store(ST_FAILED, Ordering::Relaxed);
                set_link_error(
                    "relay speaks protocol v1",
                    &format!("upgrade it: scripts/relay-install.sh --host {host}"),
                );
                return; // no point retrying a v1 peer
            }
            SessionEnd::Failed(e) => {
                if l.quit.load(Ordering::Relaxed)
                    || l.generation.load(Ordering::SeqCst) != my_gen
                {
                    return;
                }
                l.state.store(ST_RECONNECTING, Ordering::Relaxed);
                set_link_error(&e, "reconnecting with backoff — check the relay/tailnet if this persists");
                log::warn!("remote session ended ({e}); retrying in {backoff}s");
                let waited = Instant::now();
                while waited.elapsed() < Duration::from_secs(backoff) {
                    if l.quit.load(Ordering::Relaxed)
                        || l.generation.load(Ordering::SeqCst) != my_gen
                    {
                        return;
                    }
                    std::thread::sleep(Duration::from_millis(120));
                }
                backoff = (backoff * 2).min(15);
            }
            SessionEnd::Healthy => {
                backoff = 1; // a good run resets the ladder before the next drop
                l.state.store(ST_RECONNECTING, Ordering::Relaxed);
                std::thread::sleep(Duration::from_secs(1)); // never hot-loop a flapping peer
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

    let addr = match (host, port).to_socket_addrs().ok().and_then(|mut a| a.next()) {
        Some(a) => a,
        None => return SessionEnd::Failed(format!("resolve {host}: no address")),
    };
    let stream = match TcpStream::connect_timeout(&addr, Duration::from_secs(4)) {
        Ok(s) => s,
        Err(e) => return SessionEnd::Failed(format!("connect {host}:{port}: {e}")),
    };
    stream.set_nodelay(true).ok();
    // Interim finding-2 cap: a blackholed peer can stall a write at most 2 s per
    // syscall instead of forever. C2 (writer thread) removes socket I/O from
    // callers entirely; this bound just shrinks the window until then.
    stream.set_write_timeout(Some(Duration::from_secs(2))).ok();
    let read_stream = match stream.try_clone() {
        Ok(s) => s,
        Err(e) => return SessionEnd::Failed(format!("clone stream: {e}")),
    };

    // Audio plumbing FIRST (audit finding 9): the full local stack must stand
    // before the relay ever hears H. An oboe failure here returns with the
    // socket unpublished and un-greeted — it simply drops (FIN), and the relay
    // never creates pumps for a half-session.
    let audible = AudibleRing::new(RATE);
    let muted = Arc::new(AtomicBool::new(l.muted.load(Ordering::Relaxed)));
    let (restart_tx, restart_rx) = std::sync::mpsc::channel::<()>();
    let out = match open_output(audible.clone(), muted.clone(), restart_tx.clone()) {
        Ok(o) => o,
        Err(e) => return SessionEnd::Failed(e),
    };
    let out_slot: Arc<Mutex<Option<AudioStreamAsync<Output, RemoteOutput>>>> =
        Arc::new(Mutex::new(Some(out)));

    // Publish under the generation tag (audit finding 1). A session that lost the
    // race while blocked in connect_timeout retires itself here instead of
    // stomping its successor's live socket.
    {
        let mut w = l.writer.lock().unwrap();
        if l.quit.load(Ordering::Relaxed) || l.generation.load(Ordering::SeqCst) != my_gen {
            drop(w);
            let _ = stream.shutdown(std::net::Shutdown::Both);
            *out_slot.lock().unwrap() = None;
            return SessionEnd::Quit;
        }
        if let Some((_, old)) = w.take() {
            // Never silently overwrite — shut the prior generation's stream down.
            let _ = old.shutdown(std::net::Shutdown::Both);
        }
        *w = Some((my_gen, stream));
    }
    send_hello();

    // Route-change supervisor: reopen the stream when AAudio disconnects it.
    {
        let audible = audible.clone();
        let muted_flag = muted.clone();
        let out_slot = out_slot.clone();
        let restart_tx = restart_tx.clone();
        std::thread::Builder::new()
            .name("phosphor-remote-oboe".into())
            .spawn(move || {
                let l = link();
                while restart_rx.recv().is_ok() {
                    if l.quit.load(Ordering::Relaxed)
                        || l.generation.load(Ordering::SeqCst) != my_gen
                    {
                        break;
                    }
                    match open_output(audible.clone(), muted_flag.clone(), restart_tx.clone()) {
                        Ok(new_out) => {
                            *out_slot.lock().unwrap() = Some(new_out);
                            log::info!("remote oboe restarted after disconnect");
                        }
                        Err(e) => log::error!("oboe restart failed: {e}"),
                    }
                }
            })
            .ok();
    }

    // Audio writer: bounded, drop-if-behind — audio can glitch, the scope never stalls.
    let (audio_tx, audio_rx) = std::sync::mpsc::sync_channel::<Vec<f32>>(24);
    {
        let audible = audible.clone();
        std::thread::Builder::new()
            .name("phosphor-remote-audio".into())
            .spawn(move || {
                let l = link();
                while !l.quit.load(Ordering::Relaxed)
                    && l.generation.load(Ordering::SeqCst) == my_gen
                {
                    match audio_rx.recv_timeout(Duration::from_millis(300)) {
                        Ok(buf) => {
                            audible.push_blocking(&buf);
                        }
                        Err(std::sync::mpsc::RecvTimeoutError::Timeout) => continue,
                        Err(_) => break,
                    }
                }
            })
            .ok();
    }

    // Reader thread.
    let reader_done = Arc::new(AtomicBool::new(false));
    let saw_w = Arc::new(AtomicBool::new(false));
    let saw_v1 = Arc::new(AtomicBool::new(false));
    {
        let done = reader_done.clone();
        let saw_w = saw_w.clone();
        let saw_v1 = saw_v1.clone();
        std::thread::Builder::new()
            .name("phosphor-remote".into())
            .spawn(move || {
                reader(my_gen, read_stream, audio_tx, saw_w, saw_v1);
                done.store(true, Ordering::Relaxed);
            })
            .ok();
    }

    scope_ring().lock().unwrap().clear_pending();
    crate::deck::DECK_ACTIVE.store(true, Ordering::Relaxed);
    if l.cfg_geometry.load(Ordering::Relaxed) {
        let _ = crate::render::sender().send(crate::render::Cmd::GeometryActive(true));
    }
    l.last_rx_ms.store(monotonic_ms(), Ordering::Relaxed);
    log::info!("remote session up: {host}:{port} (gen {my_gen})");

    // Watchdog: K pings every 2 s; 3 s quiet = stalled; 10 s = dead.
    let mut last_ping = Instant::now();
    let mut was_streaming = false;
    loop {
        if l.quit.load(Ordering::Relaxed) || l.generation.load(Ordering::SeqCst) != my_gen {
            teardown_session(my_gen, &out_slot);
            return SessionEnd::Quit;
        }
        if saw_v1.load(Ordering::Relaxed) {
            teardown_session(my_gen, &out_slot);
            return SessionEnd::V1Relay;
        }
        if reader_done.load(Ordering::Relaxed) {
            teardown_session(my_gen, &out_slot);
            // Audit finding 11: a run that reached streaming resets the backoff ladder
            // (report Healthy; the manager still reconnects, just without punishment).
            return if was_streaming {
                SessionEnd::Healthy
            } else {
                SessionEnd::Failed("link closed by peer".into())
            };
        }
        if last_ping.elapsed() >= Duration::from_secs(2) {
            send_frame(b'K', format!(r#"{{"ts_ms":{}}}"#, now_ms()).as_bytes());
            last_ping = Instant::now();
        }
        let quiet_ms = monotonic_ms().saturating_sub(l.last_rx_ms.load(Ordering::Relaxed));
        let st = l.state.load(Ordering::Relaxed);
        if st == ST_STREAMING {
            was_streaming = true;
        }
        if quiet_ms > 10_000 {
            teardown_session(my_gen, &out_slot);
            return if was_streaming {
                SessionEnd::Failed("10 s of silence — link presumed dead".into())
            } else {
                SessionEnd::Failed("no welcome from the relay".into())
            };
        } else if quiet_ms > 3_000 && st == ST_STREAMING {
            l.state.store(ST_STALLED, Ordering::Relaxed);
        } else if quiet_ms <= 3_000 && st == ST_STALLED {
            l.state.store(ST_STREAMING, Ordering::Relaxed);
        }
        // Mirror the global mute onto this session's flag (JNI writes the global).
        muted.store(l.muted.load(Ordering::Relaxed), Ordering::Relaxed);
        std::thread::sleep(Duration::from_millis(250));
    }
}

fn teardown_session(
    my_gen: u64,
    out_slot: &Arc<Mutex<Option<AudioStreamAsync<Output, RemoteOutput>>>>,
) {
    let l = link();
    {
        // Take the socket only if it is still OURS (audit finding 1): a stale
        // teardown must never shut down a successor session's live link.
        let mut w = l.writer.lock().unwrap();
        if matches!(w.as_ref(), Some((g, _)) if *g == my_gen) {
            if let Some((_, s)) = w.take() {
                let _ = s.shutdown(std::net::Shutdown::Both);
            }
        }
    }
    *out_slot.lock().unwrap() = None; // drops + stops the oboe stream
}

fn reader(
    my_gen: u64,
    mut s: TcpStream,
    audio_tx: std::sync::mpsc::SyncSender<Vec<f32>>,
    saw_w: Arc<AtomicBool>,
    saw_v1: Arc<AtomicBool>,
) {
    let l = link();
    let mut hdr = [0u8; 5];
    let mut first = true;
    while !l.quit.load(Ordering::Relaxed) && l.generation.load(Ordering::SeqCst) == my_gen {
        if s.read_exact(&mut hdr).is_err() {
            break;
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
        if s.read_exact(&mut payload).is_err() {
            break;
        }
        // Post-blocking-read gate (audit finding 1): a frame that arrived for a
        // retired generation must not touch shared state — a reader that passed
        // the loop guard, then blocked, could otherwise publish one stale frame.
        if l.quit.load(Ordering::Relaxed) || l.generation.load(Ordering::SeqCst) != my_gen {
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
                // Geometry mode draws the desktop's beam — the scope ring rests then.
                if !l.cfg_geometry.load(Ordering::Relaxed) {
                    scope_ring().lock().unwrap().push_interleaved(&f32buf);
                }
                let _ = audio_tx.try_send(f32buf);
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
                        *l.slots.art.lock().unwrap() =
                            Some(payload[2 + hl..].to_vec());
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
            b'K' => {} // heartbeat — last_rx already updated
            _ => {}    // unknown: skipped (forward compatibility)
        }
    }
    log::info!("remote reader ended (gen {my_gen})");
}
