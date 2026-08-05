# Android lint dispositions

**Measured:** 2026-08-05

`./gradlew --no-daemon :app:lintDebug` passes with minimum SDK 29, compile SDK 36, and target SDK 36.

## Compatibility decisions

- `Surface.setFrameRate` runs only on API 30 and newer.
- `Activity.display` runs only on API 30 and newer; Android 10 uses `WindowManager.defaultDisplay`.
- Rounded-corner APIs run only on API 31 and newer.
- Notification-listener detail settings run only on API 30 and newer.
- Picture-in-picture uses explicit params below API 31 and auto-entry on API 31 and newer.
- Full-display `MediaProjectionConfig` runs only on API 34 and newer.

## Measured result

The cleanup reduced lint from 40 warnings and 32 hints to 12 warnings and no hints. No lint baseline or source suppression hides them.

| Warning | Count | Disposition |
|---|---:|---|
| `AndroidGradlePluginVersion` | 2 | Gradle 9.3.1 and AGP 9.1.1 are the tested wrapper pair. Upgrade them together in a dedicated toolchain change, not during product cleanup. |
| `GradleDependency` / `NewerVersionAvailable` | 3 | Core KTX, Lifecycle, and the JVM test JSON library stay on the versions exercised by this receipt. Upgrade each with its affected tests. |
| `OldTargetApi` | 1 | Target SDK 36 is the Android 16 and 2026 Play production target selected by the product contract. Do not target a later platform before its behavior changes are tested. |
| `ChromeOsAbiSupport` | 1 | The current device matrix is ARM64 Android. Adding x86_64 requires a second Rust/NDK artifact and becomes required only when ChromeOS joins the product scope. |
| `ModifierParameter` | 2 | `Mono` and `Prose` retain their established positional `size` call shape. Reordering this internal UI API would touch more than 100 call sites without changing runtime behavior. |
| `UseKtx` | 3 | These editors use synchronous `commit()` results to preserve rollback and refusal behavior. The KTX editor helper returns no commit result, so conversion would weaken the contract. |

The API 29 adaptive icon is now the only launcher icon resource. Unreachable pre-adaptive raster fallbacks were deleted instead of suppressing five `IconLauncherShape` warnings.

## Gate

```bash
source scripts/env.sh
./gradlew --no-daemon :app:lintDebug
```

The final signed release must run release lint again with approved signing inputs and archive the generated report with the release receipts.
