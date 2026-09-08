# Root helper feasibility decisions

Date: 2026-09-08, 02:10 UTC. This folds verified source findings into the approved R01 implementation. It does not accept root audio.

## Intent

Keep the authorized root capability private, reversible and local to one capture session. Preserve normal playback and report actual signal quality.

## Decisions and evidence

1. Use an installed native PIE supervisor plus one fixed framework helper. An ordinary app cannot reliably signal a child after root grant. The supervisor owns privileged stop and reap without a persistent daemon.
2. Store sealed DEX in app data, but execute the bootstrap from installed native code. Android 10 forbids target-29 applications from executing writable app-home binaries.
3. Require the observed existing Default root profile for the first trial. KernelSU commit `b0bc817b4e966aa6aa830834eaf6ef765d821d40`, `kernel/policy/allowlist.c`, selects inherited namespaces by default. Its `kernel/infra/su_mount_ns.c` inherited branch does nothing. Other profiles can switch or remount namespaces during grant. No profile mutation is authorized.
4. Use explicit tagged AudioPolicy loopback-with-render, 16 kHz mono PCM16, and framework-owned command-line attribution. API 29–36 `AudioMix.canBeUsedForPrivilegedCapture` imposes the format limits. `AudioMixingRule.allowPrivilegedPlaybackCapture` bypasses projection opt-out, not NO_SYSTEM_CAPTURE.
5. Restrict the initial five-second test to the original app UID and controlled MEDIA/GAME fixtures. Keep only aggregate measurements, not recordings. A known-tone measurement must distinguish real signal from a successful zero-filled read.
6. Root authorization, registration, PCM flow, physical audibility, route cleanup, service ownership and feature acceptance are separate claims. Each needs its own check.

## References and recovery

- [Android 10 execution behavior](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission)
- [KernelSU pinned default profile](https://github.com/tiann/KernelSU/blob/b0bc817b4e966aa6aa830834eaf6ef765d821d40/kernel/policy/allowlist.c)
- [KernelSU pinned namespace behavior](https://github.com/tiann/KernelSU/blob/b0bc817b4e966aa6aa830834eaf6ef765d821d40/kernel/infra/su_mount_ns.c)
- [Android 16 AudioMix](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r1/media/java/android/media/audiopolicy/AudioMix.java)
- [Android 16 AudioMixingRule](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r1/media/java/android/media/audiopolicy/AudioMixingRule.java)
- [Installed app-origin authorization and recovery](../docs/plans/mobile-expansion/section-02-authorization.md)

The prior baseline APK and preferences remain retained and hash-verified. A same-package, same-signer debug replacement is the rollback unit. Do not uninstall, clear data or repair system/root configuration. Expansion drift remains 17 until requirement acceptance, regardless of successful developer probes.

## 02:47 UTC user boundary clarification

Ben explicitly permits reads but forbids writes to `/system`, including `/system/bin`, `/vendor`, and boot/vbmeta partitions. The partition boundary includes raw block devices, aliases and both slots. Do not remount or flash. This clarification does not authorize changes to unrelated applications, grants, root profiles, volume or bootloader configuration. Helper writes remain restricted to installed app code and owned private app data.
