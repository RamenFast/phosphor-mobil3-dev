//! Small dependency-free helpers shared across the relay: paths, hostname,
//! timestamps (ISO-8601 with the machine's real UTC offset), a pure-Rust
//! SHA-256 (art ids — no crate for it, deps are serde-only), and a PATH probe.

use std::io::Read;
use std::path::PathBuf;
use std::process::{Child, Command, Output, Stdio};
use std::sync::OnceLock;
use std::sync::atomic::{AtomicBool, Ordering};
use std::thread::JoinHandle;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

pub fn home() -> PathBuf {
    std::env::var_os("HOME").map(PathBuf::from).unwrap_or_else(|| PathBuf::from("/"))
}

pub fn config_dir() -> PathBuf {
    home().join(".config/phosphor-relay")
}

pub fn cache_dir() -> PathBuf {
    home().join(".cache/phosphor-relay")
}

/// Best-effort machine hostname without a syscall crate.
pub fn hostname() -> String {
    if let Ok(s) = std::fs::read_to_string("/etc/hostname") {
        let s = s.trim();
        if !s.is_empty() {
            return s.to_string();
        }
    }
    std::env::var("HOSTNAME").ok().filter(|s| !s.is_empty()).unwrap_or_else(|| "localhost".into())
}

/// Wall clock — K payloads, logs, art ids. Liveness math uses mono_ms()
/// (audit finding 13: a clock step must never kill or immortalize a session).
pub fn now_ms() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis() as u64).unwrap_or(0)
}

/// Process-epoch monotonic milliseconds (immune to wall-clock steps).
pub fn mono_ms() -> u64 {
    static EPOCH: OnceLock<Instant> = OnceLock::new();
    EPOCH.get_or_init(Instant::now).elapsed().as_millis() as u64
}

/// Run an external command with a HARD deadline and a cancellation flag
/// (audit finding 4: no child may ever hold the session hostage). Polls
/// `try_wait` every 25 ms; on deadline OR `cancel` it kills + reaps the child
/// and reports which tripped. Stdout drains concurrently so a large pw-dump
/// cannot fill its pipe and deadlock before exit (finding 14's real-graph
/// corner). All the relay's rclone/ffmpeg/ffprobe/playerctl/curl/pw-dump/wpctl/
/// pactl invocations go through here.
pub fn run_cancellable(
    cmd: &mut Command,
    deadline: Duration,
    cancel: &AtomicBool,
) -> Result<Output, String> {
    cmd.stdin(Stdio::null());
    let mut child: Child = cmd
        .stdout(Stdio::piped())
        .stderr(Stdio::null())
        .spawn()
        .map_err(|e| format!("spawn: {e}"))?;
    let mut stdout = child.stdout.take().ok_or_else(|| "stdout pipe missing".to_string())?;
    let mut reader = Some(std::thread::spawn(move || {
        let mut bytes = Vec::new();
        stdout.read_to_end(&mut bytes).map(|_| bytes)
    }));
    let t0 = Instant::now();
    loop {
        match child.try_wait() {
            Ok(Some(status)) => {
                let stdout = join_stdout(&mut reader)?;
                return Ok(Output { status, stdout, stderr: Vec::new() });
            }
            Ok(None) => {}
            Err(e) => {
                let _ = child.kill();
                let _ = child.wait();
                let _ = join_stdout(&mut reader);
                return Err(format!("wait: {e}"));
            }
        }
        if cancel.load(Ordering::Relaxed) {
            let _ = child.kill();
            let _ = child.wait();
            let _ = join_stdout(&mut reader);
            return Err("cancelled (session ending)".into());
        }
        if t0.elapsed() >= deadline {
            let _ = child.kill();
            let _ = child.wait();
            let _ = join_stdout(&mut reader);
            return Err(format!("deadline {deadline:?} exceeded — child killed"));
        }
        std::thread::sleep(Duration::from_millis(25));
    }
}

fn join_stdout(reader: &mut Option<JoinHandle<std::io::Result<Vec<u8>>>>) -> Result<Vec<u8>, String> {
    reader
        .take()
        .ok_or_else(|| "stdout reader already joined".to_string())?
        .join()
        .map_err(|_| "stdout reader panicked".to_string())?
        .map_err(|e| format!("collect output: {e}"))
}

