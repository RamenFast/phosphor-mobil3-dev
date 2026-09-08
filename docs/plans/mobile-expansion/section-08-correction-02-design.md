# Signal correction02: retirement and read-progress freshness

## Source findings and intended change

Tulip's independent round2 reads immutable6355074. Its original report remains unchanged.
The coordinator confirmed two finite source traces before this correction.

1. CaptureService sets cleanedUp/running before asynchronous cleanup but retains the old FLOWING status until cleanup ends.
   The diagnostic adapter currently selects RUNNING before considering retirement.
   Fresh old samples can therefore mask a requested stop or a known reader failure.
2. A zero-length read sets lastReadAt but not lastPositiveAt.
   The presenter currently calls that read progress indefinitely, without checking its age.

## Contract before runtime edits

CaptureService will publish a small diagnostic-only retirement receipt synchronously before starting cleanup.
It contains the requested terminal health and reason. It does not broadcast idle or acknowledge source release early.
The existing stop future, reader stop/join, ring ownership, request-specific replies and service cleanup stay unchanged.

The actual adapter will prefer this receipt over old FLOWING status and root helper observations.
A known failure or lost permission remains visible while cleanup is pending.
An ordinary pending stop says Stopping. Completed cleanup says Input ended.
A cleanup failure says Cleanup unconfirmed with its observed error.
Retirement never admits old measured levels as current flow.
The receipt belongs to the existing service owner and cannot carry over to its replacement.

For no-positive-sample input, read/progress timestamps use the same1500ms monotonic freshness limit as positive input.
A recent zero read says No samples observed · read loop progresses.
An expired, negative or future timestamp says Stale measurement · reader health unavailable.
No completed read says Starting · waiting for input. Absence does not diagnose a dead reader.

## Checks

- Exercise the production retirement decision through pending, successful, failed and retried cleanup futures.
- Combine retirement states with fresh nonzero and silent windows in the actual presenter.
- Preserve failure/permission precedence during delayed cleanup and replace owner without reusing the retirement receipt.
- Check zero-read progress at0,1500,1501ms age,10000ms, clock regression and fresh explicit progress without positive PCM.
- Assert the actual service records retirement before cleanup thread creation and uses the production decision in signalObservation.
- Run full frozen Android/unit/lint/dual-APK/native/GPU/source gates after editing.

Pure decision tests and source wiring checks are not Android callback or physical device acceptance.
No new reader, timer, source control, root protocol, audio queue or device action is authorized by this correction.

## Coordinator gate observed13:15UTC

Gate2634988b58 exited0 after81.74s. All716 JVM tests across61 suites passed, including five new correction tests.
The126 native and three offscreen GPU cases passed. Android/Kotlin/JNI compilation, lint, dual APK builds, engine and source boundary passed.
Complete mobile/shared source and path manifests were unchanged throughout the gate.
The baseline was374326fb29580c60c4fe7d95e305d6ac04f78964 plus the four correction runtime/test paths and this prewritten contract.
Shared remained0ffd658d7f19e68180c2720e0500b23644619e90.
The source-wiring fixture is supplementary. No actual Android stop callback, recorder failure or device test ran.

Prefix: `dev/scratch/mobile-expansion-20260908T001819Z/signal-retirement-integration-1314`.
The original settings gate and all original critique reports remain unchanged.

| Receipt | SHA256 |
| --- | --- |
| Runner run-signal-retirement-integration-1314.sh | 655d6d09a4698cd50456528e7978495c71a9913601fb00f4fc1160a54fd0bafd |
| Retained app APK | 7cacce46d53d87c917c46d7f216a044596da747a638fd682d1253b863c420871 |
| Retained androidTest APK | 42077d8d2a88a14f0b8e7a9ca1c7fc2783c8ac7feb3138d64a18466dd205cf64 |
| Retained JVM XML archive | 714465cca4cf40552355ada390997fd6ccfb71712cd840a0fc91af86aea1cc09 |
| Mobile source manifest | 472ffb9afc38d9b643cc973d2f0476a2087651be5537029033283117697c7b66 |
| Shared source manifest | 581cd08f0a1fedcb8470e6848957edcec1017012bb772462e01280bc1d30acb9 |

The retained.sha256 file inventories the artifacts and result envelope. Both APKs remain uninstalled.
