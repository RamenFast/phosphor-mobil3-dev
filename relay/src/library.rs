//! Library browsing + file playback. Roots come from config (a local `path` or
//! an `rclone` remote). Browsing rejects path traversal and follows deliberate
//! user-owned symlinks. A played file is decoded by `ffmpeg` into the same A-frames as live
//! capture; pause is just "stop reading the pipe" (ffmpeg backpressures, so
//! resume is sample-exact); seek respawns with `-ss`; EOF auto-advances to the
//! next file in the selected queue.

use std::collections::{BTreeMap, HashSet};
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

pub const AUDIO_EXTS: &[&str] = &[
    "wav", "flac", "mp3", "ogg", "opus", "m4a", "aac", "aiff", "wv",
];
const RCLONE_MAX_BYTES: u64 = 256 * 1024 * 1024;

pub type LibErr = (String, String); // (error, fix)

fn check_cancel(cancel: &AtomicBool) -> Result<(), LibErr> {
    if cancel.load(Ordering::SeqCst) {
        Err((
            "library operation cancelled".into(),
            "select a source again".into(),
        ))
    } else {
        Ok(())
    }
}

fn checked_rel(rel: &str) -> Result<String, LibErr> {
    let rel = clean_rel(rel);
    if escapes_root(&rel) || rel.contains('\0') {
        return Err((
            "path escapes the library root".into(),
            "browse within the library".into(),
        ));
    }
    Ok(rel
        .split('/')
        .filter(|p| !p.is_empty() && *p != ".")
        .collect::<Vec<_>>()
        .join("/"))
}

fn join_rel(dir: &str, name: &str) -> String {
    if dir.is_empty() {
        name.into()
    } else if name.is_empty() {
        dir.into()
    } else {
        format!("{dir}/{name}")
    }
}

fn is_audio(name: &str) -> bool {
    name.rsplit('.')
        .next()
        .map(|e| AUDIO_EXTS.contains(&e.to_ascii_lowercase().as_str()))
        .unwrap_or(false)
}

/// Normalise a client-supplied relative path.
///
/// Rejects any `..` component. That is what actually stops a client from walking out
/// of the library, and it is a different question from whether a symlink INSIDE the
/// library may point elsewhere. User-owned symlinks are deliberate structure and are
/// followed, while a crafted `../../etc` request never reaches the filesystem.
fn clean_rel(rel: &str) -> String {
    rel.trim_matches('/').to_string()
}

/// True when a relative path tries to climb out of its root.
fn escapes_root(rel: &str) -> bool {
    rel.split('/').any(|part| part == "..")
}

fn split_rel(rel: &str) -> (String, String) {
    let rel = clean_rel(rel);
    match rel.rsplit_once('/') {
        Some((d, f)) => (d.to_string(), f.to_string()),
        None => (String::new(), rel),
    }
}

// ── Browsing ─────────────────────────────────────────────────────────────────

pub fn list(root: &LibraryRoot, rel: &str, cancel: &AtomicBool) -> Result<proto::Listing, LibErr> {
    check_cancel(cancel)?;
    let rel = checked_rel(rel)?;
    if root.is_rclone() {
        list_rclone(root, &rel, cancel)
    } else {
        list_local(root, &rel, cancel)
    }
}

fn list_local(
    root: &LibraryRoot,
    rel: &str,
    cancel: &AtomicBool,
) -> Result<proto::Listing, LibErr> {
    check_cancel(cancel)?;
    let base = root.path.as_deref().unwrap_or_default();
    let base = std::fs::canonicalize(base).map_err(|_| {
        (
            "library root is unreadable".into(),
            format!("check the path exists: {base}"),
        )
    })?;
    let rel_clean = checked_rel(rel)?;
    if escapes_root(&rel_clean) {
        return Err((
            "path escapes the library root".into(),
            "browse within the library".into(),
        ));
    }
    // NOT canonicalized: canonicalize() resolves symlinks, and the resulting real path
    // legitimately lands outside the root when the user symlinked another drive in. The
    // `..` check above is what keeps the wire honest.
    let target = base.join(&rel_clean);
    if !target.is_dir() {
        return Err((
            "no such folder".into(),
            "browse a folder that exists".into(),
        ));
    }
    let mut dirs = Vec::new();
    let mut files = Vec::new();
    let rd =
        std::fs::read_dir(&target).map_err(|e| ("cannot read folder".into(), format!("{e}")))?;
    for ent in rd.flatten() {
        check_cancel(cancel)?;
        let name = ent.file_name().to_string_lossy().into_owned();
        if name.starts_with('.') {
            continue;
        }
        // metadata() follows symlinks while file_type() does not. A user-created
        // symlink is served like the directory or audio file it points at.
        //
        // A broken link has no metadata; fall back to the link's own type so it is
        // skipped quietly rather than aborting the whole listing.
        let meta = std::fs::metadata(ent.path());
        let is_dir = match &meta {
            Ok(m) => m.is_dir(),
            Err(_) => continue, // dangling symlink: nothing to offer
        };
        if is_dir {
            dirs.push(name);
        } else if meta.as_ref().is_ok_and(|m| m.is_file()) && is_audio(&name) {
            let size = meta.map(|m| m.len()).unwrap_or(0);
            files.push(proto::FileEntry { name, size });
        }
    }
    dirs.sort();
    files.sort_by(|a, b| a.name.cmp(&b.name));
    Ok(proto::Listing {
        root: root.id.clone(),
        path: rel_clean,
        dirs,
        files,
    })
}

fn list_rclone(
    root: &LibraryRoot,
    rel: &str,
    cancel: &AtomicBool,
) -> Result<proto::Listing, LibErr> {
    check_cancel(cancel)?;
    let rel_clean = checked_rel(rel)?;
    if !util::tool_exists("rclone") {
        return Err((
            "rclone is not installed".into(),
            "install rclone + run: rclone config".into(),
        ));
    }
    let remote = root.rclone.as_deref().unwrap_or_default();
    let full = if rel_clean.is_empty() {
        remote.to_string()
    } else {
        format!("{remote}/{rel_clean}")
    };
    let mut cmd = Command::new("rclone");
    cmd.args(["lsjson", &full]);
    // A stalled remote listing ends after 20 seconds or when the session is cancelled.
    let out = util::run_cancellable(&mut cmd, Duration::from_secs(20), cancel).map_err(|e| {
        (
            "rclone failed".into(),
            format!("{e} — check connectivity, retry"),
        )
    })?;
    if !out.status.success() {
        return Err((
            "rclone could not list that remote path".into(),
            "check the remote name + path (rclone listremotes)".into(),
        ));
    }
    parse_rclone_listing(root, &rel_clean, &out.stdout, cancel)
}

