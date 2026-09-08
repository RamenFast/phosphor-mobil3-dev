# Section 2 helper build checkpoint

Date: 2026-09-08, 02:46 UTC. This is a debug feasibility build, not product R01 acceptance.

## Implementation and integration

The fixed native supervisor, framework-only AudioPolicy helper, typed private pipe protocol and own-UID 997 Hz fixture follow [the helper contract](section-02-helper-contract.md). The DUMP-protected receiver alone invokes the explicit action. Normal startup, production source and permissions remain unchanged.

The coordinator reviewed the helper before building. Corrections covered a final-frame/waitpid race, the hidden AudioManager success constant, leading-silence frequency measurement, and Android-managed directory modes. The coordinator pinned Rust 1.96.0 and 16 KiB ELF alignment. The real compiler then exposed unsupported legacy AGP Provider wiring, a peak field/method mismatch and hidden Os.unlink. These were corrected against the installed SDK/AGP signatures, without compatibility bypasses or system changes.

## Observed checks

- Coordinator native host tests: 11 passed with locked Cargo resolution, including ABI, fixed identity/path, framing, deadlines, kill/reap state, terminal-frame handling and SHA-256 vectors.
- Coordinator Java protocol/math assertions: 29 passed, including exact reads, failure causes, wrong tone, silence and a 500 ms leading-zero fixture.
- Wrapper task `396251s5dg`: 464 Android unit tests, lint, assembleDebug, assembleDebugAndroidTest and checkEngine passed. Exit 0 in 72.2 seconds, 88 tasks.
- The generated androidTest APK exists, but there are no handwritten instrumentation cases in this source. Its packaging is not a device-test pass.
- Actual packaged bootstrap is AArch64 ELF with entry `0x16778`, interpreter `/system/bin/linker64`, and all four LOAD segments aligned to `0x4000`. It is an executable PIE, not a renamed JNI library.
- Packaged helper JAR digest matched its packaged metadata. Debug manifest has extractNativeLibs=true, the exact debug action and DUMP-protected receiver.
- Source boundary gate returned 12 clean checks. Diff checks passed.

These checks exercised the precommit candidate. Compiler-reported redundant Kotlin assertions were then removed. Build the committed source again before installation and retain both APK hashes plus source identity. The installed phone is still the earlier `ff7067e` identity probe at this checkpoint.

## Evidence and remaining gates

Private recovery contains `root-audio-first-build.log`, `root-audio-build-agp-fix.log`, `root-audio-build-symbol-fix.log` and `root-audio-elf.txt`. The first two logs are failures with diagnosed causes, not successful builds.

Actual extracted execution, KernelSU UAPI, fd6 ART class loading, PCM, route cleanup and failure recovery remain unproven. The S25 is currently muted. Preserve volume and distinguish routing/PCM evidence from physical audibility. Do not capture other apps or change root/system configuration. Product root opt-in, service ownership, normalized streaming, ordinary/opted-out media, accessories and full section 2 critique remain open.
