# Testing Poker Payout

Everything here runs headless on a Linux workstation: no phone, no emulator window, no
sound. It is written so that a person *or* an AI agent can run it unattended.

| Tier | Command | Needs | Typical time |
|------|---------|-------|--------------|
| JVM unit tests | `./gradlew testDebugUnitTest` | JDK 21 | ~15 s warm (394 tests) |
| Device smoke tour (screenshots + UI dumps + logcat) | `scripts/device/tour.sh` | emulator (auto-booted) | ~6 min incl. build |
| Instrumented tests | `./gradlew connectedDebugAndroidTest` | running emulator | compiles; there are 0 instrumented tests (see below) |
| JVM screenshot goldens + layout checks (Roborazzi, section 9) | part of `./gradlew testDebugUnitTest`; re-record with `./gradlew recordRoborazziDebug` | JDK 21 | ~20 s for core's 120 goldens and 48 matrix checks |
| Device matrix: the real app on 10 screen sizes, fonts and rotations (section 10) | `scripts/device/matrix.sh` | emulator (auto-booted) | focused set (4 profiles) not yet timed on 1.3.4; 41 min for 8 profiles on 1.3.0 |
| Property-based tests: seeded random cases that shrink (section 6) | part of `./gradlew testDebugUnitTest` | JDK 21 | ~3 s for core's 26; ~25 s for the three process-death properties |
| Accessibility checks: TalkBack names, WCAG contrast (section 9) | part of every screen test's layout checks | JDK 21 | a few seconds over the whole matrix |
| Monkey: seeded chaos on the emulator (section 11) | `scripts/device/monkey.sh`; on GitHub `monkey.yml` | emulator (auto-booted) | ~23 min for 3 seeds of 4,000 events on GitHub, incl. boot and build |
| Mutation testing, PIT on core's maths (section 12) | `gh workflow run mutation.yml` | GitHub | ~16 min on GitHub |

## 1. Prerequisites

* Linux with KVM (`$ANDROID_HOME/emulator/emulator -accel-check` should say KVM is usable;
  your user must be in the `kvm` group).
* Android SDK at `$ANDROID_HOME` (default `~/Android/Sdk`) with `emulator/`,
  `platform-tools/` and the system image
  `system-images/android-34/google_apis_playstore/x86_64`. No `cmdline-tools`
  (avdmanager/sdkmanager) are needed: `boot.sh` writes the AVD files itself, and Gradle
  downloads missing `platforms;android-34` on first build.
* JDK 21 (all modules use `jvmToolchain(21)`; bytecode still targets 17). Commits from
  before the switch to JDK 21 need a JDK 17 toolchain that Gradle can find, for example one
  under `~/.gradle/jdks/` with a `.ready` marker file.
* `python3` (stdlib only) and `bash`.

The scripts set `ANDROID_HOME`/`ANDROID_SERIAL` themselves. Nothing has to be on `PATH`.

## 2. Quick start

```bash
scripts/device/tour.sh              # boot if needed, build, install, tour, write the report
scripts/device/tour.sh --no-build   # reuse the last debug APK (fast iteration on the tour)
scripts/device/tour.sh --release    # tour the minified (R8) release APK instead of debug
scripts/device/tour.sh --stop       # also shut the emulator down at the end
scripts/device/stop.sh              # shut the emulator down
```

stdout is a short summary, one line per step. The tour exits non-zero if any step fails:

```
[device] already running: emulator-5580 (14, API 34)
[device] built :app:assembleDebug in 38s
[device] installed PokerPayout-v1.1.12-debug.apk (19M) in 1s
[tour] PASS 01-launch (7.6s)
...
[tour] PASS: 54/54 steps passed, 0 fatal, 0 ANR, 392s total
[tour] report: build/device-reports/20261004-205652/index.md
```

## 3. Output layout

```
build/device-reports/<YYYYMMDD-HHMMSS>/     (build/device-reports/latest -> newest run)
  index.md            verdict, device, timings, one table row per step with its screenshot
  NN-<step>.png       1080x2400 screenshot after the step
  NN-<step>.xml       uiautomator UI tree after the step (text, content-desc, bounds, flags)
  logcat.txt          full logcat for the run (cleared at the start)
  logcat-crash.txt    crash buffer only
  tour.log            every ui.py action, assertion, and per-dump timing
build/device/                               (scratch state for the scripts)
  emulator.log  emulator.pid  settings.log  gradle-debug.log  install.log  shots/
```

To review a run, an agent opens `index.md`, then looks at each `NN-*.png` with its image
viewer (Claude Code's `Read` tool renders PNGs). For exact text, bounds, or
enabled/disabled state, it reads the matching `.xml` file.

## 4. The scripts (`scripts/device/`)

All the scripts are bash with `set -euo pipefail`. They share `lib.sh`. Detailed logs go to
files and stdout stays short.

| Script | What it does |
|--------|--------------|
| `boot.sh [--cold\|--wipe]` | Starts the emulator headless (`-no-window -no-audio -no-boot-anim -gpu swiftshader_indirect`), waits for `sys.boot_completed` and a responsive package manager, then applies the deterministic settings below. Idempotent: if the emulator is already up, it only re-applies the settings. It creates the dedicated AVD `pokerpayout_test` (API 34, 1080x2400 @ 420 dpi, 2 GB RAM, 4 cores) on first use. Any other AVD selected with `PP_AVD` is started `-read-only`, so nobody else's AVD is ever changed. |
| `stop.sh [--force]` | `adb emu kill` (saves a quick-boot snapshot); `--force` sends SIGKILL instead. |
| `install.sh [--release] [--no-build] [--clear]` | `./gradlew :app:assembleDebug` (or `assembleRelease`), then `adb install -r`. If the signatures don't match, or a newer version is installed (another worktree's build), it uninstalls and installs again. |
| `shot.sh <name> [outdir] [--ui]` | `adb exec-out screencap -p` to `<outdir>/<name>.png` (default `build/device/shots`), checks the PNG signature, and also writes the UI dump when given `--ui`. |
| `ui.py` | Stdlib-only UI driver over `uiautomator dump` + `adb shell input` (see below). |
| `tour.sh [--release] [--no-build] [--keep-going] [--stop]` | The one-command smoke tour (section 5). `--release` passes `--release` to `install.sh`. `--only`, `--steps-file`, `--list`, `--out`, `--no-boot`, `--no-install` run some of the steps (section 5). |
| `matrix.sh [--full] [--profiles a,b] [--steps a,b] [--no-build] [--release] [--stop]` | The device matrix: the tour's steps once per screen profile, one report and contact sheet (section 10). |
| `steps-matrix.sh` | The matrix's opt-in tour steps (profile, tab layout, rotation, table view, keyboard) and the rotation helpers. `tour.sh` sources it. |
| `listing.sh [--release] [--no-build] [--stop] [--copy-from DIR]` | The store listing's phone screenshots: a home game played through with the tour, copied to `metadata/en-US/images/phoneScreenshots/` only if every step passed (docs/RELEASING.md, "Refreshing the store listing"). |
| `steps-listing.sh` | The listing's opt-in tour steps (`listing-*`). `tour.sh` sources it. |
| `layout_check.py <report dir>..` | Layout heuristics over a tour's dumps and screenshots: off-screen text, small or overlapping targets, cut text (section 10). Works on any tour report. |
| `matrix_report.py <matrix dir>` | Writes the matrix's `index.md` and `index.html`. |

Deterministic device settings applied by `boot.sh`:

* all three animation scales set to 0
* en-US locale (verified; changing it needs root), UTC timezone, 24 h clock
* show-touches and pointer-location off
* screen always on, keyguard dismissed
* rotation locked to portrait, font scale 1.0, and no display size or density override (one left
  by a killed device-matrix run is reset)
* spell checker and autofill off
* no Play Protect prompts on adb installs
* SystemUI demo mode: the clock is pinned to 12:00, battery shows 100 %, notifications are hidden
* Wi-Fi and mobile data off, because the app needs no network and this keeps Play services quiet. Set `PP_KEEP_NETWORK=1` to keep them on.

Environment knobs: `ANDROID_HOME`, `PP_AVD` (default `pokerpayout_test`), `PP_AVD_KEYBOARD=soft`
(the soft-keyboard twin `pokerpayout_test_softkb` instead, section 10), `PP_EMU_PORT`
(default `5580`, so the serial is `emulator-5580` and it never collides with a hand-launched
emulator on 5554), `PP_GPU`, `PP_BOOT_TIMEOUT` (default 300 s), `PP_TIMEZONE`,
`PP_REPORT_ROOT`.

Measured on this machine (24 cores, KVM): boot with no snapshot takes about 25 s,
`boot.sh` on an already running emulator takes about 1 s, a warm `assembleDebug` takes
about 40 s, and an install takes about 1 s.

### ui.py: driving the app by hand

Each command takes a fresh `uiautomator dump` (about 2 s; the dump waits about 1 s for the
UI to go idle) and polls until its target appears or `--timeout` runs out (default 10 s).
Compose exposes `Text` and `contentDescription` to uiautomator, so selectors use those.
No test tags are needed today.

```bash
D=scripts/device
$D/ui.py launch --clear                      # force-stop + clear data + start the launcher activity
$D/ui.py texts                               # every visible text/content-desc, top to bottom
$D/ui.py dump --out /tmp/screen.xml          # compact tree on stdout; raw XML to a file
$D/ui.py tap text=Tools                      # exact text
$D/ui.py tap "desc=Start timer"              # exact content-description
$D/ui.py tap Odds                            # bare token: text or desc ==, else contains
$D/ui.py tap "has=A|♠" --scroll-in scrollable   # smallest node containing both labels (a card)
$D/ui.py set-text 'text=Buy-in ($)' --value 25  # tap field, clear it, type
$D/ui.py enter                               # IME action (commits Bank player names)
$D/ui.py slide class=SeekBar --frac 0.3      # tap a slider at 30 % of its track
$D/ui.py scroll down --times 2 | scroll-to "text~=High Card"
$D/ui.py wait 're=^[0-9]+\.[0-9]{2}%$' --timeout 60
$D/ui.py assert-text "Calculate Odds" "text~=Community Cards (0/5)"
$D/ui.py wait-gone "Select a Card"
$D/ui.py back | home | key KEYCODE_TAB
$D/shot.sh my-screen /tmp/shots --ui
```

Selector tokens can be combined (`"text=Player 1" class=EditText`). The available tokens are
`text=`, `text~=` (contains, case-insensitive), `desc=`, `desc~=`, `id=`, `class=`, `re=`
(regex on text or desc), `has=A|B`, and the flags `clickable`, `scrollable`, `focused`,
`checked`, `enabled`. `--index N` picks the Nth match in reading order. When a lookup
fails, ui.py exits 1 and prints what *is* on screen, which is usually enough to fix the
selector.

