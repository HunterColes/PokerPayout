#!/usr/bin/env bash
# The device matrix (PP-078, layer 2): the tour's key screens on several phone sizes, font scales
# and orientations, on the one headless test emulator. Sizes are `wm size` / `wm density`
# overrides on the AVD (no extra AVDs); the emulator is put back to its own 1080x2400 @ 420 dpi,
# font scale 1.0, rotation 0 however the run ends.
#
#   scripts/device/matrix.sh                    # build, install, each default profile's steps
#                                               # (the smoke set or the shorter screens set)
#   scripts/device/matrix.sh --full             # every tour step on every profile (slow)
#   scripts/device/matrix.sh --profiles small,tablet   # just these profiles (--list shows them; all)
#   scripts/device/matrix.sh --steps bank,ime,ime-done # these steps (after launch and profile)
#   scripts/device/matrix.sh --no-build | --release | --stop
#   scripts/device/matrix.sh --list             # the profiles and the step sets, then exit
#
# Run it under the emulator lock, like the tour:
#   flock /tmp/pokerpayout-emulator.lock scripts/device/matrix.sh --stop
#
# Output: build/device-reports/matrix-<ts>/ (also build/device-reports/matrix-latest):
#   index.md, index.html   the report and the contact sheet (rows = profiles, columns = key screens)
#   contact-sheet.png      the same grid as one picture
#   <profile>/             that profile's tour: NN-step.png/.xml, steps.tsv, checks.json, tour.log
# Exit code 0 when every profile passed every step and no layout check failed (warnings don't count).
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

# ------------------------------------------------------------------ profiles
# name          size       dpi  font rot turns set      what it stands for
#   size/dpi "-" = the emulator's own 1080x2400 @ 420. rot = user rotation (0 upright, 1 = 90).
#   turns = "no" while the app's screens are locked to portrait (today); set "yes" on a profile
#   once its screens rotate (PP-088), and the rotation checks then expect landscape there.
#   set = the steps it runs by default: smoke (everything below) or screens (the key screens).
PROFILES=(
  "small         720x1280   360  1.0  0  no   smoke    320 x 569 dp: the smallest phone we support"
  "small-f1.3    720x1280   360  1.3  0  no   screens  small at 130 % text"
  "small-f2.0    720x1280   360  2.0  0  no   screens  small at 200 % text: the tightest case"
  "compact       1080x2400  480  1.0  0  no   screens  360 x 800 dp: the commonest Android phone width"
  "default       -          -    1.0  0  no   screens  411 x 914 dp: the emulator as it is (Pixel 7 class)"
  "default-f1.3  -          -    1.3  0  no   screens  default at 130 % text"
  "default-f2.0  -          -    2.0  0  no   screens  default at 200 % text"
  "large         1440x3120  560  1.0  0  no   screens  411 x 891 dp at 3.5x: a big QHD phone (Pixel 7 Pro class)"
  "foldable      1768x2208  420  1.0  0  no   screens  673 x 841 dp: a foldable opened flat; the rail"
  "tablet        1600x2560  320  1.0  0  no   smoke    800 x 1280 dp: a 10-inch tablet held upright; the rail"
  "tablet-land   1600x2560  320  1.0  1  no   smoke    the tablet turned 90: the portrait-only app stays upright (PP-088)"
)
# The default run keeps to about 40 minutes. Not in it: large (the same dp as default, only denser:
# it found nothing default didn't), default-f1.3 (between default and default-f2.0: the same) and
# tablet-land (identical to tablet until PP-088). --profiles all runs every profile.
DEFAULT_PROFILES="small small-f1.3 small-f2.0 compact default default-f2.0 foldable tablet"

# ------------------------------------------------------------------ steps
# The smoke set: every tab and tool once, the keyboard, Odds through to exact results, the clock
# running and its table view (also from a turned display), rotation and the tab layout. The order
# matters: each step starts where the one before left off. A name the tour doesn't have is
# skipped with a warning, so the sets can name a step before or after a rename: the chip set's
# result is `chip-calc-generated` before M6 and `chip-calc-stack` after it.
SMOKE_STEPS="launch profile tournament-config rebuy-amount payouts-tab blinds-tab smallest-chip
  config-collapsed bank bank-rebuy ime ime-done pool-summary bank-scrolled payouts-screen tools
  hand-ranks odds-empty odds-card-picker odds-hole-cards odds-flop odds-results chip-calc
  chip-calc-generated chip-calc-stack back-to-tournament timer-running table-view-land
  table-view-back rotate nav-layout app-alive"
