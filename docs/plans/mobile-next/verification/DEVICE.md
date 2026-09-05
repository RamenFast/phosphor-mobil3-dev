# Authorized device procedure

This is future execution text. Root used no device command to author the roadmap.
Ben's 2026-09-05 quiet boundary remains active until he changes it.
Read [EXECUTION](../EXECUTION.md). `PM3_DEVICE_WINDOW_OK=yes` records permission and does not grant it.

## Prepare without guessing an artifact

1. Root gets a permitted device/audio window and explicitly selects the device.
2. Record its package, OS/build, API, hardware and current debug app identity privately.
3. Preserve the exact accepted APK, signer, settings export and task-changed system settings.
4. Build from reviewed clean source. Copy the app and instrumentation APK into `RUN` before running other fixtures.
5. Record full source commits, both APK hashes, signer hashes, toolchains and test case count expectations.
6. Set app/test/rollback APK paths, SHA-256 values, expected version codes and signer hashes from that immutable receipt.
7. Run only the class named by the current phase. Restore task state even when assertions fail.

The instrumentation APK does not exist at planning time. Phase 01 configures its exact package and compiles it.
Locate its one output under `app/build/outputs/apk/androidTest/debug` after that build.
Reject zero or multiple APK matches. Inspect its manifest and signer before retaining it.
Never take the newest filesystem timestamp as an artifact identity.

## Offline package guard, before every install

⚠ Current `dev/pm3 install` invokes adb before checking the selected package. Its debug profile is not a pre-install safety guard.
Root verified the commands below against the retained B2 APK without using a device.
Phase 01 adds equivalent checks to pm3. Keep these plan guards until that implementation is separately verified.
The XML parser is installed Ruby REXML 3.2.5. Android tools come from the existing `scripts/env.sh` environment.

```bash
verify_debug_apk() (
  set -euo pipefail
  apk="$1"; package="$2"; version="$3"; signer="$4"; role="$5"
  [[ "$signer" =~ ^[0-9a-f]{64}$ ]] || exit 2
  apkanalyzer manifest print "$apk" | ruby -rrexml/document -e '
    package, version, role = ARGV
    doc = REXML::Document.new(STDIN.read)
    root = doc.root
    ns = "http://schemas.android.com/apk/res/android"
    value = ->(element, name) { element&.attributes&.get_attribute_ns(ns, name)&.value }
    abort "wrong package" unless root.attributes["package"] == package
    abort "wrong version" unless value.call(root, "versionCode") == version
    abort "not debuggable" unless value.call(root.elements["application"], "debuggable") == "true"
    case role
    when "app"
      abort "not debug product" unless package == "dev.phosphor.mobil3.debug"
      abort "unexpected instrumentation" unless root.get_elements("instrumentation").empty?
    when "test"
      abort "not test package" unless package == "dev.phosphor.mobil3.debug.test"
      nodes = root.get_elements("instrumentation")
      abort "wrong runner count" unless nodes.length == 1
      abort "wrong runner" unless value.call(nodes[0], "name") == "androidx.test.runner.AndroidJUnitRunner"
      abort "wrong target" unless value.call(nodes[0], "targetPackage") == "dev.phosphor.mobil3.debug"
    else
      abort "unknown APK role"
    end
  ' "$package" "$version" "$role" || exit 2
  certs="$("$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs "$apk")" || exit 2
  mapfile -t digests < <(printf '%s\n' "$certs" | awk -F': ' '/certificate SHA-256 digest:/{print tolower($2)}')
  test "${#digests[@]}" -eq 1 || exit 2
  test "${digests[0]}" = "$signer" || exit 2
)
```

Expected: wrong package, release/nondebug APK, version, signer, runner or target fails before any device command.
Hash equality alone cannot distinguish a correctly hashed but mistakenly selected production APK.

## Copy-pasteable explicit-device runner

This function is a procedure, not a shipped CLI. It adds no product command surface.
Define it in the Bash shell initialized by EXECUTION.md. `TEST_CLASS` is supplied by the phase or matrix below.

```bash
run_device_case() (
  set -euo pipefail
  test "${PM3_DEVICE_WINDOW_OK:-no}" = yes || exit 2
  : "${PM3_SERIAL:?select one explicitly verified device}"
  : "${CANDIDATE_APK:?pin retained app APK}"
  : "${TEST_APK:?pin retained instrumentation APK}"
  : "${CANDIDATE_SHA:?pin app SHA256}"
  : "${TEST_SHA:?pin instrumentation SHA256}"
  : "${CANDIDATE_VERSION:?pin expected app version code}"
  : "${TEST_VERSION:?pin expected test version code}"
  : "${CANDIDATE_SIGNER:?pin expected app signer SHA256}"
  : "${TEST_SIGNER:?pin expected test signer SHA256}"
  : "${TEST_CLASS:?pin the phase test class}"
  : "${RUN:?use the private receipt directory}"
  [[ "$TEST_CLASS" =~ ^dev\.phosphor\.mobil3\.[A-Za-z0-9_.]+$ ]] || exit 2
  printf '%s  %s\n' "$CANDIDATE_SHA" "$CANDIDATE_APK" "$TEST_SHA" "$TEST_APK" | sha256sum -c - || exit 2
  verify_debug_apk "$CANDIDATE_APK" dev.phosphor.mobil3.debug "$CANDIDATE_VERSION" "$CANDIDATE_SIGNER" app || exit 2
  verify_debug_apk "$TEST_APK" dev.phosphor.mobil3.debug.test "$TEST_VERSION" "$TEST_SIGNER" test || exit 2
  cd "$M" || exit 2
  dev/pm3 --profile debug --serial "$PM3_SERIAL" install "$CANDIDATE_APK" || exit 2
  adb -s "$PM3_SERIAL" install -r -t "$TEST_APK" || exit 2
  paths="$(adb -s "$PM3_SERIAL" shell pm path dev.phosphor.mobil3.debug.test)" || exit 2
  mapfile -t bases < <(printf '%s\n' "$paths" | tr -d '\r' | sed -n 's/^package://p')
  test "${#bases[@]}" -eq 1 || exit 2
  adb -s "$PM3_SERIAL" pull "${bases[0]}" "$RUN/instrumentation-installed.apk" || exit 2
  printf '%s  %s\n' "$TEST_SHA" "$RUN/instrumentation-installed.apk" | sha256sum -c - || exit 2
  log="$RUN/instrumentation-$TEST_CLASS.txt"
  test ! -e "$log" || exit 2
  adb -s "$PM3_SERIAL" shell am instrument -w -r -e class "$TEST_CLASS" \
    dev.phosphor.mobil3.debug.test/androidx.test.runner.AndroidJUnitRunner | tee "$log" || exit 2
  if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' "$log"; then exit 2; fi
  grep -Eq 'OK \([1-9][0-9]* tests?\)' "$log" || exit 2
)
```