fn parse_rclone_listing(
    root: &LibraryRoot,
    rel: &str,
    bytes: &[u8],
    cancel: &AtomicBool,
) -> Result<proto::Listing, LibErr> {
    check_cancel(cancel)?;
    let malformed = || {
        (
            "malformed remote folder listing".into(),
            "refresh the remote folder and check its entry names".into(),
        )
    };
    let arr: serde_json::Value = serde_json::from_slice(bytes).map_err(|_| malformed())?;
    let items = arr.as_array().ok_or_else(malformed)?;
    let mut seen = BTreeMap::new();
    let mut dirs = Vec::new();
    let mut files = Vec::new();
    for it in items {
        check_cancel(cancel)?;
        let name = it
            .get("Name")
            .and_then(|v| v.as_str())
            .ok_or_else(malformed)?;
        if name.is_empty() || name == "." || name == ".." || name.contains(['/', '\0']) {
            return Err(malformed());
        }
        if it.get("Path").is_some_and(|p| p.as_str() != Some(name)) {
            return Err(malformed());
        }
        let is_dir = it
            .get("IsDir")
            .and_then(|v| v.as_bool())
            .ok_or_else(malformed)?;
        let size = if is_dir {
            0
        } else {
            it.get("Size")
                .and_then(|v| v.as_u64())
                .ok_or_else(malformed)?
        };
        if let Some(previous) = seen.insert(name.to_string(), (is_dir, size)) {
            if previous != (is_dir, size) {
                return Err(malformed());
            }
            continue;
        }
        if name.starts_with('.') {
            continue;
        }
        if is_dir {
            dirs.push(name.to_string());
        } else if is_audio(name) {
            files.push(proto::FileEntry {
                name: name.to_string(),
                size,
            });
        }
    }
    dirs.sort();
    files.sort_by(|a, b| a.name.cmp(&b.name));
    Ok(proto::Listing {
        root: root.id.clone(),
        path: rel.to_string(),
        dirs,
        files,
    })
}

/// Prepare the whole selection and cache its first file off the control loop.
/// The fetch completion keeps this selection, so opening cannot lose nested entries.
pub fn prefetch(root: &LibraryRoot, rel: &str, cancel: &AtomicBool) -> Result<Selection, LibErr> {
    let selection = select(root, rel, cancel)?;
    prepare_selection(selection, cancel, |path| {
        resolve_abs(root, path, cancel).map(|_| ())
    })
}

#[derive(Debug)]
pub(crate) struct Selection {
    dir_rel: String,
    entries: Vec<String>,
    index: usize,
}

fn prepare_selection(
    selection: Selection,
    cancel: &AtomicBool,
    mut resolve: impl FnMut(&str) -> Result<(), LibErr>,
) -> Result<Selection, LibErr> {
    check_cancel(cancel)?;
    resolve(&join_rel(
        &selection.dir_rel,
        &selection.entries[selection.index],
    ))?;
    check_cancel(cancel)?;
    Ok(selection)
}

fn select(root: &LibraryRoot, rel: &str, cancel: &AtomicBool) -> Result<Selection, LibErr> {
    check_cancel(cancel)?;
    let rel = checked_rel(rel)?;
    let (parent, name) = split_rel(&rel);
    let is_dir = if rel.is_empty() {
        true
    } else if root.is_rclone() {
        list(root, &parent, cancel)?.dirs.contains(&name)
    } else {
        check_cancel(cancel)?;
        Path::new(root.path.as_deref().unwrap_or_default())
            .join(&rel)
            .is_dir()
    };
    if is_dir {
        let mut entries = Vec::new();
        let mut seen = HashSet::new();
        walk_folder(
            &rel,
            "",
            cancel,
            &mut seen,
            &mut entries,
            &mut |path| list(root, path, cancel),
            &mut |path| {
                if root.is_rclone() {
                    // Opaque backend aliases have no local identity. Deduplicate lexical paths only.
                    Ok(PathBuf::from(path))
                } else {
                    std::fs::canonicalize(
                        Path::new(root.path.as_deref().unwrap_or_default()).join(path),
                    )
                    .map_err(|e| ("cannot resolve folder".into(), e.to_string()))
                }
            },
        )?;
        entries.sort();
        entries.dedup();
        if entries.is_empty() {
            return Err((
                "folder contains no supported audio".into(),
                "choose a folder with audio files".into(),
            ));
        }
        return Ok(Selection {
            dir_rel: rel,
            entries,
            index: 0,
        });
    }
    if !is_audio(&name) {
        return Err((
            "not an audio file or folder".into(),
            "pick a folder or supported audio file".into(),
        ));
    }
    let entries: Vec<_> = list(root, &parent, cancel)?
        .files
        .into_iter()
        .map(|f| f.name)
        .collect();
    let index = entries.iter().position(|n| n == &name).ok_or_else(|| {
        (
            "file is not in its directory listing".into(),
            "refresh the folder and pick again".into(),
        )
    })?;
    Ok(Selection {
        dir_rel: parent,
        entries,
        index,
    })
}

fn walk_folder(
    selected: &str,
    relative: &str,
    cancel: &AtomicBool,
    seen: &mut HashSet<PathBuf>,
    entries: &mut Vec<String>,
    list_folder: &mut impl FnMut(&str) -> Result<proto::Listing, LibErr>,
    identity: &mut impl FnMut(&str) -> Result<PathBuf, LibErr>,
) -> Result<(), LibErr> {
    check_cancel(cancel)?;
    let path = join_rel(selected, relative);
    if !seen.insert(identity(&path)?) {
        return Ok(());
    }
    check_cancel(cancel)?;
    let mut listing = list_folder(&path)?;
    listing.dirs.sort();
    for dir in listing.dirs {
        check_cancel(cancel)?;
        walk_folder(
            selected,
            &join_rel(relative, &dir),
            cancel,
            seen,
            entries,
            list_folder,
            identity,
        )?;
    }
    for file in listing.files {
        check_cancel(cancel)?;
        entries.push(join_rel(relative, &file.name));
    }
    check_cancel(cancel)?;
    Ok(())
}

/// Resolve the on-disk absolute path for a file to decode. Local roots return
/// the jailed real path; rclone roots cache-then-play (download once, ≤256 MB).
fn resolve_abs(root: &LibraryRoot, rel: &str, cancel: &AtomicBool) -> Result<PathBuf, LibErr> {
    check_cancel(cancel)?;
    let rel = checked_rel(rel)?;
    if root.is_rclone() {
        resolve_rclone_file(root, &rel, cancel)
    } else {
        let base = std::fs::canonicalize(root.path.as_deref().unwrap_or_default())
            .map_err(|_| ("library root is unreadable".into(), "check the path".into()))?;
        let rel_clean = rel;
        if escapes_root(&rel_clean) {
            return Err((
                "path escapes the library root".into(),
                "play within the library".into(),
            ));
        }
        // Same reasoning as list_local: a symlink the user placed in their own library
        // resolves outside the root by design, so the guard is on the requested path
        // rather than on where the filesystem ends up.
        let target = base.join(&rel_clean);
        if !target.is_file() {
            return Err(("no such file".into(), "pick a file that exists".into()));
        }
        Ok(target)
    }
}

