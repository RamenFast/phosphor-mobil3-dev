//! MPRIS bridge over `playerctl` (+ `busctl` for CanSeek). Polls the configured
//! player at 1 Hz off the session thread, keeping a fresh snapshot the session
//! reads when emitting M. Metadata is parsed from plain `playerctl metadata`
//! lines — never assembled by format string, because titles hold arbitrary
//! characters. Art is cached to disk; the art id is the first 16 hex of the
//! url's SHA-256.

use std::collections::HashMap;
use std::path::PathBuf;
use std::process::Command;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::thread::{self, JoinHandle};
use std::time::Duration;

use crate::util;

#[derive(Clone, Default)]
pub struct PlayerSnapshot {
    pub title: String,
    pub artist: String,
    pub album: String,
    pub playing: bool,
    pub position_ms: u64,
    pub duration_ms: Option<u64>,
    pub can_seek: bool,
    pub art_id: Option<String>,
}

pub struct ArtEntry {
    pub path: PathBuf,
    pub mime: String,
}

/// art_id (16 hex) → cached file. Shared: the player poller and embedded-art
/// extraction both fill it; the session's R handler reads it.
pub type ArtCache = Arc<Mutex<HashMap<String, ArtEntry>>>;

/// Deadline-only guard for local tools (playerctl/curl): a hung MPRIS target
/// or stalled art fetch dies on its deadline instead of wedging the poller —
/// which serve_client joins at teardown (audit finding 4's poller tail).
static NO_CANCEL: std::sync::atomic::AtomicBool = std::sync::atomic::AtomicBool::new(false);

fn run(args: &[&str]) -> Option<String> {
    let mut cmd = Command::new(args[0]);
    cmd.args(&args[1..]);
    let out = crate::util::run_cancellable(&mut cmd, std::time::Duration::from_secs(5), &NO_CANCEL).ok()?;
    if !out.status.success() {
        return None;
    }
    Some(String::from_utf8_lossy(&out.stdout).trim().to_string())
}

/// `<player> <key> <value…>` → (key, value), whitespace-column tolerant.
fn parse_meta_line(line: &str) -> Option<(String, String)> {
    let rest = line.trim_end();
    let sp = rest.find(char::is_whitespace)?;
    let rest = rest[sp..].trim_start();
    let sp = rest.find(char::is_whitespace)?;
    let key = rest[..sp].to_string();
    let value = rest[sp..].trim().to_string();
    Some((key, value))
}

fn ext_and_mime(src: &str) -> (String, String) {
    let stem = src.split('?').next().unwrap_or(src);
    let ext = stem
        .rsplit('.')
        .next()
        .filter(|e| (1..=5).contains(&e.len()) && e.chars().all(|c| c.is_ascii_alphanumeric()))
        .unwrap_or("jpg")
        .to_lowercase();
    let mime = match ext.as_str() {
        "png" => "image/png",
        "webp" => "image/webp",
        "gif" => "image/gif",
        "bmp" => "image/bmp",
        _ => "image/jpeg",
    }
    .to_string();
    (ext, mime)
}

fn percent_decode(s: &str) -> String {
    let b = s.as_bytes();
    let mut out = Vec::with_capacity(b.len());
    let mut i = 0;
    while i < b.len() {
        if b[i] == b'%' && i + 2 < b.len() {
            if let Ok(v) = u8::from_str_radix(&s[i + 1..i + 3], 16) {
                out.push(v);
                i += 3;
                continue;
            }
        }
        out.push(b[i]);
        i += 1;
    }
    String::from_utf8_lossy(&out).into_owned()
}

/// Ensure `url`'s art is on disk and registered; returns its 16-hex art id.
pub fn cache_art(url: &str, cache: &ArtCache) -> Option<String> {
    if url.is_empty() {
        return None;
    }
    let full = util::sha256_hex(url.as_bytes());
    let art_id = full[..16].to_string();
    if cache.lock().unwrap().contains_key(&art_id) {
        return Some(art_id);
    }
    let (ext, mime) = ext_and_mime(url);
    let dir = util::cache_dir().join("art");
    let _ = std::fs::create_dir_all(&dir);
    let path = dir.join(format!("{full}.{ext}"));

    if !path.exists() {
        if let Some(fpath) = url.strip_prefix("file://") {
            let decoded = percent_decode(fpath);
            let bytes = std::fs::read(&decoded).ok()?;
            std::fs::write(&path, &bytes).ok()?;
        } else if url.starts_with("http://") || url.starts_with("https://") {
            let mut cmd = Command::new("curl");
            cmd.args(["-sL", "-o", &path.to_string_lossy(), url]);
            let out = crate::util::run_cancellable(&mut cmd, std::time::Duration::from_secs(10), &NO_CANCEL).ok()?;
            if !out.status.success() || std::fs::metadata(&path).map(|m| m.len() == 0).unwrap_or(true) {
                let _ = std::fs::remove_file(&path);
                return None;
            }
        } else {
            return None;
        }
    }
    cache.lock().unwrap().insert(art_id.clone(), ArtEntry { path, mime });
    Some(art_id)
}

