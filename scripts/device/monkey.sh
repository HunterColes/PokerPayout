#!/usr/bin/env bash
# Chaos on the headless emulator: `adb shell monkey` with fixed, logged seeds against the app, and
# only the app. Fails if any seed crashes the app or makes it stop responding (ANR).
#
#   scripts/device/monkey.sh                          # boot (if needed) + build + install debug + the default seeds
#   scripts/device/monkey.sh --seeds 11,22 --events 3000
#   scripts/device/monkey.sh --release                # the minified (R8) release build instead
#   scripts/device/monkey.sh --no-build               # install the last built APK
#   scripts/device/monkey.sh --stop                   # shut the emulator down afterwards
#
# Each seed starts the app afresh (data cleared, notifications allowed, launched), so a failing seed
# replays on its own, event for event:
#   scripts/device/monkey.sh --no-build --seeds <seed> --events <events>
#
# What the monkey sends (percentages of its events): touches 45, swipes 20, rotations 5, arrow keys 5,
# Menu and DPad-centre 10, system keys 5 (Back, volume; Home and the notification shade are switched
# off for the run), the app relaunched 5, any other key 5. Throttle: --throttle ms between events.
# `-p com.huntercoles.pokerpayout` keeps it in the app: other apps' screens (a share sheet, the
# dialer) are refused.
#
# Output: build/device-reports/monkey-<timestamp>/
#   summary.md         seeds, events injected, verdict per seed
#   seed-<n>.txt       the monkey's own log (each event, -v -v)
#   logcat-<n>.txt     logcat for that seed; crash-<n>.png the screen when it failed
# Exit code 0 == no seed crashed the app or made it stop responding.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

SEEDS="20261008,1204,35"; EVENTS=4000; THROTTLE=75
VARIANT=debug; BUILD=1; STOP_AFTER=0
while (( $# )); do
  case "$1" in
    --seeds) shift; SEEDS="${1:?--seeds needs a list}" ;;
    --events) shift; EVENTS="${1:?--events needs a number}" ;;
    --throttle) shift; THROTTLE="${1:?--throttle needs milliseconds}" ;;
    --release) VARIANT=release ;;
    --no-build) BUILD=0 ;;
    --stop) STOP_AFTER=1 ;;
    -h|--help) sed -n '2,25p' "$0"; exit 0 ;;
    *) die "unknown option: $1" ;;
  esac
  shift
done
[[ "$EVENTS" =~ ^[0-9]+$ && "$THROTTLE" =~ ^[0-9]+$ ]] || die "--events and --throttle take whole numbers"
IFS=', ' read -r -a SEED_LIST <<<"$SEEDS"
for seed in "${SEED_LIST[@]}"; do [[ "$seed" =~ ^[0-9]+$ ]] || die "not a seed: $seed"; done

T0=$(date +%s)
OUT="${PP_REPORT_ROOT:-$REPO_ROOT/build/device-reports}/monkey-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
LOG="$OUT/monkey.log"

"$DEVICE_SCRIPTS/boot.sh" | tee -a "$LOG"
if (( BUILD )); then "$DEVICE_SCRIPTS/install.sh" "--$VARIANT" | tee -a "$LOG"
else "$DEVICE_SCRIPTS/install.sh" "--$VARIANT" --no-build | tee -a "$LOG"; fi
APP_VERSION="$(adb_ shell dumpsys package "$APP_ID" | sed -n 's/.*versionName=//p' | head -1 | tr -d '\r')"

# Keep the monkey in the app: no Home key, no notification shade (quick settings could turn on
# airplane mode or start a screen recording), and End-call does nothing (it would put the screen to
# sleep). Put back however the run ends.
adb_ shell settings get system end_button_behavior > "$OUT/.end-button" 2>/dev/null || true
restore() {
  adb_ shell cmd statusbar disable-for-setup false >/dev/null 2>&1 || true
  local end; end="$(tr -d '\r' < "$OUT/.end-button" 2>/dev/null || true)"
  if [[ "$end" =~ ^[0-9]+$ ]]; then adb_ shell settings put system end_button_behavior "$end" >/dev/null 2>&1 || true
  else adb_ shell settings delete system end_button_behavior >/dev/null 2>&1 || true; fi
  # Rotation events freeze the display at a rotation; free it and stand the screen upright again
  adb_ shell cmd window user-rotation free >/dev/null 2>&1 || true
  adb_ shell settings put system accelerometer_rotation 0 >/dev/null 2>&1 || true
  adb_ shell settings put system user_rotation 0 >/dev/null 2>&1 || true
  rm -f "$OUT/.end-button"
}
trap 'restore; echo "[monkey] interrupted"; exit 130' INT TERM HUP
adb_ shell cmd statusbar disable-for-setup true >/dev/null 2>&1 || warn "couldn't switch off Home and the notification shade"
adb_ shell settings put system end_button_behavior 0 >/dev/null 2>&1 || true

# The app's processes: the app itself and any ":service" process.
ours() { [[ "$1" == "$APP_ID" || "$1" == "$APP_ID:"* ]]; }

