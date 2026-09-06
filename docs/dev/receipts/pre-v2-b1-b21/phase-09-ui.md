# Phase 9: controls, PiP and effective style

Status: **VERIFY**. Offline implementation, full gates, separate source/intent reviews and exact debug packaging passed. Installation and Android acceptance remain open.

- B IDs: B14, B13, B12, B10.
- Private issue references: #4 and #5.
- Runtime baseline: `0d47f0d21037adebd3d55f89e9f97e51798f7cf8`.
- Reviewed HEAD: `c03e784c990cbcd396c9a5dc8652de763391677a`, followed by the frozen changes listed below.
- Final source manifest: `root-correction03-pre-gate.md`, SHA-256 `9d47238a2a8c51535276291f2ae74e1cb90a4c7d95a1eaff332fba239f01633e`.
- Evidence home: ignored `dev/scratch/pre-v2-20260829T072841Z/phase-09/`.
- No Phase 9 installation, device input, playback, settings, permission or service operation occurred.

## Implemented contract and checks

| Outcome | Production and observed offline evidence | Required live evidence |
|---|---|---|
| B14: easier, consistent sliders | Shared production geometry supplies 44dp hit lanes, 2dp sharp tracks, square thumbs and live beam tint to seek, tuning, range and inline volume. The existing seek recognizer handles taps, horizontal intent, initial-thumb choice, current callbacks and cancellation. Six geometry tests passed. | Acquire all four lanes near their edges. Check taps, scrubs, cancellation, nearest-thumb behavior, vertical handoff and live tint. |
| B13: useful controls survive DECK removal | SOURCE owns the existing queue and jump action. Console owns the volume rule. DECK UI, route and glyph are absent. Native/JNI deck playback is unchanged. The false-default controls setting gates every hide path, reveals immediately and rejects stale timers. Thirteen controls tests passed. | Queue jumps, volume, pinned/default hide paths, quick enable-disable, modal and PiP ownership. |
| B13: current system-volume truth | Existing Activity heartbeat publishes the inverse-cubic MUSIC fraction even while paused. Resume and slider writes refresh it immediately. Console reads observable state. Getter and setter mapping remain unchanged. A real SnapshotStateObserver test checks supplied changes without a slider callback. | Hardware/system/route volume changes with the console pinned, including paused state and PiP return. |
| B12: independent quick/full PiP choices | One true-default auto-entry setting updates platform parameters and the API29/30 fallback. Quick/full controls share state and persistence. Explicit manual entry remains independent of auto-entry, linger and pinned controls. Five PiP tests and 19 archive tests passed. | Quick/full synchronization, manual and automatic entry, Android callback state, restore/import and supported API behavior. |
| B12: reachable short-height quick menu | One bounded scroll owner contains all quick actions. Safe insets follow the existing rotated frame. The viewport and anchor consume the console's outer occupied height. Content-first nested scrolling preserves downward-at-top dismissal, reversal and fling ownership. Numeric and source checks passed. | A titled seekable track in a 360dp-high frame, both rotations, locks, larger text, nonzero insets, final AUTO PiP control and all existing actions. |
| B10: effective style, not label-only feedback | FEEL supplies coupled chrome defaults. Explicit MOTION/CORNERS/LABELS retain precedence and null fallback. The existing LocalRoomStyle supplies chrome and one responsive live sample. ROOM tiles and the full-span STYLE/sample footer share one bounded grid. Nine style tests passed. | Visible changes from every choice, short/rotated reachability, TRY, reduced motion, reopen persistence and unchanged CRT output. |

Policy tests execute deterministic logic and observable state. Source assertions inspect production wiring. Neither executes Compose measurement, pointer dispatch, AudioManager changes or Android lifecycle callbacks.

## Review and correction history

1. The first full gate passed 298 tests. Independent source review found a ROOM footer outside its scrolling viewport and a new composition-time offset warning.
2. Correction01 moved STYLE/sample into the existing bounded grid and deferred animated offset reads to placement. Full2 passed 300 tests. Separate source review closed those two findings.
3. Independent source-first intent review found the relocated volume cache stale and the enlarged quick menu without short-height recovery. Correction02 added existing-heartbeat volume publication and bounded content-first scrolling. Full3 passed 307 tests.
4. Separate source review found that the console measurement excluded padding while the new viewport assumed outer height. The intent reviewer independently corrected an unsent draft that had accepted that assumption. Both final correction02 reports retained the blocker.
5. Root correction03 moved the size observer before inset and margin padding. One regression now checks the producer ordering as well as the production numeric consumer. Full4 passed 308 tests. Separate source and intent addenda closed the remaining measurement defect and retained volume closure.

Every earlier verdict remains intact. A later passing gate does not retroactively accept an earlier source snapshot. The reviews found no further concrete defect in their mapped scope. That is not Android acceptance.

## Final frozen gate

Root ran the Gradle wrapper with offline resolution, one build owner and two Gradle workers:

```bash
./gradlew --offline --no-daemon --max-workers=2 \
  :app:testDebugUnitTest --rerun :app:lintDebug :app:checkEngine :app:cargoBuildDebug
```

