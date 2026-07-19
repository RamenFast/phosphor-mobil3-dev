# phosphor-mobil3 bridge concurrency/lifecycle audit

Scope: `rust/src/remote.rs`, `relay/src/session.rs`, their direct callers/helpers, and `docs/BRIDGE.md`. Repo files were kept read-only. Baseline verification used an out-of-tree Cargo target: `cargo test --locked --manifest-path relay/Cargo.toml` passed all 7 tests; Clippy reported style warnings only. Those tests do not cover the races below.

## Ranked findings

### 1. A retired manager can publish a socket after a new generation starts, then either session can close the other's socket

- **Severity:** high
- **Location:** `rust/src/remote.rs:216-230`, `rust/src/remote.rs:233-240`, `rust/src/remote.rs:413-431`, `rust/src/remote.rs:520-570`
- **Failure scenario:** Generation N is blocked in the four-second `connect_timeout`. `disconnect()` bumps the generation, and a new `connect()` starts generation N+2. The old connect then succeeds. `run_session` does not re-check its generation before `*l.writer.lock() = Some(stream)`, so N can overwrite N+2's writer, send H on the wrong session, open Oboe, and mark the shared deck active. Worse, `teardown_session` blindly `take()`s the one global writer; whichever manager tears down first can shut down the newer manager's live socket. This produces reconnect flapping and can briefly start stale audio/scope state. A reader that passed its loop guard before blocking can also finish and publish one buffered A/G/M/R frame after the bump because there is no post-read generation check (`rust/src/remote.rs:583-607`).
- **Minimal fix sketch:** Make the writer/session resources generation-owned, e.g. store `(generation, TcpStream)` and only replace/take when the generation matches. Re-check cancellation immediately after every blocking operation and before every shared publication. Prefer a per-session RAII object containing its socket, Oboe stream, cancellation token, and join handles; retire/join it before allowing a replacement session to publish.

### 2. A blocking phone write holds the only writer mutex, freezes the watchdog, and can ANR every main-thread JNI caller including disconnect

- **Severity:** high
- **Location:** `rust/src/remote.rs:185-200`, `rust/src/remote.rs:233-239`, `rust/src/remote.rs:248-305`, `rust/src/remote.rs:520-559`; JNI entry points at `rust/src/jni_glue.rs:221-262` and `rust/src/jni_glue.rs:491-591`
- **Failure scenario:** A Tailscale black hole or peer that stops reading eventually fills the TCP send buffer. The manager blocks in the 2 s K ping while holding `Link.writer`, so it never reaches its 3 s/10 s receive-silence checks. `disconnect()` cannot acquire the mutex to call `shutdown()`. The same lock is taken synchronously by transport, seek, stream toggles, source selection, browse/play, and art requests. These are called on Android's main/player looper (`RemotePlayer.kt:191-225`, current `PlaybackService.kt:211-226,237-298`, current `MainActivity.kt:302` and `ui/Sheets.kt:473-532`), so recovery UI, media buttons, and service teardown can all block past the ANR threshold.
- **Minimal fix sketch:** Give the phone a relay-like dedicated writer thread with a bounded command queue and a short socket write timeout. JNI calls should enqueue or fail fast, never do socket I/O. Keep a separately accessible shutdown clone/handle so cancellation never waits for the writer mutex. Treat a write failure/timeout as a session-ending event.

### 3. The Oboe restart supervisor leaks on every session and has a late-install race that can resurrect a retired stream

- **Severity:** high
- **Location:** `rust/src/remote.rs:433-469`, `rust/src/remote.rs:563-570`
- **Failure scenario:** The supervisor closure owns both `restart_rx` and a clone of `restart_tx`. Therefore `restart_rx.recv()` can never return `Disconnected`; after ordinary teardown it remains blocked forever, retaining the audible ring, mute flag, slot, and a native thread. On a route-change event it checks the generation only before `open_output`. If disconnect/generation-bump happens while open/start is in progress, teardown can clear the slot, then the stale supervisor installs `new_out` afterward without a second check. Because the supervisor's self-owned sender keeps it alive, that stale stream can remain open indefinitely and may drain buffered audio from the retired session.
- **Minimal fix sketch:** Add an explicit supervisor cancellation message/token, use `recv_timeout` (or equivalent select) so cancellation is observed, and retain/join its handle during teardown. Re-check generation/cancellation after opening and immediately before installation; drop a newly opened stream if stale. Do not rely on channel disconnection while the receiver thread itself owns a sender.

### 4. Relay watchdog teardown is only queued; blocking handlers/subprocesses can prevent it indefinitely

