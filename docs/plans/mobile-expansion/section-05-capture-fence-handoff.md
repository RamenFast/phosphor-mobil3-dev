# R13 capture producer fence handoff

Status: bounded source follow-up completed. Writer released at 2026-09-08 07:54 UTC. This is not whole R13 acceptance, independent review, Android compilation or a coordinator integration freeze.

## Context and scope

Requested bases: mobile `44172dcc1ea8d91ac1fe8783cbeb68f5c3eb3d68`, shared `084f5d9612f7f699bb80b08607845982d1579861`.

Read the filing cabinet, project docs governance, context standards, complete-build practice, spec-first practice, folder rules, section-05 HOLD contract and validation before code. The capture contract was appended before implementation. An initial implementation-level canonical-plan line was misplaced, then reverted at coordinator direction and placed in spec/EXPANSION.md. Canonical acceptance remains unchanged.

Actual loop was at the bottom of MicController.kt, not ReaderStop.kt. It is now the pure CaptureReadFence.kt function called by the same two existing reader threads. Coordinator approved the narrow deck.rs activation-token seam at 07:48 UTC. ReaderStop.kt and RootCaptureSession.kt remain unchanged.

## Exact implementation

- CaptureService and MicController retain the owner token returned by their existing setRingActive activation. They sample captureReadEpoch before each AudioRecord read and retain it with the entire returned batch.
- The existing native setRingActive return becomes Long/u64. Under the existing ring->meter lock, activation replaces StereoWindow and creates a unique nonzero capture token. Counter exhaustion rejects ownership with zero rather than reusing a token.
- JNI pushCaptureRead copies the array then delegates to engine::publish_capture_read. That production primitive checks original owner, active state and read epoch, then pushes while holding the same ring->meter lock. Publication never activates a source.
- Generic StereoWindow replacement invalidates the producer. Visual-only measurement clear preserves it. No DISPLAY lock was added. No new lock ordering, recorder, thread, audible queue mutation or transport command was added.
- Root keeps its samples-only pushCaptureSamples path. No false freshness tag was added at JNI or pipe receipt.

## Verification and requirement mapping

| Requirement | Observed check |
|---|---|
| Read begun at epoch 4 returns after resume epoch 5 | Production native held-read test rejects old samples and accepts fresh same-owner samples |
| Source owner independent of epoch | Production held-read test replaces the owner without changing epoch, rejects old owner and accepts new owner |
| Current epoch cannot resurrect capture | Production primitive rejects inactive state, zero owner and invalidated owner even with generic source active |
| Publication checks under ring lock | Producer blocked behind ring lock rechecks changed epoch and accepts zero stale samples |
| Real app loop retains whole batch epoch | Cached pure JVM thread fixture returns 960 samples tagged 4 after epoch changes to 5, then tags next 960 samples 5 |
| Zero/terminal/stop behavior retained | Pure JVM tests cover zero reads, negative and thrown terminal errors, and stop during read |
| Existing wake tests stay compatible | Five existing readSourceSamples callers updated for the two-argument push. Full WakeOwnershipTest was not run here |
| Protected surfaces | Owned diff review and git diff --quiet confirm canonical plan, root helper and root session unchanged |

Native command: `CARGO_TARGET_DIR=/home/ben/.jcode/scratch/r13-capture-fence-20260908T074335Z/native-target timeout 180s cargo +1.96.0 test --manifest-path rust/Cargo.toml --locked --offline`. Target was privately copied with reflinks from the existing cache. Result: 87 passed, 0 failed, including four new production capture tests. No GPU tests ran. This host invocation is not an exact whole-tree integration freeze while the coordinator works on other files.

JVM command: `bash /home/ben/.jcode/scratch/r13-capture-fence-20260908T074335Z/run-jvm.sh`. It uses cached Kotlin 2.4.10 compiler jars, compiles only the production pure CaptureReadFence.kt and its test, and invokes JUnit 4.13.2. Result: four passed. No Gradle or Android compilation ran. Owned `git diff --check` passed.

## Immutable artifacts

Directory: `/home/ben/.jcode/scratch/r13-capture-fence-20260908T074335Z/`.

The changed-file SHA256 manifest covers all 11 owned files, including both new Kotlin files. The snapshot contains exact source copies. The patch includes tracked edits and new-file diffs. The report, manifest, patch, archive, runner and logs are read-only handoff artifacts. The private build cache is not a shipping artifact.

| Artifact | SHA256 |
|---|---|
| changed-files.sha256 | c6fac461f48b67d222d53d18719867a4a39a2b8727d13d235dee202fe6cafaae |
| source-snapshot.tar.gz | bccce9a72a203f855b3b4bcac8ffa6f67a0d62a697b7c5d9aba9a735e678fe86 |
| tracked.patch | cbcab6435e759c1a1fae3948847c39f3923111dead0f2e39262c92959a23b799 |
| native.log | ec214d31ac031910e853dd9f09b383268550c4a3fdaf7ed0c80aa2529ada12d5 |
| jvm.log | 1d820b22ee691070f8af987a54d9ecda31e99d56b1fbb9721fcda83b692cac32 |

## Explicit limitations and next gates

Blocked root fence: AudioPolicyMain.java reads AudioRecord before sendBytes(15, Protocol.pcm(...)). Protocol.pcm carries session identity, sequence, actual format and count, but no producer-read epoch. The supervisor and app accept that fixed protocol. Receipt time cannot locate the producing read relative to visual resume.

Best current result: app AudioRecord read-order freshness and source-owner rejection have executable production-primitive evidence. Root behavior remains unchanged and unclaimed.

Next root step: separately approve a bounded fixed visual-epoch control, producer acknowledgement and epoch-bearing PCM across root Java, supervisor validation and app parser. The helper must adopt the epoch before its next read and tag returned batches. Protocol tests must hold a read across resume and reject buffered old-epoch PCM. Do not introduce arbitrary commands or mutate audible queues. This proves helper read ordering only. AudioFlinger buffered sample age still needs genuine producer timing evidence.

App read epochs do not prove AudioFlinger sample timestamps or the age of buffered samples. Network buffering before its existing receive fence remains outside source-time claims. No samples are timestamped at receipt.

Coordinator owns JNI/Android compile and ABI checks, full updated unit suite, integration with concurrent pause/render fixes, independent R13 critic, final exact source freeze, commits and device acceptance. No Git mutation, worker, network, device, GUI, service or audio action was performed in this follow-up.
