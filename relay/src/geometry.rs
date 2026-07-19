//! Geometry stream: run `phosphor tap`, forward its per-frame beam JSON as G
//! frames, rate-capped to the client's fps (latest-wins — an older frame in the
//! window is dropped for the newest). If phosphor isn't up, emit an E with a fix
//! and retry every 5 s while the toggle stays on.

use std::io::{BufRead, BufReader};
use std::process::{Child, Command, Stdio};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::mpsc::SyncSender;
use std::sync::{Arc, Mutex};
use std::thread::{self, JoinHandle};
use std::time::Duration;

use crate::proto;
use crate::session::Counters;
use crate::util;

pub struct Geometry {
    on: Arc<AtomicBool>,
    child: Arc<Mutex<Option<Child>>>,
    supervisor: Option<JoinHandle<()>>,
}

/// RAII (audit finding 8): any drop path — including a panic unwind — kills
/// phosphor-tap and joins the supervisor.
impl Drop for Geometry {
    fn drop(&mut self) {
        self.on.store(false, Ordering::SeqCst);
        if let Some(mut c) = self.child.lock().unwrap().take() {
            let _ = c.kill();
            let _ = c.wait(); // reap — no zombie phosphor-tap
        }
        if let Some(h) = self.supervisor.take() {
            let _ = h.join();
        }
    }
}

/// Sleep up to `ms`, waking early if the toggle went off.
fn nap(on: &AtomicBool, ms: u64) {
    let mut left = ms;
    while left > 0 && on.load(Ordering::SeqCst) {
        let step = left.min(100);
        thread::sleep(Duration::from_millis(step));
        left -= step;
    }
}

pub fn start(
    fps: u32,
    writer: SyncSender<Vec<u8>>,
    counters: Arc<Counters>,
) -> Geometry {
    let window = (1000 / fps.clamp(1, 240)).max(1) as u64;
    let on = Arc::new(AtomicBool::new(true));
    let child_slot: Arc<Mutex<Option<Child>>> = Arc::new(Mutex::new(None));

    let (on_t, slot_t) = (on.clone(), child_slot.clone());
    let supervisor = thread::spawn(move || {
        while on_t.load(Ordering::SeqCst) {
            let spawned = Command::new("phosphor")
                .arg("tap")
                .stdout(Stdio::piped())
                .stderr(Stdio::null())
                .spawn();
            let mut child = match spawned {
                Ok(c) => c,
                Err(_) => {
                    emit_down(&writer);
                    nap(&on_t, 5000);
                    continue;
                }
            };
            let stdout = child.stdout.take().expect("piped stdout");
            *slot_t.lock().unwrap() = Some(child);

            let latest: Arc<Mutex<Option<Vec<u8>>>> = Arc::new(Mutex::new(None));
            let done = Arc::new(AtomicBool::new(false));
            let (latest_r, done_r) = (latest.clone(), done.clone());
            let reader = thread::spawn(move || {
                let mut lines = BufReader::new(stdout).lines();
                while let Some(Ok(line)) = lines.next() {
                    let is_frame = serde_json::from_str::<serde_json::Value>(&line)
                        .ok()
                        .and_then(|v| v.get("event").and_then(|e| e.as_str()).map(|s| s == "frame"))
                        .unwrap_or(false);
                    if is_frame {
                        *latest_r.lock().unwrap() = Some(line.into_bytes());
                    }
                }
                done_r.store(true, Ordering::SeqCst);
            });

            // emitter: latest-wins at the fps window
            while on_t.load(Ordering::SeqCst) && !done.load(Ordering::SeqCst) {
                thread::sleep(Duration::from_millis(window));
                if let Some(line) = latest.lock().unwrap().take() {
                    if writer.try_send(proto::encode_frame(proto::G, &line)).is_ok() {
                        counters.tx_g.fetch_add(1, Ordering::Relaxed);
                    }
                }
            }

            if let Some(mut c) = slot_t.lock().unwrap().take() {
                let _ = c.kill();
                let _ = c.wait(); // reap before a possible respawn
            }
            let _ = reader.join();

            // child died on its own while still toggled on → report + retry
            if on_t.load(Ordering::SeqCst) {
                emit_down(&writer);
                nap(&on_t, 5000);
            }
        }
    });

    Geometry { on, child: child_slot, supervisor: Some(supervisor) }
}

fn emit_down(writer: &SyncSender<Vec<u8>>) {
    let body = proto::error_frame(
        "phosphor tap is unavailable",
        &format!("start phosphor on {}: phosphor --background", util::hostname()),
        serde_json::json!({}),
    );
    let _ = writer.try_send(proto::encode_frame(proto::E, &body));
}