If you later add `Modifier.testTag(...)`, you also need
`Modifier.semantics { testTagsAsResourceId = true }` near the root composable (gate it on
`ApplicationInfo.FLAG_DEBUGGABLE` so release builds don't change). The tags then show up as
`resource-id` and work with `id=`.

## 5. The smoke tour

`tour.sh` starts from cleared app data and visits every screen and tool. Each step is a
bash function of `ui.py` calls that ends in assertions. Tab steps find the four tabs by where
their labels line up (a row: the bottom bar; a column: the rail), so a screen title with the same
word ("Tournament") is never tapped by mistake, and they check which tab is selected. The steps
are:

1. **Tournament** (34 steps: five of them on the Payouts tab, two on presets and two on the live clock notification). The tab is one setup page (S1 v2) that folds into the clock on Start.
   * Launch: the ready ticket (LEVEL 1 · READY, 20:00) and Start. Type the buy-in 12.50 one key
     at a time (v1.1.12 turned it into 120.5), bounty 5, and five taps on the players stepper
     (10 players).
   * **Payouts tab (S6):** one row per place paid, adding up to the prize pool in the top bar
     and to "Adds up to" under the rows, to the cent, and never increasing down the table. Then
     the Top-heavy preset, whose "what 1st gets" preview must be what the 1st row pays; $5
     rounding on the page, where every place below 1st must be a whole $5; the payout structure
     sheet; and Share, which must open the system share sheet with the payouts in it.
   * **Blinds** (a section of the same page):
     * The smallest chip is a row of real chips (1, 5, 25, 100 ...), radio buttons named by
       colour and value; pick 25.
     * Type 25-minute levels and require the reason ("doesn't divide") and the "Use 20-min
       rounds (9 levels)" fix, then apply it.
     * Breaks every 4 levels with the note "Last rebuy"; the verdict must say "2 breaks, ends
       at 3:20". Enter in the note must leave the field, not open Reset.
   * The ticket before the start: LEVEL 1 · READY, 20:00, 25 / 50, next 50 / 100, 3:20 in all.
   * **Presets** (PP-032, 2 steps): the Presets row under the ticket opens the presets sheet; "Save
     as preset…" saves the setup as "Friday" ("Friday saved" with UNDO); the buy-in is then
     changed to 30. Loading Friday back from the list asks once ("Load Friday?", Keep mine or
     Load preset), since the setup no longer matches; Load brings the buy-in back to 12.50
     ("Friday loaded"). Each step waits for its snackbar to go, so the next one (and Start) isn't
     covered.
   * Start: the setup folds into the running clock (S2) with its strip. The first Start asks for
     notifications (Android 13+, PP-081); the tour allows it. -1 must take about a
     minute off the level's time left and +1 give it back (read from the digits).
   * **Live clock (PP-081)**, two steps: Home with the clock running must post the live clock
     notification ("Level 1", "Blinds 25 / 50 · Next 50 / 100", a counting-down chronometer, Pause
     and Open, public on the lock screen), read from `dumpsys notification --noredact`. Back in the
     app from the launcher, the clock must still be running and the notification gone (Pause and
     Open in the shade are opt-in steps, see "Live clock" below). Skip to level
     2; at level 4 require "Next · Break" and the break with its note in the schedule; skip into
     the break (S4: "Break · back at Level 5", Last rebuy); pause.
   * Table view: the table-view button forces a landscape screen on the paused break; resume
     there (the footer shows "10 of 10 left" and the pool); leave it and require portrait again.
     End break now starts level 5.
   * Rotation (PP-079): with auto-rotate off, `settings put system user_rotation 1` turns the
     emulator on its side: the clock must become the table view, landscape, still on level 5;
     `user_rotation 0` brings the clock back upright. The step puts the settings back however it
     ends.
   * One turn only (PP-094 #2): on its side, ✕ must show the clock upright, and turning the phone
     on its side again (`user_rotation 1`) must bring the table view back (the old rule kept the
     clock upright until you left the tab). With rotation locked, Android 14 itself sets the user
     rotation back to 0 when the app asks for the upright clock, so the tour can't hold the phone
     "still on its side" after ✕; the accelerometer path a real phone uses is unit tested.
   * The strip opens setup over the running clock, money and blinds locked; "Unlock to edit…"
     asks in a sheet first, then opens them; closing locks them again.
   * New tournament… (the menu) asks first; the reset unfolds setup at LEVEL 1 · READY, 20:00,
     50 / 100.
2. **Bank** (25 steps). Set the rebuy amount to $10. The labelled header (Player, Buy-in,
   Rebuy, Out, Paid) and the top bar. Rename Player 1 to Alice with no Enter, switch tabs and
   come back: the name must survive. A buy-in in one tap, with "Alice paid the buy-in" and UNDO
   on the snackbar; UNDO must take it back. A rebuy in one tap. Knock out Player 2 from the
   knockout sheet ("5TH PLACE"; Alice picked, applied with no second dialog): the 5th badge must
   sit in the Out column, clear of the name. Three more out with nobody credited: Alice is the
   champion; her pay-out sheet; Mark paid. Then **Settle up** (4 steps, 1.4; it took over the cash
   game's): only Alice paid in, so the summary offers "Settle up · 4 payments", and its sheet must
   list "Player 2 pays the bank $25", "Player 3 pays the bank $25", "Player 4 pays Player 5 $15" and
   "Player 4 pays the bank $10" (the Bank paid Alice $70 from $35, and keeps the $25 food). A row is
   one checkbox: tick and untick it. Share as text opens the share sheet with the settle-up. Ticking
   all four records everyone square ("Everyone is square: nobody owes anybody."; every buy-in paid,
   "all paid" in the subtitle, Settle up gone), and the top bar's Undo puts it back. The pool
   breakdown and payout structure sheets; scroll.
   Then clear the Rebuy amount and retype 15 by switching tabs: the recorded rebuy must survive
   (the keyboard is up when the tab is tapped). Leave the field empty: "Turn rebuys off?" must ask
   first, and Keep must bring back the $15 and the rebuy. Then the cutoff (PP-030): "rebuys until
   level 1" with the clock in level 2 must lock the Rebuy column, a tap must record nothing, and
   the note under the list must say why; then reset the tournament. The cutoff is set with setup's "Rebuys
   until" field, on both builds.
3. **Payouts tab** (3 steps). The finished night: Alice and Player 5 by name in their rows,
   adding up to the prize pool; the structure sheet; Back returns to Tournament (B16: Back no
   longer walks through every tab tapped).

   **History** (1 step, `history-save`, PP-037). Player 5 (2nd) is paid in the Bank too, so the
   night is over and settled: the Payouts tab offers "Save this night"; after one tap it says "Saved
   to History, in the Tools tab." and offers it no more. Tools > History then shows "1 night saved",
   "Most points all time: Alice" and her standings row read as one ("1st, Alice, 5 points, 1 night ·
   1 win": 5 players, 1st, so 5 points); the night's row ("5 players · Alice won") opens the night in
   full (Share, Alice and Player 5), Back closes it, and Back again returns to the Tools list. It
   scrolls to the night's row before tapping it, since a swipe flings less on GitHub's emulator.

4. **Tools** (7 steps). The tool list and the Sound section (S7); turn the sound off (the
   volume and Test chime rest) and on again, and play the test chime; Cue sounds (S18): the
   classic pack picked, a sound played, the minute's slot empty; Music (S18): no songs yet,
   Play with the clock and Quieter take; a song from the phone: the tour puts a WAV in
   Downloads, picks it in the system's file picker, and it must join the list, play (still
   playing three seconds later, no "File not found"), pause, and go again with Edit; Hand ranks
   (S12), with a back arrow, the Tools tab still selected, "1 in 30,940" for a royal flush and a
   kicker.
   Then **Seat draw** (S14, 6 steps): the Bank's players ("Alice, Player 2, ..."); seats per table
   down to half the players, so there are two tables; the draw must seat every player once, from
   seat 1 at each table, with the tables within one of each other; after the deal for the button
   every seat shows a card, the high card (ties on rank by suit, spades first) has the button, the
   next seats post the blinds (heads-up the button posts the small one) and "Button: NAME, seat N"
   names it; Redraw seats then Undo brings back the same draw; Share opens the system share sheet,
   and Back closes it with the draw still there; Back to the Tools list.
   Then **Backup** (5 steps): Tools > Backup (Tools still selected); Save backup… must open the
   system's file picker (DocumentsUI) and its Save must bring back "Backup saved"; Open a file…
   picks that file and the preview must name what it holds ("1 preset", "1 night in History",
   "Tournament setup and tonight's game"); Add to this phone must find nothing new; Replace this
   phone's data must start the app again on the first tab with the same game (Alice the Bank's
   champion, the night in History), ending on the Tools list.
5. **Odds.** Empty state, with the slot being filled on screen above the keypad without
   scrolling; card picker; AsKs vs QhQd; a JsTs2c flop (the picker scrolls to find 2c); calculate
   and require the exact answer, **56.06%** under Player 1 and **43.94%** under Player 2 (555 and
   435 of 990 runouts; v1.1.12 showed about 49.25 / 50.75 because of the kicker-order bug); add
   the 9h turn and require the old numbers to disappear; switch to 4 players; reset.
6. **Chip set** (S11, 6 steps; Tools still selected). The piles must add up to the Tournament's
   5,000 and agree with the "N chips a stack · K colours" line (v1.1.12 showed "Total Chips 0");
   the color-up plan from the clock's schedule; 10 greens in the colour sheet must give "Short
   10 green 25s for 5 players"; reset applies at once with Undo; the stack settings keep back
   the Tournament's estimate until the stepper is touched (PP-091 #3; here "no rebuys or
   add-ons"), then keep 2 stacks back as "your own", and the color-up plan counts 7 stacks in play.
7. Back to Tournament.
8. **Rail** (4 steps). `wm density 240` makes the phone's window 720 dp wide: the tabs must
   move to a rail down the left edge (PP-087), with the screen recreated where it was; Tools and
   Payouts on the rail; then `wm density reset` brings the bottom bar back with the tab kept. The
   tour resets the density however it ends, since the emulator keeps it across reboots. (On a
   device-matrix profile the step picks the density that makes that screen 720 dp wide, and goes
   back to the profile's own density.)
9. **Progressive knockout** (PP-035, 1 step, `bank-pko`). Last, because it clears the Bank the steps
   above leave (the bounty type is fixed while anyone is out): Reset bank… ("Clear 5 players"), then
   in setup a $5 bounty set to Progressive (its radio chip checked, and the line under it saying the
   other half goes onto the winner's bounty). In the Bank, Player 2's knockout sheet says half goes
   to whoever knocked Player 2 out; picking Player 1 shows "Player 1 takes $2.50 now · bounty up to
   $7.50"; confirming shows it on the snackbar and under Player 1's name ("bounty $7.50, 1
   knockout" to TalkBack). Each part scrolls to what it checks last, since a swipe flings less on
   GitHub's emulator.
10. Check that the app process is still alive.

Phones stay portrait (`AppOrientation` in `core`) except on the Tournament tab while a clock
exists, where the phone's own rotation turns the clock into the table view; ✕ there holds the clock
upright only until the phone is held upright again (PP-094 #2: `OnPhoneUpright` in `core` reads the
accelerometer with auto-rotate on, the user rotation with it off), so the next turn shows the table
view again; from 600 dp the app turns freely (PP-088). The tour checks both the table-view button and a turned emulator. The device matrix (section 10) turns the display on every profile.

Every command in a step counts: the step runs with `set -e`, so an assertion that fails in the
middle of a step fails it, not just the last one. After every step, the tour also fails it if
logcat's crash buffer has a `FATAL EXCEPTION` for the app or if an `ANR in
com.huntercoles.pokerpayout` appears. Another app's "isn't responding" dialog (on a loaded host,
usually Pixel Launcher right after a quick boot) is not the app's fault: `ui.py` taps Wait on it
and carries on. Use `--keep-going` to run all steps even after a failure.

### The live clock notification (PP-081) on the emulator

The live clock steps read the notification from `dumpsys notification --noredact` (the record the app
posted: its extras, actions and visibility), which needs no screenshot of the shade and works with
SystemUI's demo mode on. The tour then returns to the app from the launcher (`live-clock-back`):
the clock must still be running and the notification gone. `live-clock-flap` then leaves and comes
back ten times in one shell with no waits (the race the monkey found: a Hide that ended the service
while a Show it owed `startForeground` for was pending crashed the app), and checks the clock is
still running, the notification gone, and nothing crashed.

Pause and Open are tapped in the real shade (`cmd statusbar expand-notifications`, then
uiautomator) only in the opt-in steps `live-clock-pause` and `live-clock-open`: on the API 34
emulator the countdown in the open shade changes every second, so uiautomator never sees an idle
screen ("could not get idle state") and can't read the shade. Run them on an image where the shade
can be dumped: `--only live-clock-shade,live-clock-pause,live-clock-open` with the clock running.
The notification's Pause and Resume go through the same receiver the JVM tests drive.
`live_clock_record` and `wait_live_clock` in `tour.sh` poll for up to 15 s, so a slow host only makes
them wait. The first Start's permission question is answered by `answer_notifications_ask` in the
`start-fold` step; `live-clock-shade` also grants the permission with `pm grant`, in case the
question never came.

One more step is opt-in, since it turns the screen off: `--only live-clock-locked` (with the clock
running) locks the screen with `KEYCODE_SLEEP`, requires the notification (public) while locked,
then wakes and dismisses the keyguard and requires it gone with the app in front.

Not checked on the emulator: a vibration you can feel (the tests count the calls), the chime's
sound (the emulator runs without audio), the lock screen as drawn, and hours of Doze with the
screen off (the service holds a partial wake lock while the clock runs, so the CPU doesn't sleep
through a cue; the JVM tests run the driver on virtual time instead).

### Touring the release build

The release build is shrunk and obfuscated by R8, so a missing keep rule only shows up
there. After changing `app/proguard-rules.pro`, a dependency, or anything loaded by
reflection (Hilt, navigation routes, `@Parcelize`), tour the release APK:

```bash
flock /tmp/pokerpayout-emulator.lock scripts/device/tour.sh --release --stop
grep -cE "FATAL EXCEPTION|ClassNotFoundException|NoSuchMethodException|NoSuchFieldException" \
  build/device-reports/latest/logcat.txt                       # expect 0
```

Without `keystore.properties` (any worktree or clone) the release APK is signed with the
debug key, which is fine for testing. The release and debug builds have the same
application id and both use the debug key there, so they replace each other on the emulator.
If the signatures differ, `install.sh` uninstalls first, which clears the app's data.

To add a step, write `s_my_step() { ui tap ...; ui assert-text ...; }` and register it with
`step my-step "description" s_my_step`. End steps with an assertion: the tour then reuses
that UI dump for the step's `.xml`, which saves a second dump. Steps run in the order they are
registered. `extra_step` registers one that runs only when it is named (the device matrix's
steps in `steps-matrix.sh`, section 10).

### Running some of the steps

```bash
scripts/device/tour.sh --list                          # every step: name, tour or opt-in, what
scripts/device/tour.sh --no-build --only launch,payouts-tab,bank   # these, in this order
scripts/device/tour.sh --no-build --steps-file my-steps.txt        # one name per line, # comments
scripts/device/tour.sh --no-boot --no-install --out /tmp/run       # the matrix's way in
```

Steps build on each other (`payouts-tab` expects the buy-in typed in `tournament-config`), so a
subset must keep what its steps need; `--keep-going` shows how far it gets. Besides `index.md`,
every run writes `steps.tsv` (one line per step) and `summary.env` (the verdict and counts) for
scripts. A tour stopped with Ctrl-C or a signal still writes its report and puts the display
back (the rail's density, the matrix steps' rotation and keyboard).

## 6. JVM unit tests

```bash
./gradlew testDebugUnitTest -Dorg.gradle.jvmargs=-Xmx4g        # all modules
./gradlew :core:testDebugUnitTest --tests '*BlindStructureCalculator*'
./gradlew lintDebug detekt                                       # what CI runs after the tests
```

Reports: `<module>/build/reports/tests/testDebugUnitTest/index.html`. JUnit XML is in
`<module>/build/test-results/testDebugUnitTest/`. CI (`.github/workflows/ci.yml`) runs the
same three commands on every push and pull request and uploads these reports when it fails.

### What runs

Every module with tests applies the `de.mannodermaus.android-junit5` plugin, so its test task
runs on the JUnit Platform with two engines from the `common-test` bundle: Jupiter for JUnit 5
tests and Vintage for JUnit 4 tests. Robolectric tests (`@RunWith(RobolectricTestRunner::class)`)
are JUnit 4, so Vintage runs them. Each test runs once, in its own module.

Last measured (v1.3.0 batch B, money and clock integrated): **394 tests, 393 pass, 1 skipped,
0 fail**, about 15 s with compilation up to date. The slowest classes are `HandEvaluatorTest`
(all 133,784,560 seven-card hands, about 4 s) and `ChipDistributionOptimizerTest` (a 115,500-call
input sweep, about 2 s).

| Module | Tests | Skipped | What they cover |
|---|---|---|---|
| core | 139 | 0 | Payouts and settlement: presets, rounding (the rows always add up to the pool), standings, 2,000 seeded random tournaments that must conserve money exactly, bounties nobody claimed going to the champion. Money in cents and the money parser. Blind engine: 6,600-config property sweep (every accepted ladder in the 1.3x-2.0x band) plus exact ladders, setup advice whose every offered fix works, color-ups. Chip optimizer: reported crashes, typed failures, a 115,500-call input sweep and a brute-force oracle. FormatUtils. Bounty modes (PP-035, `BountyModesTest`, `MysteryBountyTest`): the progressive split and its rounding, exact PKO and mystery nights, every envelope deal adding up to players × bounty, the seeded draw, and 2,100 seeded random knockout orders (700 per mode) whose bounties add up to the bounty pool to the cent. History (PP-037, `core/domain/history`): `NightCodecTest` (every field round-trips; corrupt text, another format number, a bad date, a missing or out-of-range field, places that aren't 1 to N are skipped, never thrown; the saved keys), `NightStoreTest` (a fresh store reads them back, the latest night first; a deleted night put back keeps its id; an unreadable night is skipped, left in place, and its key never reused), `SeasonTest` (the points rule; totals over nights; players level on points share a rank; names matched after trimming and ignoring case, a different spelling a different player; a year against all time; the player of the year, and several when level), `NightCsvTest` (one row per player per night, oldest first; dollars with two decimals; quoting of commas, quotes and line breaks), `NightResultsTest` (nothing to save before a champion or while anyone owed is unpaid; then every player in finishing order with entry, rebuys, add-ons, prize, knockouts and bounties). `NavBarTest` keeps History under Tools. Settle up (1.4, `core/domain/settle`): `MinimumPaymentsTest` (the fewest payments: exactly what a brute force finds on 3,000 small cases, every zero-sum group found, never more than the greedy pass over 2,000 seeded random sets up to 16 parties, every party square to the cent and nobody paid more than owed), `SettleUpUseCaseTest` (balances from what the Bank recorded; over 500 seeded random nights the Bank always ends holding the food money), `BankPreferencesSettleTest` (the ticks' key; a 1.3.14 cash game left in storage is never read and gets in nobody's way). |
| bank-feature | 44 | 0 | BankViewModel money flows on real prefs: buy-ins, rebuys, knockouts, money conservation over 14 configs and 60 seeded random sessions, live totals when the Tournament settings change, purchases surviving a cleared-and-retyped amount, weights, reset. `BankBountyModesTest` (PP-035): progressive and mystery knockouts, what the knockout sheet, rows and snackbar say, Undo (envelopes back in the pool) and restarts, the seeded envelope draw, a game saved before bounty modes loading as Standard, and 36 seeded random sessions across the three modes. `BankSettleUpTest` (1.4): the settle-up once the night is over, the fewest payments, ticks through Undo, a restart, a changed payment and a reset, the last tick recording everyone square, the share text, and an install left on 1.3.14's Cash game tab. |
| tools-feature | 139 | 1 | The Sound section (S7): chime, volume, and the quiet cues' Vibrate and Flash switches (`ToolsHomeViewModelTest`, `ToolsHomeContentTest`). Odds: 100 golden hand-ranking and equity tests, exhaustive 5- and 7-card evaluator checks, the engine (exact, Monte Carlo, cancellation) and its ViewModel. `OddsKeypadRoomTest`: with the keypad open, the slot it is filling is in view (the page scrolls to it) and "Add player" within a scroll, above the keypad, inside the app's shell on all 24 cells and again in the 320 x 521 dp the device matrix's small profile leaves the app (on a short, narrow window the suits share the ranks' rows, three rows of keys, not four). Chip calculator ViewModel. History (PP-037): `HistoryViewModelTest` (over a real store: the nights the latest first with all time's standings, a year's season and its player of the year, a night opened and closed, delete with Undo on the snackbar, a night saved elsewhere showing up at once, a night shared as text), `ToolsHomeContentTest` (the History row opens History), and the screen tests in section 9. `OddsBenchmark` is skipped unless `ODDS_BENCH=1`. |
| tournament-feature | 358 | 0 | TournamentConfigViewModel (rebuy/add-on edits that can't wipe purchases, presets, paid places capped at the player count), the Float-to-cents preference migration. The clock: TimerViewModel on virtual time with a fake monotonic clock (late ticks, sleep gaps, process death mid-level and mid-overtime, reboot, v1.1 migration, chimes including the end chime after a resume, breaks, ante, write cadence, table numbers) and the break/overtime timeline. `BreakMessageFieldTest` is a Robolectric Compose UI test: hardware Enter in the break note must not click Reset. The Tournament tab (M3): `TimerViewModelControlsTest` (the one-minute nudges, End break now, color-up done, next break, projected end, rebuy state, mid-game blind changes that keep the level), `TournamentModeTest` (the setup/fold/clock/panel state machine and where the phone may turn), and the Robolectric screen tests in section 9. Bounty modes (PP-035): `TournamentBountyModeTest` (the bounty type saved under its own key, fixed from the first knockout, the mystery envelopes before the start, a game saved before it loading as Standard) and `PayoutsBountyModesTest` (the bounties card and share text in each mode). The live clock and the quiet cues (PP-081, PP-083): `ClockCueTimesTest` (when the chime, the level change and the one-minute warning fall, the chime's old timing kept exactly), `ClockCuesTest` (what each cue sets off with each switch, and a cue reported by both the clock and the service plays once), `LiveClockCardTest` and `LiveClockNotificationTest` (what the notification says, its countdown, buttons and their broadcasts, the public channel), `LiveClockDriverTest` (on virtual time: posted only when its words change, cues on time from the background, the wake lock only while running, Pause, Resume, the half-hour pause limit, reset and finish), and `LiveClockSyncTest` (the notification's Pause and Resume leave exactly what the clock's button leaves, the clock on screen follows at once, and a command made with the app's process gone is there when it comes back). Saved setups (PP-032): `PresetCodecTest` (every field round-trips; corrupt text, another format number, a missing or out-of-range field are skipped, never thrown; the saved keys), `PresetStoreTest` (a fresh store reads them back, last used first; a name in use is saved over; an unreadable preset is skipped and left in place), `CurrentSetupTest` (saved and loaded back, every field is exactly restored; players, the Bank and the clock untouched; refused once the clock has started; payouts follow tonight's player count), `PresetsViewModelTest` (save, load, rename, delete with Undo; the question only when something would be replaced; the clock and the setup page follow a load), `SetupShareTextTest` (the setup shared as text). Saving a night to History (PP-037), in `PayoutsViewModelTest`: offered only once the night is over and everyone owed is paid; one save however many taps, with every player in finishing order and their money; offered again if History deletes it, gone when the Bank is cleared; named after the preset the setup still matches. |

Since the makeover's design-system batch (M0), core also runs the screenshot goldens, the
device-matrix layout checks and the component semantics tests described in section 9: 224 more
test executions, about 20 s.

Before v1.2.0 the green run proved little: 245 executions but 101 unique tests (core's ran 3
times), and 54 tests in JUnit 4/5-mismatched modules never ran. Turning them on surfaced 43
failures.

### Skipped tests are specs for open board items

A skipped test asserts what the code *should* do, and is disabled until its board item lands.
Gradle prints each one as `SKIPPED` on every run. To enable one, remove its
`@Ignore`/`@Disabled` together with the fix.

There are none right now: batch B enabled the last ones (PP-014, PP-016, PP-018 and PP-020).
The only skipped test is `OddsBenchmark`, which is a benchmark, not a spec.

### Rules the build enforces

* **No silently skipped tests.** The root `build.gradle.kts` fails a module's test task if it
  executed fewer tests than its `src/test` declares `@Test` methods. Skipped tests count as
  executed. The check is off for `--tests` runs. If you see "ran 0 tests but src/test declares N
  @Test methods", the module lost the junit5 plugin or the `common-test` bundle.
* **Fixed locale.** Test JVMs run with `user.language=en`, `user.country=US`. A test about
  another locale must set `Locale` itself.
* **Lint and detekt fail only on new findings.** Pre-existing findings are listed in
  `<module>/lint-baseline.xml` and `<module>/detekt-baseline.xml`. After fixing some of them,
  regenerate with `./gradlew updateLintBaseline detektBaseline` and commit the smaller files.
  Don't regenerate just to hide a new finding.

### Writing tests that mean something

* Call production code and assert exact known answers or invariants, e.g. "paid out = paid in
  minus food" or "every level is a multiple of the smallest chip". Don't re-implement the
  logic in the test, don't assert on a mock's output, and don't write assertions that can't
  fail.
* **Don't mock `TimerPreferences` or `TournamentPreferences`.** Each declares a Flow property
  next to a same-named getter (`val timerRunning: Flow<Boolean>` and
  `fun getTimerRunning(): Boolean`). On the JVM those are two methods that differ only in return
  type. MockK can't tell them apart, so `every { prefs.timerRunning }` fails with "Missing mocked
  calls inside every { ... } block", depending on JVM method order. Use the real classes in a
  Robolectric test (Robolectric gives each test fresh SharedPreferences) instead.
* ViewModel tests: set `Dispatchers.Main` to a `StandardTestDispatcher`, and create the
  ViewModel through a `ViewModelStore` so `store.clear()` cancels its coroutines. For a running
  clock, step it with `advanceTimeBy` + `runCurrent`, never `advanceUntilIdle` (a running clock
  ticks forever).
* **The tournament clock reads time only through `TimeSource`.** `TimerViewModelTest` injects a
  fake whose monotonic and wall clocks follow the scheduler's virtual time, so the tick loop and
  the clock agree; `sleep(ms)` moves the clocks without running a tick (deep sleep), `reboot()`
  restarts the monotonic clock, and clearing the `ViewModelStore` and building a new ViewModel
  from fresh preference objects is a process death. Never sleep or read real time in a test.
* **A process death builds everything again from what was saved**: clear the `ViewModelStore`,
  make new preference objects over the same SharedPreferences (nothing kept in memory survives),
  build a new ViewModel. `BankTestKit.restartProcess()` does it for the Bank. Compare what the
  screen shows before and after, leaving out only what is meant to go (an open sheet, Undo's
  history).

### Property-based tests

Example tests pin the answers someone thought of; property tests state what must hold for *every*
input and let a generator look for the case nobody wrote down. They use
[kotest-property](https://kotest.io/docs/proptest/property-based-testing.html) (test-only) from
plain JUnit `@Test` methods, through `forAll` in core's test fixtures
(`core/src/testFixtures/.../testing/Properties.kt`):

```kotlin
@Test
fun `the table adds up to the pool to the cent`() =
    forAll(seed = 2026_1008_01L, iterations = 4_000, gen = cases) { case ->
        expect(case.table().totalCents == case.poolCents) { "pays ${case.table().totalCents} of ${case.poolCents}" }
    }
```

* **Seeded.** Every run checks the same cases. A failure prints the case, a *shrunk* one (kotest
  makes it as small as it can while it still fails: fewer steps, smaller numbers) and
  `Repeat this test by using seed N`. Change a seed only on purpose.
* **Generators build inputs from numbers** (`Arb.bind`, `Arb.list`), so a failing night at the Bank
  shrinks to its fewest, smallest steps. `BankNight` (core, `property/BankScript.kt`) replays a
  script of Bank steps onto the records the Bank keeps, stale credits included.
* **Kinds of property**: invariants (the table adds up to the pool), metamorphic relations
  (renumbering the players changes nothing; the same night in bigger money pays the same, scaled),
  oracles (the CSV read back by an independent RFC 4180 reader), and twin runs (the clock with a
  process death against the clock with the phone only asleep).

| Module | Class | What it holds |
|---|---|---|
| core | `PayoutPropertiesTest` | Any pool, field, structure and rounding: the table adds up to the pool, whole units below 1st within one unit of each share, no place above the one before it (falling weights), the same for 50/30/20 and 5/3/2, exact shares for exact pools; more or fewer places always give weights the editor accepts |
| core | `SettlementPropertiesTest` | Random Bank nights in all three bounty modes, step by step: nothing owed beyond the pools, nothing negative, every cent owned once there is a champion, also with late registration; renumbering or reordering the players changes nothing; one more knockout never takes money from anyone |
| core | `SettleUpPropertiesTest` | The settle-up (1.4, `MinimumPayments`) pays the same, scaled, in bigger money; ids are labels; a party already square changes no payment; any finished Bank night, with any entries ticked and winners paid, settles square in no more payments than the greedy pass |
| core | `BlindPropertiesTest` | Every setup the Tournament tab allows: the advisor and the calculator agree, every ladder keeps every rule (overtime included), every fix offered works |
| core | `MoneyPropertiesTest` | A money field in any JVM locale types back key by key; nothing typed throws; "$1,234.56" reads back; v1.1's Floats come back to the cent |
| core | `HistoryPropertiesTest` | Every night the Bank can finish saves and reads back; the CSV reads back with every name intact (Robolectric) |
| tournament-feature | `ClockRestoreTest` | Random clock sessions played twice, with process deaths and with the phone asleep for as long: the two clocks agree a second after every step |
| bank-feature | `BankRestoreTest` | Random nights with the process killed at random: the Bank shows exactly what it showed |
| tools-feature | `OddsRestoreTest` | Random odds sessions with the process killed at random: the table comes back exactly |

Bugs they found (wave 9): a late player in a mystery-bounty game made the envelopes pay out up to
$9 more than the bounty pool (`MysteryBounty.left`); overtime doubled a 500,000,000 stack into a
negative big blind; the setup advice offered a 1,200,000,000-chip stack whose ladder overflowed;
the payout editor lost its unsaved weights when the activity was recreated.

## 7. Instrumented tests

```bash
./gradlew assembleDebugAndroidTest                # compiles every module's test APK, no device needed
scripts/device/boot.sh
ANDROID_SERIAL=emulator-5580 ./gradlew connectedDebugAndroidTest --continue -Dorg.gradle.jvmargs=-Xmx4g
scripts/device/install.sh --no-build     # connected* tasks uninstall the app afterwards
```

There are no instrumented tests right now. The two old ones no longer compiled, and were
deleted in v1.2.0:
* `PlayerRowLayoutTest` targeted a now-private composable.
* `DecimalTextFieldTest` used a stale `PoolConfigurationSection` signature.

The device tour (section 5) covers on-device behaviour. The Roborazzi tier (section 9) covers
composables on the JVM. Library modules use the stock `AndroidJUnitRunner`.

## 8. Troubleshooting

| Symptom | Fix |
|---------|-----|
| `Cannot find a Java installation ... languageVersion=21` (or 17 on old commits) | Install that JDK. Gradle auto-detects `/usr/lib/jvm/*` and `~/.gradle/jdks/*` (the latter only with a `.ready` marker file). |
| `SDK location not found` | The scripts export `ANDROID_HOME`. For plain `./gradlew`, run `export ANDROID_HOME=~/Android/Sdk` or create a `local.properties` (it is gitignored). |
| `boot timed out` / `emulator process exited during boot` | Read `build/device/emulator.log`. Check KVM with `emulator -accel-check`. Make sure no other emulator holds the AVD (`pgrep -af qemu`). Try `boot.sh --cold`, or `boot.sh --wipe` for a corrupted snapshot or userdata. |
| `adb: device offline` right after boot | This is transient; `boot.sh` waits for the package manager. If it persists: `adb kill-server` and run `boot.sh` again. |
| Stale lock after a crash (`multiinstance.lock`, `*.lock` in `~/.android/avd/pokerpayout_test.avd/`) | Run `stop.sh --force`, delete the `*.lock` files in the test AVD, and boot again. |
| `uiautomator dump failed: could not get idle state` | Something animates forever. Animations are off globally, so look for an infinite Compose animation. ui.py retries 4 times. |
| A white bar with a keyboard icon at the bottom of screenshots | The IME switcher shows while a text field has focus (the emulator has a hardware keyboard, so no soft keyboard appears). Press `ui.py enter` or navigate away to drop focus. |
| App missing after `connectedDebugAndroidTest` | Expected: the test tasks uninstall it. Run `install.sh --no-build`. |
| Text assertions fail on another machine | Run `boot.sh` again: it re-applies the settings and warns if the locale is not en-US. |
| Port clash with another emulator | `PP_EMU_PORT=5590 scripts/device/tour.sh` (even numbers 5554-5682). |
| `flock /tmp/pokerpayout-emulator.lock ...` waits forever though no tour runs | Something started inside an earlier locked run still holds the lock's file descriptor; `lsof /tmp/pokerpayout-emulator.lock` shows it. `boot.sh` and `lib.sh` now start the emulator and the adb server without it. For a leftover from an older checkout, run `stop.sh` and `adb kill-server`. |
| Every step is slow, `pm clear` or `uiautomator dump` time out, high iowait on the host | A quick-boot emulator maps its 3 GB of RAM onto `snapshots/default_boot/ram.img`, and under host memory pressure it wrote hundreds of MB/s back to disk. Boot it with `boot.sh --cold` (no snapshot, no file-backed RAM) before the tour. |
| Low RAM | The emulator uses about 2 GB. The scripts cap Gradle at `-Xmx4g` and the Kotlin daemon at 2 GB. Run one Gradle build at a time. |
| `Roborazzi: ... is changed.` | A golden no longer matches. Open `<module>/build/outputs/roborazzi/*_compare.png`. If the change is intended, run `./gradlew recordRoborazziDebug` and commit the new PNGs (section 9). |
| `... doesn't fit on <config>: it is N px tall` | A gallery is taller than that screen's window, so its golden would be cut off. Split it into smaller previews. |
| A layout assertion fails only at font 2.0 | Real: at 200% the text needs more room. Let it wrap (no fixed heights, no `maxLines = 1` on labels), or reflow to one column. Shrinking text is only for fixed-width slots (see `rememberFittedStyle`). |
| A golden folder you deleted comes back as untracked after a test run | Roborazzi keeps a copy of every golden under `<module>/build/intermediates/roborazzi/` and restores it into `src/test/screenshots/`. Delete a retired golden in both places (or run `./gradlew clean`). Never `git add -A` after a test run without checking `git status`. CI starts clean, so it is unaffected. |

## 9. Screenshot goldens and layout checks (Roborazzi)

Composables render to PNG in plain JVM tests (Robolectric with native graphics, SDK 34), with
no emulator. Two layers run on a **device matrix** of eight screens and three font scales:

1. **Goldens.** Each design-system component's `@Preview` gallery is captured and compared,
   pixel for pixel, with a committed golden image. A visual change fails the build until someone
   re-records on purpose.
2. **Layout assertions.** Compose semantics checks that fail on their own, without anyone
   looking at a picture: no text clipped, cut off, ellipsized or broken mid-word; no text pushed
   off screen; every touch target at least 48 x 48 dp; no overlapping touch targets.

### The device matrix

`core/src/testFixtures/.../core/testing/DeviceMatrix.kt`. Sizes are in dp, rendered at xhdpi (2x).

| Device | Size (dp) | Why |
|---|---|---|
| `small` | 320 x 640 | The tightest phone width we support |
| `phone` | 360 x 780 | The mockup frame, so goldens compare 1:1 with `mockups.html` |
| `tall` | 412 x 915 | Pixel 7 |
| `foldable` | 600 x 960 | 7-8 inch tablet, foldable opened flat |
| `tablet` | 800 x 1280 | 10 inch tablet |
| `small-land` | 640 x 320 | Rotated small phone: the shortest height |
| `phone-land` | 780 x 360 | Rotated phone (table view, run it out) |
| `tablet-land` | 1280 x 800 | Rotated tablet |

Font scales are 1.0, 1.3 and 2.0. Robolectric runs SDK 34, so 2.0 scales large text
non-linearly, exactly as Android 14 does. Robolectric draws status and navigation bars, so the
app window is a little shorter than the screen (the `phone` window is 360 x 724 dp).

* **Layout assertions run on all 24 cells** (`DeviceMatrix.all`).
* **Goldens are stored for 6 of them** (`DeviceMatrix.goldens`, PP-090), plus a few named goldens
  on cells of their own (`DeviceMatrix.pinned`). The assertions cover the rest without storing
  pictures.

Every committed picture stays in the git history, and F-Droid clones the whole history to build,
so a cell gets goldens only if it shows a layout no other cell shows. The screens switch layout at
360 dp (small phones: 12 dp gutters, the Bank's folded columns, the bell in the menu), 600 dp (the
rail; two columns or panes once the column beside it is 600 dp wide), 840 dp (the two-pane clock and
Bank), in landscape (the table view, the odds keypad beside the cards, run it out's two panes) and
at font scales 1.15, 1.3 and 1.5 (stacked sums, wrapped lines, the Bank header's icons only):

| Cell | What only it shows |
|---|---|
| `small` at 2.0 | The small-phone layouts at the largest font: the tightest cell (the odds keypad's short form, suits sharing the ranks' rows) |
| `phone` at 1.0 | The layouts as drawn, 1:1 with `mockups.html` |
| `phone` at 1.3 | Between the font thresholds: one knockout choice a row and stacked sums (above 1.15), the Bank header's words (up to 1.3) |
| `tablet` at 1.0 | The rail beside a 704 dp column: Hand ranks in two columns, Chip set in two panes |
| `phone-land` at 1.0 | A phone on its side, the shortest common height: the table view, the keypad beside the cards, run it out's two panes |
| `tablet-land` at 1.0 | The only cell from 840 dp: the two-pane clock and Bank (Z4, Z5), the centred 720 dp column |

Left out (until PP-090 they had goldens too): `tall` at 1.0 (the `phone` layouts with more room),
`foldable` at 1.0 (the rail beside a 504 dp phone column), `small` at 1.0 and `phone` at 2.0
(`small` at 2.0 is tighter than both).

The pinned goldens keep the cells they were drawn for: `Z1_clock_small` (`small` at 1.0),
`Z2_bank_small` (`small` at 1.0 and 2.0), `Z3_table_small_land` and `S10_runout_land`
(`small-land`), `Z4_clock_tablet` and `Z5_bank_tablet` (`tablet-land`), `S2_clock_running_font2x`,
`S8_odds_font2x` and `S14_seats_font2x` (`tall` at 2.0), and `S5_bank_font2x` and `S6_payouts_font2x`
(`small` and `phone` at 2.0). A test records a pinned golden with
`DeviceMatrix.isPinned(name, config)`, never with a cell written into the test, so
`scripts/dev/retired-goldens.sh` can work out from the code which committed goldens no test
records any more.

### Where the goldens live

`<module>/src/test/screenshots/<group>/<Name>/<Name>_<device>-<w>x<h>_font<scale>.png`, for example
`core/src/test/screenshots/components/PokerButton/PokerButton_small-320x640_font2.0.png`. One
folder per component, so a reviewer can flip through one component across the matrix.
`components/Shell/` holds a whole screen assembled from the components, captured at the full
window size: the place to see stretch and rotate.

### Commands

```bash
./gradlew testDebugUnitTest            # runs everything and VERIFIES the goldens (the CI gate)
./gradlew :core:recordRoborazziDebug   # re-records core's goldens: only for an intended UI change
./gradlew :core:compareRoborazziDebug  # writes diffs without failing (for a PR's before/after)
./gradlew :core:verifyRoborazziDebug   # verify explicitly
./gradlew :core:testDebugUnitTest --tests '*ComponentLayoutTest*'   # just the matrix assertions
gh workflow run goldens.yml --ref <branch>   # re-records on GitHub instead, recompresses, commits
scripts/dev/recompress-goldens.sh --changed  # after a local record: shrink what changed, put back the rest
scripts/dev/retired-goldens.sh               # committed goldens no test records any more (--delete: git rm)
```

Plain `testDebugUnitTest` verifies because `gradle.properties` sets
`roborazzi.test.verify=true`. The record, compare and verify tasks override that setting. CI
(`.github/workflows/ci.yml`) runs `testDebugUnitTest`, so a changed picture fails CI, and the
failure uploads the diffs with the reports.

### Reviewing a change

1. A verify failure says `Roborazzi: .../PokerPill_phone-360x780_font1.0.png is changed.`
2. Open `<module>/build/outputs/roborazzi/<Name>_<config>_compare.png` (Claude Code's Read tool
   shows PNGs). Each compare image is reference | diff | new, side by side, with the changed
   pixels in red in the middle.
3. `build/test-results/roborazzi/debug/results-summary.json` lists every capture as
   `unchanged`, `changed` or `added`; `build/reports/roborazzi/debug/index.html` is the same
   as a page.
4. If the change is intended, run `recordRoborazziDebug`, look at the new goldens next to
   `mockups.html` (at least `small` at 2.0, a tablet and a landscape one), and commit them in
   the same commit as the code that changed them. Say in the commit which components changed.

Never commit re-recorded goldens to hide a change you didn't mean, just as you wouldn't
regenerate a lint baseline to hide a finding.

### Keeping the repository small (PP-090)

Goldens are the bulk of the repository: on 1.3.11 the 912 checked-in goldens were 81 MB, and every
golden ever committed to master took 92 MB of the 97 MB its file history packs into. F-Droid clones
all of it for every build. Two rules keep that down:

1. **Fewer cells** (above): 6 golden cells instead of 10, so a screen that changes adds 6 pictures,
   not 10.
2. **Lossless recompression.** Roborazzi writes each PNG as RGBA with fast compression. `goldens.yml`
   runs `scripts/dev/recompress-goldens.sh --changed` after recording: every golden the run changed
   or added goes through oxipng (a pinned release, `-o 4 --strip safe --ng`; the colour type may drop
   the unused alpha channel or become a palette, never greyscale, which Java reads through a linear
   colour space). The script then checks every file pixel for pixel against the one it replaced,
   reading both exactly as Roborazzi reads a golden (`scripts/dev/SamePixels.java`: ImageIO, drawn
   onto an ARGB canvas), and puts everything back if one pixel differs, so verification still
   passes. A golden whose pixels match the committed one is put back as committed: a re-record
   rewrites every file, and only real changes should reach the history.

`gh workflow run goldens.yml --ref <branch> -f recompress_all=true` records nothing: it recompresses
every committed golden a test still records (`--all`; retired ones are left for deletion) and
commits that, the one-time shrink of the checkout. That commit adds the whole recompressed set to the
history once; it pays off only as later re-records add smaller pictures.

After recording locally with `recordRoborazziDebug`, every golden is rewritten in Roborazzi's
encoding, and git shows them all as changed. Run `scripts/dev/recompress-goldens.sh --changed` (it
needs `oxipng` on the path, or `OXIPNG=/path/to/oxipng`) before committing, or record on GitHub.

`scripts/dev/retired-goldens.sh` lists the committed goldens whose cell no test records any more,
from `DeviceMatrix.goldens` and `DeviceMatrix.pinned` (`--check` exits 1 if there are any, `--delete`
runs `git rm` on them). A `goldens.yml` run lists every golden it didn't write next to that list in
its summary; the two match unless a test was renamed or removed.

### Determinism

* Fonts: Barlow Condensed is bundled in `core/src/main/res/font`, and Robolectric's native
  graphics renders Roboto from its own android-all jar, so no system fonts are involved.
* Animation: previews and goldens render inside `PokerTheme(reducedMotion = true)`, which turns
  off every component animation (the pulsing next-card slot becomes a static outline).
* Locale: the qualifiers pin `en-US`, and test JVMs run with `user.language=en`.
* Time: nothing in a component reads the clock. Tests that need time (the stepper's
  repeat-while-held, the 8 s Undo window) use the Compose test clock or `runTest` virtual time.
* Comparison: Roborazzi's default per-pixel tolerance (colour distance 0.007) absorbs
  anti-aliasing noise, and `changeThreshold = 0` makes any real pixel change fail.

### Writing a screenshot or layout test (feature modules too)

The kit is in `core`'s **test fixtures** (`core/src/testFixtures`). A feature module adds
`testImplementation(testFixtures(project(":core")))`, the `roborazzi` plugin, the same
`roborazzi { outputDir.set(file("src/test/screenshots")) }` block as `core/build.gradle.kts`, and
`isIncludeAndroidResources = true`. Then:

```kotlin
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ClockScreenTest(private val config: ScreenConfig) {
    @get:Rule val screen = ScreenTestRule(config)   // applies size, orientation, locale, font scale

    @Test fun running() {
        screen.compose.setContent { PokerTheme(reducedMotion = true) { ClockContent(fixtureState) } }
        LayoutAssertions.assertTextFits(screen.compose, "S2 on ${config.id}")
        LayoutAssertions.assertTouchTargets(screen.compose, "S2 on ${config.id}", strict = false)
        screen.compose.onRoot().captureGolden("screens", "S2_clock_running", config)
    }

    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs() = DeviceMatrix.parameters(DeviceMatrix.goldens)
    }
}
```

* `LayoutAssertions.assertTextFits`: every text fits its box (no overflow, no line cut off by
  `maxLines`, no ellipsis, no word broken across lines, nothing off the side of the screen).
  Mark a text that may ellipsize (a top-bar subtitle) with `Modifier.semantics { mayTruncate = true }`.
* `LayoutAssertions.assertVisibleTextUnclipped`: no fully visible text is clipped by a parent.
  Call it at each scroll position (see `ComponentLayoutTest.forEachScrollPosition`).
* `LayoutAssertions.assertTouchTargets`: 48 x 48 dp and no overlaps. `strict = true` demands
  the clickable node itself be 48 dp (the design-system components do); `strict = false`
  accepts Compose's expansion of a smaller node, as long as no neighbour is in the way. It also
  checks **TalkBack names** (`AccessibilityAssertions.assertNamed`): every tappable node has a
  text or a content description, and no empty text field is without a label (TalkBack would
  read only "Edit box"; a field with a value is read by it, as the Bank's names are).
* **Contrast** (`AccessibilityAssertions.assertTextContrast`), run by `assertTextFits` on the
  reference cell only (`phone` upright at font 1.0; contrast is about colours, not size): every
  text has 4.5:1 against what is behind it, or 3:1 at 18 sp and up (14 sp bold), WCAG 2.1 AA.
  The text's colour is the one its style declares (blended if see-through); what is behind is
  the commonest other colour in its box on the rendered screen. Text in a disabled control is
  exempt. Pairs below AA kept on purpose are listed in `KNOWN_BELOW_AA` (the owner's call, 1.4.5:
  chip values on the green and grey chips' physical colours, and Hand ranks' faded cards); any other
  pair below AA fails. The check's first run also found the selected segment's 90% gold second line
  and the odds grid's ChalkDim cards, both now drawn at full strength.
  Google's Accessibility Test Framework (through Roborazzi) was the other way to do it;
  this needs no new dependency, reads the colour the text really has instead of guessing it from
  anti-aliased pixels, and gives a message that names the text and both colours.
* `captureGolden` refuses to capture something taller than the window, because the picture
  would silently cut it off. Split a long gallery instead.
* Render sheets through their content composable (`PokerSheetContent`), not through
  `ModalBottomSheet`: a modal window doesn't capture under Robolectric.

`LayoutAssertionsTest` proves each check fails on the breakage it describes; extend it when you
add a check.

### Screens in their modules

A screen is tested inside the real shell with `InAppShell(NavTab.X) { ... }` (test fixtures), so
its golden shows the bottom bar on phones held upright and the rail from 600 dp. One
parameterized class per tab runs on all 24 cells: the layout checks everywhere, and
`captureGolden` only where `config in DeviceMatrix.goldens` (a pinned golden: where
`DeviceMatrix.isPinned(name, config)`). `forEachScrollPosition { }` scrolls
every scrolling container a page at a time, for `assertVisibleTextUnclipped`.

| Module | Class | Goldens (`src/test/screenshots/screens/`) | Layout checks |
|---|---|---|---|
| `tools-feature` | `ToolsTabScreenTest` | `S7_tools_default`, `S7_tools_muted`, `S7_tools_cues_off`, `S7_tools_notifications_off` (PP-081/083: the Vibrate and Flash rows, and the way back to notifications; the History row, PP-037), `S7_tools_music` (the Cue sounds and Music rows, a song playing) | S7: all three, at every scroll position |
| `tools-feature` | `MusicScreenTest` | `S18_music_empty` (a fresh install: no songs, nothing built in), `S18_music_playing` (five songs, one playing, one whose file has gone, shuffle and repeat on, with the clock and quieter on breaks), `S18_music_editing` (move and remove), `S18_cue_sounds`, `S18_cue_sounds_off` (the sound switched off) | Music and Cue sounds: all three, at every scroll position, on all 24 cells; also every file gone and the built-in songs (none ship yet). Fixtures in `MusicFixtures` |
| `tools-feature` | `HandRanksScreenTest` | `S12_ranks_default`, `S12_ranks_4colour` | All three, at every scroll position |
| `tools-feature` | `ChipSetScreenTest` | `S11_chipset_ok`, `S11_chipset_short`, `S11_chipset_ok_end`, `S11_chipset_settings` (the stack settings unfolded, keeping back the Tournament's estimate) | All three, at every scroll position of each pane; also the unfolded stack settings and the colour sheet |
| `tools-feature` | `HistoryScreenTest` | `S16_history_list` (all time, two players level at the top), `S16_history_night` (one night in full), `S16_history_empty` (nothing saved yet) | History (PP-037): all three, at every scroll position, on all 24 cells; also a year picked. Fixtures in `HistoryFixtures`: three nights over two years with the mockups' players |
| `tools-feature` | `SeatDrawScreenTest` (+ `SeatDrawExtraGoldenTest`) | `S14_seats_empty`, `S14_seats_one_table`, `S14_seats_two_tables`, `S14_button_draw`; `S14_seats_font2x` at tall@2.0 | All three, at every scroll position of each pane; also the name fields and an out-of-date draw with the players unfolded |
| `tournament-feature` | `TournamentTabsScreenTest` | `Shell_tournament` | Tournament: touch targets |
| `tournament-feature` | `TournamentScreenGoldenTest` | `S1_setup_{before,invalid}`, `S1_setup_mystery` (mystery bounties picked, the envelopes listed, PP-035), `S1_fold_300ms`, `S1_running_strip`, `S1_panel_{open,unlocked}`, `S2_clock_{ready,running,paused,final_minutes,overtime,finished}`, `S2_clock_running_font2x`, `S3_table_{running,paused,break}` (landscape cells), `S4_break_{colorup,done,plain}`, `S4_break_chipset` (the color-up with a chip set set up in Tools, PP-091 #9), `Z1_clock_small`, `Z3_table_small_land`, `Z4_clock_tablet` | All three checks on all 24 cells (the fold frame: none, it is mid-animation); the top bar's buttons stay 48 x 48 dp at every scroll position. `SetupStripTest`: the strip shows whole settings, as many as fit |
| `tournament-feature` | `PresetsScreenTest` | `S15_presets_list`, `S15_presets_locked` (mid-game: loading off, and why), `S15_presets_save`, `S15_presets_load` (the question when loading would replace what the host set) | Saved setups (PP-032): all three, at every scroll position, on all 24 cells; also the empty list, the rename form and the save form before the chip set is set up. The sheet is drawn as its content over the tab and its scrim; the checks run on the sheet alone. The Presets row itself is in the S1 goldens (setup page and panel) |
| `tournament-feature` | `PresetsInteractionTest` | none | What the presets' controls send: the row on the setup page and in the panel, load (off mid-game), save (the name, the chip set switch, a name in use replaces it, a blank one can't be saved), share, rename (a name in use is refused), delete, and the load question |
| `tournament-feature` | `TournamentInteractionTest`, `TournamentRotationTest`, `SetupFoldTest` | none | What each control sends; rotation per device class (Robolectric `+land` shows the table view, `+port` the clock, other tabs portrait on phones, tablets free, state kept through recreation; ✕ in a turned table view holds for that turn only, with the phone's hold faked through `LocalPhoneHold`); the fold plays once and is cut under Reduce motion |
| `tournament-feature` | `PayoutsTabScreenTest` | `S6_payouts_{standard,topheavy,custom,finished}`, `S6_payouts_font2x`, `S6_payouts_save` (PP-037: over and everyone paid, "Save this night" heads the tab) | All three, at every scroll position, and locked while the clock runs; also the saved night's line |
| `bank-feature` | `BankScreensTest` | `S5_bank_{before_buyins,midgame,rebuys_open,no_rebuys,champion,30players}`, `S5_bank_font2x`, `S5b_knockout_sheet`, `S5b_count_sheet`, `S5c_payout_{champion,second}`, `S5c_pool_breakdown`, `Z2_bank_small`, `Z5_bank_tablet`; PP-035: `S5_bank_pko` (each player's bounty under the name), `S5b_knockout_sheet_pko`, `S5c_payout_champion_pko`, `S5d_envelope_reveal`; 1.4: `S5_bank_settle_up` (Settle up under the meters), `S5e_settle_up` (who pays whom, two ticked) | All three, at every scroll position (also the mystery pool breakdown and the settle-up once everyone is square). A sheet is rendered as its content over the screen behind, since a modal window doesn't capture under Robolectric |

The screens' ViewModels are the real ones over Robolectric's in-memory preferences, set up as the
mockups' game (9 players, $40 buy-in, and so on), so a golden shows what the app shows.

### The tests in core today

| Class | Runs | What |
|---|---|---|
| `ComponentGoldenTest` | 17 galleries x 6 goldens = 102 | Each component's `@Preview` gallery (including `PokerNavRail`), plus `Shell`, the real `PokerAppShell` around a sample screen |
| `ComponentLayoutTest` | 2 x 24 cells = 48 | Every gallery in one scrolling column, and the shell, through all three layout checks |
| `AppShellTest` | 4 x 24 cells = 96 | The bar below 600 dp and the rail from 600 dp, by window width; tab geometry; the screen capped at 720 dp and centred; the shell through all three layout checks |
| `NavBarTest` | 10 | Every screen's tab (tools keep Tools selected, B16); tab taps don't pile up on the back stack and Back returns to Tournament; tapping a tab inside a tool returns to its list |
| `ComponentSemanticsTest` | 12 | TalkBack: roles (checkbox, radio button, tab, button), names ("Ace of spades", "Rebuy, 1 taken, Locked"), headings, field errors (and payout weights out of order, which aren't only red); taps; the stepper's ends and repeat-while-held |
| `DesignTokensTest` | 5 | Every contrast pairing in the design spec, computed; the six original colours unchanged (the sunset colours were retired once no screen used them) |
| `TypographyTest` | 5 | Barlow loads; `tnum` makes every digit the same width (and without it they differ); the licence ships |
| `UndoSnackbarTest` | 4 | Undo inside the 8 s window counts, after it doesn't (virtual time) |
| `MoneyComponentsTest` | 3 x 24 cells = 72 | `MoneyMeter`, `PlaceBadge` and `PayoutStructureSheet`: goldens on the 6, all three layout checks on all 24 |
| `MoneyFieldTest` | 4 | `MoneyField`: typed text kept key by key, cents out, one commit on Done, focus loss or the field going away; 0 is an amount, empty is none |
| `LayoutAssertionsTest` | 11 | The checks themselves catch what they claim |
| `ScreenOrientationTest` | 3 | Phones portrait unless the screen on show asks for more, and portrait again when it goes; free from 600 dp; a screen can take the full width beside the rail, or the whole window |
| `SystemBarsTest` | 1 | Light status and navigation bar icons on the dark app (B13) |
| `SoundManagerTest` | 7 | A loaded chime plays at the slider's volume now, not the one it had when loaded (B12); silent at 0 and with the sound off; the music dips while a cue sounds and comes back up when it ends or fails; a preview plays with the sound off. The player is a recording fake |
| `SoundPacksTest` | 5 | The classic pack is today's sounds (the chime at every change, nothing with a minute left); every pack has its own id and a name; an unknown saved id plays the default; `sound_pack` is a key of its own |
| `PlaylistTest` | 18 | The playlist as a value: adding (no doubles), moving and removing keep the place; the end of a song, Next and Previous with repeat off, all and one; songs whose files have gone passed over; shuffle on seeded randoms: the current song first, the same seed the same order, every song once a pass, never one song twice in a row across passes (200 seeds), songs added mid-pass come later |
| `MusicAutoPlayTest` | 11 | Play with the clock against the clock's states: starts and pauses with the clock, breaks keep playing, pause or play quieter, acts only on changes (the host's own pause stands), never pauses music it didn't start, does nothing when off |
| `MusicPreferencesTest` | 8 | `music_prefs`: defaults, the playlist back exactly as saved, the settings under their own keys, unreadable or half-broken text read safely; titles from file names |
| `MusicPlayerTest` | 20 | The music player with recording players and a library whose files can go: loads and starts the current song, keeps the place on a pause and across a restart, goes on at the end of a song and stops at the end of the list, passes over gone and failing files (stops when none is left), removes the song playing, saves the list, and dips under a cue (back up after 15 s if the cue never ends) and on a quiet break |

## 10. The device matrix: real screens, sizes, fonts and rotation

Section 9 renders composables on the JVM. The device matrix (PP-078 layer 2) runs the **real
app on the real emulator** at several screen sizes, densities, font scales and rotations. It
catches what Robolectric can't: system bars and insets, the soft keyboard, real rotation and the
activity being recreated, gestures and scrolling, and the process staying alive through it all.

```bash
flock /tmp/pokerpayout-emulator.lock scripts/device/matrix.sh --stop   # build, install, the focused 4 profiles
scripts/device/matrix.sh --profiles daily              # the once-a-day set (7 profiles); all = every one
scripts/device/matrix.sh --no-build                    # reuse the last APK
scripts/device/matrix.sh --profiles small,tablet       # some profiles (--list shows them)
scripts/device/matrix.sh --profiles soft-keyboard      # the full-height soft keyboard, on its own AVD
scripts/device/matrix.sh --profiles tablet-ignore      # a tablet that ignores orientation requests
scripts/device/matrix.sh --steps bank,ime,ime-done     # your own steps (launch and profile come first)
scripts/device/matrix.sh --full                        # every tour step on every profile
scripts/device/matrix.sh --release                     # the R8 build
```

Run it under the emulator lock like the tour, and don't edit `matrix.sh` while it runs (bash
reads a running script as it goes).

### Profiles

There is one AVD (`pokerpayout_test`, 1080 x 2400 @ 420 dpi), plus its soft-keyboard twin for the
`soft-kb` profiles (below). Each profile overrides its screen with `adb shell wm size` and
`wm density`, and sets `font_scale` and `user_rotation`. That takes seconds, needs no extra
system images, and the app really relays out: the `profile` step checks that the app's window is
exactly the overridden screen.

| Profile | Screen | dp | Font | Turns | Set | Why |
|---|---|---|---|---|---|---|
| `small` | 720 x 1280 @ 360 | 320 x 569 | 1.0 | no | smoke | The smallest phone we support; most screens run below the fold |
| `small-f1.3` | 720 x 1280 @ 360 | 320 x 569 | 1.3 | no | screens | Large text on it |
| `small-f2.0` | 720 x 1280 @ 360 | 320 x 569 | 2.0 | no | screens | The worst case: the largest text on the smallest screen |
| `compact` | 1080 x 2400 @ 480 | 360 x 800 | 1.0 | no | screens | The commonest Android phone width (the mockups' frame) |
| `default` | the emulator's own | 411 x 914 | 1.0 | no | screens | Pixel 7 class; the plain tour covers it step by step |
| `default-f2.0` | the emulator's own | 411 x 914 | 2.0 | no | screens | The largest text on a common phone |
| `foldable` | 1768 x 2208 @ 420 | 673 x 841 | 1.0 | yes | screens | A foldable opened flat: the rail, a nearly square window |
| `tablet` | 1600 x 2560 @ 320 | 800 x 1280 | 1.0 | yes | smoke | A 10-inch tablet held upright: the rail, the 720 dp content cap |
| `default-f1.3` | the emulator's own | 411 x 914 | 1.3 | no | screens | Not in the default run: it found nothing `default` and `default-f2.0` don't |
| `large` | 1440 x 3120 @ 560 | 411 x 891 | 1.0 | no | screens | Not in the default run: the same dp as `default` at 3.5x, and it found nothing more |
| `tablet-land` | 1600 x 2560 @ 320, turned 90 | 1280 x 800 | 1.0 | yes | smoke | Not in the default run: the tablet turned, every tab landscape (`rotate` and `rotate-clock` turn every profile anyway) |
| `tablet-ignore` | 1600 x 2560 @ 320 | 800 x 1280 | 1.0 | yes | smoke | Opt-in: the tablet with a display that ignores the app's orientation requests, as large screens may (see "Large screens that ignore orientation requests") |
| `soft-kb` | the emulator's own, on the soft-keyboard AVD | 411 x 914 | 1.0 | no | keyboard | Opt-in, in a run of its own: no hardware keyboard, so the full-height soft keyboard (see "The keyboard") |
| `soft-kb-small` | 720 x 1280 @ 360, on the soft-keyboard AVD | 320 x 569 | 1.0 | no | keyboard | The same on the smallest phone: the least room left above the keyboard |

**Turns** follows the app's rotation rules (M3, PP-079/PP-088), by the smallest width:

* **Phones** (under 600 dp): every tab is portrait, except the Tournament tab once a clock exists,
  which follows the display: turned, the clock is the table view (S3); upright, the clock (S2).
* **Foldables and tablets** (600 dp and up): every tab turns with the display; turned, the clock
  stays the clock (two panes from 840 dp).
* On every size the table-view button (⤢) shows the table view in landscape until ✕.

**Sets** (PP-093): `focused` is the routine run and the default (`small`, `small-f2.0`,
`default-f2.0`, `tablet`); `daily` runs once a day (`small`, `small-f1.3`, `small-f2.0`, `compact`,
`default-f2.0`, `foldable`, `tablet`; not `default`, which the plain tour covers on every pull
request); `soft-keyboard` runs `soft-kb` and `soft-kb-small`; `all` runs every profile on the main
AVD (every one but the `soft-kb` ones). A set's name stands alone (`--profiles focused`, not
`focused,large`). On GitHub, the `Device matrix` job in `device.yml` runs a set or profiles by
hand: `gh workflow run device.yml --ref <branch> -f job=matrix -f profiles=focused` (or
`-f profiles=soft-keyboard`, `-f profiles=tablet-ignore`). Dispatched runs with different inputs
run side by side, so those can start together; the same inputs again replace the earlier run. When
the matrix fails, the job's "Show the failing steps" prints every failed step of every profile (its
part of `tour.log` and the text on screen after it), and the log has each layout-check failure, so
most failures can be read without downloading the report.

The emulator scales any override onto its panel, so sizes bigger than 1080 x 2400 work too.
Screenshots come out at the profile's own size. SystemUI forgets its demo mode when the size
changes, so the matrix sends it again (the clock stays at 12:00).

### What runs on each profile

Each profile runs one of three step sets (`--list` prints them):

* **smoke** (50 steps, on `small` and `tablet`), in the tour's own order: setup (money, the
  Payouts tab and its structure sheet, blinds, the smallest chip, breaks, the ready ticket); Start,
  which folds setup into the clock; two levels on to the first break; the table view from its
  button and back; End break now; the running clock turned both ways; the setup panel over the
  clock; a reset; the Bank (rename, buy-in, rebuy, a knockout, the pool and structure sheets) and
  the soft keyboard; the Payouts tab; Tools, Hand ranks, Odds to the exact result, the chip set;
  the rebuy cutoff with a clock running; process death; the other tabs turned; the tab layout.
* **screens** (44 steps, everywhere else): the same without the keyboard and the Odds keypad
  round, neither of which changes with the text size.
* **keyboard** (7 steps, on the `soft-kb` profiles): `launch`, `profile`, the keyboard over the
  lowest name field on the Bank and put away (`ime`, `ime-done`), the tab layout, then
  `bank-rename`: a name typed with the keyboard up and the tab switched on the bar that rides above
  it.

`--full` runs every tour step on every profile instead, with the matrix steps added. On profiles
of 600 dp and up it leaves out the steps that assume a phone: the `rail` steps (the density
trick) and the tour's `table-view`, `table-view-resume`, `table-view-exit`, `rotate-to-table` and
`rotate-back` (landscape table view, the turned clock as the table view); `table-view-land` and
`rotate-clock` check the wide rules there instead. The sets can name steps the tour doesn't have
(a step renamed on another branch): the matrix warns once and skips them.

After a failed step the matrix's tour (`PP_TOUR_RECOVER=1`) presses Back if no tabs are on screen,
so a dialog the failure left open doesn't fail every step after it, and if the app is still out
of sight (the launcher, after a failed `process-death`), it wakes the screen, closes the shade and
opens the app again from the launcher, data and all (waiting for it without dragging anything);
the failed step's screenshot is taken first. Never after `launch`, which starts the app itself. The
plain tour doesn't do this.

Each profile starts from the same place: the app stopped and the launcher in front before the
display changes, then the screen woken, the keyguard dismissed and the shade closed. `ui.py launch`
does the last three as well. (The first run after `process-death` passed ended a profile with the
app in front and its clock running, and the next profile's launch failed: the app never came to the
front, the dumps failed, and the steps after it ran blind and opened the quick settings.)
`ui.py`'s dump also no longer falls back to an old dump file when `uiautomator dump` fails, and the
trace in `tour.log` says why a dump failed.

The matrix's own steps are opt-in tour steps in `scripts/device/steps-matrix.sh`:

| Step | Checks |
|---|---|
| `profile` | The app's window is the profile's screen (it relaid out), the font scale took, the orientation is right. Writes `display.env` (size, density, font, insets) for the checks |
| `nav-layout` | Four tabs: a bottom bar below 600 dp, a rail down the left from 600 dp, on the profile as it is |
| `rotate`, `rotate-upright` | On Tools and Bank, `user_rotation` 1, then 3, with the accelerometer off: a phone stays upright, a wide screen turns; the tabs work either way. `rotate`'s screenshot is the Bank turned to 270; `rotate-upright` turns it upright and checks it is the same process |
| `rotate-clock`, `rotate-clock-back` | The running clock turned to 90 (the screenshot), then 270, then upright: on a phone the table view (no tabs), on a wide screen the clock itself in landscape (tabs and all); the same level throughout |
| `table-view-land` | The table-view button: the clock alone, full screen, no tabs, landscape. On `tablet-ignore`: the display does not turn, and the step says whether the table view was letterboxed or fills the upright screen |
| `table-view-close` | ✕: back to the clock, in the profile's orientation (on `tablet-ignore`, in the whole screen again: no letterbox left) |
| `table-view-back` | ✕, then the same with the display turned to 270 (on a phone the turned clock already is the table view) |
| `setup-close` | Closes the setup panel opened over the running clock (the tour's `setup-panel`); the clock runs on |
| `process-death` | PP-093: the app in the background, its process ended with `am force-stop`, opened again from the launcher. The clock must be on the same level with its time still counting (within 4 s of the time that passed, no restart), and every Bank cell (buy-in, rebuy, out, paid) the same as before. Not `am kill` (what low memory does): a running clock keeps a foreground service (the live clock, PP-081), and `am kill` leaves such a process alone. force-stop ends the process, the service and the notification at once, with no chance to save, and the next start is cold (no saved instance state), so the clock and the Bank come only from what the app had already written. It no longer covers a restore into the old task from saved instance state, which the app doesn't rely on for its data |
| `ime`, `ime-done` | The soft keyboard over the lowest name field of a player still in (above the "OUT" rows) on the Bank: the field stays whole above it, below the status bar and clear of the tabs (the bar rides above the keyboard). On the main AVD (a hardware keyboard) `show_ime_with_hard_keyboard` is turned on for the two steps, a second before the tap, and put back after (however the tour ends); the field is tapped once more if no keyboard shows. On a `soft-kb` profile the setting is on throughout and the keyboard must be the full one (a fifth of the screen or more). `ime-done` puts it away |
| `payouts-screen` | The Payouts tab's table adds up (the rail step's check, at any width) |

**Scroll mode.** On a 569 dp-tall screen most of the texts a step asserts are below the fold. The
matrix sets `PP_UI_SCROLL=1`, and `ui.py` then looks for a missing target by dragging the page
(see the docstring in `ui.py`). Assertions count a text seen anywhere on the page and put the page
back; taps stop where the target is. The search goes down to the end and then up past where it
started, so a page a step left part-way down is searched to its top as well. `scroll-to` ignores a
step's small `--max` (set for the default screen): it goes on to that end of the page, then the
other way, by drags rather than swipes (a swipe flings, and a fling can carry a short line, such as
the breaks verdict on the small screen, past the screen between two dumps). Drags rest before lifting, so nothing flings. Only the app's
own scrollers are dragged: with the launcher, the lock screen or the shade in front, a drag down
would open the shade, so nothing is dragged until the app is back. A check that reads a whole list (the
payout table, the chip stack, the Bank's rows) uses `page_dump`, one dump merged from the page's
top to its end, whatever part of the page the step left on screen. The plain tour never sets it
and behaves exactly as before. (A selector for a screen's own
text next to a tab with the same name, such as the Payouts folder tab beside the rail's Payouts,
uses the `in-scroll` token: inside a scroller, a ScrollView or list, even one whose content fits.)

**The keyboard.** The main AVD has a hardware keyboard, so when `show_ime_with_hard_keyboard` is
on, Gboard shows only its toolbar strip (about 48 dp). The `ime` step still proves the app makes
room for a keyboard (the insets) and keeps the focused field above it. For the real thing, the
`soft-kb` profiles run on a second AVD, `pokerpayout_test_softkb`: the same image and screen with
`hw.keyboard=no`, so Gboard comes up full height (about a third of a phone's screen, nearly half
of the small one). `boot.sh` creates it on first use when `PP_AVD_KEYBOARD=soft` is set, which
`matrix.sh` does for these profiles. Both AVDs use the same port (`emulator-5580`), so they never
run side by side: `boot.sh` stops whichever of the two is up before it boots the other, and a
matrix refuses to mix `soft-kb` profiles with the others (`--profiles soft-keyboard` is a run of
its own). These profiles keep `show_ime_with_hard_keyboard` on from their start (so the IME comes
up even if the device reports a keyboard), the `ime` step measures the keyboard once it has
settled (Gboard can show its strip a moment before its keys, and a slow first one gets 10 s more),
and it fails if the keyboard is under a fifth of the screen. `display.env` (and the matrix log, for
these profiles) records what the device says: `keyboard=` from the configuration (`qwerty` or
`nokeys`), `hard_keyboards=` (the input devices with letter keys, from `dumpsys input`) and `ime=`.
`bank-rename` then types with the keyboard up and switches tabs on the bar above it. Typing itself
(`adb shell input text`) works the same with either keyboard.

The first run on GitHub (1.3.11) measured 126 px (420 dpi) and 108 px (360 dpi): both exactly
48 dp, Gboard's strip, on the AVD created with no hardware keyboard. The emulator adds no keyboard
device for `hw.keyboard=no` (its source: no `virtio-keyboard-pci`, no letter keys on the goldfish
events device), so either the first frame was taken before the keys came up, or Gboard still saw a
keyboard. The settle-and-wait and the always-on setting deal with the first; the facts above say
which it was. If the device does report a keyboard, Gboard can't be made to show its keys without
root: the way on would be another image (`default`, with the AOSP keyboard) for this AVD.

### Large screens that ignore orientation requests

On large screens the app's orientation requests are not always heeded. Tablets since Android 12L
may ignore them: the display stays as the user holds it, and an app that asks for a fixed
orientation is letterboxed in it. Newer releases drop the request altogether: the user can set an
app to full screen in its aspect-ratio settings (AOSP has the override from Android 14 QPR1, where
the device turns those settings on), and Android 16 does it by default for apps that target API 36
on screens 600 dp and up. The app's requests then follow the user's own rotation. On a tablet the
app asks for a fixed orientation in one place only (`TournamentOrientation`): the table view (⤢)
asks for landscape.

The opt-in `tablet-ignore` profile emulates this on the API 34 image:

* `adb shell cmd window set-ignore-orientation-request true` (the same as `wm
  set-ignore-orientation-request`). The command exists from Android 12 (API 31; it is in AOSP's
  `android12-release` `WindowManagerShellCommand`, not in `android11-release`), so the API 34
  image has it. The `profile` step checks it took (`get-ignore-orientation-request`).
* Android 16's version, best effort: `adb shell am compat enable OVERRIDE_ANY_ORIENTATION_TO_USER
  <app>`, the compat change behind the full-screen override. It only exists from Android 14 QPR3
  (AOSP `android14-qpr3-release`; not in `android14-release` to `android14-qpr2-release`), so it
  depends on which API 34 build the image is. It is `@Overridable`, so adb may set it on the
  release build too. The matrix log says whether it took, and so do the `profile` and
  `table-view-land` steps. Without it, the table view is letterboxed (the Android 12L to 15
  behaviour), which is still a real case.

On that profile `table-view-land` checks that the display did not turn and the table view shows
whole (the clock line, the timer button, ✕, no tabs), letterboxed or filling the upright screen;
`table-view-close` checks the clock is back in the whole screen. The rest of the smoke set runs as
on `tablet`, and the layout checks read every step. Phones are out of scope: Android 16 only does
this from 600 dp.

### Automatic layout checks

After the profiles run, `layout_check.py` reads every step's UI dump and screenshot. It checks only
the app's own nodes: a dump of the launcher, the share sheet, a permission dialog or the shade is
someone else's layout (on the first GitHub run, the steps after a failed `process-death` dumped the
launcher, and those dumps were checked too). Each failure is printed in the log as well as the
report:

| Check | Severity | Flags | How far to trust it |
|---|---|---|---|
| `offscreen` | **fail** past the screen; warn touching its left or right edge | A text running off-screen | uiautomator clips boxes to what is visible, so a box past the screen is real. Touching the edge is often a horizontal scroller |
| `overlap` | **fail** at half the smaller node; warn above 2 x 2 dp | Two clickable nodes, neither inside the other, overlapping | A half-covered target can't be told apart from its neighbour; small overlaps are often decoration |
| `small-target` | warn | A clickable node under 48 x 48 dp at the profile's density | Compose widens a small target's touch area when nothing is next to it, so the box can be smaller than what a finger hits |
| `text-fit` | warn | A one-line text box (or a text field's text area) narrower than its text at the app's smallest type (9 sp, 0.38 em a letter), or a text box too short for one line of it at the profile's font scale (squeezed out) | A lower bound, so false alarms are rare; it only sees bad cuts |
| `cut-text` | warn | The same text in the same step drawn under 70 % as long as on another profile (the ink in the screenshot, line by line; a field's value line against its own glyph height) | Wrapping doesn't count. A step that leaves a different state on two profiles can trip it |
| `ellipsis` | warn | A text ending in "…" | Compose gives uiautomator the full string even when it draws "…", so this only sees an ellipsis in the copy itself. `text-fit` and `cut-text` find drawn ones |
| `under-bars` | warn | A text under the status or navigation bar (portrait) | An edge-to-edge screen draws there on purpose: on the rail layout a page scrolls on under the gesture handle |

Only the two reliable checks (a text past the screen, a half-covered target) fail a profile; the
rest are warnings with a picture
(`NN-step.checks.png`, the screenshot with each finding boxed: red = fail, orange = warn). Look at
the picture before calling a warning a bug. To accept an intended one, add a rule to
`scripts/device/layout-allow.txt` (`check | step glob | label regex | why`). The pictures,
thumbnails and `cut-text` need Pillow; without it the other checks still run.

### Reading the report

`build/device-reports/matrix-<ts>/` (and `matrix-latest`) holds:

* `index.html`, the contact sheet:
  * the profiles table: result, steps passed, check failures, warnings, time;
  * **the grid**: one row per profile, one column per key screen. Each cell is a thumbnail with a
    green (passed), orange (warnings) or red (failed) border and badges; click it for the full
    screenshot with the findings boxed;
  * failing steps and check failures, with pictures;
  * the distinct warnings, one row per check and node, with the profiles and steps it showed on;
  * every step of every profile.
* `contact-sheet.png`: the grid as one picture, with the same coloured borders (an agent can
  open it with its image viewer, as Claude Code's Read tool does).
* `index.md`: the same as text, for an agent.
* `<profile>/`: that profile's tour (`NN-step.png/.xml`, `steps.tsv`, `checks.json`,
  `display.env`, `tour.log`, `logcat.txt`).

A profile fails if a step fails, the app crashes or ANRs, or a reliable check fails. Read a
failing step's screenshot first. On a small screen a step fails either because the app really
lost something there (the point of the matrix), or because the step assumes a default-size
screen (then fix the step, minimally).

### Time

These times are from 1.3.0 (before M3), when the default run was 8 profiles with shorter step
sets; it took **41 minutes** on this machine (2026-10-06, host load 1 to 6). The focused set on
1.3.4 hasn't been timed in full. In a part-run on 1.3.4, 30 of its steps took 10 minutes on
`small` and 5 on `default`; expect about 40 to 50 minutes for the focused set, and about 80 for
the daily one.

| Profile | Set | Time |
|---|---|---|
| `small` | smoke | 6 min 26 s |
| `small-f1.3` | screens | 6 min 47 s |
| `small-f2.0` | screens | 8 min 56 s |
| `compact` | screens | 3 min 05 s |
| `default` | screens | 2 min 57 s |
| `default-f2.0` | screens | 4 min 13 s |
| `foldable` | screens | 3 min 07 s |
| `tablet` | smoke | 4 min 59 s |

A UI dump costs about 2 s, and a step about 4 to 8 s. Small screens cost more: a missing text is
looked for across the whole page, and a text that really is missing (an app bug) costs a full
search, 30 to 50 s. So the small profiles get faster as their bugs are fixed. Ten profiles
with the earlier, longer step sets took 53 minutes. `--full` (every step, every profile) wasn't
timed; the plain tour alone takes 8 minutes on the default screen, so expect well over an hour.

### The emulator is always put back

The emulator is shared and keeps `wm size` and `wm density` across reboots, so:

* `matrix.sh` resets the size, density, font scale (1.0), rotation (0, accelerometer off), the
  keyboard setting and ignored orientation requests (with `tablet-ignore`'s compat change, if it
  set one) at the start, between profiles, at the end, and on Ctrl-C or any signal (it stops its
  tour first). It prints the display it left behind.
* `tour.sh` puts back whatever its own steps changed (the rail's density, a rotation, the
  keyboard) however it ends: to the profile's values, not the device's.
* If a matrix is killed outright (SIGKILL), the next `boot.sh` (every tour runs it) resets a
  leftover size or density override, and a display left ignoring orientation requests. (A leftover
  `OVERRIDE_ANY_ORIENTATION_TO_USER` does nothing on a display that heeds them.)
* After a `soft-kb` run without `--stop`, the soft-keyboard AVD is the one up; the next tour's
  `boot.sh` stops it and boots the main one.

### Adding a profile or a rotation case

* **A profile:** add a line to `PROFILES` in `matrix.sh` (name, `WxH`, dpi, font, rotation,
  turns, step set, device, description). The device column is `-`, `soft-kb` (the soft-keyboard
  AVD) or `ignore-orient` (orientation requests ignored); the steps read it as
  `PP_PROFILE_DEVICE`. To run it by default, add its name to `DEFAULT_PROFILES`, and keep an eye on
  the run's time.
* **A rotated profile:** set its rotation to 1 (or 3). `turns` says whether the app's ordinary
  screens turn with the display there: `yes` from 600 dp (PP-088), `no` on phones, where only the
  running clock turns (into the table view). The `profile`, `rotate`, `table-view-*` and
  `rotate-clock` steps read it (and the smallest width) to know what to expect.
* **A rotation step:** use the helpers in `steps-matrix.sh`: `rotate_to land|port|seascape`,
  `require_orientation land|port`, `orientation_at <rotation>` (ordinary screens on this profile)
  and `wide_screen` (smallest width 600 dp and up). A step that rotates leaves a mark
  (`$OUT/.rotation`, "accelerometer user", the same mark the tour's `rotate-to-table` uses), so
  the rotation comes back however the tour ends.
* Every `uiautomator dump` re-freezes the rotation at the display's current one, so under a
  portrait-only screen it would turn a user rotation of 90 back to 0. `rotate_to` (and a rotated
  profile) sets `PP_UI_HOLD_ROTATION`, and `ui.py` puts the rotation back after each dump;
  `require_user_rotation N` checks it held.
* Register the step with `extra_step` (a name and function the tour doesn't use: the tour dies
  on a name registered twice) and add it to `SMOKE_STEPS` (and `SCREENS_STEPS` to run it on every
  profile).

## 11. Chaos: the monkey

`adb shell monkey` taps, swipes, rotates and presses keys at random, as fast as a person never
would, to find the crash nobody walked into. `scripts/device/monkey.sh` boots the test AVD,
installs the build and runs the monkey once per seed against the app's package only (`-p`: other
apps' screens, such as a share sheet, are refused). Home, the notification shade and End-call are
switched off for the run (quick settings could start a screen recording; End-call would put the
screen to sleep) and put back afterwards, as is the rotation.

```bash
flock /tmp/pokerpayout-emulator.lock scripts/device/monkey.sh --stop          # default seeds, 4,000 events each
scripts/device/monkey.sh --no-build --seeds 1204 --events 4000              # replay one seed, event for event
gh workflow run monkey.yml --ref <branch> -f seeds=1,2,3 -f events=10000     # on GitHub's emulator
```

* **Seeds** are fixed and logged (default `20261008,1204,35`). Each seed starts from cleared data
  with notifications allowed, so it replays on its own.
* **Events**: touches 45%, swipes 20%, rotations 5%, arrow keys 5%, Menu and DPad-centre 10%,
  system keys 5% (Back and the volume keys while Home is off), relaunching the app 5%, any other
  key 5%; 75 ms apart.
* **Fails** on a crash or an ANR in the app: the monkey's own `// CRASH:` or `// NOT RESPONDING:`
  report, or, as a backstop, the app's FATAL EXCEPTION or ANR in logcat. A crash in another app
  (a system app on the Play image) ends that seed early and is noted, not failed.
* **Report**: `build/device-reports/monkey-<timestamp>/summary.md` (a row per seed: events
  injected, time, verdict), `seed-<n>.txt` (the monkey's log, every event), `logcat-<n>.txt`, and
  `crash-<n>.png` (the screen when it failed). GitHub uploads them and shows the summary on the run.
* `monkey.yml` runs on every pull request (not a required check) and by hand.

## 12. Mutation testing (PIT)

Mutation testing plants small bugs in the code (a `<` turned into `<=`, a `+` into a `-`, a
return value replaced) and counts how many the tests catch. A surviving mutant is a line the tests
run but don't check. `mutation.yml` runs [PIT](https://pitest.org) on GitHub, by hand, on
core's maths (`core.domain`, `core.utils`) with the plain JVM tests (Robolectric ones are slow to
fork and add nothing for pure code):

```bash
gh workflow run mutation.yml --ref <branch>
./gradlew --init-script scripts/dev/pitest.init.gradle :core:pitestDebug    # the same, locally (heavy)
```

The plugin (`pl.droidsonroids.pitest`) is applied only by `scripts/dev/pitest.init.gradle`, never
by the build, so the app, its dependencies and F-Droid's reproducible build don't change. Kotlin's
generated `equals`, `hashCode`, `toString`, `componentN` and `copy` aren't mutated. The run's
summary shows the score per class; the HTML report (every surviving mutant, line by line) is an
artifact. Report-only: not a gate.

First run (wave 9): 1,761 of 2,229 mutants killed (79%), 87% of the 2,032 the tests reach; about
16 minutes. Highest: the settlement and bounty maths (`BountyLedger` 56 of 60,
`CalculatePayoutsUseCase` 31 of 34). Lowest: `BlindLadderSearch` (39 survivors, mostly in the
"nice value" scoring) and `BlindSetupAdvisor` (20). `NightCodec` and `CashGame` show no coverage
only because their tests run under Robolectric, which this run leaves out.
