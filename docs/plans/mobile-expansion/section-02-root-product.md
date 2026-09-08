# Section 2 root capture product contract [R01]

spec-version: root-audio-product-1
compile-count: 0
drift: 1

## Context and winning condition

The existing finite helper proved PCM feasibility. R01 now connects that helper to the existing instrument, source retirement, and hidden bestiary. Acceptance requires actual streaming, cleanup, recovery, and UI checks. A build does not establish acceptance.

Ben clarified at04:17:59 UTC that true audio from any app, specifically SoundCloud, is the point of R01. The16kHz mono path below remains a limited, testable checkpoint. It does not lower that goal. SoundCloud support is unmeasured, not known blocked. Investigate fuller-fidelity paths and actual opt-out behavior within the read-only system/vendor/boot/vbmeta boundary before closing R01. Preserve this protocol's exact format until a separately specified and verified replacement exists.

At04:21:19 Ben made original stereo a requirement and SoundCloud the smoke test. He reports SoundCloud is silent under standard capture while Spotify already works. kHz fidelity matters and should ideally follow the chosen setting. Existing `BeamRates` selects48/96/192kHz reconstruction over fixed48kHz input, not actual capture rate. A replacement must preserve independent channels, identify actual capture/transport/reconstruction rates, and never claim reconstructed samples restore missing source bandwidth. This limited mono protocol cannot satisfy that acceptance.

## Platform and fixed boundary

One debug/release Android product targets API36 with minimum API29. The app remains unprivileged. One installed native PIE owns one fixed ART child per capture session. Authorization-only starts no ART child and captures nothing. No daemon, socket, network, recording, arbitrary executable, path, UID, package, or command interface is added.

The supported provider is KernelSU32525, UAPI2, flags5. The existing Default/inherited profile must be independently established and locally acknowledged before launch. GRANT_ROOT applies its own profile. A post-grant namespace match is a compatibility check, not pregrant proof. Denial directs the user to existing manager authorization and retry. The app never manufactures a prompt, grant, or profile change.

Preserve sealed no-follow DEX publication, exact digest and build identity, installed-library execution, fd6 class loading, platform classpath validation, root/system JAR UID/GID acceptance, post-grant parent-death rearming, original-UID revocation queries, direct-child-only kill/wait, and terminal-frame drain. Android-managed no_backup stays unchanged, including measured0771 mode. System, vendor, boot/vbmeta, block aliases, and both slots remain read-only.

## Local fields and activation

Use the existing nonportable runtime preference file only:

| Exact key | Type/default | Meaning |
|---|---|---|
| `root_capture_enabled` | Boolean false | Local choice to use root for Everything playing |
| `root_capture_profile_ack` | Boolean false | User explicitly acknowledged the existing supported Default/inherited profile prerequisite |

Neither key enters SettingsArchive, automatic backup, instrument presets, or imported settings. No authorization or health result persists. Five taps retain the existing bestiary reveal. ROOT CAPTURE appears only there. Initial enablement explains scope, provider/profile support, real existing manager authorization, and 16kHz mono. Confirming writes acknowledgement and starts finite authorization-only. Only genuine authorization success enables the switch. Disabling writes false immediately, cancels pending authorization, and retires any actual root owner before replacement. Healthy capture startup revalidates through GRANT_ROOT without an extra app dialog.

Root-off normal startup reads local flags but never stages, executes, or probes the helper. Imports do not execute root code or alter these flags. Root failure remains visible with Retry root, Open root manager, and Use standard capture choices. Standard selection is explicit and uses the unchanged RECORD_AUDIO and MediaProjection consent chain. No automatic fallback exists.

## Packaged identity

AGP9.1.1 Variant Sources APIs register task-owned generated assets and JNI directories for debug and release. Each variant identity hashes owned runtime sources, contracts, Gradle inputs, and its exact application ID. BuildConfig, generated HelperBuild, native ROOT_HELPER_BUILD, ROOT_HELPER_PACKAGE and embedded JAR digest agree. Private placement derives from that compiled package, never a debug path in production. Both variants extract the installed launcher library. Release rejects controlled feasibility modes and contains no debug receiver or entrypoint.

