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
use crate::library::{FileSession, FileSessionResources};
use crate::player::{self, ArtCache, PlayerSnapshot};
use crate::proto::{self, ReadErr};
use crate::util;

/// Frame counters surfaced in K stats and the probe receipt.
#[derive(Default)]
pub struct Counters {
    pub tx_a: AtomicU64,
    pub tx_g: AtomicU64,
    pub dropped_a: AtomicU64,
    /// Loudness of the audio actually written to the wire, accumulated between K
    /// frames and drained by each one.
    ///
    /// Why this exists: without it a client cannot tell a silent source from a broken
    /// link. Both draw nothing, and the user has no way to know whether to check the
    /// desktop or the network. Measuring here in the writer means the number describes
    /// bytes that genuinely left the machine, not what a pump hoped to send.
    ///
    /// Fixed point (sum of squares scaled by RMS_SCALE) because these are atomics on a
    /// hot path and f64 has no lock-free add.
    pub rms_sumsq: AtomicU64,
    pub rms_samples: AtomicU64,
    pub rms_peak: AtomicU64,
}

/// Fixed-point scale for the RMS accumulators. Samples normalise to -1.0..1.0, so a
/// squared sample is at most 1.0 and this keeps six decimal digits of it.
pub const RMS_SCALE: f64 = 1_000_000.0;

/// Fold one A-frame payload (s16le) into the loudness accumulators.
///
/// Runs on the writer thread for every audio frame, so it stays allocation-free and
/// lock-free. A trailing odd byte is ignored rather than misread as half a sample.
pub fn accumulate_rms(counters: &Counters, pcm: &[u8]) {
    let mut sumsq = 0.0f64;
    let mut peak = 0.0f64;
    let mut samples = 0u64;
    for chunk in pcm.chunks_exact(2) {
        let raw = i16::from_le_bytes([chunk[0], chunk[1]]);
        // Scale by 32768 so i16::MIN maps to exactly -1.0 without overflowing.
        let sample = raw as f64 / 32768.0;
        sumsq += sample * sample;
        let magnitude = sample.abs();
        if magnitude > peak {
            peak = magnitude;
        }
        samples += 1;
    }
    if samples == 0 {
        return;
    }
    counters
        .rms_sumsq
        .fetch_add((sumsq * RMS_SCALE) as u64, Ordering::Relaxed);
    counters.rms_samples.fetch_add(samples, Ordering::Relaxed);
    // Keep the loudest peak seen since the last K frame drained the window.
    counters
        .rms_peak
        .fetch_max((peak * RMS_SCALE) as u64, Ordering::Relaxed);
}

/// Drain the loudness window and return `(rms, peak)` normalised to 0.0..1.0.
///
/// Draining rather than accumulating forever means each K frame describes the second it
/// covers, so a source that just fell silent reads as silent immediately instead of
/// being masked by a session-long average.
pub fn drain_rms(counters: &Counters) -> (f64, f64) {
    let sumsq = counters.rms_sumsq.swap(0, Ordering::Relaxed) as f64 / RMS_SCALE;
    let samples = counters.rms_samples.swap(0, Ordering::Relaxed);
    let peak = counters.rms_peak.swap(0, Ordering::Relaxed) as f64 / RMS_SCALE;
    if samples == 0 {
        return (0.0, 0.0);
    }
    ((sumsq / samples as f64).sqrt(), peak)
}

/// Six decimals is plenty for a 0.0..1.0 loudness reading and keeps float noise out of
/// the K frame.
fn round6(value: f64) -> f64 {
    (value * 1_000_000.0).round() / 1_000_000.0
}

/// Internal control-loop events. Everything that mutates session state funnels
/// through this one channel, so `SessionState` needs no locking. EOF events
/// carry their pump's id so stale pumps cannot stop or advance replacements.
/// Browse and fetch results carry a token so a
/// superseded job's answer is dropped, never applied.
pub enum Ev {
    Client(u8, Vec<u8>),
    CaptureEof(u64),
    FileEof(u64),
    Browsed {
        token: u64,
        result: Result<proto::Listing, crate::library::LibErr>,
    },
    DriveFetched {
        token: u64,
        root_id: String,
        path: String,
        result: Result<crate::library::Selection, crate::library::LibErr>,
    },
    Tick,
    Watchdog,
    Disconnect,
}

