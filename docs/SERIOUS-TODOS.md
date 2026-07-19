# SERIOUS TODOS

- [ ] M4 Shizuku spike: can ADB-level privileges grant CAPTURE_AUDIO_OUTPUT-equivalent capture (Spotify)? Timeboxed; record outcome here either way.
- [ ] Instrumentation-test suite deferred (SELFTEST smoke covers cheaper) — revisit if regressions slip past.
- [ ] core-ktx/lifecycle pinned below latest until platforms;android-37 publishes.
- [ ] M4 remainder: mic source (RECORD_AUDIO AudioRecord path in CaptureService, mode extra) + in-app capture STOP control (service is not exported — only the app itself or force-stop can end it until the SRC popout lands in M5).
- [x] Shizuku spike — **CONCLUSIVE NEGATIVE (2026-07-18).** Shizuku installed (from GitHub;
  Play version refused the S25's Android version). Shizuku hands an app the shell UID (2000)
  privilege level — which is exactly what an adb shell has. Tested directly from adb:
  `pm grant dev.phosphor.mobil3 android.permission.CAPTURE_AUDIO_OUTPUT` →
  `SecurityException: Permission ... is managed by role`. That permission is ROLE-MANAGED, not
  a runtime grant, so neither shell nor Shizuku can confer it — only system-signed/privileged
  apps (Samsung's own recorder) hold it. **There is no sideloaded path to capturing opted-out
  apps (Spotify/YT Music/DRM). Do not revisit.** The honest capture story stands: deck +
  browsers/games/non-DRM apps + mic.
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
