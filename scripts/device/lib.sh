# shellcheck shell=bash
# Shared helpers for scripts/device/*.sh. Source it; do not execute it.
#
# Environment knobs (all optional):
#   ANDROID_HOME     SDK root (default: $HOME/Android/Sdk)
#   PP_AVD           AVD name (default: pokerpayout_test, created on demand)
#   PP_EMU_PORT      emulator console port, even number 5554-5682 (default: 5580,
#                    so we never collide with a hand-launched emulator on 5554)
#   PP_REPORT_ROOT   where tour reports go (default: <repo>/build/device-reports)

set -euo pipefail

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"

ADB="$ANDROID_HOME/platform-tools/adb"
EMULATOR="$ANDROID_HOME/emulator/emulator"

PP_AVD="${PP_AVD:-pokerpayout_test}"
PP_EMU_PORT="${PP_EMU_PORT:-5580}"
export ANDROID_SERIAL="emulator-$PP_EMU_PORT"   # every bare `adb` call targets our emulator

APP_ID="com.huntercoles.pokerpayout"
MAIN_ACTIVITY="com.huntercoles.pokerpayout.core.presentation.MainActivity"

DEVICE_SCRIPTS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$DEVICE_SCRIPTS/../.." && pwd)"
STATE_DIR="$REPO_ROOT/build/device"          # gitignored (build/)
mkdir -p "$STATE_DIR"

# Quiet logging: humans/agents get one-liners on stdout; details go to log files.
log()  { printf '[device] %s\n' "$*"; }
warn() { printf '[device] WARN: %s\n' "$*" >&2; }
die()  { printf '[device] ERROR: %s\n' "$*" >&2; exit 1; }

adb_() { "$ADB" -s "$ANDROID_SERIAL" "$@"; }

[[ -x "$ADB" ]] || die "adb not found at $ADB (set ANDROID_HOME)"

device_online() {
  [[ "$("$ADB" -s "$ANDROID_SERIAL" get-state 2>/dev/null || true)" == "device" ]]
}

device_booted() {
  device_online && [[ "$(adb_ shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]
}

require_device() {
  device_booted || die "emulator $ANDROID_SERIAL is not booted; run scripts/device/boot.sh first"
}

# Elapsed seconds since $1 (epoch seconds).
since() { echo $(( $(date +%s) - $1 )); }

# Gradle invocation shared by install.sh and the test docs. Another agent may run
# Gradle on this machine at the same time, so cap the heap and the Kotlin daemon.
gradle_() {
  (cd "$REPO_ROOT" && ./gradlew --console=plain \
      -Dorg.gradle.jvmargs="-Xmx4g -Dfile.encoding=UTF-8 -XX:+UseParallelGC" \
      -Pkotlin.daemon.jvmargs="-Xmx2g" "$@")
}
