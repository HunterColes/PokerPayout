# shellcheck shell=bash
# Opt-in tour steps for the device matrix (scripts/device/matrix.sh): the profile itself, the tab
# layout for the window's width, rotation, the table view from a turned display, and the soft
# keyboard. tour.sh sources this file and runs these steps only when --only or --steps-file names
# them, so the plain tour is unchanged. They use tour.sh's helpers (ui, tab, tab_positions,
# require_tab_selected, screen_size, require_landscape) and its display marks, so whatever they
# change on the display is put back however the tour ends.
#
# The profile's knobs (matrix.sh exports them; the defaults describe the bare emulator):
#   PP_PROFILE           the profile's name                                  (default: default)
#   PP_PROFILE_FONT      the font scale it set                               (default: 1.0)
#   PP_PROFILE_ROTATION  the user rotation it runs in: 0 upright, 1 = 90, 3 = 270 (default: 0)
#   PP_PROFILE_TURNS     "yes" where every tab turns with the display (foldables and tablets,
#                        sw >= 600 dp, PP-088); "no" on phones, where only the Tournament tab's
#                        running clock turns (into the table view, PP-079; see s_rotate_clock)
PP_PROFILE="${PP_PROFILE:-default}"
PP_PROFILE_FONT="${PP_PROFILE_FONT:-1.0}"
PP_PROFILE_ROTATION="${PP_PROFILE_ROTATION:-0}"
PP_PROFILE_TURNS="${PP_PROFILE_TURNS:-no}"

# ------------------------------------------------------------------ display helpers
# (Steps run with errexit and pipefail: these read all of adb's output and return 0, so neither a
# reader that stops early nor "no match" fails a step by accident.)
# The display's rotation now, 0-3 (0 = upright, 1 = turned 90 degrees, 3 = 270).
display_rotation() {
  adb_ shell dumpsys window displays | tr -d '\r' | awk 'match($0, /mCurrentRotation=ROTATION_[0-9]+/) && !n {
    print substr($0, RSTART + 26, RLENGTH - 26) / 90; n = 1 }'
}
# The first "InsetsSource ... type=<statusBars|navigationBars|ime> frame=..." line of the window dump.
inset_line() {
  adb_ shell dumpsys window | tr -d '\r' | awk -v t="type=$1 frame=" 'index($0, t) && !n { print; n = 1 }'
}
# "top bottom" of an inset's frame
inset_span() { inset_line "$1" | sed -n "s/.*type=$1 frame=\[[0-9-]*,\([0-9-]*\)\]\[[0-9-]*,\([0-9-]*\)\].*/\1 \2/p"; }
display_density() { adb_ shell wm density | tr -d '\r' | awk '{print $NF}' | tail -1; }
# rotate_to port|land|seascape|upside-down|0-3: the user rotation, with the accelerometer off. A
# screen locked to portrait keeps the display upright, which is what portrait-only screens must
# do; a screen that turns follows it. The old rotation comes back however the tour ends.
rotate_to() {
  local r
  case "$1" in
    port) r=0 ;; land) r=1 ;; upside-down) r=2 ;; seascape) r=3 ;; [0-3]) r="$1" ;;
    *) echo "[ui] ERROR rotate_to: unknown rotation $1"; return 2 ;;
  esac
  [[ -f "$ROTATION_MARK" ]] || printf '%s %s\n' "$(adb_ shell settings get system accelerometer_rotation | tr -d '\r')" \
    "$(adb_ shell settings get system user_rotation | tr -d '\r')" > "$ROTATION_MARK"
  adb_ shell settings put system accelerometer_rotation 0
  adb_ shell settings put system user_rotation "$r"
  # Every uiautomator dump freezes the rotation back at the display's (0 under a portrait-only
  # screen): ui.py puts r back after each dump for the rest of this step
  if [[ "$r" == 0 ]]; then unset PP_UI_HOLD_ROTATION; else export PP_UI_HOLD_ROTATION="$r"; fi
  # The display either turns to r or, under a portrait-only screen, stays upright: wait up to 2 s
  # for the first, then let a rotation (and the activity's recreation) settle.
  local i; for i in 1 2 3 4; do sleep 0.5; [[ "$(display_rotation)" == "$r" ]] && break; done
  sleep 0.5
  echo "user rotation $r, display rotation $(display_rotation)"
}
# The user rotation is still $1 (the display was really turned while the step worked)
require_user_rotation() {
  local got; got="$(adb_ shell settings get system user_rotation | tr -d '\r')"
  [[ "$got" == "$1" ]] || { echo "[ui] FAIL the user rotation is $got, not $1"; return 1; }
}
# The orientation of what's on screen, from a screenshot: port or land.
screen_orientation() { local s; s="$(screen_size)"; [[ "${s%x*}" -gt "${s#*x}" ]] && echo land || echo port; }
require_orientation() { # port|land
  local got; got="$(screen_orientation)"; echo "screen $(screen_size): $got"
  [[ "$got" == "$1" ]] || { echo "[ui] FAIL expected the screen $1, it is $got"; return 1; }
}
# What the app's screens show with the display turned to $1 (0-3) on this profile.
orientation_at() { [[ "$PP_PROFILE_TURNS" == yes && ( "$1" == 1 || "$1" == 3 ) ]] && echo land || echo port; }