/// Register an already-extracted art file (embedded cover from ffmpeg) under the
/// hash of its source key; returns the art id.
pub fn register_art(key: &str, path: PathBuf, mime: &str, cache: &ArtCache) -> String {
    let art_id = util::sha256_hex(key.as_bytes())[..16].to_string();
    cache.lock().unwrap().insert(art_id.clone(), ArtEntry { path, mime: mime.into() });
    art_id
}

fn query_can_seek(player: &str) -> bool {
    if !util::tool_exists("busctl") {
        return true;
    }
    run(&[
        "busctl",
        "--user",
        "get-property",
        &format!("org.mpris.MediaPlayer2.{player}"),
        "/org/mpris/MediaPlayer2",
        "org.mpris.MediaPlayer2.Player",
        "CanSeek",
    ])
    .map(|s| s.contains("true"))
    .unwrap_or(true)
}

/// Spawn the 1 Hz poller. Keeps `snap` fresh and art cached; never writes to the
/// socket itself (the session owns M emission, off this thread's latency).
pub fn spawn_poller(
    player: String,
    snap: Arc<Mutex<PlayerSnapshot>>,
    art: ArtCache,
    running: Arc<AtomicBool>,
) -> JoinHandle<()> {
    thread::spawn(move || {
        let mut can_seek_cache: Option<bool> = None;
        let mut last_art_url = String::new();
        while running.load(Ordering::Relaxed) {
            let status = run(&["playerctl", "-p", &player, "status"]).unwrap_or_default();
            let present = !status.is_empty();
            let playing = status.eq_ignore_ascii_case("Playing");

            if !present {
                can_seek_cache = None;
                *snap.lock().unwrap() = PlayerSnapshot::default();
                thread::sleep(Duration::from_millis(1000));
                continue;
            }
            if can_seek_cache.is_none() {
                can_seek_cache = Some(query_can_seek(&player));
            }

            let (mut title, mut artist, mut album) = (String::new(), String::new(), String::new());
            let (mut duration_ms, mut art_url) = (None, String::new());
            if let Some(meta) = run(&["playerctl", "-p", &player, "metadata"]) {
                for line in meta.lines() {
                    if let Some((k, v)) = parse_meta_line(line) {
                        match k.as_str() {
                            "xesam:title" => title = v,
                            "xesam:artist" => artist = v,
                            "xesam:album" => album = v,
                            "mpris:length" => {
                                duration_ms = v.parse::<u64>().ok().filter(|&n| n > 0).map(|us| us / 1000)
                            }
                            "mpris:artUrl" => art_url = v,
                            _ => {}
                        }
                    }
                }
            }
            let position_ms = run(&["playerctl", "-p", &player, "position"])
                .and_then(|s| s.parse::<f64>().ok())
                .map(|sec| (sec * 1000.0) as u64)
                .unwrap_or(0);

            let art_id = if art_url.is_empty() {
                None
            } else if art_url == last_art_url {
                Some(util::sha256_hex(art_url.as_bytes())[..16].to_string())
            } else {
                last_art_url = art_url.clone();
                cache_art(&art_url, &art)
            };

            *snap.lock().unwrap() = PlayerSnapshot {
                title,
                artist,
                album,
                playing,
                position_ms,
                duration_ms,
                can_seek: can_seek_cache.unwrap_or(true),
                art_id,
            };
            thread::sleep(Duration::from_millis(1000));
        }
    })
}

/// Drive the player over MPRIS. `seek` takes an absolute position in ms.
pub fn transport(player: &str, cmd: &str, ms: Option<u64>) {
    let verb = match cmd {
        "playpause" => "play-pause",
        "play" => "play",
        "pause" => "pause",
        "next" => "next",
        "prev" => "previous",
        "seek" => {
            let sec = ms.unwrap_or(0) as f64 / 1000.0;
            let mut cmd = Command::new("playerctl");
            cmd.args(["-p", player, "position", &format!("{sec:.3}")]);
            let _ = crate::util::run_cancellable(&mut cmd, std::time::Duration::from_secs(2), &NO_CANCEL);
            return;
        }
        _ => return,
    };
    let mut cmd = Command::new("playerctl");
    cmd.args(["-p", player, verb]);
    let _ = crate::util::run_cancellable(&mut cmd, std::time::Duration::from_secs(2), &NO_CANCEL);
}
