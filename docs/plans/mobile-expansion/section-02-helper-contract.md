# Section 2 fixed root audio feasibility contract

spec-version: root-audio-feasibility-2
compile-count: 1
drift: 1

## Context and vision

This debug-only checkpoint tests whether one authorized native child can capture Phosphor's own controlled tone through Android AudioPolicy. Ben receives truthful aggregate evidence and bounded cleanup. It does not enable a product root switch or establish R01 acceptance.

## Platform and boundary

Target Android 16, SDK36, arm64-v8a, NDK28.2, minimum native API29. Gradle owns Android builds. The standalone Rust PIE has no third-party dependencies. The Java DEX helper uses framework classes only. Production source, startup, permissions, signing, engine, root profiles and system configuration stay unchanged.

Ben reaffirmed the Samsung no-write boundary at 02:47 UTC. `/system`, including `/system/bin`, `/vendor`, and boot/vbmeta partitions remain read-only. Raw block-device aliases and both slots are included. Reading or executing existing system code is allowed. Do not remount, flash or modify bootloader configuration. App installation and owned private app data are the helper's only persistent write surfaces. No helper code opens a block device for writing.

The native helper pins Rust 1.96.0, matching the existing Android engine. Its Gradle linker flags require 16 KiB maximum-page alignment with 4 KiB common pages. Inspect the built ELF program headers before installation. Compilation and alignment do not prove an API 29 or 16 KiB-device runtime check.

AGP 9.1.1 registers helper assets and JNI output through the debug Variant Sources API with task-owned DirectoryProperty outputs. Do not disable its Provider source-set safeguard. The generated-source registration carries task dependencies.

The debug receiver accepts exactly `dev.phosphor.mobil3.ROOT_AUDIO_PROBE`, retains DUMP protection and the existing shared single-flight lock, and ignores extras. No UI opens. The normal app retains its original unprivileged credentials. No shell, ksud, socket, command/path interface, persistent daemon, other-app target or PCM file exists.

## Packaged identity and sealed code

Gradle calculates one SHA-256 build identity from the root-helper runtime source files, Cargo manifests, this contract, app Gradle file and owned debug source/manifest. That content identity, not a mutable dirty Git suffix, names the helper directory and is compiled into Java, native code and debug BuildConfig. The receipt separately records installed BuildConfig BUILD_COMMIT and package version.

Gradle javac plus SDK D8 produce `root-audio/helper.jar` and its SHA-256 metadata asset. Native compilation embeds that exact JAR digest. The PIE is staged in a separate debug jniLibs directory as `libphosphor_root_launcher.so`. Debug alone requests legacy extraction.

The app publishes the asset at `/data/user/<uid/100000>/dev.phosphor.mobil3.debug/no_backup/root-helper/<build-id>/helper.jar`. The two new `root-helper` and build-ID directories must be app-owned, non-symlink and mode0700. Existing Android-managed `no_backup` is app-owned, non-symlink and not world-writable. Its measured0771 mode stays unchanged. The app uses exclusive no-follow creation, marks the new inode 0400 before writing, hashes and fsyncs before atomic publication, and never repairs an unverified existing file. Both app and native validate regular type, original UID, no write bits, bounded size and digest. Native opens before grant and retains the inode as helper fd6. `CLASSPATH=/proc/self/fd/6` has no pathname fallback. Unsupported CE placement or ART fd class loading fails with a cause and fix.

## Native supervisor

ProcessBuilder starts only the installed fixed executable with no arguments, from a dedicated session-long app thread. Native requires argc1 and real/effective/saved UID equal and nonzero, with an Android application appId. It captures original UID from OS credentials, not IPC. It sets parent-death SIGKILL and reads it back, checks the unchanged parent, and arms a five-second startup alarm before provider access.