# ------------------------------------------------------------------ steps
# The profile took: the app's window is the overridden screen (so it relaid out), at the profile's
# font scale and rotation. Writes display.env for the layout checks (layout_check.py).
s_profile() {
  ui assert-text "desc=Reset tournament" "Start clock" || return 1
  local size density font rot status nav
  size="$(adb_ shell wm size | tr -d '\r' | sed -n 's/.*size: //p' | tail -1)"
  density="$(display_density)"
  font="$(adb_ shell settings get system font_scale | tr -d '\r')"
  rot="$(display_rotation)"
  status="$(inset_span statusBars | awk '{print $2 - $1}')"
  nav="$(inset_span navigationBars | awk '{print $2 - $1}')"
  printf 'profile=%s\nwidth=%s\nheight=%s\ndensity=%s\nfont_scale=%s\nrotation=%s\nstatus_bar=%s\nnav_bar=%s\n' \
    "$PP_PROFILE" "${size%x*}" "${size#*x}" "$density" "$font" "$rot" "${status:-0}" "${nav:-0}" > "$OUT/display.env"
  cat "$OUT/display.env"
  python3 - "$PP_UI_LAST_XML" "$size" "$density" "$rot" "$font" "$PP_PROFILE_FONT" <<'PY' || return 1
import re, sys, xml.etree.ElementTree as ET
dump, size, density, rot, font, want_font = sys.argv[1:]
w, h = (int(v) for v in size.split("x"))
if rot in ("1", "3"):
    w, h = h, w
root = next(ET.parse(dump).getroot().iter("node"))
b = [int(v) for v in re.findall(r"-?\d+", root.get("bounds"))]
dp = 160.0 / int(density)
print("app window %dx%d px = %.0f x %.0f dp, font scale %s" % (b[2] - b[0], b[3] - b[1], (b[2] - b[0]) * dp, (b[3] - b[1]) * dp, font))
if (b[2] - b[0], b[3] - b[1]) != (w, h):
    sys.exit("[ui] FAIL the app's window is %dx%d, the screen %dx%d: it did not relayout" % (b[2] - b[0], b[3] - b[1], w, h))
if abs(float(font) - float(want_font)) > 0.01:
    sys.exit("[ui] FAIL font scale is %s, the profile wants %s" % (font, want_font))
PY
  require_user_rotation "$PP_PROFILE_ROTATION" || return 1   # still turned after the dumps
  require_orientation "$(orientation_at "$PP_PROFILE_ROTATION")"
}

# The four tabs for the window's width: a bar along the bottom below 600 dp, a rail down the left
# edge from 600 dp (PP-087), on the profile as it is (no density trick, unlike `rail`).
s_nav_layout() {
  ui assert-text Bank Payouts Tools || return 1
  local tabs; tabs="$(tab_positions "$PP_UI_LAST_XML")"; echo "$tabs"
  python3 - "$PP_UI_LAST_XML" "$(display_density)" "$tabs" <<'PY'
import re, sys, xml.etree.ElementTree as ET
dump, density, tabs = sys.argv[1], int(sys.argv[2]), sys.argv[3]
root = next(ET.parse(dump).getroot().iter("node"))
b = [int(v) for v in re.findall(r"-?\d+", root.get("bounds"))]
width_dp = (b[2] - b[0]) * 160.0 / density
rows = [line.split() for line in tabs.strip().splitlines()]
if len(rows) != 4:
    sys.exit("[ui] FAIL expected four tabs, found %d" % len(rows))
xs = [int(r[1]) for r in rows]; ys = [int(r[2]) for r in rows]
rail = max(xs) - min(xs) <= 10 and ys == sorted(ys)
bar = max(ys) - min(ys) <= 10 and xs == sorted(xs)
want = "rail" if width_dp >= 600 else "bar"
print("window %.0f dp wide: want the %s; tabs are %s" % (width_dp, want, "a rail" if rail else "a bar" if bar else "neither"))
if (want == "rail" and not (rail and min(xs) < (b[2] - b[0]) * 0.2)) or (want == "bar" and not (bar and min(ys) > (b[3] - b[1]) * 0.8)):
    sys.exit("[ui] FAIL the tabs are not a %s for a %.0f dp window: %s" % (want, width_dp, rows))
PY
}

