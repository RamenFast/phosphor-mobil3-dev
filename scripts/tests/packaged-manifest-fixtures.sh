# shellcheck shell=bash
# Private fixtures sourced by scripts/test-play-boundary.sh. Uses only the existing SDK and bundletool.
prepare_packaged_fixtures() {
  SDK="${ANDROID_HOME:-$REPO/.toolchain/Sdk}"
  AAPT2="$SDK/build-tools/36.0.0/aapt2"
  if [ ! -x "$AAPT2" ] || [ ! -f "$SDK/platforms/android-36/android.jar" ]; then
    printf 'SDK fixture tools unavailable. Fix: bootstrap the pinned Android SDK.\n' >&2; exit 2;
  fi
  (cd "$REPO" && JAVA_HOME="$JDK" GRADLE_USER_HOME="${GRADLE_USER_HOME:-$REPO/.toolchain/gradle-user-home}" \
    ./gradlew --offline --console=plain :app:writeBoundaryBundletoolClasspath "-PboundaryClasspathOutput=$WORK/bundletool-classpath.txt") >"$WORK/fixture-gradle.log" 2>&1 || {
    cat "$WORK/fixture-gradle.log" >&2
    printf 'Fix: resolve the existing bundletool configuration with the Gradle wrapper.\n' >&2; exit 2;
  }
  BUNDLETOOL_CLASSPATH=$(cat "$WORK/bundletool-classpath.txt")
  cat >"$WORK/production.xml" <<'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="dev.phosphor.mobil3" android:versionCode="1" android:versionName="fixture">
  <uses-sdk android:minSdkVersion="29" android:targetSdkVersion="36"/>
  <permission android:name="dev.phosphor.mobil3.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" android:protectionLevel="signature"/>
  <uses-permission android:name="dev.phosphor.mobil3.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"/>
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"/>
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"/>
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>
  <uses-permission android:name="android.permission.RECORD_AUDIO"/>
  <application android:hasCode="false">
    <service android:name=".PlaybackService" android:exported="true" android:foregroundServiceType="mediaPlayback">
      <intent-filter><action android:name="androidx.media3.session.MediaSessionService"/></intent-filter>
    </service>
    <service android:name=".CaptureService" android:exported="false" android:foregroundServiceType="mediaProjection"/>
    <service android:name=".MicCaptureService" android:exported="false" android:foregroundServiceType="microphone"/>
    <service android:name=".RootCaptureService" android:exported="false" android:foregroundServiceType="specialUse">
      <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="User-started local capture"/>
    </service>
  </application>
</manifest>
XML
  sed 's/dev.phosphor.mobil3/dev.phosphor.mobil3.debug/;s/android:hasCode="false"/android:hasCode="false" android:debuggable="true"/' "$WORK/production.xml" >"$WORK/debug.xml"
  sed 's/android:hasCode="false"/android:hasCode="false" android:debuggable="true"/' "$WORK/production.xml" >"$WORK/debuggable.xml"
  sed 's#<application#<uses-permission android:name="android.permission.CAPTURE_AUDIO_OUTPUT"/><application#' "$WORK/production.xml" >"$WORK/permission.xml"
  sed 's/targetSdkVersion="36"/targetSdkVersion="35"/' "$WORK/production.xml" >"$WORK/wrong-sdk.xml"
  sed 's/foregroundServiceType="mediaProjection"/foregroundServiceType="mediaProjection|microphone"/' "$WORK/production.xml" >"$WORK/role-flags.xml"
  sed 's/protectionLevel="signature"/protectionLevel="signature|privileged"/' "$WORK/production.xml" >"$WORK/signature-flags.xml"
  local kind
  for kind in production debug debuggable permission wrong-sdk role-flags signature-flags; do
    "$AAPT2" link --manifest "$WORK/$kind.xml" -I "$SDK/platforms/android-36/android.jar" -o "$WORK/$kind.apk"
    "$AAPT2" link --proto-format --manifest "$WORK/$kind.xml" -I "$SDK/platforms/android-36/android.jar" -o "$WORK/$kind-proto.apk"
    mkdir -p "$WORK/$kind-module/manifest"
    unzip -p "$WORK/$kind-proto.apk" AndroidManifest.xml >"$WORK/$kind-module/manifest/AndroidManifest.xml"
    unzip -p "$WORK/$kind-proto.apk" resources.pb >"$WORK/$kind-module/resources.pb"
    (cd "$WORK/$kind-module" && zip -qr "$WORK/$kind-module.zip" manifest resources.pb)
    "$JDK/bin/java" -cp "$BUNDLETOOL_CLASSPATH" com.android.tools.build.bundletool.BundleToolMain build-bundle \
      "--modules=$WORK/$kind-module.zip" "--output=$WORK/$kind.aab" >"$WORK/$kind-bundle.log" 2>&1 || { cat "$WORK/$kind-bundle.log" >&2; exit 4; }
  done
  "$JDK/bin/java" -cp "$BUNDLETOOL_CLASSPATH" com.android.tools.build.bundletool.BundleToolMain validate \
    "--bundle=$WORK/production.aab" >"$WORK/production-validate.log" 2>&1
  printf 'Real AAPT2 APK and bundletool AAB fixtures prepared and bundle validated\n'
}