fn resolve_rclone_file(
    root: &LibraryRoot,
    rel: &str,
    cancel: &AtomicBool,
) -> Result<PathBuf, LibErr> {
    check_cancel(cancel)?;
    let rel_clean = checked_rel(rel)?;
    if !util::tool_exists("rclone") {
        return Err((
            "rclone is not installed".into(),
            "install rclone + run: rclone config".into(),
        ));
    }
    let remote = root.rclone.as_deref().unwrap_or_default();
    let full = format!("{remote}/{rel_clean}");
    let ext = rel_clean.rsplit('.').next().unwrap_or("bin").to_lowercase();
    let sha = util::sha256_hex(full.as_bytes());
    let dir = util::cache_dir().join("drive");
    let _ = std::fs::create_dir_all(&dir);
    let cache = dir.join(format!("{}.{ext}", &sha[..16]));
    if cache.exists() {
        return Ok(cache);
    }
    // size guard via lsjson of the single object (deadline + cancel)
    let mut ls = Command::new("rclone");
    ls.args(["lsjson", &full]);
    let remote_size = util::run_cancellable(&mut ls, Duration::from_secs(20), cancel)
        .ok()
        .and_then(|output| serde_json::from_slice::<serde_json::Value>(&output.stdout).ok())
        .and_then(|value| value.as_array().and_then(|items| items.first()).cloned())
        .and_then(|item| item.get("Size").and_then(|size| size.as_u64()));
    if remote_size.is_some_and(|size| size > RCLONE_MAX_BYTES) {
        return Err((
            "remote file exceeds the 256 MB cache guard".into(),
            "pick a smaller file".into(),
        ));
    }
    // Remote downloads have a generous deadline but remain cancellation-aware.
    let mut cp = Command::new("rclone");
    cp.args(["copyto", &full, &cache.to_string_lossy()]);
    let out = util::run_cancellable(&mut cp, Duration::from_secs(180), cancel)
        .map_err(|error| ("rclone copy failed".into(), error.to_string()))?;
    if !out.status.success() {
        return Err((
            "rclone could not fetch that file".into(),
            "check the remote + connectivity".into(),
        ));
    }
    Ok(cache)
}

// ── Metadata ─────────────────────────────────────────────────────────────────

fn probe_meta(
    abs: &Path,
    fallback_name: &str,
    cancel: &AtomicBool,
) -> (String, String, String, Option<u64>) {
    let stem = Path::new(fallback_name)
        .file_stem()
        .map(|s| s.to_string_lossy().into_owned())
        .unwrap_or_else(|| fallback_name.to_string());
    let mut cmd = Command::new("ffprobe");
    cmd.args([
        "-v",
        "error",
        "-show_entries",
        "format=duration:format_tags=title,artist,album",
        "-of",
        "json",
        &abs.to_string_lossy(),
    ]);
    let out = util::run_cancellable(&mut cmd, Duration::from_secs(10), cancel);
    let Ok(out) = out else {
        return (stem, String::new(), String::new(), None);
    };
    let v: serde_json::Value =
        serde_json::from_slice(&out.stdout).unwrap_or(serde_json::Value::Null);
    let fmt = v.get("format");
    let duration_ms = fmt
        .and_then(|f| f.get("duration"))
        .and_then(|d| d.as_str())
        .and_then(|s| s.parse::<f64>().ok())
        .map(|sec| (sec * 1000.0) as u64);
    let tags = fmt.and_then(|f| f.get("tags"));
    let tag = |k: &str| {
        tags.and_then(|t| t.get(k))
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .to_string()
    };
    let title = {
        let t = tag("title");
        if t.is_empty() { stem } else { t }
    };
    (title, tag("artist"), tag("album"), duration_ms)
}

/// Best-effort embedded cover → art cache. Returns the art id if a frame came out.
fn extract_art(abs: &Path, art: &ArtCache, cancel: &AtomicBool) -> Option<String> {
    let mut cmd = Command::new("ffmpeg");
    cmd.args([
        "-nostdin",
        "-v",
        "error",
        "-i",
        &abs.to_string_lossy(),
        "-an",
        "-c:v",
        "copy",
        "-f",
        "image2pipe",
        "-",
    ]);
    let out = util::run_cancellable(&mut cmd, Duration::from_secs(10), cancel).ok()?;
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
    /// Session-scoped identity. FileEof carries it so a replaced pump cannot
    /// advance the new one.
    id: u64,
    child: Arc<Mutex<Option<Child>>>,
    stopping: Arc<AtomicBool>,
    paused: Arc<AtomicBool>,
    bytes: Arc<AtomicU64>,
    base_ms: u64,
    handle: Option<JoinHandle<()>>,
}

