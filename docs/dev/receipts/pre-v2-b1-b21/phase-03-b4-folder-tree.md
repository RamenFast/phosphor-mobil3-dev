# Phase 3: B4 recursive local folders

- Date: 2026-09-05, UTC.
- Status: VERIFY. Implementation and source review passed their scoped gates. Final intent review and B4 phone acceptance remain pending.
- B IDs: B4, with B9 transport and source-handoff regression checks.
- Private issue: #1.
- Public sibling issue: RamenFast/phosphor#9.
- Mobile implementation commit: `9bf85276a2deca736dd8a2b13a7732482a96b4ac`.
- Sibling commit: `2a45b0f4d05696efe98970f51ac5358c052b565f`.
- Shared playback blob: `ea80431c1431490baf8c3ba25b8faa970e52566f`.
- Device role: S25, Android 16, build `S931USQSBCZF5_OYNBCZF5`. Exact candidate installed and read back. Live picker and playback acceptance are blocked by keyguard.
- ADB identity: [redacted].
- Package and version: `dev.phosphor.mobil3.debug`, `2.0.0-debug`.
- APK and installed readback SHA-256: `98523b2eadd1ea2807ccd9f6ebcfc0850d6d3ec430c8d744ad330c7ca2d8be0a`.
- Signer SHA-256: `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d`.

## Human outcome

The same supported music must play through the direct picker and recursive folder picker, with output and a changing trace.
Invalid entries must report and skip without freezing controls or publishing false success.
Source replacement must release the previous owner and respect the latest selection and transport intent.

## Committed shared change

The sibling adds first-chunk preflight through its existing decoder. It accepts valid silence and requires finite, nonempty decoded samples.
Validation does not spawn playback or publish events. It does not guarantee whole-file integrity.
No desktop production caller or version changed. Independent shared-source review found no blocking preflight defect.

`cargo test --manifest-path ../phosphor/Cargo.toml -p phosphor-audio --locked` passed 27 tests, including four preflight fixtures.
The reviewed source blob is unchanged in the sibling commit. Existing shared rustfmt drift was compared with baseline and remained unchanged.

## Frozen-source gate evidence, not phone acceptance

Private logs live under the ignored `dev/scratch/pre-v2-20260829T072841Z/phase-03/` directory.

| Check | Observed result | Limit |
|---|---|---|
| Android final gate 3 | 162 unit tests passed, no failures, errors or skips | Frozen-source helper and integration checks, not real service or phone execution. |
| `:app:lintDebug` and `:app:checkEngine` | Passed with final gate 3 | Native compile, not Android reader or output execution. |
| Mobile locked Cargo tests | 39 passed | Host tests exclude Android-only JNI and Oboe execution. |
| Relay locked Cargo tests | 26 passed | No live relay handoff acceptance yet. |
| `scripts/check-play-boundary.sh source --json` | 11 source checks passed | No packaged-artifact claim. |
| `scripts/test-pm3.sh` | 16 fixture checks passed | Mock installation checks, not actual APK readback. |
| `scripts/test-play-boundary.sh` | Passed | Product-boundary fixtures only. |
| `scripts/test-release-gates.sh` | Passed | Signing and provenance rejection fixtures, not a release build. |

Android commands used the repository wrapper after sourcing `scripts/env.sh`:

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:checkEngine
cargo test --manifest-path rust/Cargo.toml --locked
cargo test --manifest-path relay/Cargo.toml --locked
```

Final gate 3 completed at 09:08:55 UTC against the 09:07 source freeze.
It also passed mobile formatting, both repository diff checks and protected archive hashes.
The complete source manifests before and after the gates matched. Earlier 139-test and 158-test checkpoints remain historical evidence.

`checkEngine` invokes locked cargo-ndk checking. It does not itself emit or enforce a Git SHA receipt.
The root separately recorded both clean repository identities and bound them to the final APK build.

## Exact debug build and installation

After the implementation commit, the root ran `:app:assembleDebug :app:checkEngine` through the wrapper from a clean tree.
The build completed at 09:19 UTC. Source hashes still matched final gate 3.
The exact debug APK, merged debug manifest and debug runtime dependency report passed 11 source and 5 artifact boundary checks.
The scanner verified its single packaged C++ runtime exemption against the pinned NDK.

`dev/pm3 --serial [redacted] install <exact-candidate.apk>` passed at 09:19:48 UTC.
Local and installed APK hashes matched, and signer verification passed. `dev/pm3 run` then reported a running process.
Tuning and saved-relay XML bytes matched the fresh pre-install backup after installation.

The phone remained keyguard-restricted after launch. The root did not guess a PIN or change keyguard policy.
No direct/tree playback, audible/visual agreement, source race or live B4 crash-window PASS is claimed.
The root verified separate diagnostic access with 1000 all-PID Android records, a unique marker and exact raw replay.
That finite access check is not coverage of a B4 playback test.

## Review and acceptance still required

The independent final source review passed on the 09:07 frozen candidate.
It closed the original EOF, destructive-failure, invalid-tail, Oboe-cleanup and successful-local-face findings.
It also closed newer transport intent, persistent native and reader loss across supersession, dead remote-session retirement, and ownership-guarded idle cleanup.
At 09:15 UTC the root independently compared all 30 reviewed source identities with current files. Every identity matched.
The separate final human-intent review remains pending. Neither review substitutes for real phone acceptance.

Prepared controls include a multi-level tone tree with invalid entries and a corrupt tail, plus a separate tree exceeding 128 entries with nested compressed music.
Fixture preparation and host measurements are not phone acceptance.
Actual checks must cover direct/tree agreement, both navigation directions, invalid-tail rest, pending pause, source transitions, responsive controls and truthful state.
Output measurements, changing trace and session progression must agree. A screenshot alone is insufficient.

## Restoration and rollback

The verified Phase 2 APK remains the rollback artifact. Its SHA-256 was rechecked before installing the B4 candidate.
Fresh preferences were backed up immediately before installation. Tuning and saved-host bytes remained unchanged afterward.
No phone test toggle or keyguard setting changed. Task-owned fixture trees and the temporary log reader remain ready for the blocked live checks.
After live acceptance or closeout, restore any newly changed test toggles and remove only task-owned device fixtures and diagnostics.
Protected archive hashes passed with final gate 3.

## Redaction

This tracked checkpoint excludes literal device addresses, personal media paths, private hosts and raw device state.
Raw logs, screenshots, recordings and preference backups remain ignored and private.
