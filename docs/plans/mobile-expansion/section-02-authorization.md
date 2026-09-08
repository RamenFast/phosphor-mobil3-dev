# Section 2: normal-app authorization feasibility

This is a developer feasibility checkpoint, not root-capture acceptance.

## Contract and current evidence

KernelSU shows Phosphor debug, UID 10401, with Superuser enabled and the Default profile. The coordinator did not change the grant. The driver reports jailbreak mode and a manager/driver version mismatch. Shell and `run-as` do not expose standard su. The latter uses a different process context, so that result does not settle normal-app authorization.

The existing debug receiver now requires `android.permission.DUMP` and checks exact actions. The new explicit action calls only a bounded fixed identity command. It accepts no command/path extras, reads no audio, changes no grants/settings, and never runs from ordinary startup. Production source has no receiver or probe. The successful existing native self-test remains separate.

The command tries fixed standard su locations only when the preceding executable is absent. Permission errors, successful launches followed by denial, and timeout stop the attempt. The receipt records bounded launch errors. Authorization requires exit zero, complete output `0`, and confirmed process/output-reader termination. Timeout, malformed identity, excessive output and incomplete cleanup stay failures.

## Developer reproduction

1. Build through the Gradle wrapper and pass debug unit tests and lint.
2. Preserve current package settings and verify the existing signer before replacement.
3. Install the named same-package debug APK using `dev/pm3 --serial SERIAL install APK`.
4. Send this explicit debug broadcast with the real serial:

```sh
adb -s SERIAL shell am broadcast --receiver-foreground \
  -a dev.phosphor.mobil3.ROOT_AUTH_PROBE \
  -n dev.phosphor.mobil3.debug/dev.phosphor.mobil3.SelfTestReceiver
```

5. Read the private receipt after this attempt completes:

```sh
adb -s SERIAL exec-out run-as dev.phosphor.mobil3.debug \
  cat files/root-authorization.json
```

6. Require a fresh timestamp and the installed build identity, not just an existing file.
7. Compare app UID/PID/context with the real package process and retained install receipt.
8. Preserve the receipt, check no helper remains, and compare preferences with the pre-test backup.

The `run-as` command in step 5 only reads evidence. It does not execute the root probe.

## Receipt fields

The one-shot uses `status`, `tool=pm3-root-authorization`, `version=1.0.0`, and RFC3339 `ts`. Errors include `error` and nonempty `fix`. `data` contains package, build commit, app UID/PID/process context, outcome, fixed command, attempted locations, bounded launch errors, nullable exit code, bounded stdout/stderr, cleanup confirmation, elapsed milliseconds and `capture_tested=false`. It contains no audio or authorization token. AtomicFile replaces the private receipt transactionally. There is one active developer self-test at a time.

## Checks

- Initial implementation: 11 debug probe tests plus existing Android tests, lint, assembleDebug and checkEngine passed.
- Exact `bf9b8f2` build passed all 13 probe tests, existing Android tests, lint, assembleDebug and checkEngine. The installed readback SHA-256 is `1ae6992d0f80667676c2d54715c047a2fb049a6adcb00552c56bb5d90653a520`. Its signer matches the baseline.
- Tests use the actual command observer with host child processes for success and timeout, plus injected process fixtures for malformed/denied/overflow/read-failure states. None is a root or Android audio acceptance claim.

## Actual S25 result and next discriminating test

At 01:21:45 UTC the exact installed probe ran in PID 17076, UID 10401, SELinux `untrusted_app` context. All five fixed standard su entries returned ENOENT. The receipt reports `su_unavailable`, ten milliseconds, confirmed cleanup and `capture_tested=false`. This is real normal-app evidence, not a shell/run-as root attempt. Preferences before install, after install and after the probe all match the original archive byte-for-byte. Projection remained null and no su/id child remained. Raw receipts stay in the private recovery directory.

Read-only provider inspection then established that the preexisting logcat override identifies itself as `ksud 3.2.5`. Its own `debug info` reports driver 32525, UAPI 2, LKM and late-load flags. Its `debug su --help` exposes the provider's root-shell entrypoint. Reading the executable for a SHA-256 was denied, so no binary/source identity match is claimed.

The matching upstream sources establish the intended interface:

