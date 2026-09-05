# Requirement-to-check map

All rows are **proposed future execution**, not completed implementation.
Each task links to its exact owners, runnable gate, expected result and phase rollback.
Existing source facts are pinned separately in [SOURCE-INVENTORY](../context/SOURCE-INVENTORY.md).

| Task | Human requirement | Host/source gate | Real acceptance or limit |
|---|---|---|---|
| [00.1](../phases/00-baseline.md) | Do not lose unfinished repairs | 21-ID receipt reconciliation, exact hashes, protected archive | All inherited final live/visual evidence must exist |
| [00.2](../phases/00-baseline.md) | Future executor need not guess old owners | Recompiled paths/manifests, independent document review | No runtime activation |
| [01.1](../phases/01-test-boundary.md) | A smoke PASS means this run passed | Fake-ADB stale/error/hash cases, canonical APK unchanged | Fresh matching real smoke later |
| [01.2](../phases/01-test-boundary.md) | Test real Android without a product admin surface | Test-only dependencies, instrumentation build and manifest | InstrumentHarnessTest, exact APK readback |
| [02.1](../phases/02-source-face.md) | Source labels do not invent playing state | SourceFaceTest all source pairs, stale order, failed start | Inert until 02.2 |
| [02.2](../phases/02-source-face.md) | Late callbacks cannot resurrect a source | Projection tests, production adapter wiring, native compile | SourceFaceIntegrationTest and D1-D3 |
| [03.1](../phases/03-reader-health.md) | Distinguish silence, error and cancellation | ReaderHealthPolicyTest plus existing ReaderStop/MicHandoffPolicy tests | Inert until 03.2 |
| [03.2](../phases/03-reader-health.md) | Dead reader errors and cleans up only itself | Actual loop adapter ordering/error tests, native compile | ReaderLifecycleTest, D1/D7, injected-error limit recorded |
| [03.3](../phases/03-native-boundaries.md) | Decoder failure is not clean EOF or explicit stop | Production-linked playback_events, full-ring regression, shared/desktop compilation | Exact D2/D3, one destructive consumer |
| [03.4](../phases/03-native-boundaries.md) | Replaced provider work cancels without stale publication | DocumentCancellationTest, API-29 signature and lint | DocumentCancellationIntegrationTest and D2 |
| [03.5](../phases/03-native-boundaries.md) | Remote teardown cannot hide unresolved resources | cfg-free bridge_core and relay blocked-worker fixtures | RemoteLifetimeTest and D8, no host-remote false claim |
| [04.1](../phases/04-instrument-evidence.md) | Detect dark, rotated or wrong-channel beam | instrument_scenes, existing golden replay/cross-snapshot, negative fixtures | Real GPU required, no skipped visual PASS |
| [04.2](../phases/04-instrument-evidence.md) | Beam follows the actual decoded/output source | instrument_audio, real ring/decoder/callback helper | AudioBeamIntegrationTest and D2/D6, no physical latency claim |
| [05.1](../phases/05-settings-survival.md) | Preserve portable values and exclude private runtime state | InstrumentSettingsContractTest and existing settings suite | Inert until 05.2 |
| [05.2](../phases/05-settings-survival.md) | Failed import never masquerades as successful restore | SettingsTransferTest interruption/failure cases | SettingsSurvivalTest and D4, compatible update/rollback |
| [06.1](../phases/06-controls.md) | Controls are understandable and operable | Instrumentation compile and real production actions | InstrumentSemanticsTest and D5 with real accessibility use |
| [06.2](../phases/06-controls.md) | Immediate readable chrome leaves beam tuning intact | ChromeContrastTest, accepted frame/clock assertions | InstrumentFeedbackTest, D5/D6 and separate Ben visual approval |
| [07.1](../phases/07-lifetime-performance.md) | Shutdown/recreation does not hide surviving owners | Remote/relay blocked-worker tests, ownership table | InstrumentLifetimeTest, D7/D8, wedged-GPU risk explicit |
| [07.2](../phases/07-lifetime-performance.md) | Performance claims reflect measured device behavior | PerformanceComparisonTest rejects noisy/mixed inputs | InstrumentPerformanceTest, D9, five paired baselines |
| [08.1](../phases/08-delivery.md) | Distributed source rebuilds and excludes private records | Existing release fixtures gain extraction/rebuild/privacy cases | Missing cache/signers unavailable, no publication |
| [08.2](../phases/08-delivery.md) | Delivery names accepted bytes and recovery | Full regression, exact artifact scan, fixed scoreboard, sealed receipts | D1-D9, API branch evidence, restoration, separate release approval |

## Cross-cutting requirements

| Boundary | Explicit check |
|---|---|
| Ben's media remains uninterrupted | No device/GUI/audio/service action in planning. Runtime/device commands require later authority and DEVICE guards. |
| Product scope stays intact | Independent intent review against vision/spec, no new service/API/account/core feature. |
| No false-green substitutions | Test count/skip inspection, exact source/artifact review and distinct evidence levels in EVIDENCE.md. |
| Private data remains local | Sanitized tracked receipts, public content manifest review, source/artifact boundary scans. |
| Recovery remains possible | Reverse-order owned commit list and compatible APK/export per phase, no reset/clean/uninstall. |
| Protected history remains intact | Canonical protected SHA256SUMS checked at entry and closeout. |
| Source and ledger stay aligned | Spec-before-code, per-task observed results, final ASK/FEEDBACK/HANDOFF update and local memory. |

No row may close solely because a command returned zero. Confirm the named expected behavior and evidence identity.
