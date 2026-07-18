# Handoff — next session starts here

## M0–M3 SHIPPED in one session (2026-07-18); M4 (capture + mic + Shizuku spike) is next

- **M0** scaffold+toolchain+device: commit 2db590b/ebd53b0.
- **M1** the beam at 120 Hz: 919fd0b/20caa45. fps receipt via logcat counter +
  `frameRateOverride 120.00001` in dumpsys display; SELFTEST hatch + `pm3 smoke`
  (fnv fingerprint 7e91d807fdb542eb — compare across builds).
- **M2** real light: a24aba1 (+ upstream phosphor b72154c — feature gate, NOT pushed).
  Deck = spawn_player + oboe; pause = stop popping (backpressure). GOTCHA: oboe needs
  `shared-stdcxx` + libc++_shared.so copied by the Gradle cargo task (dlopen
  __cxa_pure_virtual crash otherwise).
- **M3** Media3: cf5c56a. GOTCHAS that cost an hour: (1) session must be `addSession()`ed
  explicitly when no controller connects; (2) COMMAND_GET_TIMELINE required for the
  notification. One UI media card lives in expanded Quick Settings, not the shade
  (`cmd statusbar expand-settings` for screenshots).
- Test track staged on-device: `files/acidrain.wav` (push via
  `adb shell 'cat /data/local/tmp/x.wav | run-as dev.phosphor.mobil3 sh -c "cat > files/x.wav"'`
  — shell CAN'T write Android/data, and apps can't read /data/local/tmp).
- Current device serial flips between `192.168.1.229:34443` and the mdns name
  `adb-R3CY90HEZ3M-yaSG5C._adb-tls-connect._tcp`; pm3 uses whichever is first.
- M5 polish list so far: media card artist shows literal "null" for untagged WAVs;
  demo Lissajous should become the SRC "demo" entry; deck sheet UI per UX-SPEC.

## Original M0 notes (2026-07-18)

Repo scaffolded from the ratified plan (`~/.claude/plans/steady-prancing-bee.md` holds the
full text; docs/ARCHITECTURE.md + docs/UX-SPEC.md are the standing extracts).

Done so far:
- Toolchain bootstrapped to `~/Android` (JDK 21 Temurin, SDK 36, build-tools 36, NDK
  28.2.13676358, Gradle dist 9.1.0 + wrapper **9.3.1** in-repo, cargo-ndk 4.1.2,
  aarch64-linux-android target). `scripts/bootstrap-android.sh` is idempotent.
- Engine crates (beam/dsp/proto/render-gpu/render-cpu) cross-compile to Android **clean,
  first try**, wgpu 27 included. Host `cargo test` green in rust/ (DSP emits segments).
- Gradle build green: AGP 9.1.1 (NOTE: AGP 9 has built-in Kotlin — do NOT add the
  kotlin-android plugin; compose plugin rides alongside), compose BOM 2026.06.01,
  core-ktx pinned 1.17.0 + lifecycle 2.10.0 (1.19/2.11 demand compileSdk 37, which has no
  published platform yet — revisit when platforms;android-37 exists).
- `dev/pm3` CLI live and conforming; `pm3 doctor` all-ok except device (needs first-time
  wireless pairing — Ben's hands), `pm3 build` returns enveloped APK receipt.
- M0 APK: black stage + mono engineInfo JSON from the Rust core (proves Kotlin→JNI→
  phosphor-dsp on device).

Next (rest of M0): pair the S25 (`pm3 pair <ip:port> <code>` → `pm3 connect`), install,
run, screenshot receipt into docs/dev/receipts/, first commit. Then M1 (see task list /
ARCHITECTURE.md): render thread + surface lifecycle handshake + SELFTEST hatch.

## Keystore (M6, not yet created)

Release keystore will live at `~/.android-keys/phosphor-mobil3.jks`, NEVER in the repo.
Losing it means users uninstall/reinstall — back it up when created.

## Standing gotchas

- Sibling `../phosphor` checkout is required (path deps). `pm3 doctor` reports its HEAD.
- `local.properties` (gitignored) needs `sdk.dir=/home/ben/Android/Sdk`.
- Wireless adb port changes every time wireless debugging toggles; `pm3 connect` mdns-
  discovers it.