## Private pipe protocol version 2

Each little-endian frame has u32 magic `0x31524150`, u32 kind, u32 payload length, then at most4096 payload bytes. Pipes have bounded kernel buffers and no user-space audio queue. A partial write or backpressure fails closed. The app drains at most64KiB per loop. Native forwards at most16 frames per bounded receive pass.

Before provider access, the app sends SELECT kind20: u64 positive generation, u32 mode. Fixed modes are 0 finite own-UID feasibility (debug), 1 authorization-only, 2 product MEDIA/GAME streaming, and 3 finite own-UID product streaming (debug). No extra knobs exist. Native obtains original UID from credentials, not SELECT. Unknown or release-forbidden modes fail before grant.

The identity prefix is original UID u32, lowercase build SHA-256 ASCII64, generation u64, mode u32 (80 bytes). INIT kind10 carries that prefix. READY11 and RESULT12 prepend it to bounded JSON with protocol2 and actual UID0. Native evidence13 and FINAL14 use bounded JSON and include generation. Authorization-only emits evidence13 and FINAL14 without helper creation.

App controls HEARTBEAT1, START2 and STOP3 each carry generation u64. START is legal once after READY. STOP is idempotent. The helper receives only START/STOP. Stale generations, wrong identities, duplicate readiness/results, invalid control states, partial EOF, oversized frames, unknown kinds, and sequence errors retire the session.

PCM kind15 contains identity80, sequence u64 (starts0), sample rate u32=16000, channels u32=1, encoding u32=2 (PCM16), frame count u32 in1..160, and exactly frameCount*2 little-endian PCM bytes. There is one producer. Every accepted block advances sequence exactly once. Zero-valued blocks are valid silence, not proof of authorization failure.

## Time, format, and shutdown

Supervisor FINAL cleanup requires reaping, no forced kill, an accepted RESULT and intact app/helper protocol. Parsing and semantic rejections remain sticky through terminal drain. A valid RESULT reporting a clean helper failure can retire ownership. Reaping alone does not validate rejected evidence.

The app sends heartbeat every250ms. Native expires the lease after2000ms without heartbeat. READY/START expires within10s. Native checks the original UID grant each second. Helper read-loop progress expires after3000ms, including blocked Binder/read/output. Kind16 carries identity80 plus a strictly increasing u64 progress sequence, starting0, every250ms after a completed nonblocking read. Zero reads emit progress but no invented PCM. The app shows no input until PCM arrives, and keeps a healthy idle session ready for later playback. PCM and progress use separate counters. Native renews a five-second process alarm only while its bounded supervisor loop progresses. A stuck supervisor therefore exits within5s, and child parent-death protection applies. This renewable watchdog replaces the old absolute20s limit, not the finite feasibility limit.

STOP, lease expiry, EOF, revocation, malformed data, helper exit, or output backpressure sends one STOP. Native allows1000ms for helper cleanup, then kills only its still-owned direct child. Reap has a further2000ms bound. No PID is signaled after successful wait. Unproven cleanup latches replacement closed. The app waits at most8s for stop and requires clean helper RESULT plus native reap FINAL and process exit. A killed child is not a successful cleanup receipt. Activity and task callbacks cannot bypass that latch.

Finite feasibility retains five-second capture and aggregate997Hz tone evidence. Controlled product mode lasts at mostfive seconds. Product mode has no absolute session duration. PCM normalization converts each16kHz mono sample to three48kHz float frames, with two identical channels. A causal linear interpolator retains the previous sample across chunks, giving exactly3N frames for N inputs and one-sample causal latency. The UI says `16 kHz mono · duplicated mono`, never stereo recovery.

## Actual foreground ownership

`CaptureService` retains shared retirement, metadata, task, status and generation logic. Its standard backend remains mediaProjection. `RootCaptureService` is a nonexported narrow subclass with only specialUse and subtype `User-enabled root playback audio visualization through a private session-scoped helper`. API34+ starts with FOREGROUND_SERVICE_SPECIAL_USE permission and specialUse type. API29–33 uses the base foreground-service requirement and no fabricated playback/projection role.