- **Severity:** high
- **Location:** `relay/src/session.rs:245-325`, `relay/src/session.rs:495-505`, `relay/src/session.rs:525-565`; blocking callees at `relay/src/library.rs:88-105`, `relay/src/library.rs:151-179`, `relay/src/library.rs:188-224`, and `relay/src/player.rs:39-44,116-119,230-248`
- **Failure scenario:** The watchdog shuts the socket and sends `Ev::Watchdog`, but only the single control loop calls `st.teardown()`. That loop synchronously runs rclone listing/copy, ffprobe/ffmpeg metadata/art extraction, playerctl transport, and other external commands with no timeout/cancellation. If a Drive download or subprocess stalls while the phone Dozes or disconnects, the watchdog event sits in the queue and capture/ffmpeg/phosphor children remain alive. The phone reaches its 10 s timeout and reconnects, creating a second relay session while the old one and its children still exist. `poller.join()` can likewise wait forever on an unbounded playerctl/curl call after otherwise-normal teardown.
- **Minimal fix sketch:** Move slow commands to cancellable worker jobs and report completion through generation-tagged events. Keep child handles, apply hard per-command deadlines, and kill/reap them when the session token is cancelled. The control loop must remain able to process watchdog/disconnect immediately; joins should have a bounded/cancellable path.

### 5. The phone audio-writer can remain blocked forever after teardown because its ring is never closed and no worker is joined

- **Severity:** medium
- **Location:** `rust/src/remote.rs:472-492`, `rust/src/remote.rs:563-570`; blocking behavior is in sibling source `../phosphor/crates/phosphor-audio/src/playback.rs:70-91,123-131`
- **Failure scenario:** `audible.push_blocking()` waits while the roughly 100 ms ring is full and exits early only when `AudibleRing::close()` is called. Teardown drops the Oboe consumer but never closes the ring and discards the audio thread's join handle. If teardown catches the producer waiting for space (especially after a route failure), no callback remains to drain it, so that native thread and ring live forever. Repeated Tailscale drops/reconnects can accumulate these workers.
- **Minimal fix sketch:** Close (and optionally clear) the audible ring before dropping Oboe, drop the audio sender, retain the audio worker handle, and join it. Make the whole sequence part of idempotent session RAII teardown.

### 6. Untagged CaptureEof/FileEof events can kill or advance a replacement pump

- **Severity:** medium
- **Location:** `relay/src/session.rs:35-42`, `relay/src/session.rs:93-140`, `relay/src/session.rs:303-347`; event producers at `relay/src/capture.rs:205-228` and `relay/src/library.rs:285-330`
- **Failure scenario:** An old capture reaches EOF and observes `stopping == false` just before the control loop processes a source-change C frame. The source change stops/joins the old pump and starts the selected replacement; the already-queued untagged `CaptureEof` is then applied to the replacement, which is killed and replaced by the default monitor. The same race lets a stale `FileEof` from a pre-seek/pre-next pump advance the newly started file pump, causing a track skip.
- **Minimal fix sketch:** Give every capture/file pump a monotonically increasing ID and carry it in `Ev::CaptureEof(id)` / `Ev::FileEof(id)`. Ignore events whose ID is not the active pump's ID; bump the ID before stopping/replacing a pump.

### 7. A transient Oboe reopen failure permanently disables audio for an otherwise healthy network session

- **Severity:** medium
- **Location:** `rust/src/remote.rs:444-467`
- **Failure scenario:** Bluetooth/USB route loss emits one restart notification. Oboe has already closed the failed stream. If the replacement route is not ready at the supervisor's single immediate `open_output` attempt, the code only logs and goes back to `recv()`. There is no stream left to generate another disconnect notification, so the link continues receiving A/K and reports `streaming`, but playback stays silent until a full network reconnect.
- **Minimal fix sketch:** After a disconnect, retry reopen with a small bounded backoff while the session generation is current, or surface an explicit audio-output-failed state that triggers session restart. Coalesce duplicate restart signals and cancel retries during teardown.

### 8. Normal relay exits reap pumps, but panic unwinding has no cleanup guard and detaches the whole session graph