expect_boundary() {
  local expected="$1" error="$2" label="$3" output code
  shift 3
  set +e
  output=$("$REPO/scripts/check-play-boundary.sh" "$@" --json 2>"$WORK/case-stderr.txt")
  code=$?
  set -e
  if [ "$code" -ne "$expected" ]; then
    printf '%s: expected exit %s, got %s\n%s\n' "$label" "$expected" "$code" "$output" >&2; return 1
  fi
  printf '%s\n' "$output" | jq -e -s --arg error "$error" '
    length == 1 and (.[0] | if $error == "" then
      keys == ["data","status","tool","ts","version"] and .status == "ok"
      and (.data | keys == ["artifact","artifact_checks","manifest","mode","source_checks","trusted_runtime_exemptions"])
    else keys == ["error","fix","message","status","tool","ts","version"]
      and .status == "error" and .error == $error and (.fix | length > 0) end)' >/dev/null
  printf 'PASS %s (exit %s)\n' "$label" "$code"
  BOUNDARY_RESULT="$output"
}

check_packaged_fixtures() {
  local format kind member manifest_dir
  for format in apk aab; do
    if [ "$format" = apk ]; then member=AndroidManifest.xml; else member=base/manifest/AndroidManifest.xml; fi
    expect_boundary 0 '' "$format production, no detached manifest" artifact --artifact "$WORK/production.$format" --dependencies "$WORK/dependencies.txt"
    for kind in debug debuggable permission wrong-sdk role-flags signature-flags; do
      expect_boundary 4 manifest_boundary_violation "$format $kind cannot use unrelated production XML" artifact \
        --artifact "$WORK/$kind.$format" --manifest "$WORK/production.xml" --dependencies "$WORK/dependencies.txt"
    done
    for kind in missing malformed plaintext mixed duplicate; do
      cp "$WORK/production.$format" "$WORK/$kind.$format"
      manifest_dir="$WORK/mutation-$format-$kind"
      mkdir -p "$manifest_dir/$(dirname "$member")"
      case "$kind" in
        missing) zip -qd "$WORK/$kind.$format" "$member" ;;
        malformed|plaintext)
          if [ "$kind" = plaintext ]; then cp "$WORK/production.xml" "$manifest_dir/$member"; else printf '\001garbage\377' >"$manifest_dir/$member"; fi
          (cd "$manifest_dir" && zip -q "$WORK/$kind.$format" "$member") ;;
        mixed)
          if [ "$format" = apk ]; then
            mkdir -p "$manifest_dir/base/manifest"
            cp "$WORK/production-module/manifest/AndroidManifest.xml" "$manifest_dir/base/manifest/AndroidManifest.xml"
            (cd "$manifest_dir" && zip -q "$WORK/$kind.$format" base/manifest/AndroidManifest.xml)
          else
            unzip -p "$WORK/production.apk" AndroidManifest.xml >"$manifest_dir/AndroidManifest.xml"
            (cd "$manifest_dir" && zip -q "$WORK/$kind.$format" AndroidManifest.xml)
          fi ;;
        duplicate)
          printf 'duplicate' >"$manifest_dir/other"
          (cd "$manifest_dir" && zip -q "$WORK/$kind.$format" other)
          printf '@ other\n@=%s\n' "$member" | zipnote -w "$WORK/$kind.$format" ;;
      esac
      case "$kind" in
        missing|mixed) error=packaged_manifest_layout ;;
        duplicate) error=archive_duplicate_entry ;;
        *) error=packaged_manifest_decode_failed ;;
      esac
      expect_boundary 4 "$error" "$format $kind evidence" artifact --artifact "$WORK/$kind.$format" \
        --manifest "$WORK/production.xml" --dependencies "$WORK/dependencies.txt"
    done
  done
  if [ -f "$REPO/app/build/outputs/apk/debug/app-debug.apk" ]; then
    sha256sum "$REPO/app/build/outputs/apk/debug/app-debug.apk"
    expect_boundary 4 manifest_boundary_violation 'existing real app debug APK mismatched production XML' artifact \
      --artifact "$REPO/app/build/outputs/apk/debug/app-debug.apk" --manifest "$WORK/production.xml" --dependencies "$WORK/dependencies.txt"
  else
    printf 'Existing app debug APK absent. Isolated real compiled debug APK mismatch was checked above.\n'
  fi

  printf '<?xml version="1.1"?><manifest xmlns:a="http://schemas.android.com/apk/res/android" package="dev.phosphor.mobil3"><uses-permission a:name="UNKNOWN&#x1;NAME"/><application/></manifest>\n' >"$WORK/control.xml"
  expect_boundary 4 manifest_boundary_violation 'XML 1.1 U+0001 produces valid JSON' artifact \
    --artifact "$WORK/production.apk" --manifest "$WORK/control.xml" --dependencies "$WORK/dependencies.txt"
  jq -e '.message | contains("UNKNOWN\u0001NAME")' <<<"$BOUNDARY_RESULT" >/dev/null
  local i char
  for ((i=1; i<32; i++)); do
    printf -v char '%b' "\\$(printf '%03o' "$i")"
    expect_boundary 3 bad_input "C0 byte $i in public argument error" "bad${char}argument"
    jq -e --arg char "$char" '.message | contains($char)' <<<"$BOUNDARY_RESULT" >/dev/null
  done
  PHOSPHOR_BOUNDARY_BUNDLETOOL_CLASSPATH="$BUNDLETOOL_CLASSPATH" expect_boundary 0 '' 'Gradle caller supplies resolved pinned runtime, no recursive Gradle' artifact \
    --artifact "$WORK/production.aab" --dependencies "$WORK/dependencies.txt"
  PHOSPHOR_BOUNDARY_BUNDLETOOL_CLASSPATH="$WORK/no-runtime" expect_boundary 2 manifest_decoder_unavailable 'missing bundletool runtime' artifact \
    --artifact "$WORK/production.aab" --dependencies "$WORK/dependencies.txt"
  mkdir -p "$WORK/no-sdk"
  ANDROID_HOME="$WORK/no-sdk" expect_boundary 2 manifest_decoder_unavailable 'missing SDK decoder' artifact \
    --artifact "$WORK/production.apk" --dependencies "$WORK/dependencies.txt"
  local marker ndk="${ANDROID_NDK_HOME:-$SDK/ndk/28.2.13676358}"
  mkdir -p "$WORK/scan-payload/lib/arm64-v8a"
  for marker in 'dev.phosphor.mobil3.fortress' 'https://sentry.io/report' '100.66.1.2'; do
    cp "$WORK/production.apk" "$WORK/scan.apk"
    printf '%s\n' "$marker" >"$WORK/scan-payload/payload.txt"
    (cd "$WORK/scan-payload" && zip -q "$WORK/scan.apk" payload.txt)
    expect_boundary 4 play_boundary_violation "retained archive scan: $marker" artifact \
      --artifact "$WORK/scan.apk" --dependencies "$WORK/dependencies.txt"
  done
  printf 'releaseRuntimeClasspath\ncom.google.firebase:firebase-analytics\n' >"$WORK/tracking-dependencies.txt"
  expect_boundary 4 play_boundary_violation 'retained tracking dependency scan' artifact \
    --artifact "$WORK/production.apk" --dependencies "$WORK/tracking-dependencies.txt"
  cp "$ndk/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so" "$WORK/scan-payload/lib/arm64-v8a/libc++_shared.so"
  "$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded "$WORK/scan-payload/lib/arm64-v8a/libc++_shared.so"
  cp "$WORK/production.apk" "$WORK/runtime.apk"
  (cd "$WORK/scan-payload" && zip -q "$WORK/runtime.apk" lib/arm64-v8a/libc++_shared.so)
  expect_boundary 0 '' 'retained exact pinned stripped libc++ exemption' artifact \
    --artifact "$WORK/runtime.apk" --dependencies "$WORK/dependencies.txt"
  jq -e '.data.trusted_runtime_exemptions == 1' <<<"$BOUNDARY_RESULT" >/dev/null
  printf 'substitution' >>"$WORK/scan-payload/lib/arm64-v8a/libc++_shared.so"
  (cd "$WORK/scan-payload" && zip -q "$WORK/runtime.apk" lib/arm64-v8a/libc++_shared.so)
  expect_boundary 4 untrusted_android_runtime 'retained altered libc++ rejection' artifact \
    --artifact "$WORK/runtime.apk" --dependencies "$WORK/dependencies.txt"
  # A private source snapshot selects this real Java launcher, not a mocked parser.
  mkdir -p "$WORK/compilerless/scripts/lib" "$WORK/compilerless/.toolchain/jdk-21/bin"
  cp "$REPO/scripts/check-play-boundary.sh" "$WORK/compilerless/scripts/"
  cp "$REPO/scripts/lib/ManifestBoundary.java" "$WORK/compilerless/scripts/lib/"
  printf '#!/usr/bin/env bash\nexec %q --limit-modules java.base,java.xml "$@"\n' "$JDK/bin/java" >"$WORK/compilerless/.toolchain/jdk-21/bin/java"
  chmod +x "$WORK/compilerless/.toolchain/jdk-21/bin/java"
  local original_repo="$REPO"
  REPO="$WORK/compilerless"
  expect_boundary 2 manifest_parser_unavailable 'real Java without jdk.compiler' artifact \
    --artifact "$WORK/production.apk" --dependencies "$WORK/dependencies.txt"
  REPO="$original_repo"
}
