//! Host-testable core of the phone↔relay bridge: the frame codec, the dedicated
//! writer loop, bounded thread joins, timeout-surviving reads, and the pure
//! decision tables (backoff, watchdog). No Android, no oboe, no JNI — plain
//! `cargo test` exercises everything here; the android-gated `remote` consumes it.

use std::io::{self, Read, Write};
use std::sync::atomic::{AtomicBool, AtomicU32, AtomicU64, Ordering};
use std::sync::mpsc::{Receiver, RecvTimeoutError};
use std::thread::JoinHandle;
use std::time::{Duration, Instant};

/// One pre-encoded wire frame: `[tag][BE u32 len][payload]`.
pub fn encode_frame(tag: u8, payload: &[u8]) -> Vec<u8> {
    let mut v = Vec::with_capacity(5 + payload.len());
    v.push(tag);
    v.extend_from_slice(&(payload.len() as u32).to_be_bytes());
    v.extend_from_slice(payload);
    v
}

/// Commands for the writer thread. Frames arrive pre-encoded so the writer does
/// no formatting; `Hello` is a wake marker only — the real H is rebuilt from
/// live config whenever the dirty flag is set (coalesced, unlosable).
pub enum WriterCmd {
    Frame(Vec<u8>),
    Hello,
}

pub struct WriterControl<'a> {
    pub cancel: &'a AtomicBool,
    pub hello_dirty: &'a AtomicBool,
    pub ping_period: Duration,
}

/// The dedicated writer loop is the only place session bytes are written. H always
/// goes first as the relay's first client frame; K self-
/// generates on `ping_period` and is never queued, so a full command queue can
/// never starve liveness; any write error/timeout reports through `on_fatal`
/// exactly once and the loop exits.
pub fn run_writer<W: Write>(
    mut sink: W,
    rx: Receiver<WriterCmd>,
    control: WriterControl<'_>,
    mut build_hello: impl FnMut() -> Vec<u8>,
    mut build_ping: impl FnMut() -> Vec<u8>,
    on_fatal: impl FnOnce(String),
) {
    let tick = control
        .ping_period
        .min(Duration::from_millis(250))
        .max(Duration::from_millis(5));
    let result = (|| -> Result<(), String> {
        let put = |sink: &mut W, buf: &[u8], what: &str| -> Result<(), String> {
            sink.write_all(buf)
                .and_then(|_| sink.flush())
                .map_err(|e| format!("{what} write: {e}"))
        };
        put(&mut sink, &build_hello(), "hello")?;
        control.hello_dirty.store(false, Ordering::Relaxed);
        let mut next_ping = Instant::now() + control.ping_period;
        loop {
            if control.cancel.load(Ordering::Relaxed) {
                return Ok(());
            }
            if control.hello_dirty.swap(false, Ordering::Relaxed) {
                put(&mut sink, &build_hello(), "hello")?;
            }
            if Instant::now() >= next_ping {
                put(&mut sink, &build_ping(), "ping")?;
                next_ping = Instant::now() + control.ping_period;
            }
            match rx.recv_timeout(tick) {
                Ok(WriterCmd::Frame(buf)) => put(&mut sink, &buf, "frame")?,
                Ok(WriterCmd::Hello) => {} // wake only — flag handled at loop top
                Err(RecvTimeoutError::Timeout) => {}
                Err(RecvTimeoutError::Disconnected) => return Ok(()),
            }
        }
    })();
    if let Err(e) = result {
        on_fatal(e);
    }
}

/// Join with a deadline: poll `is_finished` every 15 ms; on expiry DETACH (drop
/// the handle), count the leak, and log — never block a teardown forever on a
/// thread that is designed to self-exit within one wake period anyway.
pub fn bounded_join(h: JoinHandle<()>, name: &str, deadline: Duration, leaked: &AtomicU32) -> bool {
    let t0 = Instant::now();
    while t0.elapsed() < deadline {
        if h.is_finished() {
            let _ = h.join();
            return true;
        }
        std::thread::sleep(Duration::from_millis(15));
    }
    leaked.fetch_add(1, Ordering::Relaxed);
    log::error!("bounded_join: thread '{name}' outlived {deadline:?} — detached (counted)");
    false
}