/// Drop guard: `running=false` fires on every serve_client exit, including panic
/// unwind, so the ticker, watchdog, and poller cannot outlive the session.
struct RunningGuard(Arc<AtomicBool>);
impl Drop for RunningGuard {
    fn drop(&mut self) {
        self.0.store(false, Ordering::Relaxed);
    }
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
    /// Pump-id source shared with FileSession's internal spawns.
    pump_ids: Arc<AtomicU64>,
    /// Set by watchdog, disconnect, and teardown. Every external command observes
    /// this flag within 25 ms, so it cannot hold the control loop indefinitely.
    cancel_ops: Arc<AtomicBool>,
    /// In-flight async jobs (browse / Drive prefetch) + their newest tokens.
    jobs: Vec<thread::JoinHandle<()>>,
    browse_token: u64,
    fetch_token: u64,
    /// Desktop scope state (ctl-socket probe), refreshed per tick while
    /// geometry streams — rides every K frame for the phone's honesty lane.
    scope_state: Option<serde_json::Value>,
}

/// A requested source becomes authoritative only after capture starts. Failure
/// keeps the last working source, which the next S frame must continue to mark.
fn selected_after_choose(previous: &str, requested: &str, succeeded: bool) -> String {
    if succeeded { requested } else { previous }.to_string()
}

/// Panic unwind still tears down the session graph. Pumps clean themselves up,
/// while this state cancels and joins asynchronous jobs.
impl Drop for SessionState {
    fn drop(&mut self) {
        self.teardown();
    }
}

impl SessionState {
    fn in_file(&self) -> bool {
        self.file.is_some()
    }

    // ── capture lifecycle ────────────────────────────────────────────────────
    fn spawn_capture(
        &self,
        ctx: &Ctx,
        spec: &capture::ConnectSpec,
    ) -> std::io::Result<CapturePump> {
        let id = self.pump_ids.fetch_add(1, Ordering::Relaxed) + 1;
        capture::start(
            spec,
            id,
            ctx.wtx.clone(),
            ctx.counters.clone(),
            ctx.ctl.clone(),
        )
    }

    fn start_capture(&mut self, ctx: &Ctx) {
        let Some(spec) = capture::resolve(&self.selected) else {
            ctx.error(
                "selected source is not available",
                "pick a source again (send Q for the list)",
                serde_json::json!({ "selected": self.selected }),
            );
            return;
        };
        match self.spawn_capture(ctx, &spec) {
            Ok(pump) => {
                // Spawn first, then replace, so failure preserves the working pump.
                self.capture = Some(pump);
                serve_event(
                    "capture-started",
                    serde_json::json!({ "source": self.selected }),
                );
            }
            Err(e) => ctx.error(
                "could not start capture",
                "check pw-record / the source, then reselect",
                serde_json::json!({ "detail": e.to_string() }),
            ),
        }
    }

    fn stop_capture(&mut self) {
        self.capture = None; // Drop kills + joins (RAII)
    }

    /// pw-record died (app closed): warn, fall back to the default monitor, and
    /// push a fresh source list so the phone re-syncs its picker.
    fn on_capture_eof(&mut self, ctx: &Ctx, id: u64) {
        // Only the active pump's EOF may change session state.
        if self.capture.as_ref().map(|p| p.id) != Some(id) {
            return;
        }
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
        self.geo = Some(geometry::start(
            self.fps,
            ctx.wtx.clone(),
            ctx.counters.clone(),
        ));
        serve_event("geometry-started", serde_json::json!({ "fps": self.fps }));
    }

    fn stop_geometry(&mut self) {
        self.geo = None; // Drop kills + joins (RAII)
    }

    // ── frames out ───────────────────────────────────────────────────────────
    fn push_sources(&self, ctx: &Ctx) {
        let inventory = capture::enumerate();
        self.push_source_inventory(ctx, &inventory);
    }