/// Dropping the pump kills ffmpeg and joins its thread.
impl Drop for FilePump {
    fn drop(&mut self) {
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
    id: u64,
    base_ms: u64,
    paused_initial: bool,
    resources: &FileSession,
) -> Result<FilePump, LibErr> {
    let mut cmd = Command::new("ffmpeg");
    cmd.args(["-nostdin", "-v", "error"]);
    if base_ms > 0 {
        cmd.args(["-ss", &format!("{:.3}", base_ms as f64 / 1000.0)]);
    }
    cmd.args([
        "-i",
        &abs.to_string_lossy(),
        "-f",
        "s16le",
        "-ar",
        "48000",
        "-ac",
        "2",
        "-",
    ]);
    check_cancel(&resources.cancel)?;
    let mut child = cmd
        .stdout(Stdio::piped())
        .stderr(Stdio::null())
        .spawn()
        .map_err(|e| {
            (
                "ffmpeg failed to start".into(),
                format!("install ffmpeg — {e}"),
            )
        })?;
    let mut stdout = child.stdout.take().expect("piped stdout");
    let child = Arc::new(Mutex::new(Some(child)));
    let stopping = Arc::new(AtomicBool::new(false));
    let paused = Arc::new(AtomicBool::new(paused_initial));
    let bytes = Arc::new(AtomicU64::new(0));
    let writer = resources.writer.clone();
    let counters = resources.counters.clone();
    let ctl = resources.ctl.clone();

    let (st, pz, by, child_t) = (
        stopping.clone(),
        paused.clone(),
        bytes.clone(),
        child.clone(),
    );
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
                    // A full queue drops one frame. A disconnected writer ends the pump.
                    // The writer counts tx_a at the wire.
                    match writer.try_send(frame) {
                        Ok(()) => {}
                        Err(std::sync::mpsc::TrySendError::Full(_)) => {
                            counters.dropped_a.fetch_add(1, Ordering::Relaxed);
                        }
                        Err(std::sync::mpsc::TrySendError::Disconnected(_)) => break,
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
                        let _ = ctl.send(Ev::FileEof(id)); // session-scoped pump id
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

    Ok(FilePump {
        id,
        child,
        stopping,
        paused,
        bytes,
        base_ms,
        handle: Some(handle),
    })
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
    /// Session-wide pump-id fountain (shared with capture) + the session's
    /// ops-cancel flag (watchdog/disconnect kill in-flight externals ≤25 ms).
    pump_ids: Arc<AtomicU64>,
    cancel: Arc<AtomicBool>,
    // current file:
    title: String,
    artist: String,
    album: String,
    duration_ms: Option<u64>,
    art_id: Option<String>,
    rel_path: String,
    pump: Option<FilePump>,
}

pub(crate) struct FileSessionResources {
    pub(crate) writer: SyncSender<Vec<u8>>,
    pub(crate) counters: Arc<Counters>,
    pub(crate) ctl: Sender<Ev>,
    pub(crate) art: ArtCache,
    pub(crate) pump_ids: Arc<AtomicU64>,
    pub(crate) cancel: Arc<AtomicBool>,
}

impl FileSession {
    pub fn open(
        root: LibraryRoot,
        rel: &str,
        resources: FileSessionResources,
    ) -> Result<FileSession, LibErr> {
        let selection = select(&root, rel, &resources.cancel)?;
        Self::open_selection(root, selection, resources)
    }

    pub(crate) fn open_selection(
        root: LibraryRoot,
        selection: Selection,
        resources: FileSessionResources,
    ) -> Result<FileSession, LibErr> {
        let FileSessionResources {
            writer,
            counters,
            ctl,
            art,
            pump_ids,
            cancel,
        } = resources;
        check_cancel(&cancel)?;
        let Selection {
            dir_rel,
            entries,
            index,
        } = selection;
        let mut fs = FileSession {
            root,
            dir_rel,
            entries,
            index,
            writer,
            counters,
            ctl,
            art,
            pump_ids,
            cancel,
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
        self.pump = None; // Drop kills + joins (RAII)
    }

    /// The live pump's id — the session's stale-EOF guard reads this.
    pub fn current_pump_id(&self) -> Option<u64> {
        self.pump.as_ref().map(|p| p.id)
    }

    /// Load `entries[index]` and start decoding. `base_ms` seeds a seek offset;
    /// `paused` seeds pause state (used so a seek keeps a paused track paused).
    fn play_index(&mut self, base_ms: u64, paused: bool) -> Result<(), LibErr> {
        self.stop_pump();
        check_cancel(&self.cancel)?;
        let name = self.entries[self.index].clone();
        let rel = if self.dir_rel.is_empty() {
            name.clone()
        } else {
            format!("{}/{}", self.dir_rel, name)
        };
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

        let abs = resolve_abs(&self.root, &rel, &self.cancel)?;
        let (title, artist, album, duration_ms) = probe_meta(&abs, &name, &self.cancel);
        check_cancel(&self.cancel)?;
        self.title = title;
        self.artist = artist;
        self.album = album;
        self.duration_ms = duration_ms;
        self.art_id = extract_art(&abs, &self.art, &self.cancel);
        check_cancel(&self.cancel)?;

        let pump = start_pump(
            &abs,
            self.pump_ids.fetch_add(1, Ordering::Relaxed) + 1,
            base_ms,
            paused,
            self,
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
        self.pump
            .as_ref()
            .map(|p| !p.paused.load(Ordering::SeqCst))
            .unwrap_or(false)
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
        let paused = self
            .pump
            .as_ref()
            .map(|p| p.paused.load(Ordering::SeqCst))
            .unwrap_or(false);
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
}

/// Dropping the session stops its pump. Explicit `.take()` is the stop API.
impl Drop for FileSession {
    fn drop(&mut self) {
        self.stop_pump();
    }
}

#[cfg(test)]
mod tests {
    use super::{escapes_root, list_local};
    use crate::config::LibraryRoot;
    use std::fs;

    /// A library root with a real folder, an audio file, and a symlinked folder
    /// that points outside the root.
    fn fixture(name: &str) -> (std::path::PathBuf, std::path::PathBuf, LibraryRoot) {
        let tmp = std::env::temp_dir().join(format!("phosphor-lib-{name}-{}", std::process::id()));
        let root = tmp.join("music");
        let outside = tmp.join("elsewhere");
        let _ = fs::remove_dir_all(&tmp);
        fs::create_dir_all(root.join("albums")).unwrap();
        fs::create_dir_all(&outside).unwrap();
        fs::write(root.join("albums").join("track.flac"), b"x").unwrap();
        fs::write(outside.join("remote.flac"), b"yy").unwrap();
        #[cfg(unix)]
        std::os::unix::fs::symlink(&outside, root.join("WAV versions")).unwrap();
        let lib = LibraryRoot {
            id: "music0".into(),
            label: "Music".into(),
            path: Some(root.to_string_lossy().into_owned()),
            rclone: None,
        };
        (tmp, root, lib)
    }

    #[test]
    #[cfg(unix)]
    fn a_symlinked_folder_is_listed_rather_than_silently_dropped() {
        // The reported bug: file_type() does not follow links, so a symlinked folder was
        // neither dir nor audio and vanished from the listing entirely.
        let (tmp, _root, lib) = fixture("listed");
        let listing = list_local(&lib, "", &std::sync::atomic::AtomicBool::new(false))
            .expect("root should list");
        assert!(
            listing.dirs.contains(&"WAV versions".to_string()),
            "symlinked folder missing from {:?}",
            listing.dirs,
        );
        assert!(listing.dirs.contains(&"albums".to_string()));
        let _ = fs::remove_dir_all(tmp);
    }

    #[test]
    #[cfg(unix)]
    fn entering_a_symlink_serves_what_it_points_at() {
        // Listing it is useless if opening it refuses. The old canonicalize+starts_with
        // guard resolved the link and then rejected its real location.
        let (tmp, _root, lib) = fixture("enter");
        let listing = list_local(
            &lib,
            "WAV versions",
            &std::sync::atomic::AtomicBool::new(false),
        )
        .expect("symlink should be browsable");
        assert_eq!(
            vec!["remote.flac".to_string()],
            listing
                .files
                .iter()
                .map(|f| f.name.clone())
                .collect::<Vec<_>>(),
        );
        let _ = fs::remove_dir_all(tmp);
    }

    #[test]
    fn a_dot_dot_path_is_still_refused() {
        // Following symlinks must not become a way to walk the whole filesystem. A
        // crafted relative path from the wire is a different thing from a symlink the
        // user placed in their own library.
        assert!(escapes_root("../etc"));
        assert!(escapes_root("albums/../../etc"));
        assert!(escapes_root(".."));
        assert!(!escapes_root("albums"));
        assert!(!escapes_root("WAV versions"));
        // A filename that merely CONTAINS dots is fine.
        assert!(!escapes_root("a..b"));
    }

    #[test]
    #[cfg(unix)]
    fn a_dot_dot_request_is_rejected_by_the_lister_itself() {
        let (tmp, _root, lib) = fixture("escape");
        let err = list_local(
            &lib,
            "../elsewhere",
            &std::sync::atomic::AtomicBool::new(false),
        )
        .err()
        .expect("must refuse to climb out");
        assert!(err.0.contains("escapes"), "unexpected error: {}", err.0);
        assert!(!err.1.is_empty(), "refusal must carry a fix");
        let _ = fs::remove_dir_all(tmp);
    }

    #[test]
    #[cfg(unix)]
    fn a_dangling_symlink_is_skipped_without_breaking_the_listing() {
        // A link to a deleted drive must not take the whole folder down with it.
        let (tmp, root, lib) = fixture("dangling");
        std::os::unix::fs::symlink(tmp.join("gone"), root.join("missing")).unwrap();
        let listing = list_local(&lib, "", &std::sync::atomic::AtomicBool::new(false))
            .expect("listing should survive a broken link");
        assert!(!listing.dirs.contains(&"missing".to_string()));
        assert!(listing.dirs.contains(&"albums".to_string()));
        let _ = fs::remove_dir_all(tmp);
    }
}

#[cfg(test)]
pub(crate) mod folder_tests {
    use super::*;
    use std::fs;
    use std::sync::mpsc::{self, Receiver};

    pub(crate) struct WavTree {
        base: PathBuf,
        pub(crate) root: LibraryRoot,
    }

    impl WavTree {
        pub(crate) fn new(label: &str) -> Self {
            static IDS: AtomicU64 = AtomicU64::new(0);
            let base = std::env::temp_dir().join(format!(
                "phosphor-folder-{label}-{}-{}",
                std::process::id(),
                IDS.fetch_add(1, Ordering::Relaxed)
            ));
            fs::create_dir_all(base.join("music")).unwrap();
            let root = LibraryRoot {
                id: "fixture".into(),
                label: "Fixture".into(),
                path: Some(base.join("music").to_string_lossy().into_owned()),
                rclone: None,
            };
            Self { base, root }
        }

        pub(crate) fn wav(&self, rel: &str) {
            let path = Path::new(self.root.path.as_ref().unwrap()).join(rel);
            fs::create_dir_all(path.parent().unwrap()).unwrap();
            // Four 10 ms frames, stereo PCM16 at 48 kHz, with no embedded artwork.
            let data_len = (4 * A_FRAME) as u32;
            let mut bytes = Vec::new();
            bytes.extend_from_slice(b"RIFF");
            bytes.extend_from_slice(&(36 + data_len).to_le_bytes());
            bytes.extend_from_slice(b"WAVEfmt ");
            bytes.extend_from_slice(&16u32.to_le_bytes());
            bytes.extend_from_slice(&1u16.to_le_bytes());
            bytes.extend_from_slice(&2u16.to_le_bytes());
            bytes.extend_from_slice(&48000u32.to_le_bytes());
            bytes.extend_from_slice(&192000u32.to_le_bytes());
            bytes.extend_from_slice(&4u16.to_le_bytes());
            bytes.extend_from_slice(&16u16.to_le_bytes());
            bytes.extend_from_slice(b"data");
            bytes.extend_from_slice(&data_len.to_le_bytes());
            for _ in 0..data_len / 4 {
                bytes.extend_from_slice(&8192i16.to_le_bytes());
                bytes.extend_from_slice(&(-8192i16).to_le_bytes());
            }
            fs::write(path, bytes).unwrap();
        }

        fn path(&self, rel: &str) -> PathBuf {
            Path::new(self.root.path.as_ref().unwrap()).join(rel)
        }
    }

    impl Drop for WavTree {
        fn drop(&mut self) {
            if !std::thread::panicking() {
                fs::remove_dir_all(&self.base).unwrap();
            }
        }
    }

    fn resources() -> (FileSessionResources, Receiver<Vec<u8>>, Receiver<Ev>) {
        let (writer, frames) = mpsc::sync_channel(64);
        let (ctl, events) = mpsc::channel();
        (
            FileSessionResources {
                writer,
                ctl,
                counters: Arc::new(Counters::default()),
                art: Arc::new(Mutex::new(Default::default())),
                pump_ids: Arc::new(AtomicU64::new(0)),
                cancel: Arc::new(AtomicBool::new(false)),
            },
            frames,
            events,
        )
    }

    pub(crate) fn eof(events: &Receiver<Ev>) -> u64 {
        match events
            .recv_timeout(Duration::from_secs(10))
            .expect("finite ffmpeg EOF")
        {
            Ev::FileEof(id) => id,
            _ => panic!("expected a real file EOF"),
        }
    }

    pub(crate) fn pcm_frames(frames: &Receiver<Vec<u8>>) {
        let received: Vec<_> = frames
            .try_iter()
            .filter(|frame| frame[0] == proto::A)
            .collect();
        assert_eq!(received.len(), 4, "four finite PCM frames per WAV");
        for frame in received {
            assert_eq!(frame.len(), proto::HEADER_LEN + A_FRAME);
            assert_eq!(
                u32::from_be_bytes(frame[1..5].try_into().unwrap()),
                A_FRAME as u32
            );
            assert_eq!(&frame[5..9], &[0, 32, 0, 224]);
        }
    }

    #[test]
    #[cfg(unix)]
    fn folder_session_root_real_eof_relative_order_aliases_and_hidden_entries() {
        let tree = WavTree::new("tree");
        for name in [
            "00-root.wav",
            "10-album/01.WAV",
            "10-album/20-disc/01.wav",
            "15-middle.wav",
            "25-named.wav/01.wav",
            "90-last.wav",
            ".hidden.wav",
            ".hidden-dir/01.wav",
        ] {
            tree.wav(name);
        }
        fs::write(tree.path("readme.txt"), b"not audio").unwrap();
        fs::create_dir_all(tree.base.join("outside")).unwrap();
        fs::copy(tree.path("00-root.wav"), tree.base.join("outside/01.wav")).unwrap();
        std::os::unix::fs::symlink(tree.path("10-album"), tree.path("40-alias")).unwrap();
        std::os::unix::fs::symlink(tree.base.join("outside"), tree.path("30-outside")).unwrap();
        std::os::unix::fs::symlink(tree.base.join("missing"), tree.path("broken")).unwrap();
        std::os::unix::fs::symlink(tree.path(""), tree.path("10-album/back")).unwrap();
        let expected = [
            "00-root.wav",
            "10-album/01.WAV",
            "10-album/20-disc/01.wav",
            "15-middle.wav",
            "25-named.wav/01.wav",
            "30-outside/01.wav",
            "90-last.wav",
        ];
        let (resources, frames, events) = resources();
        let mut session = FileSession::open(tree.root.clone(), "", resources).unwrap();
        assert_eq!(session.entries, expected);
        let mut previous = 0;
        for (i, path) in expected.iter().enumerate() {
            let meta = session.meta();
            assert_eq!(meta.path.as_deref(), Some(*path));
            assert_eq!(meta.duration_ms, Some(40));
            assert!(meta.playing);
            assert_eq!(meta.source, "file");
            let id = session.current_pump_id().unwrap();
            assert!(id > previous);
            assert_eq!(eof(&events), id);
            pcm_frames(&frames);
            previous = id;
            assert_eq!(session.advance(1).unwrap(), i + 1 < expected.len());
        }
        assert!(!session.meta().playing);
        assert_eq!(session.current_pump_id(), None);
    }

    #[test]
    fn folder_session_directory_extension_and_nested_selection_keep_full_queue() {
        let tree = WavTree::new("named");
        tree.wav("album.wav/00.wav");
        tree.wav("album.wav/disc/01.wav");
        tree.wav("outside.wav");
        let (resources, frames, events) = resources();
        let mut session = FileSession::open(tree.root.clone(), "album.wav", resources).unwrap();
        assert_eq!(session.entries, ["00.wav", "disc/01.wav"]);
        assert_eq!(session.meta().path.as_deref(), Some("album.wav/00.wav"));
        assert_eq!(eof(&events), session.current_pump_id().unwrap());
        pcm_frames(&frames);
        assert!(session.advance(1).unwrap());
        assert_eq!(
            session.meta().path.as_deref(),
            Some("album.wav/disc/01.wav")
        );
        assert_eq!(eof(&events), session.current_pump_id().unwrap());
        pcm_frames(&frames);
        assert!(!session.advance(1).unwrap());
    }

    #[test]
    fn folder_session_direct_file_starts_requested_then_sorted_siblings() {
        let tree = WavTree::new("direct");
        for path in [
            "album/03.wav",
            "album/01.wav",
            "album/02.WAV",
            "album/nested/04.wav",
        ] {
            tree.wav(path);
        }
        let (resources, frames, events) = resources();
        let mut session = FileSession::open(tree.root.clone(), "album/02.WAV", resources).unwrap();
        assert_eq!(session.entries, ["01.wav", "02.WAV", "03.wav"]);
        assert_eq!(session.index, 1);
        for path in ["album/02.WAV", "album/03.wav"] {
            assert_eq!(session.meta().path.as_deref(), Some(path));
            assert_eq!(eof(&events), session.current_pump_id().unwrap());
            pcm_frames(&frames);
            assert_eq!(session.advance(1).unwrap(), path.ends_with("02.WAV"));
        }
    }

    #[test]
    #[cfg(unix)]
    fn folder_session_sorted_traversal_selects_first_canonical_alias() {
        let tree = WavTree::new("alias");
        tree.wav("z-album/01.wav");
        std::os::unix::fs::symlink(tree.path("z-album"), tree.path("a-alias")).unwrap();
        let selection = select(&tree.root, "", &AtomicBool::new(false)).unwrap();
        assert_eq!(selection.entries, ["a-alias/01.wav"]);
    }

    #[test]
    fn folder_session_local_backslash_names_remain_valid_for_direct_and_folder_play() {
        let tree = WavTree::new("backslash");
        tree.wav("album\\live/01\\take.WAV");
        for path in ["album\\live/01\\take.WAV", "album\\live", ""] {
            let (resources, frames, events) = resources();
            let session = FileSession::open(tree.root.clone(), path, resources).unwrap();
            assert_eq!(
                session.meta().path.as_deref(),
                Some("album\\live/01\\take.WAV")
            );
            assert_eq!(eof(&events), session.current_pump_id().unwrap());
            pcm_frames(&frames);
        }
    }

    #[test]
    fn folder_session_empty_missing_and_traversal_fail_with_fix() {
        let tree = WavTree::new("errors");
        fs::write(tree.path("readme.txt"), b"not audio").unwrap();
        for path in [
            "",
            "missing.wav",
            "../outside",
            "album/../../outside",
            "a\\b",
        ] {
            let (resources, _, _) = resources();
            let (error, fix) = FileSession::open(tree.root.clone(), path, resources)
                .err()
                .unwrap();
            assert!(!error.is_empty());
            assert!(!fix.is_empty());
        }
    }

    #[test]
    fn folder_session_supported_extensions_are_case_insensitive() {
        for extension in AUDIO_EXTS {
            assert!(is_audio(&format!("track.{extension}")));
            assert!(is_audio(&format!("track.{}", extension.to_uppercase())));
        }
        assert!(!is_audio("readme.txt"));
        assert!(!is_audio("track.wav.backup"));
    }

    #[test]
    fn folder_session_cancelled_before_selection_resolution_or_pump() {
        let tree = WavTree::new("cancel");
        tree.wav("01.wav");
        let cancelled = AtomicBool::new(true);
        assert!(
            select(&tree.root, "", &cancelled)
                .unwrap_err()
                .0
                .contains("cancelled")
        );
        assert!(list(&tree.root, "", &cancelled).is_err());
        assert!(resolve_abs(&tree.root, "01.wav", &cancelled).is_err());
        let selection = select(&tree.root, "", &AtomicBool::new(false)).unwrap();
        assert!(
            prepare_selection(selection, &cancelled, |_| panic!(
                "cancelled resolution ran"
            ))
            .is_err()
        );
        let (resources, _frames, events) = resources();
        let ids = resources.pump_ids.clone();
        resources.cancel.store(true, Ordering::SeqCst);
        assert!(FileSession::open(tree.root.clone(), "", resources).is_err());
        assert_eq!(ids.load(Ordering::Relaxed), 0);
        assert!(events.try_recv().is_err());
    }

    #[test]
    fn folder_session_cancellation_after_listing_prevents_descent_and_entries() {
        for directory in [true, false] {
            let cancel = AtomicBool::new(false);
            let mut visits = Vec::new();
            let mut entries = Vec::new();
            let result = walk_folder(
                "",
                "",
                &cancel,
                &mut HashSet::new(),
                &mut entries,
                &mut |path| {
                    visits.push(path.to_string());
                    cancel.store(true, Ordering::SeqCst);
                    Ok(proto::Listing {
                        root: "fixture".into(),
                        path: path.into(),
                        dirs: if directory {
                            vec!["child".into()]
                        } else {
                            vec![]
                        },
                        files: vec![proto::FileEntry {
                            name: "01.wav".into(),
                            size: 1,
                        }],
                    })
                },
                &mut |path| Ok(PathBuf::from(path)),
            );
            assert!(result.unwrap_err().0.contains("cancelled"));
            assert_eq!(visits, [""]);
            assert!(entries.is_empty());
        }
    }

    #[test]
    fn folder_session_rclone_parser_rejects_malformed_and_traversal_listings() {
        let tree = WavTree::new("remote-parser");
        for bad in [
            "null",
            "{}",
            "not json",
            "[{}]",
            r#"[{"Name":"../escape.wav","IsDir":false,"Size":1}]"#,
            r#"[{"Name":"disc/escape","IsDir":true}]"#,
            r#"[{"Name":"..","IsDir":true}]"#,
            r#"[{"Name":"ok.wav","Path":"../bad","IsDir":false,"Size":1}]"#,
            r#"[{"Name":"ok.wav","IsDir":"false","Size":1}]"#,
            r#"[{"Name":"ok.wav","IsDir":false,"Size":-1}]"#,
            r#"[{"Name":"ok.wav","IsDir":false,"Size":1},{"Name":"ok.wav","IsDir":true}]"#,
        ] {
            let err = parse_rclone_listing(&tree.root, "", bad.as_bytes(), &AtomicBool::new(false))
                .err()
                .unwrap();
            assert!(err.0.contains("malformed"), "{bad}");
            assert!(!err.1.is_empty());
        }
    }

    #[test]
    fn folder_session_rclone_parser_preserves_literal_backslashes() {
        let tree = WavTree::new("remote-backslash-parser");
        let json = br#"[{"Name":"album\\live","Path":"album\\live","IsDir":true},{"Name":"01\\take.WAV","Path":"01\\take.WAV","IsDir":false,"Size":7724},{"Name":"02.wav","IsDir":false,"Size":7724}]"#;
        let listing = parse_rclone_listing(&tree.root, "", json, &AtomicBool::new(false)).unwrap();
        assert_eq!(listing.dirs, ["album\\live"]);
        assert_eq!(
            listing
                .files
                .iter()
                .map(|f| f.name.as_str())
                .collect::<Vec<_>>(),
            ["01\\take.WAV", "02.wav"]
        );
    }

    #[test]
    fn folder_session_rclone_walk_and_prefetch_retain_nested_queue_lexical_duplicates_only() {
        let tree = WavTree::new("remote-tree");
        let cancel = AtomicBool::new(false);
        let mut visits = Vec::new();
        let mut entries = Vec::new();
        walk_folder("Albums", "", &cancel, &mut HashSet::new(), &mut entries,
            &mut |path| {
                visits.push(path.to_string());
                let json = match path {
                    "Albums" => r#"[{"Name":"z-disc","IsDir":true},{"Name":"album.wav","IsDir":true},{"Name":"album.wav","IsDir":true},{"Name":"middle.WAV","IsDir":false,"Size":2},{"Name":".hidden","IsDir":true},{"Name":".hidden.wav","IsDir":false,"Size":1},{"Name":"note.txt","IsDir":false,"Size":2}]"#,
                    "Albums/album.wav" | "Albums/z-disc" => r#"[{"Name":"01.WAV","IsDir":false,"Size":1},{"Name":"01.WAV","IsDir":false,"Size":1}]"#,
                    _ => panic!("unexpected descendant {path}"),
                };
                parse_rclone_listing(&tree.root, path, json.as_bytes(), &cancel)
            }, &mut |path| Ok(PathBuf::from(path))).unwrap();
        entries.sort();
        let selection = Selection {
            dir_rel: "Albums".into(),
            entries,
            index: 0,
        };
        let mut resolved = Vec::new();
        let prepared = prepare_selection(selection, &cancel, |path| {
            resolved.push(path.to_string());
            Ok(())
        })
        .unwrap();
        assert_eq!(visits, ["Albums", "Albums/album.wav", "Albums/z-disc"]);
        assert_eq!(resolved, ["Albums/album.wav/01.WAV"]);
        assert_eq!(
            prepared.entries,
            ["album.wav/01.WAV", "middle.WAV", "z-disc/01.WAV"]
        );
        assert_eq!(prepared.dir_rel, "Albums");
        assert_eq!(prepared.index, 0);
    }

    #[test]
    #[cfg(unix)]
    fn folder_session_rclone_command_boundary_isolated_child() {
        use std::os::unix::fs::{PermissionsExt, symlink};
        if let Some(base) = std::env::var_os("PHOSPHOR_RCLONE_FIXTURE") {
            rclone_command_child(&PathBuf::from(base));
            return;
        }
        let tree = WavTree::new("rclone-child");
        tree.wav("source.wav");
        fs::copy(tree.path("source.wav"), tree.base.join("source.wav")).unwrap();
        let bin = tree.base.join("bin");
        let home = tree.base.join("home");
        fs::create_dir_all(&bin).unwrap();
        fs::create_dir_all(&home).unwrap();
        for tool in ["ffmpeg", "ffprobe"] {
            let executable = std::env::split_paths(&std::env::var_os("PATH").unwrap())
                .map(|dir| dir.join(tool))
                .find(|p| p.is_file())
                .expect("host ffmpeg and ffprobe required");
            symlink(fs::canonicalize(executable).unwrap(), bin.join(tool)).unwrap();
        }
        // PATH contains only this fixture and the two real finite decoders. No backend can run.
        let shim = bin.join("rclone");
        fs::write(&shim, r#"#!/bin/sh
set -eu
printf '%s|%s|%s\n' "$1" "$2" "${3-}" >> "$PHOSPHOR_RCLONE_FIXTURE/calls"
case "$1|$2" in
  'lsjson|fixture:music')
    test "$#" = 2
    printf '%s' '[{"Name":"album.wav","IsDir":true},{"Name":"root.wav","IsDir":false,"Size":7724}]' ;;
  'lsjson|fixture:music/album.wav')
    test "$#" = 2
    printf '%s' '[{"Name":"nested","IsDir":true},{"Name":"00.wav","IsDir":false,"Size":7724}]' ;;
  'lsjson|fixture:music/album.wav/nested')
    test "$#" = 2
    printf '%s' '[{"Name":"01.wav","IsDir":false,"Size":7724}]' ;;
  'lsjson|fixture:music/album.wav/00.wav'|'lsjson|fixture:music/album.wav/nested/01.wav'|'lsjson|fixture:music/root.wav')
    test "$#" = 2
    printf '%s' '[{"Name":"audio.wav","IsDir":false,"Size":7724}]' ;;
  'lsjson|malformed:music')
    test "$#" = 2
    printf '%s' '[{"Name":"../escape.wav","IsDir":false,"Size":7724}]' ;;
  'lsjson|literal:music')
    test "$#" = 2
    printf '%s' '[{"Name":"album\\live","Path":"album\\live","IsDir":true}]' ;;
  'lsjson|literal:music/album\live')
    test "$#" = 2
    printf '%s' '[{"Name":"01\\take.WAV","Path":"01\\take.WAV","IsDir":false,"Size":7724},{"Name":"02.wav","IsDir":false,"Size":7724}]' ;;
  'lsjson|literal:music/album\live/01\take.WAV'|'lsjson|literal:music/album\live/02.wav')
    test "$#" = 2
    printf '%s' '[{"Name":"audio.wav","IsDir":false,"Size":7724}]' ;;
  'copyto|literal:music/album\live/01\take.WAV'|'copyto|literal:music/album\live/02.wav')
    test "$#" = 3
    case "$3" in "$HOME/.cache/phosphor-relay/drive/"*.wav) ;; *) exit 97 ;; esac
    /bin/cp "$PHOSPHOR_RCLONE_FIXTURE/source.wav" "$3" ;;
  'copyto|fixture:music/album.wav/00.wav'|'copyto|fixture:music/album.wav/nested/01.wav'|'copyto|fixture:music/root.wav')
    test "$#" = 3
    case "$3" in "$HOME/.cache/phosphor-relay/drive/"*.wav) ;; *) exit 97 ;; esac
    /bin/cp "$PHOSPHOR_RCLONE_FIXTURE/source.wav" "$3" ;;
  *) exit 98 ;;