/// Local UTC offset in seconds, read once via `date +%:z` (one spawn/process).
fn offset_secs() -> i64 {
    static OFF: OnceLock<i64> = OnceLock::new();
    *OFF.get_or_init(|| {
        let out = std::process::Command::new("date").arg("+%:z").output();
        let s = out.ok().map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string()).unwrap_or_default();
        // s like "-07:00" / "+00:00"
        parse_offset(&s).unwrap_or(0)
    })
}

fn parse_offset(s: &str) -> Option<i64> {
    let b = s.as_bytes();
    if b.len() < 6 {
        return None;
    }
    let sign = match b[0] {
        b'+' => 1,
        b'-' => -1,
        _ => return None,
    };
    let h: i64 = s[1..3].parse().ok()?;
    let m: i64 = s[4..6].parse().ok()?;
    Some(sign * (h * 3600 + m * 60))
}

fn off_string(off: i64) -> String {
    let sign = if off < 0 { '-' } else { '+' };
    let a = off.abs();
    format!("{sign}{:02}:{:02}", a / 3600, (a % 3600) / 60)
}

/// ISO-8601 with the machine's real UTC offset, per AGENT-CLI R1.
pub fn iso8601_now() -> String {
    let off = offset_secs();
    let secs = (SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0) as i64) + off;
    let (y, mo, d, h, mi, s) = civil(secs);
    format!("{y:04}-{mo:02}-{d:02}T{h:02}:{mi:02}:{s:02}{}", off_string(off))
}

/// days-since-epoch → civil date (Howard Hinnant's algorithm), then clock.
fn civil(secs: i64) -> (i64, i64, i64, i64, i64, i64) {
    let days = secs.div_euclid(86400);
    let rem = secs.rem_euclid(86400);
    let (h, mi, s) = (rem / 3600, (rem % 3600) / 60, rem % 60);
    let z = days + 719468;
    let era = if z >= 0 { z } else { z - 146096 } / 146097;
    let doe = z - era * 146097;
    let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
    let y = yoe + era * 400;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = doy - (153 * mp + 2) / 5 + 1;
    let mo = if mp < 10 { mp + 3 } else { mp - 9 };
    (if mo <= 2 { y + 1 } else { y }, mo, d, h, mi, s)
}

/// Is `name` an executable on PATH? (dependency-free `which`).
pub fn tool_exists(name: &str) -> bool {
    let Some(path) = std::env::var_os("PATH") else { return false };
    std::env::split_paths(&path).any(|dir| {
        let p = dir.join(name);
        std::fs::metadata(&p).map(|m| m.is_file()).unwrap_or(false)
    })
}

const SHA_K: [u32; 64] = [
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
];