- **Severity:** medium
- **Location:** `relay/src/session.rs:75-85`, `relay/src/session.rs:376-382`, `relay/src/session.rs:406-565`, caller `relay/src/cli.rs:132-138`; wrappers at `relay/src/capture.rs:169-188`, `relay/src/geometry.rs:18-35`, and `relay/src/library.rs:239-258,345-362,508-510`
- **Failure scenario:** `serve_client` is spawned as a bare thread and cleanup is a tail block, not a guard. `SessionState`, `CapturePump`, `Geometry`, `FilePump`, and `FileSession` do not implement `Drop`. If any session handler unwinds (for example from a poisoned shared mutex), Rust drops `JoinHandle`s by detaching them and `Child` does not kill/reap the process. Capture/file/geometry children, writer/timer/poller threads, and channel clones can therefore survive the failed client thread. Relay release builds use normal unwind semantics.
- **Minimal fix sketch:** Make every child/worker wrapper RAII and idempotently stop/kill/wait in `Drop`; make `SessionState::Drop` stop its active resources. Wrap the per-client body in `catch_unwind` only to log/reason-code after the guard has cleaned up, not as a substitute for RAII.

### 9. Initial Oboe failure returns after publishing H/socket, leaving a half-session until later overwrite/relay timeout

- **Severity:** medium
- **Location:** `rust/src/remote.rs:426-439`
- **Failure scenario:** The client publishes the global writer and sends H before opening Oboe. If open/start fails, `run_session` returns directly without `teardown_session`. The relay has already processed H and may start `pw-record`, while the phone manager enters backoff with the TCP socket still stored/open and sends no K. A later retry overwrites the writer without an explicit shutdown; the relay retains the abandoned session/pump until its 8 s silence watchdog fires. Repeated audio-device failures create overlapping half-sessions.
- **Minimal fix sketch:** Do not publish/send H until all required local resources are ready, or install a scope guard immediately after TCP connect that always shuts down its own socket on every early return. Never replace a writer slot without shutting down the exact prior generation-owned stream.

### 10. The Oboe data callback performs allocation-capable and mutex-taking work on the real-time thread

- **Severity:** medium
- **Location:** `rust/src/remote.rs:122-145`; ring implementation `../phosphor/crates/phosphor-audio/src/playback.rs:51-104`
- **Failure scenario:** `scratch.resize()` can allocate when callback frame size grows, and `AudibleRing::pop_into()` locks a mutex also used by the blocking producer. Oboe explicitly treats the data callback as real-time and forbids allocations and mutex synchronization. Under route churn or CPU contention the callback can priority-invert behind the producer, causing underruns/pops; an allocation on the callback adds another unbounded latency source.
- **Minimal fix sketch:** Replace the mutex/condvar ring on this path with a fixed-capacity lock-free SPSC ring. Preallocate a maximum callback buffer before start (or write directly into `frames`) so the callback does no allocation, blocking, logging, or locking.

### 11. Reconnect backoff never resets after a healthy run

- **Severity:** low
- **Location:** `rust/src/remote.rs:369-401`, `rust/src/remote.rs:520-551`
- **Failure scenario:** `manager` resets backoff only for `SessionEnd::Healthy`, but `run_session` has no path returning that variant. Every peer close/10 s timeout returns `Failed`, even after hours in `ST_STREAMING`, so successive unrelated relay restarts increase recovery delay 1, 2, 4, 8, then 15 seconds permanently.
- **Minimal fix sketch:** Return a healthy-ended outcome after W/streaming (or return duration/`saw_w`) and reset the ladder before sleeping when the just-ended session had reached a healthy state.

### 12. Slow-client audio accounting is queue accounting, and `Disconnected` is mislabeled as a capacity drop

- **Severity:** low
- **Location:** `relay/src/capture.rs:205-220`, `relay/src/library.rs:285-316`, `relay/src/session.rs:25-31,420-435`
- **Failure scenario:** Under a full 256-frame channel, `try_send` does correctly count one dropped A frame per failed enqueue. But it matches all errors together, so a dropped writer receiver also increments `dropped_a` until teardown. Conversely `tx_a` increments when a frame is merely enqueued, even if the writer later times out before putting it on the wire. The 256-frame FIFO also preserves about 2.56 s of old audio under a slow client while dropping newer audio, increasing latency rather than staying near-live.
- **Minimal fix sketch:** Match `TrySendError::Full` and `Disconnected` separately; on disconnected, terminate the pump/send a session-disconnect event. Count successful wire writes in the writer (or rename the metric to `queued_a`). Use a much shallower audio queue or an oldest-drop/latest-wins queue so congestion produces a glitch without multi-second stale playback.

### 13. Both watchdogs use wall-clock milliseconds, so clock correction can false-kill or indefinitely extend a session