# The rotation rules (M3, PP-079/PP-088), by the window's smallest width:
#   phone (sw < 600 dp): every tab is portrait, except the Tournament tab once a clock exists,
#     which follows the display: turned, the clock is the table view (S3); upright, the clock (S2).
#   foldable / tablet (sw >= 600 dp): every tab turns with the display; turned, the clock stays
#     the clock (two panes from 840 dp).
#   every size: the table-view button (⤢) shows the table view in landscape until ✕.
smallest_width_dp() {
  local size density; size="$(adb_ shell wm size | tr -d '\r' | sed -n 's/.*size: //p' | tail -1)"
  density="$(display_density)"
  local w=${size%x*} h=${size#*x}; (( w < h )) || w=$h
  echo $(( w * 160 / density ))
}
wide_screen() { (( $(smallest_width_dp) >= 600 )); }

# The display turned to 90 and to 270 degrees (accelerometer off) on tabs other than a running
# Tournament: on a phone they stay upright and work; on a wide screen they turn with it and work.
# The step leaves the display turned (its screenshot is the Bank at 270); rotate-upright turns it
# back and checks the app neither crashed nor restarted.
s_rotate() {
  adb_ shell pidof "$APP_ID" | tr -d '\r' > "$OUT/.rotate-pid"
  tab Tools
  ui assert-text text=Odds || return 1
  rotate_to land
  require_orientation "$(orientation_at 1)" || return 1
  ui assert-text text=Odds "text=Hand ranks" || return 1
  require_tab_selected Tools
  require_user_rotation 1 || return 1
  rotate_to seascape
  require_orientation "$(orientation_at 3)" || return 1
  tab Bank
  ui assert-text "desc=More options" "$BANK_SUBTITLE" || return 1
  require_tab_selected Bank
  require_user_rotation 3
}
s_rotate_upright() {
  rotate_to "$PP_PROFILE_ROTATION"
  require_orientation "$(orientation_at "$PP_PROFILE_ROTATION")" || return 1
  ui assert-text "desc=More options" || return 1
  local pid now; pid="$(cat "$OUT/.rotate-pid" 2>/dev/null)"; now="$(adb_ shell pidof "$APP_ID" | tr -d '\r')"
  echo "app pid $pid before, $now after"
  [[ -n "$now" && "$now" == "$pid" ]] || { echo "[ui] FAIL the app process changed: $pid -> ${now:-none}"; return 1; }
}

# What the clock or the table view says about the level: "Level 5 · time left" or, on a break,
# "Break · back at Level 5" (drawn in capitals: uiautomator reads "LEVEL 5 · TIME LEFT").
CLOCK_LINE='re=(?i)^(level [0-9]+ · (time left|overtime)|break · back at level [0-9]+)$'
TIMER_BUTTON='re=^(Start|Pause|Resume) timer$'
# The Bank's subtitle: "5 players · $100 to collect" before the start, "5 of 5 left · $35
# collected" while a clock runs, "Finished · Alice wins · ..." at the end
BANK_SUBTITLE='re=( players · |[0-9]+ of [0-9]+ left · |^Finished · )'
clock_line() { # the clock's level line from the last dump
  python3 - "$PP_UI_LAST_XML" <<'PY'
import re, sys, xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).iter("node"):
    t = n.get("text") or ""
    if re.fullmatch(r"(?i)level \d+ · (time left|overtime)|break · back at level \d+", t):
        print(t)
        break
PY
}

