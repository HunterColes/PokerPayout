#!/usr/bin/env bash
# The device matrix (PP-078, layer 2): the tour's key screens on several phone sizes, font scales
# and orientations, on the one headless test emulator. Sizes are `wm size` / `wm density`
# overrides on the AVD (no extra AVDs, except the soft-keyboard twin the soft-kb profiles boot
# instead); the emulator is put back to its own 1080x2400 @ 420 dpi, font scale 1.0, rotation 0
# however the run ends.
#
#   scripts/device/matrix.sh                    # build, install, the focused profiles' steps
#                                               # (the smoke set or the shorter screens set)
#   scripts/device/matrix.sh --profiles daily   # the once-a-day set (7 profiles); all = every one
#                                               # on the main AVD
#   scripts/device/matrix.sh --full             # every tour step on every profile (slow)
#   scripts/device/matrix.sh --profiles small,tablet   # just these profiles (--list shows them)
#   scripts/device/matrix.sh --profiles soft-keyboard  # the full-height soft keyboard, on its own AVD
#   scripts/device/matrix.sh --profiles tablet-ignore  # a tablet that ignores orientation requests
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
# name          size       dpi  font rot turns set      device        what it stands for
#   size/dpi "-" = the emulator's own 1080x2400 @ 420. rot = user rotation (0 upright, 1 = 90).
#   turns = "yes" where every tab turns with the display (smallest width 600 dp and up: PP-088);
#   "no" on phones, where only the Tournament tab's running clock turns, into the table view
#   (PP-079; the rotate-clock step checks that on every profile).
#   set = the steps it runs by default: smoke (everything below), screens (the key screens) or
#   keyboard (the soft keyboard's).
#   device = what else it changes. "-": nothing. "soft-kb": it runs on the soft-keyboard AVD
#   (pokerpayout_test_softkb: no hardware keyboard, so the full-height soft keyboard; boot.sh), which
#   never runs beside the main one, so only in a matrix of soft-kb profiles. "ignore-orient": the
#   display ignores the app's orientation requests, as large screens may (see the profile loop).
PROFILES=(
  "small         720x1280   360  1.0  0  no   smoke    -             320 x 569 dp: the smallest phone we support"
  "small-f1.3    720x1280   360  1.3  0  no   screens  -             small at 130 % text"
  "small-f2.0    720x1280   360  2.0  0  no   screens  -             small at 200 % text: the tightest case"
  "compact       1080x2400  480  1.0  0  no   screens  -             360 x 800 dp: the commonest Android phone width"
  "default       -          -    1.0  0  no   screens  -             411 x 914 dp: the emulator as it is (Pixel 7 class)"
  "default-f1.3  -          -    1.3  0  no   screens  -             default at 130 % text"
  "default-f2.0  -          -    2.0  0  no   screens  -             default at 200 % text"
  "large         1440x3120  560  1.0  0  no   screens  -             411 x 891 dp at 3.5x: a big QHD phone (Pixel 7 Pro class)"
  "foldable      1768x2208  420  1.0  0  yes  screens  -             673 x 841 dp: a foldable opened flat; the rail"
  "tablet        1600x2560  320  1.0  0  yes  smoke    -             800 x 1280 dp: a 10-inch tablet held upright; the rail"
  "tablet-land   1600x2560  320  1.0  1  yes  smoke    -             the tablet turned 90: 1280 x 800 dp, every tab landscape (PP-088)"
  "tablet-ignore 1600x2560  320  1.0  0  yes  smoke    ignore-orient the tablet ignoring the app's orientation requests, as large screens may (Android 12L on; Android 16's override)"
  "soft-kb       -          -    1.0  0  no   keyboard soft-kb       411 x 914 dp with no hardware keyboard: the full-height soft keyboard"
  "soft-kb-small 720x1280   360  1.0  0  no   keyboard soft-kb       320 x 569 dp with the full-height soft keyboard: the least room left"
)
# Named sets for --profiles (PP-093):
#   focused  the routine run (the default): the smallest phone with its full smoke set, the worst
#            text cases on the smallest and a common phone, and the tablet with its smoke set
#   daily    once a day: also 130 % text, the 360 dp phone and the foldable. Not `default`: the
#            plain tour covers that screen step by step on every pull request
#   soft-keyboard  the soft-kb profiles, on their own AVD (a run of their own)
#   all      every profile on the main AVD (not the soft-kb ones). Never in a named set: large (the
#            same dp as default, only denser: it found nothing default didn't), default-f1.3
#            (between default and default-f2.0), tablet-land (the tablet turned; rotate and
#            rotate-clock turn every profile anyway) and tablet-ignore (opt-in)
FOCUSED_PROFILES="small small-f2.0 default-f2.0 tablet"
DAILY_PROFILES="small small-f1.3 small-f2.0 compact default-f2.0 foldable tablet"
SOFT_KEYBOARD_PROFILES="soft-kb soft-kb-small"
DEFAULT_PROFILES="$FOCUSED_PROFILES"

