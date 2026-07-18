#!/usr/bin/env bash
# bootstrap-android.sh — one-shot, idempotent Android toolchain install for phosphor-mobil3.
# Everything lands in-repo under .toolchain/ (hidden, gitignored, no home-folder clutter, no
# sudo). Rerun any time; each step is check-then-act. No Python is authored or invoked.
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TC="$REPO/.toolchain"
SDK="$TC/Sdk"
JDK="$TC/jdk-21"
GRADLE_DIST="$TC/gradle-9.1.0"
NDK_ID="ndk;28.2.13676358"
PLATFORM="platforms;android-36"
BUILD_TOOLS="build-tools;36.0.0"
CT_URL="https://dl.google.com/android/repository/commandlinetools-linux-14742923_latest.zip"
GRADLE_URL="https://services.gradle.org/distributions/gradle-9.1.0-bin.zip"
JDK_URL="https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"

mkdir -p "$TC"
step() { printf '\n== %s\n' "$*"; }

step "JDK 21 (Temurin, in-repo)"
if [ ! -x "$JDK/bin/javac" ]; then
  curl -fL "$JDK_URL" -o /tmp/jdk21.tgz
  mkdir -p "$JDK" && tar xzf /tmp/jdk21.tgz -C "$JDK" --strip-components=1
  rm -f /tmp/jdk21.tgz
fi
export JAVA_HOME="$JDK"
"$JDK/bin/javac" -version

step "Android cmdline-tools"
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  curl -fLo /tmp/ct.zip "$CT_URL"
  mkdir -p "$SDK/cmdline-tools" && unzip -qo /tmp/ct.zip -d "$SDK/cmdline-tools"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -f /tmp/ct.zip
fi
SDKM="$SDK/cmdline-tools/latest/bin/sdkmanager"

step "SDK licenses"
yes | "$SDKM" --sdk_root="$SDK" --licenses >/dev/null || true

step "SDK packages: platform-tools, $PLATFORM, $BUILD_TOOLS, $NDK_ID"
"$SDKM" --sdk_root="$SDK" "platform-tools" "$PLATFORM" "$BUILD_TOOLS" "$NDK_ID"

step "Gradle 9.1.0 distribution (for wrapper generation)"
if [ ! -x "$GRADLE_DIST/bin/gradle" ]; then
  curl -fLo /tmp/gradle.zip "$GRADLE_URL"
  unzip -qo /tmp/gradle.zip -d "$TC"
  rm -f /tmp/gradle.zip
fi
"$GRADLE_DIST/bin/gradle" --version | head -5

step "Rust: aarch64-linux-android target + cargo-ndk"
rustup target add aarch64-linux-android
command -v cargo-ndk >/dev/null || cargo install cargo-ndk --locked
cargo ndk --version

step "DONE — source scripts/env.sh for the environment"