Root ownership means a live service-owned RootCaptureSession, validated READY/PCM, and current owner generation. It never means a projection token or app AudioRecord. Shared CaptureStopLifecycle and SourceRetirement wait for actual root termination before disabling the ring or admitting another source. PlaybackService retains the same capture metadata mirror owner ID. SourceWakePolicy gains explicit root facts instead of passing projection=true. BackgroundLifecyclePolicy task/linger rules stay shared. Recreation rebinds current status/owner without restarting or prompting. Duplicate starts cannot replace the current owner.

## Observable acceptance map

| Requirement | Check |
|---|---|
| Root-off inertness and import boundary | Policy/unit tests plus coordinator cold startup/import process and preference observations |
| Supported real authorization | Native tests, authorization-only output, app UID and provider observations |
| Variant identity and sealed code | Exact debug/release generated package/build/digest checks and installed ELF readback |
| Typed stream rejection and lease bounds | Rust/Java/Kotlin tests for framing, generations, sequence, malformed sizes, heartbeat, stop and timeout |
| Normalization truth | Chunk-split equality, exact3N frame counts, range and duplicated-channel tests |
| Real root direct startup | UI starts root without RECORD_AUDIO or MediaProjection dialogs and native ring receives live PCM |
| Honest FGS and rebind | Manifest source/release checks, service dumps and activity recreation with stable owner/helper IDs |
| Replacement and failures | Stop, root-off, EOF, task removal/linger, revocation, failure and retry with no overlap or silent fallback |
| Controlled policy behavior | Fixed own-UID SYSTEM versus NONE fixtures, actual PCM/ring, cleanup and route restoration receipts |
| Audible output | Actual route and AudioTrack render observation, plus independent physical audibility if measured |

Coordinator owns Android compilation, exact installation, physical acceptance, scanner edits, critique, receipts and commits. Suggested gate: `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:checkEngine`, followed by release packaging/source boundaries and controlled device cases. Host passes and unrun Android/device checks are reported separately.

Old a93db645 feasibility receipts use protocol1, finite own-UID aggregate output and debug-only packaging. They do not establish this protocol2 product loop.

## Fixed coordinator developer checks

The existing DUMP-protected, single-flight debug receiver ignores extras. The coordinator approved these exact additions:

- `ROOT_CAPTURE_SYSTEM_TEST`: finite own-UID mode3 through the real RootCaptureService, decoder, normalizer and native ingress. Require expected997Hz PCM and post-JNI ring aggregates.
- `ROOT_CAPTURE_NONE_TEST`: the same path with ALLOW_CAPTURE_BY_NONE. Require an advanced actual AudioTrack plus exclusion and completed helper read-loop progress. Zero reads are not fabricated PCM.
- `ROOT_CAPTURE_TONE`: require the existing UI-started live root owner. Play the fixed own-UID997Hz tone for five seconds. Do not create or stop a helper, source or service.

Each action rejects communication mode and existing audible playback. The first two also reject existing local, relay, mic, capture, helper and unresolved cleanup owners. A cancelled queued admission checks its cancellation gate before reserve, service startup and READY. Cleanup uses observer identity, not whichever owner currently exists. A failed preflight cannot stop another source.

The debug-only native snapshot reads at most0.5seconds from the existing ring and returns only frame count, sample rate, duplicated-channel equality, RMS and frequency. Its fixed aggregate function is host-tested and shared from root-helper/native/src/signal.rs. Gradle tracks that exact source for core rebuilds. A snapshot requires at least24000 newly delivered input frames and1500ms in the same generation, rejecting old ring history. Zero-read exclusion does not claim downstream PCM delivery. No raw audio is retained or returned.

Receipts are fixed aggregate JSON files: root-capture-system.json, root-capture-none.json and root-capture-tone.json. Record unchanged local runtime preferences, real AudioTrack start/advancement, original app UID, build identity, generation and cleanup. Physical audibility remains false unless independently measured.

Native FINAL includes `child_started`. False means setup or authorization-only created no ART child. That confirmed no-child result permits retry or explicit standard choice without inventing missing Java cleanup. True requires helper cleanup plus direct-child reap. App parser failure rejects further PCM publication and keeps uncertain cleanup closed.
