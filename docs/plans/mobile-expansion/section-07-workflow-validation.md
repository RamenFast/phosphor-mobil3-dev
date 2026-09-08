# Section 7 workflow integration receipt

## Source and ownership

Microbe explicitly released fourteen source paths at11:05 UTC on2026-09-08. The coordinator verified the exact manifest and report before acquiring the sole build slot. The worker then stopped. No source changed during the full gate.

This integrates the user-facing workflow over core/native commit `019c0da`. The build used base `f4eff17` plus the fourteen enumerated source changes. It is a working-source integration gate, not a clean reviewed installation freeze.

- Writer manifest: `/home/ben/.jcode/scratch/r16-workflow-20260908/release/changed-paths.sha256`, SHA256 `f20dc827b8d2da59269bdc56817a5a3990015ed97bbebe0128d951ff313e041a`.
- Immutable writer report: same directory `RELEASE.md`, SHA256 `a939c83404485aa620976eae66ad02c0b474aa8cc7af5504e042571e6756bf1a`.
- Writer source archive SHA256: `4b93487c03f411d88ae56b1f36cd16f9a77adb68d09286b0872762645ae839dd`.
- Shared engine remains `0ffd658d7f19e68180c2720e0500b23644619e90`.

## Changed behavior and checks

| Requirement | Production path and observed check | Remaining boundary |
| --- | --- | --- |
| One atomic apply and one undo | Existing native request admission plus Activity-owned `InstrumentWorkflow`. Production owner fixtures cover committed cancel before a later edit, queued cancellation, stale replies, undo and capability rejection | Actual JNI scheduling and UI/native frame coherence on Android |
| Authored setup and modified association | Snapshot reads manual gain, local auto-gain, shared focus and complete typed setup. MainActivity compiles against actual Android APIs. Core and workflow tests pass | Actual displayed controls and audio-only relay continuity |
| Durable settings and recovery | Typed persistence failure, exact prior-value restore, failed-rollback latch and explicit RETRY SAVE CURRENT. Production owner/store tests simulate in-memory mutation and failed rollback | Real SharedPreferences filesystem durability |
| Inert saved records and portable documents | Dedicated store, stable-ID CRUD, strict codec, bounded stream reader, explicit import conflicts and document tickets. Production tests cover corrupt bytes, stale preview, cancellation and exact absence/string restoration | Actual SAF providers, process recreation and document results |
| Rapid timing and HOLD boundaries | Whole-light guard reused, safe timing is modified rather than exact recall. Source adapter publishes no source, pause or Surface setters | Actual guard UI, held pixels, inspection and source continuity |
| Accessible browser | Settings and compact entries, curated/saved browser, CRUD, APPLY/UNDO, import/export and recovery compile and pass lint | Large-font layout, touch targets and screen-reader behavior on device |

The writer's sealed actual-source host run passed86 tests across ten suites. Those include37 new tests and unchanged core/light/settings coverage. Its PSI syntax checks remain supplementary, not Android acceptance.

## Coordinator full gate

Task `580618965w` ran from11:06:20 through11:07:57 UTC, exit0, duration96.82seconds. All six recorded gate exits are0. No installation or phone action occurred.

- **624 JVM tests across57 suites**, zero failures, errors or skips.
- **121 native host tests** passed.
- **Three offscreen Vulkan GPU tests** passed through the retained-frame public API. These are not phone compositor tests.
- `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:checkEngine` and `:app:buildReleaseRootAudioLauncher` passed through the Gradle wrapper.
- Production source boundary passed.
- Complete mobile and engine source file lists and hashes matched before and after the gate.

All retained paths below share the ignored prefix:

`dev/scratch/mobile-expansion-20260908T001819Z/instrument-workflow-integration-1107`

| Suffix or runner | SHA256 |
| --- | --- |
| `run-instrument-workflow-integration-1107.sh` in the same directory | `63971a51e18a6c43d0d688fec2774a11427d924deb24a06b2b6766f1b9291725` |
| `-mobile-source.sha256` | `639c7a0e5fd5a4fdbc911d7070a7a50b43680b914e25bf9c65c2f190004b068c` |
| `-engine-source.sha256` | `581cd08f0a1fedcb8470e6848957edcec1017012bb772462e01280bc1d30acb9` |
| `-results.json` | `4a4e27d389ae7374f0276fb56525d17f71b94fbeb5dd28ac7826e8bb83e102b1` |
| `.apk` | `7d3abbcee4ec745ace2c8b7bdff4ef5f2341f24e7e4624ff8ca75d42642a2349` |
| `-androidTest.apk` | `c2fb459efdc9b116a211f5838a984c7dd08a8deacdde540367a69a863a54c159` |
| `-jvm-results.tar.gz` | `364c9d4affab8e62da4c78cb214d29bedbcf33b848df8ebeda3dae1d1e5a5f9a` |
| `-lint-results.xml` | `9f9f238ca9d4399742ce51d2ee2ea7d5f206ed4a0874c13bf79e8a45e56a31f7` |
| `-gradle.log` | `4f68ba21236ee80a5b713a2b18f830416f39af6a2c2dc96213b8c2d98573a940` |
| `-native.log` | `e3fe0203483828a905b2ad0cd8cf2e4fbe0a6465f00458e5a8e566d88b15ecdd` |
| `-gpu.log` | `1f203fc3ad0bb8511f2ea71be41e39622edd46b55d1b7c4e05c5c48268827080` |

## Acceptance still open

Independent full-section R16 critique follows this checkpoint. Native execution on Android, real persistence recovery, SAF, CRUD/apply/undo UI, source/HOLD continuity and accessibility remain unaccepted. The final reviewed clean source must build both APKs together again before guarded installation.

Host USB/ADB enumeration at11:00 found no ASUS. Local ADB mDNS enumeration at11:03 found no advertised services. The S25 remains undisturbed while Ben sleeps. No source review or host test substitutes for these unavailable device checks. Root stereo, SoundCloud, audibility, latency, R09 mixing and R13 buffered source age remain separate open work.
