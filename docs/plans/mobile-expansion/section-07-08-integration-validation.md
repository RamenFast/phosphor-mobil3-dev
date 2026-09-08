# R16 correction01 and R17 integrated validation

## Outcome and boundary

Coordinator gate `783238n3mp` passed at 2026-09-08 12:00:31 UTC. All source writers had released before compilation. Both repositories stayed byte-identical throughout the gate.

This is a working-source integration check against mobile base `46d631b3d7636a9aa882f772a669b0f86ff93a8a` plus the recorded delta. Shared source remained clean at `0ffd658d7f19e68180c2720e0500b23644619e90`. It is not the final clean reviewed installation freeze. No APK was installed. No Android callback, SAF provider, physical audio or phone UI acceptance occurred.

## Changes and requirement-linked checks

| Requirement | Implementation and observed check | Remaining acceptance |
|---|---|---|
| R16 F1: preserve prior durable tuning after failed preset save | Automatic gain and lifecycle persistence use the shared workflow policy. Pure owner tests preserve the exact prior map. Activity wiring tests check both gain callers and lifecycle preservation. | Fault-injected Android preferences plus relay selection. |
| R16 F2: reject stale whole-settings documents | One owner ticket starts before the picker and follows provider decoding to main-thread acceptance. Actual archive decode barrier, later apply/edit/undo, ABA, cancellation and duplicate tests pass. | Android picker/provider and Activity recreation. |
| R16 F3: retain rollback uncertainty | Light and archive adapters forward typed restoration results to the same workflow latch. Failed rollback blocks later edits until explicit complete-save recovery. Owner and adapter tests pass. | Actual filesystem failure and durable recovery. |
| Compact instrument recall | LIGHT exposes RECALL INSTRUMENT without applying anything. Existing browser handles complete setups. Route and minimum-target source checks pass. | Phone reachability and accessibility. |
| R17 actual input observations | Existing mic, standard capture and root read boundaries publish owner-tagged format, route and finite-window measurements. Actual pure production aggregate tests pass. | Real recorders, routing and permission callbacks. |
| R17 native progress | Capture admission, render consumption, local output and relay observations remain distinct. Four new native signal tests pass within the full native suite. | JNI timing and live source replacement. |
| R17 truthful presentation | Seventeen pure observation tests cover precedence, invalid versus silent input, clipping, freshness, display state and unavailable values. Android/Compose compilation and lint pass. | Visible-only refresh, source recovery and display behavior on ASUS. |
| No acceptance inflation | Root 16 kHz mono and duplicated-mono normalization remain explicit. Missing local raw/original and relay original recorder formats remain unavailable. | Original stereo, SoundCloud, audibility, latency and R09 mixing remain separate. |

The [pure-owner receipt](section-07-correction-01-owner-validation.md) preserves its earlier 85-test scope. The original [R16 round1 report](critiques/section-07-round-01.md) remains unchanged at 7/10. R16 full round2 and R17 full round1 follow this checkpoint.

## Released writer provenance

Clover released all 22 R17 paths at 11:51 UTC. Coordinator verified their hashes before applying disjoint R16 adapters and stopped the worker.

Private handoff: `/home/ben/.jcode/scratch/r17-implementation-5b1be19-20260908T1126Z/`.

- Source manifest SHA256: `cbd96d120d5453466d2e45b768493312cd1c8f08dbbfc842d4923121b8440b26`.
- Source archive SHA256: `b930085d3812ad94570a61b940110d421bac88cc0640bd03df36cb08d5927461`.
- Owned-path list SHA256: `3255c1728fd0ff760bd9e1445369ffe0caced469cef16debb855efb43de15c23`.

The writer's 17 Kotlin tests and focused native checks are not additional Android acceptance. The [implementation map](section-08-signal-implementation.md) records its original source-only boundary.

## Gate and exact retained evidence

All paths below share the ignored prefix `dev/scratch/mobile-expansion-20260908T001819Z/signal-instrument-integration-1200`.

| Check | Observed result |
|---|---|
| Gradle `testDebugUnitTest` | 653 tests across 58 XML suites, zero failures and errors. |
| Locked offline Rust 1.96.0 mobile suite | 125 passed. |
| Shared retained-frame GPU suite | Three passed on the host RADV GFX1201 adapter. These are offscreen tests, not Android pixels. |
| `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, `checkEngine`, `buildReleaseRootAudioLauncher` | Passed. Both debug APKs were built in the same invocation. |
| Production source boundary | Passed. |
| Complete mobile/shared manifests before and after | Hash and path-list comparisons passed. |

Runner: `run-signal-instrument-integration-1200.sh`, SHA256 `887f9d22f225d26e740589530ba967207851eb5083b19665bb0d912c0a611a1e`.

| Retained suffix | SHA256 |
|---|---|
| `-app.apk` | `38cfee7545becf9d382e5a8f1fba3d58f28cce37a83b1cb84cf74cebba0c019a` |
| `-androidTest.apk` | `20cb69c8977f19524f490c925cdfbc676b81575e919d1578c3f9ff5ca74ddb6e` |
| `-jvm-results.tar.gz` | `16d06acf8fa5e2d6a17f54aa9cf807e2721b232105df535dca1c917d24ea2623` |
| `-mobile-source.sha256` | `3d54d2441aa8bdd464fcce66d40c5e63792eab792a9827142d4e0ff690e64755` |
| `-engine-source.sha256` | `581cd08f0a1fedcb8470e6848957edcec1017012bb772462e01280bc1d30acb9` |
| `-results.json` | `4a4e27d389ae7374f0276fb56525d17f71b94fbeb5dd28ac7826e8bb83e102b1` |

The archive contains actual JUnit XML and HTML/XML lint reports. Adjacent logs retain exact commands, compiler warnings and source checks. Documentation added after the gate is not part of its source manifest.

## Failed predecessor and correction

Gate `543404bsmw` failed six source-linked assertions, not Android compilation. Its `signal-instrument-integration-1143-jvm-results.tar.gz` is retained with SHA256 `41ac63fe758e3b6c61bddcd5f8de16281b533df281e6a95a11d78529ceef5707`.

The revised tests locate the argument-bearing source-selection function, verify exact local-open identity and publication order, and inspect the guarded gain helper. Signal source lookup now supports repository-root and Gradle app working directories. Existing lifecycle, no-mic-stop and local-gain obligations remain. No production behavior was changed between these two gates.

## Follow-through

Retain original reviews. Run independent full-section reviews against the committed integration. Correct concrete findings before final clean dual-artifact freeze. ASUS was absent at the last host-only enumeration at 11:41 UTC. Leave the S25 undisturbed while Ben sleeps. Device acceptance and the whole expansion remain incomplete.
