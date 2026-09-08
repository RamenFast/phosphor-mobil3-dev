# R13 HOLD integration evidence

## Status

At 2026-09-08 07:37 UTC, the coordinator's frozen working-tree integration gate passed. This is not independent R15 approval, a clean install freeze, or Android device acceptance. The last installed app remains the separate `06f84e2` stereo checkpoint.

Hatchling released the bounded source implementation and stopped. The original [writer handoff](section-05-writer-handoff.md) is retained unchanged. Its SHA256 is `a12ee939955dca74577f551561f9a923129b7ffd621fbafc103199e66bbf9952`. Its original 25-path manifest SHA256 is `0c6fb72b4cdc8c9cde77de0a1e7f34934b55e94dffc909b7b19f9651f235ebeb`. These describe the writer baseline, not the coordinator's later corrections.

## Iterations and observed results

The first frozen gate, task `5737253okz`, passed 83 native tests and two actual offscreen GPU tests. Android compilation passed, but two of 505 unit tests failed. The overall gate failed. Source manifests remained unchanged.

Investigation found more than stale test text. The display-only label reused observed transport-glyph telemetry, and selected capture metadata could imply a live reader. The coordinator first clarified the contract, then separated display and transport labels and receipts. A live-source suffix now requires observed playback or an active reader. Production `PauseDisplayPolicy` drives both controls and tests. Seven focused tests and two archive tests were added. Existing transport and capture assertions were retained and strengthened.

The second frozen gate, task `966199akcz`, completed in 68.03 seconds with exit 0:

| Check | Observed result | Scope |
|---|---|---|
| Android unit tests | 514 tests, zero failures, errors or skips | JVM tests and source assertions, not Android callbacks |
| Native locked/offline tests | 83 passed | Native host behavior |
| Actual offscreen GPU integration | 2 passed | Public renderer API, synthetic beam textures |
| Android lint | Passed | Static Android analysis |
| App and androidTest build | Both passed in one Gradle invocation | Working-tree artifacts, not final clean freeze |
| Engine and release root launcher checks | Passed | Compilation/integration |
| Production source boundary | Passed | Source-only production boundary |
| Both complete source manifests | Identical before and after | Includes tracked and nonignored untracked file lists |

The offscreen adapter was AMD Radeon Graphics, RADV GFX1201, Mesa 25.2.8, Vulkan. The driver emitted its nonconformant/testing-use warning. No Android GPU, compositor or physical display was exercised.

The first pixel test proves exact retained/live equality, held-image survival across destructive live resize and mutation, changed inspection pixels, and exact reset-to-fit recovery. The second proves transparent emission rather than a faded opaque rectangle, premultiplied channels, opaque destination alpha, and exact transparent recovery. Only synthetic test textures are read back. Product rendering adds no CPU image readback.

## Exact gate evidence

Private retained prefix: `dev/scratch/mobile-expansion-20260908T001819Z/hold-integration-0736`.

| Artifact | SHA256 |
|---|---|
| Runner `run-hold-integration-0736.sh` | `2e6d1a090565e69c42b0b8af262994c4eac926e4b2607e318a5e03a3145b1a07` |
| `-results.json` | `4a4e27d389ae7374f0276fb56525d17f71b94fbeb5dd28ac7826e8bb83e102b1` |
| `-mobile-source.sha256` | `8d0ab00ff8e036eac4a6bb7416a4eafd7d5053f9fb23dc62fe2599bdcdc0b0f8` |
| `-engine-source.sha256` | `7445221b2a36c2a6d889d7ed54f260220b28e04221bcdc6f8dcd2b06d23adece` |
| `-gradle.log` | `f227c48f0f839f732449393890bcef4e87a9e5ce5e3a9245bd83a72845c93214` |
| `-gpu.log` | `97ed83f093d83224355f443c6a098b49af32a9c2397f9f222c6319695aa03803` |
| `-native.log` | `5d357ff3c6a68c290e6e292cd592d5910bb23e82265ccb3717c0f4e280e38378` |

The pre-gate Git heads were mobile `3227960801109a139a4151c3e2d357912bd2c2ee` and engine `efd6b53ff132e79d1974314cb55377af9845c42f`. These heads alone do not identify the tested dirty source. The two full manifests do. The earlier failed gate and writer receipts remain unchanged.

## Open acceptance obligations

- Independently review the exact integrated source against canonical section 5 and R13, including failure paths and transport authority.
- Close the capture producer read-epoch gap. Clearing visual queues cannot reject a pre-resume read published afterward without producer evidence. Network buffering before decode likewise does not prove zero-age source time.
- Exercise actual Android pause entry points, callbacks, source changes, rotation, PiP/HUD transfer, transparency, gesture arbitration, accessibility and reset.
- Verify stationary HOLD and hidden surfaces under source flood without unintended GPU work. Test bounded allocation failure and truthful driver-stall limitations.
- Preserve last completed application-present identity without claiming physical scanout or pre-observation external pause timing.
- Run a final clean reviewed source freeze that builds and hashes app and androidTest together before installation.

At the last read-only phone preflight, 07:19 UTC, unrelated media remained active. No installation, audio control, volume, route, grant, security or protected-partition action was performed for this gate. R01 stereo separation, audible monitoring, latency and SoundCloud remain unproven. R02 physical acceptance and the remaining expansion sections remain open.
