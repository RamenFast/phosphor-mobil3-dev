//! Capture: enumerate PipeWire outputs/apps via `pw-dump`, and pump one chosen
//! target as raw s16le stereo 48 kHz into fixed 1920-byte A-frames. Ids mirror
//! desktop phosphor's targets.rs: `device:<node>.monitor` for sinks, `app:<name>`
//! (`+`-suffixed on collision, announce order) for app streams.

use std::process::{Child, Command, Stdio};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::mpsc::{Sender, SyncSender};
use std::sync::{Arc, Mutex};
use std::thread::{self, JoinHandle};

use crate::proto::{self, A_FRAME};
use crate::session::{Counters, Ev};
use crate::util;

/// How to actually connect a capture stream.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum ConnectSpec {
    SinkMonitor { node_name: String },
    AppStream { serial: u64 },
}

#[derive(Clone)]
pub struct Source {
    pub id: String,
    pub kind: String, // "app" | "monitor"
    pub label: String,
    pub spec: ConnectSpec,
}

impl Source {
    pub fn entry(&self) -> proto::SourceEntry {
        proto::SourceEntry {
            id: self.id.clone(),
            kind: self.kind.clone(),
            label: self.label.clone(),
            available: true,
        }
    }
}

fn str_prop<'a>(props: &'a serde_json::Value, key: &str) -> Option<&'a str> {
    props.get(key).and_then(|v| v.as_str()).filter(|s| !s.is_empty())
}

/// Parse `pw-dump` once into the ordered source list (apps first in announce
/// order, then monitors sorted by label) — the same order the phone shows.
pub fn enumerate() -> Vec<Source> {
    let out = match Command::new("pw-dump").output() {
        Ok(o) => o.stdout,
        Err(_) => return Vec::new(),
    };
    let dump: serde_json::Value = match serde_json::from_slice(&out) {
        Ok(v) => v,
        Err(_) => return Vec::new(),
    };
    let Some(objs) = dump.as_array() else { return Vec::new() };

    let mut apps: Vec<Source> = Vec::new();
    let mut monitors: Vec<Source> = Vec::new();
    let mut seen_app_keys: Vec<String> = Vec::new();

    for o in objs {
        if o.get("type").and_then(|v| v.as_str()) != Some("PipeWire:Interface:Node") {
            continue;
        }
        let props = match o.get("info").and_then(|i| i.get("props")) {
            Some(p) => p,
            None => continue,
        };
        match str_prop(props, "media.class") {
            Some("Stream/Output/Audio") => {
                let serial = props
                    .get("object.serial")
                    .and_then(|v| v.as_u64().or_else(|| v.as_str().and_then(|s| s.parse().ok())));
                let Some(serial) = serial else { continue };
                let app_name = str_prop(props, "application.name");
                let media_name = str_prop(props, "media.name");
                // stable key: application.name, `+` on collision (announce order)
                let mut key = app_name
                    .map(|s| s.to_string())
                    .unwrap_or_else(|| format!("stream-{serial}"));
                while seen_app_keys.contains(&key) {
                    key.push('+');
                }
                seen_app_keys.push(key.clone());
                let body: Vec<&str> = [app_name, media_name].into_iter().flatten().collect();
                let label = if body.is_empty() {
                    format!("APP · stream #{serial}")
                } else {
                    format!("APP · {}", body.join(" — "))
                };
                apps.push(Source {
                    id: format!("app:{key}"),
                    kind: "app".into(),
                    label,
                    spec: ConnectSpec::AppStream { serial },
                });
            }
            Some("Audio/Sink") => {
                let Some(node_name) = str_prop(props, "node.name") else { continue };
                let desc = str_prop(props, "node.description").unwrap_or(node_name);
                monitors.push(Source {
                    id: format!("device:{node_name}.monitor"),
                    kind: "monitor".into(),
                    label: format!("OUT · {desc}"),
                    spec: ConnectSpec::SinkMonitor { node_name: node_name.to_string() },
                });
            }
            _ => {}
        }
    }
    monitors.sort_by(|a, b| a.label.cmp(&b.label));
    apps.into_iter().chain(monitors).collect()
}

