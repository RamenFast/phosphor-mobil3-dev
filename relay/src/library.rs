//! Library browsing + file playback. Roots come from config (a local `path` or
//! an `rclone` remote). Browsing is jailed (canonicalise + prefix check). A
//! played file is decoded by `ffmpeg` into the same 1920-byte A-frames as live
//! capture; pause is just "stop reading the pipe" (ffmpeg backpressures, so
//! resume is sample-exact); seek respawns with `-ss`; EOF auto-advances to the
//! next file in the sorted directory.

use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::mpsc::{Sender, SyncSender};
use std::sync::{Arc, Mutex};
use std::thread::{self, JoinHandle};
use std::time::Duration;

use crate::config::LibraryRoot;
use crate::player::{self, ArtCache};
use crate::proto::{self, A_FRAME};
use crate::session::{Counters, Ev};
use crate::util;

pub const AUDIO_EXTS: &[&str] = &["wav", "flac", "mp3", "ogg", "opus", "m4a", "aac", "aiff", "wv"];
const RCLONE_MAX_BYTES: u64 = 256 * 1024 * 1024;

pub type LibErr = (String, String); // (error, fix)

fn is_audio(name: &str) -> bool {
    name.rsplit('.')
        .next()
        .map(|e| AUDIO_EXTS.contains(&e.to_ascii_lowercase().as_str()))
        .unwrap_or(false)
}

fn clean_rel(rel: &str) -> String {
    rel.trim_matches('/').to_string()
}

fn split_rel(rel: &str) -> (String, String) {
    let rel = clean_rel(rel);
    match rel.rsplit_once('/') {
        Some((d, f)) => (d.to_string(), f.to_string()),
        None => (String::new(), rel),
    }
}

// ── Browsing ─────────────────────────────────────────────────────────────────

pub fn list(root: &LibraryRoot, rel: &str) -> Result<proto::Listing, LibErr> {
    if root.is_rclone() {
        list_rclone(root, rel)
    } else {
        list_local(root, rel)
    }
}

fn list_local(root: &LibraryRoot, rel: &str) -> Result<proto::Listing, LibErr> {
    let base = root.path.as_deref().unwrap_or_default();
    let base = std::fs::canonicalize(base)
        .map_err(|_| ("library root is unreadable".into(), format!("check the path exists: {base}")))?;
    let rel_clean = clean_rel(rel);
    let target = std::fs::canonicalize(base.join(&rel_clean))
        .map_err(|_| ("no such folder".into(), "browse a folder that exists".into()))?;
    if !target.starts_with(&base) {
        return Err(("path escapes the library root".into(), "browse within the library".into()));
    }
    let mut dirs = Vec::new();
    let mut files = Vec::new();
    let rd = std::fs::read_dir(&target)
        .map_err(|e| ("cannot read folder".into(), format!("{e}")))?;
    for ent in rd.flatten() {
        let name = ent.file_name().to_string_lossy().into_owned();
        if name.starts_with('.') {
            continue;
        }
        let ft = ent.file_type().ok();
        if ft.map(|t| t.is_dir()).unwrap_or(false) {
            dirs.push(name);
        } else if is_audio(&name) {
            let size = ent.metadata().map(|m| m.len()).unwrap_or(0);
            files.push(proto::FileEntry { name, size });
        }
    }
    dirs.sort();
    files.sort_by(|a, b| a.name.cmp(&b.name));
    Ok(proto::Listing { root: root.id.clone(), path: rel_clean, dirs, files })
}

fn list_rclone(root: &LibraryRoot, rel: &str) -> Result<proto::Listing, LibErr> {
    if !util::tool_exists("rclone") {
        return Err((
            "rclone is not installed".into(),
            "install rclone + run: rclone config".into(),
        ));
    }
    let remote = root.rclone.as_deref().unwrap_or_default();
    let rel_clean = clean_rel(rel);
    let full = if rel_clean.is_empty() {
        remote.to_string()
    } else {
        format!("{remote}/{rel_clean}")
    };
    let out = Command::new("rclone")
        .args(["lsjson", &full])
        .output()
        .map_err(|e| ("rclone failed".into(), format!("{e}")))?;
    if !out.status.success() {
        return Err((
            "rclone could not list that remote path".into(),
            "check the remote name + path (rclone listremotes)".into(),
        ));
    }
    let arr: serde_json::Value = serde_json::from_slice(&out.stdout).unwrap_or(serde_json::Value::Null);
    let mut dirs = Vec::new();
    let mut files = Vec::new();
    if let Some(items) = arr.as_array() {
        for it in items {
            let name = it.get("Name").and_then(|v| v.as_str()).unwrap_or("").to_string();
            if name.is_empty() {
                continue;
            }
            if it.get("IsDir").and_then(|v| v.as_bool()).unwrap_or(false) {
                dirs.push(name);
            } else if is_audio(&name) {
                let size = it.get("Size").and_then(|v| v.as_u64()).unwrap_or(0);
                files.push(proto::FileEntry { name, size });
            }
        }
    }
    dirs.sort();
    files.sort_by(|a, b| a.name.cmp(&b.name));
    Ok(proto::Listing { root: root.id.clone(), path: rel_clean, dirs, files })
}