esac
"#).unwrap();
        fs::set_permissions(&shim, fs::Permissions::from_mode(0o700)).unwrap();
        let mut child = Command::new(std::env::current_exe().unwrap());
        child
            .args([
                "--exact",
                "library::folder_tests::folder_session_rclone_command_boundary_isolated_child",
                "--nocapture",
            ])
            .env_clear()
            .env("HOME", &home)
            .env("PATH", &bin)
            .env("TMPDIR", std::env::temp_dir())
            .env("PHOSPHOR_RCLONE_FIXTURE", &tree.base);
        let output =
            util::run_cancellable(&mut child, Duration::from_secs(30), &AtomicBool::new(false))
                .unwrap();
        assert!(
            output.status.success(),
            "isolated command fixture failed:\n{}",
            String::from_utf8_lossy(&output.stdout)
        );
    }

    #[cfg(unix)]
    fn rclone_command_child(base: &Path) {
        let root = LibraryRoot {
            id: "fixture".into(),
            label: "Fixture".into(),
            path: None,
            rclone: Some("fixture:music".into()),
        };
        let cancel = AtomicBool::new(false);
        let prepared = prefetch(&root, "album.wav", &cancel).unwrap();
        assert_eq!(prepared.dir_rel, "album.wav");
        assert_eq!(prepared.entries, ["00.wav", "nested/01.wav"]);
        let calls = || fs::read_to_string(base.join("calls")).unwrap();
        let prefetched_calls = calls();
        let (handles, frames, events) = resources();
        let mut session = FileSession::open_selection(root.clone(), prepared, handles).unwrap();
        assert_eq!(session.meta().path.as_deref(), Some("album.wav/00.wav"));
        assert_eq!(
            calls(),
            prefetched_calls,
            "first item must hit the prefetched cache without relisting the folder"
        );
        assert_eq!(eof(&events), session.current_pump_id().unwrap());
        pcm_frames(&frames);
        assert!(session.advance(1).unwrap());
        assert_eq!(
            session.meta().path.as_deref(),
            Some("album.wav/nested/01.wav")
        );
        assert_eq!(eof(&events), session.current_pump_id().unwrap());
        pcm_frames(&frames);
        assert!(!session.advance(1).unwrap());
        drop(session);
        let direct = prefetch(&root, "root.wav", &cancel).unwrap();
        assert_eq!(direct.dir_rel, "");
        assert_eq!(direct.entries, ["root.wav"]);
        prefetch(&root, "root.wav", &cancel).unwrap();
        let (handles, frames, events) = resources();
        let session = FileSession::open(root.clone(), "root.wav", handles).unwrap();
        assert_eq!(eof(&events), session.current_pump_id().unwrap());
        pcm_frames(&frames);
        assert_eq!(session.meta().path.as_deref(), Some("root.wav"));
        drop(session);

        let mut expected = vec![
            "lsjson|fixture:music|".to_string(),
            "lsjson|fixture:music/album.wav|".into(),
            "lsjson|fixture:music/album.wav/nested|".into(),
        ];
        let copied = |path: &str| {
            let full = format!("fixture:music/{path}");
            let sha = util::sha256_hex(full.as_bytes());
            let cache = util::cache_dir()
                .join("drive")
                .join(format!("{}.wav", &sha[..16]));
            vec![
                format!("lsjson|{full}|"),
                format!("copyto|{full}|{}", cache.display()),
            ]
        };
        expected.extend(copied("album.wav/00.wav"));
        expected.extend(copied("album.wav/nested/01.wav"));
        expected.push("lsjson|fixture:music|".into());
        expected.push("lsjson|fixture:music|".into()); // direct selection's sibling list
        expected.extend(copied("root.wav"));
        expected.extend(vec!["lsjson|fixture:music|".into(); 4]); // second prefetch and direct open, two listings each
        assert_eq!(calls().lines().collect::<Vec<_>>(), expected);

        let before_cancel = calls();
        assert!(
            prefetch(&root, "", &AtomicBool::new(true))
                .unwrap_err()
                .0
                .contains("cancelled")
        );
        assert!(prefetch(&root, "../escape.wav", &cancel).is_err());
        assert_eq!(
            calls(),
            before_cancel,
            "cancel and traversal must launch no rclone command"
        );
        let malformed = LibraryRoot {
            rclone: Some("malformed:music".into()),
            ..root
        };
        assert!(
            prefetch(&malformed, "", &cancel)
                .unwrap_err()
                .0
                .contains("malformed")
        );
        assert_eq!(calls(), format!("{before_cancel}lsjson|malformed:music|\n"));

        let before_literal = calls();
        let literal = LibraryRoot {
            rclone: Some("literal:music".into()),
            ..malformed
        };
        let direct = prefetch(&literal, "album\\live/01\\take.WAV", &cancel).unwrap();
        assert_eq!(direct.entries, ["01\\take.WAV", "02.wav"]);
        let (handles, frames, events) = resources();
        let direct = FileSession::open_selection(literal.clone(), direct, handles).unwrap();
        assert_eq!(
            direct.meta().path.as_deref(),
            Some("album\\live/01\\take.WAV")
        );
        assert_eq!(eof(&events), direct.current_pump_id().unwrap());
        pcm_frames(&frames);
        drop(direct);
        let folder = prefetch(&literal, "album\\live", &cancel).unwrap();
        assert_eq!(folder.entries, ["01\\take.WAV", "02.wav"]);
        let (handles, frames, events) = resources();
        let mut folder = FileSession::open_selection(literal, folder, handles).unwrap();
        assert_eq!(eof(&events), folder.current_pump_id().unwrap());
        pcm_frames(&frames);
        assert!(folder.advance(1).unwrap());
        assert_eq!(folder.meta().path.as_deref(), Some("album\\live/02.wav"));
        assert_eq!(eof(&events), folder.current_pump_id().unwrap());
        pcm_frames(&frames);
        assert!(!folder.advance(1).unwrap());
        let literal_calls = calls().strip_prefix(&before_literal).unwrap().to_owned();
        let copy_line = |path: &str| {
            let full = format!("literal:music/{path}");
            let sha = util::sha256_hex(full.as_bytes());
            format!(
                "copyto|{full}|{}",
                util::cache_dir()
                    .join("drive")
                    .join(format!("{}.wav", &sha[..16]))
                    .display()
            )
        };
        assert_eq!(
            literal_calls.lines().collect::<Vec<_>>(),
            [
                "lsjson|literal:music/album\\live|".to_string(),
                "lsjson|literal:music/album\\live|".into(),
                "lsjson|literal:music/album\\live/01\\take.WAV|".into(),
                copy_line("album\\live/01\\take.WAV"),
                "lsjson|literal:music|".into(),
                "lsjson|literal:music/album\\live|".into(),
                "lsjson|literal:music/album\\live/02.wav|".into(),
                copy_line("album\\live/02.wav"),
            ]
        );
    }
}
