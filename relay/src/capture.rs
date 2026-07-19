//! Capture: enumerate PipeWire outputs/apps via `pw-dump`, and pump one chosen
//! target as raw s16le stereo 48 kHz into fixed 1920-byte A-frames. Ids mirror
//! desktop phosphor's targets.rs: `device:<node>.monitor` for sinks, `app:<name>`
//! (`+`-suffixed on collision, announce order) for app streams.

use std::process::{Child, Command, Output, Stdio};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::mpsc::{Sender, SyncSender};
use std::sync::{Arc, Mutex};
use std::thread::{self, JoinHandle};
use std::time::Duration;

use crate::proto::{self, A_FRAME};
use crate::session::{Counters, Ev};
use crate::util;

/// How to actually connect a capture stream.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum ConnectSpec {
    SinkMonitor { node_name: String, object_id: u64 },
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

/// Deadline-only guard for local, fast tools (pw-dump/pactl): a hung PipeWire
/// daemon gets its probe killed at 5 s instead of freezing the control loop
/// forever (audit finding 4's local-tool corner).
static NO_CANCEL: AtomicBool = AtomicBool::new(false);

/// Parse `pw-dump` once into the ordered source list (apps first in announce
/// order, then monitors sorted by label) — the same order the phone shows.
pub fn enumerate() -> Vec<Source> {
    let out = match util::run_cancellable(
        &mut Command::new("pw-dump"),
        Duration::from_secs(5),
        &NO_CANCEL,
    ) {
        Ok(o) => o.stdout,
        Err(_) => return Vec::new(),
    };
    parse_dump(&out)
}

