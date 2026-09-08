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
