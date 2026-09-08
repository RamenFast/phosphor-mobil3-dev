# Section 2 stereo trial receipts

## Clean candidate, 2026-09-08 at05:21 UTC

This candidate is ready for a finite own-UID experiment. It is not stereo, SoundCloud, latency or product acceptance.

| Identity | Exact value |
|---|---|
| Mobile source | `06f84e2eb7da46c758e9f8b388c3537e6c67905d` |
| Clean source archive SHA256 | `93056c9d40934dfa70d5df0054faf7a6a56a7a863b98d1c6955a07d01da6dc7c` |
| App APK SHA256 | `4a370170cda5410caf8abc82d8cd9a997fd0777c718cbd1ba31c2cd3aa9127fa` |
| Companion androidTest APK SHA256 | `a74bb7e6cebde8e645c18e057a3ba0aaddecbaf10ccdd33c838ad126c390e0d9` |
| Debug manifest SHA256 | `2f8ed062c6c875e3e7eb851aa52f43000f668a1149438c54d926008f8e9388cc` |
| Original review SHA256 | `afb7399b6d863a6334810574607af263af37bc588e379f36c60520cabadf6eb0` |
| Correction addendum SHA256 | `f059a483f689da9961d7099d878545a5e930632a5d419d642a54343b00c7a890` |

Task809210e4xz committed the reviewed source, verified a clean tree, built both APKs together, and verified a clean tree afterward. The exact Gradle command was:

```bash
source scripts/env.sh
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug \
  :app:assembleDebug :app:assembleDebugAndroidTest :app:checkEngine \
  :app:buildReleaseRootAudioLauncher
```

Result: exit0, BUILD SUCCESSFUL,487Android unit tests with zero failures/errors, lint, engine and both native helper variants passed. Coordinator30native host tests and264Java assertions passed. Independent review re-ran30native tests and the57stereo subset of thoseJava assertions. These are source checks, not hardware substitutes.

Private recovery root remains `dev/scratch/mobile-expansion-20260908T001819Z/`. Retained files are `root-stereo-06f84e2eb7da.apk`, its `-androidTest.apk` companion, `-freeze.json` and `-retained.sha256`. The build log is `stereo-clean-build.log`. No new signing identity or release artifact was created.

## Connection and quiet-boundary observations

At05:22 UTC task97018036jr retained the candidate and then failed because USB serial `R3CY90HEZ3M` disappeared. It stopped before installation. The first unexecuted command had been rejected by the harness because scratch output paths used fixed variables. The justified retry created only owned host receipts. Neither attempt deleted user data.

At05:23 UTC wireless endpoint `100.102.2.83:5555` remained connected. Read-only properties matched hardware serial `R3CY90HEZ3M` and the original exact Samsung API36 build fingerprint. No pairing, grant, connectivity or phone setting changed.

At05:24 UTC task054733k0xs checked all retained hashes and the exact wireless hardware identity. Its audio preflight found an active external MEDIA AudioTrack, stereo44100Hz, in normal mode. The explicit quiet guard returned exit2 before preferences were archived or installation began. No tone, helper or policy was started. The installed package remains the earlier `ccee7c8` product APK, not this stereo candidate.

The original USB runner is `run-root-stereo-check.sh`, SHA256`82eabce0ccd0052e93df3c25a728ad58112ec3f360a72fa637d75e8870cac107`. The separately retained wireless runner is `run-root-stereo-check-wireless.sh`, SHA256`a54117c69440d5ecb8701dc3348e470c423cc0680ad51779f34d291f765e0e73`. Its only connection changes are the explicit wireless serial and a required hardware-serial match. Both passed Bash syntax and shellcheck-x. Neither runner was executed or independently reviewed. Each checks the freeze, installed APK hash, idle audio, fresh fixed-action receipt, cleanup and unchanged preferences/volume/route/fixed-volume state.

## Next acceptance boundary

Keep current playback untouched. Resume installation and one SYSTEM trial only after a fresh quiet preflight. Reuse the retained exact candidate even if another workstream has changed source. Do not rebuild or install a dirty intermediate artifact. Require full clean retirement before another trial. A clean successful SYSTEM result permits the corresponding finite NONE trial, not unrestricted SoundCloud capture.

Actual stereo, format/rate progression, gain, physical audibility, additional latency, source/display alignment and abnormal cleanup remain unmeasured for this candidate. The quiet-window deferral is not proof that the method fails. Ben's conditional root-ready fallback remains available if bounded technical investigation later stays blocked.

Independent R02 HUD work may continue while the device boundary is occupied. R09 mixing still awaits the root format/clock decision. Protected system/vendor/boot/vbmeta paths remain read-only, and no physical volume or other app playback is controlled.

## Installed candidate and first SYSTEM trial, 05:40 UTC

