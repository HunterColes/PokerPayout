#!/usr/bin/env bash
# One-command headless smoke tour of the whole app.
#
#   scripts/device/tour.sh                 # boot (if needed) + build + install + tour
#   scripts/device/tour.sh --no-build      # reuse the last built debug APK
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

BUILD=1; KEEP_GOING=0; STOP_AFTER=0
for arg in "$@"; do
  case "$arg" in
    --no-build) BUILD=0 ;;
    --keep-going) KEEP_GOING=1 ;;
    --stop) STOP_AFTER=1 ;;
    -h|--help) sed -n '2,15p' "$0"; exit 0 ;;
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
if (( BUILD )); then "$DEVICE_SCRIPTS/install.sh" | tee -a "$LOG"
else "$DEVICE_SCRIPTS/install.sh" --no-build | tee -a "$LOG"; fi
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
  if "$fn" >>"$LOG" 2>&1 && crash_check >>"$LOG" 2>&1; then
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
    echo "- App: $APP_ID $APP_VERSION (debug)"
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

# ------------------------------------------------------------------ steps
# Tournament ---------------------------------------------------------------
s_launch() {
  ui launch --clear
  ui assert-text "Poker Payout" "text~=Tournament" Bank Tools "Tournament Configuration" "Buy-in (\$)" "Start timer"
}
s_tournament_config() {
  ui set-text 'text=Buy-in ($)' --value 25
  ui set-text 'text=Bounty ($)' --value 5
  ui slide class=SeekBar --frac 0.22          # players slider 3..30 -> ~9
  ui assert-text text=25 text=5
  ui find 're=^(8|9|10)$'                     # player count label moved off the default 5
}
s_blinds_tab() {
  ui tap text=Blinds
  ui assert-text "Duration (Hours)" "Round Length (Min)" "Smallest Chip" "Starting Chips"
}
s_config_collapsed() {
  ui tap desc=Collapse
  ui wait desc=Expand
  ui wait-gone "text=Buy-in (\$)"
}
s_timer_running() {
  ui tap "desc=Start timer"
  ui assert-text "Level 1" "Level 2" "Tournament Locked" "Next blind level"
  ui find 're=^[0-9]+:[0-9]{2}:[0-9]{2}$'
}
s_timer_next_level() {
  ui tap "desc=Next blind level"
  ui assert-text "Level 2" "Previous blind level"
}
s_timer_paused() {
  ui tap 're=^[0-9]+:[0-9]{2}:[0-9]{2}$'
  ui assert-text "Resume timer"
}
s_tournament_reset_dialog() {
  ui tap "desc=Reset All Data"
  ui assert-text "Reset tournament?" Cancel Reset
}
s_tournament_reset_confirm() {
  ui tap text=Reset
  ui wait-gone "text=Reset tournament?"
  ui assert-text "Start timer"
}

# Bank ---------------------------------------------------------------------
s_bank() {
  ui tap text=Bank
  ui assert-text "text~=Bank Tracker" "text~=Pool Summary" "Player 1" "Buy-in pending" "Payout pending"
}
s_bank_rename() {
  ui set-text "text=Player 1" class=EditText --value Alice
  ui enter                                    # names are only committed on the IME action (Done/Enter)
  ui assert-text text=Alice
}
s_bank_buyin_dialog() {
  ui tap "desc=Buy-in pending"
  ui assert-text "text~=Alice has paid the buy-in" Okay Cancel
}
s_bank_buyin_done() {
  ui tap text=Okay
  ui assert-text "Buy-in completed"
}
s_bank_knockout_dialog() {
  ui tap "desc=Still in" --index 1
  ui assert-text Okay Cancel
}
s_bank_knockout_done() {
  ui tap text=Okay
  ui assert-text "Knocked out"
}
s_weights_editor() {
  ui tap "desc=Edit payout weights"
  ui assert-text "text~=Edit Payout Weights" "Higher weights = larger payouts." Save Cancel
}
s_weights_close() {
  ui tap text=Cancel
  ui wait-gone "text~=Edit Payout Weights"
}
s_pool_summary() {
  ui tap "desc=Pool Summary Details"
  ui assert-text "text~=Pool Summary Breakdown" "Prize Pool:" "Total Pool:" Close
}
s_bank_scrolled() {
  ui tap text=Close
  ui scroll down --times 2
  ui assert-text "Buy-in pending"
}