/// `read_exact` that survives SO_RCVTIMEO: preserves partial progress across
/// WouldBlock/TimedOut (a naive read_exact would DESYNC the frame stream by
/// discarding half-read frames), re-checking `cancel` at every timeout.
/// Ok(true) = buffer filled · Ok(false) = cancelled · Err = real error/EOF.
pub fn read_fully<R: Read>(r: &mut R, buf: &mut [u8], cancel: &AtomicBool) -> io::Result<bool> {
    let mut done = 0;
    while done < buf.len() {
        if cancel.load(Ordering::Relaxed) {
            return Ok(false);
        }
        match r.read(&mut buf[done..]) {
            Ok(0) => return Err(io::Error::new(io::ErrorKind::UnexpectedEof, "peer closed")),
            Ok(n) => done += n,
            Err(e)
                if matches!(
                    e.kind(),
                    io::ErrorKind::WouldBlock
                        | io::ErrorKind::TimedOut
                        | io::ErrorKind::Interrupted
                ) =>
            {
                continue;
            }
            Err(e) => return Err(e),
        }
    }
    Ok(true)
}

// ── Pure decision tables (the watchdog/backoff brain, table-tested) ──────────

pub const QUIET_STALLED_MS: u64 = 3_000;
pub const QUIET_DEAD_MS: u64 = 10_000;

/// Decoded media accepted by the real remote reader, before audio/render dispatch.
#[derive(Debug)]
pub enum RemoteMedia {
    Audio(Vec<f32>),
    Geometry {
        points: Vec<[f32; 2]>,
        aspect: f32,
        intensity: f32,
    },
}

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct MediaStatus {
    pub received: bool,
    pub live: bool,
}

/// One native session's receipts. Retired sessions cannot regain media authority.
pub struct SessionMedia {
    last_ms: AtomicU64,
    retired: AtomicBool,
}

impl Default for SessionMedia {
    fn default() -> Self {
        Self {
            last_ms: AtomicU64::new(u64::MAX),
            retired: AtomicBool::new(false),
        }
    }
}

impl SessionMedia {
    pub fn retire(&self) {
        self.retired.store(true, Ordering::SeqCst);
    }

    pub fn status(&self, now_ms: u64) -> MediaStatus {
        let last_ms = self.last_ms.load(Ordering::Acquire);
        if self.retired.load(Ordering::SeqCst) || last_ms == u64::MAX {
            return MediaStatus::default();
        }
        MediaStatus {
            received: true,
            live: now_ms.saturating_sub(last_ms) <= QUIET_STALLED_MS,
        }
    }

    pub fn accept(&self, tag: u8, payload: &[u8], now_ms: u64) -> Option<RemoteMedia> {
        if self.retired.load(Ordering::SeqCst) {
            return None;
        }
        let media = match tag {
            // Protocol v2 carries nonempty s16le stereo, not partial channel frames.
            b'A' if !payload.is_empty() && payload.len().is_multiple_of(4) => RemoteMedia::Audio(
                payload
                    .chunks_exact(2)
                    .map(|c| i16::from_le_bytes([c[0], c[1]]) as f32 / 32768.0)
                    .collect(),
            ),
            b'G' => {
                let v: serde_json::Value = serde_json::from_slice(payload).ok()?;
                let (tw, th) = match v.get("trace_size") {
                    None => (1.0, 1.0),
                    Some(size) => {
                        let size = size.as_array()?;
                        if size.len() != 2 {
                            return None;
                        }
                        (size[0].as_f64()?, size[1].as_f64()?)
                    }
                };
                if !tw.is_finite() || !th.is_finite() || tw <= 0.0 || th <= 0.0 {
                    return None;
                }
                let peak = match v.get("peak") {
                    None => 0.6,
                    Some(peak) => peak.as_f64()?,
                } as f32;
                let aspect = (tw / th.max(1.0)) as f32;
                if !peak.is_finite() || !aspect.is_finite() {
                    return None;
                }
                let line = v.get("polyline")?.as_array()?;
                if line.len() < 2 {
                    return None;
                }
                let mut points = Vec::with_capacity(line.len());
                for point in line {
                    let point = point.as_array()?;
                    let x = (point.first()?.as_f64()? / tw.max(1.0)) as f32;
                    let y = (point.get(1)?.as_f64()? / th.max(1.0)) as f32;
                    if !x.is_finite() || !y.is_finite() {
                        return None;
                    }
                    points.push([x, y]);
                }
                RemoteMedia::Geometry {
                    points,
                    aspect,
                    intensity: (0.8 * peak.clamp(0.2, 1.0)).max(0.15),
                }
            }
            _ => return None,
        };
        self.last_ms.store(now_ms, Ordering::Release);
        if self.retired.load(Ordering::SeqCst) {
            return None;
        }
        Some(media)
    }
}

