# Section 6 color integration evidence

## Released source and correction

Duckling released all15 implementation paths at08:31:47 UTC, then stopped. The unchanged [writer handoff](section-06-color-handoff.md) identifies that source and its host-only checks. Coordinator verified all15 hashes before the full integration gate. The [contract](section-06-color-contract.md) predates implementation.

First gate5254318h78 passed104 native tests, three offscreen GPU tests and the production source boundary. Android compilation reached538 tests, with three failures. Each failing source-linked assertion still searched the old no-argument `restoreTuning()` or old import-result binding. Coordinator inspected the new typed merge, boolean persistence and orientation restoration before updating those exact anchors. No existing behavior assertion was removed. A new public `SettingsArchive.decode`/`merge` regression verifies present double-tap/grid booleans, absent orientation choices and unchanged input preferences.

## Frozen integration gate

Gate942127xywq ran08:42:22–08:43:03 UTC. It passed539 JVM tests across48 suites with zero failures, errors or skips,104 native tests,three actual offscreen GPU tests,lint,both debug APKs,engine integration,release-helper build and production source boundary. Complete mobile/shared source manifests were unchanged before and after. Shared source is0ffd658d7f19e68180c2720e0500b23644619e90.

This was a frozen working-source integration build, not the later reviewed clean-source installation freeze. Neither APK was installed. It does not prove Android callback delivery, six-slot touch targets, accessibility, rapid-warning flow, actual color timing, or HOLD/TRACK behavior on the phone.

Evidence directory: `dev/scratch/mobile-expansion-20260908T001819Z/`. Prefix: `color-integration-0842`.

| Evidence | SHA256 |
|---|---|
| `run-color-integration-0842.sh` | `00060eeefe44036f1147fd96bb8d0fce4f78a0140c7ce864c7f2c787dbe3389e` |
| `color-integration-0842-results.json` | `4a4e27d389ae7374f0276fb56525d17f71b94fbeb5dd28ac7826e8bb83e102b1` |
| `color-integration-0842-mobile-source.sha256` | `5ee9af0eec0573ba418d08a6e0ab74348cb0530ac76f9ad07aec9c796523ab79` |
| `color-integration-0842-engine-source.sha256` | `581cd08f0a1fedcb8470e6848957edcec1017012bb772462e01280bc1d30acb9` |
| `color-integration-0842-gradle.log` | `6fbdb58527d9dfa2973082b3bb28dba1af3176370024b1e7fbf6ddfe5b013ec7` |
| `color-integration-0842-native.log` | `53e89210fe4bcad9351dc4157bb5c50b3d8649063c1b239d0d7e20f475d92ed8` |
| `color-integration-0842-gpu.log` | `977da514503040620a0227a0b3ff1bead2339783041dbe3564caba174717a2c6` |
| app APK | `65fe46b2e04eeaae3b7faf7938ce3eb4d3e681f7147fb3de4e63711c87b85baf` |
| androidTest APK | `1d2aa8a0bccfdcf8e5d054ecee2fbc40fdc3a0eb42baeea8b7246f534f3a2582` |

## Requirement checks and remaining work

| Requirement | Observed check | Remaining |
|---|---|---|
| Six saved slots and independent membership | Typed policy tests, native validation and539-test Android unit gate | Actual six-slot CRUD and accessible editing |
| Whole-tuple migration and archive preservation | Original /1 regressions, /2 partial merges, new unrelated-boolean preservation case | SAF import/export interaction and failure UI |
| Native TIMER/TRACK, generated colors and shuffle | Deterministic production-policy host tests within104 native checks | Actual render timing, metadata delivery and visible owner transitions |
| Rapid acknowledgement | Complete-snapshot guard tests and Android source integration | Device warning, dismissal and acceptance paths |
| HOLD and other sources stay intact | Three offscreen retained-frame GPU checks and retained native regression suite | Android handoff and source continuity, carried R13 gaps |
| Public production boundary | Source gate passed, source files unchanged during full gate | Final exact release-artifact and signing gates |

Independent Astra section6 round1 follows the committed checkpoint. Preserve this integration receipt and the failed gate. Later findings and corrections receive separate records. The app still runs installed stereo06f, with no new source or audio action on the phone.

## Clean source freeze,08:46 UTC

Gate113021dsr0 repeated the full gate on clean mobile7ac1e5546a58711a68c4b09bb8f18fe9f057df43/shared0ffd658d7f19e68180c2720e0500b23644619e90. Both app and androidTest APKs were built together. The worktree stayed clean and both full source manifests matched. Native104/GPU3/unit/lint/engine/release-helper/source gates passed. The exact source is under independent section6 review, not yet accepted by that review.

- Mobile archive: `72bb4928412f0b3b5c056277ad12c4808c3cf02a23aeae571c1374b00963fda6`.
- Shared archive: `c8a35001b41537def1b7d3777045ebe197526c73e423c1897d54c2ed9724915f`.
- App: `cb08c744d06f99fcc7d8d9ce0e74a95d45966549a14894ba7e00be5163e15ca5`.
- androidTest: `bead1ca1e2d3d943825a9ce36fcfe3dd99cadad880b6e761fe22023a6ba0226d`.
- Evidence prefix: `dev/scratch/mobile-expansion-20260908T001819Z/color-freeze-7ac1`.

Both APKs are retained and uninstalled. The08:46 bounded read-only phone preflight still found unrelated MEDIA playback, no AudioPolicy mix and baseline preference SHA256 `1f3fcf264ead0a0b3823a2c31f8c512842c9e25501c082ce659ed2b2fa3d1a72`. No install, playback, route or volume action followed.