# Tools --------------------------------------------------------------------
s_tools() {
  ui tap text=Tools
  ui assert-text "text~=Tools" Odds Ranks "Chip Calculator" Settings
}
s_settings_dialog() {
  ui tap text=Settings
  ui assert-text "desc~=ute"                 # Mute / Unmute volume toggle
}
s_odds_empty() {
  ui back
  ui wait-gone "desc~=ute" --timeout 5
  ui tap text=Odds
  ui assert-text "text~=Poker Odds Calculator" "text~=Number of Players" "Calculate Odds" "text~=Community Cards (0/5)" "Player 1" "Player 2"
}
s_card_picker() {
  ui tap "desc=Add Card"
  ui assert-text "Select a Card" Cancel
}
pick() { # rank suit-symbol; scrolls the picker grid when the card is below the fold.
  # No wait-gone needed: the next dump only sees the main window once the dialog closes.
  # `clickable` pins the match to one card; without it a half-scrolled grid can match the
  # grid itself (rank of one card + suit of another).
  ui tap "has=$1|$2" clickable --scroll-in scrollable
}
s_hole_cards() {
  pick A "♠"                                  # picker already open for Player 1
  ui tap "desc=Add Card"; pick K "♠"
  ui tap "desc=Add Card"; pick Q "♥"
  ui tap "desc=Add Card"; pick Q "♦"
  ui assert-text "text~=Community Cards (0/5)"
}
s_flop() {
  ui tap "desc=Add Card"; pick J "♠"
  ui tap "desc=Add Card"; pick T "♠"
  ui tap "desc=Add Card"; pick 2 "♣"
  ui assert-text "text~=Community Cards (3/5)" Flop Turn River
}
# Centre x of the node whose text is exactly $2, from `ui find` output in $1.
x_of() { sed -n "s/^text='$2' .* @\([0-9]*\),[0-9]*\$/\1/p" <<<"$1" | head -1; }
s_odds_results() {
  ui tap "Calculate Odds"
  # AsKs vs QhQd on JsTs2c is enumerated exactly: Player 1 wins 555 and Player 2 435 of the
  # 990 turn-and-river runouts, with no ties. Before v1.2.0 the kicker-order bug (PP-011)
  # showed about 49.25 / 50.75 here, so anything else is a regression.
  ui wait "text=56.06%" --timeout 30 || return 1
  local found p1 p2 w1 w2
  found="$(ui find 're=^(Player 1|Player 2|56\.06%|43\.94%)$')"
  p1="$(x_of "$found" "Player 1")"; p2="$(x_of "$found" "Player 2")"
  w1="$(x_of "$found" "56.06%")";   w2="$(x_of "$found" "43.94%")"
  echo "odds columns: Player 1 x=$p1 shows 56.06% at x=$w1; Player 2 x=$p2 shows 43.94% at x=$w2"
  [[ -n "$p1" && -n "$p2" && -n "$w1" && -n "$w2" ]] || { echo "[ui] FAIL expected 56.06% / 43.94% under Player 1 / Player 2"; return 1; }
  (( p1 < p2 && w1 < w2 )) || { echo "[ui] FAIL 56.06% / 43.94% are not under Player 1 / Player 2"; return 1; }
}
s_odds_card_clears() {
  ui tap "desc=Add Card"; pick 9 "♥"         # the turn: any input change drops the old odds
  ui assert-text "text~=Community Cards (4/5)" "Calculate Odds" || return 1
  ui wait-gone 're=^[0-9]+\.[0-9]{2}%$' --timeout 5
}
s_odds_more_players() {
  ui slide class=SeekBar --frac 0.3            # 2..10 -> 4 players
  ui assert-text "text~=Number of Players: 4"
}
s_odds_reset_dialog() {
  ui tap desc=Reset
  ui assert-text "Reset odds calculator?" Cancel Reset
}
s_odds_reset_done() {
  ui tap text=Reset
  ui assert-text "text~=Community Cards (0/5)"
}
s_hand_ranks() {
  ui back
  ui tap text=Ranks
  ui assert-text "text~=Poker Hand Rankings" "text~=Royal Flush" "text~=High Card"
}
s_chip_calc() {
  ui back
  ui tap "text=Chip Calculator"
  ui assert-text "text~=Chip Calculator" Generate
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
  ui tap text=Tournament --index 0
  ui assert-text "text~=Tournament Configuration" "Start timer"
}
s_app_alive() {
  local pid; pid="$(adb_ shell pidof "$APP_ID" | tr -d '\r')"
  [[ -n "$pid" ]] || { echo "app process is not running"; return 1; }
  echo "app pid $pid"
}

