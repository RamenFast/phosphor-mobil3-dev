# play-store line: S25 base reproduction (2026-11-01)

## Why this branch exists

Ben, 2026-11-01 17:45 PST:

> branch off from an older version (the version installed on my phone) and build from there, gpt models kinda messed up the code and I want to get this app ready for a play store release at some point.

The `play-store` branch starts at the exact source of the app on Ben's Galaxy S25. It does not start at `master`. `master` is 152 commits ahead of this base (2026-09-07 22:37 to 2026-09-29 23:06).

## The installed build (read from the phone)

Device: SM-S931U, adb serial `R3CY90HEZ3M` (USB).

| field | value |
|---|---|
| package | `dev.phosphor.mobil3.debug` |
| versionName / versionCode | `2.0.0-debug` / `2000000` |
| lastUpdateTime | 2026-09-07 22:39:49 |
| base.apk SHA-256 | `4a370170cda5410caf8abc82d8cd9a997fd0777c718cbd1ba31c2cd3aa9127fa` |
| `BuildConfig.BUILD_COMMIT` | `06f84e2eb7da` (no `-dirty` suffix, so the tree was clean) |
| `BuildConfig.ROOT_AUDIO_BUILD` | `fbff112a911c6f3a421f10a5e680371a3b3046ccb567d28e4088fae8959d0f46` |
| signer | `CN=Android Debug`, cert SHA-256 `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d` (`~/.android/debug.keystore`) |

`BUILD_COMMIT` was read with `dexdump -f` from the pulled APK.

## Source of the build

- Mobile: `06f84e2eb7da46c758e9f8b388c3537e6c67905d`. Branch `play-store`, tag `installed/s25-2026-09-07`.
- Engine (sibling `../phosphor`, desktop repo): `7729990bb29f0167ef906d0fbdb44e1e91206955`. This is the last engine commit before the install. The next engine source change (`084f5d9`, 2026-09-08 00:42) came after it.

The Cargo path dependencies point at `../../phosphor/crates/*`, so the engine checkout must sit beside this repository. The working pair is:

```
~/Dev/builds/wt/phosphor-play/phosphor-mobil3   branch play-store
~/Dev/builds/wt/phosphor-play/phosphor          engine, detached at 7729990
```

## Reproduction

1. `./gradlew :app:assembleDebug` in the pair: 230 of 231 APK entries are byte-identical to the installed APK (CRC and size). This includes all seven dex files, `resources.arsc`, the manifest, assets and `libphosphor_root_launcher.so`. Identical dex means `BUILD_COMMIT` and `ROOT_AUDIO_BUILD` match.
2. The one other entry, `lib/arm64-v8a/libphosphor_mobil3_core.so`, differs only because of the checkout location. Cargo hashes the location of a path dependency into the crate metadata, and that changes symbol hashes and code layout.
3. Built at the original paths, the core library is byte-identical. The command binds the pair over the original paths in a private mount namespace, so the real checkouts do not change:

```bash
W=~/Dev/builds/wt/phosphor-play; R=/home/ben/Dev/ClaudeWorkspace; OUT=<scratch>
bwrap --dev-bind / / \
  --bind $W/phosphor $R/phosphor \
  --bind $W/phosphor-mobil3 $R/phosphor-mobil3 \
  --bind $R/phosphor-mobil3/.toolchain $R/phosphor-mobil3/.toolchain \
  --bind $OUT/target $R/phosphor-mobil3/rust/target \
  -- bash -c "cd $R/phosphor-mobil3/rust && ANDROID_NDK_HOME=$R/phosphor-mobil3/.toolchain/Sdk/ndk/28.2.13676358 cargo ndk -t arm64-v8a -P 29 -o $OUT/out build --locked"
llvm-strip --strip-unneeded -o core.so $OUT/out/arm64-v8a/libphosphor_mobil3_core.so
```

Result: SHA-256 `7ec71019436ec691fd2324939b02c8ca1ceac32406704c5eeced7df376321568`, the same as the installed library. The engine pin is proven, not inferred.

4. The rebuilt APK has the same signer as the installed app. A debug install from this branch upgrades in place and keeps the app data on the phone.

## Baseline checks on this base

| check | result |
|---|---|
| `:app:testDebugUnitTest` | 487 tests, 0 failures, 0 errors, 0 skipped |
| `:app:lintDebug` | 0 errors, 20 warnings: GradleDependency 5, UseKtx 3, NewerVersionAvailable 3, ModifierParameter 2, AndroidGradlePluginVersion 2, WakelockTimeout 1, StaticFieldLeak 1, SdCardPath 1, OldTargetApi 1, ChromeOsAbiSupport 1 |
| `scripts/check-play-boundary.sh source --json` | ok, 12 source checks |
| `:app:checkEngine` | exit 0 |
| `cargo test --manifest-path rust/Cargo.toml --locked` | 73 passed, 0 failed (2 suites) |
| `cargo test --manifest-path relay/Cargo.toml --locked` | 43 passed, 0 failed |
| `scripts/test-pm3.sh` | fixtures passed |
| `scripts/test-play-boundary.sh` | production boundary fixtures passed |
| `scripts/test-release-gates.sh` | exit 0 |

Not run yet: `scripts/ship-check.sh` and the artifact boundary. Both need a signed release bundle, and the artifact boundary also needs a Play-upload signer, which does not exist yet.

## Worktree setup

- `local.properties` is a symlink to the root checkout's file. It holds signing inputs, so it is never copied.
- `.toolchain` is a symlink to the root checkout's toolchain. The tracked pattern `.toolchain/` matches only a directory, so the symlink is ignored through the repository's `.git/info/exclude`. Without that, the build stamps `BUILD_COMMIT` as `-dirty`.
- `source scripts/env.sh` works in the worktree through the symlink.