/// Resolve the on-disk absolute path for a file to decode. Local roots return
/// the jailed real path; rclone roots cache-then-play (download once, ≤256 MB).
fn resolve_abs(root: &LibraryRoot, rel: &str) -> Result<PathBuf, LibErr> {
    if root.is_rclone() {
        resolve_rclone_file(root, rel)
    } else {
        let base = std::fs::canonicalize(root.path.as_deref().unwrap_or_default())
            .map_err(|_| ("library root is unreadable".into(), "check the path".into()))?;
        let target = std::fs::canonicalize(base.join(clean_rel(rel)))
            .map_err(|_| ("no such file".into(), "pick a file that exists".into()))?;
        if !target.starts_with(&base) {
            return Err(("path escapes the library root".into(), "play within the library".into()));
        }
        Ok(target)
    }
}

fn resolve_rclone_file(root: &LibraryRoot, rel: &str) -> Result<PathBuf, LibErr> {
    if !util::tool_exists("rclone") {
        return Err(("rclone is not installed".into(), "install rclone + run: rclone config".into()));
    }
    let remote = root.rclone.as_deref().unwrap_or_default();
    let rel_clean = clean_rel(rel);
    let full = format!("{remote}/{rel_clean}");
    let ext = rel_clean.rsplit('.').next().unwrap_or("bin").to_lowercase();
    let sha = util::sha256_hex(full.as_bytes());
    let dir = util::cache_dir().join("drive");
    let _ = std::fs::create_dir_all(&dir);
    let cache = dir.join(format!("{}.{ext}", &sha[..16]));
    if cache.exists() {
        return Ok(cache);
    }
    // size guard via lsjson of the single object
    if let Ok(o) = Command::new("rclone").args(["lsjson", &full]).output() {
        if let Ok(v) = serde_json::from_slice::<serde_json::Value>(&o.stdout) {
            if let Some(sz) = v.as_array().and_then(|a| a.first()).and_then(|f| f.get("Size")).and_then(|s| s.as_u64()) {
                if sz > RCLONE_MAX_BYTES {
                    return Err(("remote file exceeds the 256 MB cache guard".into(), "pick a smaller file".into()));
                }
            }
        }
    }
    let status = Command::new("rclone")
        .args(["copyto", &full, &cache.to_string_lossy()])
        .status()
        .map_err(|e| ("rclone copy failed".into(), format!("{e}")))?;
    if !status.success() {
        return Err(("rclone could not fetch that file".into(), "check the remote + connectivity".into()));
    }
    Ok(cache)
}

// ── Metadata ─────────────────────────────────────────────────────────────────

fn probe_meta(abs: &Path, fallback_name: &str) -> (String, String, String, Option<u64>) {
    let stem = Path::new(fallback_name)
        .file_stem()
        .map(|s| s.to_string_lossy().into_owned())
        .unwrap_or_else(|| fallback_name.to_string());
    let out = Command::new("ffprobe")
        .args([
            "-v", "error",
            "-show_entries", "format=duration:format_tags=title,artist,album",
            "-of", "json",
            &abs.to_string_lossy(),
        ])
        .output();
    let Ok(out) = out else { return (stem, String::new(), String::new(), None) };
    let v: serde_json::Value = serde_json::from_slice(&out.stdout).unwrap_or(serde_json::Value::Null);
    let fmt = v.get("format");
    let duration_ms = fmt
        .and_then(|f| f.get("duration"))
        .and_then(|d| d.as_str())
        .and_then(|s| s.parse::<f64>().ok())
        .map(|sec| (sec * 1000.0) as u64);
    let tags = fmt.and_then(|f| f.get("tags"));
    let tag = |k: &str| tags.and_then(|t| t.get(k)).and_then(|v| v.as_str()).unwrap_or("").to_string();
    let title = {
        let t = tag("title");
        if t.is_empty() { stem } else { t }
    };
    (title, tag("artist"), tag("album"), duration_ms)
}

