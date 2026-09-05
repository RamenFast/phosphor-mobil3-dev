# Phase 3: B4 recursive local folders

- Date: 2026-09-05, UTC.
- Status: Phase 3 task 3.5 PASS. B4 remains VERIFY until final regression. Required direct/tree playback passed on the exact candidate. Oversized-provider stress has the separate limitation below.
- B IDs: B4, with B9 transport and source-handoff regression checks.
- Private issue: #1.
- Public sibling issue: RamenFast/phosphor#9.
- Mobile implementation commit: `9bf85276a2deca736dd8a2b13a7732482a96b4ac`.
- Sibling commit: `2a45b0f4d05696efe98970f51ac5358c052b565f`.
- Shared playback blob: `ea80431c1431490baf8c3ba25b8faa970e52566f`.
- Device role: S25, Android 16, build `S931USQSBCZF5_OYNBCZF5`. Exact candidate installed and read back. Ben unlocked the phone for the live checks below.
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
The separate final human-intent review also passed for the frozen source. It independently closed H1, H2 and the examined S1 orderings.
At 09:24 UTC the root checked all 30 intent-review identities against current source and found no mismatch.
Neither review substitutes for real phone acceptance.

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

## Live acceptance, 2026-09-05 09:39 through 10:09 UTC

Phase 3 task 3.5: **PASS** for its direct control, recursive valid tracks, invalid-entry handling and responsive playback requirements.
The source candidate remained unchanged. Private evidence is under `phase-03/live-02/` in the ignored task directory above.
This result does not close final regression, B2, B3, B6/B7/B8, or the remaining B1-B21 phases.

| Requirement | Observed check | Evidence and limit |
|---|---|---|
| Direct and recursive agreement | The identical control WAV opened through both Android pickers. Both produced a lit trace and PLAYING state. | `direct-1.png`, `tree-control.png`, direct/tree MediaSession snapshots and native open records. |
| Complete nested tree and stable navigation | The eight-item tree reached valid indices 0, 5 and 6, including depth two. Next skipped four invalid entries. Previous from 5 returned to 0. | `tree-next-media.txt`, `tree-previous-media.txt`, `last-playing.txt` and named native skips. |
| Decode before replacing ownership | Invalid-only and empty trees retained a working mic. The rejected E-AC-3 file retained prior paused local ownership. | Mic UI, active unsilenced AudioFlinger recorder, native validation errors and previous session state. |
| Exhaustion rests | The invalid tail emitted one skip and one exhaustion warning. It stayed paused without later automatic retries before source replacement. | `tail-rest-1.txt` and the complete marked log. |
| Real large-provider enumeration | The tree published 132 entries and naturally advanced through index 129 to supported nested music at index 131. | `paged-newer-pause.txt`, `paged-after-enumeration.txt`, native opens and `paged-live-controls.png`. This proves completeness, not that Android honored Bundle paging. |
| Representative compressed music | The same task-owned MP3 played directly and recursively with title, artist, position progression and changing trace. Ben confirmed audible output at 09:59:48 UTC. | Direct MP3 and paged-music snapshots. The MP3 was transcoded from a stock E-AC-3 track, which is not supported by this decoder. The original remained unchanged. |
| Transport intent and responsiveness | Direct pause held 1416 ms. MP3 pause held 21264 ms. A pause delivered during tree preparation preceded native open, and the published item stayed paused. | Separate session snapshots. Pause log epoch 1788601926.344970084 precedes open at 1788601926.591898521. Manual paused-next retained legacy autoplay. |
| Real seek after orientation change | A drag on the freshly observed rotated slider reopened the nested MP3 at 54.803 seconds and progressed to 55.728 seconds. | `paged-rotated-seek.txt` and native open record. Earlier old-coordinate attempts did not seek and are not counted. |
| Mic and capture handoffs | Mic-to-tree and capture-to-tree disabled the reader ring before local open. Capture used real Android consent and its projection became null after handoff. | MediaSession, projection, AudioFlinger and ordered native records. No real stuck reader or hardware failure was manufactured. |
| Narrow remote regression | The saved Tailscale relay connected and received audio frames. Returning to a nested local tree published deck state, played music and closed the relay socket. | `remote-ui.xml`, `remote-to-tree-ui.xml`, native session/open records and empty relay socket readback. The desktop GUI was not running, so full B3 geometry/audio acceptance remains in its own phase. |
| Grants and restoration | Each tree used the real picker and explicit grant. Reopening returned to the previous tree. Tuning and hosts stayed byte-identical. | Picker observations and restored preference archives. No Binder queue-array path was used. |

### Complete normal-workflow log window

The all-PID main/system/crash follow ran for 1200 seconds and reached `follow_deadline` with 435,373 records.
The host tool timed out at 600 seconds, but the device reader continued until its finite deadline.
Raw replay produced all 435,373 records and matched the captured text byte-for-byte.
Actual `PhosphorB4` start and end markers bracket the direct, recursive, transport and normal source tests.

- Raw SHA-256: `0655e352038e13928130e6c0febea6bc469edc993b5ccf13c7490072be69e3f7`.
- Captured and replayed text SHA-256: `d0d28172390b617cce0c42d4fc8f2b060196e2f5b2dc73c2949e1ea958e76edb`.
- Crash/ANR/fatal/panic candidate scan: zero matches in this window.
- Log-drop/chatty candidate scan: zero matches. No claim is made that Android can never drop a record without notice.
- App PID stayed 2273. Exit history changed only its persistence timestamp, not its entries.

The earlier reader rejected a maximum-length message before playback. Its corrected parser passed 275 strict checks, 275 sanitizer checks and 27 CLI checks.
The corrected reader also replayed the original 18,722 packets. It did not retroactively cover the gap before this fresh window.
No system executable, mount, privilege or application source changed for log collection.

### Separate oversized-provider stress and cancellation limit

After the required 132-entry run, the root added a temporary 20,001-file directory to make enumeration slow enough for a real touch cancellation.
Android's external-storage provider reported an ANR while DocumentsUI was listing it, before the app received the tree grant.
The provider restarted. This is a failed oversized-provider stress result, not a clean-device or arbitrary-size PASS.

The tree grant marker occurred at epoch 1788602705.908269109. A real mic selection followed at 1788602707.704134213.
Mic started at 1788602717.313164573, and still owned the source at 1788602766.196934815.
The superseded tree never opened its control track. Phosphor stayed on PID 2273 until the deliberate cleanup stop.
The delayed handoff is recorded as uncertainty in `docs/SERIOUS-TODOS.md`, not described as immediate cancellation.
The separate 180-second log completed with 79,427 records and exact raw replay.

The root removed the stress directory and verified that the real Android picker again listed the normal Music directory promptly.
Three-request destructive-loss races, late reader failures and focus-loss orderings retain production-linked test evidence.
They are not falsely counted as induced phone failures. The normal source transitions and pending-enumeration cancellation above are the live evidence.

### Final restoration

The root stopped only the debug app before restoring its original runtime preference file.
Final tuning, saved-host and runtime XML files each matched the pre-test backup byte-for-byte.
Speaker media volume returned from temporary index 3 to its exact original fine index 0.
USB stay-awake was already active. No keyguard, sleep, screensaver or charging policy changed.

Both task-owned external fixture trees, the slow-directory stress fixture, the last staged MP3 copy and the device diagnostic directory were removed.
The original stock file hash remained `4477b63b734753bdafa55fb01b85641434ba52fcf7ffde1016d9443ad7a88d85`.
The exact B4 APK remains installed. Rollback APK and private evidence remain on the host.