# The running clock turned with the display (PP-079): on a phone, landscape is the table view
# and upright the clock, on the same level; on a wide screen the clock itself turns, tabs and all.
# rotate-clock turns it to 90 and stays there (its screenshot is the turned clock);
# rotate-clock-back turns it to 270, then upright, then back to the profile's rotation.
check_turned_clock() { # $1 = the level line it must still show
  if wide_screen; then
    ui wait "desc=Table view" || return 1
    require_orientation land || return 1
    ui assert-text "$CLOCK_LINE" "$TIMER_BUTTON" || return 1
    [[ -n "$(tab_positions "$PP_UI_LAST_XML")" ]] || { echo "[ui] FAIL no tabs beside the turned clock"; return 1; }
  else
    ui wait "desc=Exit table view" || return 1
    require_orientation land || return 1
    ui assert-text "$CLOCK_LINE" "$TIMER_BUTTON" || return 1
  fi
  [[ "$(clock_line)" == "$1" ]] || { echo "[ui] FAIL the level changed: $1 -> $(clock_line)"; return 1; }
}
clock_at_top() { # the clock's page scrolled to its top, the level line in the last dump
  ui scroll up --times 4 >/dev/null
  ui assert "$CLOCK_LINE" >/dev/null
}
s_rotate_clock() {
  tab Tournament
  clock_at_top || return 1
  ui assert-text "$CLOCK_LINE" "desc=Table view" || return 1
  clock_line > "$OUT/.clock-line"; echo "clock: $(cat "$OUT/.clock-line")"
  rotate_to land
  check_turned_clock "$(cat "$OUT/.clock-line")"
}
s_rotate_clock_back() {
  local before; before="$(cat "$OUT/.clock-line" 2>/dev/null)"
  rotate_to seascape
  check_turned_clock "$before" || return 1
  rotate_to port
  if ! wide_screen; then ui wait-gone "desc=Exit table view" || return 1; fi
  require_orientation port || return 1
  ui assert-text "$CLOCK_LINE" "desc=Table view" || return 1
  [[ "$(clock_line)" == "$before" ]] || { echo "[ui] FAIL the level changed: $before -> $(clock_line)"; return 1; }
  rotate_to "$PP_PROFILE_ROTATION"
  require_orientation "$(orientation_at "$PP_PROFILE_ROTATION")"
}

# Table view (PP-025, S3) from its button, on the profile as it is: the clock alone, full screen
# (no tabs), landscape on every size (the button forces it until ✕).
table_view_open() {
  tab Tournament
  ui tap "desc=Table view"
  ui wait "desc=Exit table view"
}
table_view_close() {
  ui tap "desc=Exit table view"
  ui wait-gone "desc=Exit table view"
  sleep 1
}
s_table_view_land() {
  table_view_open || return 1
  ui assert-text "$CLOCK_LINE" "$TIMER_BUTTON" "desc=Exit table view" || return 1
  [[ -z "$(tab_positions "$PP_UI_LAST_XML")" ]] || { echo "[ui] FAIL the table view shows the tabs"; return 1; }
  require_landscape
}
# Leave it: the clock, in the profile's orientation.
s_table_view_close() {
  table_view_close
  require_orientation "$(orientation_at "$PP_PROFILE_ROTATION")" || return 1
  ui assert-text "$CLOCK_LINE" "desc=Table view"
}
# Leave it, then with the display turned to 270: the button again on a wide screen; on a phone the
# turned clock already is the table view. Then back.
s_table_view_back() {
  s_table_view_close || return 1
  rotate_to seascape
  if wide_screen; then
    table_view_open || return 1
    require_orientation land || return 1
    table_view_close
  else
    ui wait "desc=Exit table view" || return 1
    require_landscape || return 1
  fi
  rotate_to "$PP_PROFILE_ROTATION"
  if ! wide_screen; then ui wait-gone "desc=Exit table view" || return 1; fi
  require_orientation "$(orientation_at "$PP_PROFILE_ROTATION")" || return 1
  ui assert-text "$CLOCK_LINE" "desc=Table view"
}
# Close the setup panel opened over the running clock (setup-panel); the clock runs on.
s_setup_close() {
  ui tap "desc=Close setup"
  ui wait-gone "text~=Setup · clock still running" || return 1
  ui assert-text "$CLOCK_LINE" "desc~=Opens setup" "Pause timer"
}