    fn push_source_inventory(&self, ctx: &Ctx, inventory: &[capture::Source]) {
        let sources = inventory.iter().map(|s| s.entry()).collect();
        let body = serde_json::to_vec(&proto::Sources {
            sources,
            selected: self.selected.clone(),
        })
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
            return ctx
                .wtx
                .send(proto::encode_frame(proto::M, json.as_bytes()))
                .is_ok();
        }
        true
    }

    fn tick_stats(&mut self, ctx: &Ctx) -> bool {
        // While the desktop scope feeds the beam, its live state (mode / gain /
        // auto) rides every K — the phone renders `auto · pc` from truth, not
        // a stale local multiplier (the autogain honesty ask).
        self.scope_state = if self.geometry {
            crate::scope::status()
        } else {
            None
        };
        // Drain the loudness window so this K describes the second it covers. Sent only
        // while audio streams: in a geometry-only session there is no audio to be silent
        // about, and a hard 0.0 would read as "gone quiet".
        let (rms_value, peak_value) = drain_rms(&ctx.counters);
        let (rms, rms_peak) = if self.audio {
            (Some(round6(rms_value)), Some(round6(peak_value)))
        } else {
            (None, None)
        };
        let body = serde_json::to_vec(&proto::Stats {
            ts_ms: util::now_ms(),
            tx_a: ctx.counters.tx_a.load(Ordering::Relaxed),
            tx_g: ctx.counters.tx_g.load(Ordering::Relaxed),
            dropped_a: ctx.counters.dropped_a.load(Ordering::Relaxed),
            rms,
            rms_peak,
            scope: self.scope_state.clone(),
        })
        .unwrap_or_default();
        ctx.wtx.send(proto::encode_frame(proto::K, &body)).is_ok()
    }

    /// V frame: the phone drives the desktop scope (mode/theme/ui/gain).
    fn on_scope_ctl(&mut self, ctx: &Ctx, payload: &[u8]) {
        let Ok(req) = serde_json::from_slice::<proto::ScopeCtl>(payload) else {
            return;
        };
        if let Err((e, fix)) = crate::scope::ctl(&req.verb, &req.value, &self.cancel_ops) {
            ctx.error(
                &e,
                &fix,
                serde_json::json!({ "verb": req.verb, "value": req.value }),
            );
        }
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
            serve_event(
                "hello",
                serde_json::json!({ "audio": h.audio, "geometry": h.geometry }),
            );
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
        let c = match serde_json::from_slice::<proto::Choose>(payload) {
            Ok(c) => c,
            Err(e) => {
                ctx.error(
                    "invalid source choice",
                    "send C as JSON with one source id from the latest S frame",
                    serde_json::json!({ "detail": e.to_string() }),
                );
                self.push_sources(ctx);
                return;
            }
        };
        self.file = None; // choosing a live source exits file mode (RAII stop)
        self.fetch_token += 1; // supersede any in-flight Drive fetch
        self.last_meta = None;

        // One PipeWire snapshot owns resolution, sink id, and the S echo. Re-probing
        // between phases could switch one graph while announcing another.
        let inventory = capture::enumerate();
        let previous = self.selected.clone();
        let previous_spec = inventory
            .iter()
            .find(|s| s.id == previous)
            .map(|s| s.spec.clone());
        let Some(chosen) = inventory.iter().find(|s| s.id == c.id).cloned() else {
            ctx.error(
                "chosen source is not available",
                "pick a source from the refreshed list",
                serde_json::json!({ "requested": c.id, "selected": previous }),
            );
            self.recover_previous_capture(ctx, previous_spec.as_ref());
            self.push_source_inventory(ctx, &inventory);
            return;
        };

        let switched_sink = match &chosen.spec {
            capture::ConnectSpec::SinkMonitor { node_name, .. } => {
                if let Err(e) = capture::switch_output(&chosen.spec, &self.cancel_ops) {
                    ctx.error(
                        &e.error,
                        &e.fix,
                        serde_json::json!({ "sink": node_name, "detail": e.detail }),
                    );
                    self.restore_previous_output(ctx, previous_spec.as_ref(), node_name);
                    self.recover_previous_capture(ctx, previous_spec.as_ref());
                    self.push_source_inventory(ctx, &inventory);
                    return;
                }
                serve_event("output-switched", serde_json::json!({ "sink": node_name }));
                Some(node_name.clone())
            }
            capture::ConnectSpec::AppStream { .. } => None,
        };

        let replacement = if self.audio {
            self.spawn_capture(ctx, &chosen.spec).map(Some)
        } else {
            Ok(None)
        };
        match replacement {
            Ok(pump) => {
                self.selected = selected_after_choose(&previous, &chosen.id, true);
                if let Some(pump) = pump {
                    self.capture = Some(pump); // Drop old pump only after replacement exists.
                    serve_event(
                        "capture-started",
                        serde_json::json!({ "source": self.selected }),
                    );
                }
            }
            Err(e) => {
                self.selected = selected_after_choose(&previous, &chosen.id, false);
                if let Some(sink) = switched_sink.as_deref() {
                    self.restore_previous_output(ctx, previous_spec.as_ref(), sink);
                }
                ctx.error(
                    "could not start capture for the chosen source",
                    "the previous source remains selected; check pw-record / the source, then reselect",
                    serde_json::json!({
                        "requested": chosen.id,
                        "selected": self.selected,
                        "detail": e.to_string(),
                    }),
                );

                self.recover_previous_capture(ctx, previous_spec.as_ref());
            }
        }
        // C always terminates in an S echo — success and every failure path —
        // because the phone caches S for both rows and the selected checkmark.
        self.push_source_inventory(ctx, &inventory);
    }

    fn restore_previous_output(
        &self,
        ctx: &Ctx,
        previous_spec: Option<&capture::ConnectSpec>,
        attempted_sink: &str,
    ) {
        let Some(spec @ capture::ConnectSpec::SinkMonitor { node_name, .. }) = previous_spec else {
            return; // an app capture remains valid independent of desktop output
        };
        if node_name == attempted_sink {
            return; // reselecting the current output has nothing to roll back
        }
        match capture::switch_output(spec, &self.cancel_ops) {
            Ok(()) => serve_event(
                "output-switch-rolled-back",
                serde_json::json!({ "sink": node_name, "attempted_sink": attempted_sink }),
            ),
            Err(e) => ctx.error(
                "could not restore the previous desktop output",
                &e.fix,
                serde_json::json!({
                    "sink": node_name,
                    "attempted_sink": attempted_sink,
                    "detail": e.detail,
                }),
            ),
        }
    }

    fn recover_previous_capture(
        &mut self,
        ctx: &Ctx,
        previous_spec: Option<&capture::ConnectSpec>,
    ) {
        // File mode owns audio with capture=None. If choosing a live source
        // fails after file teardown, rebuild the previous live pump so the
        // reverted S checkmark still names an actually running capture.
        if !self.audio || self.capture.is_some() {
            return;
        }
        let Some(spec) = previous_spec else { return };
        match self.spawn_capture(ctx, spec) {
            Ok(pump) => {
                self.capture = Some(pump);
                serve_event(
                    "capture-started",
                    serde_json::json!({ "source": self.selected, "recovered": true }),
                );
            }
            Err(recovery) => ctx.error(
                "could not restore the previous capture source",
                "pick another available source from the refreshed list",
                serde_json::json!({
                    "selected": self.selected,
                    "detail": recovery.to_string(),
                }),
            ),
        }
    }

    /// Browsing runs off the control loop so a slow remote listing cannot block
    /// K and M frames. The token drops superseded answers.
    fn on_browse(&mut self, ctx: &Ctx, payload: &[u8]) {
        let Ok(b) = serde_json::from_slice::<proto::Browse>(payload) else {
            return;
        };
        let Some(root) = ctx.cfg.find_root(&b.root) else {
            ctx.error(
                "no such library root",
                "browse a configured root (see W.libraries)",
                serde_json::json!({ "root": b.root }),
            );
            return;
        };
        self.browse_token += 1;
        let token = self.browse_token;
        let root = root.clone();
        let path = b.path.clone();
        let (ctl, cancel) = (ctx.ctl.clone(), self.cancel_ops.clone());
        self.jobs.push(thread::spawn(move || {
            let result = crate::library::list(&root, &path, &cancel);
            let _ = ctl.send(Ev::Browsed { token, result });
        }));
        self.reap_jobs();
    }

    fn on_browsed(
        &mut self,
        ctx: &Ctx,
        token: u64,
        result: Result<proto::Listing, crate::library::LibErr>,
    ) {
        if token != self.browse_token {
            return; // superseded — a newer browse owns the screen
        }
        match result {
            Ok(listing) => {
                let _ = ctx.send(proto::L, &serde_json::to_vec(&listing).unwrap_or_default());
            }
            Err((e, fix)) => ctx.error(&e, &fix, serde_json::json!({})),
        }
    }

    /// Join any jobs that already finished (their sends are in the queue) so
    /// the vec can't grow unboundedly across a long session.
    fn reap_jobs(&mut self) {
        let mut live = Vec::new();
        for j in self.jobs.drain(..) {
            if j.is_finished() {
                let _ = j.join();
            } else {
                live.push(j);
            }
        }
        self.jobs = live;
    }

    fn on_play(&mut self, ctx: &Ctx, payload: &[u8]) {
        if self.cancel_ops.load(Ordering::SeqCst) {
            return;
        }
        let Ok(p) = serde_json::from_slice::<proto::Play>(payload) else {
            return;
        };
        if p.action == "stop" {
            self.file = None; // RAII stop
            self.fetch_token += 1; // supersede any in-flight fetch
            self.last_meta = None;
            if self.audio {
                self.start_capture(ctx); // resume the previously selected capture
            }
            return;
        }
        let Some(root) = ctx.cfg.find_root(&p.root).cloned() else {
            ctx.error(
                "no such library root",
                "play from a configured root (see W.libraries)",
                serde_json::json!({ "root": p.root }),
            );
            return;
        };
        self.fetch_token += 1; // every accepted Play supersedes older remote fetches
        if root.is_rclone() {
            // Remote downloads run off the control loop. Live capture continues until
            // the file is ready, while the phone receives downloading metadata immediately.
            let token = self.fetch_token;
            let downloading = proto::Meta {
                title: p.path.rsplit('/').next().unwrap_or(&p.path).to_string(),
                playing: false,
                source: "file".into(),
                path: Some(p.path.clone()),
                can_seek: true,
                ..Default::default()
            };
            if let Ok(body) = serde_json::to_vec(&downloading) {
                let _ = ctx.wtx.try_send(proto::encode_frame(proto::M, &body));
            }
            let (ctl, cancel) = (ctx.ctl.clone(), self.cancel_ops.clone());
            let (root_id, path) = (p.root.clone(), p.path.clone());
            let root_c = root.clone();
            self.jobs.push(thread::spawn(move || {
                let result = crate::library::prefetch(&root_c, &path, &cancel);
                let _ = ctl.send(Ev::DriveFetched {
                    token,
                    root_id,
                    path,
                    result,
                });
            }));
            self.reap_jobs();
            return;
        }
        self.open_file(ctx, root, &p.root, &p.path, None);
    }

    /// Shared open path (local play + post-fetch Drive play — cache-hit fast).
    fn open_file(
        &mut self,
        ctx: &Ctx,
        root: crate::config::LibraryRoot,
        root_id: &str,
        path: &str,
        selection: Option<crate::library::Selection>,
    ) {
        // Entering file mode: the live capture stops; the file pump is the audio.
        self.stop_capture();
        self.file = None;
        let resources = FileSessionResources {
            writer: ctx.wtx.clone(),
            counters: ctx.counters.clone(),
            ctl: ctx.ctl.clone(),
            art: ctx.art.clone(),
            pump_ids: self.pump_ids.clone(),
            cancel: self.cancel_ops.clone(),
        };
        let opened = match selection {
            Some(selection) => FileSession::open_selection(root, selection, resources),
            None => FileSession::open(root, path, resources),
        };
        match opened {
            Ok(fs) => {
                self.file = Some(fs);
                self.last_meta = None;
                serve_event(
                    "file-started",
                    serde_json::json!({ "root": root_id, "path": path }),
                );
            }
            Err((e, fix)) => {
                ctx.error(
                    &e,
                    &fix,
                    serde_json::json!({ "root": root_id, "path": path }),
                );
                if self.audio {
                    self.start_capture(ctx); // recover to live audio
                }
            }
        }
    }

    fn on_drive_fetched(
        &mut self,
        ctx: &Ctx,
        token: u64,
        root_id: String,
        path: String,
        result: Result<crate::library::Selection, crate::library::LibErr>,
    ) {
        if token != self.fetch_token || self.cancel_ops.load(Ordering::SeqCst) {
            return; // superseded (newer play/stop/choose) — cache write is harmless
        }
        match result {
            Ok(selection) => {
                let Some(root) = ctx.cfg.find_root(&root_id).cloned() else {
                    return;
                };
                self.open_file(ctx, root, &root_id, &path, Some(selection));
            }
            Err((e, fix)) => {
                ctx.error(
                    &e,
                    &fix,
                    serde_json::json!({ "root": root_id, "path": path }),
                );
                if self.audio && self.capture.is_none() && !self.in_file() {
                    self.start_capture(ctx);
                }
            }
        }
    }

    fn on_file_eof(&mut self, ctx: &Ctx, id: u64) {
        // EOF from a replaced pump must not advance the active file session.
        if self.file.as_ref().and_then(|f| f.current_pump_id()) != Some(id) {
            return;
        }
        if let Some(fs) = &mut self.file {
            match fs.advance(1) {
                Ok(true) => self.last_meta = None,  // playing the next track
                Ok(false) => self.last_meta = None, // end of directory → M{playing:false}
                Err((e, fix)) => ctx.error(&e, &fix, serde_json::json!({})),
            }
        }
    }

    fn on_art(&self, ctx: &Ctx, payload: &[u8]) {
        let Ok(req) = serde_json::from_slice::<proto::ArtReq>(payload) else {
            return;
        };
        let entry = ctx.art.lock().unwrap();
        let Some(e) = entry.get(&req.id) else {
            ctx.error(
                "no art for that id",
                "request an art_id from a recent M frame",
                serde_json::json!({ "id": req.id }),
            );
            return;
        };
        let bytes = match std::fs::read(&e.path) {
            Ok(b) => b,
            Err(_) => {
                ctx.error(
                    "art file vanished",
                    "it will re-cache on the next track",
                    serde_json::json!({ "id": req.id }),
                );
                return;
            }
        };
        // R = [u16 BE header_len][header json][raw image bytes]
        let header = serde_json::to_vec(&proto::ArtHeader {
            id: req.id.clone(),
            mime: e.mime.clone(),
        })
        .unwrap_or_default();
        let mut body = Vec::with_capacity(2 + header.len() + bytes.len());
        body.extend_from_slice(&(header.len() as u16).to_be_bytes());
        body.extend_from_slice(&header);
        body.extend_from_slice(&bytes);
        let _ = ctx.send(proto::R, &body);
    }

    fn teardown(&mut self) {
        // Cancel first so external commands stop before their jobs are joined.
        self.cancel_ops.store(true, Ordering::SeqCst);
        self.stop_capture();
        self.stop_geometry();
        self.file = None;
        for j in self.jobs.drain(..) {
            let _ = j.join();
        }
    }
}

