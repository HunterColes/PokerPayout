#!/usr/bin/env bash
# One-command headless smoke tour of the whole app.
#
#   scripts/device/tour.sh                 # boot (if needed) + build + install + tour
#   scripts/device/tour.sh --no-build      # reuse the last built APK
#   scripts/device/tour.sh --release       # tour the minified (R8) release build instead of debug
#   scripts/device/tour.sh --keep-going    # don't stop at the first failing step
#   scripts/device/tour.sh --stop          # shut the emulator down afterwards
#
# Every step: run actions/assertions with ui.py, then save <NN-name>.png (screenshot)
# and <NN-name>.xml (uiautomator tree). The tour FAILS if an expected text is missing,
# if logcat shows a FATAL EXCEPTION / ANR for the app, or if the app process dies.
#
# Output: build/device-reports/<timestamp>/{index.md, *.png, *.xml, logcat.txt, tour.log}
#         (also symlinked as build/device-reports/latest). Exit code 0 == all steps passed.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

BUILD=1; KEEP_GOING=0; STOP_AFTER=0; VARIANT=debug
for arg in "$@"; do
  case "$arg" in
    --no-build) BUILD=0 ;;
    --release) VARIANT=release ;;
    --keep-going) KEEP_GOING=1 ;;
    --stop) STOP_AFTER=1 ;;
    -h|--help) sed -n '2,16p' "$0"; exit 0 ;;
    *) die "unknown option: $arg" ;;
  esac
done

T0=$(date +%s)
REPORT_ROOT="${PP_REPORT_ROOT:-$REPO_ROOT/build/device-reports}"
OUT="$REPORT_ROOT/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
ln -sfn "$OUT" "$REPORT_ROOT/latest"
LOG="$OUT/tour.log"
: > "$LOG"

# ui.py saves every dump to $PP_UI_LAST_XML; if a step ends with an assertion, that
# dump already shows the final state and capture() reuses it (saves ~2s per step).
export PP_UI_LAST_XML="$OUT/.last-dump.xml"
export PP_UI_TRACE=1        # per-dump timings in tour.log
LAST_UI=""
ui() { LAST_UI="$1"; python3 "$DEVICE_SCRIPTS/ui.py" "$@"; }

# ------------------------------------------------------------------ setup
t=$(date +%s)
"$DEVICE_SCRIPTS/boot.sh" | tee -a "$LOG"
BOOT_SECS=$(since "$t")

t=$(date +%s)
if (( BUILD )); then "$DEVICE_SCRIPTS/install.sh" "--$VARIANT" | tee -a "$LOG"
else "$DEVICE_SCRIPTS/install.sh" "--$VARIANT" --no-build | tee -a "$LOG"; fi
INSTALL_SECS=$(since "$t")

adb_ logcat -b all -c >/dev/null 2>&1 || true
APP_VERSION="$(adb_ shell dumpsys package "$APP_ID" | sed -n 's/.*versionName=//p' | head -1 | tr -d '\r')"

# ------------------------------------------------------------------ step runner
STEP_NO=0; PASSED=0; FAILED=0; ROWS=()
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
  case "$LAST_UI" in
    assert|assert-text|wait|wait-gone|find)
      [[ -f "$PP_UI_LAST_XML" ]] && mv "$PP_UI_LAST_XML" "$OUT/$1.xml" && return 0 ;;
  esac
  ui dump --out "$OUT/$1.xml" >/dev/null 2>>"$LOG" || true
}
# step <name> <description> <function>
step() {
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
    detail="$(tail -n +"$((log_mark + 1))" "$LOG" | grep -E "\[ui\] (FAIL|ERROR)|crash|ANR|not running" | head -2 | tr '\n' ' ' | cut -c1-300)"
  fi
  capture "$id"
  secs=$(( ($(date +%s%N) - t1) / 100000000 )); secs="$((secs / 10)).$((secs % 10))"
  ROWS+=("| $STEP_NO | \`$name\` | $desc | **$status** | ${secs}s | ![]($id.png) | ${detail//|/\\|} |")
  printf '[tour] %-4s %s (%ss)%s\n' "$status" "$id" "$secs" "${detail:+ -- $detail}"
  if [[ "$status" == FAIL && "$KEEP_GOING" != 1 ]]; then finish; fi
}