A fresh05:38 quiet preflight found no active player. Task `9872769kyw` installed retained `06f84e2eb7da` through the verified wireless S25 endpoint. `dev/pm3` read back APK SHA256 `4a370170cda5410caf8abc82d8cd9a997fd0777c718cbd1ba31c2cd3aa9127fa` and the existing signer. Preferences before and after matched the original archive hash `1f3fcf264ead0a0b3823a2c31f8c512842c9e25501c082ce659ed2b2fa3d1a72`. This supersedes the earlier installed-state deferral only.

Task `015380v664` ran one fixed own-UID SYSTEM trial. It returned exit4 with `capture: IllegalArgumentException: monitor_queue_overrun`. Actual capture and monitor each initialized48000Hz,2channels,PCM16. Monitor identity was proven as UID0, the helper PID and a unique player/session, distinct from originalUID10401. Tagged LOOP_BACK-only registration returned0. The physical output remained the existing Bluetooth route. This proves initialization, not independent stereo or audible success.

The monitor requested960frames but received8793frames, about183.2ms of client capacity. The record buffer was3024frames. The helper read and wrote4992frames in81ms before its unchanged4800frame/100ms queue guard stopped capture. Its last retained accepted queue highwater was4512frames. No steady-window frames were reached, so reported correlation1 and zero RMS cannot diagnose duplicated mono. Source head reached12974frames, well short of the240000frame fixture.

The helper reported clean monitor/record/policy teardown and the native supervisor reaped naturally, without forced kill. Independent process/policy/service checks found no residue. Preferences, volume, route and fixed-volume fields were preserved. No NONE or SoundCloud trial followed this failure. The failure is not permission to raise the queue limit.

Pinned AOSP Android16 `AudioTrack.java` lines2306–2362 describe a relevant startup condition: a newly created stream defaults to its full buffer capacity before it starts. Explicit effective-buffer reduction or a per-track start-threshold change can reduce this condition. The observed8793frame capacity exceeds our4800frame guard. The candidate did not record the actual threshold, so this is a source-backed hypothesis, not a measured cause. The next correction will configure and report only the owned monitor's effective buffer and start threshold before capture, retain the100ms guard, and fail before policy if Android cannot honor the bounded configuration.

## Reviewed startup-buffer freeze, 06:39 UTC

Task `217974blpq` built both debug APKs together from an isolated, clean checkout of`15bcbe50931a8a21514c9f5b17570da7eff940ed`. The sibling engine was separately pinned to`7729990bb29f0167ef906d0fbdb44e1e91206955`. Complete source manifests for both checkouts matched before and after the gate. Both remained Git-clean. The active HUD writer's source was not part of this build. Only the ignored Cargo cache and installed toolchain were reused under the coordinator's exclusive build slot.

| Evidence | SHA256 or result |
|---|---|
| Source archive | `298d19d2d887bfc0491176d842a20d4f18ab03a10e309e484b6991ed994b1354` |
| App APK | `4708959a55ef2c3ccac0e3ea97c29ce2b8db7cf2a7294f39c8ebf6913c7c2e86` |
| Companion androidTest APK | `611000a6516dbdb76ada7f984e43ea2d1297dec94d9496ffc8009545d2829903` |
| App signer | `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d` |
| apkanalyzer-decoded packaged manifest | `34b7a9996dbc13ed1cdf12ba6fb167d554c5918220055433d642f276772aac1e` |
| Helper content identity | `9fe902ac2be2f56e7146bc2b4702dc6bf993d61aa769c78c5f785327bec18ae4` |
| Packaged helper DEX JAR and declared digest | `5d844a3d7d50c6ccb240d5f349c7094ec3968f4442eeb56a462664343b0791c2` |
| Retained freeze JSON | `d001f3ee9ea8f52ba1399f75443a0e4b856003a2ff64c3eb8ba3d57a46f5c285` |
| Android unit gate | 487 tests, zero failures/errors |
| Other Android gates | lint, engine check, both debug APKs and both native helper variants passed |
| Root host checks | 30 locked native tests and436 Java assertions passed |
| Source boundary | 12 checks passed |

The retained files are `root-stereo-15bcbe50931a{,-androidTest}.apk`, `root-stereo-15bcbe50931a-freeze.json` and their checksum list in the existing private recovery directory. Build evidence is under `/home/ben/.jcode/scratch/stereo-freeze-15bcbe5.gyQcka`. Both BuildConfig files name`15bcbe50931a` without a dirty suffix. The companion APK was built, not installed or run as instrumentation.

At06:34, bounded read-only preflight found an unrelated active MEDIA player on the current Bluetooth route. No helper or policy mix remained, and preferences still matched the original archive byte-for-byte. No installation or new test audio followed. The candidate remains uninstalled. A later quiet preflight may use this reviewed immutable candidate with the exact v2 runner SHA`d0102fc1fccddede249f955371efb27e18d0daeebeeac257721b9285797cc880`. Do not rebuild changing HUD source for that trial. Stereo separation, audibility, gain, latency and SoundCloud acceptance remain open.
