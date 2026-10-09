#!/usr/bin/env bash
# One-command headless smoke tour of the whole app.
#
#   scripts/device/tour.sh                 # boot (if needed) + build + install + tour
#   scripts/device/tour.sh --no-build      # reuse the last built APK
#   scripts/device/tour.sh --release       # tour the minified (R8) release build instead of debug
#   scripts/device/tour.sh --keep-going    # don't stop at the first failing step
#   scripts/device/tour.sh --stop          # shut the emulator down afterwards
#
#   scripts/device/tour.sh --only launch,bank,tools   # just these steps, in this order
#   scripts/device/tour.sh --steps-file FILE          # the same, names one per line (# comments)
#   scripts/device/tour.sh --list                     # print every step (name, opt-in, what), exit
#   scripts/device/tour.sh --out DIR                  # report to DIR (no build/device-reports/latest)
#   scripts/device/tour.sh --no-boot --no-install     # use the running emulator and installed app
#                                                     # as they are (the device matrix does this)
#
# Every step: run actions/assertions with ui.py, then save <NN-name>.png (screenshot)
# and <NN-name>.xml (uiautomator tree). The tour FAILS if an expected text is missing,
# if logcat shows a FATAL EXCEPTION / ANR for the app, or if the app process dies.
# Steps registered with `extra_step` (steps-matrix.sh, steps-listing.sh) run only when --only/--steps-file names them.
#
# Output: build/device-reports/<timestamp>/{index.md, *.png, *.xml, logcat.txt, tour.log}
#         (also symlinked as build/device-reports/latest). Exit code 0 == all steps passed.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

BUILD=1; KEEP_GOING=0; STOP_AFTER=0; VARIANT=debug
BOOT=1; INSTALL=1; LIST=0; ONLY=""; OUT=""
while (( $# )); do
  case "$1" in
    --no-build) BUILD=0 ;;
    --release) VARIANT=release ;;
    --keep-going) KEEP_GOING=1 ;;
    --stop) STOP_AFTER=1 ;;
    --no-boot) BOOT=0 ;;
    --no-install) INSTALL=0; BUILD=0 ;;
    --list) LIST=1 ;;
    --only) shift; ONLY="$ONLY ${1:?--only needs step names}" ;;
    --only=*) ONLY="$ONLY ${1#--only=}" ;;
    --steps-file) shift; [[ -f "${1:-}" ]] || die "no steps file: ${1:-}"
                  ONLY="$ONLY $(sed 's/#.*//' "$1")" ;;
    --out) shift; OUT="${1:?--out needs a directory}" ;;
    -h|--help) sed -n '2,23p' "$0"; exit 0 ;;
    *) die "unknown option: $1" ;;
  esac
  shift
done
ONLY="$(tr ',' ' ' <<<"$ONLY" | xargs)"

T0=$(date +%s)
REPORT_ROOT="${PP_REPORT_ROOT:-$REPO_ROOT/build/device-reports}"
LINK_LATEST=0
[[ -n "$OUT" ]] || { OUT="$REPORT_ROOT/$(date +%Y%m%d-%H%M%S)"; LINK_LATEST=1; }
LOG="$OUT/tour.log"

# ui.py saves every dump to $PP_UI_LAST_XML; if a step ends with an assertion, that
# dump already shows the final state and capture() reuses it (saves ~2s per step).
export PP_UI_LAST_XML="$OUT/.last-dump.xml"
export PP_UI_TRACE=1        # per-dump timings in tour.log
LAST_UI=""
ui() { LAST_UI="$1"; python3 "$DEVICE_SCRIPTS/ui.py" "$@"; }

# ------------------------------------------------------------------ setup
setup() {
  mkdir -p "$OUT"
  (( LINK_LATEST )) && ln -sfn "$OUT" "$REPORT_ROOT/latest"
  : > "$LOG"
  printf 'id\tname\tstatus\tseconds\tdetail\n' > "$OUT/steps.tsv"
  local t
  t=$(date +%s)
  if (( BOOT )); then "$DEVICE_SCRIPTS/boot.sh" | tee -a "$LOG"; else require_device; fi
  BOOT_SECS=$(since "$t")

  t=$(date +%s)
  if (( !INSTALL )); then :
  elif (( BUILD )); then "$DEVICE_SCRIPTS/install.sh" "--$VARIANT" | tee -a "$LOG"
  else "$DEVICE_SCRIPTS/install.sh" "--$VARIANT" --no-build | tee -a "$LOG"; fi
  INSTALL_SECS=$(since "$t")

  adb_ logcat -b all -c >/dev/null 2>&1 || true
  APP_VERSION="$(adb_ shell dumpsys package "$APP_ID" | sed -n 's/.*versionName=//p' | head -1 | tr -d '\r')"
  # Put back what a step changed on the display however the tour ends, a signal included.
  trap 'echo "[tour] interrupted"; ABORTED=1; finish' INT TERM HUP
}

# ------------------------------------------------------------------ step registry
# `step NAME DESCRIPTION FUNCTION` registers a step of the tour; they run in the order registered.
# `extra_step` registers one that runs only when --only or --steps-file names it (steps-matrix.sh).
STEP_NAMES=(); declare -A STEP_DESC=() STEP_FN=() STEP_OPT_IN=()
step() {
  [[ -z "${STEP_FN[$1]:-}" ]] || die "step $1 registered twice"
  STEP_NAMES+=("$1"); STEP_DESC[$1]="$2"; STEP_FN[$1]="$3"
}
extra_step() { step "$@"; STEP_OPT_IN[$1]=1; }

run_tour() {
  local names=() n
  if (( LIST )); then
    for n in "${STEP_NAMES[@]}"; do
      printf '%s\t%s\t%s\n' "$n" "$([[ -n "${STEP_OPT_IN[$n]:-}" ]] && echo opt-in || echo tour)" "${STEP_DESC[$n]}"
    done
    exit 0
  fi
  if [[ -n "$ONLY" ]]; then
    read -r -a names <<<"$ONLY"
    for n in "${names[@]}"; do [[ -n "${STEP_FN[$n]:-}" ]] || die "unknown step: $n (see --list)"; done
  else
    for n in "${STEP_NAMES[@]}"; do [[ -n "${STEP_OPT_IN[$n]:-}" ]] || names+=("$n"); done
  fi
  setup
  for n in "${names[@]}"; do run_step "$n" "${STEP_DESC[$n]}" "${STEP_FN[$n]}"; done
  finish
}

# ------------------------------------------------------------------ step runner
STEP_NO=0; PASSED=0; FAILED=0; ROWS=(); ABORTED=0; BOOT_SECS=0; INSTALL_SECS=0; APP_VERSION=""
crash_check() {
  local crash
  crash="$(adb_ logcat -d -b crash 2>/dev/null | grep -E "FATAL EXCEPTION|Process: $APP_ID" || true)"
  if [[ -n "$crash" ]]; then echo "logcat crash buffer: $crash"; return 1; fi
  if adb_ logcat -d -b main,system -s ActivityManager:E 2>/dev/null | grep -q "ANR in $APP_ID"; then
    echo "ANR in $APP_ID"; return 1
  fi
  return 0
}
capture() { # $1 = id
  adb_ exec-out screencap -p > "$OUT/$1.png" 2>>"$LOG" || true
  # A step that changed the density (the rail) says so, for the layout checks of its dump
  [[ -f "$DENSITY_MARK" && -f "$OUT/.step-density" ]] && cp "$OUT/.step-density" "$OUT/$1.density"
  case "$LAST_UI" in
    assert|assert-text|wait|wait-gone|find)
      [[ -f "$PP_UI_LAST_XML" ]] && mv "$PP_UI_LAST_XML" "$OUT/$1.xml" && return 0 ;;
  esac
  ui dump --out "$OUT/$1.xml" >/dev/null 2>>"$LOG" || true
}
# run_step <name> <description> <function>
run_step() {
  local name="$1" desc="$2" fn="$3" status detail t1 secs
  STEP_NO=$((STEP_NO + 1))
  local id; id="$(printf '%02d-%s' "$STEP_NO" "$name")"
  t1=$(date +%s%N)
  local log_mark; log_mark=$(wc -l < "$LOG"); LAST_UI=""
  echo "=== $id: $desc" >>"$LOG"
  # Run the step with errexit so ANY failing command fails it, not just the last one. (Called
  # as an `if` condition, bash ignores `set -e` inside the function, so an assertion in the
  # middle of a step could fail unnoticed.) The subshell keeps LAST_UI, so hand it back.
  local rc
  set +e
  ( set -e; "$fn"; printf '%s' "$LAST_UI" > "$OUT/.last-ui" ) >>"$LOG" 2>&1
  rc=$?
  set -e
  LAST_UI="$(cat "$OUT/.last-ui" 2>/dev/null || true)"; rm -f "$OUT/.last-ui"
  if (( rc == 0 )) && crash_check >>"$LOG" 2>&1; then
    status=PASS; PASSED=$((PASSED + 1)); detail=""
  else
    status=FAIL; FAILED=$((FAILED + 1))
    # (|| true: a step that fails without a "[ui] FAIL" line must not end the whole tour here)
    detail="$(tail -n +"$((log_mark + 1))" "$LOG" | grep -E "\[ui\] (FAIL|ERROR)|crash|ANR|not running" | head -2 | tr '\n' ' ' | cut -c1-300 || true)"
    [[ -n "$detail" ]] || detail="exit $rc after: $(tail -n 1 "$LOG" | cut -c1-200)"
  fi
  capture "$id"
  # (Never after launch: it starts the app itself, and a second start would only fight it)
  [[ "$status" == FAIL && "${PP_TOUR_RECOVER:-}" == 1 && "$name" != launch ]] && recover_screen
  secs=$(( ($(date +%s%N) - t1) / 100000000 )); secs="$((secs / 10)).$((secs % 10))"
  ROWS+=("| $STEP_NO | \`$name\` | $desc | **$status** | ${secs}s | ![]($id.png) | ${detail//|/\\|} |")
  printf '%s\t%s\t%s\t%s\t%s\n' "$id" "$name" "$status" "$secs" "${detail//$'\t'/ }" >> "$OUT/steps.tsv"
  printf '[tour] %-4s %s (%ss)%s\n' "$status" "$id" "$secs" "${detail:+ -- $detail}"
  if [[ "$status" == FAIL && "$KEEP_GOING" != 1 ]]; then finish; fi
}

# The device matrix (PP_TOUR_RECOVER=1) keeps going after a failed step. A step that failed with a
# dialog (or the table view) still over the tabs would fail every step after it, so close that
# with Back first. If the app is still out of sight then (a step that failed on the launcher, as
# process-death can), open it again from the launcher, as a user would: its data stays. The failed
# step's screenshot is already taken.
recover_screen() {
  ui dump --out "$OUT/.recover.xml" >/dev/null 2>>"$LOG" || return 0
  [[ -z "$(tab_positions "$OUT/.recover.xml")" ]] || return 0
  echo "[tour] no tabs on screen after the failed step: Back, to close what it left open" >>"$LOG"
  ui back >>"$LOG" 2>&1 || true
  sleep 1
  ui dump --out "$OUT/.recover.xml" >/dev/null 2>>"$LOG" || return 0
  if ! grep -q "package=\"$APP_ID\"" "$OUT/.recover.xml"; then
    echo "[tour] the app isn't on screen: opening it again from the launcher" >>"$LOG"
    adb_ shell "input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard; cmd statusbar collapse" >>"$LOG" 2>&1 || true
    adb_ shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1 >>"$LOG" 2>&1 || true
    # Without scroll mode: until the app is in front, a drag would be on the launcher (a drag down
    # there opens the shade)
    PP_UI_SCROLL=0 ui wait "re=$TAB_RE" --timeout 15 >>"$LOG" 2>&1 || true
  fi
  return 0
}

finish() {
  trap - INT TERM HUP
  restore_display
  adb_ logcat -d -v threadtime > "$OUT/logcat.txt" 2>/dev/null || true
  adb_ logcat -d -b crash > "$OUT/logcat-crash.txt" 2>/dev/null || true
  local fatal anr
  fatal=$(grep -c "FATAL EXCEPTION" "$OUT/logcat.txt" || true)
  anr=$(grep -c "ANR in $APP_ID" "$OUT/logcat.txt" || true)
  local total_secs; total_secs=$(since "$T0")
  local verdict=PASS
  (( FAILED > 0 || fatal > 0 || anr > 0 || ABORTED )) && verdict=FAIL
  printf 'verdict=%s\npassed=%s\nfailed=%s\nrun=%s\nfatal=%s\nanr=%s\naborted=%s\nseconds=%s\n' \
    "$verdict" "$PASSED" "$FAILED" "$STEP_NO" "$fatal" "$anr" "$ABORTED" "$total_secs" > "$OUT/summary.env"
  {
    echo "# Device tour report: $verdict"
    echo
    echo "- When: $(date -Is)"
    echo "- App: $APP_ID $APP_VERSION ($VARIANT)"
    echo "- Device: $ANDROID_SERIAL, AVD \`$PP_AVD\`, Android $(adb_ shell getprop ro.build.version.release | tr -d '\r') (API $(adb_ shell getprop ro.build.version.sdk | tr -d '\r')), $(adb_ shell wm size | awk '{print $NF}' | tr -d '\r') @ $(adb_ shell wm density | awk '{print $NF}' | tr -d '\r')dpi"
    echo "- Steps: $PASSED passed, $FAILED failed, of $STEP_NO run$( (( ABORTED )) && echo ' (interrupted)')"
    echo "- Logcat: $fatal FATAL EXCEPTION, $anr ANR (full log: [logcat.txt](logcat.txt))"
    echo "- Timing: boot ${BOOT_SECS}s, build+install ${INSTALL_SECS}s, total ${total_secs}s"
    echo
    echo "Each step has a screenshot (\`NN-name.png\`) and a UI tree dump (\`NN-name.xml\`)."
    echo
    echo "| # | Step | What | Result | Time | Screenshot | Detail |"
    echo "|---|------|------|--------|------|------------|--------|"
    printf '%s\n' "${ROWS[@]}"
  } > "$OUT/index.md"
  (( STOP_AFTER )) && "$DEVICE_SCRIPTS/stop.sh" >/dev/null
  echo "[tour] $verdict: $PASSED/$STEP_NO steps passed, $fatal fatal, $anr ANR, ${total_secs}s total"
  echo "[tour] report: $OUT/index.md"
  [[ "$verdict" == PASS ]] && exit 0 || exit 1
}