FAILED=0; ROWS=()
for seed in "${SEED_LIST[@]}"; do
  log "seed $seed: $EVENTS events, ${THROTTLE}ms apart"
  adb_ shell pm clear "$APP_ID" >/dev/null
  adb_ shell pm grant "$APP_ID" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
  adb_ shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb_ logcat -b all -c >/dev/null 2>&1 || true
  adb_ shell am start -W -n "$APP_ID/$MAIN_ACTIVITY" >/dev/null
  t=$(date +%s)
  adb_ shell monkey -p "$APP_ID" -s "$seed" --throttle "$THROTTLE" \
    --pct-touch 45 --pct-motion 20 --pct-pinchzoom 0 --pct-trackball 0 --pct-rotation 5 \
    --pct-nav 5 --pct-majornav 10 --pct-syskeys 5 --pct-appswitch 5 --pct-flip 0 --pct-anyevent 5 \
    --pct-permission 0 \
    -v -v "$EVENTS" > "$OUT/seed-$seed.txt" 2>&1 || true
  secs=$(since "$t")
  adb_ logcat -d -v threadtime -b main,system,crash > "$OUT/logcat-$seed.txt" 2>/dev/null || true

  injected="$(sed -n 's/^Events injected: //p' "$OUT/seed-$seed.txt" | tail -1 | tr -d '\r')"
  crashed="$(sed -n 's|^// CRASH: \([^ ]*\).*|\1|p' "$OUT/seed-$seed.txt" | tr -d '\r' | head -1)"
  hung="$(sed -n 's|^// NOT RESPONDING: \([^ ]*\).*|\1|p' "$OUT/seed-$seed.txt" | tr -d '\r' | head -1)"
  problem=""
  if [[ -n "$crashed" ]] && ours "$crashed"; then problem="crash in $crashed"; fi
  if [[ -z "$problem" && -n "$hung" ]] && ours "$hung"; then problem="not responding: $hung"; fi
  # Backstop: the app's own crash or ANR in logcat, even if the monkey didn't report it
  if [[ -z "$problem" ]] && grep -qE "Process: $APP_ID(:| |,|$)" <(grep -A2 "FATAL EXCEPTION" "$OUT/logcat-$seed.txt"); then
    problem="FATAL EXCEPTION in logcat"
  fi
  if [[ -z "$problem" ]] && grep -q "ANR in $APP_ID" "$OUT/logcat-$seed.txt"; then problem="ANR in logcat"; fi
  # Something else crashed (a system app): the monkey stopped early, but not because of us
  note=""
  if [[ -n "$crashed" ]] && ! ours "$crashed"; then note="stopped early: $crashed crashed"; fi
  if [[ -n "$hung" ]] && ! ours "$hung"; then note="stopped early: $hung not responding"; fi

  if [[ -n "$problem" ]]; then
    FAILED=$((FAILED + 1))
    adb_ exec-out screencap -p > "$OUT/crash-$seed.png" 2>/dev/null || true
    log "seed $seed: FAIL ($problem) after ${injected:-?} events, ${secs}s"
    {
      echo "== seed $seed: $problem"
      grep -E -A40 "^// (CRASH|NOT RESPONDING): $APP_ID" "$OUT/seed-$seed.txt" | head -60 || true
      grep -A25 "FATAL EXCEPTION" "$OUT/logcat-$seed.txt" | head -40 || true
    } | tee -a "$LOG" >&2
    ROWS+=("| $seed | FAIL | $problem | ${injected:-?} of $EVENTS | ${secs}s | [log](seed-$seed.txt), [logcat](logcat-$seed.txt), [screen](crash-$seed.png) |")
  else
    log "seed $seed: pass, ${injected:-?} events, ${secs}s${note:+ ($note)}"
    ROWS+=("| $seed | pass | ${note:-} | ${injected:-?} of $EVENTS | ${secs}s | [log](seed-$seed.txt), [logcat](logcat-$seed.txt) |")
  fi
done

restore
trap - INT TERM HUP
verdict=PASS; (( FAILED > 0 )) && verdict=FAIL
{
  echo "# Monkey report: $verdict"
  echo
  echo "- When: $(date -Is)"
  echo "- App: $APP_ID $APP_VERSION ($VARIANT)"
  echo "- Device: $ANDROID_SERIAL, Android $(adb_ shell getprop ro.build.version.release | tr -d '\r') (API $(adb_ shell getprop ro.build.version.sdk | tr -d '\r'))"
  echo "- Seeds: ${SEED_LIST[*]}; $EVENTS events each, ${THROTTLE}ms apart; total $(since "$T0")s"
  echo "- Replay one: \`scripts/device/monkey.sh --no-build --seeds <seed> --events $EVENTS --throttle $THROTTLE\`"
  echo
  echo "| Seed | Result | Detail | Events | Time | Files |"
  echo "|------|--------|--------|--------|------|-------|"
  printf '%s\n' "${ROWS[@]}"
} > "$OUT/summary.md"
(( STOP_AFTER )) && "$DEVICE_SCRIPTS/stop.sh" >/dev/null
echo "[monkey] $verdict: $FAILED of ${#SEED_LIST[@]} seeds failed (report: $OUT/summary.md)"
(( FAILED == 0 ))
