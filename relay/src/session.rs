//! One connected client, end to end. The core invariant lives here: a **single
//! writer thread** owns the socket's write half and pulls assembled frames from
//! an mpsc channel — producers never touch the socket, so v1's mid-frame
//! interleave corruption (two threads writing tag/len/payload over cloned
//! streams) is impossible by construction. Audio frames are counted-and-dropped
//! when the channel is full; control frames block. A liveness watchdog tears the
//! whole session — and every child process — down after 8 s of client silence.

use std::io::Write;
use std::net::{Shutdown, TcpStream};
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::mpsc::{self, Sender, SyncSender};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::Duration;

use crate::capture::{self, CapturePump};
use crate::config::Config;
use crate::geometry::{self, Geometry};
use crate::library::FileSession;
use crate::player::{self, ArtCache, PlayerSnapshot};
use crate::proto::{self, ReadErr};
use crate::util;

/// Frame counters surfaced in K stats and the probe receipt.
#[derive(Default)]
pub struct Counters {
    pub tx_a: AtomicU64,
    pub tx_g: AtomicU64,
    pub dropped_a: AtomicU64,
}

/// Internal control-loop events. Everything that mutates session state funnels
/// through this one channel, so `SessionState` needs no locking.
pub enum Ev {
    Client(u8, Vec<u8>),
    CaptureEof,
    FileEof,
    Tick,
    Watchdog,
    Disconnect,
}

/// A serve-stream NDJSON event line (stdout is the machine surface; println
/// locks, so lines from concurrent client threads never interleave).
pub fn serve_event(name: &str, extra: serde_json::Value) {
    let mut obj = serde_json::Map::new();
    obj.insert("event".into(), serde_json::Value::String(name.into()));
    if let serde_json::Value::Object(m) = extra {
        obj.extend(m);
    }
    obj.insert("ts".into(), serde_json::Value::String(util::iso8601_now()));
    println!("{}", serde_json::Value::Object(obj));
}

/// Shared, read-mostly handles the control loop and its handlers need.
struct Ctx {
    cfg: Arc<Config>,
    wtx: SyncSender<Vec<u8>>,
    ctl: Sender<Ev>,
    counters: Arc<Counters>,
    snap: Arc<Mutex<PlayerSnapshot>>,
    art: ArtCache,
}

impl Ctx {
    fn send(&self, tag: u8, payload: &[u8]) -> bool {
        self.wtx.send(proto::encode_frame(tag, payload)).is_ok()
    }
    fn error(&self, error: &str, fix: &str, context: serde_json::Value) {
        let _ = self.send(proto::E, &proto::error_frame(error, fix, context));
    }
}

struct SessionState {
    hello: bool,
    audio: bool,
    geometry: bool,
    fps: u32,
    selected: String,
    capture: Option<CapturePump>,
    file: Option<FileSession>,
    geo: Option<Geometry>,
    last_meta: Option<String>,
}

impl SessionState {
    fn in_file(&self) -> bool {
        self.file.is_some()
    }

    // ── capture lifecycle ────────────────────────────────────────────────────
    fn start_capture(&mut self, ctx: &Ctx) {
        self.stop_capture();
        let Some(spec) = capture::resolve(&self.selected) else {
            ctx.error(
                "selected source is not available",
                "pick a source again (send Q for the list)",
                serde_json::json!({ "selected": self.selected }),
            );
            return;
        };
        match capture::start(&spec, ctx.wtx.clone(), ctx.counters.clone(), ctx.ctl.clone()) {
            Ok(pump) => {
                self.capture = Some(pump);
                serve_event("capture-started", serde_json::json!({ "source": self.selected }));
            }
            Err(e) => ctx.error(
                "could not start capture",
                "check pw-record / the source, then reselect",
                serde_json::json!({ "detail": e.to_string() }),
            ),
        }
    }

    fn stop_capture(&mut self) {
        if let Some(p) = self.capture.take() {
            p.stop();
        }
    }

