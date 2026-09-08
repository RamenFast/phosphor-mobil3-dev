# Section 2 fixed helper device trials

This is feasibility evidence, not product R01 acceptance. The coordinator alone runs the S25 trials.

## Trial 1, 02:57 UTC

| Identity | Exact value |
|---|---|
| Mobile commit | `4f8e5ec4568b8f6e0bb3e0575e8bb44771d8fb80` |
| Source archive SHA-256 | `be88b115b3ae6eb43379b57e6d84e97ae4ed7841108a7a567fb0da8739e4791e` |
| App APK local and installed SHA-256 | `40b0712d77fed7083e9f2799eec320e8700e35198e631c6db29df1c4b1de95bf` |
| Companion androidTest APK SHA-256 | `dc4b6fe7dab7bcde67a339a5fe9ada6c4da22e537265b6f9e277e31ed09f2a01` |
| Signer SHA-256 | `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d` |
| Packaged helper JAR SHA-256 | `3c0305d207b1f80bcc89d24afb6487448b7b90306d682ac1c2e2772ac1094a6c` |
| Helper content identity | `008f9f74dc0694ffc01ea26cd719420adec5b768e3ae8be6a161e01854142865` |

Task `037122r8l5` built both APKs together from the clean commit. Unit tests, lint and checkEngine passed in51.4seconds. The prior464-unit-test count is retained. The generated test APK contains no handwritten instrumentation cases and is not a device-test pass. Task `165797yp7y` installed only the exact app APK through `dev/pm3`, verified its readback and signer, and preserved preferences byte-for-byte.

At02:54 the user-selected route had changed from muted speaker to Bluetooth A2DP, MUSIC7/15. Actual mode was MODE_NORMAL and no player was started. This newer state supersedes the02:11 muted baseline. No volume, focus, route or UI change was made by the coordinator.

The coordinator sent the ordinary explicit DUMP-protected ROOT_AUDIO_PROBE broadcast without foreground-receiver flags. A fresh receipt arrived in570ms. The installed native PIE ran from app UID10401 and acquired all root UIDs through the existing KernelSU32525/UAPI2/flags5 grant. The before/after mount namespace was exactly the same. The normal app remained UID10401.

The ART child exited with raw wait status6 before READY. Native reported helper_eof and reaped its child. The app reported cleanup uncertainty, not success, because no Java cleanup frame existed. No fixture tone started. Independent process and AudioPolicy dumps found no helper process or policy mix afterward. Existing long-lived `logcat sulogd` was unchanged. The complete preference archive still hashed `1f3fcf264ead0a0b3823a2c31f8c512842c9e25501c082ce659ed2b2fa3d1a72`.

### Diagnosed cause and correction

A finite shell-UID logd dump read20000records through the retained diagnostic. Its binary hash was checked after staging. The coordinator removed that single task-owned diagnostic afterward. No system executable, mount, root grant or security setting changed.

The actual ART abort said: `BOOTCLASSPATH and DEX2OATBOOTCLASSPATH must not be empty`. Its runtime did not yet exist. Therefore this trial does not test Java reflection, fd6 DEX loading or audio capture.

The spec-first correction reads Android's existing `/data/system/environ/classpath` after grant. It validates ownership, type, mode, bounded export syntax and system/APEX JAR paths. It passes only the two required boot-classpath keys alongside fixed environment values. It never executes export text, inherits caller environment or writes the platform file. Pinned Android16 exporter blob `c1faec2e28c69bb464dab961b0fb6daa890618aa` establishes the file format.

Sixteen locked native tests now pass, including five new parser/path groups. The source boundary and protected-file hashes pass. Independent reviewer flamingo found the original launch omission and no material issue in the bounded source-only correction pass. Actual file parsing and corrected ART startup remain pending.

## Evidence location and retry gate

### Trial2,03:08 UTC

Clean source `fed17e6fb4c220ac2d81f3e48e9edc7740205ba9`, archive hash `254171fc0da0b53bea33aca090b65aeb632c50f6bc0daa430e59ce1fb5bc4fc0`, passed both APK builds and unit/lint/checkEngine in52.7seconds. Installed/readback app hash is `b5ebc8509009a04badd3bd2f0eae85be4326b3393d34f7cdc98f564fcb48f6a7`. Companion test APK hash is `8c643d5fc3bb80be985f1d1b666b7dc0b83a09c90f95991c888375ef18c6c0d4`. Signer and preferences stayed unchanged.

The24ms trial read and parsed the protected platform export file, then refused before fork at `platform_classpath_jar_owner_type_mode`. Read-only shell metadata established the cause: the existing APEX JARs are system:system0644, not root-owned. Framework JARs are root:root0644. The ART APEX mount reports read-only. No file permission was changed. The corrected source accepts only root/system UID and GID with regular type and no group/world-write. A focused ownership test retains rejection of app/shell ownership, unsafe groups, nonregular files and writable modes. No helper, policy or fixture remained after trial2.