/// Pure SHA-256; returns 64 lowercase hex chars. Art ids are its first 16.
pub fn sha256_hex(data: &[u8]) -> String {
    let mut h: [u32; 8] = [
        0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19,
    ];
    let bitlen = (data.len() as u64).wrapping_mul(8);
    let mut msg = data.to_vec();
    msg.push(0x80);
    while msg.len() % 64 != 56 {
        msg.push(0);
    }
    msg.extend_from_slice(&bitlen.to_be_bytes());
    for chunk in msg.chunks(64) {
        let mut w = [0u32; 64];
        for i in 0..16 {
            w[i] = u32::from_be_bytes([chunk[i * 4], chunk[i * 4 + 1], chunk[i * 4 + 2], chunk[i * 4 + 3]]);
        }
        for i in 16..64 {
            let s0 = w[i - 15].rotate_right(7) ^ w[i - 15].rotate_right(18) ^ (w[i - 15] >> 3);
            let s1 = w[i - 2].rotate_right(17) ^ w[i - 2].rotate_right(19) ^ (w[i - 2] >> 10);
            w[i] = w[i - 16].wrapping_add(s0).wrapping_add(w[i - 7]).wrapping_add(s1);
        }
        let (mut a, mut b, mut c, mut d, mut e, mut f, mut g, mut hh) =
            (h[0], h[1], h[2], h[3], h[4], h[5], h[6], h[7]);
        for i in 0..64 {
            let s1 = e.rotate_right(6) ^ e.rotate_right(11) ^ e.rotate_right(25);
            let ch = (e & f) ^ ((!e) & g);
            let t1 = hh.wrapping_add(s1).wrapping_add(ch).wrapping_add(SHA_K[i]).wrapping_add(w[i]);
            let s0 = a.rotate_right(2) ^ a.rotate_right(13) ^ a.rotate_right(22);
            let maj = (a & b) ^ (a & c) ^ (b & c);
            let t2 = s0.wrapping_add(maj);
            hh = g; g = f; f = e; e = d.wrapping_add(t1);
            d = c; c = b; b = a; a = t1.wrapping_add(t2);
        }
        h[0] = h[0].wrapping_add(a); h[1] = h[1].wrapping_add(b);
        h[2] = h[2].wrapping_add(c); h[3] = h[3].wrapping_add(d);
        h[4] = h[4].wrapping_add(e); h[5] = h[5].wrapping_add(f);
        h[6] = h[6].wrapping_add(g); h[7] = h[7].wrapping_add(hh);
    }
    let mut out = String::with_capacity(64);
    for v in h {
        out.push_str(&format!("{v:08x}"));
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sha256_known_vectors() {
        assert_eq!(sha256_hex(b""), "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        assert_eq!(sha256_hex(b"abc"), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    #[test]
    fn offset_parse() {
        assert_eq!(parse_offset("-07:00"), Some(-25200));
        assert_eq!(parse_offset("+00:00"), Some(0));
        assert_eq!(parse_offset("+05:30"), Some(19800));
        assert_eq!(parse_offset("bad"), None);
    }

    #[test]
    fn civil_epoch() {
        // 0 → 1970-01-01T00:00:00
        assert_eq!(civil(0), (1970, 1, 1, 0, 0, 0));
        // 1_600_000_000 → 2020-09-13T12:26:40Z
        assert_eq!(civil(1_600_000_000), (2020, 9, 13, 12, 26, 40));
    }

    #[test]
    fn mono_ms_is_monotonic() {
        let a = mono_ms();
        std::thread::sleep(Duration::from_millis(5));
        assert!(mono_ms() >= a + 4);
    }

    #[test]
    fn run_cancellable_deadline_kills() {
        let mut cmd = Command::new("sleep");
        cmd.arg("5");
        let cancel = AtomicBool::new(false);
        let t0 = Instant::now();
        let res = run_cancellable(&mut cmd, Duration::from_millis(120), &cancel);
        assert!(res.is_err() && res.unwrap_err().contains("deadline"));
        assert!(t0.elapsed() < Duration::from_secs(2), "killed promptly, not waited out");
    }

    #[test]
    fn run_cancellable_cancel_kills_early() {
        let mut cmd = Command::new("sleep");
        cmd.arg("5");
        let cancel = AtomicBool::new(true); // pre-cancelled
        let t0 = Instant::now();
        let res = run_cancellable(&mut cmd, Duration::from_secs(30), &cancel);
        assert!(res.is_err() && res.unwrap_err().contains("cancelled"));
        assert!(t0.elapsed() < Duration::from_secs(1));
    }

    #[test]
    fn run_cancellable_collects_output() {
        let mut cmd = Command::new("echo");
        cmd.arg("hi");
        let cancel = AtomicBool::new(false);
        let out = run_cancellable(&mut cmd, Duration::from_secs(5), &cancel).unwrap();
        assert_eq!(String::from_utf8_lossy(&out.stdout).trim(), "hi");
    }

    #[test]
    fn run_cancellable_drains_more_than_a_pipe_buffer() {
        let mut cmd = Command::new("head");
        cmd.args(["-c", "262144", "/dev/zero"]);
        let cancel = AtomicBool::new(false);
        let out = run_cancellable(&mut cmd, Duration::from_secs(5), &cancel).unwrap();
        assert_eq!(out.stdout.len(), 262144);
    }
}