    /// pw-record died (app closed): warn, fall back to the default monitor, and
    /// push a fresh source list so the phone re-syncs its picker.
    fn on_capture_eof(&mut self, ctx: &Ctx) {
        if self.in_file() {
            return; // file mode owns audio; a stale capture EOF is moot
        }
        ctx.error(
            "capture source ended",
            "pick a source again — falling back to the default output",
            serde_json::json!({ "was": self.selected }),
        );
        self.stop_capture(); // join the dead pump's thread (which reaps the child)
        if let Some(def) = capture::default_monitor_id() {
            self.selected = def;
        }
        if self.audio {
            self.start_capture(ctx);
        }
        self.push_sources(ctx);
    }

    // ── geometry lifecycle ───────────────────────────────────────────────────
    fn start_geometry(&mut self, ctx: &Ctx) {
        self.stop_geometry();
        self.geo = Some(geometry::start(self.fps, ctx.wtx.clone(), ctx.counters.clone()));
        serve_event("geometry-started", serde_json::json!({ "fps": self.fps }));
    }

    fn stop_geometry(&mut self) {
        if let Some(g) = self.geo.take() {
            g.stop();
        }
    }

    // ── frames out ───────────────────────────────────────────────────────────
    fn push_sources(&self, ctx: &Ctx) {
        let sources = capture::enumerate().iter().map(|s| s.entry()).collect();
        let body = serde_json::to_vec(&proto::Sources { sources, selected: self.selected.clone() })
            .unwrap_or_default();
        let _ = ctx.send(proto::S, &body);
    }

    fn tick_meta(&mut self, ctx: &Ctx) -> bool {
        let meta = if let Some(fs) = &self.file {
            fs.meta()
        } else {
            let s = ctx.snap.lock().unwrap();
            proto::Meta {
                title: s.title.clone(),
                artist: s.artist.clone(),
                album: s.album.clone(),
                playing: s.playing,
                position_ms: s.position_ms,
                duration_ms: s.duration_ms,
                can_seek: s.can_seek,
                source: "player".into(),
                art_id: s.art_id.clone(),
                path: None,
            }
        };
        let json = serde_json::to_string(&meta).unwrap_or_default();
        let playing = meta.playing;
        // M on change AND every 1 s while playing.
        if self.last_meta.as_deref() != Some(json.as_str()) || playing {
            self.last_meta = Some(json.clone());
            return ctx.wtx.send(proto::encode_frame(proto::M, json.as_bytes())).is_ok();
        }
        true
    }

    fn tick_stats(&self, ctx: &Ctx) -> bool {
        let body = serde_json::to_vec(&proto::Stats {
            ts_ms: util::now_ms(),
            tx_a: ctx.counters.tx_a.load(Ordering::Relaxed),
            tx_g: ctx.counters.tx_g.load(Ordering::Relaxed),
            dropped_a: ctx.counters.dropped_a.load(Ordering::Relaxed),
        })
        .unwrap_or_default();
        ctx.wtx.send(proto::encode_frame(proto::K, &body)).is_ok()
    }

    // ── client frames ────────────────────────────────────────────────────────
    fn on_hello(&mut self, ctx: &Ctx, payload: &[u8]) {
        let h: proto::Hello = match serde_json::from_slice(payload) {
            Ok(h) => h,
            Err(_) => return,
        };
        let first = !self.hello;
        self.hello = true;
        if first {
            serve_event("hello", serde_json::json!({ "audio": h.audio, "geometry": h.geometry }));
        }

        // audio toggle diff — only manages the live-capture pump (file mode owns
        // its own audio and keeps playing regardless).
        if h.audio != self.audio {
            self.audio = h.audio;
            if !self.in_file() {
                if self.audio {
                    self.start_capture(ctx);
                } else {
                    self.stop_capture();
                }
            }
        } else if first && self.audio && !self.in_file() {
            self.start_capture(ctx);
        }

        // geometry toggle diff (fps change while on → restart at the new rate).
        let fps_changed = h.geometry_fps != self.fps;
        self.fps = h.geometry_fps.clamp(1, 240);
        if h.geometry != self.geometry {
            self.geometry = h.geometry;
            if self.geometry {
                self.start_geometry(ctx);
            } else {
                self.stop_geometry();
            }
        } else if self.geometry && fps_changed {
            self.start_geometry(ctx);
        }
    }