/// Resolve a persisted id against the live graph, or None if it vanished.
pub fn resolve(id: &str) -> Option<ConnectSpec> {
    enumerate().into_iter().find(|s| s.id == id).map(|s| s.spec)
}

/// `device:<default-sink>.monitor` — the connect-time default.
pub fn default_monitor_id() -> Option<String> {
    let out = Command::new("pactl").arg("get-default-sink").output().ok()?;
    let sink = String::from_utf8_lossy(&out.stdout).trim().to_string();
    (!sink.is_empty()).then(|| format!("device:{sink}.monitor"))
}

fn spawn_child(spec: &ConnectSpec) -> std::io::Result<Child> {
    if util::tool_exists("pw-record") {
        let mut cmd = Command::new("pw-record");
        match spec {
            ConnectSpec::SinkMonitor { node_name } => {
                cmd.args(["--target", node_name, "-P", "{ stream.capture.sink = true }"]);
            }
            ConnectSpec::AppStream { serial } => {
                cmd.args(["--target", &serial.to_string()]);
            }
        }
        cmd.args(["--latency", "20ms", "--rate", "48000", "--channels", "2", "--format", "s16", "-"]);
        cmd.stdout(Stdio::piped()).stderr(Stdio::null()).spawn()
    } else {
        // Fallback: parec on a sink monitor only (no per-app path).
        let monitor = match spec {
            ConnectSpec::SinkMonitor { node_name } => format!("{node_name}.monitor"),
            ConnectSpec::AppStream { .. } => {
                return Err(std::io::Error::new(
                    std::io::ErrorKind::Unsupported,
                    "per-app capture needs pw-record",
                ));
            }
        };
        Command::new("parec")
            .args([
                "--format=s16le",
                "--rate=48000",
                "--channels=2",
                "--raw",
                "--latency-msec=20",
                "-d",
                &monitor,
            ])
            .stdout(Stdio::piped())
            .stderr(Stdio::null())
            .spawn()
    }
}

/// A running capture pump. Dropping/`stop()`-ing kills its child so no zombie
/// pw-record survives a source switch or a session teardown.
pub struct CapturePump {
    child: Arc<Mutex<Option<Child>>>,
    stopping: Arc<AtomicBool>,
    handle: Option<JoinHandle<()>>,
}

impl CapturePump {
    /// We asked it to stop (source switch / teardown) — kill quietly, no EOF event.
    pub fn stop(mut self) {
        self.stopping.store(true, Ordering::SeqCst);
        if let Some(mut c) = self.child.lock().unwrap().take() {
            let _ = c.kill();
        }
        if let Some(h) = self.handle.take() {
            let _ = h.join();
        }
    }
}

/// Spawn a capture pump feeding 1920-byte A-frames to the writer. On the child
/// dying on its own (app closed) it emits `Ev::CaptureEof` so the session can
/// fall back to the default monitor and push a fresh source list.
pub fn start(
    spec: &ConnectSpec,
    writer: SyncSender<Vec<u8>>,
    counters: Arc<Counters>,
    ctl: Sender<Ev>,
) -> std::io::Result<CapturePump> {
    let mut child = spawn_child(spec)?;
    let mut stdout = child.stdout.take().expect("piped stdout");
    let child = Arc::new(Mutex::new(Some(child)));
    let stopping = Arc::new(AtomicBool::new(false));

    let stopping_t = stopping.clone();
    let handle = thread::spawn(move || {
        use std::io::Read;
        let mut buf = [0u8; A_FRAME];
        loop {
            match stdout.read_exact(&mut buf) {
                Ok(()) => {
                    let frame = proto::encode_frame(proto::A, &buf);
                    match writer.try_send(frame) {
                        Ok(()) => {
                            counters.tx_a.fetch_add(1, Ordering::Relaxed);
                        }
                        Err(_) => {
                            counters.dropped_a.fetch_add(1, Ordering::Relaxed);
                        }
                    }
                }
                Err(_) => {
                    // EOF or read error: if we asked to stop, exit silently;
                    // otherwise the source vanished — signal a fallback.
                    if !stopping_t.load(Ordering::SeqCst) {
                        let _ = ctl.send(Ev::CaptureEof);
                    }
                    break;
                }
            }
        }
    });

    Ok(CapturePump { child, stopping, handle: Some(handle) })
}