# ------------------------------------------------------------------ steps
# The smoke set: every tab and tool, the Tournament tab from setup through the clock, a break, the
# table view and the turned clock to a reset, the Bank's records, the keyboard, Odds through to the
# exact result, the chip set, the rebuy cutoff with a running clock, process death, rotation and
# the tab layout. The order matters: each step starts where the one before left off (the same
# order as the tour's own). A name the tour doesn't have is skipped with a warning.
SMOKE_STEPS="launch profile tournament-config payouts-tab payouts-editor blinds smallest-chip breaks
  ready-ticket start-fold timer-next-level timer-break table-view-land table-view-close end-break
  rotate-clock rotate-clock-back setup-panel setup-close tournament-reset tournament-reset-ok rebuy-amount
  bank bank-rename bank-buyin bank-rebuy bank-knockout-sheet bank-knockout-done pool-summary
  weights-editor weights-closed ime ime-done payouts-screen tools hand-ranks odds-empty
  odds-card-picker odds-hole-cards odds-flop odds-results chip-calc chip-calc-stack
  bank-cutoff process-death rotate rotate-upright nav-layout app-alive"
# The screens set: the same screens and sheets without the keyboard and the Odds keypad round
# (neither changes with the text size), to keep the run short.
SCREENS_STEPS="launch profile tournament-config payouts-tab payouts-editor blinds smallest-chip breaks
  ready-ticket start-fold timer-next-level timer-break table-view-land table-view-close end-break
  rotate-clock rotate-clock-back setup-panel setup-close tournament-reset tournament-reset-ok rebuy-amount
  bank bank-rename bank-buyin bank-rebuy bank-knockout-sheet bank-knockout-done pool-summary
  weights-editor weights-closed payouts-screen tools hand-ranks odds-empty chip-calc
  chip-calc-stack bank-cutoff process-death rotate rotate-upright nav-layout app-alive"
# The keyboard set (the soft-kb profiles): the keyboard over the lowest name field on the Bank, put
# away again, then a name typed with it up and the tab switched on the bar that rides above it.
KEYBOARD_STEPS="launch profile ime ime-done nav-layout bank-rename app-alive"
# Matrix steps added to --full: after the step named, these (each finds its own screen). After
# bank-rebuy-blocked the clock runs in level 2 and the Bank has its records.
FULL_EXTRAS_AFTER_bank_rebuy_blocked="rotate-clock rotate-clock-back table-view-land table-view-close process-death"
FULL_EXTRAS_BEFORE_app_alive="ime ime-done rotate rotate-upright nav-layout"
# Only on phones (smallest width under 600 dp), and only in --full: the rail steps force a phone's
# window to 720 dp and expect the bottom bar back; the tour's own table-view and rotate-to-table
# steps expect a phone's rules (landscape, the turned clock as the table view). On wide profiles
# nav-layout, table-view-land and rotate-clock check the wide rules instead.
PHONE_ONLY="rail rail-tools rail-payouts rail-restored table-view table-view-resume table-view-exit
  rotate-to-table rotate-back"
# The contact sheet's columns (one that no profile ran is left out).
KEY_SCREENS="launch payouts-tab blinds ready-ticket start-fold timer-break table-view-land rotate-clock
  setup-panel bank bank-knockout-sheet pool-summary ime payouts-screen tools hand-ranks odds-results
  chip-calc-stack process-death rotate"

