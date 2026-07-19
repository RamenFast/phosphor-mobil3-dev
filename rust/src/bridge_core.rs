//! Host-testable core of the phone↔relay bridge: the frame codec, the dedicated
//! writer loop, bounded thread joins, timeout-surviving reads, and the pure
//! decision tables (backoff, watchdog). No Android, no oboe, no JNI — plain
//! `cargo test` exercises everything here; the android-gated `remote` consumes it.

use std::io::{self, Read, Write};
use std::sync::atomic::{AtomicBool, AtomicU32, Ordering};
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

/// The dedicated writer loop (audit finding 2): the ONLY place session bytes are
/// written. H always goes first (the relay's first client frame); K self-
/// generates on `ping_period` and is never queued, so a full command queue can
/// never starve liveness; any write error/timeout reports through `on_fatal`
/// exactly once and the loop exits.
pub fn run_writer<W: Write>(
    mut sink: W,
    rx: Receiver<WriterCmd>,
    cancel: &AtomicBool,
    hello_dirty: &AtomicBool,
    mut build_hello: impl FnMut() -> Vec<u8>,
    ping_period: Duration,
    mut build_ping: impl FnMut() -> Vec<u8>,
    on_fatal: impl FnOnce(String),
) {
    let tick = ping_period
        .min(Duration::from_millis(250))
        .max(Duration::from_millis(5));
    let result = (|| -> Result<(), String> {
        let put = |sink: &mut W, buf: &[u8], what: &str| -> Result<(), String> {
            sink.write_all(buf)
                .and_then(|_| sink.flush())
                .map_err(|e| format!("{what} write: {e}"))
        };
        put(&mut sink, &build_hello(), "hello")?;
        hello_dirty.store(false, Ordering::Relaxed);
        let mut next_ping = Instant::now() + ping_period;
        loop {
            if cancel.load(Ordering::Relaxed) {
                return Ok(());
            }
            if hello_dirty.swap(false, Ordering::Relaxed) {
                put(&mut sink, &build_hello(), "hello")?;
            }
            if Instant::now() >= next_ping {
                put(&mut sink, &build_ping(), "ping")?;
                next_ping = Instant::now() + ping_period;
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
pub fn bounded_join(
    h: JoinHandle<()>,
    name: &str,
    deadline: Duration,
    leaked: &AtomicU32,
) -> bool {
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
pub fn read_fully<R: Read>(
    r: &mut R,
    buf: &mut [u8],
    cancel: &AtomicBool,
) -> io::Result<bool> {
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
                    io::ErrorKind::WouldBlock | io::ErrorKind::TimedOut | io::ErrorKind::Interrupted
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

/// Reconnect ladder: 1 → 2 → 4 → 8 → 15 (cap). A Healthy session end resets to 1
/// at the call site (audit finding 11's law).
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

/// The receive-silence state machine, extracted verbatim from the session
/// watchdog so the thresholds live in one tested place.
pub fn watchdog_action(quiet_ms: u64, streaming: bool, stalled: bool) -> WatchdogAction {
    if quiet_ms > QUIET_DEAD_MS {
        WatchdogAction::Dead
    } else if quiet_ms > QUIET_STALLED_MS && streaming {
        WatchdogAction::MarkStalled
    } else if quiet_ms <= QUIET_STALLED_MS && stalled {
        WatchdogAction::MarkStreaming
    } else {
        WatchdogAction::Ok
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::AtomicUsize;
    use std::sync::mpsc::sync_channel;
    use std::sync::Arc;

    /// A Write sink that records everything and can fail from the Nth write on.
    struct MockSink {
        data: Vec<u8>,
        writes: usize,
        fail_from: Option<usize>,
    }
    impl MockSink {
        fn new(fail_from: Option<usize>) -> Self {
            Self { data: Vec::new(), writes: 0, fail_from }
        }
    }
    impl Write for MockSink {
        fn write(&mut self, buf: &[u8]) -> io::Result<usize> {
            self.writes += 1;
            if let Some(n) = self.fail_from {
                if self.writes >= n {
                    return Err(io::Error::new(io::ErrorKind::TimedOut, "blackhole"));
                }
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
            &cancel,
            &dirty,
            || encode_frame(b'H', b"{}"),
            Duration::from_secs(60),
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
            &cancel,
            &dirty,
            || encode_frame(b'H', b"{}"),
            Duration::from_secs(60),
            || encode_frame(b'K', b"{}"),
            |e| panic!("unexpected fatal: {e}"),
        );
        let t = tags(&sink.data);
        assert_eq!(t.first(), Some(&b'H'));
        assert_eq!(t.iter().filter(|&&x| x == b'H').count(), 1, "dirty cleared before markers drained");
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
            &cancel,
            &dirty,
            || encode_frame(b'H', b"{}"),
            Duration::from_millis(20),
            || encode_frame(b'K', b"{}"),
            |e| panic!("unexpected fatal: {e}"),
        );
        let pings = tags(&sink.data).iter().filter(|&&t| t == b'K').count();
        assert!(pings >= 3, "expected >=3 K in 120 ms at 20 ms cadence, got {pings}");
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
            &cancel,
            &dirty,
            || encode_frame(b'H', b"{}"),
            Duration::from_secs(60),
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
        assert!(bounded_join(quick, "quick", Duration::from_secs(1), &leaked));
        assert_eq!(leaked.load(Ordering::Relaxed), 0);
        let stuck = std::thread::spawn(|| std::thread::sleep(Duration::from_millis(300)));
        assert!(!bounded_join(stuck, "stuck", Duration::from_millis(40), &leaked));
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
        let mut r = DribbleRead { chunks: vec![], timeouts_between: true, gave_timeout: false };
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
        assert!(res.is_err(), "blocked read must wake with EOF/err after shutdown");
        drop(server);
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
        assert_eq!(watchdog_action(0, false, false), Ok);
        assert_eq!(watchdog_action(3_500, true, false), MarkStalled);
        assert_eq!(watchdog_action(3_500, false, false), Ok); // pre-welcome quiet
        assert_eq!(watchdog_action(1_000, false, true), MarkStreaming);
        assert_eq!(watchdog_action(10_001, true, false), Dead);
        assert_eq!(watchdog_action(10_001, false, false), Dead);
    }
}