fn build_welcome(cfg: &Config, caps: proto::Caps, selected: &str) -> Vec<u8> {
    let libraries = cfg
        .libraries
        .iter()
        .map(|r| proto::LibraryInfo {
            id: r.id.clone(),
            label: r.label.clone(),
            path: r.display_path(),
        })
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
    // Depth 32 is about 320 ms of audio. Slow clients drop frames instead of
    // accumulating stale audio; callback-side catch-up absorbs the remainder.
    let (wtx, wrx) = mpsc::sync_channel::<Vec<u8>>(32);
    let w_counters = Arc::new(Counters::default());
    let writer = {
        let counters = w_counters.clone();
        thread::spawn(move || {
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
                // tx_a counts completed wire writes, not queued frames.
                if frame.first() == Some(&proto::A) {
                    counters.tx_a.fetch_add(1, Ordering::Relaxed);
                    accumulate_rms(&counters, &frame[proto::HEADER_LEN..]);
                }
            }
        })
    };

    // ── control channel + shared state ──────────────────────────────────────
    let (ctl, crx) = mpsc::channel::<Ev>();
    let counters = w_counters;
    let snap = Arc::new(Mutex::new(PlayerSnapshot::default()));
    let art: ArtCache = Arc::new(Mutex::new(std::collections::HashMap::new()));
    let running = Arc::new(AtomicBool::new(true));
    let _running_guard = RunningGuard(running.clone()); // fires even on unwind
    let cancel_ops = Arc::new(AtomicBool::new(false));
    // Liveness uses a monotonic clock so wall-clock changes cannot affect sessions.
    let last_seen = Arc::new(AtomicU64::new(util::mono_ms()));

    let selected = capture::default_monitor_id()
        .or_else(|| {
            capture::enumerate()
                .into_iter()
                .find(|s| s.kind == "monitor")
                .map(|s| s.id)
        })
        .unwrap_or_else(|| "device:default.monitor".into());

    let ctx = Ctx {
        cfg: cfg.clone(),
        wtx: wtx.clone(),
        ctl: ctl.clone(),
        counters: counters.clone(),
        snap: snap.clone(),
        art: art.clone(),
    };

    // W immediately; nothing else streams until H arrives.
    let _ = ctx.wtx.send(proto::encode_frame(
        proto::W,
        &build_welcome(&cfg, caps, &selected),
    ));

    // ── reader thread ────────────────────────────────────────────────────────
    let (r_ctl, r_wtx, r_seen, r_cancel) = (
        ctl.clone(),
        wtx.clone(),
        last_seen.clone(),
        cancel_ops.clone(),
    );
    let reader = thread::spawn(move || {
        let mut r = read_half;
        loop {
            match proto::read_frame(&mut r, proto::MAX_C2S) {
                Ok((tag, payload)) => {
                    r_seen.store(util::mono_ms(), Ordering::Relaxed);
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
                    r_cancel.store(true, Ordering::SeqCst); // kill in-flight externals
                    let _ = r_ctl.send(Ev::Disconnect);
                    break;
                }
                Err(ReadErr::Io) => {
                    // Client gone: cancel external work so the control loop can drain.
                    r_cancel.store(true, Ordering::SeqCst);
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
    let (w_ctl, w_run, w_seen, w_cancel) = (
        ctl.clone(),
        running.clone(),
        last_seen.clone(),
        cancel_ops.clone(),
    );
    let watchdog = thread::spawn(move || {
        while w_run.load(Ordering::Relaxed) {
            thread::sleep(Duration::from_millis(1000));
            if util::mono_ms().saturating_sub(w_seen.load(Ordering::Relaxed)) > 8000 {
                // Cancel before queueing teardown so any external command releases the
                // control loop before the event is processed.
                w_cancel.store(true, Ordering::SeqCst);
                if let Some(s) = &wd_stream {
                    let _ = s.shutdown(Shutdown::Both); // unblock the reader
                }
                let _ = w_ctl.send(Ev::Watchdog);
                break;
            }
        }
    });

    let poller = player::spawn_poller(
        cfg.player.clone(),
        snap.clone(),
        art.clone(),
        running.clone(),
    );

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
        pump_ids: Arc::new(AtomicU64::new(0)),
        cancel_ops: cancel_ops.clone(),
        jobs: Vec::new(),
        browse_token: 0,
        fetch_token: 0,
        scope_state: None,
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
                proto::V => st.on_scope_ctl(&ctx, &payload),
                proto::K => {} // ping: liveness already refreshed in the reader
                _ => {}        // unknown: read-and-skip, never error
            },
            Ev::Tick => {
                if st.hello && (!st.tick_stats(&ctx) || !st.tick_meta(&ctx)) {
                    break;
                }
            }
            Ev::CaptureEof(id) => st.on_capture_eof(&ctx, id),
            Ev::FileEof(id) => st.on_file_eof(&ctx, id),
            Ev::Browsed { token, result } => st.on_browsed(&ctx, token, result),
            Ev::DriveFetched {
                token,
                root_id,
                path,
                result,
            } => st.on_drive_fetched(&ctx, token, root_id, path, result),
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
    serve_event(
        "client-ended",
        serde_json::json!({ "peer": peer, "reason": reason }),
    );
}

#[cfg(test)]
mod tests {
    use super::{Counters, accumulate_rms, drain_rms, selected_after_choose};

    /// Build an s16le buffer from normalised samples.
    fn pcm(samples: &[f64]) -> Vec<u8> {
        samples
            .iter()
            .flat_map(|s| ((s * 32767.0) as i16).to_le_bytes())
            .collect()
    }

    #[test]
    fn silence_reads_as_zero_so_a_quiet_source_differs_from_a_dead_link() {
        // The whole point: frames are arriving, but there is nothing in them.
        let counters = Counters::default();
        accumulate_rms(&counters, &pcm(&[0.0; 128]));
        assert_eq!(drain_rms(&counters), (0.0, 0.0));
    }

    #[test]
    fn a_full_scale_square_wave_reads_as_full_scale() {
        // Every sample at |1.0| means RMS is 1.0, which pins the normalisation.
        let counters = Counters::default();
        accumulate_rms(&counters, &pcm(&[1.0, -1.0, 1.0, -1.0]));
        let (rms, peak) = drain_rms(&counters);
        assert!((rms - 1.0).abs() < 0.001, "rms was {rms}");
        assert!((peak - 1.0).abs() < 0.001, "peak was {peak}");
    }

    #[test]
    fn one_loud_transient_survives_a_quiet_average() {
        // A clipping source must not look calm because most samples were quiet.
        let counters = Counters::default();
        let mut samples = vec![0.0; 99];
        samples.push(1.0);
        accumulate_rms(&counters, &pcm(&samples));
        let (rms, peak) = drain_rms(&counters);
        assert!(rms < 0.2, "rms should stay low, was {rms}");
        assert!(
            (peak - 1.0).abs() < 0.001,
            "peak should be full scale, was {peak}"
        );
    }

    #[test]
    fn draining_resets_the_window_so_each_frame_describes_its_own_second() {
        // Without the reset, a source that just went quiet would keep reporting the
        // loudness of everything it had ever played.
        let counters = Counters::default();
        accumulate_rms(&counters, &pcm(&[1.0, -1.0, 1.0, -1.0]));
        assert!(drain_rms(&counters).0 > 0.9);

        accumulate_rms(&counters, &pcm(&[0.0; 64]));
        assert_eq!(
            drain_rms(&counters),
            (0.0, 0.0),
            "the previous window leaked"
        );
    }

    #[test]
    fn an_empty_window_is_zero_rather_than_a_division_by_zero() {
        assert_eq!(drain_rms(&Counters::default()), (0.0, 0.0));
    }

    #[test]
    fn a_trailing_odd_byte_is_ignored_rather_than_read_as_half_a_sample() {
        let counters = Counters::default();
        let mut buf = pcm(&[1.0, -1.0]);
        buf.push(0x7f);
        accumulate_rms(&counters, &buf);
        let (rms, _) = drain_rms(&counters);
        assert!((rms - 1.0).abs() < 0.001, "odd byte skewed rms to {rms}");
    }

    #[test]
    fn failed_choose_keeps_the_last_working_source_for_the_s_echo() {
        assert_eq!(
            selected_after_choose("device:old.monitor", "device:new.monitor", false),
            "device:old.monitor"
        );
        assert_eq!(
            selected_after_choose("device:old.monitor", "device:new.monitor", true),
            "device:new.monitor"
        );
    }
}

#[cfg(test)]
mod folder_session_tests {
    use super::*;
    use crate::library::{
        self,
        folder_tests::{WavTree, eof, pcm_frames},
    };
    use std::sync::mpsc::Receiver;

    fn harness(tree: &WavTree) -> (SessionState, Ctx, Receiver<Ev>, Receiver<Vec<u8>>) {
        let (wtx, frames) = mpsc::sync_channel(64);
        let (ctl, events) = mpsc::channel();
        let ctx = Ctx {
            cfg: Arc::new(Config {
                port: 0,
                player: String::new(),
                libraries: vec![tree.root.clone()],
            }),
            wtx,
            ctl,
            counters: Arc::new(Counters::default()),
            snap: Arc::new(Mutex::new(PlayerSnapshot::default())),
            art: Arc::new(Mutex::new(Default::default())),
        };
        let state = SessionState {
            hello: false,
            audio: false,
            geometry: false,
            fps: 60,
            selected: String::new(),
            capture: None,
            file: None,
            geo: None,
            last_meta: None,
            pump_ids: Arc::new(AtomicU64::new(0)),
            cancel_ops: Arc::new(AtomicBool::new(false)),
            jobs: vec![],
            browse_token: 0,
            fetch_token: 0,
            scope_state: None,
        };
        (state, ctx, events, frames)
    }

    fn play(state: &mut SessionState, ctx: &Ctx, path: &str) {
        state.on_play(
            ctx,
            &serde_json::to_vec(&serde_json::json!({"root":"fixture", "path":path})).unwrap(),
        );
    }

    fn current(state: &SessionState) -> (String, Option<u64>) {
        let file = state.file.as_ref().expect("actual FileSession opened");
        (file.meta().path.unwrap(), file.current_pump_id())
    }

    #[test]
    fn folder_session_play_and_real_eof_handlers_advance_nested_and_fence_superseded_ids() {
        let tree = WavTree::new("handler");
        for path in ["00.wav", "10-disc/01.wav", "20.wav"] {
            tree.wav(path);
        }
        let (mut state, ctx, events, frames) = harness(&tree);
        play(&mut state, &ctx, "");
        let first = current(&state);
        assert_eq!(first.0, "00.wav");
        let id1 = eof(&events);
        assert_eq!(first.1, Some(id1));
        pcm_frames(&frames);
        state.on_file_eof(&ctx, id1);
        let second = current(&state);
        assert_eq!(second.0, "10-disc/01.wav");
        assert_ne!(second.1, first.1);
        state.on_file_eof(&ctx, id1);
        assert_eq!(current(&state), second);
        let id2 = eof(&events);
        pcm_frames(&frames);
        state.on_file_eof(&ctx, id2);
        let third = current(&state);
        assert_eq!(third.0, "20.wav");
        state.on_file_eof(&ctx, id2);
        assert_eq!(current(&state), third);
        let id3 = eof(&events);
        pcm_frames(&frames);
        state.on_file_eof(&ctx, id3);
        assert_eq!(current(&state), ("20.wav".into(), None));
        assert!(!state.file.as_ref().unwrap().meta().playing);
        play(&mut state, &ctx, "00.wav");
        let replacement = current(&state);
        assert!(replacement.1.unwrap() > id3);
        state.on_file_eof(&ctx, id3);
        assert_eq!(current(&state), replacement);
        assert_eq!(eof(&events), replacement.1.unwrap());
        pcm_frames(&frames);
        assert!(state.capture.is_none() && state.geo.is_none());
    }

    #[test]
    fn folder_session_local_play_invalidates_remote_fetch_and_completion_keeps_prepared_queue() {
        let tree = WavTree::new("fetched");
        tree.wav("folder/00.wav");
        tree.wav("folder/nested/01.wav");
        tree.wav("new.wav");
        let (mut state, ctx, events, frames) = harness(&tree);
        state.fetch_token = 41;
        let stale = library::prefetch(&tree.root, "folder", &state.cancel_ops).unwrap();
        play(&mut state, &ctx, "new.wav");
        assert_eq!(state.fetch_token, 42);
        let selected = current(&state);
        state.on_drive_fetched(&ctx, 41, "fixture".into(), "folder".into(), Ok(stale));
        assert_eq!(current(&state), selected);
        assert_eq!(eof(&events), selected.1.unwrap());
        pcm_frames(&frames);
        let prepared = library::prefetch(&tree.root, "folder", &state.cancel_ops).unwrap();
        state.on_drive_fetched(&ctx, 42, "fixture".into(), "folder".into(), Ok(prepared));
        assert_eq!(current(&state).0, "folder/00.wav");
        let first = eof(&events);
        pcm_frames(&frames);
        state.on_file_eof(&ctx, first);
        assert_eq!(current(&state).0, "folder/nested/01.wav");
        let second = eof(&events);
        pcm_frames(&frames);
        state.on_file_eof(&ctx, second);
        assert_eq!(current(&state), ("folder/nested/01.wav".into(), None));
    }

    #[test]
    fn folder_session_cancelled_play_and_fetch_cannot_start_a_pump() {
        let tree = WavTree::new("handler-cancel");
        tree.wav("00.wav");
        let (mut state, ctx, events, frames) = harness(&tree);
        let prepared = library::prefetch(&tree.root, "", &state.cancel_ops).unwrap();
        state.cancel_ops.store(true, Ordering::SeqCst);
        play(&mut state, &ctx, "");
        state.on_drive_fetched(&ctx, 0, "fixture".into(), "".into(), Ok(prepared));
        assert!(state.file.is_none());
        assert_eq!(state.pump_ids.load(Ordering::Relaxed), 0);
        assert!(events.try_recv().is_err());
        assert!(frames.try_recv().is_err());
    }
}