# Process death (PP-093): the app in the background, killed as low memory does (`am kill`), then
# opened again from the launcher. The running clock must come back on the same level with its time
# still counting (no restart, no lost minutes), and every Bank record intact.
bank_records() { # the Bank's cells, one per line, from a dump: "Alice, buy-in, paid", ...
  python3 - "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
seen = set()
for n in ET.parse(sys.argv[1]).iter("node"):
    d = n.get("content-desc") or ""
    if re.match(r"^[^,]+, (buy-in|rebuy|add-on|out|paid out|champion)\b", d):
        seen.add(d)
print("\n".join(sorted(seen)))
PY
}
bank_page() { # $1 = file: the whole Bank list as one dump (the screen, if the page can't be made)
  python3 "$DEVICE_SCRIPTS/ui.py" page --out "$1" >/dev/null 2>&1 || ui dump --out "$1" >/dev/null
}
s_process_death() {
  tab Bank
  ui assert-text "desc=More options" || return 1
  bank_page "$OUT/.bank-before.xml"
  local records; records="$(bank_records "$OUT/.bank-before.xml")"
  [[ -n "$records" ]] || { echo "[ui] FAIL no Bank records to keep"; return 1; }
  tab Tournament
  ui assert-text "$CLOCK_LINE" "Pause timer" || return 1
  clock_at_top || return 1
  local line secs t0; line="$(clock_line)"; secs="$(hero_seconds)"; t0=$(date +%s)
  [[ -n "$line" && "$secs" -gt 0 ]] || { echo "[ui] FAIL can't read the clock before the kill"; return 1; }
  echo "before: $line, ${secs}s left; Bank: $(tr '\n' ';' <<<"$records")"
  local pid; pid="$(adb_ shell pidof "$APP_ID" | tr -d '\r')"
  ui home
  sleep 2
  adb_ shell am kill "$APP_ID"
  local i
  for i in 1 2 3 4 5 6 7 8 9 10; do [[ -z "$(adb_ shell pidof "$APP_ID" | tr -d '\r')" ]] && break; sleep 0.5; done
  [[ -z "$(adb_ shell pidof "$APP_ID" | tr -d '\r')" ]] || { echo "[ui] FAIL am kill left the app running"; return 1; }
  echo "process $pid killed in the background"
  sleep 3
  # Back from the launcher, as a user would: monkey brings the task back (ui.py launch would
  # force-stop it first, which is not a process death)
  adb_ shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  ui wait "$CLOCK_LINE" --timeout 20 || return 1
  ui assert-text "Pause timer" || return 1
  clock_at_top || return 1
  local now_line now_secs elapsed
  now_line="$(clock_line)"; now_secs="$(hero_seconds)"; elapsed=$(( $(date +%s) - t0 ))
  echo "after: $now_line, ${now_secs}s left, ${elapsed}s later; new pid $(adb_ shell pidof "$APP_ID" | tr -d '\r')"
  [[ "$now_line" == "$line" ]] || { echo "[ui] FAIL the clock came back on '$now_line', not '$line'"; return 1; }
  local off=$(( secs - now_secs - elapsed ))
  (( off >= -4 && off <= 4 )) || { echo "[ui] FAIL ${now_secs}s left, expected about $(( secs - elapsed ))s (${off}s off)"; return 1; }
  if ui find "text=Start clock" >/dev/null 2>&1; then echo "[ui] FAIL the clock restarted"; return 1; fi
  tab Bank
  ui assert-text "desc=More options" || return 1
  bank_page "$OUT/.bank-after.xml"
  local after; after="$(bank_records "$OUT/.bank-after.xml")"
  if [[ "$after" != "$records" ]]; then
    echo "[ui] FAIL the Bank records changed across the process death"
    diff <(echo "$records") <(echo "$after") || true
    return 1
  fi
  echo "Bank records intact: $(wc -l <<<"$after") cells"
  ui assert-text "desc=More options"
}