Example, after Phase 01 authors its test and all permission/artifact variables are set:

```bash
export TEST_CLASS=dev.phosphor.mobil3.InstrumentHarnessTest
run_device_case
```

Expected: app hash/signer readback succeeds through pm3, test APK readback matches, actual named tests run with no failure or missing cases.
Inspect named test results and ignored tests too. The final text match alone cannot prove required case coverage.
The runner uses explicit `adb -s` rather than assuming connected Gradle tasks honor one selected device.
It does not dismiss Android consent or grant permissions. Those flows remain ordinary UI interactions in a permitted window.
Each retry uses a fresh `RUN` attempt subdirectory so evidence is not overwritten.

## Mandatory scenario matrix

| ID | Exercise on the exact candidate | Required evidence |
|---|---|---|
| D1 | Five capture-to-mic cycles, denied consent, ordinary retry, capture revocation, newer-source replacement | Correct owner ordering, one recorder, positive sample progression, moving beam, truthful error. No forced hardware failure. |
| D2 | Local direct/tree, invalid entries and corrupt tail, paused full-ring seek, bursts, same-path reopen, tree cancellation | Latest owner wins, EOF differs from error, no stale face, deadlock or ghost audio. Use representative provider scale first. |
| D3 | UI and MediaSession across capture/local/remote/mic transitions | Source, actions, title and playing truth agree with each real authority. Selection and silence are not inferred playback. |
| D4 | Settings import/export, failed import, recreation, process restart, same-package debug update | User edits survive, incomplete import never claims success, no consent/host leakage, no unsolicited source start. |
| D5 | Real controls, keyboard/D-pad, TalkBack/Switch Access, large font, reduced motion | Names, ranges, focus, unavailable reasons, return focus and sharp visual geometry remain usable. |
| D6 | Fixed scenes, surface-inclusive screenshots, rotation, PiP, modal open/close, surface recreation | Separate chrome and beam metrics pass. Renderer state is not inferred from a Compose-only screenshot. |
| D7 | Default task removal, permitted linger, audio focus/noisy route, source stop and wake release | Accepted B21 ownership, idempotent release, sleep eligibility, no survivor or COMMAND_RELEASE crash. |
| D8 | Selected relay live audio/file/folder, disconnect/reconnect, stream toggle, stop during startup | Existing protocol remains compatible, no new child/thread leaks or stale session publication. Requires client-free service window. |
| D9 | Five paired performance baselines and candidate comparisons | Matching device/driver/scene identities, resolved measurement uncertainty, no missing native presentation metric. |

API coverage remains explicit: API 29, 31, 34 and 36 permission/lifecycle/PiP branches need actual suitable test environments.
S25 acceptance does not establish older API behavior. Missing hardware/emulators remain BLOCKED in compatibility, not skipped green.
True screen-lock/PIN testing needs separate approval. Never manipulate keyguard to complete a row.
Do not repeat the 20,001-file provider ANR fixture without a separately justified bounded test.

## Restoration and rollback

Stop only task-started sources. Restore saved preferences and the exact system settings changed during this attempt.
Keep logs and failed artifacts. Remove only known task-created fixtures after preserving their receipt.
If debug rollback is signer/version compatible and the device window remains permitted:

```bash
test "${PM3_DEVICE_WINDOW_OK:-no}" = yes || exit 2
: "${PM3_SERIAL:?select authorized device}"
: "${ROLLBACK_APK:?pin prior verified compatible debug APK}"
: "${ROLLBACK_SHA:?pin prior APK SHA256}"
: "${ROLLBACK_VERSION:?pin prior version code}"
: "${ROLLBACK_SIGNER:?pin prior signer SHA256}"
printf '%s  %s\n' "$ROLLBACK_SHA" "$ROLLBACK_APK" | sha256sum -c - || exit 2
verify_debug_apk "$ROLLBACK_APK" dev.phosphor.mobil3.debug "$ROLLBACK_VERSION" "$ROLLBACK_SIGNER" app || exit 2
dev/pm3 --profile debug --serial "$PM3_SERIAL" install "$ROLLBACK_APK" || exit 2
```

Verify rollback through pm3 readback and restore portable settings through the supported import flow when necessary.
A blocked downgrade calls for a signer-compatible forward fix, not uninstall, data clear or password reset.
