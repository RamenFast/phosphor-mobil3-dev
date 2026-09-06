# Phase 10: stable beam and live-source display ownership

Status: **VERIFY**. Frozen offline gates and separate source and genuinely source-first intent reviews passed at their stated scope. Exact committed packaging, installation and Android acceptance follow separately.

- B IDs: B5 and B11. Private issue reference: #5.
- Runtime baseline: `992c08a6ab836e6af25896c87ce8763817cabfea`.
- Reviewed HEAD: `2a5e15e0854c49b85b95d6b055d256f42cd8adc6`, followed by 31 frozen source, test and spec paths.
- Independently reviewed freeze: `root-combined06-pre-gate.md`, SHA-256 `30a5263882f3c65c951dfb4e8a359ba816a05f365a194961d0b53ec6669b1298`.
- Hash-only manifest SHA-256: `30f2412b702470545d583a09d8a9a03b52e351378be158110e43a34382a6f3f1`.
- Evidence home: ignored `dev/scratch/pre-v2-20260829T072841Z/phase-10/`.
- No Phase 10 installation, device input, playback, timeout, brightness, permission or service operation occurred.

## Implemented contract and requirement-linked evidence

| Outcome | Actual production and observed offline checks | Required live evidence |
|---|---|---|
| B5: chrome does not drive beam intensity | Removed the Compose bloom driver and bloom-only overscroll/displacement, JNI command and native state/multiplier. Real reveal/scroll owners and 0.40 scrim remain. Three retirement source guards and two native deposit regressions passed. The renderer calls the exercised production transform. | Fixed system brightness, nondefault beam setting, settled luminance through chrome/settings cycles. Separate modal scrim and cold-start warmup. |
| B5: chosen tuning survives surface changes | Existing computer and beam parameter remain outside surface lifetime. Independent review traced Kotlin/JNI, native deposit, shared DSP and actual GPU shaders. Same-size GPU resize preserves energy textures. Changed-size resize recreates them. | Actual SurfaceView/PiP recreation and settled luminance. Do not claim preserved historical pixels across changed dimensions from a transform test. |
| B11: wake belongs to real sources | One lazy non-reference-counted screen lock per actual playback/capture owner. Activity owns its mic lock. Capture's Media3 mirror cannot acquire another lock. Wrapper/policy tests cover idempotent cleanup, replacement, errors and destroyed-owner rejection. | PowerManager success, source stop, pause where applicable, end, revocation, failure and destruction. Confirm sleep eligibility, not just a policy boolean. |
| B11: local terminal truth releases wake | Real post-EOF empty output-ring pop and stopped-output error feed the existing serial event poll. Exact Open/latest-request main publication retains ENDED/error until successful reopen. Paused tails and silence remain distinct from drained output. Existing queue continuation remains. Seven native terminal, four activation, one close and related Kotlin owner tests passed. | Unknown-duration/early termination, paused-tail resume, actual Oboe and Media3 callbacks, same-path seek, queue transition and audible latency. |
| B11: remote wake requires current media | Each native session owns valid A/G receipt and freshness. W/K/M traffic, lifetime counters and malformed frames cannot renew it. Silent PCM and valid geometry-only media qualify. Existing watchdog separates media stall from socket death. JNI status reaches the existing service wake consumer. Eight actual producer-helper cases, the existing watchdog table and Kotlin consumer/wrapper cases passed. | Prior-media reconnect with control only, media stop while heartbeats continue, silence, geometry-only flow, recovery, retarget and disconnect with actual wake state. |
| B11: actual capture and mic lifetimes | Successful recorder/projection start acquires wake. Real read loops dispatch negative/thrown failures once to guarded main retirement. Current mic failure clears only its live face and reports a retry remedy. Cancelled file/folder/capture requests cannot retire the retained recorder's failure authority. Runtime saves require actual recording. | Android consent, AudioRecord/MediaProjection delivery, cancelled picker followed by mic failure, replacement, visible remedy and persistence. |
| B11: main and PiP mirror source truth | Existing start/focus/surface/source callbacks and foreground heartbeat reassert only actual source state. Activity stop clears visible flags without destroying a service-owned live lock. Neither linger nor PiP preference grants wake. Deterministic visibility and source-wiring checks passed. | Main and PiP stay awake beyond a captured shortened timeout, then become sleep-eligible after source stop. Restore exact original timeout/screensaver settings. |

The 12 selected engine tests include both deposit regressions. Kotlin's WakeOwnership suite contains 29 tests, RemoteLinkTruth 20, PlaybackTruth 15 and BrightnessRetirement 3. Aggregate counts do not replace the requirement map or real platform checks.

## Preserved correction and review history

1. The initial gate compiled Android/JNI but failed two obsolete Phase 9 source assertions among 328 cases. Test-only correction retained the volume heartbeat and ROOM scroll requirements while retiring the bloom expectation.
2. The next gate passed Android and selected engine tests but failed Rust formatting. Root formatting and a new frozen gate passed 328 Android cases and 12 engine cases.
3. Independent source and intent inspection found missing local terminal-to-wake publication and a stale mic failure face. Actual terminal/ring/output and guarded Activity publication corrections followed. The next gate passed 341 Android and 24 native cases.
4. Source review found attempted selection still invalidated a surviving recorder's failure callback. Another source trace found historical remote counters plus control traffic could hold wake. Root corrected mic authority and harness capture timing. A bounded worker corrected the actual remote producer/status chain and per-session optional loudness reset.
5. A preparation assertion rejected rustfmt's expression-only brace changes. Its outer shell lacked fail-fast handling and continued into attempt05. That gate passed 348 Android and 33 native cases, but the failed preparation was not accepted as the final gate. A subsequent preparation stopped correctly on an encoding-sensitive Ruby comparison. Root fixed binary comparison, retained both failures and inspected the full two-file formatting diff.
6. Final fail-fast attempt06 verified the written freeze before execution and passed. Both independent reviewers rehashed all 31 source paths and all 823 gate input rows.
7. Earlier fresh-review attempts disclosed incidental embedded tests or swarm-metadata results before their maps. Those reports remain invalid for ordering. The final isolated reviewer used a filtered production reader, including shared GPU shaders, and direct coordinator routing. Its substantive map and initial verdict were frozen before any tests, retained results or prior reports. Separate evidence reconciliation preserved that map unchanged.

