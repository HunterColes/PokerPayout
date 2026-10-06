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
#   PP_PROFILE_TURNS     "no" while the app's screens are locked to portrait (today: MainActivity
#                        is portrait in core's manifest); "yes" once they turn with the display
#                        on this profile (PP-088 for tablets, the Clock batch for the table view)
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
  [[ -f "$ROTATION_MARK" ]] || adb_ shell settings get system user_rotation | tr -d '\r' > "$ROTATION_MARK"
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
  ui assert-text "desc=Reset tournament" Bank Tools || return 1
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

# The display turned to 90 and to 270 degrees (accelerometer off): portrait-only screens (all of
# them today) stay upright, the tabs keep working, and the app neither crashes nor restarts.
s_rotate() {
  local pid; pid="$(adb_ shell pidof "$APP_ID" | tr -d '\r')"
  rotate_to land
  require_orientation "$(orientation_at 1)" || return 1
  tab Tools
  ui assert-text text=Odds "text=Hand ranks" || return 1
  require_tab_selected Tools
  require_user_rotation 1 || return 1
  rotate_to seascape
  require_orientation "$(orientation_at 3)" || return 1
  tab Tournament
  ui assert-text "text~=Tournament Configuration" || return 1
  require_tab_selected Tournament
  require_user_rotation 3 || return 1
  rotate_to "$PP_PROFILE_ROTATION"
  require_orientation "$(orientation_at "$PP_PROFILE_ROTATION")" || return 1
  local now; now="$(adb_ shell pidof "$APP_ID" | tr -d '\r')"
  echo "app pid $pid before, $now after"
  [[ -n "$now" && "$now" == "$pid" ]] || { echo "[ui] FAIL the app process changed: $pid -> ${now:-none}"; return 1; }
}

# Table view (PP-025) on the profile: the clock alone, landscape, whatever the clock is doing.
table_view_open() {
  tab Tournament
  ui tap "desc=Table view" --scroll-in scrollable    # the clock card can sit below the fold
  ui wait "desc=Exit table view"
}
table_view_close() {
  ui tap "desc=Exit table view"
  ui wait-gone "desc=Exit table view"
  sleep 1
}
s_table_view_land() {
  table_view_open || return 1
  ui assert-text "re=^(LEVEL [0-9]+|BREAK)$" "re=^(LEVEL|BREAK) TIME LEFT$" \
    "re=^(Start|Pause|Resume) timer$" "desc=Exit table view" || return 1
  require_landscape
}
# Leave it: back to the profile's orientation.
s_table_view_close() {
  table_view_close
  require_orientation "$(orientation_at "$PP_PROFILE_ROTATION")" || return 1
  ui assert-text "re=^(LEVEL [0-9]+|BREAK)$" "desc=Table view"
}
# Leave it (back to the profile's orientation), then the same with the display turned to 270:
# the clock must still come up landscape, and go back.
s_table_view_back() {
  table_view_close
  require_orientation "$(orientation_at "$PP_PROFILE_ROTATION")" || return 1
  rotate_to seascape
  table_view_open || return 1
  ui assert-text "re=^(LEVEL [0-9]+|BREAK)$" "desc=Exit table view" || return 1
  require_landscape || return 1
  table_view_close
  require_orientation "$(orientation_at 3)" || return 1
  rotate_to "$PP_PROFILE_ROTATION"
  require_orientation "$(orientation_at "$PP_PROFILE_ROTATION")" || return 1
  ui assert-text "re=^(LEVEL [0-9]+|BREAK)$" "desc=Table view"
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
  ui assert-text "desc=Reset bank" || return 1
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
  PP_UI_SCROLL=0 ui find "desc=Reset bank" --timeout 2 >/dev/null 2>&1 || tab Bank
  ui assert-text "desc=Reset bank" || return 1
  require_tab_selected Bank
}

extra_step profile            "Profile applied: window, density, font scale, rotation"  s_profile
extra_step nav-layout         "Tabs: bottom bar below 600 dp, rail from 600 dp"         s_nav_layout
extra_step rotate             "Display turned 90 and 270: portrait screens stay, work"  s_rotate
extra_step table-view-land    "Table view: the clock alone, landscape"                  s_table_view_land
extra_step table-view-back    "Leave table view; again from a display turned 270"       s_table_view_back
extra_step table-view-close   "Leave table view: the profile's orientation again"       s_table_view_close
extra_step ime                "Soft keyboard over a low name field: field stays clear"  s_ime
extra_step ime-done           "Keyboard put away and turned off again"                  s_ime_done
extra_step payouts-screen     "Payouts tab: the table adds up"                          s_rail_payouts