    fn on_transport(&mut self, ctx: &Ctx, payload: &[u8]) {
        // JSON {cmd,ms} OR a bare v1 string ("next"/"prev"/"playpause").
        let (cmd, ms) = match serde_json::from_slice::<proto::Transport>(payload) {
            Ok(t) => (t.cmd, t.ms),
            Err(_) => (String::from_utf8_lossy(payload).trim().to_string(), None),
        };
        if let Some(fs) = &mut self.file {
            match cmd.as_str() {
                "playpause" => fs.toggle_paused(),
                "play" => fs.set_paused(false),
                "pause" => fs.set_paused(true),
                "next" => {
                    if let Ok(false) = fs.advance(1) {
                        self.last_meta = None;
                    }
                }
                "prev" => {
                    let _ = fs.advance(-1);
                }
                "seek" => {
                    if let Err((e, fix)) = fs.seek(ms.unwrap_or(0)) {
                        ctx.error(&e, &fix, serde_json::json!({}));
                    }
                }
                _ => {}
            }
        } else {
            player::transport(&ctx.cfg.player, &cmd, ms);
        }
    }

    fn on_choose(&mut self, ctx: &Ctx, payload: &[u8]) {
        let Ok(c) = serde_json::from_slice::<proto::Choose>(payload) else { return };
        if let Some(fs) = self.file.take() {
            fs.stop(); // choosing a live source exits file mode
        }
        self.selected = c.id;
        self.last_meta = None;
        if self.audio {
            self.start_capture(ctx);
        }
    }

    fn on_browse(&self, ctx: &Ctx, payload: &[u8]) {
        let Ok(b) = serde_json::from_slice::<proto::Browse>(payload) else { return };
        let Some(root) = ctx.cfg.find_root(&b.root) else {
            ctx.error("no such library root", "browse a configured root (see W.libraries)",
                serde_json::json!({ "root": b.root }));
            return;
        };
        match crate::library::list(root, &b.path) {
            Ok(listing) => {
                let _ = ctx.send(proto::L, &serde_json::to_vec(&listing).unwrap_or_default());
            }
            Err((e, fix)) => ctx.error(&e, &fix, serde_json::json!({ "root": b.root, "path": b.path })),
        }
    }

    fn on_play(&mut self, ctx: &Ctx, payload: &[u8]) {
        let Ok(p) = serde_json::from_slice::<proto::Play>(payload) else { return };
        if p.action == "stop" {
            if let Some(fs) = self.file.take() {
                fs.stop();
            }
            self.last_meta = None;
            if self.audio {
                self.start_capture(ctx); // resume the previously selected capture
            }
            return;
        }
        let Some(root) = ctx.cfg.find_root(&p.root).cloned() else {
            ctx.error("no such library root", "play from a configured root (see W.libraries)",
                serde_json::json!({ "root": p.root }));
            return;
        };
        // Entering file mode: the live capture stops; the file pump is the audio.
        self.stop_capture();
        if let Some(fs) = self.file.take() {
            fs.stop();
        }
        match FileSession::open(root, &p.path, ctx.wtx.clone(), ctx.counters.clone(), ctx.ctl.clone(), ctx.art.clone()) {
            Ok(fs) => {
                self.file = Some(fs);
                self.last_meta = None;
                serve_event("file-started", serde_json::json!({ "root": p.root, "path": p.path }));
            }
            Err((e, fix)) => {
                ctx.error(&e, &fix, serde_json::json!({ "root": p.root, "path": p.path }));
                if self.audio {
                    self.start_capture(ctx); // recover to live audio
                }
            }
        }
    }

    fn on_file_eof(&mut self, ctx: &Ctx) {
        if let Some(fs) = &mut self.file {
            match fs.advance(1) {
                Ok(true) => self.last_meta = None,     // playing the next track
                Ok(false) => self.last_meta = None,    // end of directory → M{playing:false}
                Err((e, fix)) => ctx.error(&e, &fix, serde_json::json!({})),
            }
        }
    }

