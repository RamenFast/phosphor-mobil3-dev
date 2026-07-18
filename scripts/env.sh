# env.sh — source this for the phosphor-mobil3 Android environment (agents + humans).
# Self-locating: the toolchain lives in-repo at .toolchain/ (hidden, gitignored, moves with
# the project). No dependency on any absolute home path.
_PM3_ENV_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")/.." && pwd)"
_PM3_TC="$_PM3_ENV_DIR/.toolchain"

export JAVA_HOME="$_PM3_TC/jdk-21"
export ANDROID_HOME="$_PM3_TC/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$_PM3_TC/gradle-9.1.0/bin:$PATH"
