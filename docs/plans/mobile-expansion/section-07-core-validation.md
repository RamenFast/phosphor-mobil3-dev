# Section 7 core integration checkpoint

## Scope and result

The typed preset collection, strict portable codec and atomic native admission seam compile together. This is not the complete preset feature. Activity-owned application, persistence, SAF, collection controls, undo and device acceptance remain open.

The coordinator independently verified every entry in Bonehound's sealed release inventory and all seven live source hashes at 10:25 UTC. The original handoff remains unchanged at `/home/ben/.jcode/scratch/r16-core-20260908-SBOJyD/release/RELEASE.md`.

| Evidence | SHA256 |
| --- | --- |
| Worker source archive | `e15292c62890a169136cb8cfd8a875a6c4eb9e6ab48256bb632d64fca701bec6` |
| Seven-path release manifest | `75743fe1c5090d4eb5acdeeba4d0bf38e030ccf942a35197a9e4f24fa5f22f66` |
| Full release inventory | `8af423e6f73be9c3ed8a02834c0c965f5d023d57d635b5a5f2b6e7405dcd529e` |

## Failed gate and correction

Task `159335pkv8` retained the unmodified released core plus coordinator native/focus changes. GPU, native and boundary checks passed. Android test compilation failed because its API surface does not provide `Files.writeString`.

The fixture exporter now uses `Files.write` with explicit UTF-8 bytes. This preserves the codec's encoded content and does not change production parsing. The original released test and failed gate remain retained. Both repositories' source manifests were unchanged during that failed gate.

## Successful frozen gate

Task `277196bf2y` passed at 10:28:40 UTC. Evidence prefix:

`dev/scratch/mobile-expansion-20260908T001819Z/instrument-core-integration-1028`

Runner: `run-instrument-core-integration-1028.sh`, SHA256 `1925ed855d5b4875ab65cf6ec923494e10bb388a361d4c74d4335e3c730e8fab`.

| Requirement or seam | Observed check |
| --- | --- |
| Kotlin setup, codec and collection integrated with existing app tests | 587 JVM tests across 53 suites, zero failures, errors or skips |
| Native decoder, request identity, cancellation and receipt retention | 121 locked native host tests passed, including ten instrument tests |
| Kotlin-to-Rust wire agreement | Four retained real Kotlin encoder fixtures accepted by production Rust decoder |
| Existing retained rendering behavior | Three offscreen Vulkan GPU tests passed |
| Android JNI and render command compilation | Debug app and androidTest built together, checkEngine passed |
| Android static checks and production boundary | lintDebug, release helper packaging and source boundary passed |
| Frozen source integrity | Complete source file sets and hashes matched before and after in both repositories |

The GPU tests use an offscreen desktop adapter. They do not prove phone pixels, Android callbacks or physical scanout. JVM persistence callback tests do not prove Android filesystem rollback.

| Exact working-source gate output | SHA256 |
| --- | --- |
| Mobile source manifest | `2aab7a240c55fe3902de96bb3983eaba090da2b7721cdd5e271a35020b6520d9` |
| Shared source manifest | `581cd08f0a1fedcb8470e6848957edcec1017012bb772462e01280bc1d30acb9` |
| Results JSON | `4a4e27d389ae7374f0276fb56525d17f71b94fbeb5dd28ac7826e8bb83e102b1` |
| App APK | `f8001726a482b4341bf59bad19fe3e57a61b89f808b3c2471952a70b21217267` |
| androidTest APK | `9494d589b181ae4837fd8e5f3bcb13f8a7b4e719afc94ae1a01e7c1ead44f68b` |

These are frozen working-source artifacts based on mobile `c75f510c2772c325b34c97a742d76bfbca7c026d` plus the enumerated source delta, and shared `0ffd658d7f19e68180c2720e0500b23644619e90`. They are not a clean reviewed final freeze. Neither APK was installed.

## Remaining acceptance

1. Integrate Activity-owned preset apply and undo with serialized manual/import tuning changes.
2. Persist collection bytes with truthful failure recovery and inert SAF preview/import.
3. Provide curated and saved setup controls, explicit CRUD, association and modified indication.
4. Independently review the complete user-facing transaction, then gate clean reviewed source.
5. Exercise Android callbacks, source changes, HOLD, cancellation, persistence faults and accessibility on an identified authorized phone.

The S25 remained undisturbed. The ASUS was not enumerated at the last host-only check. Root stereo, SoundCloud, buffered source age and whole expansion acceptance remain open.

## Retained report outputs

Before a later build could replace Gradle's report directory, the coordinator retained the exact successful JVM result directory and lint XML. These supplement the already retained task logs and dual APKs under the same evidence prefix.

| Retained output | SHA256 |
| --- | --- |
| JVM results archive, `-jvm-results.tar.gz` | `5f17f23b6da57ad40a9dc4d3e98c8246c371f62aceead952e050cd5ff5133785` |
| Lint XML, `-lint-results.xml` | `caad108db1cff2195d47c9555af19c4ae386a8993ef1ebfa518d2eab79cb21ad` |
| Gradle log | `45448e61e12815511bdda5ce44073a1745dc53598e92e9138c6e2d7037b5c71b` |
| Native log | `8ab51115fbc8bea1bd81ac6d59139294088f3d536ac79acc8a06fcf97365e1cb` |
| GPU log | `0248b1c128ec65675399e21272e75bfcfe3dccd71d0fbf8c41dae70a38794fa2` |