    fn on_art(&self, ctx: &Ctx, payload: &[u8]) {
        let Ok(req) = serde_json::from_slice::<proto::ArtReq>(payload) else { return };
        let entry = ctx.art.lock().unwrap();
        let Some(e) = entry.get(&req.id) else {
            ctx.error("no art for that id", "request an art_id from a recent M frame",
                serde_json::json!({ "id": req.id }));
            return;
        };
        let bytes = match std::fs::read(&e.path) {
            Ok(b) => b,
            Err(_) => {
                ctx.error("art file vanished", "it will re-cache on the next track",
                    serde_json::json!({ "id": req.id }));
                return;
            }
        };
        // R = [u16 BE header_len][header json][raw image bytes]
        let header = serde_json::to_vec(&proto::ArtHeader { id: req.id.clone(), mime: e.mime.clone() })
            .unwrap_or_default();
        let mut body = Vec::with_capacity(2 + header.len() + bytes.len());
        body.extend_from_slice(&(header.len() as u16).to_be_bytes());
        body.extend_from_slice(&header);
        body.extend_from_slice(&bytes);
        let _ = ctx.send(proto::R, &body);
    }

    fn teardown(&mut self) {
        self.stop_capture();
        self.stop_geometry();
        if let Some(fs) = self.file.take() {
            fs.stop();
        }
    }
}

fn build_welcome(cfg: &Config, caps: proto::Caps, selected: &str) -> Vec<u8> {
    let libraries = cfg
        .libraries
        .iter()
        .map(|r| proto::LibraryInfo { id: r.id.clone(), label: r.label.clone(), path: r.display_path() })
        .collect();
    let w = proto::Welcome {
        proto: proto::PROTO,
        tool: proto::TOOL,
        version: proto::VERSION,
        host: util::hostname(),
        caps,
        selected: selected.to_string(),
        libraries,
    };
    serde_json::to_vec(&w).unwrap_or_default()
}