- [3.2.5 command dispatch](https://github.com/tiann/KernelSU/blob/v3.2.5/userspace/ksud/src/cli.rs)
- [3.2.5 grant_root shell](https://github.com/tiann/KernelSU/blob/v3.2.5/userspace/ksud/src/su.rs)
- [3.2.5 driver grant request](https://github.com/tiann/KernelSU/blob/v3.2.5/userspace/ksud/src/ksucalls.rs)

The separately invoked `KSU_AUTH_PROBE` action first checks exact provider version under the app UID, then supplies the same fixed identity command to `debug su` through private stdin. It never uses `--global-mnt`, changes the grant/profile, modifies the executable, or creates a product dependency on this device-specific path. This is not automatic fallback from standard su.

### Compatibility probe passed on the exact installed artifact

At 01:32:35 UTC, source `ff7067e019cb` returned `root_granted`, exit 0 and stdout `0` through the fixed KernelSU provider command. The parent was normal app UID 10401, PID 22323, `untrusted_app` context. The provider attempt completed in 210 ms with confirmed process and output-reader termination. No Android projection request, recording, grant/profile change, system write or settings mutation occurred.

The Gradle wrapper passed all 17 probe tests, existing Android tests, lint, assembleDebug and checkEngine at that exact source. `dev/pm3` installed and read back APK SHA-256 `31d90be7dd58522148397302f61686806b19e842c4507cfc0d878e02601249d3`. Signer SHA-256 remains `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d`. All pre/post preference archives still match the original `1f3fcf264ead0a0b3823a2c31f8c512842c9e25501c082ce659ed2b2fa3d1a72` hash.

Post-probe process inspection found no remaining child of Phosphor. The root `logcat sulogd` PID 10693 was already 15 days old, not created by this probe, and was left untouched. Projection remained null. Global auto-brightness, timeout and screensaver settings were unchanged. The user had switched to Telegram, so the coordinator did not force a launcher/UI restoration.

Private evidence: `ksu-auth-ff7067e-install.json`, `ksu-auth-ff7067e-result.json`, `ksu-auth-build.log`, `ksu-auth-postinstall-preferences.tar` and `ksu-auth-postprobe-preferences.tar` in the preserved recovery directory. This is app-origin authorization evidence, not audio acceptance or a shipping dependency on the overridden executable.

### Namespace prerequisite and fresh native regression

The 01:10 read-only Phosphor profile evidence shows Default, not a custom root profile. The coordinator inspected KernelSU commit `b0bc817b4e966aa6aa830834eaf6ef765d821d40`: `kernel/policy/allowlist.c` initializes the default root profile with `KSU_NS_INHERITED`, and `kernel/infra/su_mount_ns.c` performs no namespace operation for that value. Grant itself invokes this profile-dependent function. Therefore the initial trial relies on the existing Default profile evidence, not merely the absence of mount calls in Phosphor. No profile change is authorized. Record before/after namespace identity during the packaged-helper trial and stop on a mismatch.

At 01:54:36 UTC the existing DUMP-protected native self-test completed freshly on `ff7067e`. Both output mtimes changed from `1785912063` to `1788832476`. The result reported 3,999 segments, 45,853 lit pixels and pixel FNV-1a `4cd2eb49f325d649`, with `ok=true`. An unknown broadcast action left the root-authorization receipt byte-identical. At 02:04 UTC the post-smoke preference archive still matched the baseline SHA-256, and projection was null. This is actual native offscreen regression evidence, not root PCM or HDR evidence.

Private receipts include `native-smoke-ff7067e.json`, `selftest-20260907-185437.png`, and `native-smoke-post-preferences.tar`. Research handoffs cover the fixed two-process supervisor and exact AudioPolicy sequence. The initial capture rule must include the original Phosphor app UID and controlled media/game fixtures only. Do not capture other apps, calls or private messages during this feasibility test.

## Recovery and remaining work

Keep the original baseline APK and private app-state backup. Same-package debug replacement must preserve signer and preferences. Revert only this owned source checkpoint if needed. Do not uninstall, clear data, change kernel/root configuration, or alter the logcat override.

R01 remains open until the actual root helper produces real PCM, keeps audible output, handles opted-out/OEM failures truthfully, follows lifecycle/FGS ownership and recovers routes/processes. The probe is not a product root toggle or a substitute for those checks.