# The screens set: the same screens and dialogs, without the keyboard, the Odds keypad round and
# the rotated table view (none of which change with the text size), to keep the run short.
SCREENS_STEPS="launch profile tournament-config rebuy-amount payouts-tab blinds-tab smallest-chip
  config-collapsed bank bank-rebuy pool-summary bank-scrolled payouts-screen tools hand-ranks
  chip-calc chip-calc-generated chip-calc-stack odds-empty back-to-tournament timer-running
  table-view-land table-view-close rotate nav-layout app-alive"
# Matrix steps added to --full (before app-alive; each one finds its own screen).
FULL_EXTRAS="ime ime-done table-view-land table-view-back rotate nav-layout"
# These force a phone's window to 720 dp (wm density) and expect the bottom bar back after: only
# under 600 dp, and only in --full (the plain tour runs them; nav-layout checks the real rail on
# the wide profiles).
PHONE_ONLY="rail rail-tools rail-payouts rail-restored"
# The contact sheet's columns (one that no profile ran is left out).
KEY_SCREENS="launch payouts-tab blinds-tab config-collapsed timer-running table-view-land bank ime
  pool-summary payouts-screen tools hand-ranks odds-empty odds-results chip-calc-generated
  chip-calc-stack rotate"

usage() { sed -n '2,22p' "$0"; }
BUILD=1; VARIANT=debug; FULL=0; STOP_AFTER=0; LIST=0; ONLY_PROFILES=""; STEPS=""
while (( $# )); do
  case "$1" in
    --full) FULL=1 ;;
    --profiles) shift; ONLY_PROFILES="${1:?--profiles needs names}" ;;
    --steps) shift; STEPS="${1:?--steps needs step names}" ;;
    --no-build) BUILD=0 ;;
    --release) VARIANT=release ;;
    --stop) STOP_AFTER=1 ;;
    --list) LIST=1 ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown option: $1" ;;
  esac
  shift
done

profile_line() { local p; for p in "${PROFILES[@]}"; do [[ "${p%% *}" == "$1" ]] && { echo "$p"; return 0; }; done; return 1; }
if (( LIST )); then
  printf '%-13s %-10s %-4s %-4s %-3s %-5s %-8s %s\n' profile size dpi font rot turns set ""
  for p in "${PROFILES[@]}"; do
    read -r n s d f r t stepset desc <<<"$p"
    printf '%-13s %-10s %-4s %-4s %-3s %-5s %-8s %s%s\n' "$n" "$s" "$d" "$f" "$r" "$t" "$stepset" "$desc" \
      "$([[ " $DEFAULT_PROFILES " == *" $n "* ]] && echo "" || echo " (not in the default run)")"
  done
  echo; echo "smoke steps: $(xargs <<<"$SMOKE_STEPS")"
  echo; echo "screens steps: $(xargs <<<"$SCREENS_STEPS")"
  exit 0
fi
[[ "$ONLY_PROFILES" == all ]] && ONLY_PROFILES="$(for p in "${PROFILES[@]}"; do echo "${p%% *}"; done | xargs)"
read -r -a RUN_PROFILES <<<"$(tr ',' ' ' <<<"${ONLY_PROFILES:-$DEFAULT_PROFILES}")"
for n in "${RUN_PROFILES[@]}"; do profile_line "$n" >/dev/null || die "unknown profile: $n (see --list)"; done

# ------------------------------------------------------------------ display reset
# The emulator is shared: put it back to its own size, density, font and rotation on any end.
reset_display() {
  adb_ shell wm size reset >/dev/null 2>&1 || true
  adb_ shell wm density reset >/dev/null 2>&1 || true
  adb_ shell settings put system font_scale 1.0 >/dev/null 2>&1 || true
  adb_ shell settings put system accelerometer_rotation 0 >/dev/null 2>&1 || true
  adb_ shell settings put system user_rotation 0 >/dev/null 2>&1 || true
  adb_ shell settings put secure show_ime_with_hard_keyboard 0 >/dev/null 2>&1 || true
}
display_state() {
  printf '%s @ %sdpi, font %s, rotation %s' \
    "$(adb_ shell wm size | tr -d '\r' | sed -n 's/.*size: //p' | tail -1)" \
    "$(adb_ shell wm density | tr -d '\r' | awk '{print $NF}' | tail -1)" \
    "$(adb_ shell settings get system font_scale | tr -d '\r')" \
    "$(adb_ shell settings get system user_rotation | tr -d '\r')"
}
demo_status_bar() { # SystemUI drops its demo mode when the display size changes
  local c
  for c in "command enter" "command clock -e hhmm 1200" "command battery -e level 100 -e plugged false" \
           "command network -e wifi show -e level 4 -e mobile hide" "command notifications -e visible false"; do
    adb_ shell "am broadcast -a com.android.systemui.demo -e $c" >/dev/null 2>&1 || true
  done
}