step launch               "Fresh launch (data cleared), Tournament tab"         s_launch
step tournament-config    "Enter buy-in 25, bounty 5, players ~9"               s_tournament_config
step blinds-tab           "Blinds tab of the configuration panel"               s_blinds_tab
step config-collapsed     "Collapse configuration (timer not started)"          s_config_collapsed
step timer-running        "Start the timer; blind levels appear"                s_timer_running
step timer-next-level     "Skip to the next blind level"                        s_timer_next_level
step timer-paused         "Pause the timer"                                     s_timer_paused
step tournament-reset     "Reset confirmation dialog"                           s_tournament_reset_dialog
step tournament-reset-ok  "Confirm reset; timer cleared"                        s_tournament_reset_confirm
step bank                 "Bank tab, default players"                           s_bank
step bank-rename          "Rename Player 1 to Alice"                            s_bank_rename
step bank-buyin-dialog    "Buy-in action dialog"                                s_bank_buyin_dialog
step bank-buyin-done      "Buy-in confirmed"                                    s_bank_buyin_done
step bank-knockout-dialog "Knock-out dialog for Player 2"                       s_bank_knockout_dialog
step bank-knockout-done   "Knock-out confirmed"                                 s_bank_knockout_done
step weights-editor       "Payout weights editor dialog"                        s_weights_editor
step weights-closed       "Cancel weights editor"                               s_weights_close
step pool-summary         "Pool summary breakdown dialog"                       s_pool_summary
step bank-scrolled        "Bank list scrolled down"                             s_bank_scrolled
step tools                "Tools home grid"                                     s_tools
step settings-dialog      "Settings tile (volume dialog)"                       s_settings_dialog
step odds-empty           "Odds calculator, empty state"                        s_odds_empty
step odds-card-picker     "Card picker dialog"                                  s_card_picker
step odds-hole-cards      "Hole cards: AsKs vs QhQd"                            s_hole_cards
step odds-flop            "Flop: Js Ts 2c"                                      s_flop
step odds-results         "Exact odds: 56.06% / 43.94%"                          s_odds_results
step odds-card-clears     "Add the turn; stale odds disappear"                  s_odds_card_clears
step odds-4-players       "Raise player count to 4 (empty seats)"               s_odds_more_players
step odds-reset-dialog    "Odds reset dialog"                                   s_odds_reset_dialog
step odds-reset-done      "Odds reset confirmed"                                s_odds_reset_done
step hand-ranks           "Hand rankings (all 10 hands fit on one screen)"      s_hand_ranks
step chip-calc            "Chip calculator"                                     s_chip_calc
step chip-calc-generated  "Generate chip breakdown"                             s_chip_calc_generated
step chip-calc-advanced   "Advanced settings expanded"                          s_chip_calc_advanced
step chip-calc-scrolled   "Chip calculator scrolled"                            s_chip_calc_scrolled
step back-to-tournament   "Back to Tournament tab, state intact"                s_back_to_tournament
step app-alive            "App process still alive"                             s_app_alive
finish