usage() { sed -n '2,26p' "$0"; }
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
profile_device() { local _n _s _d _f _r _t _set dev _desc; read -r _n _s _d _f _r _t _set dev _desc <<<"$(profile_line "$1")"; echo "$dev"; }
named_set() { # the named set a profile is in, for --list
  local n=" $1 "
  if [[ " $FOCUSED_PROFILES " == *"$n"* ]]; then echo " [focused]"
  elif [[ " $DAILY_PROFILES " == *"$n"* ]]; then echo " [daily]"
  elif [[ " $SOFT_KEYBOARD_PROFILES " == *"$n"* ]]; then echo " [soft-keyboard]"; fi
  return 0
}
if (( LIST )); then
  printf '%-13s %-10s %-4s %-4s %-3s %-5s %-8s %-13s %s\n' profile size dpi font rot turns set device ""
  for p in "${PROFILES[@]}"; do
    read -r n s d f r t stepset dev desc <<<"$p"
    printf '%-13s %-10s %-4s %-4s %-3s %-5s %-8s %-13s %s%s\n' "$n" "$s" "$d" "$f" "$r" "$t" "$stepset" "$dev" "$desc" "$(named_set "$n")"
  done
  echo; echo "focused (the default): $FOCUSED_PROFILES"
  echo "daily: $DAILY_PROFILES"
  echo "soft-keyboard (on its own AVD, in a run of its own): $SOFT_KEYBOARD_PROFILES"
  echo; echo "smoke steps: $(xargs <<<"$SMOKE_STEPS")"
  echo; echo "screens steps: $(xargs <<<"$SCREENS_STEPS")"
  echo; echo "keyboard steps: $(xargs <<<"$KEYBOARD_STEPS")"
  exit 0
fi
case "$ONLY_PROFILES" in
  all) ONLY_PROFILES="$(for p in "${PROFILES[@]}"; do n="${p%% *}"; [[ "$(profile_device "$n")" == soft-kb ]] || echo "$n"; done | xargs)" ;;
  focused) ONLY_PROFILES="$FOCUSED_PROFILES" ;;
  daily) ONLY_PROFILES="$DAILY_PROFILES" ;;
  soft-keyboard) ONLY_PROFILES="$SOFT_KEYBOARD_PROFILES" ;;
esac
read -r -a RUN_PROFILES <<<"$(tr ',' ' ' <<<"${ONLY_PROFILES:-$DEFAULT_PROFILES}")"
for n in "${RUN_PROFILES[@]}"; do profile_line "$n" >/dev/null || die "unknown profile: $n (see --list)"; done
# The soft-kb profiles run on the soft-keyboard AVD, which never runs beside the main one: a matrix
# runs them or the others, not both. boot.sh (here and in every tour) then boots that AVD.
SOFT_KB_RUN=""; MAIN_RUN=""
for n in "${RUN_PROFILES[@]}"; do
  if [[ "$(profile_device "$n")" == soft-kb ]]; then SOFT_KB_RUN="$SOFT_KB_RUN $n"; else MAIN_RUN="$MAIN_RUN $n"; fi
done
[[ -z "$SOFT_KB_RUN" || -z "$MAIN_RUN" ]] || die "the soft-kb profiles ($(xargs <<<"$SOFT_KB_RUN")) run on an AVD of" \
  "their own, never beside the main one: run them alone (--profiles soft-keyboard), the rest in another matrix"
if [[ -n "$SOFT_KB_RUN" ]]; then export PP_AVD_KEYBOARD=soft; fi