The initial provider compatibility table contains only version32525, UAPI2, flags LKM|LATE_LOAD with MANAGER/PR_BUILD absent. Structures are C layout: GET_INFO four u32, size16/alignment4. UID_GRANTED_ROOT is u32 UID, u8 boolean, three zero padding bytes, size8. Arm64 reboot syscall142 with magic DEADBEEF/CAFEBABE installs the FD. The FD output, not syscall return, determines acquisition. GET_INFO=80104B02, GRANT_ROOT=4B01, UID_GRANTED_ROOT=C0004B08. Grant has a null argument and uses the current UID's existing authority. No pregrant UID grant query occurs.

After grant, all three UIDs must be zero. Native rearms and reads back PDEATHSIG because credential changes may clear it. The mount namespace link must equal the pregrant link. Before/after credential, GID, SELinux, seccomp and namespace evidence travels as bounded aggregate status. No configuration repair occurs. The existing Default/inherited profile evidence is a coordinator prerequisite already satisfied, not a request to change it.

The single-threaded native process forks exactly one direct helper. The child sets PDEATHSIG before exec and verifies its parent. It closes unrelated descriptors, including the KSU FD. Only stdin/stdout/stderr, and pinned DEX fd6 survive. The executable is `/system/bin/app_process`, argv is exactly that path, `/`, and `dev.phosphor.mobil3.root.AudioPolicyMain`. Environment contains fixed Android runtime roots, fd6 CLASSPATH and two validated platform boot-classpath exports. No inherited caller environment enters ART.

After grant and before fork, native reads only `/data/system/environ/classpath`, the existing Android-generated export file. Check each parent and the no-follow file for root/system ownership, trusted group and no world-write. Require a bounded regular file of at most64KiB. Parse data, never shell syntax. Accept only the four named Android exports, reject duplicates and require nonempty BOOTCLASSPATH and DEX2OATBOOTCLASSPATH. Preserve their exact order. Pass only those two exports to ART. Every required entry must be a bounded absolute JAR path directly under `/system/framework` or `/apex/<module>/javalib`. Validate canonical placement, regular type, root/system UID and GID, and no group/world-write before exec. The S25's measured APEX JARs are system:system0644 on read-only mounts, while framework JARs are root:root0644. Missing, malformed or inaccessible platform exports fail with a named stage. Do not regenerate, edit or repair the file, and do not substitute caller environment or writable app paths.

This follows Android16 `packages/modules/SdkExtensions/derive_classpath/derive_classpath.cpp`, blob `c1faec2e28c69bb464dab961b0fb6daa890618aa`, `WriteClasspathExports`. On exact4f8e5ec, logd observed ART PID13206 abort before runtime creation because BOOTCLASSPATH and DEX2OATBOOTCLASSPATH were empty. The native supervisor reaped that child. No policy or fixture started, and independent process/policy checks found no residue.

The native supervisor is the sole wait owner. It signals only its positive unreaped direct child PID. It queries saved original UID authorization once per second after root. EOF, STOP, missing heartbeat for two seconds, revocation/query failure, protocol error, backpressure, helper death or timeout initiates STOP. Grace is one second, then root SIGKILL and wait/reap. Ready deadline is ten seconds from supervisor session start. START is legal once after READY. Capture deadline is six seconds from START, allowing a five-second helper window. Total postgrant alarm is twenty seconds. Kernel parent-death SIGKILL is the last resort, not a cleanup success claim.

## Private typed protocol v1

All streams are private pipes. Each frame has three little-endian u32 values: magic `0x31524150`, kind, payload byte length. Maximum payload4096, total frame4108. Partial reads accumulate within that bound. EOF with a partial frame is a protocol error. Unknown kinds, duplicate/stale READY, invalid lengths and unexpected state fail closed. Writes are nonblocking and bounded. A full pipe is failure, never an unbounded wait.