finish() {
  restore_display
  adb_ logcat -d -v threadtime > "$OUT/logcat.txt" 2>/dev/null || true
  adb_ logcat -d -b crash > "$OUT/logcat-crash.txt" 2>/dev/null || true
  local fatal anr
  fatal=$(grep -c "FATAL EXCEPTION" "$OUT/logcat.txt" || true)
  anr=$(grep -c "ANR in $APP_ID" "$OUT/logcat.txt" || true)
  local total_secs; total_secs=$(since "$T0")
  local verdict=PASS
  (( FAILED > 0 || fatal > 0 || anr > 0 )) && verdict=FAIL
  {
    echo "# Device tour report: $verdict"
    echo
    echo "- When: $(date -Is)"
    echo "- App: $APP_ID $APP_VERSION ($VARIANT)"
    echo "- Device: $ANDROID_SERIAL, AVD \`$PP_AVD\`, Android $(adb_ shell getprop ro.build.version.release | tr -d '\r') (API $(adb_ shell getprop ro.build.version.sdk | tr -d '\r')), $(adb_ shell wm size | awk '{print $NF}' | tr -d '\r') @ $(adb_ shell wm density | awk '{print $NF}' | tr -d '\r')dpi"
    echo "- Steps: $PASSED passed, $FAILED failed, of $STEP_NO run"
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

# The rail step (s_rail) lowers the display density so the phone reports a tablet-wide window. The
# emulator is shared and keeps the setting across reboots, so put it back however a step ends.
DENSITY_MARK="$OUT/.density-changed"
restore_display() {
  if [[ -f "$DENSITY_MARK" ]]; then
    adb_ shell wm density reset >/dev/null 2>&1 || true
    rm -f "$DENSITY_MARK"
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
  local x y; read -r _ x y < <(tab_positions "$OUT/.tabs.xml" | grep "^$1 ")
  [[ -n "$x" ]] || { echo "[ui] FAIL no $1 tab on screen"; return 1; }
  ui tap-xy "$x" "$y"
}

# ------------------------------------------------------------------ steps
# Tournament ---------------------------------------------------------------
s_launch() {
  ui launch --clear
  # One header per screen (M2): the tab's name and its reset button; four tabs below. The clock
  # card sits below the configuration; its controls are below the fold until it collapses.
  ui assert-text "text=Tournament" "desc=Reset tournament" Bank Payouts Tools "Tournament Configuration" \
    "Buy-in (\$)" "LEVEL 1" READY || return 1
  require_tab_selected Tournament
}
s_tournament_config() {
  # One keystroke at a time, with recomposition in between: v1.1.12 moved the cursor in front of
  # the '.' after every change and turned "12.50" into "120.5" (B10).
  ui set-text 'text=Buy-in ($)' --value ""
  for key in 1 2 . 5 0; do ui type "$key"; done
  ui assert-text text=12.50 || return 1
  ui set-text 'text=Bounty ($)' --value 5
  ui slide class=SeekBar --frac 0.26          # players slider 3..30 -> ~10
  ui assert-text text=12.50 text=5            # leaving the field shows the saved amount
  ui find 're=^(9|10|11)$'                    # player count label moved off the default 5
}

# The Payouts tab's table (S6): the rows must add up to the prize pool in the top bar ("$125 prize
# pool · 3 places paid") to the cent, and to "Adds up to $125" under them; one row per place paid;
# no lower place paying more; with a unit (cents) every place below 1st a whole number of units.
# A row is one node for TalkBack ("1st, Still playing, $63, 50%") or, failing that, the texts on
# one line.
check_payout_table() { # $1 = ui dump, $2 = rounding unit in cents (default 100)
  python3 - "$1" "${2:-100}" <<'PY'
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
  ui wait "text~=Tournament Configuration"
}
# The blind setup and the clock (PP-015/020/025/026/051). Default setup: 3 h of 20-minute
# rounds from a 50 chip to 5,000 = 9 levels, 50/100 to 5,000/10,000.
s_blinds_tab() {
  ui tap text=Blinds
  ui assert-text "Duration (Hours)" "Round Length (Min)" "Smallest Chip" "Starting Chips" Breaks \
    "Big-blind ante" "text~=9 levels, 50 / 100 to 5,000 / 10,000"
}
s_smallest_chip() {
  # PP-051: a menu of real chip values, each with its chip colour, not a free-entry field. The menu
  # is its own window, so a dump shows only its visible items (the list scrolls on past 250).
  ui tap "has=Smallest Chip|50" clickable
  ui assert-text text=1 text=5 text=10 text=25 text=50 text=100 text=250 || return 1
  ui tap text=25
  ui wait-gone text=250
  ui assert-text "has=Smallest Chip|25" "text~=9 levels, 25 / 50 to 5,000 / 10,000"
}
s_invalid_setup() {
  # PP-020: an invalid setup says why and offers the nearest valid round length
  ui set-text "text=Round Length (Min)" --value 25
  ui enter
  ui assert-text "text~=Can't build blinds" "text~=doesn't divide into 25-minute rounds" "Use 20-min rounds (9 levels)"
}
s_invalid_setup_fixed() {
  ui tap "text=Use 20-min rounds (9 levels)"
  ui wait-gone "text~=Can't build blinds"
  ui assert-text "has=Round Length (Min)|20" "text~=9 levels, 25 / 50 to 5,000 / 10,000"
}
s_breaks() {
  # PP-026: a break every 4 levels with a note; the verdict counts the breaks and the new end
  ui tap "has=Breaks|Off" clickable
  ui tap "text=Every 4 levels"
  ui assert-text "Break (Min)" "Break note" "text~=9 levels, 25 / 50 to 5,000 / 10,000 · 2 breaks · ends at 3:20" || return 1
  ui set-text "text=Break note" --value "Last rebuy"
  ui enter                                    # leaves the note; it used to click Reset instead
  ui assert-text "text=Last rebuy" "has=Breaks|Every 4 levels" "has=Break (Min)|10"
}
s_config_collapsed() {
  ui scroll up --times 4
  ui tap desc=Collapse
  ui wait desc=Expand
  ui wait-gone "text=Buy-in (\$)"
  # Before the start the clock already shows level 1, labelled, with its full time
  ui assert-text "LEVEL 1" READY "LEVEL TIME LEFT" text=20:00 BLINDS "text=25 / 50" "text=Next: 50 / 100" "Start timer"
}
s_timer_running() {
  ui tap "desc=Start timer"
  ui assert-text "LEVEL 1" "LEVEL TIME LEFT" "text=Next: 50 / 100" "Tournament Locked" "Pause timer" \
    "text=Tournament:" "Next blind level" || return 1
  ui find 're=^1[0-9]:[0-9]{2}$'            # the level countdown, under 20:00
}
s_timer_next_level() {
  ui tap "desc=Next blind level"
  ui assert-text "LEVEL 2" "text=50 / 100" "text~=Next: " "Previous blind level"
}
s_timer_break() {
  ui tap "desc=Next blind level"; ui wait "LEVEL 3"
  ui tap "desc=Next blind level"; ui wait "LEVEL 4"
  # The last level before the break says what comes next, and the schedule (below the clock)
  # shows the break with its note
  ui assert-text "text=Next: Break · 10 min" || return 1
  ui scroll-to "text=☕ Break · 10 min" --max 3
  ui assert-text "text=☕ Break · 10 min" "text~=Last rebuy" || return 1
  ui scroll up --times 3
  ui tap "desc=Next blind level"
  ui assert-text BREAK "BREAK TIME LEFT" "text=Last rebuy" "text~=Next: Level 5 · " || return 1
  ui find 're=^(10:00|9:[0-9]{2})$'
}
s_timer_paused() {
  # PP-046: the play button sits below the digits instead of over them
  ui tap "desc=Pause timer"
  ui assert-text "Resume timer" PAUSED "BREAK TIME LEFT"
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
  # PP-025: a full-screen landscape clock, here on the paused break
  ui tap "desc=Table view"
  ui wait "desc=Exit table view"
  ui assert-text BREAK "BREAK TIME LEFT" "text=Last rebuy" "Resume timer" PAUSED || return 1
  require_landscape
}
s_table_view_level() {
  # The table view's own controls: resume, then on to level 5 with the clock running
  ui tap "desc=Resume timer"
  ui wait "desc=Pause timer"
  ui tap "desc=Next blind level"
  ui assert-text "LEVEL 5" "LEVEL TIME LEFT" BLINDS "text~=Next: " "Pause timer" "text=Tournament:" || return 1
  require_landscape
}
s_table_view_exit() {
  ui tap "desc=Exit table view"
  ui wait-gone "desc=Exit table view"
  sleep 2
  local size; size="$(screen_size)"; echo "screen $size"
  [[ "${size%x*}" -lt "${size#*x}" ]] || { echo "[ui] FAIL not back to portrait ($size)"; return 1; }
  ui assert-text "LEVEL 5" "Pause timer"
}
s_tournament_reset_dialog() {
  ui tap "desc=Reset tournament"
  ui assert-text "Reset tournament?" Cancel Reset
}
s_tournament_reset_confirm() {
  ui tap text=Reset
  ui wait-gone "text=Reset tournament?"
  # The configuration opens again, so the clock card is below it: scroll to it, then back up
  ui assert-text "LEVEL 1" READY || return 1
  ui scroll-to "Start timer" --max 4
  ui assert-text "LEVEL TIME LEFT" text=20:00 "text=50 / 100" "Start timer" || return 1
  ui scroll up --times 4
}
s_rebuy_amount() {
  ui tap text=Player
  ui set-text 'text=Rebuy ($)' --value 10
  ui enter
  ui assert-text text=10
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
  ui wait "text~=Tournament Configuration"
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
  check_placement_badge "$PP_UI_LAST_XML"
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
# column. Setup's "Rebuys until" field is M3's; until it lands, the tour writes the preference
# (debug builds only: run-as needs a debuggable app).
set_rebuy_cutoff() { # $1 = level
  adb_ shell am force-stop "$APP_ID"
  adb_ shell "run-as $APP_ID sed -i -e '/rebuy_until_level/d' -e 's#</map>#<int name=\"rebuy_until_level\" value=\"$1\" /></map>#' shared_prefs/tournament_prefs.xml"
  adb_ shell "run-as $APP_ID cat shared_prefs/tournament_prefs.xml" | grep rebuy_until_level
  ui launch
}
s_bank_cutoff() {
  if [[ "$VARIANT" == release ]]; then
    # No run-as on a release build: check the column is open with no cutoff instead
    echo "release build: the cutoff needs run-as to set; the debug tour covers it"
    tab Bank
    ui assert-text "desc=Alice, rebuy, 1 taken" Rebuy || return 1
    ui find "desc=Rebuy, closed" && { echo "[ui] FAIL the Rebuy column is closed with no cutoff"; return 1; }
    return 0
  fi
  set_rebuy_cutoff 1
  ui wait "desc=Reset tournament"
  ui scroll-to "desc=Start timer" --max 4
  ui tap "desc=Start timer"
  ui wait "desc=Pause timer"
  ui tap "desc=Next blind level"
  ui assert-text "LEVEL 2" || return 1
  tab Bank
  ui assert-text "desc=Rebuy, closed" "desc=Alice, rebuy, closed after level 1, 1 taken"
}
s_bank_rebuy_blocked() {
  if [[ "$VARIANT" == release ]]; then echo "release build: see bank-cutoff"; return 0; fi
  # A tap does nothing after the cutoff; the note under the list says why
  ui tap "desc=Alice, rebuy, closed after level 1, 1 taken"
  sleep 1
  ui assert-text "desc=Alice, rebuy, closed after level 1, 1 taken" || return 1
  ui find "text~=Rebuy for Alice" && { echo "[ui] FAIL a rebuy was recorded after the cutoff"; return 1; }
  ui scroll-to "text~=Rebuys closed after level 1" --max 3
  ui assert-text "text~=Rebuys closed after level 1. Taken ones stay filled"
}
s_bank_cutoff_reset() {
  # Reset the tournament for the steps after: clock back to level 1, cutoff and rebuys cleared
  tab Tournament
  ui tap "desc=Reset tournament"
  ui tap text=Reset
  ui wait-gone "text=Reset tournament?"
  ui assert-text "LEVEL 1" READY
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
  ui assert-text "text~=Tournament Configuration" "desc=Reset tournament" || return 1
  require_tab_selected Tournament
}

# Clearing the Rebuy amount to retype it must not wipe recorded rebuys (PP-014).
s_rebuy_retype() {
  tab Tournament
  ui set-text 'text=Rebuy ($)' --value ""
  for key in 1 5; do ui type "$key"; done
  tab Bank                                    # leave the field by switching tabs
  bank_has_alices_rebuy
}
s_rebuy_zero_prompt() {
  tab Tournament
  ui set-text 'text=Rebuy ($)' --value ""
  ui enter                                    # leave it empty: asks before clearing anything
  ui assert-text "Turn rebuys off?" Keep "text~=Clear rebuy"
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
  ui assert-text "Everything works offline" text=Odds "text=Chip set" "text=Hand ranks" text=Sound \
    "desc=Chime volume" "Test chime" || return 1
  require_tab_selected Tools
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
  # A tool's screen keeps Tools selected (B16) and has a back arrow. It also holds everything the
  # removed (unreachable) "Rules" popup showed.
  ui tap "text=Hand ranks"
  ui assert-text "Best to worst" desc=Back "text~=Royal Flush" "text~=High Card" || return 1
  require_tab_selected Tools
}
s_odds_empty() {
  # The Settings volume dialog may still be open: the dump only sees a dialog's window, so if the
  # Odds tile isn't there, close the dialog first.
  ui find text=Odds >/dev/null 2>&1 || ui back
  ui tap text=Odds
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
  found="$(ui find 're=(Player 1, options|Player 2, options|win 56\.06 · tie 0\.00|win 43\.94 · tie 0\.00)')"
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
  ui back
  ui tap "text=Chip set"
  ui assert-text "text=Chip set" desc=Back "desc=Reset chip set" Generate || return 1
  require_tab_selected Tools
}
s_chip_calc_generated() {
  ui tap Generate
  ui assert-text "text~=Chip Breakdown" "Total Chips" "Total Value" 're=^× [0-9]+$' || return 1
  check_chip_totals "$PP_UI_LAST_XML"
}
# The stats row against the breakdown rows in a UI dump: Total Chips must be non-zero and
# equal the sum of the "× N" counts (v1.1.12 showed 0), and Denominations must equal the
# number of rows, so a row below the fold can't drop out of the sum unnoticed.
check_chip_totals() {
  python3 - "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
nodes = set()
for n in ET.parse(sys.argv[1]).iter("node"):
    b = [int(v) for v in re.findall(r"-?\d+", n.get("bounds", ""))]
    label = n.get("text") or n.get("content-desc") or ""
    if label and len(b) == 4:
        nodes.add((label, (b[0] + b[2]) // 2, b[1], b[3]))
def stat(name):  # the number shown directly above a stat's label
    label = next((n for n in nodes if n[0] == name), None)
    above = [n for n in nodes if label and re.fullmatch(r"\d+", n[0]) and 0 <= label[2] - n[3] < 200]
    if not above:
        sys.exit("[ui] FAIL no value shown for %r" % name)
    return int(min(above, key=lambda n: abs(n[1] - label[1]))[0])
total, denoms = stat("Total Chips"), stat("Denominations")
counts = [int(n[0][2:]) for n in sorted(nodes, key=lambda n: n[2]) if re.fullmatch(r"× \d+", n[0])]
print("chip stats: Total Chips %d, Denominations %d, row counts %s (sum %d)" % (total, denoms, counts, sum(counts)))
if len(counts) != denoms:
    sys.exit("[ui] FAIL %d breakdown rows on screen but Denominations is %d" % (len(counts), denoms))
if total <= 0 or total != sum(counts):
    sys.exit("[ui] FAIL Total Chips is %d but the breakdown adds up to %d" % (total, sum(counts)))
PY
}
s_chip_calc_advanced() {
  ui tap "desc=Expand advanced settings"
  ui assert-text "Smallest Chip" "Starting Chips" Denoms "Distribution Curve"
}
s_chip_calc_scrolled() {
  ui scroll down --times 2
}
s_back_to_tournament() {
  tab Tournament
  ui assert-text "text~=Tournament Configuration" "LEVEL 1" READY
}

# Rail (PP-087) --------------------------------------------------------------
# A tablet-wide window on the phone emulator: at density 240 the 1080 px screen is 720 dp wide,
# so the four tabs move from the bottom bar to a rail down the left edge. The activity is
# recreated on the change; the screen must come back where it was.
s_rail() {
  touch "$DENSITY_MARK"
  adb_ shell wm density 240
  sleep 3
  ui assert-text "text~=Tournament Configuration" "LEVEL 1" || return 1
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
  adb_ shell wm density reset
  rm -f "$DENSITY_MARK"
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
s_app_alive() {
  local pid; pid="$(adb_ shell pidof "$APP_ID" | tr -d '\r')"
  [[ -n "$pid" ]] || { echo "app process is not running"; return 1; }
  echo "app pid $pid"
}

step launch               "Fresh launch (data cleared), Tournament tab"         s_launch
step tournament-config    "Type buy-in 12.50 key by key, bounty 5, players ~10" s_tournament_config
step payouts-tab          "Payouts tab (S6): rows add up to the prize pool"     s_payouts_tab
step payouts-preset       "Top-heavy: 1st gets what its preview said"           s_payouts_preset
step payouts-rounded      "Round to \$5: lower places in \$5, adds up"           s_payouts_rounded
step payouts-editor       "Payout structure sheet opens and closes"             s_payouts_editor
step payouts-share        "Share as text: the share sheet has the payouts"      s_payouts_share
step blinds-tab           "Blinds tab: setup, breaks, ante, verdict"            s_blinds_tab
step smallest-chip        "Smallest chip picker: pick 25"                       s_smallest_chip
step invalid-setup        "25-minute rounds: reason and nearest fix shown"      s_invalid_setup
step invalid-setup-fixed  "Apply the fix: 20-minute rounds"                     s_invalid_setup_fixed
step breaks               "Breaks every 4 levels, note 'Last rebuy'"            s_breaks
step config-collapsed     "Collapse configuration; clock shows level 1 ready"   s_config_collapsed
step timer-running        "Start: level countdown, Next, tournament line"       s_timer_running
step timer-next-level     "Skip to level 2"                                     s_timer_next_level
step timer-break          "Skip to the first break"                             s_timer_break
step timer-paused         "Pause on the break"                                  s_timer_paused
step table-view           "Table view: full-screen landscape clock, on break"   s_table_view
step table-view-level     "Table view: resume, next level, clock running"       s_table_view_level
step table-view-exit      "Leave table view: back to portrait"                  s_table_view_exit
step tournament-reset     "Reset confirmation dialog"                           s_tournament_reset_dialog
step tournament-reset-ok  "Confirm reset; timer cleared"                        s_tournament_reset_confirm
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
step tools                "Tools tab: tool list and Sound (S7)"                 s_tools
step sound-off            "Sound off: switch off, volume and chime rest"        s_sound_off
step sound-on             "Sound back on; test chime"                           s_sound_on
step hand-ranks           "Hand ranks: back arrow, Tools stays selected"        s_hand_ranks
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
step chip-calc            "Chip set (the chip calculator), Tools selected"      s_chip_calc
step chip-calc-generated  "Generate chip breakdown"                             s_chip_calc_generated
step chip-calc-advanced   "Advanced settings expanded"                          s_chip_calc_advanced
step chip-calc-scrolled   "Chip calculator scrolled"                            s_chip_calc_scrolled
step back-to-tournament   "Back to Tournament tab, state intact"                s_back_to_tournament
step rail                 "720 dp wide: tabs move to a rail (PP-087)"           s_rail
step rail-tools           "Rail: Tools tab"                                     s_rail_tools
step rail-payouts         "Rail: Payouts tab, table adds up"                    s_rail_payouts
step rail-restored        "Phone width again: bottom bar back, tab kept"        s_rail_restored
step app-alive            "App process still alive"                             s_app_alive
finish
