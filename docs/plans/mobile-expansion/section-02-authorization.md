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

The separately invoked `KSU_AUTH_PROBE` action first checks exact provider version under the app UID, then supplies the same fixed identity command to `debug su` through private stdin. It never uses `--global-mnt`, changes the grant/profile, modifies the executable, or creates a product dependency on this device-specific path. This is not automatic fallback from standard su. Its build and actual result are pending.

## Recovery and remaining work

Keep the original baseline APK and private app-state backup. Same-package debug replacement must preserve signer and preferences. Revert only this owned source checkpoint if needed. Do not uninstall, clear data, change kernel/root configuration, or alter the logcat override.

R01 remains open until the actual root helper produces real PCM, keeps audible output, handles opted-out/OEM failures truthfully, follows lifecycle/FGS ownership and recovers routes/processes. The probe is not a product root toggle or a substitute for those checks.
