//! The desktop scope lane: read live state off phosphor's ctl socket (frozen
//! NDJSON protocol v1 — `{"op":"status"}` is a stable, documented shape) and
//! drive it via the `phosphor ctl` CLI (the CLI and server share ONE typed
//! grammar — desktop BUGLOG #16 — so shelling out is correct-by-construction
//! where hand-built wire args would be guesswork). Every touch is
//! deadline-bounded; absence of a running scope degrades to None/E+fix.

use std::io::{BufRead, BufReader, Write};
use std::os::unix::net::UnixStream;
use std::process::Command;
use std::sync::atomic::AtomicBool;
use std::time::Duration;

use crate::util;

fn sock_path() -> Option<std::path::PathBuf> {
    std::env::var_os("XDG_RUNTIME_DIR")
        .map(|d| std::path::PathBuf::from(d).join("phosphor/ctl.sock"))
        .filter(|p| p.exists())
}

/// One status round-trip on the ctl socket; None when the scope isn't running.
fn status_raw() -> Option<serde_json::Value> {
    let path = sock_path()?;
    let mut s = UnixStream::connect(&path).ok()?;
    s.set_read_timeout(Some(Duration::from_secs(2))).ok();
    s.set_write_timeout(Some(Duration::from_secs(2))).ok();
    s.write_all(b"{\"op\":\"status\"}\n").ok()?;
    let mut r = BufReader::new(s);
    let mut line = String::new();
    r.read_line(&mut line).ok()?;
    serde_json::from_str(&line).ok()
}

/// The phone's honesty subset (kept small — it rides every K frame).
pub fn status() -> Option<serde_json::Value> {
    let v = status_raw()?;
    let mut out = serde_json::Map::new();
    for key in ["running", "mode", "theme", "ui_style", "gain", "fps"] {
        if let Some(x) = v.get(key) {
            out.insert(key.to_string(), x.clone());
        }
    }
    (!out.is_empty()).then(|| serde_json::Value::Object(out))
}

const ALLOWED_VERBS: &[&str] = &["mode", "theme", "ui", "gain"];

/// Drive the scope. Returns Err((error, fix)) in the house error shape.
pub fn ctl(verb: &str, value: &str, cancel: &AtomicBool) -> Result<(), (String, String)> {
    if !ALLOWED_VERBS.contains(&verb) {
        return Err((
            format!("scope verb '{verb}' is not remote-drivable"),
            "use one of: mode, theme, ui, gain".into(),
        ));
    }
    if !util::tool_exists("phosphor") {
        return Err((
            "desktop phosphor is not installed on this host".into(),
            "install phosphor ≥4.7.1 (ctl gain) on the source machine".into(),
        ));
    }
    let mut cmd = Command::new("phosphor");
    cmd.args(["ctl", verb, value]);
    let out = util::run_cancellable(&mut cmd, Duration::from_secs(3), cancel)
        .map_err(|e| ("phosphor ctl failed".to_string(), e))?;
    let reply: serde_json::Value =
        serde_json::from_slice(&out.stdout).unwrap_or(serde_json::Value::Null);
    if reply.get("status").and_then(|s| s.as_str()) == Some("ok") {
        Ok(())
    } else {
        let err = reply
            .get("error")
            .and_then(|e| e.as_str())
            .unwrap_or("scope rejected the command")
            .to_string();
        let fix = reply
            .get("fix")
            .and_then(|f| f.as_str())
            .unwrap_or("start desktop phosphor (phosphor --background) and retry")
            .to_string();
        Err((err, fix))
    }
}