TOUR_PID=""; INTERRUPTED=0
on_exit() {
  local rc=$?
  if device_online; then
    reset_display
    echo "[matrix] display reset: $(display_state)"
    (( STOP_AFTER )) && "$DEVICE_SCRIPTS/stop.sh" >/dev/null
  fi
  exit "$rc"
}
on_signal() {
  trap - INT TERM HUP
  INTERRUPTED=1
  echo "[matrix] interrupted: stopping the tour, then resetting the display"
  if [[ -n "$TOUR_PID" ]] && kill -0 "$TOUR_PID" 2>/dev/null; then
    kill -TERM "$TOUR_PID" 2>/dev/null || true
    pkill -TERM -P "$TOUR_PID" 2>/dev/null || true     # its running step, by parent pid only
    local i; for i in $(seq 1 120); do kill -0 "$TOUR_PID" 2>/dev/null || break; sleep 0.5; done
  fi
  device_online && reset_display
  write_report || true
  exit 130
}

# ------------------------------------------------------------------ run
T0=$(date +%s)
REPORT_ROOT="${PP_REPORT_ROOT:-$REPO_ROOT/build/device-reports}"
M="$REPORT_ROOT/matrix-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$M"
ln -sfn "$M" "$REPORT_ROOT/matrix-latest"
printf 'profile\tsize\tdensity\tfont\trotation\tturns\tdescription\tseconds\texit\n' > "$M/profiles.tsv"