# ------------------------------------------------------------------ display reset
# The compat change that turns the app's orientation requests into the user's (tablet-ignore).
ORIENTATION_OVERRIDE=OVERRIDE_ANY_ORIENTATION_TO_USER
OVERRIDE_SET=0
# The emulator is shared: put it back to its own size, density, font and rotation on any end, with
# the app's orientation requests heeded again.
reset_display() {
  adb_ shell wm size reset >/dev/null 2>&1 || true
  adb_ shell wm density reset >/dev/null 2>&1 || true
  adb_ shell settings put system font_scale 1.0 >/dev/null 2>&1 || true
  adb_ shell settings put system accelerometer_rotation 0 >/dev/null 2>&1 || true
  adb_ shell settings put system user_rotation 0 >/dev/null 2>&1 || true
  adb_ shell settings put secure show_ime_with_hard_keyboard 0 >/dev/null 2>&1 || true
  adb_ shell cmd window set-ignore-orientation-request false >/dev/null 2>&1 || true
  if (( OVERRIDE_SET )); then           # (it stops the app, so only when a profile set it)
    adb_ shell am compat reset "$ORIENTATION_OVERRIDE" "$APP_ID" >/dev/null 2>&1 || true
    OVERRIDE_SET=0
  fi
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

TOUR_PID=""; INTERRUPTED=0; CUR_ROW=""; CUR_T0=0
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
  # The profile that was running goes in the report with what it got through
  [[ -z "$CUR_ROW" ]] || printf '%s\t%s\t130\n' "$CUR_ROW" "$(since "$CUR_T0")" >> "$M/profiles.tsv"
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
    --variant "$VARIANT" --mode "$( (( FULL )) && echo full || { [[ -n "$STEPS" ]] && echo custom || { [[ -n "$SOFT_KB_RUN" ]] && echo "keyboard (soft-keyboard AVD)" || echo "smoke and screens"; }; }; )" \
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
    [[ "$name" == app-alive ]] && FULL_STEPS="$FULL_STEPS $FULL_EXTRAS_BEFORE_app_alive"
    FULL_STEPS="$FULL_STEPS $name"
    [[ "$name" == launch ]] && FULL_STEPS="$FULL_STEPS profile"
    [[ "$name" == bank-rebuy-blocked ]] && FULL_STEPS="$FULL_STEPS $FULL_EXTRAS_AFTER_bank_rebuy_blocked"
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
  elif [[ "$1" == keyboard ]]; then echo "$KEYBOARD_STEPS"
  else echo "$SMOKE_STEPS"; fi
}
# The tour's own step names: a set's name the tour doesn't have is skipped, with one warning.
KNOWN_STEPS=" $("$DEVICE_SCRIPTS/tour.sh" --list | cut -f1 | xargs) "
UNKNOWN_STEPS=""
for s in $SMOKE_STEPS $SCREENS_STEPS $KEYBOARD_STEPS $CUSTOM_STEPS; do
  [[ "$KNOWN_STEPS" == *" $s "* || " $UNKNOWN_STEPS " == *" $s "* ]] || UNKNOWN_STEPS="$UNKNOWN_STEPS $s"
done
[[ -z "$UNKNOWN_STEPS" ]] || warn "the tour has no step$UNKNOWN_STEPS; skipped (see tour.sh --list)"