| Direction | Kind | Payload |
|---|---:|---|
| App to native | 1 HEARTBEAT | Empty, every250ms |
| App to native, native to helper | 2 START | Empty, once after READY and fixture playback starts |
| App to native, native to helper | 3 STOP | Empty |
| Native to helper | 10 INIT | Original UID u32 followed by64 lowercase ASCII build hex |
| Helper to native to app | 11 READY | INIT identity prefix followed by bounded UTF-8 JSON observations |
| Helper to native to app | 12 RESULT | INIT identity prefix followed by bounded UTF-8 JSON result |
| Native to app | 13 EVIDENCE | Bounded UTF-8 JSON native before/after observations |
| Native to app | 14 FINAL | JSON status, cause/fix, cleanup_confirmed, killed and child exit |

Helper validates INIT against its compiled build and its actual UID0. Native validates every READY/RESULT identity prefix. JSON never contains instructions or PCM. The app requires exact identities, one READY, one RESULT, one FINAL, helper exit0, successful aggregates, helper cleanup true, native cleanup true and normal supervisor exit before success. Missing cleanup confirmation latches refusal of another audio probe for the app process lifetime. A fresh coordinator-verified cleanup trial is required before treating restart as safe recovery.

## Framework capture and fixture

Bare Java main requires actual UID0 and does not fabricate Application, Context or package attribution. Reflection resolves the framework's hidden AudioManager noarg constructor, AudioMixingRule, AudioMix and AudioPolicy APIs. It uses a HandlerThread and AudioPolicy.Builder(null). Rules are MEDIA OR GAME, AND original Phosphor UID. Privileged capture is true, route flags3 are LOOP_BACK|RENDER, format PCM16/16000Hz/CHANNEL_OUT_MONO. Only registered policy.createAudioRecordSink(mix) creates the recorder. No MediaProjection, untagged REMOTE_SUBMIX or app RECORD_AUDIO request exists.

READY follows successful registration, validated actual format and started recording. It reports protocol/build/original UID, actual UID/PID, SDK, stage, attribution and framework permission checks. The app refuses unless AudioManager.mode is MODE_NORMAL and rechecks during playback. After READY, it starts only its own MEDIA AudioTrack, a997Hz sine at0.025 software amplitude, with no global volume, audio focus or route changes. A bounded static loop avoids an extra audio writer thread. It sends START only after fixture.play().

The helper reads at most80000 mono samples during a five-second monotonic window in160-sample nonblocking blocks. Zero-length reads differ from zero PCM. Aggregates contain frame count, nonzero count, normalized RMS, peak, positive-crossing frequency estimate,997Hz projection ratio, actual format and elapsed time. Success requires enough frames, nonzero signal and a frequency-compatible tone, not registration alone. No samples persist or leave the helper.

Every branch stops/releases AudioRecord, synchronously unregisters a registered policy, and quits/joins the HandlerThread. Cleanup exceptions retain their stage and cause. The supervisor bounds hung Binder/cleanup through its deadline and direct-child kill. The app always stops/releases its fixture and closes pipes. It never relies on unprivileged kill to terminate root children.

## Receipt and checks

AtomicFile publishes `files/root-audio-feasibility.json` with `{status,tool,version,ts,data}`. Tool is `pm3-root-audio-feasibility`, version1.0.0. Failure includes bounded error and concrete fix. Data includes installed package/version/commit, content build identity, app UID/PID, native/helper evidence, aggregate result, elapsed time and cleanup confirmation. No zero-PCM success, credentials or audio data are recorded.

Host tests cover ABI constants/layout, fixed launch derivation, UID checks, frame limits and partial I/O, state transitions for STOP/EOF/heartbeat/revocation/stale READY/timeout/backpressure/child exit/kill-reap, and no success with incomplete cleanup. Java host fixtures exercise protocol bounds and tone/silence/wrong-frequency/partial-read aggregation. Debug unit tests exercise app protocol identity and success gates. These are host fixtures, not Android execution or hardware audio evidence.

Coordinator owns Gradle tests/lint/build, extracted PIE and fd6 classpath proof, exact installed APK identity, real PCM and audible route checks, owner death/revocation/hung cleanup trials and final R01 review. If a premise fails: record **Blocked**, **Evidence**, **Best current result**, and **Next smallest action**. Do not add a system repair.

## Implementation notes and coordinator handoff

