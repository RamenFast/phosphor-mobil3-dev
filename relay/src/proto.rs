//! The wire protocol: `[1-byte type][4-byte BE u32 len][payload]`, one TCP
//! connection. Frame tags, size caps, the (de)serialised payload structs, and
//! the three primitives every module shares: `encode_frame`, `write_frame`,
//! `read_frame`. v2 of docs/BRIDGE.md.

use std::io::{self, Read, Write};

use serde::{Deserialize, Serialize};

// ── Frame tags (byte values are the wire contract; do not renumber) ──────────
pub const W: u8 = 0x57; // welcome              server→client
pub const A: u8 = 0x41; // audio pcm            server→client
pub const G: u8 = 0x47; // geometry (tap json)  server→client
pub const M: u8 = 0x4D; // now-playing metadata server→client
pub const S: u8 = 0x53; // source list          server→client
pub const L: u8 = 0x4C; // library listing      server→client
pub const R: u8 = 0x52; // art blob / art req   both ways
pub const E: u8 = 0x45; // error                server→client
pub const K: u8 = 0x4B; // stats / keepalive    both ways
pub const H: u8 = 0x48; // hello (toggles)      client→server
pub const T: u8 = 0x54; // transport            client→server
pub const Q: u8 = 0x51; // query sources        client→server
pub const C: u8 = 0x43; // choose source        client→server
pub const B: u8 = 0x42; // browse library       client→server
pub const P: u8 = 0x50; // play file / stop     client→server
pub const V: u8 = 0x56; // scope control (ctl)  client→server

// ── Size caps ────────────────────────────────────────────────────────────────
pub const MAX_C2S: usize = 64 * 1024; // client→server payload cap
pub const MAX_S2C: usize = 8 * 1024 * 1024; // server→client payload cap
pub const A_FRAME: usize = 1920; // 10 ms s16le stereo @ 48 kHz

pub const PROTO: u32 = 2;
pub const VERSION: &str = env!("CARGO_PKG_VERSION"); // single source: Cargo.toml
pub const TOOL: &str = "phosphor-relay";

/// Read error distinguishes an oversize declaration (→ E + close) from a plain
/// disconnect/EOF (→ clean teardown). The IO cause isn't needed downstream —
/// both mean "this connection is done".
#[derive(Debug)]
pub enum ReadErr {
    Oversize(usize),
    Io,
}

impl From<io::Error> for ReadErr {
    fn from(_: io::Error) -> Self {
        ReadErr::Io
    }
}

/// Assemble one full frame into a single buffer. The single-writer thread sends
/// these whole — a frame is never split across writes, so mid-frame interleave
/// (v1's corruption bug) is impossible by construction.
pub fn encode_frame(tag: u8, payload: &[u8]) -> Vec<u8> {
    let mut buf = Vec::with_capacity(5 + payload.len());
    buf.push(tag);
    buf.extend_from_slice(&(payload.len() as u32).to_be_bytes());
    buf.extend_from_slice(payload);
    buf
}

pub fn write_frame<Wr: Write>(w: &mut Wr, tag: u8, payload: &[u8]) -> io::Result<()> {
    w.write_all(&encode_frame(tag, payload))
}

/// Read one frame. `max` guards the declared length before allocating: a client
/// claiming more than the cap is rejected via `ReadErr::Oversize` (never a huge
/// alloc). Unknown tags are *not* handled here — the dispatcher skips them, so
/// this always returns the (tag, payload) pair for a well-framed message.
pub fn read_frame<Rd: Read>(r: &mut Rd, max: usize) -> Result<(u8, Vec<u8>), ReadErr> {
    let mut hdr = [0u8; 5];
    r.read_exact(&mut hdr)?;
    let tag = hdr[0];
    let len = u32::from_be_bytes([hdr[1], hdr[2], hdr[3], hdr[4]]) as usize;
    if len > max {
        return Err(ReadErr::Oversize(len));
    }
    let mut payload = vec![0u8; len];
    r.read_exact(&mut payload)?;
    Ok((tag, payload))
}

// ── Server→client payloads ───────────────────────────────────────────────────

#[derive(Serialize, Clone, Copy)]
pub struct Caps {
    pub audio: bool,
    pub geometry: bool,
    pub per_app: bool,
    pub library: bool,
    pub art: bool,
}

impl Caps {
    /// Probe the host once for which subsystems are actually available.
    pub fn detect() -> Caps {
        use crate::util::tool_exists;
        Caps {
            audio: true,
            geometry: tool_exists("phosphor"),
            per_app: tool_exists("pw-record"),
            library: tool_exists("ffmpeg"),
            art: tool_exists("curl"),
        }
    }
}

#[derive(Serialize)]
pub struct LibraryInfo {
    pub id: String,
    pub label: String,
    pub path: String,
}

#[derive(Serialize)]
pub struct Welcome {
    pub proto: u32,
    pub tool: &'static str,
    pub version: &'static str,
    pub host: String,
    pub caps: Caps,
    pub selected: String,
    pub libraries: Vec<LibraryInfo>,
}

#[derive(Serialize, Clone, Default)]
pub struct Meta {
    pub title: String,
    pub artist: String,
    pub album: String,
    pub playing: bool,
    pub position_ms: u64,
    pub duration_ms: Option<u64>,
    pub can_seek: bool,
    pub source: String, // "player" | "file"
    pub art_id: Option<String>,
    pub path: Option<String>,
}