No earlier failure or invalid review is retroactively relabelled a pass. The final reviews found no remaining concrete blocker in their bounded source scope. They do not close Android acceptance.

## Final frozen gate and identities

Root's sole build owner used offline resolution and two Gradle workers:

```bash
./gradlew --offline --no-daemon --max-workers=2 \
  :app:testDebugUnitTest --rerun :app:lintDebug :app:checkEngine :app:cargoBuildDebug
```

Attempt06 completed at 2026-09-06 03:13:29 UTC, exit 0: **348 Android unit tests, zero failures, errors or skips**. Selected native tests passed **33/33**: 12 engine, 7 terminal, 4 activation, 1 close, 8 remote media and 1 watchdog. Whole bridge_core and its socket test were not selected.

Lint retained **16 warnings and zero errors**, including the disclosed `WakelockTimeout`. The lock is intentionally source-owned rather than timer-owned. Unit tests reran. Kotlin compilation, lint and cargoBuildDebug were UP-TO-DATE in the final gate, not a clean rebuild or runtime JNI load. checkEngine, format, protected archive and full before/after identity checks passed.

| Evidence | SHA-256 |
|---|---|
| Both 823-row full input inventories | `ce7269b0da87f2f419d32ed76a58c42fd4f0121eaf57e1f073d2b0de83f5830a` |
| Attempt06 Gradle log | `c69e4d68fef1a7c362637be910162d64226581129d418242ab42256740ec44dd` |
| Android test summary | `4397cdb7b078dfdc5ad6d06d11b915dbee0c69e4004b05391ce428e0e0f1343d` |
| Lint XML | `d1bda08ed0d7ea123419fab7d15a482e77377ca2c32adfe2e6bd8c1cfeb7038a` |
| Final source review | `b6a601932069fd56bfae9e7409397c7a11708de133e06a2dcf3e13bce4bf3d6a` |
| Immutable pre-evidence intent map | `94681518201106fa3c2233463ad46a9c3def5b75d38ec3d24b8653d3a02f106f` |
| Final separate intent reconciliation | `88bcb8af9fe5909847236efd69c7fb524f96019a1b7f9ffac80b9361d2c63235` |

Native logs, exact source maps, original failures and review identities remain in the ignored evidence home. Protected historical files remain byte-identical. Shared-source work was read, not edited or reset by this phase.

## Acceptance, rollback and next step

- **B5: VERIFY.** Driver retirement and bounded source review passed. Settled physical luminance remains unmeasured.
- **B11: VERIFY.** Real source owners and deterministic checks passed. Android wake, timeout and sleep eligibility remain unmeasured.

The exact Phase 9 APK is retained and uninstalled. Phase 7 remains the last verified installed debug baseline. Existing preference backups and rollback artifacts are preserved. No fresh phone backup is claimed for this phase.

Next: commit only the reviewed sources and evidence, build the exact clean-snapshot debug APK, and verify source/package/signer identities. During an undisturbed authorized phone window, preserve current preferences and display settings, install through dev/pm3 with readback, then execute the named acceptance matrix. Restore exact timeout/screensaver values in a trap and at phase end. Revert this phase's source commit and use the retained prior artifact if rollback is required. No release signing, publication, final B-card closure or drift reset is included.

## Packaging-boundary name correction, 2026-09-06 03:51 UTC

The precommit privacy scan found a false positive where a test method's trailing name matched an SDK constructor detector. Root changed only that declaration name, not the test body, production, specs or scanner. Reversing the one identifier replacement reproduces the exact independently reviewed test hash. All other 30 reviewed input hashes remained identical. This mechanical correction is root-verified, not a new independent review. The existing source and intent maps still describe identical production and equivalent test behavior.

Correction07 freeze SHA-256 is `dd18ea9534e50dffe4ea4b5076203ca44d66dd2904ba4ec554465352a139c1d4`. A fresh full attempt07 completed at 03:51:23 UTC, exit 0, with 348 Android unit cases and 33 selected native cases passing. Lint, JNI/checkEngine, format and protected checks passed. Its full before/after inventory stayed identical. The unchanged privacy scanner passed after the rename. The first failed scan remains preserved separately.

| Current execution evidence | SHA-256 |
|---|---|
| `implementation-attempt07-before.sha256` | `e95d555d39f7fa54a3eafca1e7122a6c8f3890dfa761d6658ea2a537119ed02f` |
| `implementation-attempt07-gradle.log` | `91f621f59a1ced37e780cf6d9856580197e2eeb5b4c4c005debdbaf4d43b3c9a` |
| `implementation-attempt07-tests.txt` | `4397cdb7b078dfdc5ad6d06d11b915dbee0c69e4004b05391ce428e0e0f1343d` |
| `implementation-attempt07-lint.xml` | `d1bda08ed0d7ea123419fab7d15a482e77377ca2c32adfe2e6bd8c1cfeb7038a` |
| `precommit-source-boundary-correction07.json` | `e25d43f97d3a903b1bb329a46004d7e4ce5486834375fbd2deb46bb509d62e59` |

This supersedes only the executable test-name identity for packaging. It does not enlarge the source approvals or close Android acceptance. The exact committed build uses attempt07 as its gate.