write_report() {
  local dirs=() n
  for n in "${RUN_PROFILES[@]}"; do [[ -f "$M/$n/steps.tsv" ]] && dirs+=("$M/$n"); done
  (( ${#dirs[@]} )) || return 0
  python3 "$DEVICE_SCRIPTS/layout_check.py" "${dirs[@]}" | sed 's/^/[matrix] /' || true
  python3 "$DEVICE_SCRIPTS/matrix_report.py" "$M" --key-screens "$(xargs <<<"$KEY_SCREENS")" \
    --variant "$VARIANT" --mode "$( (( FULL )) && echo full || { [[ -n "$STEPS" ]] && echo custom || echo "smoke and screens"; }; )" \
    --wall "$(since "$T0")" --interrupted "$INTERRUPTED" --display-after "$(display_state 2>/dev/null || true)"
}

trap on_exit EXIT
trap on_signal INT TERM HUP

"$DEVICE_SCRIPTS/boot.sh" | sed 's/^/[matrix] /'
reset_display                                 # a clean start, whatever an earlier run left
if (( BUILD )); then "$DEVICE_SCRIPTS/install.sh" "--$VARIANT" | sed 's/^/[matrix] /'
else "$DEVICE_SCRIPTS/install.sh" "--$VARIANT" --no-build | sed 's/^/[matrix] /'; fi

# The steps for a profile's set ($1): smoke or screens, unless --full or --steps say otherwise.
FULL_STEPS=""
if (( FULL )); then
  while IFS=$'\t' read -r name kind _; do
    [[ "$kind" == tour ]] || continue
    [[ "$name" == app-alive ]] && FULL_STEPS="$FULL_STEPS $FULL_EXTRAS"
    FULL_STEPS="$FULL_STEPS $name"
    [[ "$name" == launch ]] && FULL_STEPS="$FULL_STEPS profile"
  done < <("$DEVICE_SCRIPTS/tour.sh" --list)
fi
CUSTOM_STEPS=""
if [[ -n "$STEPS" ]]; then
  CUSTOM_STEPS="$(tr ',' ' ' <<<"$STEPS")"
  [[ " $CUSTOM_STEPS " == *" launch "* ]] || CUSTOM_STEPS="launch profile $CUSTOM_STEPS"
fi
steps_for() {
  if (( FULL )); then echo "$FULL_STEPS"
  elif [[ -n "$CUSTOM_STEPS" ]]; then echo "$CUSTOM_STEPS"
  elif [[ "$1" == screens ]]; then echo "$SCREENS_STEPS"
  else echo "$SMOKE_STEPS"; fi
}
# The tour's own step names: a set's name the tour doesn't have is skipped, with one warning.
KNOWN_STEPS=" $("$DEVICE_SCRIPTS/tour.sh" --list | cut -f1 | xargs) "
UNKNOWN_STEPS=""
for s in $SMOKE_STEPS $SCREENS_STEPS $CUSTOM_STEPS; do
  [[ "$KNOWN_STEPS" == *" $s "* || " $UNKNOWN_STEPS " == *" $s "* ]] || UNKNOWN_STEPS="$UNKNOWN_STEPS $s"
done
[[ -z "$UNKNOWN_STEPS" ]] || warn "the tour has no step$UNKNOWN_STEPS; skipped (see tour.sh --list)"

FAILED_PROFILES=0
for name in "${RUN_PROFILES[@]}"; do
  read -r _ size density font rot turns stepset desc <<<"$(profile_line "$name")"
  t=$(date +%s)
  reset_display
  if [[ "$size" != - ]]; then adb_ shell wm size "$size"; adb_ shell wm density "$density"; fi
  adb_ shell settings put system font_scale "$font"
  adb_ shell settings put system accelerometer_rotation 0
  adb_ shell settings put system user_rotation "$rot"
  sleep 2
  demo_status_bar
  # The steps for this profile: the phone-only ones only under 600 dp
  phys_w="$(adb_ shell wm size | tr -d '\r' | sed -n 's/.*size: \([0-9]*\)x.*/\1/p' | tail -1)"
  dpi="$(adb_ shell wm density | tr -d '\r' | awk '{print $NF}' | tail -1)"
  steps=""
  for s in $(steps_for "$stepset"); do
    [[ "$KNOWN_STEPS" == *" $s "* ]] || continue
    if (( phys_w * 160 / dpi >= 600 )) && [[ " $PHONE_ONLY " == *" $s "* ]]; then continue; fi
    steps="$steps $s"
  done
  echo "[matrix] $name ($desc): $(display_state), $(wc -w <<<"$steps") steps"
  mkdir -p "$M/$name"
  # PP_UI_SCROLL: what a step expects may be below the fold on this screen; ui.py drags the page
  # to look for it (see ui.py). The plain tour never sets it.
  # PP_TOUR_RECOVER: after a failed step, close a dialog it left open (one bug, one failure).
  PP_UI_SCROLL=1 PP_TOUR_RECOVER=1 PP_PROFILE="$name" PP_PROFILE_FONT="$font" PP_PROFILE_ROTATION="$rot" PP_PROFILE_TURNS="$turns" \
    "$DEVICE_SCRIPTS/tour.sh" --no-boot --no-install --keep-going --out "$M/$name" --only "$(xargs <<<"$steps")" \
    > "$M/$name/tour.out" 2>&1 &
  TOUR_PID=$!
  rc=0; wait "$TOUR_PID" || rc=$?
  TOUR_PID=""
  secs=$(since "$t")
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$name" "$size" "$density" "$font" "$rot" "$turns" "$desc" "$secs" "$rc" >> "$M/profiles.tsv"
  grep -E '^\[tour\] FAIL ' "$M/$name/tour.out" | sed "s/^/[matrix]   /" || true
  echo "[matrix] $name: $(grep -E '^\[tour\] (PASS|FAIL):' "$M/$name/tour.out" | sed 's/^\[tour\] //' || echo "no result (see $M/$name/tour.out)")"
  (( rc == 0 )) || FAILED_PROFILES=$((FAILED_PROFILES + 1))
done

reset_display
write_report
verdict="$(sed -n 's/^verdict=//p' "$M/summary.env" 2>/dev/null || echo FAIL)"
echo "[matrix] $verdict: ${#RUN_PROFILES[@]} profiles, $FAILED_PROFILES with failing steps, $(since "$T0")s"
echo "[matrix] report: $M/index.md (contact sheet: $M/index.html)"
[[ "$verdict" == PASS ]]
