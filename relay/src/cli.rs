//! The agent-first CLI surface (AGENT-CLI-STANDARD). One-shots carry the
//! `{status,tool,version,ts}` envelope and `fix` on every error; exits are
//! 0 ok / 2 unavailable / 3 bad-args / 4 runtime; isatty auto-switches pretty↔
//! compact JSON, `--json` forces it. Verbs: serve · sources · doctor · probe ·
//! config · schema (+ --help/--version).

use std::io::IsTerminal;
use std::net::{TcpListener, TcpStream};
use std::sync::mpsc;
use std::sync::Arc;
use std::thread;
use std::time::{Duration, Instant};

use serde_json::{json, Map, Value};

use crate::config::Config;
use crate::proto::{self, ReadErr};
use crate::session;
use crate::util;

// ── output + envelope ─────────────────────────────────────────────────────────

fn emit(v: &Value, force_json: bool) {
    if force_json || !std::io::stdout().is_terminal() {
        println!("{v}");
    } else {
        println!("{}", serde_json::to_string_pretty(v).unwrap_or_else(|_| v.to_string()));
    }
}

fn envelope(status: &str) -> Map<String, Value> {
    let mut m = Map::new();
    m.insert("status".into(), json!(status));
    m.insert("tool".into(), json!(proto::TOOL));
    m.insert("version".into(), json!(proto::VERSION));
    m.insert("ts".into(), json!(util::iso8601_now()));
    m
}

fn ok_out(mut fields: Map<String, Value>, force_json: bool) {
    let mut m = envelope("ok");
    m.append(&mut fields);
    emit(&Value::Object(m), force_json);
}

fn err_out(code: i32, error: &str, fix: &str, force_json: bool) -> ! {
    let mut m = envelope("error");
    m.insert("error".into(), json!(error));
    m.insert("fix".into(), json!(fix));
    emit(&Value::Object(m), force_json);
    std::process::exit(code);
}

// ── flag helpers ──────────────────────────────────────────────────────────────

struct Flags {
    map: Vec<(String, Option<String>)>,
    json: bool,
}

fn parse_flags(args: &[String]) -> Flags {
    let mut map = Vec::new();
    let mut json = false;
    let mut i = 0;
    while i < args.len() {
        let a = &args[i];
        if a == "--json" {
            json = true;
            i += 1;
        } else if let Some(key) = a.strip_prefix("--") {
            // value flags take the next token unless it looks like a flag
            let val = args.get(i + 1).filter(|v| !v.starts_with("--")).cloned();
            if val.is_some() {
                i += 2;
            } else {
                i += 1;
            }
            map.push((key.to_string(), val));
        } else {
            i += 1;
        }
    }
    Flags { map, json }
}

impl Flags {
    fn get(&self, key: &str) -> Option<&str> {
        self.map.iter().find(|(k, _)| k == key).and_then(|(_, v)| v.as_deref())
    }
}

// ── config load with CLI overrides ────────────────────────────────────────────

fn load_config(flags: &Flags) -> Config {
    let mut cfg = Config::load().unwrap_or_else(|e| {
        err_out(4, &format!("config is unreadable: {e}"), "fix the JSON or delete it to use defaults", flags.json)
    });
    if let Some(p) = flags.get("port").and_then(|v| v.parse().ok()) {
        cfg.port = p;
    }
    if let Some(pl) = flags.get("player") {
        cfg.player = pl.to_string();
    }
    cfg
}

// ── serve (default) ────────────────────────────────────────────────────────────

pub fn serve(args: &[String]) -> ! {
    let flags = parse_flags(args);
    let cfg = Arc::new(load_config(&flags));
    let caps = proto::Caps::detect();

    let listener = match TcpListener::bind(("0.0.0.0", cfg.port)) {
        Ok(l) => l,
        Err(e) => err_out(2, &format!("bind :{} failed: {e}", cfg.port), "pick a free --port", flags.json),
    };

    session::serve_event(
        "listening",
        json!({
            "host": util::hostname(),
            "port": cfg.port,
            "player": cfg.player,
            "caps": { "audio": caps.audio, "geometry": caps.geometry, "per_app": caps.per_app, "library": caps.library, "art": caps.art },
        }),
    );
    if std::io::stderr().is_terminal() {
        eprintln!("phosphor-relay {}: listening on 0.0.0.0:{} · player={}", proto::VERSION, cfg.port, cfg.player);
    }

    for conn in listener.incoming() {
        match conn {
            Ok(stream) => {
                let peer = stream.peer_addr().map(|a| a.to_string()).unwrap_or_default();
                let cfg = cfg.clone();
                thread::spawn(move || {
                    // catch_unwind is for the LOG LINE only (audit finding 8):
                    // cleanup is RAII — SessionState/pump Drops + the running
                    // guard fire during the unwind itself.
                    let p2 = peer.clone();
                    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(move || {
                        session::serve_client(stream, cfg, caps, peer)
                    }));
                    if res.is_err() {
                        session::serve_event(
                            "client-panicked",
                            json!({ "peer": p2, "fix": "session cleaned up by RAII; report this — a handler panicked" }),
                        );
                    }
                });
            }
            Err(e) => session::serve_event("error", json!({ "error": format!("accept: {e}"), "fix": "transient; the listener keeps running" })),
        }
    }
    std::process::exit(0);
}