/// The parser is split from the probe so source-choice tests never need a live
/// PipeWire graph. Sink object ids ride the same pw-dump pass as node names:
/// output switching must not re-dump a graph that can change between probes.
fn parse_dump(out: &[u8]) -> Vec<Source> {
    let dump: serde_json::Value = match serde_json::from_slice(out) {
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
                let object_id = o
                    .get("id")
                    .and_then(|v| v.as_u64().or_else(|| v.as_str().and_then(|s| s.parse().ok())));
                let Some(object_id) = object_id else { continue };
                let desc = str_prop(props, "node.description").unwrap_or(node_name);
                monitors.push(Source {
                    id: format!("device:{node_name}.monitor"),
                    kind: "monitor".into(),
                    label: format!("OUT · {desc}"),
                    spec: ConnectSpec::SinkMonitor { node_name: node_name.to_string(), object_id },
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
    let mut cmd = Command::new("pactl");
    cmd.arg("get-default-sink");
    let out = util::run_cancellable(&mut cmd, Duration::from_secs(5), &NO_CANCEL).ok()?;
    let sink = String::from_utf8_lossy(&out.stdout).trim().to_string();
    (!sink.is_empty()).then(|| format!("device:{sink}.monitor"))
}

/// A monitor-choice failure is a user-visible protocol error, so retain the
/// machine-actionable fix separately from the low-level command receipt.
pub struct OutputSwitchError {
    pub error: String,
    pub fix: String,
    pub detail: String,
}

fn checked_command(
    cmd: &mut Command,
    deadline: Duration,
    cancel: &AtomicBool,
) -> Result<Output, String> {
    let out = util::run_cancellable(cmd, deadline, cancel)?;
    if out.status.success() {
        Ok(out)
    } else {
        Err(format!("process exited with {}", out.status))
    }
}

fn sink_input_indices(stdout: &[u8]) -> Result<Vec<u32>, String> {
    String::from_utf8_lossy(stdout)
        .lines()
        .filter(|line| !line.trim().is_empty())
        .map(|line| {
            line.split_whitespace()
                .next()
                .ok_or_else(|| format!("missing sink-input index in {line:?}"))?
                .parse::<u32>()
                .map_err(|_| format!("invalid sink-input index in {line:?}"))
        })
        .collect()
}

/// Finding 14: choosing an output is an output switch, not merely a monitor
/// retarget. Make it the PipeWire default, then move every existing PulseAudio
/// sink-input because pinned/live streams do not follow a default change.
/// Every child uses the session's deadline+cancellation path (finding 4).
pub fn switch_output(spec: &ConnectSpec, cancel: &AtomicBool) -> Result<(), OutputSwitchError> {
    let ConnectSpec::SinkMonitor { node_name, object_id } = spec else {
        return Ok(()); // app-stream choices retain their capture-only behavior
    };

    let mut set_default = Command::new("wpctl");
    set_default.args(["set-default", &object_id.to_string()]);
    if let Err(detail) = checked_command(&mut set_default, Duration::from_secs(5), cancel) {
        return Err(OutputSwitchError {
            error: "could not make the chosen output the desktop default".into(),
            fix: "check that wpctl can control PipeWire, then reselect the output".into(),
            detail: format!("wpctl set-default {object_id}: {detail}"),
        });
    }

    let mut list = Command::new("pactl");
    list.args(["list", "short", "sink-inputs"]);
    let listed = checked_command(&mut list, Duration::from_secs(5), cancel).map_err(|detail| {
        OutputSwitchError {
            error: "could not enumerate currently playing desktop streams".into(),
            fix: "check that pactl can reach PipeWire, then reselect the output".into(),
            detail: format!("pactl list short sink-inputs: {detail}"),
        }
    })?;
    let inputs = sink_input_indices(&listed.stdout).map_err(|detail| OutputSwitchError {
        error: "could not read the currently playing desktop streams".into(),
        fix: "check pactl list short sink-inputs, then reselect the output".into(),
        detail,
    })?;

    let mut failed = Vec::new();
    for index in inputs {
        let mut move_input = Command::new("pactl");
        move_input.args(["move-sink-input", &index.to_string(), node_name]);
        if let Err(detail) = checked_command(&mut move_input, Duration::from_secs(5), cancel) {
            failed.push(format!("{index}: {detail}"));
        }
    }
    if !failed.is_empty() {
        return Err(OutputSwitchError {
            error: "could not move every playing desktop stream to the chosen output".into(),
            fix: "check that the sink is still available and pactl can move streams, then reselect the output".into(),
            detail: format!("pactl move-sink-input -> {node_name}: {}", failed.join("; ")),
        });
    }
    Ok(())
}

fn spawn_child(spec: &ConnectSpec) -> std::io::Result<Child> {
    if util::tool_exists("pw-record") {
        let mut cmd = Command::new("pw-record");
        match spec {
            ConnectSpec::SinkMonitor { node_name, .. } => {
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
            ConnectSpec::SinkMonitor { node_name, .. } => format!("{node_name}.monitor"),
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

/// A running capture pump. RAII (audit finding 8): DROPPING it — by any path,
/// including a panic unwind — kills its child and joins its thread, so no
/// zombie pw-record survives a source switch, a teardown, or a crash.
pub struct CapturePump {
    /// Session-scoped pump identity; EOF events carry it so a stale pump's
    /// death can never kill its replacement (audit finding 6).
    pub id: u64,
    child: Arc<Mutex<Option<Child>>>,
    stopping: Arc<AtomicBool>,
    handle: Option<JoinHandle<()>>,
}

impl Drop for CapturePump {
    fn drop(&mut self) {
        self.stopping.store(true, Ordering::SeqCst);
        if let Some(c) = self.child.lock().unwrap().as_mut() {
            let _ = c.kill(); // unblock the read; the pump thread wait()s it
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
    id: u64,
    writer: SyncSender<Vec<u8>>,
    counters: Arc<Counters>,
    ctl: Sender<Ev>,
) -> std::io::Result<CapturePump> {
    let mut child = spawn_child(spec)?;
    let mut stdout = child.stdout.take().expect("piped stdout");
    let child = Arc::new(Mutex::new(Some(child)));
    let stopping = Arc::new(AtomicBool::new(false));

    let (stopping_t, child_t) = (stopping.clone(), child.clone());
    let handle = thread::spawn(move || {
        use std::io::Read;
        use std::sync::mpsc::TrySendError;
        let mut buf = [0u8; A_FRAME];
        loop {
            match stdout.read_exact(&mut buf) {
                Ok(()) => {
                    let frame = proto::encode_frame(proto::A, &buf);
                    // Full vs Disconnected split (audit finding 12): a slow
                    // client drops ONE frame; a gone writer ends the pump —
                    // it must not spin counting drops against a corpse.
                    // tx_a is counted at the wire by the writer, not here.
                    match writer.try_send(frame) {
                        Ok(()) => {}
                        Err(TrySendError::Full(_)) => {
                            counters.dropped_a.fetch_add(1, Ordering::Relaxed);
                        }
                        Err(TrySendError::Disconnected(_)) => break,
                    }
                }
                Err(_) => {
                    // EOF or read error: if we asked to stop, exit silently;
                    // otherwise the source vanished — signal a fallback,
                    // tagged with OUR id so a stale EOF can't kill a
                    // replacement pump (audit finding 6).
                    if !stopping_t.load(Ordering::SeqCst) {
                        let _ = ctl.send(Ev::CaptureEof(id));
                    }
                    break;
                }
            }
        }
        // reap the child (whether it died on its own or we killed it) — no zombie.
        if let Some(mut c) = child_t.lock().unwrap().take() {
            let _ = c.wait();
        }
    });

    Ok(CapturePump { id, child, stopping, handle: Some(handle) })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pw_dump_sink_keeps_object_id_for_wpctl_without_a_second_dump() {
        let sources = parse_dump(
            br#"[
              {"id": 91, "type":"PipeWire:Interface:Node", "info":{"props":{
                "media.class":"Audio/Sink", "node.name":"alsa_output.usb",
                "node.description":"USB DAC"
              }}},
              {"id": 92, "type":"PipeWire:Interface:Node", "info":{"props":{
                "media.class":"Stream/Output/Audio", "object.serial":"404",
                "application.name":"Player", "media.name":"Music"
              }}}
            ]"#,
        );
        assert_eq!(sources.len(), 2);
        assert_eq!(sources[0].id, "app:Player");
        assert_eq!(
            sources[1].spec,
            ConnectSpec::SinkMonitor { node_name: "alsa_output.usb".into(), object_id: 91 }
        );
    }

    #[test]
    fn pactl_short_list_extracts_every_live_input_index() {
        let rows = b"42\tPipeWire\t-\t77\t12\ts16le 2ch 48000Hz\n73\tPipeWire\t-\t88\t13\n";
        assert_eq!(sink_input_indices(rows).unwrap(), vec![42, 73]);
        assert!(sink_input_indices(b"not-an-index PipeWire\n").is_err());
    }
}