/// Best-effort embedded cover → art cache. Returns the art id if a frame came out.
fn extract_art(abs: &Path, art: &ArtCache) -> Option<String> {
    let out = Command::new("ffmpeg")
        .args(["-nostdin", "-v", "error", "-i", &abs.to_string_lossy(), "-an", "-c:v", "copy", "-f", "image2pipe", "-"])
        .stderr(Stdio::null())
        .output()
        .ok()?;
    if !out.status.success() || out.stdout.is_empty() {
        return None;
    }
    let key = format!("file:{}", abs.display());
    let full = util::sha256_hex(key.as_bytes());
    let dir = util::cache_dir().join("art");
    let _ = std::fs::create_dir_all(&dir);
    let path = dir.join(format!("{full}.jpg"));
    std::fs::write(&path, &out.stdout).ok()?;
    Some(player::register_art(&key, path, "image/jpeg", art))
}

// ── The ffmpeg pump ──────────────────────────────────────────────────────────

struct FilePump {
    child: Arc<Mutex<Option<Child>>>,
    stopping: Arc<AtomicBool>,
    paused: Arc<AtomicBool>,
    bytes: Arc<AtomicU64>,
    base_ms: u64,
    handle: Option<JoinHandle<()>>,
}

impl FilePump {
    fn stop(mut self) {
        self.stopping.store(true, Ordering::SeqCst);
        if let Some(c) = self.child.lock().unwrap().as_mut() {
            let _ = c.kill(); // unblock the read; the thread wait()s it
        }
        if let Some(h) = self.handle.take() {
            let _ = h.join();
        }
    }
}

fn start_pump(
    abs: &Path,
    base_ms: u64,
    paused_initial: bool,
    writer: SyncSender<Vec<u8>>,
    counters: Arc<Counters>,
    ctl: Sender<Ev>,
) -> Result<FilePump, LibErr> {
    let mut cmd = Command::new("ffmpeg");
    cmd.args(["-nostdin", "-v", "error"]);
    if base_ms > 0 {
        cmd.args(["-ss", &format!("{:.3}", base_ms as f64 / 1000.0)]);
    }
    cmd.args(["-i", &abs.to_string_lossy(), "-f", "s16le", "-ar", "48000", "-ac", "2", "-"]);
    let mut child = cmd
        .stdout(Stdio::piped())
        .stderr(Stdio::null())
        .spawn()
        .map_err(|e| ("ffmpeg failed to start".into(), format!("install ffmpeg — {e}")))?;
    let mut stdout = child.stdout.take().expect("piped stdout");
    let child = Arc::new(Mutex::new(Some(child)));
    let stopping = Arc::new(AtomicBool::new(false));
    let paused = Arc::new(AtomicBool::new(paused_initial));
    let bytes = Arc::new(AtomicU64::new(0));

    let (st, pz, by, child_t) = (stopping.clone(), paused.clone(), bytes.clone(), child.clone());
    let handle = thread::spawn(move || {
        use std::io::Read;
        use std::time::Instant;
        // A file decodes far faster than real time and has no sound-card clock
        // (unlike pw-record), so the pump must pace itself: one 1920-byte /10 ms
        // frame per 10 ms. That also drives the pause mechanism — the pipe fills,
        // ffmpeg backpressures — and keeps position_ms honest.
        let frame_dur = Duration::from_millis(10);
        let mut next = Instant::now() + frame_dur;
        let mut buf = [0u8; A_FRAME];
        loop {
            if st.load(Ordering::SeqCst) {
                break;
            }
            if pz.load(Ordering::SeqCst) {
                thread::sleep(Duration::from_millis(20));
                next = Instant::now() + frame_dur; // don't burst to "catch up" on resume
                continue;
            }
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
                    by.fetch_add(A_FRAME as u64, Ordering::Relaxed);
                    let now = Instant::now();
                    if now < next {
                        thread::sleep(next - now);
                    }
                    next += frame_dur;
                    if next < now {
                        next = now + frame_dur; // we fell behind; re-anchor
                    }
                }
                Err(_) => {
                    if !st.load(Ordering::SeqCst) {
                        let _ = ctl.send(Ev::FileEof);
                    }
                    break;
                }
            }
        }
        // reap ffmpeg (natural EOF or a kill from stop/seek/advance) — no zombie.
        if let Some(mut c) = child_t.lock().unwrap().take() {
            let _ = c.wait();
        }
    });

    Ok(FilePump { child, stopping, paused, bytes, base_ms, handle: Some(handle) })
}

// ── The file session ─────────────────────────────────────────────────────────

pub struct FileSession {
    root: LibraryRoot,
    dir_rel: String,
    entries: Vec<String>,
    index: usize,
    writer: SyncSender<Vec<u8>>,
    counters: Arc<Counters>,
    ctl: Sender<Ev>,
    art: ArtCache,
    // current file:
    title: String,
    artist: String,
    album: String,
    duration_ms: Option<u64>,
    art_id: Option<String>,
    rel_path: String,
    pump: Option<FilePump>,
}