- **Severity:** low
- **Location:** `rust/src/remote.rs:101-103,517-555`; `relay/src/util.rs:32-34`; `relay/src/session.rs:443,495-505`
- **Failure scenario:** Android/Desktop wall-clock jumps forward past a threshold and a live link is declared dead; a backward correction makes `saturating_sub` return zero until wall time catches up, masking a dead link. This is uncommon but avoidable in long-lived phone sessions.
- **Minimal fix sketch:** Track liveness with monotonic `Instant`/elapsed time. Keep wall-clock timestamps only in K payloads and logs.

## Checks that did not become findings

- **v2 first-frame detection is sound for this relay:** `docs/BRIDGE.md:19-21` requires W first, and `relay/src/session.rs:420-452` synchronously enqueues W before starting the reader or accepting H-driven producers. The single FIFO writer cannot legally emit A/M/K ahead of it. A compliant v2 relay's first frame is therefore always W. However, `saw_w` is never read and a noncompliant peer that sends K forever before W can leave the phone in `connecting` forever (`rust/src/remote.rs:495-550`); validating `first != W` as a protocol error would harden this.
- **The nominal watchdog timings do not deadlock each other:** relay K every 1 s keeps the phone alive, phone K every 2 s keeps the relay alive, and 3/10 s versus 8 s gives orderly one-way-failure behavior. A normal phone Doze leads to relay teardown then reconnect on wake. The exceptions are findings 2 and 4: a blocked phone write freezes its own watchdog, and a blocked relay handler prevents queued watchdog cleanup.
- **Relay writer teardown is bounded on the normal path:** the five-second write timeout plus channel receiver drop unblocks blocking control-frame sends, and `stream.shutdown()` precedes joins (`relay/src/session.rs:420-435,554-565`). Normal non-panic exits do kill/join/reap capture, geometry, and file pumps. The missing cases are external-command stalls (finding 4) and unwinding (finding 8).
- **Dropping Oboe from teardown is not itself a demonstrated UB/deadlock:** teardown runs off the callback thread, the callback never takes `out_slot`, and Oboe 0.6.1 serializes concurrent close/error-close internally. Dropping the stream while a callback is active can block briefly inside Oboe but has no app-level mutex cycle here. Taking the stream out under the slot lock and dropping it after releasing the lock is still cleaner. The real `out_slot` defects are the supervisor lifetime and late-install race in finding 3.

## Verdict

**Not ship-safe yet.** The protocol ordering and nominal heartbeat design are coherent, and the relay's ordinary writer/pump teardown is substantially better than v1, but the phone has a cross-generation socket ownership race, unrecoverable blocking writes on Android's main-thread call paths, and unowned Oboe/audio workers; the relay can also miss its zombie law when its control loop blocks or unwinds. Findings 1-4 should be release blockers, with 5-9 fixed before calling reconnect/route-change lifecycle hardened.

---

## Resolution footer (2026-07-18 evening, the service-bench session)

All 13 findings addressed on master, commits `c824caf..b1fda36`:

| finding | fix | commit |
|---|---|---|
| 1 | generation-tagged publish → single-owner control thread (overlap impossible) | c824caf → ed70d89 |
| 2 | dedicated writer thread, fail-fast JNI enqueue, K unstarvable | aad0e34 |
| 3 | supervisor token + recv_timeout + install gate under slot lock, joined | 663bf0b |
| 4 | run_cancellable deadlines + cancel flag (watchdog/disconnect) + browse/fetch jobs | f087ec3 |
| 5 | ring close+clear before bounded worker join (ordered teardown) | 663bf0b |
| 6 | pump-id-tagged CaptureEof/FileEof, stale ids ignored | f087ec3 |
| 7 | bounded reopen ladder; exhaustion trips the session (one reconnect, never silence) | 663bf0b |
| 8 | Drop RAII on every pump + SessionState + RunningGuard; catch_unwind logs only | f087ec3 |
| 9 | full audio stack opens BEFORE publish + H | c824caf |
| 10 | lock-free SPSC BlockRing + preallocated scratch + no-log callback | 570549a |
| 11 | (pre-session fix preserved) Healthy backoff reset | a7e4904 |
| 12 | Full/Disconnected split, wire-counted tx_a, 32-frame audio queue | f087ec3 |
| 13 | monotonic liveness both sides; wall clock only in K/logs | c824caf + f087ec3 |

Field additions the audit didn't ask for: BUGLOG #5 (stale mute across sessions) found
live and fixed; catch-up policy field-tuned with Ben's ears (sustained gate — the first
cut skipped inside normal wifi jitter). Verdict flip: **ship-safe pending Ben's polish
round** — the daily-driver path now carries its own instrumentation (remoteStatus:
audio_buf_ms/skips/a_drops/leaked_threads + the Nerd HUD bridge line).