FAILED_PROFILES=0
for name in "${RUN_PROFILES[@]}"; do
  read -r _ size density font rot turns stepset dev desc <<<"$(profile_line "$name")"
  t=$(date +%s)
  # Every profile from the same place: the app stopped, the launcher in front, then the display
  # changed. (Since process-death passes, a profile can end with the app in front and its clock
  # running. On the first run like that, the launch failed on exactly the profiles that followed
  # one ending so, and worked after the one that didn't; before, every profile ended on the launcher.)
  adb_ shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
  adb_ shell "input keyevent KEYCODE_HOME; cmd statusbar collapse" >/dev/null 2>&1 || true
  reset_display
  if [[ "$size" != - ]]; then adb_ shell wm size "$size"; adb_ shell wm density "$density"; fi
  adb_ shell settings put system font_scale "$font"
  adb_ shell settings put system accelerometer_rotation 0
  adb_ shell settings put system user_rotation "$rot"
  override=""
  if [[ "$dev" == soft-kb ]]; then
    # On for the whole profile, well before the keyboard is wanted: if the device reports a
    # keyboard after all, the IME still comes up ("Show on-screen keyboard"). Reset with the display.
    adb_ shell settings put secure show_ime_with_hard_keyboard 1
  fi
  if [[ "$dev" == ignore-orient ]]; then
    # Large screens may ignore an app's orientation requests: tablets since Android 12L letterbox a
    # fixed orientation in a display that keeps the user's, and Android 16 lets the user (for apps
    # that target API 36, the system) drop the request altogether. `cmd window
    # set-ignore-orientation-request` exists from Android 12 (API 31); the profile step checks it took.
    adb_ shell cmd window set-ignore-orientation-request true || warn "set-ignore-orientation-request failed"
    # Android 16's way: the app's requests become the user's, so nothing is letterboxed. That compat
    # change exists from Android 14 QPR3 images on (it is @Overridable, so adb may set it for the
    # release build too); without it the fixed orientation is letterboxed (the 12L to 15 way). It
    # stops the app; the profile's first step, launch, starts it again.
    if [[ "$(adb_ shell am compat enable "$ORIENTATION_OVERRIDE" "$APP_ID" 2>&1 | tr -d '\r' || true)" == *"Enabled change"* ]]; then
      override=user; OVERRIDE_SET=1
    else
      override=letterbox
    fi
    echo "[matrix] $name: orientation requests ignored; a fixed orientation is $( [[ "$override" == user ]] \
      && echo "the user's ($ORIENTATION_OVERRIDE, as Android 16)" || echo "letterboxed (this image has no $ORIENTATION_OVERRIDE)")"
  fi
  sleep 2
  adb_ shell "input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard; cmd statusbar collapse" >/dev/null 2>&1 || true
  demo_status_bar
  # The steps for this profile: the phone-only ones only under 600 dp (smallest width)
  phys="$(adb_ shell wm size | tr -d '\r' | sed -n 's/.*size: //p' | tail -1)"
  phys_w=${phys%x*}; (( phys_w < ${phys#*x} )) || phys_w=${phys#*x}
  dpi="$(adb_ shell wm density | tr -d '\r' | awk '{print $NF}' | tail -1)"
  steps=""
  for s in $(steps_for "$stepset"); do
    [[ "$KNOWN_STEPS" == *" $s "* ]] || continue
    if (( phys_w * 160 / dpi >= 600 )) && [[ " $(xargs <<<"$PHONE_ONLY") " == *" $s "* ]]; then continue; fi
    steps="$steps $s"
  done
  echo "[matrix] $name ($desc): $(display_state), $(wc -w <<<"$steps") steps"
  mkdir -p "$M/$name"
  # PP_UI_SCROLL: what a step expects may be below the fold on this screen; ui.py drags the page
  # to look for it (see ui.py). The plain tour never sets it.
  # PP_TOUR_RECOVER: after a failed step, close a dialog it left open (one bug, one failure).
  # PP_UI_HOLD_ROTATION: a rotated profile stays rotated through uiautomator's dumps (see ui.py).
  # PP_PROFILE_DEVICE, PP_PROFILE_ORIENTATION: the device column, and what a fixed orientation
  # became on an ignore-orient profile (see steps-matrix.sh).
  hold=""; [[ "$rot" == 0 ]] || hold="$rot"
  PP_PROFILE_DEVICE="$dev" PP_PROFILE_ORIENTATION="$override" \
  PP_UI_HOLD_ROTATION="$hold" PP_UI_SCROLL=1 PP_TOUR_RECOVER=1 PP_PROFILE="$name" PP_PROFILE_FONT="$font" PP_PROFILE_ROTATION="$rot" PP_PROFILE_TURNS="$turns" \
    "$DEVICE_SCRIPTS/tour.sh" --no-boot --no-install --keep-going --out "$M/$name" --only "$(xargs <<<"$steps")" \
    > "$M/$name/tour.out" 2>&1 &
  TOUR_PID=$!
  CUR_ROW="$(printf '%s\t%s\t%s\t%s\t%s\t%s\t%s' "$name" "$size" "$density" "$font" "$rot" "$turns" "$desc")"; CUR_T0=$t
  rc=0; wait "$TOUR_PID" || rc=$?
  TOUR_PID=""; CUR_ROW=""
  secs=$(since "$t")
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$name" "$size" "$density" "$font" "$rot" "$turns" "$desc" "$secs" "$rc" >> "$M/profiles.tsv"
  grep -E '^\[tour\] FAIL ' "$M/$name/tour.out" | sed "s/^/[matrix]   /" || true
  if [[ "$dev" == soft-kb && -f "$M/$name/display.env" ]]; then   # what the device says about keyboards
    echo "[matrix] $name: $(grep -E '^(keyboard|hard_keyboards|ime)=' "$M/$name/display.env" | xargs)"
  fi
  echo "[matrix] $name: $(grep -E '^\[tour\] (PASS|FAIL):' "$M/$name/tour.out" | sed 's/^\[tour\] //' || echo "no result (see $M/$name/tour.out)")"
  (( rc == 0 )) || FAILED_PROFILES=$((FAILED_PROFILES + 1))
done

reset_display
write_report
verdict="$(sed -n 's/^verdict=//p' "$M/summary.env" 2>/dev/null || echo FAIL)"
echo "[matrix] $verdict: ${#RUN_PROFILES[@]} profiles, $FAILED_PROFILES with failing steps, $(since "$T0")s"
echo "[matrix] report: $M/index.md (contact sheet: $M/index.html)"
[[ "$verdict" == PASS ]]
