#!/usr/bin/env bash
# Cold-start time and memory of the installed app on the headless emulator (boot.sh, install.sh).
#
#   scripts/device/startup.sh [runs]     default 8 cold starts per mode
#
# Two modes, the same APK:
#   as-installed  how adb (and F-Droid) leave it: no baseline profile, so ART verifies the dex and
#                 the JIT warms it up launch by launch
#   speed         every method compiled ahead of time (cmd package compile -m speed): the most a
#                 baseline profile could buy; the dex is reset to as-installed afterwards
# Each run: force-stop, `am start -W`, read TotalTime (process start to first frame). The first
# as-installed run also creates the app's data, so it is listed but left out of the median.
# Then the app's memory (dumpsys meminfo) a few seconds after the last start.
#
# The emulator uses a software GPU and shared CI cores: compare runs with each other, not with a
# phone. stdout: the results; also written to build/device/startup.md.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

RUNS="${1:-8}"
require_device
out="$STATE_DIR/startup.md"
component="$APP_ID/$MAIN_ACTIVITY"

cold_start() { # prints TotalTime in ms
  adb_ shell am force-stop "$APP_ID"
  sleep 2
  adb_ shell am start -W -n "$component" 2>/dev/null | tr -d '\r' | awk -F': ' '/^TotalTime/ {print $2}'
}

median() { sort -n | awk '{a[NR]=$1} END {if (NR == 0) print "n/a"; else if (NR % 2) print a[(NR+1)/2]; else print int((a[NR/2]+a[NR/2+1])/2)}'; }

measure_mode() { # $1 = mode name; prints a markdown row
  local mode="$1" times=() t
  for i in $(seq 1 "$RUNS"); do
    t="$(cold_start)"
    times+=("${t:-?}")
  done
  local counted=("${times[@]}")
  [[ "$mode" == as-installed ]] && counted=("${times[@]:1}")
  local med
  med="$(printf '%s\n' "${counted[@]}" | grep -E '^[0-9]+$' | median)"
  echo "| $mode | **$med ms** | ${times[*]} |"
}

{
  echo "# Cold start: $(adb_ shell getprop ro.product.model | tr -d '\r'), API $(adb_ shell getprop ro.build.version.sdk | tr -d '\r')"
  echo
  echo "App: $(adb_ shell dumpsys package "$APP_ID" | tr -d '\r' | awk -F= '/versionName/ {print $2; exit}')," \
    "$( adb_ shell dumpsys package "$APP_ID" | tr -d '\r' | grep -q 'DEBUGGABLE' && echo debuggable || echo release)." \
    "$RUNS starts per mode; the as-installed median leaves out the first start."
  echo
  echo "| Mode | Median TotalTime | Each start (ms) |"
  echo "|---|---:|---|"
  adb_ shell pm clear "$APP_ID" >/dev/null
  adb_ shell cmd package compile --reset "$APP_ID" >/dev/null 2>&1 || true
  measure_mode as-installed
  adb_ shell cmd package compile -m speed -f "$APP_ID" >/dev/null 2>&1
  measure_mode speed
  adb_ shell cmd package compile --reset "$APP_ID" >/dev/null 2>&1 || true
  echo
  adb_ shell am force-stop "$APP_ID"
  adb_ shell am start -W -n "$component" >/dev/null 2>&1
  sleep 5
  echo "Memory 5 s after a start (dumpsys meminfo, KiB):"
  echo
  echo '```'
  adb_ shell dumpsys meminfo "$APP_ID" | tr -d '\r' | sed -n '/App Summary/,/TOTAL SWAP/p'
  echo '```'
} | tee "$out"