// ── sources ────────────────────────────────────────────────────────────────────

pub fn sources(args: &[String]) -> ! {
    let flags = parse_flags(args);
    if !util::tool_exists("pw-dump") {
        err_out(2, "pw-dump is not installed", "install pipewire-utils (provides pw-dump)", flags.json);
    }
    let list: Vec<Value> = crate::capture::enumerate()
        .into_iter()
        .map(|s| json!({ "id": s.id, "kind": s.kind, "label": s.label }))
        .collect();
    let mut fields = Map::new();
    fields.insert("selected".into(), json!(crate::capture::default_monitor_id()));
    fields.insert("count".into(), json!(list.len()));
    fields.insert("sources".into(), json!(list));
    ok_out(fields, flags.json);
    std::process::exit(0);
}

// ── doctor ─────────────────────────────────────────────────────────────────────

fn tool_check(name: &str, fix: &str) -> Value {
    let ok = util::tool_exists(name);
    if ok {
        json!({ "check": name, "ok": true, "detail": "present on PATH" })
    } else {
        json!({ "check": name, "ok": false, "fix": fix })
    }
}

pub fn doctor(args: &[String]) -> ! {
    let flags = parse_flags(args);
    let cfg = Config::load().unwrap_or_default();
    let mut checks = vec![
        tool_check("pw-record", "install pipewire-utils (per-app + monitor capture)"),
        tool_check("pw-dump", "install pipewire-utils (source enumeration)"),
        tool_check("wpctl", "install wireplumber (desktop output switching)"),
        tool_check("pactl", "install pulseaudio-utils (move live streams between outputs)"),
        tool_check("parec", "install pulseaudio-utils (monitor-only capture fallback)"),
        tool_check("playerctl", "install playerctl (now-playing metadata + transport)"),
        tool_check("ffmpeg", "install ffmpeg (library file playback)"),
        tool_check("ffprobe", "install ffmpeg (file metadata)"),
        tool_check("curl", "install curl (remote cover art)"),
        tool_check("rclone", "install rclone + run: rclone config (cloud library roots)"),
        tool_check("phosphor", "install phosphor + run: phosphor --background (geometry stream)"),
    ];

    // config roots
    for r in &cfg.libraries {
        if r.is_rclone() {
            let ok = util::tool_exists("rclone");
            checks.push(json!({ "check": format!("root:{}", r.id), "ok": ok,
                "detail": format!("rclone remote {}", r.display_path()),
                "fix": if ok { Value::Null } else { json!("install rclone to use this root") } }));
        } else {
            let ok = std::path::Path::new(r.path.as_deref().unwrap_or("")).is_dir();
            checks.push(if ok {
                json!({ "check": format!("root:{}", r.id), "ok": true, "detail": r.display_path() })
            } else {
                json!({ "check": format!("root:{}", r.id), "ok": false, "fix": format!("create or fix the path: {}", r.display_path()) })
            });
        }
    }

    // port free?
    let port_ok = TcpListener::bind(("0.0.0.0", cfg.port)).is_ok();
    checks.push(if port_ok {
        json!({ "check": "port", "ok": true, "detail": format!("{} is free", cfg.port) })
    } else {
        json!({ "check": "port", "ok": false, "fix": format!("port {} is busy — stop the other relay or pick a free --port", cfg.port) })
    });

    let all_ok = checks.iter().all(|c| c.get("ok").and_then(|v| v.as_bool()).unwrap_or(false));
    let mut fields = Map::new();
    fields.insert("all_ok".into(), json!(all_ok));
    fields.insert("checks".into(), json!(checks));
    ok_out(fields, flags.json); // a report always succeeds; read all_ok
    std::process::exit(0);
}

// ── config ─────────────────────────────────────────────────────────────────────

pub fn config(args: &[String]) -> ! {
    let flags = parse_flags(args);
    let cfg = load_config(&flags);
    let mut fields = Map::new();
    fields.insert("path".into(), json!(Config::path().to_string_lossy()));
    fields.insert("config".into(), serde_json::to_value(&cfg).unwrap_or(Value::Null));
    ok_out(fields, flags.json);
    std::process::exit(0);
}

// ── probe (the end-to-end receipt tool: acts as a v2 client) ───────────────────