Ignored private recovery contains `root-audio-exact-build.log`, `root-audio-4f8e5ec-install.json`, retained app/test APKs, `root-audio-trial1.json`, before/after audio/policy/process dumps and preference archives. `root-audio-trial1-logd.txt` contains private system logs and must not be published.

Before retry, preserve the failed receipt, confirm no helper or policy remains, and build/install the exact corrected commit. The same-package install retires the old app process and its cleanup latch. Do not clear data, reset a grant, change the Default profile or retry an unexplained failure.

`/system`, including `/system/bin`, `/vendor`, and boot/vbmeta partitions remain read-only through all paths. Reads are allowed. No remount, flash or bootloader changes are authorized.

## Trials 3 through 5: real PCM and recovery

The ownership correction produced clean source `a93db645e0c8fe5a0c234101a5b977d04a7ab00d`. Task `027685myzr` passed 17 locked native tests and the Android unit, lint, checkEngine and dual-APK build gates. Both APKs came from that same clean source. The generated androidTest APK still has no handwritten instrumentation cases.

| Identity | SHA-256 |
|---|---|
| Source archive | `898008810fe7d4cf5b188f6c1ff942d8f72cec2eb885973aa3755f5f65ee3e21` |
| App APK, local and installed | `d50ab66a450619caefc4431dcd887a34f950a5a370610bc6b3afb0bd898f6c73` |
| Companion androidTest APK | `4434ac0b484c1f574cee9e286980742996d8017a6f2329544459c148cd2ed37b` |
| Helper content identity | `8fb20726d11e685034d6254e75b41f6e1d6849dff9246f1ea355a99f71a5f889` |
| Signer, unchanged | `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d` |

The coordinator installed and read back that exact app artifact for trial 3. Trials 4 and 5 reused it without reinstalling and rechecked its installed hash. Each fresh receipt matched the build and a new timestamp. The app retained UID10401. Native acquired the existing grant, retained the same mount namespace before and after grant, and loaded the sealed DEX through fd6. Framework attribution naturally resolved to UID1000 and a real system package. No app or shell identity was fabricated.

| Trial | Time UTC | App PID | Frames | Nonzero | RMS | Frequency Hz | Tone ratio | Capture ms |
|---|---|---|---|---|---|---|---|---|
| 3, first real PCM | 03:13:42 | 18710 | 80000 | 74223 | 0.0170212 | 996.99748 | 0.92657 | 5001 |
| 4, same-process repeat | 03:16:03 | 18710 | 80000 | 73887 | 0.0169825 | 996.99797 | 0.92324 | 5001 |
| 5, post-death recovery | 03:28:12 | 23144 | 80000 | 72208 | 0.0167888 | 996.99716 | 0.89572 | 5000 |

All three used actual PCM16, 16000Hz, one channel and route flags3. Java reported synchronous cleanup, native reaped an exit0 child without killing it, and the supervisor exited0. Independent post-trial process and AudioPolicy dumps found no owned helper or mix. Each before/after preference archive exactly matched the original baseline hash `1f3fcf264ead0a0b3823a2c31f8c512842c9e25501c082ce659ed2b2fa3d1a72`.

Trial 4 captured live routing while the fixture ran. AudioPolicy showed MIX_TYPE_PLAYERS with MIX_ROUTE_FLAG_RENDER and MIX_ROUTE_FLAG_LOOP_BACK, tagged REMOTE_SUBMIX, UID10401, MEDIA and GAME. The actual started Phosphor AudioTrack used device783, which the same dump identified as Bluetooth A2DP. AudioFlinger showed an active 16000Hz submix output with advancing frames. The complete MUSIC route/volume section was unchanged afterward. This proves simultaneous platform rendering and tagged capture, not physical audibility. Trial 3 has no live-routing dump because its synchronous broadcast returned only after completion.

### Real parent death, 03:22:26 UTC

Task `745267v5tj` observed an actual started fixture and the hierarchy app18710 -> root supervisor22997 -> root ART helper22999. The coordinator used `run-as dev.phosphor.mobil3.debug kill -KILL 18710`, targeting only the ordinary app with its own UID. This was not `am force-stop` or a signal to either root child. Both root descendants were absent at the first post-kill process observation. The policy mix was empty, no AudioTrack remained active, and preferences were byte-identical. The long-lived unrelated `logcat sulogd` process was not touched.

Trial 5 then started a fresh app process on the same installed artifact. Task `086847sagy` exited0 after a new 80000-frame tone capture and full cleanup. This verifies recovery after the real parent-death path. No root grant, profile, volume, global setting or protected path changed.

### Scope and remaining acceptance

The three successful receipts retain `hardware_acceptance:false` and `audibility_proven:false`. Physical output was not independently heard or measured. Production streaming, service ownership, the hidden opt-in UI, opt-out fixtures, permission/revocation and stalled-reader cases remain open. These feasibility checks do not accept full R01.

Private evidence includes `root-audio-apex-build.log`, `root-audio-trial{3,4,5}*`, `root-audio-parentdeath-*`, retained exact APKs and the bounded coordinator runner. Raw device identities and system dumps remain private. The [bounded helper review](critiques/section-02-helper-safety.md) is separate from the pending scored R01 review.
