# Phase 03, native substage: distinct failure and honest cancellation

Entry: reader tasks 03.1-03.2 PASS. Complete tasks 03.3-03.5 before Phase 04.
Phase 00 may retire a task only if the accepted inherited repair already proves its entire contract.

## ▸ 03.3 Preserve one event consumer and distinguish decoder failure

📁 Existing: `M/rust/src/deck.rs`, `M/rust/src/deck_close.rs`, `M/rust/src/jni_glue.rs`, `M/rust/src/lib.rs`, `M/app/src/main/kotlin/dev/phosphor/mobil3/PhosphorNative.kt`, `M/app/src/main/kotlin/dev/phosphor/mobil3/PlaybackService.kt`, `E/crates/phosphor-audio/src/events.rs`, `E/crates/phosphor-audio/src/playback.rs`, `E/crates/phosphor-app/src/shell.rs`, `E/crates/phosphor-app/src/feed.rs`.
NEW if the accepted B6 implementation lacks an equivalent seam: `M/rust/src/playback_events.rs` with production-linked host tests.

Reuse the accepted B6 destructive receiver. Fold its observations into current-generation state and expose non-destructive metadata reads.
Do not consume events in validation, a second tick or a latest-command slot that can discard terminal state.
If still absent, add shared `PlaybackFailed { error: String }` for decoder/open failure and update exhaustive desktop consumers in the same paired change.
Clean EOF remains PlaybackEnded. Explicit Stop emits neither fake EOF nor decoder failure. Retain accepted invalid-entry advancement policy.
Preserve audible-ring close before Stop/join, which prevents paused full-ring deadlock.
Land event contract and tests first, then activate the single existing consumer. Keep Kotlin/JNI/shared enum identities paired in the receipt.

✅ Run after adding cases for corrupt tail, clean EOF, explicit stop, stale generation and repeated metadata reads:

```bash
cargo test --manifest-path "$M/rust/Cargo.toml" --locked playback_events
cargo test --manifest-path "$M/rust/Cargo.toml" --locked deck_close
cargo test --manifest-path "$E/Cargo.toml" --locked -p phosphor-audio
cargo check --manifest-path "$E/Cargo.toml" --locked -p phosphor-app
cd "$M"
./gradlew --no-daemon :app:checkEngine :app:testDebugUnitTest
```

Expected: nonzero named event cases distinguish failure/EOF/stop, no double advance, desktop matches compile and full-ring regression passes.
Run exact-candidate D2/D3 before closing this task. Host helpers do not execute Android JNI.

## ▸ 03.4 Cancel only the replaced provider request

📁 Existing: `M/app/src/main/kotlin/dev/phosphor/mobil3/DocumentTreeSource.kt`, `M/app/src/main/kotlin/dev/phosphor/mobil3/LocalAudioStaging.kt`, `M/app/src/main/kotlin/dev/phosphor/mobil3/PlaybackService.kt`.
NEW: `M/app/src/test/kotlin/dev/phosphor/mobil3/DocumentCancellationTest.kt`, `M/app/src/androidTest/kotlin/dev/phosphor/mobil3/DocumentCancellationIntegrationTest.kt`.

Use one CancellationSignal per source request for supported query calls. Verify the exact API-29 overload before activation.
Keep cooperative current-request checks before publication and throughout traversal. Close late cursors and staged streams on cancellation.
Do not reuse the signal for a successor. Test a controlled blocked provider call, replacement, late cursor delivery and cancellation during fallback pagination.
The test provider belongs only to the instrumentation package. It adds no product provider or exported administration surface.
Cancellation does not promise to stop an external provider ANR before grant. Use representative tree scale rather than repeating extreme stress.

✅ Run host policy cases, minimum-API lint and actual provider integration:

```bash
cd "$M"
./gradlew --no-daemon :app:testDebugUnitTest --tests dev.phosphor.mobil3.DocumentCancellationTest
./gradlew --no-daemon :app:lintDebug :app:checkEngine :app:assembleDebugAndroidTest
export TEST_CLASS=dev.phosphor.mobil3.DocumentCancellationIntegrationTest
run_device_case
```

Expected: exactly the old request cancels, late resources close, ordering stays stable and a replacement never receives the old listing.
Run D2 source replacement with the exact APK. An unresponsive external provider remains a documented platform limit.

## ▸ 03.5 Make remote and relay completion failures explicit

📁 Existing: `M/rust/src/bridge_core.rs`, `M/rust/src/remote.rs`, `M/relay/src/session.rs`, `M/relay/src/util.rs`.
NEW: `M/app/src/androidTest/kotlin/dev/phosphor/mobil3/RemoteLifetimeTest.kt`.

Add generic join/cancel tests at the existing cfg-free bridge_core production seam and blocked-socket tests in retained relay modules.
Android-gated `remote.rs` has no host test module. Its actual teardown is covered by Android integration, not implied by host Cargo success.
Test blocked send, stop during startup, browse/fetch cancellation, refused join and stale completion after replacement.

Only then fix a demonstrated ignored completion result or cancellation/socket-shutdown/join order in that owner.
Keep one writer and protocol v2. Drop streams outside shared locks and retain current Arc-identity checks.
A failed join reports the resource that remains unresolved. Block replacement only where that resource still conflicts with the successor.

Do not forbid all reconnects because an unrelated revoked worker timed out. Do not clear diagnostic counts to manufacture completion.

✅ Run deterministic production-seam cases, compile Android and run exact remote integration:

```bash
cd "$M"
cargo test --manifest-path rust/Cargo.toml --locked bridge_core
cargo test --manifest-path relay/Cargo.toml --locked
cargo clippy --manifest-path rust/Cargo.toml --locked --all-targets -- -D warnings
cargo clippy --manifest-path relay/Cargo.toml --locked --all-targets -- -D warnings
./gradlew --no-daemon :app:checkEngine :app:assembleDebugAndroidTest
export TEST_CLASS=dev.phosphor.mobil3.RemoteLifetimeTest
run_device_case
```

Expected: named host cases execute, normal fixtures leave no owned survivors, refused joins remain errors and Android remote ownership stays correct.
D8 requires a separately permitted client-free relay/device window. Do not attach fixtures to Ben's active music relay.

↩ Rollback: revert only the failing subtask's activation, then its unused inert contract commit.
Revert mobile/shared event changes as a recorded pair. Provider and relay changes use separate commits and independent rollback units.
Restore the exact compatible debug APK if installed. Do not modify live service configuration as part of source rollback.
⛔ No second event drain, universal resource manager, protocol redesign or renderer-timeout recovery change.