# The soft keyboard over a name field low on the Bank list. The AVD has a hardware keyboard, so
# Gboard shows only its toolbar strip (about 126 px); the check is that the app resizes for it:
# the focused field stays clear of the keyboard and of the status bar.
ime_frame() { # "top bottom" of the keyboard while it shows, else nothing
  inset_line ime | awk '/visible=true/' \
    | sed -n 's/.*type=ime frame=\[[0-9]*,\([0-9]*\)\]\[[0-9]*,\([0-9]*\)\].*/\1 \2/p'
}
s_ime() {
  tab Bank
  ui assert-text "desc=More options" "$BANK_SUBTITLE" || return 1
  local xy; xy="$(python3 - "$PP_UI_LAST_XML" <<'PY'
import re, sys, xml.etree.ElementTree as ET
nodes = list(ET.parse(sys.argv[1]).getroot().iter("node"))
H = int(re.findall(r"-?\d+", nodes[0].get("bounds"))[3])
fields = []
for n in nodes:
    b = [int(v) for v in re.findall(r"-?\d+", n.get("bounds", ""))]
    if "EditText" in n.get("class", "") and len(b) == 4 and b[3] - b[1] > 20 and b[3] < H * 0.95:
        fields.append(b)
if fields:
    b = max(fields, key=lambda b: b[3])          # the lowest name field on screen
    print((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)
PY
)"
  [[ -n "$xy" ]] || { echo "[ui] FAIL no name field on the Bank screen"; return 1; }
  touch "$IME_MARK"
  adb_ shell settings put secure show_ime_with_hard_keyboard 1
  ui tap-xy $xy
  local ime="" i
  for i in $(seq 1 12); do ime="$(ime_frame)"; [[ -n "$ime" ]] && break; sleep 0.5; done
  [[ -n "$ime" ]] || { echo "[ui] FAIL the soft keyboard did not come up"; return 1; }
  sleep 1
  ui find focused class=EditText > /dev/null || return 1
  python3 - "$PP_UI_LAST_XML" "${ime% *}" <<'PY'
import re, sys, xml.etree.ElementTree as ET
dump, ime_top = sys.argv[1], int(sys.argv[2])
f = next(n for n in ET.parse(dump).getroot().iter("node") if n.get("focused") == "true" and "EditText" in n.get("class", ""))
b = [int(v) for v in re.findall(r"-?\d+", f.get("bounds"))]
print("focused field %r at %s, keyboard from y=%d" % (f.get("text"), b, ime_top))
if b[3] > ime_top:
    sys.exit("[ui] FAIL the keyboard (from y=%d) covers the focused field %s" % (ime_top, b))
if b[3] - b[1] < 20:
    sys.exit("[ui] FAIL the focused field is squeezed to %d px" % (b[3] - b[1]))
PY
}
# Put the keyboard away (Escape, else Back) and turn it off again. Back can also leave the Bank
# tab (B16), so come back to it if it did.
s_ime_done() {
  local key i
  for key in KEYCODE_ESCAPE KEYCODE_BACK; do
    [[ -n "$(ime_frame)" ]] || break
    ui key "$key"
    for i in 1 2 3 4 5 6; do sleep 0.5; [[ -z "$(ime_frame)" ]] && break; done
  done
  adb_ shell settings put secure show_ime_with_hard_keyboard 0
  rm -f "$IME_MARK"
  [[ -z "$(ime_frame)" ]] || { echo "[ui] FAIL the soft keyboard is still up"; return 1; }
  ui find "$BANK_SUBTITLE" --timeout 2 >/dev/null 2>&1 || tab Bank
  ui assert-text "desc=More options" "$BANK_SUBTITLE" || return 1
  require_tab_selected Bank
}

extra_step profile            "Profile applied: window, density, font scale, rotation"  s_profile
extra_step nav-layout         "Tabs: bottom bar below 600 dp, rail from 600 dp"         s_nav_layout
extra_step rotate             "Other tabs turned 90, 270: phones upright, wide turn"    s_rotate
extra_step rotate-upright     "Upright again: same process, nothing restarted"          s_rotate_upright
extra_step rotate-clock       "Running clock turned 90: phone table view, wide clock"   s_rotate_clock
extra_step rotate-clock-back  "Turned 270, then upright: the same level throughout"     s_rotate_clock_back
extra_step table-view-land    "Table view button: the clock alone, full screen"         s_table_view_land
extra_step table-view-back    "Leave table view; again from a display turned 270"       s_table_view_back
extra_step table-view-close   "Leave table view: the profile's orientation again"       s_table_view_close
extra_step setup-close        "Close the setup panel: the clock runs on"                s_setup_close
extra_step process-death      "Killed in the background: clock and Bank come back"      s_process_death
extra_step ime                "Soft keyboard over a low name field: field stays clear"  s_ime
extra_step ime-done           "Keyboard put away and turned off again"                  s_ime_done
extra_step payouts-screen     "Payouts tab: the table adds up"                          s_rail_payouts
