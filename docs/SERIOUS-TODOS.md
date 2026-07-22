# SERIOUS TODOS

- [ ] **P0 Fortress audio spike, reopened 2026-07-22:** test whether a process already running as shell can capture output through `REMOTE_SUBMIX`/AudioPolicy, first with scrcpy ground truth, then a Shizuku UserService, then an ADB sidecar fallback. Record Spotify, SoundCloud, YouTube Music, browser/game, route, lock, reboot, and DRM edge cases. Full contract: `spec/AUDIO-CONNECTIVITY-AND-PROJECTM.md`.
- [ ] Instrumentation-test suite deferred (SELFTEST smoke covers cheaper) — revisit if regressions slip past.
- [ ] core-ktx/lifecycle pinned below latest until platforms;android-37 publishes.
- [ ] M4 remainder: mic source (RECORD_AUDIO AudioRecord path in CaptureService, mode extra) + in-app capture STOP control (service is not exported — only the app itself or force-stop can end it until the SRC popout lands in M5).
- [x] Shizuku permission-grant test — **NARROW NEGATIVE (2026-07-18), overbroad conclusion corrected 2026-07-22.** Directly running `pm grant dev.phosphor.mobil3 android.permission.CAPTURE_AUDIO_OUTPUT` fails because the permission is role/signature managed. This proves that shell cannot grant the privileged permission to the ordinary Phosphor app process. It does **not** prove that a shell process cannot capture output. Read-only S25 evidence now shows `com.android.shell` itself already holds `CAPTURE_AUDIO_OUTPUT`, `CAPTURE_MEDIA_OUTPUT`, `MODIFY_AUDIO_ROUTING`, and `MANAGE_MEDIA_PROJECTION`, and the remote-submix path exists. Shizuku UserService can execute code as UID 2000. Therefore all-app music capture is a plausible Fortress-only controlled spike, not a Play path and not yet a shipped capability. Public MediaProjection remains opt-out-respecting and must stay honest.
- [ ] Resting-beam dot (desktop law) not yet on mobile: silent source shows black, should show the centered dot + `no signal · <source>` label (M5).
- [ ] Genuine ">120" quality lever (Ben wants above-120): Android can't PRESENT above the panel
  rate (compositor vsync-locks all app surfaces; caps = [Mailbox, Fifo], no Immediate). The
  honest "more temporal detail" knob is the desktop's scope reconstruction rate /
  Computer::set_sample_rate(rate, oversample) — expose 48/96/192/384 kHz oversampling so the
  beam is integrated at higher effective rate per displayed 120 Hz frame. Needs decay-dt
  correctness (persistence scaled per substep) to avoid dimming. Wire in an M5 polish pass.
- [ ] rclone shared client_id retires during 2026 — mint our own Drive client_id (https://rclone.org/drive/#making-your-own-client-id) before it breaks
- [x] **Bridge v2 concurrency hardening — LANDED (service-bench session, 2026-07-18 evening).**
  All 13 audit findings addressed across commits c824caf…b1fda36 (phone Session RAII +
  control thread + writer thread + oboe supervisor rework + SPSC ring; relay deadlines +
  jobs + pump ids + Drop RAII + monotonic clocks; relay 2.1.0 on both machines). Receipts
  in the commit messages + docs/BRIDGE.md "Lifecycle & hardening". Self-assessed
  ship-safe pending Ben's polish round; an optional codex re-audit before v1.0.0 would
  be honest ceremony.
- [ ] Relay browse/play stayed sync-with-deadline where fast and became async jobs where
  slow (Drive fetch/browse) — if any NEW slow handler lands, it must be a job too; the
  control loop's budget is the phone's 3 s stall window.

## From the 2026-07-18 accuracy audit (fidelity map, not yet fixed)
- **Remote VISUALIZER geometry decimation drawn as trace**: desktop sends ≤64 strided
  segment midpoints; mobile joins adjacent midpoints as if they were the trace
  (render.rs geometry branch) — sparse rotated polygons on repeated traversals. The
  first suspect if a clean circle ever still splits in GEOMETRY mode (audio mode is
  fixed). Fix direction: draw decimated points as points/short dashes, or raise the
  desktop tap budget for the bridge.
- **s16 transport quantization**: relay captures s16le; desktop scope path is float.
  Low-level detail loss on the bridge. Consider f32 or 24-bit A-frames (bandwidth ×2).
- **Focus default divergence**: mobile 0.3 px vs desktop 1.6 — deliberate look choice?
  Ask Ben which is "the" phosphor look; the setting exists on both.
- **JNI capture ingest allocates per 10 ms chunk** (jni_glue pushCaptureSamples) —
  cadence jitter risk under GC; preallocate/reuse.
- **Local deck audio callback still uses mutex/Condvar AudibleRing** (deck.rs) — the
  remote path got SPSC; the local path could inherit it (audible-glitch class, not
  scope-tear class).
