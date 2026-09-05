# Verification and receipt contract

## Distinct evidence levels

| Claim | Required observation | Insufficient substitute |
|---|---|---|
| Policy correctness | Named production-policy cases execute, including adverse ordering | Grep or zero-test Cargo filter |
| Android adapter correctness | Actual Activity/service/JNI path runs on retained APK | Copied policy or plain JVM stand-in |
| Beam fidelity | Fixed sample/clock/geometry inputs, declared image ROI and measured output | A title, live flag or Compose-only screenshot |
| Audio truth | Sample/channel/owner evidence plus actual Android source behavior | Initialized recorder or nonzero volume |
| Teardown completion | Resource release/join and current-owner checks | Timeout expiry or a cancellation request |
| Delivery | Exact clean source, artifact/hash/signer, installed readback and scenario receipt | A build from later docs HEAD or source-only PASS |

All future tests named in phase files are proposed unless explicitly called existing.
Require nonzero test counts and the named requirement cases. Detect ignored tests, GPU skips and missing adapters explicitly.
Never relabel a host policy test as native Android or physical latency acceptance.

## Phase receipt template

Create `docs/dev/receipts/mobile-next/NN-name.md` for each implemented phase.
The tracked receipt is sanitized. Full logs/settings/addresses stay in ignored private storage.

```text
Phase / task IDs:
Status: OPEN | FAIL | BLOCKED | PASS
Requirement and expected result for each task:
Mobile source commit / shared source commit:
Owned paths and before/after SHA256 manifest:
Spec version / feedback state:
APK hash / signer hash / build identity:
Instrumentation APK hash, where relevant:
Toolchains / dependency locks:
Command / exit / actual cases / failures / errors / skips:
Real-device scenario IDs and observed result:
Independent source review identity and verdict:
Independent intent review identity and verdict:
Failed attempts and correction:
Private evidence reference IDs, without device addresses:
Restoration evidence / rollback APK and source commit list:
Remaining limits and next action:
```

An offline PASS can coexist with device BLOCKED, but the phase remains OPEN until its required device rows pass.
Root reviews the exact source hashes approved by reviewers. Any source edit invalidates that review until checked again.
Do not reset `drift` or close inherited B cards on the new roadmap's planning authority.

## Source and offline regression floor

Run these only during authorized implementation, after exclusive build ownership is established:

```bash
cd "$M"
source scripts/env.sh
scripts/test-pm3.sh
scripts/test-play-boundary.sh
scripts/test-release-gates.sh
scripts/check-play-boundary.sh source --json
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:checkEngine
cargo test --manifest-path rust/Cargo.toml --locked
cargo test --manifest-path relay/Cargo.toml --locked
cargo test --manifest-path "$E/Cargo.toml" --locked -p phosphor-audio
cargo fmt --manifest-path rust/Cargo.toml --check
cargo fmt --manifest-path relay/Cargo.toml --check
cargo clippy --manifest-path rust/Cargo.toml --locked --all-targets -- -D warnings
cargo clippy --manifest-path relay/Cargo.toml --locked --all-targets -- -D warnings
(cd docs/dev/archive/2026-08-05-scope-reset/protected && sha256sum -c SHA256SUMS)
git diff --check
```

Add each phase's new tests and affected shared crates. A changed shared enum requires desktop consumer compilation too.
Preserve known baseline warnings separately. Do not repair unrelated formatting drift by silently widening ownership.
GPU tests and audible/device fixtures are excluded from quiet media sessions even if their executable is local.

## Freeze and exact build

Run fixture scripts before the final build. Commit reviewed source and confirm both trees are clean.
Record full source and lockfile manifests before and after `./gradlew --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest`.
Build both APKs together from the same reviewed clean source. Do not retain an earlier dirty instrumentation APK beside a clean app APK.
Retain the app and test APK, source identities, signer verification, merged manifest and resolved runtime dependency report.
Use the existing artifact scanner interface with those exact paths:

```bash
: "${CANDIDATE_APK:?exact retained APK}"
: "${MERGED_MANIFEST:?manifest from that exact variant build}"
: "${RUNTIME_DEPENDENCIES:?dependency report from that exact variant build}"
scripts/check-play-boundary.sh artifact --artifact "$CANDIDATE_APK" \
  --manifest "$MERGED_MANIFEST" --dependencies "$RUNTIME_DEPENDENCIES" --json
```

Re-find variant output paths from the build rather than inventing them. Root records the full commands in the receipt.
Only [DEVICE](DEVICE.md) activates the retained candidate. A subsequent docs commit does not silently substitute a rebuilt APK.

## Privacy and release separation

Public records contain fixture hashes, source/artifact identities, measured results and sanitized commands.
Do not export private receipt bodies, estate paths, serials, hosts, media titles, captured samples, credentials or settings backups.
A token scan is only a floor. Inspect the actual public archive manifest and contents.

Keep the canonical fixed ship-check gates. Pre-release behavior acceptance permits exactly three external reds:
`signing.release`, `provenance.release`, `release.bundle`. It permits no skips or additional red gates.
Release acceptance requires approved credentials/provenance and all release gates green. Never weaken the checker to claim a release.