impl FileSession {
    pub fn open(
        root: LibraryRoot,
        rel: &str,
        writer: SyncSender<Vec<u8>>,
        counters: Arc<Counters>,
        ctl: Sender<Ev>,
        art: ArtCache,
    ) -> Result<FileSession, LibErr> {
        let (dir_rel, filename) = split_rel(rel);
        if !is_audio(&filename) {
            return Err(("not an audio file".into(), "pick a file with a supported audio extension".into()));
        }
        let listing = list(&root, &dir_rel)?;
        let entries: Vec<String> = listing.files.iter().map(|f| f.name.clone()).collect();
        let index = entries.iter().position(|n| n == &filename).ok_or_else(|| {
            ("file is not in its directory listing".into(), "refresh the folder and pick again".into())
        })?;
        let mut fs = FileSession {
            root,
            dir_rel,
            entries,
            index,
            writer,
            counters,
            ctl,
            art,
            title: String::new(),
            artist: String::new(),
            album: String::new(),
            duration_ms: None,
            art_id: None,
            rel_path: String::new(),
            pump: None,
        };
        fs.play_index(0, false)?;
        Ok(fs)
    }

    fn stop_pump(&mut self) {
        if let Some(p) = self.pump.take() {
            p.stop();
        }
    }

    /// Load `entries[index]` and start decoding. `base_ms` seeds a seek offset;
    /// `paused` seeds pause state (used so a seek keeps a paused track paused).
    fn play_index(&mut self, base_ms: u64, paused: bool) -> Result<(), LibErr> {
        self.stop_pump();
        let name = self.entries[self.index].clone();
        let rel = if self.dir_rel.is_empty() { name.clone() } else { format!("{}/{}", self.dir_rel, name) };
        self.rel_path = rel.clone();

        // rclone roots: announce the download, then cache-then-play (may block).
        if self.root.is_rclone() {
            let downloading = proto::Meta {
                title: name.clone(),
                playing: false,
                source: "file".into(),
                path: Some(rel.clone()),
                can_seek: true,
                ..Default::default()
            };
            if let Ok(body) = serde_json::to_vec(&downloading) {
                let _ = self.writer.try_send(proto::encode_frame(proto::M, &body));
            }
        }

        let abs = resolve_abs(&self.root, &rel)?;
        let (title, artist, album, duration_ms) = probe_meta(&abs, &name);
        self.title = title;
        self.artist = artist;
        self.album = album;
        self.duration_ms = duration_ms;
        self.art_id = extract_art(&abs, &self.art);

        let pump = start_pump(
            &abs,
            base_ms,
            paused,
            self.writer.clone(),
            self.counters.clone(),
            self.ctl.clone(),
        )?;
        self.pump = Some(pump);
        Ok(())
    }

    pub fn position_ms(&self) -> u64 {
        match &self.pump {
            Some(p) => p.base_ms + p.bytes.load(Ordering::Relaxed) / 192,
            None => 0,
        }
    }

    pub fn is_playing(&self) -> bool {
        self.pump.as_ref().map(|p| !p.paused.load(Ordering::SeqCst)).unwrap_or(false)
    }

    pub fn set_paused(&self, paused: bool) {
        if let Some(p) = &self.pump {
            p.paused.store(paused, Ordering::SeqCst);
        }
    }

    pub fn toggle_paused(&self) {
        if let Some(p) = &self.pump {
            let now = p.paused.load(Ordering::SeqCst);
            p.paused.store(!now, Ordering::SeqCst);
        }
    }

    pub fn seek(&mut self, ms: u64) -> Result<(), LibErr> {
        let paused = self.pump.as_ref().map(|p| p.paused.load(Ordering::SeqCst)).unwrap_or(false);
        self.play_index(ms, paused)
    }

    /// Move by `delta` files (no wrap). Returns false at either end.
    pub fn advance(&mut self, delta: i32) -> Result<bool, LibErr> {
        let next = self.index as i32 + delta;
        if next < 0 || next as usize >= self.entries.len() {
            self.stop_pump();
            return Ok(false);
        }
        self.index = next as usize;
        self.play_index(0, false)?;
        Ok(true)
    }

    pub fn meta(&self) -> proto::Meta {
        proto::Meta {
            title: self.title.clone(),
            artist: self.artist.clone(),
            album: self.album.clone(),
            playing: self.is_playing(),
            position_ms: self.position_ms(),
            duration_ms: self.duration_ms,
            can_seek: true,
            source: "file".into(),
            art_id: self.art_id.clone(),
            path: Some(self.rel_path.clone()),
        }
    }

    pub fn stop(mut self) {
        self.stop_pump();
    }
}