#[derive(Serialize)]
pub struct SourceEntry {
    pub id: String,
    pub kind: String, // "app" | "monitor"
    pub label: String,
    pub available: bool,
}

#[derive(Serialize)]
pub struct Sources {
    pub sources: Vec<SourceEntry>,
    pub selected: String,
}

#[derive(Serialize)]
pub struct FileEntry {
    pub name: String,
    pub size: u64,
}

#[derive(Serialize)]
pub struct Listing {
    pub root: String,
    pub path: String,
    pub dirs: Vec<String>,
    pub files: Vec<FileEntry>,
}

#[derive(Serialize)]
pub struct ArtHeader {
    pub id: String,
    pub mime: String,
}

#[derive(Serialize)]
pub struct ErrorFrame {
    pub error: String,
    pub fix: String,
    #[serde(skip_serializing_if = "serde_json::Value::is_null")]
    pub context: serde_json::Value,
}

#[derive(Serialize)]
pub struct Stats {
    pub ts_ms: u64,
    pub tx_a: u64,
    pub tx_g: u64,
    pub dropped_a: u64,
    /// Live desktop scope state (probe reply subset) while geometry streams —
    /// the phone's honesty source for mode/gain/auto (`auto · pc`). Absent when
    /// desktop phosphor isn't running. Old phones skip unknown JSON keys.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub scope: Option<serde_json::Value>,
}

#[derive(serde::Deserialize)]
pub struct ScopeCtl {
    pub verb: String,
    #[serde(default)]
    pub value: String,
}

// ── Client→server payloads ───────────────────────────────────────────────────

#[derive(Deserialize, Clone, Copy)]
pub struct Hello {
    // `proto` and `client` ride on the wire but the relay doesn't branch on
    // them; serde ignores the extra keys.
    #[serde(default)]
    pub audio: bool,
    #[serde(default)]
    pub geometry: bool,
    #[serde(default = "default_fps")]
    pub geometry_fps: u32,
}

fn default_fps() -> u32 {
    60
}

#[derive(Deserialize)]
pub struct Transport {
    pub cmd: String,
    #[serde(default)]
    pub ms: Option<u64>,
}

#[derive(Deserialize)]
pub struct Choose {
    pub id: String,
}

#[derive(Deserialize)]
pub struct Browse {
    pub root: String,
    #[serde(default)]
    pub path: String,
}

#[derive(Deserialize)]
pub struct Play {
    #[serde(default)]
    pub root: String,
    #[serde(default)]
    pub path: String,
    #[serde(default)]
    pub action: String, // "stop" or empty
}

#[derive(Deserialize)]
pub struct ArtReq {
    pub id: String,
}

/// Build an E frame body. Every error a user can hit carries a `fix` (house law).
pub fn error_frame(error: &str, fix: &str, context: serde_json::Value) -> Vec<u8> {
    serde_json::to_vec(&ErrorFrame { error: error.into(), fix: fix.into(), context }).unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Cursor;

    #[test]
    fn roundtrip_frames() {
        let mut buf = Vec::new();
        write_frame(&mut buf, M, br#"{"title":"x"}"#).unwrap();
        write_frame(&mut buf, A, &[1, 2, 3, 4]).unwrap();
        let mut cur = Cursor::new(buf);
        let (t1, p1) = read_frame(&mut cur, MAX_S2C).unwrap();
        assert_eq!(t1, M);
        assert_eq!(p1, br#"{"title":"x"}"#);
        let (t2, p2) = read_frame(&mut cur, MAX_S2C).unwrap();
        assert_eq!(t2, A);
        assert_eq!(p2, vec![1, 2, 3, 4]);
    }

    #[test]
    fn oversize_rejected_before_alloc() {
        // header declares 100 MiB with a 64 KiB cap → Oversize, no huge alloc.
        let mut hdr = vec![A];
        hdr.extend_from_slice(&(100u32 * 1024 * 1024).to_be_bytes());
        let mut cur = Cursor::new(hdr);
        match read_frame(&mut cur, MAX_C2S) {
            Err(ReadErr::Oversize(n)) => assert_eq!(n, 100 * 1024 * 1024),
            _ => panic!("expected Oversize"),
        }
    }

    #[test]
    fn unknown_tag_reads_and_skips() {
        // An unknown tag frame is still well-framed: read_frame returns it whole,
        // the dispatcher ignores the tag, and the next frame parses cleanly.
        let mut buf = Vec::new();
        write_frame(&mut buf, 0xFF, b"ignore me").unwrap();
        write_frame(&mut buf, T, b"next").unwrap();
        let mut cur = Cursor::new(buf);
        let (t1, _) = read_frame(&mut cur, MAX_C2S).unwrap();
        assert_eq!(t1, 0xFF); // caller skips
        let (t2, p2) = read_frame(&mut cur, MAX_C2S).unwrap();
        assert_eq!(t2, T);
        assert_eq!(p2, b"next");
    }

    #[test]
    fn empty_payload_roundtrips() {
        let mut buf = Vec::new();
        write_frame(&mut buf, Q, &[]).unwrap();
        let mut cur = Cursor::new(buf);
        let (t, p) = read_frame(&mut cur, MAX_C2S).unwrap();
        assert_eq!(t, Q);
        assert!(p.is_empty());
    }
}
