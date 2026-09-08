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

Ignored private recovery contains `root-audio-exact-build.log`, `root-audio-4f8e5ec-install.json`, retained app/test APKs, `root-audio-trial1.json`, before/after audio/policy/process dumps and preference archives. `root-audio-trial1-logd.txt` contains private system logs and must not be published.

Before retry, preserve the failed receipt, confirm no helper or policy remains, and build/install the exact corrected commit. The same-package install retires the old app process and its cleanup latch. Do not clear data, reset a grant, change the Default profile or retry an unexplained failure.

`/system`, including `/system/bin`, `/vendor`, and boot/vbmeta partitions remain read-only through all paths. Reads are allowed. No remount, flash or bootloader changes are authorized.