/// Run one client to completion. Spawns the writer, reader, timers, watchdog and
/// player poller, then owns the control loop until disconnect/silence, and tears
/// every thread and child process down before returning (the zombie law).
pub fn serve_client(stream: TcpStream, cfg: Arc<Config>, caps: proto::Caps, peer: String) {
    let _ = stream.set_nodelay(true);
    serve_event("client-connected", serde_json::json!({ "peer": peer }));

    let write_half = match stream.try_clone() {
        Ok(s) => s,
        Err(_) => return,
    };
    let read_half = match stream.try_clone() {
        Ok(s) => s,
        Err(_) => return,
    };
    let wd_stream = stream.try_clone().ok();

    // ── single writer thread ────────────────────────────────────────────────
    let (wtx, wrx) = mpsc::sync_channel::<Vec<u8>>(256);
    let writer = thread::spawn(move || {
        let mut w = write_half;
        // A write timeout is load-bearing: if the client stops reading, the
        // socket send buffer fills and a plain write_all would block forever —
        // shutdown() can't discard a full buffer. The timeout guarantees the
        // writer can always error out and exit, which drops the channel receiver
        // and unblocks every producer's send, so teardown can never wedge.
        let _ = w.set_write_timeout(Some(Duration::from_secs(5)));
        for frame in wrx {
            if w.write_all(&frame).is_err() {
                break;
            }
        }
    });

    // ── control channel + shared state ──────────────────────────────────────
    let (ctl, crx) = mpsc::channel::<Ev>();
    let counters = Arc::new(Counters::default());
    let snap = Arc::new(Mutex::new(PlayerSnapshot::default()));
    let art: ArtCache = Arc::new(Mutex::new(std::collections::HashMap::new()));
    let running = Arc::new(AtomicBool::new(true));
    let last_seen = Arc::new(AtomicU64::new(util::now_ms()));

    let selected = capture::default_monitor_id()
        .or_else(|| capture::enumerate().into_iter().find(|s| s.kind == "monitor").map(|s| s.id))
        .unwrap_or_else(|| "device:default.monitor".into());

    let ctx = Ctx { cfg: cfg.clone(), wtx: wtx.clone(), ctl: ctl.clone(), counters: counters.clone(), snap: snap.clone(), art: art.clone() };

    // W immediately; nothing else streams until H arrives.
    let _ = ctx.wtx.send(proto::encode_frame(proto::W, &build_welcome(&cfg, caps, &selected)));

    // ── reader thread ────────────────────────────────────────────────────────
    let (r_ctl, r_wtx, r_seen) = (ctl.clone(), wtx.clone(), last_seen.clone());
    let reader = thread::spawn(move || {
        let mut r = read_half;
        loop {
            match proto::read_frame(&mut r, proto::MAX_C2S) {
                Ok((tag, payload)) => {
                    r_seen.store(util::now_ms(), Ordering::Relaxed);
                    if r_ctl.send(Ev::Client(tag, payload)).is_err() {
                        break;
                    }
                }
                Err(ReadErr::Oversize(n)) => {
                    let body = proto::error_frame(
                        "frame exceeds the 64 KiB client limit",
                        "send smaller frames; the connection is closing",
                        serde_json::json!({ "declared_len": n }),
                    );
                    let _ = r_wtx.send(proto::encode_frame(proto::E, &body));
                    let _ = r_ctl.send(Ev::Disconnect);
                    break;
                }
                Err(ReadErr::Io) => {
                    let _ = r_ctl.send(Ev::Disconnect);
                    break;
                }
            }
        }
    });

    // ── 1 Hz tick ────────────────────────────────────────────────────────────
    let (t_ctl, t_run) = (ctl.clone(), running.clone());
    let ticker = thread::spawn(move || {
        while t_run.load(Ordering::Relaxed) {
            thread::sleep(Duration::from_millis(1000));
            if t_ctl.send(Ev::Tick).is_err() {
                break;
            }
        }
    });

    // ── liveness watchdog: 8 s of client silence → tear everything down ───────
    let (w_ctl, w_run, w_seen) = (ctl.clone(), running.clone(), last_seen.clone());
    let watchdog = thread::spawn(move || {
        while w_run.load(Ordering::Relaxed) {
            thread::sleep(Duration::from_millis(1000));
            if util::now_ms().saturating_sub(w_seen.load(Ordering::Relaxed)) > 8000 {
                if let Some(s) = &wd_stream {
                    let _ = s.shutdown(Shutdown::Both); // unblock the reader
                }
                let _ = w_ctl.send(Ev::Watchdog);
                break;
            }
        }
    });

    let poller = player::spawn_poller(cfg.player.clone(), snap.clone(), art.clone(), running.clone());

    // ── control loop ─────────────────────────────────────────────────────────
    let mut st = SessionState {
        hello: false,
        audio: false,
        geometry: false,
        fps: 60,
        selected,
        capture: None,
        file: None,
        geo: None,
        last_meta: None,
    };

    let mut reason = "client-disconnect";
    for ev in crx.iter() {
        match ev {
            Ev::Client(tag, payload) => match tag {
                proto::H => st.on_hello(&ctx, &payload),
                proto::T => st.on_transport(&ctx, &payload),
                proto::Q => st.push_sources(&ctx),
                proto::C => st.on_choose(&ctx, &payload),
                proto::B => st.on_browse(&ctx, &payload),
                proto::P => st.on_play(&ctx, &payload),
                proto::R => st.on_art(&ctx, &payload),
                proto::K => {} // ping: liveness already refreshed in the reader
                _ => {}        // unknown: read-and-skip, never error
            },
            Ev::Tick => {
                if st.hello && (!st.tick_stats(&ctx) || !st.tick_meta(&ctx)) {
                    break;
                }
            }
            Ev::CaptureEof => st.on_capture_eof(&ctx),
            Ev::FileEof => st.on_file_eof(&ctx),
            Ev::Watchdog => {
                reason = "client-timeout";
                break;
            }
            Ev::Disconnect => break,
        }
    }

    // ── teardown: stop children, unblock the reader, drop senders, join ──────
    running.store(false, Ordering::Relaxed);
    st.teardown(); // kill pumps / ffmpeg / phosphor-tap → no zombies
    let _ = stream.shutdown(Shutdown::Both); // unblock a still-reading reader
    drop(ctx); // drops the control-loop wtx clone
    drop(wtx);
    drop(ctl);
    let _ = writer.join(); // all wtx senders gone → writer loop ends
    let _ = reader.join();
    let _ = ticker.join();
    let _ = watchdog.join();
    let _ = poller.join();
    serve_event("client-ended", serde_json::json!({ "peer": peer, "reason": reason }));
}