/// Reconnect ladder: 1 → 2 → 4 → 8 → 15 seconds. A healthy session resets it to 1.
pub fn next_backoff(prev: u64) -> u64 {
    (prev.max(1) * 2).min(15)
}

#[derive(Debug, PartialEq, Eq)]
pub enum WatchdogAction {
    Ok,
    MarkStalled,
    MarkStreaming,
    Dead,
}

/// Socket silence ends a session. Only fresh accepted media recovers media flow.
pub fn watchdog_action(
    quiet_ms: u64,
    media: MediaStatus,
    streaming: bool,
    stalled: bool,
) -> WatchdogAction {
    if quiet_ms > QUIET_DEAD_MS {
        WatchdogAction::Dead
    } else if streaming && (quiet_ms > QUIET_STALLED_MS || (media.received && !media.live)) {
        WatchdogAction::MarkStalled
    } else if quiet_ms <= QUIET_STALLED_MS && stalled && media.live {
        WatchdogAction::MarkStreaming
    } else {
        WatchdogAction::Ok
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::Arc;
    use std::sync::atomic::AtomicUsize;
    use std::sync::mpsc::sync_channel;

    /// A Write sink that records everything and can fail from the Nth write on.
    struct MockSink {
        data: Vec<u8>,
        writes: usize,
        fail_from: Option<usize>,
    }
    impl MockSink {
        fn new(fail_from: Option<usize>) -> Self {
            Self {
                data: Vec::new(),
                writes: 0,
                fail_from,
            }
        }
    }
    impl Write for MockSink {
        fn write(&mut self, buf: &[u8]) -> io::Result<usize> {
            self.writes += 1;
            if let Some(n) = self.fail_from
                && self.writes >= n
            {
                return Err(io::Error::new(io::ErrorKind::TimedOut, "blackhole"));
            }
            self.data.extend_from_slice(buf);
            Ok(buf.len())
        }
        fn flush(&mut self) -> io::Result<()> {
            Ok(())
        }
    }

    fn tags(data: &[u8]) -> Vec<u8> {
        // Walk [tag][len][payload] and collect the tags.
        let mut out = Vec::new();
        let mut i = 0;
        while i + 5 <= data.len() {
            out.push(data[i]);
            let len =
                u32::from_be_bytes([data[i + 1], data[i + 2], data[i + 3], data[i + 4]]) as usize;
            i += 5 + len;
        }
        out
    }

    #[test]
    fn frame_layout_roundtrips() {
        let f = encode_frame(b'T', b"{\"cmd\":\"next\"}");
        assert_eq!(f[0], b'T');
        assert_eq!(u32::from_be_bytes([f[1], f[2], f[3], f[4]]) as usize, 14);
        assert_eq!(&f[5..], b"{\"cmd\":\"next\"}");
    }

    #[test]
    fn writer_sends_hello_first_then_frames_in_order() {
        let (tx, rx) = sync_channel(8);
        tx.send(WriterCmd::Frame(encode_frame(b'T', b"1"))).unwrap();
        tx.send(WriterCmd::Frame(encode_frame(b'Q', b""))).unwrap();
        drop(tx); // writer drains then exits on Disconnected
        let cancel = AtomicBool::new(false);
        let dirty = AtomicBool::new(true);
        let mut sink = MockSink::new(None);
        run_writer(
            &mut sink,
            rx,
            WriterControl {
                cancel: &cancel,
                hello_dirty: &dirty,
                ping_period: Duration::from_secs(60),
            },
            || encode_frame(b'H', b"{}"),
            || encode_frame(b'K', b"{}"),
            |e| panic!("unexpected fatal: {e}"),
        );
        assert_eq!(tags(&sink.data), vec![b'H', b'T', b'Q']);
        assert!(!dirty.load(Ordering::Relaxed));
    }

    #[test]
    fn writer_coalesces_hello_and_never_loses_it() {
        let (tx, rx) = sync_channel(8);
        let cancel = AtomicBool::new(false);
        let dirty = AtomicBool::new(true);
        // Two wake markers + the dirty flag = exactly one extra H (after the
        // mandatory first), not three.
        tx.send(WriterCmd::Hello).unwrap();
        tx.send(WriterCmd::Hello).unwrap();
        drop(tx);
        let mut sink = MockSink::new(None);
        run_writer(
            &mut sink,
            rx,
            WriterControl {
                cancel: &cancel,
                hello_dirty: &dirty,
                ping_period: Duration::from_secs(60),
            },
            || encode_frame(b'H', b"{}"),
            || encode_frame(b'K', b"{}"),
            |e| panic!("unexpected fatal: {e}"),
        );
        let t = tags(&sink.data);
        assert_eq!(t.first(), Some(&b'H'));
        assert_eq!(
            t.iter().filter(|&&x| x == b'H').count(),
            1,
            "dirty cleared before markers drained"
        );
    }

    #[test]
    fn writer_pings_on_cadence_without_queue_traffic() {
        let (tx, rx) = sync_channel::<WriterCmd>(1);
        let cancel = Arc::new(AtomicBool::new(false));
        let dirty = AtomicBool::new(false);
        let c2 = cancel.clone();
        std::thread::spawn(move || {
            std::thread::sleep(Duration::from_millis(120));
            c2.store(true, Ordering::Relaxed);
            drop(tx);
        });
        let mut sink = MockSink::new(None);
        run_writer(
            &mut sink,
            rx,
            WriterControl {
                cancel: &cancel,
                hello_dirty: &dirty,
                ping_period: Duration::from_millis(20),
            },
            || encode_frame(b'H', b"{}"),
            || encode_frame(b'K', b"{}"),
            |e| panic!("unexpected fatal: {e}"),
        );
        let pings = tags(&sink.data).iter().filter(|&&t| t == b'K').count();
        assert!(
            pings >= 3,
            "expected >=3 K in 120 ms at 20 ms cadence, got {pings}"
        );
    }

    #[test]
    fn writer_write_failure_reports_fatal_once_and_exits() {
        let (_tx, rx) = sync_channel::<WriterCmd>(1);
        let cancel = AtomicBool::new(false);
        let dirty = AtomicBool::new(false);
        let fired = AtomicUsize::new(0);
        let mut sink = MockSink::new(Some(1)); // first write (the hello) fails
        run_writer(
            &mut sink,
            rx,
            WriterControl {
                cancel: &cancel,
                hello_dirty: &dirty,
                ping_period: Duration::from_secs(60),
            },
            || encode_frame(b'H', b"{}"),
            || encode_frame(b'K', b"{}"),
            |e| {
                fired.fetch_add(1, Ordering::Relaxed);
                assert!(e.contains("hello"), "wrong error: {e}");
            },
        );
        assert_eq!(fired.load(Ordering::Relaxed), 1);
    }

    #[test]
    fn bounded_join_joins_fast_threads_and_detaches_stuck_ones() {
        let leaked = AtomicU32::new(0);
        let quick = std::thread::spawn(|| {});
        assert!(bounded_join(
            quick,
            "quick",
            Duration::from_secs(1),
            &leaked
        ));
        assert_eq!(leaked.load(Ordering::Relaxed), 0);
        let stuck = std::thread::spawn(|| std::thread::sleep(Duration::from_millis(300)));
        assert!(!bounded_join(
            stuck,
            "stuck",
            Duration::from_millis(40),
            &leaked
        ));
        assert_eq!(leaked.load(Ordering::Relaxed), 1);
    }

    /// A Read that yields data in dribbles with fake SO_RCVTIMEO timeouts between.
    struct DribbleRead {
        chunks: Vec<Vec<u8>>,
        timeouts_between: bool,
        gave_timeout: bool,
    }
    impl Read for DribbleRead {
        fn read(&mut self, buf: &mut [u8]) -> io::Result<usize> {
            if self.timeouts_between && !self.gave_timeout {
                self.gave_timeout = true;
                return Err(io::Error::new(io::ErrorKind::WouldBlock, "rcvtimeo"));
            }
            self.gave_timeout = false;
            match self.chunks.first_mut() {
                None => Ok(0),
                Some(c) => {
                    let n = c.len().min(buf.len()).min(3); // dribble ≤3 bytes
                    buf[..n].copy_from_slice(&c[..n]);
                    c.drain(..n);
                    if c.is_empty() {
                        self.chunks.remove(0);
                    }
                    Ok(n)
                }
            }
        }
    }

    #[test]
    fn read_fully_survives_timeouts_without_desync() {
        let mut r = DribbleRead {
            chunks: vec![b"abcdefghij".to_vec()],
            timeouts_between: true,
            gave_timeout: false,
        };
        let cancel = AtomicBool::new(false);
        let mut buf = [0u8; 10];
        assert!(read_fully(&mut r, &mut buf, &cancel).unwrap());
        assert_eq!(&buf, b"abcdefghij");
    }

    #[test]
    fn read_fully_observes_cancel_and_reports_eof() {
        let mut r = DribbleRead {
            chunks: vec![],
            timeouts_between: true,
            gave_timeout: false,
        };
        let cancel = AtomicBool::new(true);
        let mut buf = [0u8; 4];
        assert!(!read_fully(&mut r, &mut buf, &cancel).unwrap());
        let cancel = AtomicBool::new(false);
        let err = read_fully(&mut r, &mut buf, &cancel).unwrap_err();
        assert_eq!(err.kind(), io::ErrorKind::UnexpectedEof);
    }

    #[test]
    fn shutdown_wakes_a_blocked_read() {
        // Pins the teardown assumption: shutdown(Both) on a clone unblocks a
        // blocked recv with EOF on this kernel (Android shares the semantics).
        use std::net::{TcpListener, TcpStream};
        let l = TcpListener::bind("127.0.0.1:0").unwrap();
        let addr = l.local_addr().unwrap();
        let client = TcpStream::connect(addr).unwrap();
        let (server, _) = l.accept().unwrap();
        let mut reader = client.try_clone().unwrap();
        let h = std::thread::spawn(move || {
            let mut b = [0u8; 8];
            let cancel = AtomicBool::new(false);
            read_fully(&mut reader, &mut b, &cancel)
        });
        std::thread::sleep(Duration::from_millis(60)); // let it block
        client.shutdown(std::net::Shutdown::Both).unwrap();
        let res = h.join().unwrap();
        assert!(
            res.is_err(),
            "blocked read must wake with EOF/err after shutdown"
        );
        drop(server);
    }

    #[test]
    fn remote_media_prior_receipt_then_reconnect_control_only_not_live() {
        let prior = SessionMedia::default();
        assert!(prior.accept(b'A', &[0; 4], 0).is_some());
        assert!(prior.status(0).live);
        prior.retire();

        // Automatic reconnect and retarget both construct a fresh SessionShared.
        let current = SessionMedia::default();
        for now in [0, 1_000, 4_000, 10_001, 60_001] {
            for tag in [b'W', b'K', b'M'] {
                assert!(current.accept(tag, br#"{"rms":0.0}"#, now).is_none());
                assert_eq!(current.status(now), MediaStatus::default());
                assert_eq!(
                    watchdog_action(0, current.status(now), true, false),
                    WatchdogAction::Ok
                );
            }
        }
        assert_eq!(prior.status(0), MediaStatus::default());
    }

    #[test]
    fn remote_media_stops_while_heartbeats_continue_and_recovers() {
        let media = SessionMedia::default();
        assert!(media.accept(b'A', &[0; 4], 100).is_some());
        assert!(media.status(3_100).live);
        for now in [3_101, 5_000, 15_000] {
            for tag in [b'W', b'K', b'M'] {
                assert!(media.accept(tag, b"{}", now).is_none());
            }
            let status = media.status(now);
            assert_eq!(
                status,
                MediaStatus {
                    received: true,
                    live: false
                }
            );
            assert_eq!(
                watchdog_action(0, status, true, false),
                WatchdogAction::MarkStalled
            );
            assert_eq!(watchdog_action(0, status, false, true), WatchdogAction::Ok);
        }
        assert!(
            media
                .accept(b'G', br#"{"polyline":[[0,0],[0,0]]}"#, 15_001)
                .is_some()
        );
        assert!(media.status(15_001).live);
        assert_eq!(
            watchdog_action(0, media.status(15_001), false, true),
            WatchdogAction::MarkStreaming
        );
        assert!(!media.status(18_002).live);
        assert!(media.accept(b'A', &[0; 4], 18_003).is_some());
        assert_eq!(
            watchdog_action(0, media.status(18_003), false, true),
            WatchdogAction::MarkStreaming
        );
    }

    #[test]
    fn remote_media_silent_stereo_audio_is_valid_at_monotonic_zero() {
        let media = SessionMedia::default();
        let Some(RemoteMedia::Audio(samples)) = media.accept(b'A', &[0; 1920], 0) else {
            panic!("valid silent A frame was rejected");
        };
        assert_eq!(samples, vec![0.0; 960]);
        assert_eq!(
            media.status(0),
            MediaStatus {
                received: true,
                live: true
            }
        );
        assert!(media.status(QUIET_STALLED_MS).live);
        assert!(!media.status(QUIET_STALLED_MS + 1).live);
        let Some(RemoteMedia::Audio(samples)) = media.accept(b'A', &[0, 128, 255, 127], 4_000)
        else {
            panic!("valid full-scale stereo A frame was rejected");
        };
        assert_eq!(samples, vec![-1.0, 32767.0 / 32768.0]);
        assert!(media.status(4_000).live);
    }

    #[test]
    fn remote_media_valid_geometry_only_preserves_renderer_values() {
        let media = SessionMedia::default();
        let frame = br#"{"trace_size":[200,100],"peak":0.5,"polyline":[[0,0],[100,50]]}"#;
        let Some(RemoteMedia::Geometry {
            points,
            aspect,
            intensity,
        }) = media.accept(b'G', frame, 25)
        else {
            panic!("valid geometry-only G frame was rejected");
        };
        assert_eq!(points, vec![[0.0, 0.0], [0.5, 0.5]]);
        assert_eq!(aspect, 2.0);
        assert_eq!(intensity, 0.4);
        assert!(media.status(25).live);
        // Coincident zero points and peak zero remain real geometry, not amplitude tests.
        assert!(
            media
                .accept(b'G', br#"{"peak":0,"polyline":[[0,0],[0,0]]}"#, 4_000)
                .is_some()
        );
        assert!(media.status(4_000).live);
    }

    #[test]
    fn remote_media_malformed_audio_neither_acquires_nor_renews() {
        let media = SessionMedia::default();
        for len in [0, 1, 2, 3, 5, 6, 7] {
            assert!(media.accept(b'A', &vec![0; len], 0).is_none());
            assert_eq!(media.status(0), MediaStatus::default());
        }
        assert!(media.accept(b'A', &[0; 4], 0).is_some());
        for len in [0, 1, 2, 3, 5, 6, 7] {
            assert!(media.accept(b'A', &vec![0; len], 4_000).is_none());
            assert_eq!(
                media.status(4_000),
                MediaStatus {
                    received: true,
                    live: false
                }
            );
        }
    }

    #[test]
    fn remote_media_malformed_geometry_neither_acquires_nor_renews() {
        let invalid: &[&[u8]] = &[
            b"not json",
            b"{}",
            br#"{"polyline":[]}"#,
            br#"{"polyline":[[0,0]]}"#,
            br#"{"polyline":[[0,0],[1,1],["bad",0]]}"#,
            br#"{"polyline":[[0,0],[1]]}"#,
            br#"{"polyline":[[0,0],[1e300,0]]}"#,
            br#"{"trace_size":[0,100],"polyline":[[0,0],[1,1]]}"#,
            br#"{"trace_size":[1],"polyline":[[0,0],[1,1]]}"#,
            br#"{"trace_size":[1e300,1],"polyline":[[0,0],[1,1]]}"#,
            br#"{"peak":"bad","polyline":[[0,0],[1,1]]}"#,
            br#"{"peak":1e300,"polyline":[[0,0],[1,1]]}"#,
        ];
        let media = SessionMedia::default();
        for payload in invalid {
            assert!(media.accept(b'G', payload, 0).is_none());
            assert_eq!(media.status(0), MediaStatus::default());
        }
        assert!(
            media
                .accept(b'G', br#"{"polyline":[[0,0],[1,1]]}"#, 0)
                .is_some()
        );
        for payload in invalid {
            assert!(media.accept(b'G', payload, 4_000).is_none());
            assert_eq!(
                media.status(4_000),
                MediaStatus {
                    received: true,
                    live: false
                }
            );
        }
    }

    #[test]
    fn remote_media_disconnect_retires_receipt_and_rejects_late_frames() {
        let media = SessionMedia::default();
        assert!(media.accept(b'A', &[0; 4], 5).is_some());
        assert!(media.status(5).live);
        // SessionShared::trip uses this before FIN on disconnect and every failure path.
        media.retire();
        media.retire();
        for (tag, payload) in [
            (b'A', &[0; 4][..]),
            (b'G', &br#"{"polyline":[[0,0],[1,1]]}"#[..]),
        ] {
            assert!(media.accept(tag, payload, 6).is_none());
            assert_eq!(media.status(6), MediaStatus::default());
        }
    }

    #[test]
    fn remote_media_socket_death_remains_separate_from_media_stall() {
        let media = SessionMedia::default();
        assert!(media.accept(b'A', &[0; 4], 0).is_some());
        let stale = media.status(QUIET_DEAD_MS + 1);
        assert_eq!(
            watchdog_action(0, stale, true, false),
            WatchdogAction::MarkStalled
        );
        assert_eq!(
            watchdog_action(QUIET_DEAD_MS, stale, false, true),
            WatchdogAction::Ok
        );
        assert_eq!(
            watchdog_action(QUIET_DEAD_MS + 1, stale, false, true),
            WatchdogAction::Dead
        );
        assert_eq!(
            watchdog_action(QUIET_DEAD_MS + 1, MediaStatus::default(), false, false),
            WatchdogAction::Dead
        );
    }

    #[test]
    fn backoff_ladder_and_watchdog_tables() {
        assert_eq!(next_backoff(1), 2);
        assert_eq!(next_backoff(2), 4);
        assert_eq!(next_backoff(4), 8);
        assert_eq!(next_backoff(8), 15);
        assert_eq!(next_backoff(15), 15);
        assert_eq!(next_backoff(0), 2); // degenerate input still climbs

        use WatchdogAction::*;
        let no_media = MediaStatus::default();
        let live = MediaStatus {
            received: true,
            live: true,
        };
        assert_eq!(watchdog_action(0, no_media, false, false), Ok);
        assert_eq!(watchdog_action(3_500, no_media, true, false), MarkStalled);
        assert_eq!(watchdog_action(3_500, no_media, false, false), Ok); // pre-welcome quiet
        assert_eq!(watchdog_action(1_000, live, false, true), MarkStreaming);
        assert_eq!(watchdog_action(1_000, no_media, false, true), Ok);
        assert_eq!(watchdog_action(10_001, live, true, false), Dead);
        assert_eq!(watchdog_action(10_001, no_media, false, false), Dead);
    }
}