- The app sends at most100 fixed12-byte control frames. Even a stalled reader cannot fill a minimum4096-byte pipe with this1200-byte session budget. Native/helper status writers use O_NONBLOCK.
- Framework RuntimeInit redirects System.out. Helper frames use explicit inherited FileDescriptor.out through Os.write, never System.out.
- Frequency uses first-to-last positive-crossing positions. Startup silence remains in frame/nonzero/RMS counts and does not depress the frequency estimate.
- A reaped child can leave a final frame queued after the previous EAGAIN. Native drains terminal frames before retiring protocol state. It never kills after successful waitpid.
- Synchronous unregistration is attempted after every registration attempt, including an exception. Its own failure cannot erase the original cause.
- READY is rejected after app cancellation. The finally-path drain cannot create a new fixture.
- The app observes a23-second total deadline and allows two seconds for final cleanup evidence. The supervisor's startup/postgrant alarms remain the privileged lifetime backstop.

Coordinator reproduction uses an ordinary explicit broadcast, without `--receiver-foreground`. Foreground broadcast timeout is shorter than the allowed session.

```sh
adb -s SERIAL shell am broadcast \
  -a dev.phosphor.mobil3.ROOT_AUDIO_PROBE \
  -n dev.phosphor.mobil3.debug/dev.phosphor.mobil3.SelfTestReceiver
adb -s SERIAL exec-out run-as dev.phosphor.mobil3.debug \
  cat files/root-audio-feasibility.json
```

Gradle tasks: `:app:generateRootAudioIdentity`, `:app:compileRootAudioJava`, `:app:dexRootAudioHelper`, `:app:buildRootAudioLauncher`. Debug merge tasks depend on the payload tasks. Existing release source sets, signing, classpath tasks and engine tasks are preserved. The coordinator runs `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:checkEngine` and validates debug extraction plus release exclusion.

Host verification at implementation handoff:

| Requirement | Check | Observed scope |
|---|---|---|
| UAPI/layout/UID/provider/fixed path/env | Rust unit fixtures | Passed, no provider invocation |
| Frame bounds, byte-at-a-time input, partial EOF | Rust and Java protocol fixtures | Passed, synthetic bytes |
| STOP/owner EOF/revocation/backpressure/kill-reap | Rust Session failure fixtures | Passed state logic, not OS fault injection |
| Heartbeat/ready/capture deadlines, stale READY | Rust Session fixtures | Passed synthetic monotonic times |
| Final frame concurrent with exit | Rust decoder/state regression | Passed synthetic ordering, actual waitpid race not host-injected |
| DEX SHA-256 | Empty/abc/million-a known vectors | Passed, sealed Android inode not exercised |
|997Hz,440Hz,silence,500ms leading silence | Java ToneStats fixtures |29 protocol/math assertions passed |
| Debug identity, action, aggregate/cleanup success gates | Seven RootAudioProtocolTest tests authored | Coordinator Gradle run pending |
| Reflection/permissions/registration/read/cleanup | Stage/cause-preserving Java paths | Source authored, framework execution unproven |
| Extracted PIE, grant, fd6 DEX, real PCM, route cleanup | Coordinator Android workflow | Not performed by implementation worker |

Native host command was `CARGO_TARGET_DIR=/home/ben/.jcode/scratch/section2-native-target cargo test --manifest-path root-helper/native/Cargo.toml --locked`:11 tests passed. The host also compiled the platform module without executing it. Java host fixtures used JDK21 `javac --release17 -Xlint:all -Werror` and only Protocol/ToneStats/Test, not framework helper compilation. No APK, device, profile, system configuration or Git mutation occurred in this worker window.

**Blocked:** Hardware feasibility and Android integration remain unproven, not failed. **Evidence:** Only host fixtures and source inspection ran here. **Best current result:** Fixed debug implementation and requirement-linked tests are ready for coordinator trials. **Next smallest action:** Build through Gradle, verify the exact packaged/extracted code, then run one own-UID probe with existing volume and route unchanged.
