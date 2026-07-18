# SERIOUS TODOS

- [ ] M4 Shizuku spike: can ADB-level privileges grant CAPTURE_AUDIO_OUTPUT-equivalent capture (Spotify)? Timeboxed; record outcome here either way.
- [ ] Instrumentation-test suite deferred (SELFTEST smoke covers cheaper) — revisit if regressions slip past.
- [ ] core-ktx/lifecycle pinned below latest until platforms;android-37 publishes.
- [ ] M4 remainder: mic source (RECORD_AUDIO AudioRecord path in CaptureService, mode extra) + in-app capture STOP control (service is not exported — only the app itself or force-stop can end it until the SRC popout lands in M5).
- [ ] Shizuku spike blocked on device: Shizuku is NOT installed on the S25. Ben: install Shizuku (Play Store) + start it via wireless debugging, then the spike can test ADB-uid capture privileges.
- [ ] Resting-beam dot (desktop law) not yet on mobile: silent source shows black, should show the centered dot + `no signal · <source>` label (M5).
