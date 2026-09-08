# Startup recovery and signal correction validation

## Scope and original reviews

R16 full round2 and R17 full round1 reviewed mobile `15e4072e86054af15f483f395d5adc8bcb09803f`, shared `0ffd658d7f19e68180c2720e0500b23644619e90`. Both scored7/10. Their reports are retained unchanged:

- [R16 round2](critiques/section-07-round-02.md), SHA256 `605d14fedadd6aeb33c68fc59a7741206cea3f1144e8b561b7146796e1403b15`.
- [R17 round1](critiques/section-08-round-01.md), SHA256 `ac7cd8be899f75f42d1c7a737c424c1f0b62db81718b094f6750112a3fe14645`.

Sunflower and Hibiscus released source, sealed their receipts and stopped. Corrections follow separate [startup](section-07-correction-02-design.md) and [signal](section-08-correction-01-design.md) designs. Neither original review assesses these later changes.

## Findings mapped to corrections and checks

| Finding | Correction | Observed evidence and remaining limit |
|---|---|---|
| R16 F3 startup | Construct the existing workflow before `restoreTuning`, so restore-time light failures reach the shared owner. | Actual Activity ordering assertion and fresh production workflow plus real SettingsWriteOwner failed-commit/rollback test pass. Apply/import/manual/automatic save remain blocked until retry. Android filesystem fault injection remains open. |
| R17 F1 stale local progress | Same-owner counter deltas need a valid bounded observation interval. Record the earlier timestamp as an explicitly labeled age upper bound. | Long hidden pause, repeated counts, new progress, failed snapshots, regression and backward-clock fixtures pass through actual JSON join and presenter. No producer timestamp is invented. |
| R17 F2 relay positive rail | The accepted s16le path uses a named PCM16 meter threshold for both rails, preserving audio floats. | Actual SessionMedia decode-to-meter test distinguishes32767,32766,-32768,-32767 and zero in independent channels. Original recorder format remains unavailable. |
| R17 F3 root units | Root counters say received PCM/progress observations, not completed AudioRecord calls. | Positive PCM plus independent progress fixture shows two observations and no completed-read claim. Real recorder calls are not inferred from protocol traffic. |
| R17 F4 unowned tap and extras | Diagnostic tap peak is unavailable without owner/age. Foreign extras require a matched selected input. Playback kind and local/relay identity gate transport detail. | Actual presenter/join tests reject old transport and numeric extras. Activity assertion preserves exactly one normal stats acquisition. Normal scope display is unchanged. |
| R17 F5 picker identity | Picker launch keeps the current diagnostic kind. Accepted file and folder results explicitly select LOCAL. | Actual callback source assertions cover cancellation-before-selection and typed successful adapters. Android picker lifecycle remains unrun. |

The section9 pure settings owners are present in this gate, with their15 tests. Compose integration remains pending. These checks do not finish R13 source-age, R09 mixing, original root stereo or SoundCloud acceptance.

## Frozen gate and retained evidence

Final task `4931158kjm` passed at12:29:20 UTC in66.96seconds. Base mobile is `f504b3fa961275ec6654e65a00e40b4ab497b0db` plus the recorded correction delta. Shared source remained unchanged at `0ffd658d7f19e68180c2720e0500b23644619e90`.

- 675 JVM tests across59 actual XML suites, zero failures/errors.
- 126 native host tests, including real wire decode-to-meter integration.
- Three host offscreen retained-frame GPU tests.
- Android/Kotlin/JNI compilation, lint, both debug APKs, engine/release-helper and production source boundary passed.
- Complete source path lists and hashes matched before and after the gate in both repositories.

Runner `dev/scratch/mobile-expansion-20260908T001819Z/run-signal-startup-correction-1228.sh` SHA256: `87e282b7d2f86dc702bfd063aa880bd4834f65e4c19fbbbb5cf51a2602e14252`.

Retained files share prefix `dev/scratch/mobile-expansion-20260908T001819Z/signal-startup-correction-1228`:

| Suffix | SHA256 |
|---|---|
| `-app.apk` | `6f54f9e71d193f5606d7515f91eeaf92a4e2f2f464e23bb55ef30557a9ebaa6a` |
| `-androidTest.apk` | `5d01be8a6f556fb760549bd588b4b68a80dbf50ff8fd180a40c528b1a748e822` |
| `-jvm-results.tar.gz` | `df77e0da6cce034ba8448b9ccb3731a147fddb31d7260878d863f953e18cc320` |
| `-mobile-source.sha256` | `33ce0c21aa91ab02b21602b317b0f64491dc5522aa6833d9824c2d3dd364f012` |
| `-engine-source.sha256` | `581cd08f0a1fedcb8470e6848957edcec1017012bb772462e01280bc1d30acb9` |
| `-results.json` | `4a4e27d389ae7374f0276fb56525d17f71b94fbeb5dd28ac7826e8bb83e102b1` |

The preceding1224 gate failed because the new manual-edit test did not expect the owner's intentional exception. Its failed XML is retained as `signal-startup-correction-1224-jvm-results.tar.gz`, SHA256 `bbb43584d4bd7eb6dd0b3d30a500d2337d6774f8527abd5f08ee710f73c920bf`. The test now asserts that rejection and unchanged state. Gate1226 then passed674 tests. Full-report reading prompted one additional transport-extra fence and test before final1228, which passed675. The1226 APKs and XML remain separately retained.

## Follow-through and phone boundary

This is working-source verification, not the final clean reviewed artifact freeze. Both APKs were built together and retained, not installed. Documentation added after the gate is not part of its source manifest. Independent R16 round3 and R17 round2 remain required. Host-only ADB/USB enumeration at12:28 found only the S25 wireless endpoint and no ASUS. No phone was targeted. Leave S25 undisturbed while Ben sleeps.