pub fn probe(args: &[String]) -> ! {
    let flags = parse_flags(args);
    let host = flags.get("host").unwrap_or("127.0.0.1").to_string();
    let port: u16 = flags.get("port").and_then(|v| v.parse().ok()).unwrap_or(45777);
    let seconds: u64 = flags.get("seconds").and_then(|v| v.parse().ok()).unwrap_or(3);
    let want_rms = flags.map.iter().any(|(k, _)| k == "rms");

    let mut stream = match TcpStream::connect((host.as_str(), port)) {
        Ok(s) => s,
        Err(e) => err_out(2, &format!("connect {host}:{port} failed: {e}"), "is phosphor-relay serving there? check host/port", flags.json),
    };
    let _ = stream.set_nodelay(true);

    // Send H: audio on, geometry off (this is the audio receipt path).
    let hello = json!({ "proto": 2, "client": "probe", "audio": true, "geometry": false, "geometry_fps": 60 });
    if proto::write_frame(&mut stream, proto::H, hello.to_string().as_bytes()).is_err() {
        err_out(4, "failed to send hello", "the relay closed the connection early", flags.json);
    }

    // Reader thread → channel; main collects until the deadline, pinging K.
    let rstream = stream.try_clone().unwrap();
    let (tx, rx) = mpsc::channel::<(u8, Vec<u8>)>();
    let reader = thread::spawn(move || {
        let mut r = rstream;
        loop {
            match proto::read_frame(&mut r, proto::MAX_S2C) {
                Ok(f) => {
                    if tx.send(f).is_err() {
                        break;
                    }
                }
                Err(ReadErr::Oversize(_)) | Err(ReadErr::Io) => break,
            }
        }
    });

    let (mut a, mut g, mut m, mut k, mut other) = (0u64, 0u64, 0u64, 0u64, 0u64);
    let mut bytes = 0u64;
    let mut welcome = Value::Null;
    let mut peak = 0.0f64;
    let mut sumsq = 0.0f64;
    let mut nsamp = 0u64;

    let start = Instant::now();
    let deadline = start + Duration::from_secs(seconds);
    let mut last_ping = start;
    loop {
        let now = Instant::now();
        if now >= deadline {
            break;
        }
        if now.duration_since(last_ping) >= Duration::from_secs(2) {
            let ping = json!({ "ts_ms": util::now_ms() });
            let _ = proto::write_frame(&mut stream, proto::K, ping.to_string().as_bytes());
            last_ping = now;
        }
        match rx.recv_timeout(Duration::from_millis(100)) {
            Ok((tag, payload)) => {
                bytes += 5 + payload.len() as u64;
                match tag {
                    proto::W => welcome = serde_json::from_slice(&payload).unwrap_or(Value::Null),
                    proto::A => {
                        a += 1;
                        if want_rms {
                            for ch in payload.chunks_exact(2) {
                                let s = i16::from_le_bytes([ch[0], ch[1]]) as f64 / 32768.0;
                                sumsq += s * s;
                                nsamp += 1;
                                peak = peak.max(s.abs());
                            }
                        }
                    }
                    proto::G => g += 1,
                    proto::M => m += 1,
                    proto::K => k += 1,
                    _ => other += 1,
                }
            }
            Err(mpsc::RecvTimeoutError::Timeout) => {}
            Err(mpsc::RecvTimeoutError::Disconnected) => break,
        }
    }
    let _ = stream.shutdown(std::net::Shutdown::Both);
    let _ = reader.join();

    let a_per_sec = a as f64 / seconds.max(1) as f64;
    let rms = if nsamp > 0 { (sumsq / nsamp as f64).sqrt() } else { 0.0 };

    let mut fields = Map::new();
    fields.insert("host".into(), json!(host));
    fields.insert("port".into(), json!(port));
    fields.insert("seconds".into(), json!(seconds));
    fields.insert("welcome".into(), welcome);
    fields.insert("frames".into(), json!({ "a": a, "g": g, "m": m, "k": k, "other": other }));
    fields.insert("bytes".into(), json!(bytes));
    fields.insert("a_per_sec".into(), json!((a_per_sec * 100.0).round() / 100.0));
    if want_rms {
        fields.insert("rms".into(), json!((rms * 10000.0).round() / 10000.0));
        fields.insert("rms_peak".into(), json!((peak * 10000.0).round() / 10000.0));
    }
    ok_out(fields, flags.json);
    std::process::exit(0);
}

// ── schema (self-description; carries the envelope per R5) ──────────────────────