# The rail step (s_rail) lowers the display density so the phone reports a tablet-wide window; the
# matrix steps (steps-matrix.sh) turn the display and show the soft keyboard. The emulator is shared
# and keeps these settings across reboots, so each such step leaves a mark with the value to go
# back to, and the tour puts it back however it ends (a matrix profile's own size, density and
# rotation stay as the profile set them).
DENSITY_MARK="$OUT/.density-changed"    # holds the override density before the step ("" = none)
ROTATION_MARK="$OUT/.rotation"          # "accelerometer_rotation user_rotation" before the step
IME_MARK="$OUT/.ime-shown"              # the step turned show_ime_with_hard_keyboard on
density_override() { adb_ shell wm density | tr -d '\r' | sed -n 's/^Override density: //p'; }
screen_width_px() { adb_ shell wm size | tr -d '\r' | sed -n 's/.*size: \([0-9]*\)x.*/\1/p' | tail -1; }
restore_density() {
  local d; d="$(cat "$DENSITY_MARK" 2>/dev/null || true)"
  if [[ -n "$d" ]]; then adb_ shell wm density "$d"; else adb_ shell wm density reset; fi
  rm -f "$DENSITY_MARK" "$OUT/.step-density"
}
restore_display() {
  if [[ -f "$DENSITY_MARK" ]]; then restore_density >/dev/null 2>&1 || true; fi
  if [[ -f "$ROTATION_MARK" ]]; then
    local acc usr; read -r acc usr < "$ROTATION_MARK" || true
    adb_ shell settings put system user_rotation "${usr:-0}" >/dev/null 2>&1 || true
    adb_ shell settings put system accelerometer_rotation "${acc:-0}" >/dev/null 2>&1 || true
    rm -f "$ROTATION_MARK"
  fi
  if [[ -f "$IME_MARK" ]]; then
    adb_ shell settings put secure show_ime_with_hard_keyboard 0 >/dev/null 2>&1 || true
    rm -f "$IME_MARK"
  fi
  return 0
}

# The dump for a check that reads a whole list (payout rows, chip rows). In the device matrix
# (PP_UI_SCROLL=1, see ui.py) a list can run below the fold on a small screen: there it is the
# whole page, merged from a drag to its end and back (ui.py page); otherwise the dump given.
page_dump() { # $1 = the dump to use outside the matrix
  if [[ "${PP_UI_SCROLL:-}" == 1 ]]; then
    python3 "$DEVICE_SCRIPTS/ui.py" page --out "$OUT/.page.xml" >&2 && echo "$OUT/.page.xml"
  else
    echo "$1"
  fi
}

