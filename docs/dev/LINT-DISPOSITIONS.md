# Android lint dispositions

Date: 2026-07-28

## Scope and evidence

The release baseline is `app/build/reports/lint-results-playRelease.txt`, measured before this work at **0 errors, 48 warnings, and 30 hints**.

The required verification report is `app/build/reports/lint-results-playDebug.txt`, measured after this work at **0 errors, 41 warnings, and 30 hints**.

The supplied warning breakdown omitted three Compose warnings that were present in the baseline report: two `ModifierParameter` warnings and one `UseOfNonLambdaOffsetOverload` warning. They are included below so the baseline rows sum to 48.

`playDebug` has one more `UseKtx` warning than the release baseline because concurrent remote-host work added a warning in `RemoteHostStore.kt`. This task did not modify that protected file. The net warning reduction is seven because eight baseline warnings were removed and one debug warning was added outside this task.

## Warning ledger

| Warning ID | Play release baseline | Verified play debug | Disposition |
|---|---:|---:|---|
| `AndroidGradlePluginVersion` | 2 | 2 | **deferred-with-reason**: Gradle and AGP upgrades are dependency churn outside this release-hygiene task. They require their own compatibility pass. |
| `ApplySharedPref` | 0 | 0 | **suppressed-with-reason**: the one migration use was introduced during this work and locally suppressed. Synchronous `commit()` is required to copy an excluded value before removing its backed-up source. |
| `ChromeOsAbiSupport` | 1 | 1 | **deferred-with-reason**: arm64-only is deliberate first-release scope. `docs/dev/GOOGLE-PLAY-PUBLISHING-PLAN.md` section 2.2 excludes x86_64 and ChromeOS support from the initial release. |
| `DataExtractionRules` | 1 | 0 | **fixed**: the application now references Android 12+ data extraction rules and legacy full-backup rules. |
| `DefaultLocale` | 1 | 0 | **fixed**: the gain readout uses `Locale.ROOT`. It is an instrument value with a stable decimal format, not localized prose. |
| `DiscouragedApi` | 1 | 0 | **fixed**: removed the redundant manifest `screenOrientation="unspecified"`. Runtime orientation policy remains adaptive. |
| `ExportedReceiver` | 0 | 0 | **suppressed-with-reason**: the debug-only `SelfTestReceiver` must accept the explicit receipts broadcast. Release variants do not contain it. |
| `ExportedService` | 1 | 0 | **suppressed-with-reason**: `PlaybackService` is intentionally exported for Media3 external controllers. The manifest contains a local justification and `tools:ignore="ExportedService"`. |
| `GradleDependency` | 2 | 2 | **deferred-with-reason**: `core-ktx` and lifecycle remain pinned until `platforms;android-37` publishes, as recorded in `docs/SERIOUS-TODOS.md`. |
| `IconLauncherShape` | 5 | 5 | **deferred-with-reason**: launcher artwork changes are packaging and asset churn outside this manifest and backup-policy task. |
| `ModifierParameter` | 2 | 2 | **deferred-with-reason**: the established `Mono` and `Prose` APIs have many positional call sites. Reordering optional parameters is non-functional Compose style churn and is not release correctness work. |
| `NewerVersionAvailable` | 1 | 1 | **deferred-with-reason**: the `org.json` dependency update is dependency churn outside this task. |
| `ObsoleteSdkInt` | 2 | 0 | **fixed**: removed the impossible pre-Android-12 branch under `minSdk = 35`, and moved adaptive icon XML from `mipmap-anydpi-v26` to `mipmap-anydpi`. |
| `OldTargetApi` | 1 | 1 | **deferred-with-reason**: `compileSdk` and `targetSdk` are both 36. Target 37 waits for the stable platform and a dedicated behavior-compatibility pass. |
| `PictureInPictureIssue` | 1 | 0 | **fixed**: PiP parameters now set both automatic entry and a laid-out source rectangle hint. |
| `UseKtx` | 23 | 24 | **deferred-with-reason**: explicitly out of scope as dependency and API-surface churn. The verified debug count includes one new warning in protected concurrent remote-host work. |
| `UseOfNonLambdaOffsetOverload` | 1 | 0 | **fixed**: the animated stone content now uses the lambda `Modifier.offset` overload. |
| `UseTomlInstead` | 3 | 3 | **deferred-with-reason**: version-catalog migration is Gradle configuration churn outside this task. |

Baseline total: **48 warnings**. Verified total: **41 warnings**.

## Backup policy

Android backup XML selects files, not individual SharedPreferences keys. The previous `phosphor.prefs` file mixed portable settings with private causal state and device runtime metadata. This work separates those boundaries before applying file-level rules.

### Included

The rules include the SharedPreferences domain so the user's genuine instrument preferences restore. These include the explicit `SettingsArchive` allowlist such as mode, beam, grid, gain, glow, room, HUD mode, orientation locks, remote latency policy, and custom beam settings.

The backed-up user settings file is:

- `phosphor.prefs.xml`

### Excluded

Both cloud backup and device transfer exclude:

- `remote_hosts.xml`: relay endpoints describe one user's private network. They can be wrong and privacy-leaking on another device.
- `entitlement.xml`: reserved for billing. A trial clock or cached entitlement must come from the billing authority, not a restored snapshot.
- `phosphor.causal_state.xml`: contains causal audit, idempotency, receipt counters, and authority runtime state. `StoreHealth` exists as an in-memory model but is not part of `PhosphorStoreImage`, so no health flag is persisted or restored.
- `phosphor.runtime.xml`: contains device consent acknowledgements, last source, random-track runtime state, and calibration metadata.

The legacy `backup_rules.xml` mirrors the same split. The current `minSdk` is 35, so Android 12+ rules are the active contract. The legacy file is a documented compatibility backstop, not an assertion that pre-12 devices are supported.

### Alignment with `SettingsArchive`

The split follows `SettingsArchive`'s policy that runtime metadata, endpoints, consent tokens, purchase data, and authorization material do not travel. `hud_mode` remains portable because it is explicitly allowlisted. The private envelope and legacy `nerd_hud` bootstrap flag remain in the excluded causal file.

One narrow difference remains by design: the crash-recovery marker for an in-progress verified HUD settings import stays in `phosphor.prefs`. It must commit atomically with imported settings. It contains operation recovery data, not endpoints, purchase state, authorization material, or a trial clock.

## Exported Media3 service evidence

The official Android Media3 background-playback guide states that `MediaSessionService` lets Google Assistant, system media controls, peripheral media buttons, and companion devices discover and control playback. Its manifest example declares the service with `android:exported="true"` and the `androidx.media3.session.MediaSessionService` action.

Source: <https://developer.android.com/media/media3/session/background-playback>

The service remains exported. A permission was not added because it would block the external platform controllers that the service exists to serve. `PlaybackService.onGetSession` remains the Media3 connection boundary.