pub fn schema(args: &[String]) -> ! {
    let flags = parse_flags(args);
    let contract = json!({
        "envelope": { "status": "ok|error", "tool": proto::TOOL, "version": proto::VERSION, "ts": "iso8601 with UTC offset", "on_error": ["error", "fix"] },
        "exit_codes": { "0": "ok", "2": "unavailable (missing tool / port busy)", "3": "bad arguments (usage shown)", "4": "runtime failure" },
        "wire": {
            "framing": "[1-byte type][4-byte BE u32 payload length][payload]",
            "port_default": 45777,
            "caps": { "client_to_server": proto::MAX_C2S, "server_to_client": proto::MAX_S2C },
            "a_frame_bytes": proto::A_FRAME,
            "connect_sequence": "server sends W on accept, then streams nothing until client H; then K+M always, A/G per toggles",
            "unknown_frames": "read-and-skip, never error",
            "liveness": "client pings K every 2 s; 8 s of silence tears the session + kills all child processes"
        },
        "frames_server_to_client": {
            "W(0x57)": "welcome json {proto,tool,version,host,caps,selected,libraries}",
            "A(0x41)": "raw s16le stereo 48kHz PCM, fixed 1920-byte (10 ms) frames",
            "G(0x47)": "phosphor tap frame json, verbatim, rate-capped",
            "M(0x4D)": "now-playing json {title,artist,album,playing,position_ms,duration_ms,can_seek,source,art_id,path}",
            "S(0x53)": "source list json {sources:[{id,kind,label,available}],selected}",
            "L(0x4C)": "library listing json {root,path,dirs,files:[{name,size}]}",
            "R(0x52)": "[u16 BE header_len][header json {id,mime}][raw image bytes]",
            "E(0x45)": "error json {error,fix,context}",
            "K(0x4B)": "stats json {ts_ms,tx_a,tx_g,dropped_a} every 1000 ms"
        },
        "frames_client_to_server": {
            "H(0x48)": "hello json {proto,client,audio,geometry,geometry_fps} — resendable; diffed live",
            "T(0x54)": "transport json {cmd:playpause|play|pause|next|prev|seek, ms?} OR bare v1 string",
            "Q(0x51)": "empty → reply S",
            "C(0x43)": "choose json {id} — kill+respawn capture pump, exits file mode",
            "B(0x42)": "browse json {root,path} → reply L (path is jailed)",
            "P(0x50)": "play json {root,path} OR {action:stop}",
            "R(0x52)": "art request json {id} → reply R",
            "K(0x4B)": "ping json {ts_ms} every 2 s"
        },
        "verbs": {
            "serve": "default; NDJSON events (event: listening|client-connected|hello|capture-started|file-started|geometry-started|client-ended|error) [--port --player]",
            "sources": "one-shot enumerated capture sources (pw-dump)",
            "doctor": "environment checks {check,ok,detail|fix} + all_ok",
            "probe": "act as a v2 client [--host --port --seconds --rms] → welcome + frame counts + a_per_sec + rms_peak",
            "config": "print the effective config",
            "schema": "this document",
            "--help / --version": "usage / version"
        },
        "config": {
            "path": Config::path().to_string_lossy(),
            "schema": { "port": "u16 (default 45777)", "player": "string (default spotify)",
                "libraries": "[{id,label,path} | {id,label,rclone:'remote:path'}]" }
        }
    });
    let mut m = envelope("ok");
    m.insert("schema".into(), contract);
    emit(&Value::Object(m), flags.json);
    std::process::exit(0);
}

// ── help / version ─────────────────────────────────────────────────────────────

pub fn help() -> ! {
    println!(
        "phosphor-relay {v} — stream a desktop's audio + player + geometry to the phosphor phone.\n\
         \n\
         usage: phosphor-relay <verb> [flags]\n\
         \n\
         verbs:\n\
         \x20 serve [--port N] [--player NAME]   run the relay (default verb); NDJSON events on stdout\n\
         \x20 sources                            list capturable sources (pw-dump)\n\
         \x20 doctor                             check the environment (tools, roots, port)\n\
         \x20 probe --host H [--port N]          connect as a client and report a receipt\n\
         \x20       [--seconds S] [--rms]\n\
         \x20 config                             print the effective config\n\
         \x20 schema                             full machine contract (frames, verbs, exits)\n\
         \x20 --help | --version\n\
         \n\
         every one-shot prints a {{status,tool,version,ts}} envelope; --json forces JSON; errors carry a fix.",
        v = proto::VERSION
    );
    std::process::exit(0);
}

pub fn version() -> ! {
    println!("{} {}", proto::TOOL, proto::VERSION);
    std::process::exit(0);
}

pub fn bad_verb(verb: &str) -> ! {
    // usage → stderr, error envelope → stdout, exit 3
    eprintln!("phosphor-relay: unknown verb '{verb}'. try: serve | sources | doctor | probe | config | schema | --help");
    err_out(3, &format!("unknown verb '{verb}'"), "run `phosphor-relay --help` for the verb list", false);
}