# The four tabs: bottom bar on a phone held upright, rail from 600 dp (PP-087).
TAB_RE='^(Tournament|Bank|Payouts|Tools)$'
# Centre "x y" of each tab label in a dump, in tab order, as "label x y" lines. A screen can show
# the same words (the "Tournament" title, the "Payouts" folder tab), so the tabs are the one set of
# four labels that line up: in a row (the bar) or in a column (the rail).
tab_positions() { # $1 = ui dump
  python3 - "$1" <<'PY'
import itertools, re, sys, xml.etree.ElementTree as ET
labels = ("Tournament", "Bank", "Payouts", "Tools")
found = {t: [] for t in labels}
for n in ET.parse(sys.argv[1]).iter("node"):
    t = n.get("text") or ""
    b = [int(v) for v in re.findall(r"-?\d+", n.get("bounds", ""))]
    if t in found and len(b) == 4:
        found[t].append(((b[0] + b[2]) // 2, (b[1] + b[3]) // 2))
for combo in itertools.product(*(found[t] for t in labels)):
    xs = [p[0] for p in combo]; ys = [p[1] for p in combo]
    row = max(ys) - min(ys) <= 10 and xs == sorted(xs)
    column = max(xs) - min(xs) <= 10 and ys == sorted(ys)
    if row or column:
        for t, (x, y) in zip(labels, combo):
            print(t, x, y)
        break
PY
}
# The selected tab's label, from the node marked selected="true" that holds a tab label.
selected_tab() { # $1 = ui dump
  python3 - "$1" <<'PY'
import sys, xml.etree.ElementTree as ET
labels = ("Tournament", "Bank", "Payouts", "Tools")
for n in ET.parse(sys.argv[1]).iter("node"):
    if n.get("selected") == "true":
        texts = [d.get("text") for d in n.iter("node") if d.get("text") in labels]
        if texts:
            print(texts[-1])
            break
PY
}
require_tab_selected() { # $1 = label; checks the last dump
  local got; got="$(selected_tab "$PP_UI_LAST_XML")"
  echo "selected tab: ${got:-none}"
  [[ "$got" == "$1" ]] || { echo "[ui] FAIL expected the $1 tab selected, got '${got:-none}'"; return 1; }
}
# Taps a tab in the bar or rail (the screen may have a title of the same name above it).
tab() { # $1 = label
  ui dump --out "$OUT/.tabs.xml" >/dev/null
  local x y; read -r _ x y < <(tab_positions "$OUT/.tabs.xml" | grep "^$1 ") || true
  [[ -n "$x" ]] || { echo "[ui] FAIL no $1 tab on screen"; return 1; }
  ui tap-xy "$x" "$y"
}

# ------------------------------------------------------------------ steps
# Tournament ---------------------------------------------------------------
# S1 v2 (M3): one setup page (the ready ticket, players, money, blinds, a payouts row, Start) that
# folds into the clock on Start. A text field is an EditText that holds its label as a child, so
# fields are found by label: `has=Buy-in class=EditText`.
s_launch() {
  ui launch --clear
  # One header per screen (M2): the tab's name and its reset button; four tabs below. The ready
  # ticket shows level 1 with its full time; Start sits at the foot of the page.
  ui assert-text "text=Tournament" "desc=Reset tournament" Bank Payouts Tools "text~=Setup · not started" \
    "text~=Level 1 · ready" text=20:00 "has=Buy-in" "Start clock" || return 1
  require_tab_selected Tournament
}
s_tournament_config() {
  # One keystroke at a time, with recomposition in between: v1.1.12 moved the cursor in front of
  # the '.' after every change and turned "12.50" into "120.5" (B10).
  ui set-text has=Buy-in class=EditText --value ""
  for key in 1 2 . 5 0; do ui type "$key"; done
  ui assert-text text=12.50 || return 1
  ui set-text has=Bounty class=EditText --value 5
  for _ in 1 2 3 4 5; do ui tap "desc=Increase Players"; done   # the stepper: 5 -> 10 players
  ui assert-text text=12.50 text=5 "has=Players|10"            # leaving the field shows the saved amount
}

# The Payouts tab's table (S6): the rows must add up to the prize pool in the top bar ("$125 prize
# pool · 3 places paid") to the cent, and to "Adds up to $125" under them; one row per place paid;
# no lower place paying more; with a unit (cents) every place below 1st a whole number of units.
# A row is one node for TalkBack ("1st, Still playing, $63, 50%") or, failing that, the texts on
# one line.
check_payout_table() { # $1 = ui dump, $2 = rounding unit in cents (default 100)
  python3 - "$(page_dump "$1")" "${2:-100}" <<'PY'
import re, sys, xml.etree.ElementTree as ET
unit = int(sys.argv[2])
AMOUNT = r"\$[\d,]+(?:\.\d\d)?"
def cents(s):
    whole, _, frac = s[1:].replace(",", "").partition(".")
    return int(whole) * 100 + int(frac or 0)
nodes = []
for n in ET.parse(sys.argv[1]).iter("node"):
    b = [int(v) for v in re.findall(r"-?\d+", n.get("bounds", ""))]
    for t in (n.get("text") or "", n.get("content-desc") or ""):
        if t and len(b) == 4:
            nodes.append((t, (b[1] + b[3]) // 2, b[0]))
sub = next((m for t, _, _ in nodes for m in [re.fullmatch(r"(%s) prize pool · (\d+) places? paid" % AMOUNT, t)] if m), None)
if not sub:
    sys.exit("[ui] FAIL no '$X prize pool · N places paid' subtitle")
pool, places = cents(sub.group(1)), int(sub.group(2))
ORD = r"\d+(?:st|nd|rd|th)"
rows = {}
for t, cy, _ in nodes:
    m = re.fullmatch(r"(%s), (.+), (%s), [\d.]+%%" % (ORD, AMOUNT), t)
    if m:
        rows[m.group(1)] = (cy, cents(m.group(3)), m.group(2))
if not rows:  # not merged: the ordinal and the amount on one line
    for t, cy, _ in nodes:
        if re.fullmatch(ORD, t):
            found = sorted((x, a) for a, y, x in nodes if abs(y - cy) < 25 and re.fullmatch(AMOUNT, a))
            if found:
                rows[t] = (cy, cents(found[-1][1]), "")
ordered = [rows[k] for k in sorted(rows, key=lambda k: int(re.match(r"\d+", k).group()))]
amounts = [a for _, a, _ in ordered]
print("payout table: pool %d cents, %d places paid, rows %s = %d, holders %s"
      % (pool, places, amounts, sum(amounts), [h for _, _, h in ordered]))
if len(amounts) != places:
    sys.exit("[ui] FAIL %d payout rows on screen for %d places paid" % (len(amounts), places))
if sum(amounts) != pool:
    sys.exit("[ui] FAIL payout rows add up to %d cents, prize pool is %d" % (sum(amounts), pool))
adds = next((t for t, _, _ in nodes if t.startswith("Adds up to ")), None)
if adds is None or cents(adds[len("Adds up to "):]) != pool:
    sys.exit("[ui] FAIL expected 'Adds up to' the prize pool, got %r" % adds)
if any(a % unit for a in amounts[1:]):
    sys.exit("[ui] FAIL places below 1st are not whole multiples of %d cents: %s" % (unit, amounts))
if amounts != sorted(amounts, reverse=True):
    sys.exit("[ui] FAIL a lower place pays more than a higher one: %s" % amounts)
PY
}
# The selected preset's preview ("what 1st gets") must be what the 1st row pays.
check_first_preview() { # $1 = ui dump, $2 = preset label
  python3 - "$1" "$2" <<'PY'
import re, sys, xml.etree.ElementTree as ET
dump, preset = sys.argv[1], sys.argv[2]
MONEY = r"\$[\d,]+(?:\.\d\d)?"
nodes = []
for n in ET.parse(dump).iter("node"):
    texts = [t for t in (n.get("text"), n.get("content-desc")) if t]
    texts += [d.get("text") for d in n.iter("node") if d is not n and d.get("text")]
    nodes.append((n, texts))
chosen = [n for n, texts in nodes if n.get("selected") == "true" or n.get("checked") == "true"]
option = next((n for n in chosen if any(t.startswith(preset) for t in
              [n.get("text") or "", n.get("content-desc") or ""] + [d.get("text") or "" for d in n.iter("node")])), None)
if option is None:
    sys.exit("[ui] FAIL the %s preset isn't selected" % preset)
words = " ".join([option.get("text") or "", option.get("content-desc") or ""] + [d.get("text") or "" for d in option.iter("node")])
preview = re.search(MONEY, words)
# The 1st row: the amount furthest right on the line of the "1st" label
def centre_y(n):
    b = [int(v) for v in re.findall(r"-?\d+", n.get("bounds", ""))]
    return ((b[1] + b[3]) // 2, b[0]) if len(b) == 4 else (None, None)
label = next((n for n, _ in nodes if n.get("text") == "1st"), None)
first = None
if label is not None:
    y = centre_y(label)[0]
    on_line = sorted((centre_y(n)[1], n.get("text")) for n, _ in nodes
                     if re.fullmatch(MONEY, n.get("text") or "") and abs(centre_y(n)[0] - y) < 25)
    first = on_line[-1][1] if on_line else None
print("%s selected: %r; 1st row pays %s" % (preset, words.strip(), first))
if not preview or preview.group(0) != first:
    sys.exit("[ui] FAIL the %s preview (%s) isn't what 1st gets (%s)" % (preset, preview and preview.group(0), first))
PY
}
s_payouts_tab() {
  # S6: the payouts on their own tab. The pool in big digits with where it came from, the
  # presets with what 1st would get, rounding, and one row per place, adding up to the pool.
  tab Payouts
  ui assert-text "PRIZE POOL" "text~=prize pool · 3 places paid" Top-heavy Standard Flat "Round to" "Places paid" \
    "desc=Share the payouts" "desc=Edit payout structure" || return 1
  require_tab_selected Payouts
  check_payout_table "$PP_UI_LAST_XML"
}
s_payouts_preset() {
  ui tap text=Top-heavy
  ui assert-text "text~=prize pool · 3 places paid" || return 1
  check_payout_table "$PP_UI_LAST_XML"
  check_first_preview "$PP_UI_LAST_XML" Top-heavy
}
s_payouts_rounded() {
  # Round to $5 on the page itself: every place below 1st a whole $5, still adding up
  ui tap 'text=$5'
  ui assert-text "text~=prize pool · 3 places paid" || return 1
  check_payout_table "$PP_UI_LAST_XML" 500
}
s_payouts_editor() {
  # The payout structure sheet (PP-048: as tall as what it holds, no empty dialog)
  ui tap "desc=Edit payout structure"
  ui assert-text "text=Payout structure" "PRESET · WHAT 1ST GETS" "ROUND TO" "Places paid" "WEIGHT OF EACH PLACE" \
    "text=Save structure" Cancel || return 1
  ui tap text=Cancel
  ui wait-gone "text=Save structure"
}
s_payouts_share() {
  # Share as text: the system's share sheet opens with the payouts in it; Back closes it
  ui tap "desc=Share the payouts"
  ui assert-text "text~=Poker night payouts" || return 1
  ui back
  ui wait "desc=Share the payouts"
  tab Tournament
  ui wait "Start clock"
}
# The blind setup and the clock (PP-015/020/025/026/051). Default setup: 3 h of 20-minute
# rounds from a 50 chip to 5,000 = 9 levels, 50/100 to 5,000/10,000. The blinds are a section of
# the setup page now (no folder tabs).
s_blinds_tab() {
  tab Tournament
  # Scroll to the verdict itself: how far a swipe flings depends on the emulator's speed (CI's is
  # slower), so the line under the chips isn't always on screen once "Smallest chip" is.
  ui scroll-to "text~=Works: " --max 6
  ui assert-text "has=Game length" "has=Level length" "has=Starting stack" "text=Smallest chip" \
    "text~=Works: 9 levels, 50 / 100 to 5,000 / 10,000"
}
s_smallest_chip() {
  # PP-051: a row of real chips (radio buttons TalkBack names by colour and value), not a free
  # entry field
  ui assert-text "re= 1 chip$" "re= 5 chip$" "re= 25 chip$" "re= 100 chip$" || return 1
  ui tap "re= 25 chip$"
  ui assert-text "text~=Works: 9 levels, 25 / 50 to 5,000 / 10,000"
}
s_invalid_setup() {
  # PP-020: an invalid setup says why and offers the nearest valid round length
  ui set-text "has=Level length" class=EditText --value 25
  ui enter
  ui scroll-to "Use 20-min rounds (9 levels)" --max 4   # under the verdict, can be below the fold
  ui assert-text "text~=Can't build blinds" "text~=doesn't divide into 25-minute rounds" "Use 20-min rounds (9 levels)"
}
s_invalid_setup_fixed() {
  ui tap "text=Use 20-min rounds (9 levels)"
  ui wait-gone "text~=Can't build blinds"
  ui assert-text "has=Level length|20" "text~=Works: 9 levels, 25 / 50 to 5,000 / 10,000"
}
s_breaks() {
  # PP-026: a break every 4 levels with a note; the verdict counts the breaks and the new end
  ui scroll-to "has=Breaks|Off" clickable --max 3
  ui tap "has=Breaks|Off" clickable
  ui tap "text=Every 4"
  ui scroll-to "has=Break note" class=EditText --max 3
  ui set-text "has=Break note" class=EditText --value "Last rebuy"
  ui enter                                    # leaves the note; it used to click Reset instead
  ui assert-text "text=Last rebuy" "has=Breaks|Every 4" "has=Break length|10" || return 1
  ui scroll-to "text~=2 breaks, ends at 3:20" --max 3
  ui assert-text "text~=2 breaks, ends at 3:20"
}
s_ready_ticket() {
  # Before the start the ticket shows level 1, labelled, with its full time, blinds, what's next
  # and the whole game's shape. (Up to the ticket's top line rather than a fixed number of swipes:
  # with 200 % text on the smallest screen 8 swipes left the ticket's top above the screen.)
  ui scroll-to "text~=Level 1 · ready" --dir up --max 8
  ui assert-text "text~=Level 1 · ready" text=20:00 "text=25 / 50" "text~=next 50 / 100" "text~=9 levels · 2 breaks" \
    "text~=3:20 in all"
}
# Presets (PP-032): save the night's setup under a name, change a value, load the preset back, and
# the value returns. The row sits under the ticket; the sheet is modal, so the steps tap through it.
s_preset_save() {
  ui scroll-to "text=Presets" --dir up --max 4
  ui tap "text=Presets"
  ui wait "text=Save as preset…"
  ui tap "text=Save as preset…"
  ui wait "desc=Preset name"
  ui set-text "desc=Preset name" --value Friday
  ui tap "text=Save preset"
  ui wait-gone "text=Save preset"
  ui assert-text "text=Friday saved" UNDO || return 1
  ui wait-gone text=UNDO --timeout 12            # the snackbar gone, so the next one shows at once
  # Change a value the preset holds: the buy-in, 12.50 -> 30
  ui scroll-to has=Buy-in class=EditText --dir up --max 4
  ui set-text has=Buy-in class=EditText --value 30
  ui enter
  ui assert-text "has=Buy-in|30"
}
s_preset_load() {
  # The setup now differs from the preset: loading asks once, then the buy-in is 12.50 again
  ui scroll-to "text=Presets" --dir up --max 4
  ui tap "text=Presets"
  ui wait "text=Friday"
  ui assert-text "text~=Last used" "text=Share setup as text" || return 1
  ui tap "text=Friday"
  ui assert-text "text=Load Friday?" "text=Keep mine" "text=Load preset" || return 1
  ui tap "text=Load preset"
  ui wait-gone "text=Load Friday?"
  ui assert-text "text=Friday loaded" "has=Buy-in|12.50" || return 1
  ui wait-gone text=UNDO --timeout 12            # so the snackbar can't cover Start
  ui scroll up --times 4
  ui assert-text "has=Buy-in|12.50" "text~=Level 1 · ready" "text=20:00"
}
s_start_fold() {
  # Start folds the setup into the clock (S1 v2 -> S2): the setup becomes a one-line strip, the
  # ticket the running clock with its controls. The first Start asks for notifications (PP-081)
  ui tap "Start clock"
  answer_notifications_ask
  ui wait "desc=Pause timer" || return 1
  ui assert-text "text~=Level 1 of 9 · running" "text~=Level 1 · time left" "desc~=Opens setup" "desc=Remove one minute" \
    "desc=Add one minute" "desc=Next blind level" "text~=played" || return 1
  ui assert 're=^1[0-9]:[0-9]{2}$'          # the level countdown, under 20:00
}
# The clock's digits in seconds: the tallest m:ss on screen in the last dump.
hero_seconds() {
  python3 - "$PP_UI_LAST_XML" <<'PY'
import re, sys, xml.etree.ElementTree as ET
best = None
for n in ET.parse(sys.argv[1]).iter("node"):
    m = re.fullmatch(r"(\d{1,2}):(\d{2})", n.get("text") or "")
    b = [int(v) for v in re.findall(r"-?\d+", n.get("bounds", ""))]
    if m and len(b) == 4 and (best is None or b[3] - b[1] > best[0]):
        best = (b[3] - b[1], int(m.group(1)) * 60 + int(m.group(2)))
print(best[1] if best else -1)
PY
}
s_nudge() {
  # D5: -1 takes a minute off the level's time left and +1 gives it back, the clock running on
  local before after back
  ui assert "desc=Remove one minute" >/dev/null; before="$(hero_seconds)"
  ui tap "desc=Remove one minute"
  ui assert "desc=Add one minute" >/dev/null; after="$(hero_seconds)"
  ui tap "desc=Add one minute"
  ui assert "desc=Pause timer" >/dev/null; back="$(hero_seconds)"
  echo "time left: ${before}s, after -1 ${after}s, after +1 ${back}s"
  (( before - after >= 50 && before - after <= 80 )) || { echo "[ui] FAIL -1 took $((before - after))s off"; return 1; }
  (( back - after >= 40 && back - after <= 65 )) || { echo "[ui] FAIL +1 gave $((back - after))s back"; return 1; }
}
# The live clock notification (PP-081) ---------------------------------------------------------
# PP-081: on Android 13+ the first Start asks, once, for permission to post notifications (the data
# was cleared, so the permission is back to "ask"). Allow it, so the live clock can show. On an
# older Android, or with it already granted, there is no question.
answer_notifications_ask() {
  # (find is a probe: it never drags the page, even in the device matrix's scroll mode)
  if ui find "id=permission_allow_button" --timeout 8 >/dev/null 2>&1; then
    ui tap "id=permission_allow_button"
    ui wait-gone "id=permission_allow_button" >/dev/null
    echo "notifications: asked at the first Start, allowed"
  else
    echo "notifications: not asked (Android 12 or older, or already granted)"
  fi
}
# The app's notifications in `dumpsys notification --noredact`: the lines of each NotificationRecord
# posted by the app (the one the live clock shows), or nothing.
live_clock_record() {
  adb_ shell dumpsys notification --noredact 2>/dev/null | tr -d '\r' | python3 -c '
import sys
app = sys.argv[1]
inside = False
for line in sys.stdin:
    if "NotificationRecord(" in line:
        inside = ("pkg=" + app + " ") in line
    if inside:
        sys.stdout.write(line)
' "$APP_ID"
}
# Waits (up to ~15 s) for the live clock to show every pattern given (grep -E, one each), or to be
# gone with no pattern. Prints the record either way.
wait_live_clock() {
  local record="" tries
  for tries in $(seq 1 15); do
    record="$(live_clock_record)"
    if (( $# == 0 )); then
      [[ -z "$record" ]] && { echo "live clock: gone"; return 0; }
    else
      local all=1 p
      for p in "$@"; do grep -qE -- "$p" <<<"$record" || { all=0; break; }; done
      if (( all )); then
        echo "live clock record:"
        grep -E 'android\.(title|text)=|"(Pause|Resume|Open)"|showChronometer|chronometerCountDown|vis=' <<<"$record" \
          | sed 's/^ */  /' || true
        return 0
      fi
    fi
    sleep 1
  done
  echo "live clock record after 15 s:"
  sed 's/^/  /' <<<"${record:-(none)}" | head -60 || true
  if (( $# == 0 )); then echo "[ui] FAIL the live clock notification didn't go"; else echo "[ui] FAIL the live clock notification doesn't show: $*"; fi
  return 1
}
s_live_clock_shade() {
  # Home with the clock running: an ongoing notification with the level, the time left counting
  # down by itself (a chronometer, so nothing is posted per second), the blinds and the next ones,
  # Pause and Open; public on the lock screen. (The permission is granted again here in case the
  # first Start didn't ask: a grant doesn't restart the app.)
  adb_ shell pm grant "$APP_ID" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
  ui home
  wait_live_clock 'android\.title=.*\(Level 1\)' 'android\.text=.*Blinds 25 / 50 · Next 50 / 100' \
    '"Pause"' '"Open"' 'showChronometer=Boolean \(true\)' 'chronometerCountDown=Boolean \(true\)' 'vis=PUBLIC'
}
# SystemUI's demo mode (boot.sh) pins the status bar for screenshots. The shade is opened without
# it, in case it hides more than the status bar's icons, and it comes back after.
demo_mode() { # on|off
  local c cmds=("command exit")
  if [[ "$1" == on ]]; then
    cmds=("command enter" "command clock -e hhmm 1200" "command battery -e level 100 -e plugged false"
      "command network -e wifi show -e level 4 -e mobile hide" "command notifications -e visible false")
  fi
  for c in "${cmds[@]}"; do adb_ shell "am broadcast -a com.android.systemui.demo -e $c" >/dev/null 2>&1 || true; done
}
open_shade() {
  demo_mode off
  adb_ shell cmd statusbar expand-notifications
  sleep 2
}
close_shade() {
  adb_ shell cmd statusbar collapse >/dev/null 2>&1 || true
  demo_mode on
}
# Taps a button of the live clock notification in the open shade. The top notification shows its
# buttons; if it came collapsed, it is expanded first. Labels match in any case (some Android
# versions draw them in capitals).
tap_shade_button() { # label
  local sel="re=(?i)^$1\$"
  ui find "$sel" --timeout 4 >/dev/null 2>&1 || ui tap "desc=Expand" --timeout 4 || true
  ui tap "$sel"
}
s_live_clock_pause() {
  # Pause from the shade: the notification says paused, with the time left, and offers Resume
  open_shade
  tap_shade_button Pause || { close_shade; return 1; }
  wait_live_clock 'android\.title=.*\(Level 1 · paused\)' 'android\.text=.*[0-9]+:[0-9]{2} left · Blinds 25 / 50' \
    '"Resume"' '"Open"' || { close_shade; return 1; }
  close_shade
}
s_live_clock_open() {
  # Open from the shade: the clock is paused on screen too (one clock), the notification goes
  # while the app is in front, and the clock's own button resumes it
  open_shade
  tap_shade_button Open || { close_shade; return 1; }
  demo_mode on
  ui wait "desc=Resume timer" --timeout 15 || return 1
  ui assert-text "text~=Level 1 of 9 · paused" "text~=Level 1 · time left" || return 1
  wait_live_clock || return 1
  ui tap "desc=Resume timer"
  ui assert-text "desc=Pause timer" "text~=Level 1 of 9 · running"
}
s_live_clock_back() {
  # Back from Home: the clock is still running on screen, and the live clock notification goes
  # while the app is in front. (Pause and Open from the shade are opt-in steps: on the API 34
  # emulator the countdown in the open shade never lets uiautomator see an idle screen, so the
  # shade can't be read there.)
  adb_ shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || return 1
  ui wait "desc=Pause timer" --timeout 15 || return 1
  ui assert-text "text~=Level 1 of 9 · running" "text~=Level 1 · time left" || return 1
  wait_live_clock
}
s_live_clock_locked() {
  # Opt-in: the screen locks with the clock running; the live clock shows (on the lock screen,
  # public), and goes again once the app is back in front
  adb_ shell input keyevent KEYCODE_SLEEP
  local rc=0
  wait_live_clock 'android\.title=.*\(Level [0-9]+\)' 'vis=PUBLIC' || rc=1
  adb_ shell input keyevent KEYCODE_WAKEUP
  adb_ shell wm dismiss-keyguard >/dev/null 2>&1 || true
  sleep 2
  (( rc == 0 )) || return 1
  ui wait "desc=Pause timer" --timeout 15 || return 1
  wait_live_clock
}

s_timer_next_level() {
  ui tap "desc=Next blind level"
  ui assert-text "text~=Level 2 · time left" "text~=Level 2 of 9" "text=50 / 100" "desc=Previous blind level"
}
s_timer_break() {
  ui tap "desc=Next blind level"; ui wait "text~=Level 3 · time left"
  ui tap "desc=Next blind level"; ui wait "text~=Level 4 · time left"
  # The last level before the break says a break is next, with its length and note; so does the
  # schedule below the clock
  ui assert-text "text~=Next · Break" "text=10 min" "text=Last rebuy" || return 1
  ui scroll-to "text~=Break · 10 min" --max 4
  ui assert-text "text~=Break · 10 min" || return 1
  ui scroll up --times 4
  ui tap "desc=Next blind level"
  # S4: the break's own screen, with its note and End break now
  ui assert-text "text~=Break · back at Level 5" "text~=Break 1 ·" "text=Last rebuy" || return 1
  ui assert 're=^(10:00|9:[0-9]{2})$'
}
s_timer_paused() {
  # PP-046: the play button has its own place (on a break, beside End break now), never the digits
  ui tap "desc=Pause timer" --scroll-in scrollable
  ui assert "desc=Resume timer" >/dev/null || return 1
  ui scroll up --times 4
  ui assert-text "text~=paused" "text~=Break · back at Level 5"
}
# Width x height of the current screen, from the PNG header of a screencap.
screen_size() {
  adb_ exec-out screencap -p | python3 -c 'import sys,struct; d=sys.stdin.buffer.read(24); print("%dx%d" % struct.unpack(">II", d[16:24]))'
}
require_landscape() {
  local size; size="$(screen_size)"; echo "screen $size"
  [[ "${size%x*}" -gt "${size#*x}" ]] || { echo "[ui] FAIL table view isn't landscape ($size)"; return 1; }
}
s_table_view() {
  # PP-025: the table-view button forces a full-screen landscape clock (S3), here on the paused break
  ui tap "desc=Table view"
  ui wait "desc=Exit table view"
  ui assert-text "text~=Break · back at Level 5" "Resume timer" || return 1
  require_landscape
}
s_table_view_resume() {
  # The table view's own controls: resume; the footer has the table's numbers
  ui tap "desc=Resume timer"
  ui wait "desc=Pause timer"
  ui assert-text "text~=10 of 10 left" "text~=Pool " "Pause timer" "desc=Exit table view" || return 1
  require_landscape
}
s_table_view_exit() {
  ui tap "desc=Exit table view"
  ui wait-gone "desc=Exit table view"
  sleep 2
  local size; size="$(screen_size)"; echo "screen $size"
  [[ "${size%x*}" -lt "${size#*x}" ]] || { echo "[ui] FAIL not back to portrait ($size)"; return 1; }
  ui assert-text "text~=Break · back at Level 5" "text~=Break 1 ·" "desc=Table view"
}
s_end_break() {
  # S4: End break now starts the next level at once
  ui tap "text=End break now" --scroll-in scrollable
  ui wait "text~=Level 5 of 9 · running" || return 1
  ui scroll up --times 4
  ui assert-text "text~=Level 5 · time left" "text~=Level 5 of 9 · running" "Pause timer"
}
# Turning the emulator: auto-rotate off and user_rotation 1 (90 degrees) is a phone on its side.
# The step that turns it saves the settings first and puts them back if it fails; the next step
# puts them back either way.
ROTATION_MARK="$OUT/.rotation"
restore_rotation() {
  [[ -f "$ROTATION_MARK" ]] || return 0
  local acc usr; read -r acc usr < "$ROTATION_MARK"
  adb_ shell settings put system user_rotation "${usr:-0}"
  adb_ shell settings put system accelerometer_rotation "${acc:-0}"
  rm -f "$ROTATION_MARK"
}
s_rotate_to_table() {
  # PP-079: once a clock exists the Tournament tab follows the phone's rotation; on its side the
  # clock is the table view (S3), with the clock still running
  printf '%s %s\n' "$(adb_ shell settings get system accelerometer_rotation | tr -d '\r')" \
    "$(adb_ shell settings get system user_rotation | tr -d '\r')" > "$ROTATION_MARK"
  adb_ shell settings put system accelerometer_rotation 0
  adb_ shell settings put system user_rotation 1
  { ui wait "desc=Exit table view" && require_landscape && ui assert-text "text~=Level 5 · time left" "Pause timer"; } \
    || { restore_rotation; return 1; }
}
# Waits up to 5 s for the screen to be portrait (port) or landscape (land), from screenshots alone.
wait_screen() {
  local i size=""
  for i in 1 2 3 4 5 6 7 8 9 10; do
    size="$(screen_size)"
    if [[ "$1" == land && "${size%x*}" -gt "${size#*x}" ]] || [[ "$1" == port && "${size%x*}" -lt "${size#*x}" ]]; then
      echo "screen $size"; return 0
    fi
    sleep 0.5
  done
  echo "[ui] FAIL the screen didn't turn $1 ($size)"; return 1
}
s_rotate_close() {
  # PP-094 #2: ✕ in the turned table view shows the clock upright for this turn only; turned again,
  # the table view is back (before, ✕ held the clock upright until you left the tab). The tour
  # turns the phone with rotation locked (user_rotation), and Android 14 itself puts the locked
  # rotation back to upright once the app asks for the upright clock, so "still on its side" can't
  # be held here; on a phone with auto-rotate on the app reads the accelerometer instead (unit
  # tested in TournamentRotationTest and PhoneHoldRulesTest).
  { ui tap "desc=Exit table view" && wait_screen port; } || { restore_rotation; return 1; }
  sleep 2.5 # longer than the app waits before it counts the phone as upright
  adb_ shell settings put system user_rotation 1   # on its side again
  { wait_screen land && ui wait "desc=Exit table view" && ui assert-text "text~=Level 5 · time left" "Pause timer"; } \
    || { restore_rotation; return 1; }
}
s_rotate_back() {
  # Upright again: the clock (S2), same level, still running
  restore_rotation
  adb_ shell settings put system user_rotation 0
  ui wait-gone "desc=Exit table view"
  sleep 1
  local size; size="$(screen_size)"; echo "screen $size"
  [[ "${size%x*}" -lt "${size#*x}" ]] || { echo "[ui] FAIL not back to portrait ($size)"; return 1; }
  ui assert-text "text~=Level 5 · time left" "desc=Table view" "Pause timer"
}
s_setup_panel() {
  # The strip opens setup over the running clock: players, rebuys-until, ante and breaks change
  # any time; money and blinds are locked while the clock runs
  ui scroll up --times 3
  ui tap "desc~=Opens setup"
  ui assert-text "text~=Setup · clock still running" "text~=Change any time" "text~=Locked while the clock runs" \
    "has=Rebuys until" "Unlock to edit…" || return 1
}
s_setup_unlock() {
  # "Unlock to edit…" asks first, in a sheet; then money and blinds open
  ui tap "text=Unlock to edit…" --scroll-in scrollable
  ui assert-text "Edit money and blinds?" "Keep locked" "text=Unlock to edit" || return 1
  ui tap "text=Unlock to edit"
  ui wait-gone "Edit money and blinds?"
  ui assert-text "text~=Unlocked: money and blinds" "has=Buy-in" || return 1
  ui scroll-to "has=Level length" --max 4   # the blinds sit below the money (and the bounty type)
  ui assert-text "has=Level length"
}
s_setup_closed() {
  # Closing setup locks it again; the clock never stopped
  ui tap "desc=Close setup"
  ui wait-gone "text~=Unlocked: money and blinds"
  ui assert-text "text~=Level 5 · time left" "Pause timer" "desc~=Opens setup"
}
s_tournament_reset_dialog() {
  # Mid-game, reset is "New tournament…" in the menu; it asks first
  ui tap "desc=More options"
  ui tap "text=New tournament…"
  ui assert-text "Reset tournament?" Cancel "text=Reset tournament"
}
s_tournament_reset_confirm() {
  ui tap "text=Reset tournament"
  ui wait-gone "text=Reset tournament?"
  # Setup unfolds again with the defaults: level 1 ready at 20:00, 50 / 100
  ui assert-text "text~=Setup · not started" "text~=Level 1 · ready" text=20:00 "text=50 / 100" "Start clock" \
    "desc=Reset tournament"
}
s_rebuy_amount() {
  ui scroll-to has=Rebuy class=EditText --max 3
  ui set-text has=Rebuy class=EditText --value 10
  ui enter
  ui assert-text "has=Rebuy|10"
}

# Bank (S5 v2) ---------------------------------------------------------------
# One line per player under a labelled header (Buy-in, Rebuy, Out, Paid; Add-on is hidden while
# add-ons cost $0). A tap applies at once and the snackbar offers UNDO for 8 s (PP-030).
s_bank() {
  tab Bank
  ui assert-text Player Buy-in Rebuy Out Paid "Player 1" "text~=players · " Collected "Paid out" Breakdown \
    "text=Payout structure" "desc=More options" "desc=Nothing to undo" || return 1
  require_tab_selected Bank
}
s_bank_rename() {
  # No Done/Enter: switching tabs must keep the name (B18; v1.1.12 dropped it). The keyboard is
  # up when the tab is tapped: the bar rides above it (edge to edge, M2).
  ui set-text "text=Player 1" class=EditText --value Alice
  tab Tournament
  ui wait "Start clock"
  tab Bank
  ui assert-text text=Alice
}
s_bank_buyin() {
  # No confirm dialog any more: the tap records the buy-in, and the snackbar says what happened
  ui tap "desc=Alice, buy-in, not paid"
  ui assert-text "desc=Alice, buy-in, paid" "text~=Alice paid the buy-in · " text=UNDO "text~=collected"
}
s_bank_undo() {
  # UNDO on the snackbar takes it back; then record it again for the steps after
  ui tap text=UNDO
  ui assert-text "desc=Alice, buy-in, not paid" || return 1
  ui tap "desc=Alice, buy-in, not paid"
  ui assert-text "desc=Alice, buy-in, paid" "desc~=Undo: Alice paid the buy-in"
}
s_bank_rebuy() {
  ui tap "desc=Alice, rebuy, none yet"
  ui assert-text "desc=Alice, rebuy, 1 taken" "text=Rebuy for Alice · \$10"
}
s_bank_knockout_sheet() {
  # S5b: who knocked out whom. Nothing happens until a choice is made.
  ui tap "desc=Knock out Player 2"
  ui assert-text "text=Player 2 is out" "text=5TH PLACE" "re=^Nobody" "text=Knock out Player 2"
}
s_bank_knockout_done() {
  # Pick Alice and confirm: applied at once, with no second dialog
  ui tap text=Alice
  ui tap "text=Knock out Player 2"
  ui wait-gone "text=Player 2 is out"
  ui assert-text "desc=Player 2, out, 5th, knocked out by Alice. Bring back" "text~=Player 2 is out in 5th" \
    "text~=OUT · 1" || return 1
  # The whole list in the matrix: assert-text may have found the badge below the fold and put the
  # page back
  check_placement_badge "$(page_dump "$PP_UI_LAST_XML")"
}
# The knocked-out player's place is a badge in the Out column, clear of the name (PP-047;
# v1.1.12 painted a big number over the name, v1.2 a badge on its top edge).
check_placement_badge() {
  python3 - "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
def box(n):
    return [int(v) for v in re.findall(r"-?\d+", n.get("bounds", ""))]
nodes = list(ET.parse(sys.argv[1]).iter("node"))
badges = [n for n in nodes if re.fullmatch(r".+, out, \d+(st|nd|rd|th)\b.*Bring back", n.get("content-desc") or "")]
fields = [n for n in nodes if "EditText" in (n.get("class") or "")]
if not badges:
    sys.exit("[ui] FAIL no place badge in the Out column")
for badge in badges:
    name = badge.get("content-desc").split(",")[0]
    field = next((f for f in fields if f.get("text") == name), None)
    if field is None:
        sys.exit("[ui] FAIL no name field for %r" % name)
    bx1, by1, bx2, by2 = box(badge)
    fx1, fy1, fx2, fy2 = box(field)
    print("badge %r %s, name field %s" % (badge.get("content-desc"), box(badge), box(field)))
    if not (fy1 < (by1 + by2) // 2 < fy2 + 60):
        sys.exit("[ui] FAIL the badge isn't on its player's row")
    if bx1 < fx2:
        sys.exit("[ui] FAIL the badge (from x=%d) overlaps the name (to x=%d)" % (bx1, fx2))
PY
}
s_bank_champion() {
  # The other three go out with nobody credited; Alice is left, on a gold row at the top
  local p
  for p in 3 4 5; do
    ui tap "desc=Knock out Player $p"
    ui tap "re=^Nobody"
    ui tap "text=Knock out Player $p"
    ui wait-gone "text=Player $p is out"
  done
  ui assert-text "desc=Alice, champion" CHAMPION "text~=Finished · Alice wins · " "text~=OUT · 4"
}
s_bank_payout_sheet() {
  # S5c: what to hand the champion, and how it adds up
  ui tap "desc~=Alice, paid out, \$"
  ui assert-text "text=Pay Alice" "Hand over" "text~=Mark paid · \$"
}
s_bank_paid() {
  ui tap "text~=Mark paid · \$"
  ui wait-gone "text=Pay Alice"
  ui assert-text "desc=Alice, paid out" "text~=Paid Alice \$" "text~=Finished · Alice wins · "
}
s_pool_summary() {
  # The pool and where it came from; the rebuy is inside the prize pool, not on top of it
  ui tap text=Breakdown
  ui assert-text "text=Pool breakdown" "Prize pool" "· of which rebuys" "Total pool" 1st 2nd Close
}
s_weights_editor() {
  ui tap text=Close
  ui wait-gone "text=Pool breakdown"
  ui tap "text=Payout structure"
  ui assert-text "Places paid" "WEIGHT OF EACH PLACE" "text=Save structure" Cancel
}
s_weights_close() {
  ui tap text=Cancel
  ui wait-gone "text=Save structure"
}
s_bank_scrolled() {
  ui scroll down --times 2
  ui assert-text "text~=OUT · 4" "desc=Player 2, out, 5th, knocked out by Alice. Bring back"
}
# What the Tournament tab's rebuy steps (M3) check in the Bank: Alice's one rebuy, still recorded.
bank_has_alices_rebuy() {
  ui assert-text "desc=Alice, rebuy, 1 taken"
}

# The rebuy cutoff (PP-030): "rebuys until level 1", with the clock in level 2, closes the Rebuy
# column. It is set with setup's "Rebuys until" field, so both builds check it.
s_bank_cutoff() {
  tab Tournament
  ui scroll-to "has=Rebuys until" clickable --max 3
  ui tap "has=Rebuys until" clickable
  ui tap "text=End of L1"
  ui assert-text "has=Rebuys until|End of L1" || return 1
  ui tap "Start clock"
  ui wait "desc=Pause timer"
  ui tap "desc=Next blind level"
  ui assert-text "text~=Level 2 · time left" || return 1
  tab Bank
  if rebuy_column_folds; then
    ui assert-text "1 rebuy" || return 1
    if ui find "desc~=, rebuy, " --timeout 1 >/dev/null 2>&1; then echo "[ui] FAIL the closed Rebuy column still shows"; return 1; fi
  else
    ui assert-text "desc=Rebuy, closed" "desc=Alice, rebuy, closed after level 1, 1 taken"
  fi
}
# Under 360 dp (the matrix's small profiles) a closed Rebuy column folds into the line under each
# name, "↻ 1" (M4's Z2), so there is no Rebuy cell to see or tap.
rebuy_column_folds() {
  local size density; size="$(adb_ shell wm size | tr -d '\r' | sed -n 's/.*size: //p' | tail -1)"
  density="$(adb_ shell wm density | tr -d '\r' | awk '{print $NF}' | tail -1)"
  local w=${size%x*} h=${size#*x}
  [[ "$(adb_ shell dumpsys window displays | tr -d '\r' | grep -o 'mCurrentRotation=ROTATION_[0-9]*' | head -1)" =~ ROTATION_(90|270) ]] && w=$h
  (( w * 160 / density < 360 ))
}
s_bank_rebuy_blocked() {
  # A tap does nothing after the cutoff; the note under the list says why
  if rebuy_column_folds; then
    ui assert-text "1 rebuy" || return 1        # folded under the name: nothing to tap
  else
    ui tap "desc=Alice, rebuy, closed after level 1, 1 taken"
    sleep 1
    ui assert-text "desc=Alice, rebuy, closed after level 1, 1 taken" || return 1
  fi
  ui find "text~=Rebuy for Alice" && { echo "[ui] FAIL a rebuy was recorded after the cutoff"; return 1; }
  ui scroll-to "text~=Rebuys closed after level 1" --max 3
  ui assert-text "text~=Rebuys closed after level 1. Taken ones stay filled"
}
s_bank_cutoff_reset() {
  # Reset the tournament for the steps after: clock back to level 1, cutoff and rebuys cleared
  tab Tournament
  ui tap "desc=More options"
  ui tap "text=New tournament…"
  ui tap "text=Reset tournament"
  ui wait-gone "text=Reset tournament?"
  ui assert-text "text~=Level 1 · ready" "Start clock"
}

# Payouts tab (S6) after the Bank: the finished night, with names ------------------------------
s_payouts_nav() {
  # The champion and the runner-up by name in their rows, still adding up to the prize pool
  tab Payouts
  ui assert-text "text~=prize pool · 2 places paid" "desc=Share the payouts" "text~=Alice" "text~=Player 5" || return 1
  require_tab_selected Payouts
  check_payout_table "$PP_UI_LAST_XML"
}
s_payouts_nav_editor() {
  ui tap "desc=Edit payout structure"
  ui assert-text "Places paid" "text=Save structure" Cancel || return 1
  ui tap text=Cancel
  ui wait-gone "text=Save structure"
}
s_payouts_nav_back() {
  # B16: Back from any tab goes to the first tab, not back through every tab tapped
  ui back
  ui assert-text "Start clock" "desc=Reset tournament" || return 1
  require_tab_selected Tournament
}

# History (PP-037) -----------------------------------------------------------------------------------
# The tour's finished night (Alice 1st, Player 5 2nd) is over once Player 5 is paid too: the Payouts
# tab then offers "Save this night", once, and the night shows in Tools > History, with Alice on 5
# points (5 players, 1st). Each part scrolls to what it checks last, since a swipe flings less on
# GitHub's emulator.
s_history_save() {
  tab Bank
  ui scroll-to "desc~=Player 5, paid out, " --max 3
  ui tap "desc~=Player 5, paid out, "
  ui tap "text~=Mark paid · \$"
  ui wait-gone "text=Pay Player 5"
  tab Payouts
  ui tap "text=Save this night"
  ui assert-text "text=Saved to History, in the Tools tab." || return 1
  if ui find "text=Save this night" --timeout 1 >/dev/null 2>&1; then echo "[ui] FAIL still offered after saving"; return 1; fi
  tab Tools
  ui scroll-to text=History --max 4
  ui tap text=History
  ui assert-text "text=1 night saved" "text=Most points all time: Alice" "desc=1st, Alice, 5 points, 1 night · 1 win" || return 1
  require_tab_selected Tools || return 1
  ui scroll-to "5 players · Alice won" --max 3
  ui tap "5 players · Alice won"
  ui assert-text "desc=Share this night" Alice "Player 5" || return 1
  ui back                                      # the night -> History
  ui assert-text "text=1 night saved" || return 1
  ui back                                      # History -> the Tools list
  ui assert-text "text=Seat draw" text=History
}

# Cash game in the Bank (S13, M7) ---------------------------------------------------------------
# Its own ledger beside the tournament's: three players buy in, Theo tops up, everyone's chips are
# counted ($120 in, $120 out), and the settle-up says who pays whom. Then back to the tournament,
# which must be as the steps above left it.
s_cash_mode() {
  tab Bank
  ui assert-text "desc=Alice, champion" || return 1   # the tournament's Bank, from the steps above
  ui tap "text=Cash game"
  ui assert-text "text=Nobody at the table yet" "text=Cash game · nobody in yet" "text=Add player" "desc=Nothing to undo"
  ui assert "has=Cash game" checked             # the switch: its option is checked, not its label
  require_tab_selected Bank
}
# The last snackbar gone (8 s), so it can't be over what the next tap aims at.
cash_snackbar_gone() {
  ui wait-gone text=UNDO --timeout 12
}
cash_add_player() { # $1 = name, $2 = buy-in in dollars
  cash_snackbar_gone
  ui scroll-to "text=Add player" --max 4
  ui tap "text=Add player"
  ui wait "text=Add a player"
  ui set-text "desc=Name" --value "$1"
  ui set-text "desc=Buy-in" --value "$2"
  ui tap "text=Add $1"
  ui wait-gone "text=Add a player"
  ui assert-text "text=$1 bought in · \$$2" "has=$1|in \$$2|chips not counted yet"
}
s_cash_players() {
  cash_add_player Dana 40
  cash_add_player Sam 20
  cash_add_player Theo 40
  ui scroll up --times 4
  ui assert-text "text=Cash game · 3 players · \$100 in play" "text=COUNTING" "text~=3 still to count: Dana, Sam, Theo"
}
s_cash_top_up() {
  # Theo's sheet: the top-up starts at his last buy-in ($40); make it $20
  ui scroll-to "has=Theo|in \$40" --max 4
  ui tap "has=Theo|in \$40"
  ui wait "text=BOUGHT IN · \$40"
  ui set-text "desc=Top-up for Theo" --value 20
  ui tap "text=Top up \$20"
  ui assert-text "text=BOUGHT IN · \$60" "text=Top-up 1"   # (the snackbar is behind the sheet)
}
cash_count() { # $1 = name, $2 = chips counted out in dollars, $3 = what the sheet then says
  ui set-text "desc=$1's chips counted out" --value "$2"
  ui enter                                    # leaves the field: the count is saved
  ui assert-text "text=$3" || return 1      # saved: the sheet says what it means for the night
  ui tap text=Done
  ui wait-gone "text=Done"
}
s_cash_count() {
  cash_count Theo 45 "Down \$15 on the night"
  ui scroll-to "has=Dana|in \$40" --max 4 --dir up
  ui tap "has=Dana|in \$40"
  cash_count Dana 75 "Up \$35 on the night"
  ui scroll-to "has=Sam|in \$20" --max 4
  ui tap "has=Sam|in \$20"
  cash_count Sam 0 "Down \$20 on the night"
  ui scroll up --times 4
  # Each line reads to TalkBack as "Dana, in $40, out $75, up $35"
  ui assert-text "text=BALANCED" "has=Dana|in \$40|out \$75|up \$35" "has=Sam|in \$20|out \$0|down \$20" \
    "has=Theo|in \$60|out \$45|down \$15"
}
s_cash_settle() {
  # Two payments for three players, largest debt first; tick Theo's
  cash_snackbar_gone
  ui scroll-to "text=Share as text" --max 6     # the whole settle-up card is then in view
  ui assert-text "has=Sam pays Dana|\$20" "has=Theo pays Dana|\$15" "text=Tick each one when paid" || return 1
  ui tap "has=Theo pays Dana|\$15"
  ui assert "has=Theo pays Dana" checked
  ui assert-text "text=Theo paid Dana \$15"
}
s_cash_share() {
  cash_snackbar_gone
  ui scroll-to "text=Share as text" --max 6
  ui tap "text=Share as text"
  ui assert-text "text~=Poker night: cash game" || return 1
  ui back
  ui wait "text=Share as text"
}
s_cash_undo() {
  # Tick Sam's payment, then UNDO on the snackbar; then the top bar's Undo takes Theo's tick back
  cash_snackbar_gone
  ui scroll-to "text=Share as text" --max 6
  ui tap "has=Sam pays Dana|\$20"
  ui tap text=UNDO
  ui wait-gone "has=Sam pays Dana" checked
  ui scroll up --times 6
  ui tap "desc=Undo: Theo paid Dana \$15"
  ui scroll-to "has=Theo pays Dana|\$15" --max 4
  ui wait-gone "has=Theo pays Dana" checked
  ui assert-text "has=Theo pays Dana|\$15" "has=Sam pays Dana|\$20" "desc~=Undo: Sam cashed out"
}
s_cash_back_to_tournament() {
  # The tournament's Bank is as it was: Alice the champion, paid
  ui scroll up --times 6
  ui tap text=Tournament                      # the switch: the topmost "Tournament" on screen
  ui assert-text "desc=Alice, champion" "desc=Alice, paid out" "text~=Finished · Alice wins" "text=Payout structure" || return 1
  ui assert "has=Tournament" checked
  require_tab_selected Bank
}

# Clearing the Rebuy amount to retype it must not wipe recorded rebuys (PP-014).
s_rebuy_retype() {
  tab Tournament
  ui scroll-to has=Rebuy class=EditText --max 3
  ui set-text has=Rebuy class=EditText --value ""
  for key in 1 5; do ui type "$key"; done
  tab Bank                                    # leave the field by switching tabs
  bank_has_alices_rebuy
}
s_rebuy_zero_prompt() {
  tab Tournament
  ui scroll-to has=Rebuy class=EditText --max 3
  ui set-text has=Rebuy class=EditText --value ""
  ui enter                                    # leave it empty: asks before clearing anything
  ui assert-text "Turn rebuys off?" Keep "re=^Clear [0-9]+ rebuys?$"
}
s_rebuy_kept() {
  ui tap text=Keep
  ui wait-gone "Turn rebuys off?"
  ui assert-text text=15 || return 1          # the saved amount comes back into the field
  tab Bank
  bank_has_alices_rebuy
}

# Tools --------------------------------------------------------------------
s_tools() {
  # S7: the tools as a list, then the Sound section (it was a volume dialog behind "Settings")
  tab Tools
  ui assert-text "Everything works offline" text=Odds "text=Chip set" "text=Hand ranks" || return 1
  require_tab_selected Tools || return 1
  # The Sound section comes after the tools: on a phone it is below the fold
  ui scroll-to "Test chime" --max 4
  ui assert-text text=Sound "desc=Chime volume" "Test chime"
}
# The Sound row is one switch; the uiautomator node that holds "Sound" and is checkable.
sound_checked() { # prints true/false from the last dump
  python3 - "$PP_UI_LAST_XML" <<'PY'
import sys, xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).iter("node"):
    if n.get("checkable") == "true" and any(d.get("text") == "Sound" for d in n.iter("node")):
        print(n.get("checked"))
        break
PY
}
s_sound_off() {
  ui tap text=Sound
  ui assert "desc=Chime volume" || return 1
  local state; state="$(sound_checked)"; echo "sound switch checked=$state"
  [[ "$state" == false ]] || { echo "[ui] FAIL the sound switch didn't turn off"; return 1; }
  # With the sound off the volume and the test chime rest
  ui find "desc=Chime volume" | grep -q DISABLED || { echo "[ui] FAIL volume still enabled while muted"; return 1; }
}
s_sound_on() {
  ui tap text=Sound
  ui assert "desc=Chime volume" || return 1
  local state; state="$(sound_checked)"; echo "sound switch checked=$state"
  [[ "$state" == true ]] || { echo "[ui] FAIL the sound switch didn't turn back on"; return 1; }
  ui tap "Test chime"                          # plays the level chime once; must not crash
  ui assert-text "Test chime" "desc=Chime volume"
}
s_hand_ranks() {
  # S12: a tool's screen keeps Tools selected (B16) and has a back arrow. Each hand shows how often
  # it comes up by the river: the royal flush is 1 in 30,940 of the 133,784,560 seven-card hands.
  ui scroll-to "text=Hand ranks" --dir up --max 4   # the Sound steps left the list scrolled down
  ui tap "text=Hand ranks"
  ui assert-text "Best to worst" desc=Back "re=Royal flush" "re=1 in 30,940" || return 1
  require_tab_selected Tools || return 1
  ui scroll-to "re=High card" --max 6
  ui assert-text "re=High card" "re=17\.4%" "re=kicker"
}
# Seat draw (S14, PP-036) ----------------------------------------------------
s_seat_draw() {
  # Seat draw opens from the Tools list with the Bank's players (Player 1 is Alice since the Bank
  # steps), nothing drawn yet, and Tools still selected.
  ui back                                      # Hand ranks -> the Tools list
  ui scroll-to "text=Seat draw" --max 4
  ui tap "text=Seat draw"
  ui assert-text "text=Seat draw" desc=Back "text=From the Bank" "re=^Alice, Player 2" "text=Draw seats" \
    "re=^[0-9]+ tables?( of [0-9]+|: .+)$" || return 1
  require_tab_selected Tools || return 1
  seat_names "$PP_UI_LAST_XML" > "$OUT/.seat-names.txt"
  echo "players: $(cat "$OUT/.seat-names.txt")"
}
# The players line on the seat draw screen ("Alice, Player 2, ..."), from a UI dump.
seat_names() { # $1 = ui dump
  python3 - "$1" <<'PY'
import sys, xml.etree.ElementTree as ET
line = next((n.get("text") for n in ET.parse(sys.argv[1]).iter("node") if (n.get("text") or "").startswith("Alice, ")), None)
if line is None:
    sys.exit("[ui] FAIL no players line on screen")
print(line)
PY
}
# Every seat on the seat draw screen into $1, sorted: TalkBack's one stop per seat ("Seat 2, Alice,
# table 1, King of spades, Button") and the "Button: Alice, seat 2" lines. A dump only holds what is
# on screen, so it reads a page at a time down the screen, then scrolls back to the top.
seat_lines() { # $1 = output file
  local page
  : > "$1"
  for page in 1 2 3 4; do
    ui dump --out "$OUT/.seats.xml" >/dev/null
    python3 - "$OUT/.seats.xml" >> "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).iter("node"):
    for key in ("content-desc", "text"):
        value = n.get(key) or ""
        if re.match(r"^(Seat \d+, .+, table \d+|Button: .+, seat \d+$)", value):
            print(value)
PY
    ui scroll down >/dev/null
  done
  ui scroll up --times 4 >/dev/null
  sort -u -o "$1" "$1"
}
# Checks a draw read by seat_lines ($1) against the players ($2, "Alice, Player 2, ..."): everyone
# seated once, seats from 1 at every table, tables within one of each other. With $3 = 1 (dealt):
# every seat has a card, the high card has the button (a tie on rank goes by suit, spades first),
# "Button: NAME, seat N" names it, and the next seats post the blinds (heads-up: the button posts
# the small blind). Before the deal no seat has a card or a pill.
check_seat_draw() { # $1 = seat lines, $2 = players file, $3 = dealt (0/1)
  python3 - "$1" "$2" "$3" <<'PY'
import collections, re, sys
lines = open(sys.argv[1], encoding="utf-8").read().splitlines()
players = open(sys.argv[2], encoding="utf-8").read().strip().split(", ")
dealt = sys.argv[3] == "1"
RANKS = ["2", "3", "4", "5", "6", "7", "8", "9", "10", "Jack", "Queen", "King", "Ace"]
SUITS = ["clubs", "diamonds", "hearts", "spades"]          # the house order, lowest first
card_re = re.compile(r"^(Ace|King|Queen|Jack|\d+) of (spades|hearts|diamonds|clubs)$")
tables, buttons = collections.defaultdict(dict), {}
for line in lines:
    seat = re.match(r"^Seat (\d+), (.+?), table (\d+)(?:, (.*))?$", line)
    if seat:
        rest = (seat.group(4) or "").split(", ") if seat.group(4) else []
        tables[int(seat.group(3))][int(seat.group(1))] = (seat.group(2), rest)
    button = re.match(r"^Button: (.+), seat (\d+)$", line)
    if button:
        buttons[button.group(1)] = int(button.group(2))
def fail(message):
    sys.exit("[ui] FAIL " + message)
seated = sorted(name for table in tables.values() for name, _ in table.values())
if seated != sorted(players):
    fail("seated %s, the players are %s" % (seated, players))
if sorted(tables) != list(range(1, len(tables) + 1)):
    fail("tables %s" % sorted(tables))
sizes = [len(t) for _, t in sorted(tables.items())]
if max(sizes) - min(sizes) > 1:
    fail("tables not balanced: %s" % sizes)
for number, table in sorted(tables.items()):
    k = len(table)
    if sorted(table) != list(range(1, k + 1)):
        fail("table %d seats %s" % (number, sorted(table)))
    if not dealt:
        if any(rest for _, rest in table.values()):
            fail("table %d shows cards or pills before the deal: %s" % (number, table))
        print("table %d: %s" % (number, ", ".join("%d %s" % (s, table[s][0]) for s in sorted(table))))
        continue
    cards = {}
    for s, (name, rest) in table.items():
        card = card_re.match(rest[0]) if rest else None
        if card is None:
            fail("table %d seat %d has no card: %s" % (number, s, rest))
        cards[s] = (RANKS.index(card.group(1)), SUITS.index(card.group(2)))
    high = max(cards, key=cards.get)
    small = high if k == 2 else high % k + 1
    big = small % k + 1
    want = {s: [] for s in table}
    want[high].append("Button")
    want[small].append("Small blind")
    want[big].append("Big blind")
    for s, (name, rest) in table.items():
        if rest[1:] != want[s]:
            fail("table %d seat %d (%s) shows %s, expected %s" % (number, s, name, rest[1:], want[s]))
    if buttons.get(table[high][0]) != high:
        fail("table %d: no 'Button: %s, seat %d' line (have %s)" % (number, table[high][0], high, buttons))
    print("table %d: %s; button seat %d (%s), small blind %d, big blind %d" % (
        number, ", ".join("%d %s %s" % (s, table[s][0], table[s][1][0]) for s in sorted(table)), high,
        table[high][1][0], small, big))
if dealt and len(buttons) != len(tables):
    fail("%d button lines for %d tables" % (len(buttons), len(tables)))
if not dealt and buttons:
    fail("a button line before the deal: %s" % buttons)
PY
}
s_seat_draw_seats() {
  # Two tables: seats per table down to half the players (three at least), then draw.
  local players per seats
  players=$(( $(tr -cd ',' < "$OUT/.seat-names.txt" | wc -c) + 1 ))
  per=$(( (players + 1) / 2 ))
  if (( per < 3 )); then per=3; fi
  for (( seats = 9; seats > per; seats-- )); do ui tap "desc=Decrease Seats per table"; done
  ui assert-text "re=^[0-9]+ tables?( of [0-9]+|: .+)$" || return 1
  ui tap "text=Draw seats"
  ui assert-text "text=Deal for the button" "text=Redraw seats" "text=TABLE 1" "desc=Share the seats" \
    "re=^Seat 1, .+, table 1$" || return 1
  seat_lines "$OUT/.seats-drawn.txt"
  check_seat_draw "$OUT/.seats-drawn.txt" "$OUT/.seat-names.txt" 0
}
s_seat_draw_button() {
  # The classic draw for the button: a card face up to each seat, the high card takes it, the rule
  # is on screen, and the button seat is named per table with its blinds.
  ui tap "text=Deal for the button"
  ui assert-text "re=^Button: .+, seat [0-9]+$" "text=Deal again" "text~=High card gets the button" || return 1
  seat_lines "$OUT/.seats-dealt.txt"
  check_seat_draw "$OUT/.seats-dealt.txt" "$OUT/.seat-names.txt" 1
}
s_seat_draw_undo() {
  # Redraw applies at once with Undo on the snackbar; Undo brings the dealt draw back exactly.
  ui tap "text=Redraw seats"
  ui assert-text "text=Seats redrawn" text=UNDO "text=Deal for the button" || return 1
  ui tap text=UNDO
  ui wait "text=Deal again" || return 1
  ui wait-gone text=UNDO --timeout 15 || return 1
  seat_lines "$OUT/.seats-undone.txt"
  diff "$OUT/.seats-dealt.txt" "$OUT/.seats-undone.txt" || { echo "[ui] FAIL Undo didn't bring the draw back"; return 1; }
  ui assert-text "re=^Button: .+, seat [0-9]+$"
}
s_seat_draw_share() {
  # Share hands the draw to the system share sheet as plain text (ACTION_SEND); Back closes the
  # sheet and leaves the draw on screen.
  local focus="" try
  ui tap "desc=Share the seats"
  for try in $(seq 20); do
    focus="$(adb_ shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus || true)"
    [[ "$focus" =~ intentresolver|[Cc]hooser|[Rr]esolver ]] && break
    sleep 0.5
  done
  echo "focus: $focus"
  [[ "$focus" =~ intentresolver|[Cc]hooser|[Rr]esolver ]] || { echo "[ui] FAIL the share sheet didn't open"; return 1; }
  ui back
  ui assert-text "text=Seat draw" "text=TABLE 1" "re=^Button: .+, seat [0-9]+$" "desc=Share the seats"
}
s_seat_draw_back() {
  # Back returns to the Tools list, Tools still selected
  ui back
  ui assert-text text=Odds "text=Hand ranks" "text=Seat draw" || return 1
  require_tab_selected Tools
}
s_odds_empty() {
  # The Settings volume dialog may still be open: the dump only sees a dialog's window, so if the
  # Odds tile isn't there, close the dialog first.
  ui find text=Odds >/dev/null 2>&1 || ui back
  ui tap text=Odds
  # The slot being filled is on screen above the open keypad without scrolling: the page keeps it in
  # view (find never scrolls, even in the matrix's scroll mode; on a small profile the rest below
  # may need the page dragged).
  ui find "desc=Player 1, card 1, empty. Next" >/dev/null
  ui assert-text text=Odds "Player 1" "Player 2" "desc=Player 1, card 1, empty. Next" "text~=Pick cards" "Add player"
}
s_card_picker() {
  # The rank-then-suit keypad is docked (no dialog): 13 ranks and delete, then suits that wait for a rank.
  ui assert-text desc=Ace desc=10 desc=2 "desc=Delete card" "desc=Spades, pick a rank first" Random Done
}
key_card() { # rank suit, as TalkBack names the keys: "Ace Spades", "10 Hearts"
  ui tap "desc=$1"
  ui tap "desc=$2"
}
s_hole_cards() {
  key_card Ace Spades; key_card King Spades; key_card Queen Hearts; key_card Queen Diamonds
  # The keypad moved on by itself to the flop after the last hole card.
  ui assert-text "desc=Player 1, card 1, Ace of spades" "desc=Player 2, card 2, Queen of diamonds" "text~=Flop · card 1"
}
s_flop() {
  key_card Jack Spades; key_card 10 Spades; key_card 2 Clubs
  ui tap text=Done                            # put the keypad away to see the results
  ui assert-text "desc=Flop card 1, Jack of spades" "desc=Flop card 3, 2 of clubs" "desc=Turn, empty"
}
# Centre y of the first node whose text or description matches the regex $2, from `ui find` output in $1.
y_of() { grep -E "$2" <<<"$1" | head -1 | sed -n 's/.* @[0-9]*,\([0-9]*\)$/\1/p'; }
s_odds_results() {
  # A♠K♠ v Q♥Q♦ on J♠10♠2♣ is enumerated exactly, live: Player 1 wins 555 and Player 2 435 of the
  # 990 turn-and-river runouts, no ties. Before v1.2.0 the kicker-order bug (PP-011) showed about
  # 49.25 / 50.75 here, so anything else is a regression.
  ui wait "text~=win 56.06 · tie 0.00" --timeout 30 || return 1
  local found p1 p2 w1 w2
  # (page_dump: on a matrix profile Player 2's row can be below the fold; here it is the screen)
  found="$(ui find --from "$(page_dump "")" 're=(Player 1, options|Player 2, options|win 56\.06 · tie 0\.00|win 43\.94 · tie 0\.00)')"
  p1="$(y_of "$found" "Player 1, options")"; p2="$(y_of "$found" "Player 2, options")"
  w1="$(y_of "$found" "win 56\.06")";        w2="$(y_of "$found" "win 43\.94")"
  echo "odds rows: Player 1 y=$p1, 56.06 at y=$w1; Player 2 y=$p2, 43.94 at y=$w2"
  [[ -n "$p1" && -n "$p2" && -n "$w1" && -n "$w2" ]] || { echo "[ui] FAIL expected 56.06 / 43.94 for Player 1 / Player 2"; return 1; }
  (( p1 < w1 && w1 < p2 && p2 < w2 )) || { echo "[ui] FAIL 56.06 / 43.94 are not in Player 1's / Player 2's rows"; return 1; }
  ui assert-text "text~=exact · 990 runouts" "text~=Nut flush draw + gutshot" "text~=Overpair, queens"
}
s_odds_runout() {
  # Run it out (S10) on the same hand: deal the turn and the river from a fresh random seed, run it
  # twice, then back leaves run it out with the hand and its odds untouched.
  ui tap "text=Run it out" --scroll-in scrollable
  ui assert-text "text=Run it out" "text~=Flop dealt" || return 1
  ui tap "text=Deal the turn" --scroll-in scrollable
  ui wait "text=Deal the river" --timeout 20 || return 1
  ui assert-text "text~=Turn dealt" || return 1
  ui tap "text=Deal the river"
  ui wait "text~=River dealt" --timeout 20 || return 1
  ui assert-text "re=River dealt · (Player [12] (wins|holds)|Split pot)" || return 1
  ui tap "text=Run it twice" --scroll-in scrollable
  ui assert-text "text~=Run twice · Each run is half the pot" || return 1
  ui back                                     # back leaves run it out, not the odds screen
  ui assert-text text=Odds "text~=win 56.06 · tie 0.00" "desc=Turn, empty"
}
s_odds_card_clears() {
  # Typing the turn drops the flop's numbers at once and works out the turn: the 7♥ leaves Player 1
  # exactly 16 rivers of 44 (36.36%).
  ui tap "desc=Turn, empty"
  key_card 7 Hearts
  ui tap text=Done
  ui wait-gone "text~=win 56.06" --timeout 10 || return 1
  ui wait "text~=win 36.36 · tie 0.00" --timeout 30 || return 1
  ui assert-text "desc=Turn, 7 of hearts" "text~=Turn · exact · 44 runouts"
}
s_odds_more_players() {
  # Two more seats with unknown hands (the slider is gone: "Add player"), then fold Player 3.
  ui tap "text=Add player" --scroll-in scrollable
  ui tap text=Done
  ui tap "text=Add player" --scroll-in scrollable
  ui tap text=Done
  ui scroll up --times 3
  ui tap "desc=Player 3, options" --scroll-in scrollable
  ui tap text=Fold
  ui assert-text "text~=Player 4" "text~=Folded" "re=^≈?[0-9]+\.[0-9]%$"
}
s_odds_reset_new_hand() {
  # New hand (the header's ↺) clears every card and fold at once and keeps the four seats; the
  # keypad stays closed under the Undo snackbar. Undo is on the app's snackbar once MainActivity
  # hosts it (M2); tap it if it's there.
  ui tap "desc=New hand"
  ui assert-text "desc=Player 1, card 1, empty" "text~=Pick cards" || return 1
  if ui find text=UNDO >/dev/null 2>&1; then
    ui tap text=UNDO
    ui wait "desc=Turn, 7 of hearts" --timeout 10 || return 1
    ui tap "desc=New hand"
    ui wait "text~=Pick cards" --timeout 10 || return 1
  else
    echo "no Undo snackbar on screen (MainActivity doesn't host the app's snackbar yet)"
  fi
  ui scroll-to "desc=Player 4, options"
}
s_odds_reset_table() {
  # "Clear table" at the end of the page goes back to two empty seats. New hand's Undo snackbar
  # (8 s) sits over the end of the page, so let it go first; with the keypad closed the page may
  # fit without scrolling.
  ui wait-gone text=UNDO --timeout 15 || return 1
  ui tap "text=Clear table" --scroll-in scrollable
  ui wait-gone "desc=Player 3, options" --timeout 10 || return 1
  ui scroll up --times 3
  ui assert-text "desc=Player 1, card 1, empty" "desc=Player 2, options"
}
s_chip_calc() {
  # S11: the chip calculator is the chip set now, planned from the chips you own (no Generate)
  ui back
  ui tap "text=Chip set"
  ui assert-text "text=Chip set" desc=Back "desc=Reset chip set" "text~=Chips you own" "Green 25" \
    "desc=Increase Green 25 chips, by 5" "text~=Add a colour" || return 1
  require_tab_selected Tools
}
s_chip_calc_stack() {
  # Each player's stack: the piles add up to the Tournament's 5,000, and the reserve check says
  # how many more stacks the box holds for rebuys and add-ons
  ui scroll-to "re=enough left for|no full stack is left" --max 4
  ui assert-text "text~=Each player gets" "text~=5,000 from Tournament setup" "re=chips? a stack ·" || return 1
  check_chip_totals "$(page_dump "$PP_UI_LAST_XML")" 5000
}
# The stack picture against its totals in a UI dump: every pile's "N × V" times its value must add up
# to the stack ($2), the chips to the "N chips a stack" line, and the colours to its count.
check_chip_totals() {
  python3 - "$1" "$2" <<'PY'
import re, sys, xml.etree.ElementTree as ET
stack = int(sys.argv[2])
labels = []
for n in ET.parse(sys.argv[1]).iter("node"):
    for key in ("text", "content-desc"):
        if n.get(key):
            labels.append(n.get(key))
def value(text):
    text = text.replace(",", "")
    if text.endswith("K"):
        return int(float(text[:-1]) * 1000)
    if text.endswith("M"):
        return int(float(text[:-1]) * 1000000)
    return int(text)
piles = {}
for label in labels:
    for count, chip in re.findall(r"(\d+) × ([\d.,]+[KM]?)", label):
        piles[value(chip)] = int(count)
totals = next((re.search(r"(\d+) chips? a stack · (\d+) colours? · ([\d,]+)", l) for l in labels
               if re.search(r"chips? a stack ·", l)), None)
if not piles or totals is None:
    sys.exit("[ui] FAIL no stack picture or totals on screen: %s" % labels)
worth = sum(v * c for v, c in piles.items())
chips, colours, shown = int(totals.group(1)), int(totals.group(2)), value(totals.group(3))
print("stack: %s = %d in %d chips; totals say %d chips, %d colours, %d" % (
    " + ".join("%d × %d" % (c, v) for v, c in sorted(piles.items())), worth, sum(piles.values()), chips, colours, shown))
if worth != stack or shown != stack:
    sys.exit("[ui] FAIL the piles add up to %d and the totals say %d, not %d" % (worth, shown, stack))
if chips != sum(piles.values()) or colours != len(piles):
    sys.exit("[ui] FAIL the totals (%d chips, %d colours) don't match the piles" % (chips, colours))
PY
}
s_chip_calc_colorup() {
  # The color-up plan reads the clock's schedule (5,000 from 50s; the tour's reset cleared the
  # breaks, so color-ups fall at the start of a level): the 25s go into 100s from level 4
  ui scroll-to "re=Counted for \d+ stacks in play" --max 4
  ui assert-text "text~=Color-up plan" "re=Start of Level 4: green 25s into black 100s" "re=blacks needed"
}
s_chip_calc_short() {
  # Only 10 greens for 5 players: no stack adds up, so the screen says what is short and by how much.
  # Edit the colour in its sheet (the stepper moves in fives; the sheet takes exact counts).
  ui scroll up --times 4
  ui tap "text=Green 25"
  ui assert-text "text=Edit Green 25" "text~=Each chip is worth" "text~=How many you own" "text=Remove Green 25" || return 1
  ui set-text class=EditText text=150 --value 10
  ui enter                                    # Done puts the keyboard away
  ui tap text=Save
  ui wait-gone "text=Edit Green 25" --timeout 10 || return 1
  ui scroll-to "re=\+10 more" --max 4
  ui assert-text "re=Short 10 green 25s for 5 players\." "re=full stacks? of 5,000" "re=With 10 more"
}
s_chip_calc_reset() {
  # Reset applies at once (back to the 500-chip starting set) and offers Undo on the snackbar
  ui scroll up --times 4
  ui tap "desc=Reset chip set"
  ui assert-text "text~=500-chip starting set" "text=UNDO" "desc=Increase Green 25 chips, by 5" || return 1
  ui wait-gone text=UNDO --timeout 15 || return 1
  ui scroll-to "re=enough left for|no full stack is left" --max 4
  ui assert-text "re=chips? a stack ·" || return 1
  check_chip_totals "$(page_dump "$PP_UI_LAST_XML")" 5000
}
s_chip_calc_settings() {
  # The old advanced settings live on as stack settings: keep 2 stacks back for rebuys, and the
  # color-up plan counts them as in play. Until the stepper is touched the number is the
  # Tournament's estimate (PP-091 #3): none here, the reset left no rebuys or add-ons
  ui scroll-to "re=(?i)stack settings" --max 6
  ui tap "re=(?i)^stack settings"
  ui scroll-to "re=Lots of small chips" --max 4   # the lowest thing checked (a fling's reach varies)
  ui assert-text "text=Starting stack" "text=Keep back for rebuys and add-ons" "text=Colours per stack, at most" \
    "re=More small chips" "re=Lots of small chips" "text=From Tournament setup: no rebuys or add-ons." || return 1
  ui tap "desc=Increase Keep back for rebuys and add-ons"
  ui tap "desc=Increase Keep back for rebuys and add-ons"
  ui assert-text "text=Your own · Tournament setup suggests 0" "text=Use Tournament's estimate" || return 1
  ui scroll up --times 6
  ui scroll-to "re=You keep 2 back" --max 6
  ui assert-text "re=You keep 2 back" || return 1
  ui scroll-to "re=Counted for 7 stacks in play" --max 6
  ui assert-text "re=Counted for 7 stacks in play \(5 players and 2 kept back\)"
}
# Shot clock, dealer's choice and the equity quiz ------------------------------------------------
# The shot clock's seconds left, from its face in the last dump ("Shot clock, 27 seconds left");
# -1 when the face isn't there.
shot_clock_seconds() {
  python3 - "$PP_UI_LAST_XML" <<'PY'
import re, sys, xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).iter("node"):
    m = re.match(r"Shot clock, (\d+) seconds? left", n.get("content-desc") or "")
    if m:
        print(m.group(1))
        break
else:
    print(-1)
PY
}
s_shot_clock() {
  # The shot clock opens from the Tools list full and waiting, with the Bank's players in its time
  # bank (Player 1 is Alice since the Bank steps), Tools still selected.
  ui back                                      # Chip set -> the Tools list
  ui scroll-to "text=Shot clock" --max 4
  ui tap "text=Shot clock"
  ui assert-text "text=Shot clock" desc=Back "desc=Shot clock, 30 seconds left" "text=Tap the clock to start" \
    "text=30 s" "text=45 s" "text=60 s" || return 1
  require_tab_selected Tools
}
s_shot_clock_run() {
  # One tap on the face starts a decision. Pause holds the time left (the same number seconds
  # later); a time-bank card adds 30 s to it and is gone from that player, with the way to give
  # everyone theirs back.
  local paused later carded
  ui tap "desc=Shot clock, 30 seconds left"
  ui assert-text "text=Tap the clock for the next decision" text=Pause text=Reset || return 1
  ui tap text=Pause
  ui assert-text text=Resume text=Paused "re=^Shot clock, [0-9]+ seconds? left, paused$" || return 1
  paused="$(shot_clock_seconds)"
  sleep 3
  ui assert "re=^Shot clock, [0-9]+ seconds? left, paused$" >/dev/null; later="$(shot_clock_seconds)"
  echo "paused at ${paused}s, 3 s later ${later}s"
  [[ "$paused" == "$later" ]] && (( paused > 0 && paused < 30 )) || { echo "[ui] FAIL the paused clock moved"; return 1; }
  ui scroll-to "desc=Play a card for Alice, 30 more seconds" --max 6 --in scrollable
  ui tap "desc=Play a card for Alice, 30 more seconds"
  # Alice's row is still in view (her cards, then the face's seconds, from the same dump)
  ui assert-text "desc=1 of 2 cards left" "re=^Shot clock, [0-9]+ seconds left, paused$" || return 1
  carded="$(shot_clock_seconds)"
  echo "after the card: ${carded}s"
  (( carded == paused + 30 )) || { echo "[ui] FAIL the card added $((carded - paused))s, not 30"; return 1; }
  ui scroll-to "text=Give everyone their cards back" --max 4 --in scrollable
  ui assert-text "text=Give everyone their cards back"
}
s_shot_clock_reset() {
  # Everyone's cards back (with Undo on the snackbar), then Reset: full and waiting again
  ui tap "text=Give everyone their cards back"
  ui assert-text "text=Cards given back" text=UNDO || return 1
  ui wait-gone text=UNDO --timeout 15 || return 1
  ui scroll-to text=Reset --dir up --max 6 --in scrollable   # the face never scrolls: drag the controls
  ui tap text=Reset
  ui assert-text "desc=Shot clock, 30 seconds left" "text=Tap the clock to start" || return 1
  ui scroll-to "desc=Play a card for Alice, 30 more seconds" --max 6 --in scrollable
  ui assert-text "re=^2 of 2 cards left$"
}
s_dealers_choice() {
  # Dealer's choice: the wheel of the nine classics, nothing picked yet
  ui back                                      # Shot clock -> the Tools list
  ui scroll-to "text=Dealer's choice" --max 4
  ui tap "text=Dealer's choice"
  ui assert-text "text=Dealer's choice" "text=9 games on the wheel" "desc=Game wheel, 9 games" "text=Spin the wheel" \
    "text=Spin the wheel to pick the next game." || return 1
  require_tab_selected Tools
}
# The game the wheel last picked, from the result card's TalkBack line in the last dump.
dealers_pick() { grep -o 'content-desc="Next game: [^"]*"' "$PP_UI_LAST_XML" | head -1 | sed 's/.*Next game: //; s/"$//'; }
s_dealers_spin() {
  # A spin (instant, with animations off) picks a game on the wheel and shows its rules; the next
  # spin never picks the same game twice running.
  local first second
  ui tap "text=Spin the wheel"
  ui scroll-to "re=^Next game: " --max 4
  ui assert-text "text=NEXT GAME" \
    "re=^Next game: (Texas Hold'em|Omaha|Big O|Seven-card stud|Razz|2-7 Triple Draw|Badugi|Pineapple|Crazy Pineapple)$" || return 1
  first="$(dealers_pick)"
  ui scroll-to "text=Spin the wheel" --dir up --max 6
  ui tap "text=Spin the wheel"
  ui scroll-to "re=^Next game: " --max 4
  ui assert "re=^Next game: " >/dev/null
  second="$(dealers_pick)"
  echo "picked: $first, then $second"
  [[ -n "$first" && "$first" != "$second" ]] || { echo "[ui] FAIL the wheel picked $first twice running"; return 1; }
}
s_dealers_house_game() {
  # A house game goes on the wheel (ten games now); every game's rules open in a sheet
  ui scroll-to "desc=Add a house game" --max 8
  ui set-text "desc=Add a house game" --value "Guts"
  ui tap text=Add
  ui assert-text "text=10 games on the wheel" "desc=Remove Guts" || return 1
  ui scroll-to "text=Rules for every game" --max 4
  ui tap "text=Rules for every game"
  ui assert-text "text=Rules for every game" "text=Texas Hold'em" "text~=exactly two of your cards" || return 1
  ui back                                      # close the sheet
  ui assert-text "text=Dealer's choice" "desc=Remove Guts"
}
# Each hand's equity from the quiz's TalkBack lines ("Hand A: ..., 62.8%"), one a line.
quiz_equities() { grep -o 'content-desc="Hand [A-C]: [^"]*"' "$PP_UI_LAST_XML" | sed -n 's/.*, \([0-9]*\.[0-9]\)%.*/\1/p'; }
s_equity_quiz() {
  # The equity quiz deals a spot at once: two hands face up, who's ahead?
  ui back                                      # Dealer's choice -> the Tools list
  ui scroll-to "text=Equity quiz" --max 4
  ui tap "text=Equity quiz"
  ui assert-text "text=Equity quiz" "text=Two hands · Who's ahead" "re=^Hand A: .+ of .+, .+ of .+$" \
    "re=^Hand B: .+ of .+, .+ of .+$" || return 1
  require_tab_selected Tools
}
s_equity_quiz_answer() {
  # Pick Hand A: the engine's exact odds show under both hands (adding up to 100%), with right or
  # not, and the score counts one answer.
  local sum
  ui tap "re=^Hand A: "
  ui wait "re=^(Right!|Not this time)$" --timeout 30 || return 1
  ui assert-text "re=^Hand A: .+, [0-9]+\.[0-9]%" "re=^Hand B: .+, [0-9]+\.[0-9]%" "re=^Right, [01], of 1$" || return 1
  sum="$(quiz_equities | awk '{ s += $1 } END { print s + 0 }')"
  echo "equities add up to $sum"
  awk -v s="$sum" 'BEGIN { exit !(s >= 99.8 && s <= 100.2) }' || { echo "[ui] FAIL the equities add up to $sum"; return 1; }
  ui scroll-to "text=Deal again" --max 4
  ui assert-text "text=Deal again"
}
s_equity_quiz_range() {
  # Three hands, asked how often the first wins: the five ranges are the answers; a guess names the pick
  ui tap "text=Deal again"
  ui scroll-to "text=Three hands" --max 6
  ui tap "text=Three hands"
  ui scroll-to "text=How often" --max 4
  ui tap "text=How often"
  ui scroll-to "re=^Hand C: " --dir up --max 6
  ui assert-text "text=Three hands · How often" "re=^Hand C: " || return 1
  ui scroll-to "text=Over 80%" --max 6
  ui assert-text "text=How often does Hand A win?" "text=Under 20%" "text=40–60%" "text=Over 80%" || return 1
  ui tap "text=40–60%"
  ui wait "re=^(Right!|Not this time)$" --timeout 30 || return 1
  ui scroll-to "text=Your pick: 40–60%" --max 4
  ui assert-text "text=Your pick: 40–60%" "re=^Hand A wins [0-9]+\.[0-9]%: " || return 1
  ui back                                      # the Tools list
  ui assert-text text=Odds "text=Equity quiz" || return 1
  require_tab_selected Tools
}
s_back_to_tournament() {
  tab Tournament
  ui assert-text "Start clock" "text~=Level 1 · ready"
}

# Rail (PP-087) --------------------------------------------------------------
# A tablet-wide window on the phone emulator: at density 240 the 1080 px screen is 720 dp wide
# (on a matrix profile, whatever density makes its width 720 dp), so the four tabs move from the
# bottom bar to a rail down the left edge. The activity is recreated on the change; the screen
# must come back where it was.
s_rail() {
  density_override > "$DENSITY_MARK"
  local density=$(( $(screen_width_px) * 160 / 720 ))
  echo "density=$density" > "$OUT/.step-density"
  adb_ shell wm density "$density"
  sleep 3
  ui assert-text "Start clock" "text~=Level 1 · ready" || return 1
  local tabs; tabs="$(tab_positions "$PP_UI_LAST_XML")"; echo "$tabs"
  python3 - "$tabs" <<'PY' || return 1
import sys
rows = [line.split() for line in sys.argv[1].strip().splitlines()]
if len(rows) != 4:
    sys.exit("[ui] FAIL expected four tabs, found %d" % len(rows))
xs = [int(r[1]) for r in rows]; ys = [int(r[2]) for r in rows]
if max(xs) - min(xs) > 10 or ys != sorted(ys) or min(xs) > 300:
    sys.exit("[ui] FAIL tabs are not a rail down the left edge: %s" % rows)
print("rail: four tabs at x=%d, top down" % xs[0])
PY
  require_tab_selected Tournament
}
s_rail_tools() {
  tab Tools
  ui assert-text text=Odds "text=Chip set" text=Sound || return 1
  require_tab_selected Tools
}
s_rail_payouts() {
  tab Payouts
  ui assert-text "PRIZE POOL" "text~=prize pool · " || return 1
  require_tab_selected Payouts
  check_payout_table "$PP_UI_LAST_XML"
}
s_rail_restored() {
  restore_density
  sleep 3
  ui assert-text "PRIZE POOL" || return 1
  local tabs; tabs="$(tab_positions "$PP_UI_LAST_XML")"; echo "$tabs"
  python3 - "$tabs" <<'PY' || return 1
import sys
rows = [line.split() for line in sys.argv[1].strip().splitlines()]
ys = [int(r[2]) for r in rows]
if len(rows) != 4 or max(ys) - min(ys) > 10:
    sys.exit("[ui] FAIL expected the bottom bar back: %s" % rows)
print("bottom bar back: four tabs at y=%d" % ys[0])
PY
  require_tab_selected Payouts
}

# Progressive knockout (PP-035) --------------------------------------------------------------------
# Last of the screens, because it clears the Bank the steps above leave (the bounty type is fixed
# while anyone is out). A $5 bounty set to Progressive in setup; Player 1 knocks Player 2 out, takes
# half ($2.50) in cash, and the other half goes onto Player 1's own bounty: $7.50, shown under the
# name. Each part scrolls to what it checks last, since a swipe flings less on the CI emulator.
s_bank_pko() {
  tab Bank
  ui tap "desc=More options"
  ui tap "text=Reset bank…"
  ui tap "re=^Clear [0-9]+ players$"
  ui wait-gone "text=Reset the bank?"
  tab Tournament
  ui scroll-to has=Bounty class=EditText --max 3
  ui set-text has=Bounty class=EditText --value 5
  ui enter
  ui scroll-to text=Progressive --max 3
  ui tap text=Progressive
  ui scroll-to "text~=adds the other half to the winner" --max 3
  ui assert "has=Progressive" checked || return 1
  ui assert-text "text~=adds the other half to the winner" || return 1
  tab Bank
  ui tap "desc=Knock out Player 2"
  ui assert-text "text=Player 2 is out" "text~=half to whoever knocked Player 2 out" || return 1
  ui tap "text~=Player 1"
  ui scroll-to "text~=Player 1 takes" --max 3
  ui assert-text "text=Player 1 takes \$2.50 now · bounty up to \$7.50" || return 1
  ui tap "text=Knock out Player 2"
  ui wait-gone "text=Player 2 is out"
  ui assert-text "text~=Player 1 takes \$2.50, bounty now \$7.50" "has=Player 1|bounty \$7.50, 1 knockout"
}
s_app_alive() {
  local pid; pid="$(adb_ shell pidof "$APP_ID" | tr -d '\r')"
  [[ -n "$pid" ]] || { echo "app process is not running"; return 1; }
  echo "app pid $pid"
}

step launch               "Fresh launch (data cleared): setup page, ready ticket" s_launch
step tournament-config    "Type buy-in 12.50 key by key, bounty 5, players 10"  s_tournament_config
step payouts-tab          "Payouts tab (S6): rows add up to the prize pool"     s_payouts_tab
step payouts-preset       "Top-heavy: 1st gets what its preview said"           s_payouts_preset
step payouts-rounded      "Round to \$5: lower places in \$5, adds up"           s_payouts_rounded
step payouts-editor       "Payout structure sheet opens and closes"             s_payouts_editor
step payouts-share        "Share as text: the share sheet has the payouts"      s_payouts_share
step blinds               "Blind setup on the setup page, with its verdict"     s_blinds_tab
step smallest-chip        "Smallest chip from a row of chips: pick 25"          s_smallest_chip
step invalid-setup        "25-minute rounds: reason and nearest fix shown"      s_invalid_setup
step invalid-setup-fixed  "Apply the fix: 20-minute rounds"                     s_invalid_setup_fixed
step breaks               "Breaks every 4 levels, note 'Last rebuy'"            s_breaks
step ready-ticket         "The ticket: level 1 ready, 20:00, 25 / 50, 3:20"     s_ready_ticket
step preset-save          "Save as preset 'Friday', then change the buy-in"     s_preset_save
step preset-load          "Load Friday back: asks once, the buy-in returns"     s_preset_load
step start-fold           "Start: setup folds into the running clock (S2)"      s_start_fold
step nudge                "-1 and +1: a minute off the level, and back"         s_nudge
step live-clock-shade     "Home: the live clock notification (PP-081)"          s_live_clock_shade
step live-clock-back      "Back to the app: still running; notification gone"   s_live_clock_back
step timer-next-level     "Skip to level 2"                                     s_timer_next_level
step timer-break          "Skip to the first break (S4)"                        s_timer_break
step timer-paused         "Pause on the break"                                  s_timer_paused
step table-view           "Table-view button: landscape clock, on the break"    s_table_view
step table-view-resume    "Table view: resume; the table's numbers"             s_table_view_resume
step table-view-exit      "Leave table view: back to portrait"                  s_table_view_exit
step end-break            "End break now: level 5 starts"                       s_end_break
step rotate-to-table      "Phone on its side: the table view (PP-079)"          s_rotate_to_table
step rotate-close         "✕ holds only this turn; turn again: table view"      s_rotate_close
step rotate-back          "Upright again: the clock, same level"                s_rotate_back
step setup-panel          "Strip opens setup over the clock, money locked"      s_setup_panel
step setup-unlock         "Unlock to edit… asks, then unlocks money and blinds" s_setup_unlock
step setup-closed         "Close setup: the clock still running"                s_setup_closed
step tournament-reset     "New tournament…: the reset question"                 s_tournament_reset_dialog
step tournament-reset-ok  "Reset: setup unfolds, level 1 ready"                 s_tournament_reset_confirm
step rebuy-amount         "Rebuy amount \$10 for the Bank steps"                 s_rebuy_amount
step bank                 "Bank tab (S5 v2): labelled header, top bar"          s_bank
step bank-rename          "Rename Player 1 to Alice, switch tabs, name kept"    s_bank_rename
step bank-buyin           "Buy-in in one tap; snackbar with UNDO"               s_bank_buyin
step bank-undo            "UNDO takes the buy-in back; record it again"         s_bank_undo
step bank-rebuy           "Record a rebuy in one tap"                           s_bank_rebuy
step bank-knockout-sheet  "Knockout sheet for Player 2 (S5b)"                   s_bank_knockout_sheet
step bank-knockout-done   "Alice knocked Player 2 out; 5th badge clear of name" s_bank_knockout_done
step bank-champion        "Three more out; Alice is the champion"               s_bank_champion
step bank-payout-sheet    "Pay-out sheet for the champion (S5c)"                s_bank_payout_sheet
step bank-paid            "Mark paid: Paid column, finished subtitle"           s_bank_paid
step pool-summary         "Pool breakdown sheet"                                s_pool_summary
step weights-editor       "Payout structure sheet from the Bank"                s_weights_editor
step weights-closed       "Cancel the payout structure sheet"                   s_weights_close
step bank-scrolled        "Bank list scrolled down"                             s_bank_scrolled
step rebuy-retype         "Clear and retype the Rebuy amount; rebuy kept"       s_rebuy_retype
step rebuy-zero-prompt    "Leave Rebuy empty: asks before clearing"             s_rebuy_zero_prompt
step rebuy-kept           "Keep: amount and rebuy stay"                         s_rebuy_kept
step bank-cutoff          "Rebuys until level 1, clock in level 2: closed"      s_bank_cutoff
step bank-rebuy-blocked   "A rebuy tap after the cutoff does nothing"           s_bank_rebuy_blocked
step bank-cutoff-reset    "Reset the tournament: clock and cutoff cleared"      s_bank_cutoff_reset
step payouts-nav          "Payouts tab: the finished night by name, adds up"    s_payouts_nav
step payouts-nav-editor   "Payouts tab: structure sheet opens and closes"       s_payouts_nav_editor
step payouts-nav-back     "Back from a tab returns to Tournament (B16)"         s_payouts_nav_back
step history-save         "Pay everyone: save the night once; it is in History" s_history_save
step cash-mode            "Bank: switch to the cash game (S13), nobody in yet"   s_cash_mode
step cash-players         "Cash: Dana \$40, Sam \$20, Theo \$40 buy in"           s_cash_players
step cash-top-up          "Cash: Theo tops up \$20 from his sheet"              s_cash_top_up
step cash-count           "Cash: chips counted, \$120 in, \$120 out: balanced"   s_cash_count
step cash-settle          "Cash: settle-up, 2 payments; tick Theo's"            s_cash_settle
step cash-share           "Cash: share the settle-up as text"                   s_cash_share
step cash-undo            "Cash: UNDO on the snackbar, then the top bar's Undo" s_cash_undo
step cash-tournament      "Back to Tournament: the tournament's Bank intact"    s_cash_back_to_tournament
step tools                "Tools tab: tool list and Sound (S7)"                 s_tools
step sound-off            "Sound off: switch off, volume and chime rest"        s_sound_off
step sound-on             "Sound back on; test chime"                           s_sound_on
step hand-ranks           "Hand ranks (S12): how often by the river, kickers"   s_hand_ranks
step seat-draw            "Seat draw (S14): the Bank's players, Tools selected" s_seat_draw
step seat-draw-seats      "Draw seats: everyone once, balanced tables"          s_seat_draw_seats
step seat-draw-button     "Deal for the button: high card, blinds by seat"      s_seat_draw_button
step seat-draw-undo       "Redraw seats, then Undo brings the draw back"        s_seat_draw_undo
step seat-draw-share      "Share as text: the share sheet opens and closes"     s_seat_draw_share
step seat-draw-back       "Back to the Tools list"                              s_seat_draw_back
step odds-empty           "Odds: empty table, first slot waiting"              s_odds_empty
step odds-card-picker     "Docked keypad: ranks, then suits that wait"          s_card_picker
step odds-hole-cards      "Keypad: AsKs vs QhQd, auto-advance to the flop"      s_hole_cards
step odds-flop            "Keypad: flop Js 10s 2c, keypad put away"             s_flop
step odds-results         "Exact odds: 56.06% / 43.94%, live"                   s_odds_results
step odds-run-it-out      "Run it out: deal turn and river, run twice, back"    s_odds_runout
step odds-card-clears     "Add the turn 7h: stale odds go, 36.36% comes"        s_odds_card_clears
step odds-4-players       "Add two players, fold Player 3"                      s_odds_more_players
step odds-reset-new-hand  "New hand: cards cleared, seats kept"                 s_odds_reset_new_hand
step odds-reset-table     "Clear table: back to two empty seats"               s_odds_reset_table
step chip-calc            "Chip set (S11): chips you own, Tools selected"       s_chip_calc
step chip-calc-stack      "Each player's stack adds up to 5,000; reserve check" s_chip_calc_stack
step chip-calc-colorup    "Color-up plan from the blind schedule"               s_chip_calc_colorup
step chip-calc-short      "Only 10 greens: short, by how many, and the fix"     s_chip_calc_short
step chip-calc-reset      "Reset to the starting set at once, with Undo"        s_chip_calc_reset
step chip-calc-settings   "Stack settings: keep 2 stacks back for rebuys"       s_chip_calc_settings
step shot-clock           "Shot clock: full and waiting, Tools selected"        s_shot_clock
step shot-clock-run       "Tap to start, pause holds, a card adds 30 s"         s_shot_clock_run
step shot-clock-reset     "Cards back with Undo; Reset: full and waiting"       s_shot_clock_reset
step dealers-choice       "Dealer's choice: the wheel of nine games"            s_dealers_choice
step dealers-spin         "Spin: a game and its rules, never twice running"     s_dealers_spin
step dealers-house-game   "Add a house game; every game's rules in a sheet"     s_dealers_house_game
step equity-quiz          "Equity quiz: two hands dealt, who's ahead?"          s_equity_quiz
step equity-quiz-answer   "Pick Hand A: exact odds add up to 100%, scored"      s_equity_quiz_answer
step equity-quiz-range    "Three hands, how often: a range picked, named"       s_equity_quiz_range
step back-to-tournament   "Back to Tournament tab, state intact"                s_back_to_tournament
step rail                 "720 dp wide: tabs move to a rail (PP-087)"           s_rail
step rail-tools           "Rail: Tools tab"                                     s_rail_tools
step rail-payouts         "Rail: Payouts tab, table adds up"                    s_rail_payouts
step rail-restored        "Phone width again: bottom bar back, tab kept"        s_rail_restored
step bank-pko             "PKO: Player 1 takes \$2.50, bounty up to \$7.50"     s_bank_pko
step app-alive            "App process still alive"                             s_app_alive

extra_step live-clock-pause "Pause from the shade: paused, Resume offered"      s_live_clock_pause
extra_step live-clock-open  "Open: paused on screen too; notification gone"     s_live_clock_open
extra_step live-clock-locked "Screen locked, clock running: the live clock shows" s_live_clock_locked

source "$DEVICE_SCRIPTS/steps-matrix.sh"   # opt-in steps for the device matrix (extra_step)
source "$DEVICE_SCRIPTS/steps-listing.sh"  # opt-in steps for the store listing's screenshots (listing.sh)
run_tour