Full4 completed at 2026-09-06 01:19:45 UTC with exit 0: **308 tests, zero failures, errors or skips**. Lint retained 15 existing warnings and zero errors. Its unchanged report and native Cargo task were UP-TO-DATE, not fresh native runtime execution. The complete before/after input inventories were byte-identical.

| Evidence | SHA-256 |
|---|---|
| Both full input inventories | `48fa978efc6eeddde2aa96c41f4bb895eb8760baf30fe82f920b62a34f22ae01` |
| Full4 Gradle log | `ac80e1133c32d0bb76d8a5eecf7f5e2dd1040104f099b92a682feb2d84a909d1` |
| Full4 test summary | `fe803258ffcd0c62f3bda16172bc9e659ddb1e59f117f8b71d6815b551f4aa5e` |
| Full4 lint XML | `cc880e32ffcb1e0052f776d43c4d7bea26894346c103e4375ca1e27cf9fb5192` |
| Final source correction review | `aad1541a3474c0f18e50d9ed4dfa553ed5ccf994a8da32676784a251fdba4d76` |
| Final intent correction review | `ce3aeaf76abe0306a9711a8d4802bc49b42d15a812f9d099ea94a8382dec0c5b` |

All 21 current implementation/test/spec hashes were independently checked. DECK deletion was verified separately. Phase8 stage guards, paused-seek ownership and native playback were preserved. The 53 consumed shared-source hashes matched the retained engine `4dc0f2c3ec27c560b497b887f2a8e9c9031a967f`. Unrelated sibling work was not reset or changed. Static package boundaries and the protected archive passed.

## Acceptance, rollback and next step

- B10: VERIFY. Source checks and style tests passed. Rendered acceptance remains open.
- B12: VERIFY. Policy, archive and bounded-menu checks passed. Android PiP and interaction remain open.
- B13: VERIFY. State, ownership and source checks passed. Actual volume, queue and pin behavior remain open.
- B14: VERIFY. Geometry tests passed. Actual touch acquisition and arbitration remain open.

The exact Phase8 APK remains retained and uninstalled. The earlier Phase7 APK is the last verified installed debug baseline. Existing device backups and task fixtures remain governed by the private phone checkpoint. No fresh preference backup is claimed for this phase.

Next: commit the reviewed sources, retain the exact clean-snapshot Phase9 debug APK and verify its identities. During an undisturbed phone window, back up current preferences, install through `dev/pm3`, verify readback and preference preservation, then execute the named matrix and paused-seek/Phase8 regression. Revert this phase's source commit and use the retained prior artifact if rollback is needed. No publication, release signing, final B-card closure or drift reset is included.

## Exact committed artifact, 2026-09-06 01:30 UTC

Behavior commit `992c08a6ab836e6af25896c87ce8763817cabfea` contains only the 27 reviewed implementation, test, spec and evidence paths. Before commit, root rechecked 814 unchanged Full4 input hashes, excluding only owned documentation updates. The staged path set matched the owned list. The protected archive and source boundary passed. Both original working trees were clean after the commit, with no sibling change made by this phase.

The sole isolated build completed at 01:29:34 UTC, exit 0, from clean detached snapshots of that mobile commit and consumed engine `4dc0f2c3ec27c560b497b887f2a8e9c9031a967f`. Its input manifest covers 355 exact mobile paths and 53 consumed shared paths. Source checks matched before and after packaging. The exact build passed 308 tests, lint and engine checks. Scope gates passed 2/2. Package boundaries passed 11 source and 5 artifact checks, with one documented trusted-runtime exemption.

- Package: `dev.phosphor.mobil3.debug`.
- Version: `2.0.0-debug`, code `2000000`, minimum SDK 29.
- Artifact: `candidate-992c08a6ab83-isolated/phase-09-992c08a6ab83.apk` in the private evidence home.
- APK SHA-256: `7d8ed70487ab7ffad1060cc1b46fcea2a1d4e8fc5dffc31314b96fe2bf04be3c`.
- Debug signer SHA-256: `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d`, matching the installed debug lineage.

Root independently ran `sha256sum -c SHA256SUMS`. All six entries passed:

| Evidence | SHA-256 |
|---|---|
| Build log | `67c19ec40ccdd38db096c7cac64a341acca41ff019173608a2bb841a8e848b6e` |
| Source identity | `6dbf664bb7fe61372ca219932ec0d2073a84e657a3cb423c8053233c8c32e900` |
| Test summary | `fe803258ffcd0c62f3bda16172bc9e659ddb1e59f117f8b71d6815b551f4aa5e` |
| Signer receipt | `5cd4660fc96362205bbaa03078d98b001a8ed33a18e1e32a904cff46f9ddd49a` |
| Artifact boundary | `35a94ded3bff68a4e0802e20462f77e23a49e8c731a7a412dbe7e28da1b54fe4` |

The sixth entry is the APK hash above. This closes exact packaging, not installation or live B-card acceptance. No device action occurred. The next live step still starts with current preference backup and exact installed readback. Sequential Phase10 source work may proceed offline while those gates remain open.
