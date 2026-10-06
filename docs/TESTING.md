# Testing Poker Payout

Everything here runs headless on a Linux workstation: no phone, no emulator window, no
sound. It is written so that a person *or* an AI agent can run it unattended.

| Tier | Command | Needs | Typical time |
|------|---------|-------|--------------|
| JVM unit tests | `./gradlew testDebugUnitTest` | JDK 21 | ~15 s warm (394 tests) |
| Device smoke tour (screenshots + UI dumps + logcat) | `scripts/device/tour.sh` | emulator (auto-booted) | ~6 min incl. build |
| Instrumented tests | `./gradlew connectedDebugAndroidTest` | running emulator | compiles; there are 0 instrumented tests (see below) |
| JVM screenshot goldens + layout checks (Roborazzi, section 9) | part of `./gradlew testDebugUnitTest`; re-record with `./gradlew recordRoborazziDebug` | JDK 21 | ~20 s for core's 140 goldens and 48 matrix checks |
| Device matrix: the real app on 10 screen sizes, fonts and rotations (section 10) | `scripts/device/matrix.sh` | emulator (auto-booted) | ~41 min (8 profiles) |

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

Environment knobs: `ANDROID_HOME`, `PP_AVD` (default `pokerpayout_test`), `PP_EMU_PORT`
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

1. **Tournament** (30 steps, five of them on the Payouts tab). The tab is one setup page (S1 v2) that folds into the clock on Start.
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
   * Start: the setup folds into the running clock (S2) with its strip. -1 must take about a
     minute off the level's time left and +1 give it back (read from the digits). Skip to level
     2; at level 4 require "Next · Break" and the break with its note in the schedule; skip into
     the break (S4: "Break · back at Level 5", Last rebuy); pause.
   * Table view: the table-view button forces a landscape screen on the paused break; resume
     there (the footer shows "10 of 10 left" and the pool); leave it and require portrait again.
     End break now starts level 5.
   * Rotation (PP-079): with auto-rotate off, `settings put system user_rotation 1` turns the
     emulator on its side: the clock must become the table view, landscape, still on level 5;
     `user_rotation 0` brings the clock back upright. The step puts the settings back however it
     ends.
   * One turn only (PP-094 #2): on its side, ✕ must show the clock upright while the user rotation
     stays 1; `user_rotation 0` (upright) and then 1 again (on its side) must bring the table view
     back. The step takes no UI dump between the ✕ and the second turn, since every uiautomator dump
     sets the user rotation back to the display's, which to the app is the phone turned upright.
   * The strip opens setup over the running clock, money and blinds locked; "Unlock to edit…"
     asks in a sheet first, then opens them; closing locks them again.
   * New tournament… (the menu) asks first; the reset unfolds setup at LEVEL 1 · READY, 20:00,
     50 / 100.
2. **Bank** (21 steps). Set the rebuy amount to $10. The labelled header (Player, Buy-in,
   Rebuy, Out, Paid) and the top bar. Rename Player 1 to Alice with no Enter, switch tabs and
   come back: the name must survive. A buy-in in one tap, with "Alice paid the buy-in" and UNDO
   on the snackbar; UNDO must take it back. A rebuy in one tap. Knock out Player 2 from the
   knockout sheet ("5TH PLACE"; Alice picked, applied with no second dialog): the 5th badge must
   sit in the Out column, clear of the name. Three more out with nobody credited: Alice is the
   champion; her pay-out sheet; Mark paid. The pool breakdown and payout structure sheets; scroll.
   Then clear the Rebuy amount and retype 15 by switching tabs: the recorded rebuy must survive
   (the keyboard is up when the tab is tapped). Leave the field empty: "Turn rebuys off?" must ask
   first, and Keep must bring back the $15 and the rebuy. Then the cutoff (PP-030): "rebuys until
   level 1" with the clock in level 2 must lock the Rebuy column, a tap must record nothing, and
   the note under the list must say why; then reset the tournament. The cutoff is set with setup's "Rebuys
   until" field, on both builds.
3. **Payouts tab** (3 steps). The finished night: Alice and Player 5 by name in their rows,
   adding up to the prize pool; the structure sheet; Back returns to Tournament (B16: Back no
   longer walks through every tab tapped).
4. **Tools** (4 steps). The tool list and the Sound section (S7); turn the sound off (the
   volume and Test chime rest) and on again, and play the test chime; Hand ranks (S12), with a
   back arrow, the Tools tab still selected, "1 in 30,940" for a royal flush and a kicker.
5. **Odds.** Empty state; card picker; AsKs vs QhQd; a JsTs2c flop (the picker scrolls to
   find 2c); calculate and require the exact answer, **56.06%** under Player 1 and **43.94%**
   under Player 2 (555 and 435 of 990 runouts; v1.1.12 showed about 49.25 / 50.75 because of
   the kicker-order bug); add the 9h turn and require the old numbers to disappear; switch to
   4 players; reset.
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
9. Check that the app process is still alive.

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

### Touring the release build

The release build is shrunk and obfuscated by R8, so a missing keep rule only shows up
there. After changing `app/proguard-rules.pro`, a dependency, or anything loaded by
reflection (Hilt, navigation routes, Room, `@Parcelize`), tour the release APK:

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
| core | 139 | 0 | Payouts and settlement: presets, rounding (the rows always add up to the pool), standings, 2,000 seeded random tournaments that must conserve money exactly, bounties nobody claimed going to the champion. Money in cents and the money parser. Blind engine: 6,600-config property sweep (every accepted ladder in the 1.3x-2.0x band) plus exact ladders, setup advice whose every offered fix works, color-ups. Chip optimizer: reported crashes, typed failures, a 115,500-call input sweep and a brute-force oracle. FormatUtils. |
| bank-feature | 44 | 0 | BankViewModel money flows on real prefs: buy-ins, rebuys, knockouts, money conservation over 14 configs and 60 seeded random sessions, live totals when the Tournament settings change, purchases surviving a cleared-and-retyped amount, weights, reset. |
| tools-feature | 139 | 1 | Odds: 100 golden hand-ranking and equity tests, exhaustive 5- and 7-card evaluator checks, the engine (exact, Monte Carlo, cancellation) and its ViewModel. Chip calculator ViewModel. `OddsBenchmark` is skipped unless `ODDS_BENCH=1`. |
| tournament-feature | 358 | 0 | TournamentConfigViewModel (rebuy/add-on edits that can't wipe purchases, presets, paid places capped at the player count), the Float-to-cents preference migration. The clock: TimerViewModel on virtual time with a fake monotonic clock (late ticks, sleep gaps, process death mid-level and mid-overtime, reboot, v1.1 migration, chimes including the end chime after a resume, breaks, ante, write cadence, table numbers) and the break/overtime timeline. `BreakMessageFieldTest` is a Robolectric Compose UI test: hardware Enter in the break note must not click Reset. The Tournament tab (M3): `TimerViewModelControlsTest` (the one-minute nudges, End break now, color-up done, next break, projected end, rebuy state, mid-game blind changes that keep the level), `TournamentModeTest` (the setup/fold/clock/panel state machine and where the phone may turn), and the Robolectric screen tests in section 9. |

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
* **Goldens are stored for 10 of them** (`DeviceMatrix.goldens`): every device at 1.0, `phone`
  at 1.3, and `small` and `phone` at 2.0. The assertions cover the rest without storing
  pictures.

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
  accepts Compose's expansion of a smaller node, as long as no neighbour is in the way.
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
`captureGolden` only where `config in DeviceMatrix.goldens`. `forEachScrollPosition { }` scrolls
every scrolling container a page at a time, for `assertVisibleTextUnclipped`.

| Module | Class | Goldens (`src/test/screenshots/screens/`) | Layout checks |
|---|---|---|---|
| `tools-feature` | `ToolsTabScreenTest` | `S7_tools_default`, `S7_tools_muted` | S7: all three, at every scroll position |
| `tools-feature` | `HandRanksScreenTest` | `S12_ranks_default`, `S12_ranks_4colour` | All three, at every scroll position |
| `tools-feature` | `ChipSetScreenTest` | `S11_chipset_ok`, `S11_chipset_short`, `S11_chipset_ok_end`, `S11_chipset_settings` (the stack settings unfolded, keeping back the Tournament's estimate) | All three, at every scroll position of each pane; also the unfolded stack settings and the colour sheet |
| `tournament-feature` | `TournamentTabsScreenTest` | `Shell_tournament` | Tournament: touch targets |
| `tournament-feature` | `TournamentScreenGoldenTest` | `S1_setup_{before,invalid}`, `S1_fold_300ms`, `S1_running_strip`, `S1_panel_{open,unlocked}`, `S2_clock_{ready,running,paused,final_minutes,overtime,finished}`, `S2_clock_running_font2x`, `S3_table_{running,paused,break}` (landscape cells), `S4_break_{colorup,done,plain}`, `S4_break_chipset` (the color-up with a chip set set up in Tools, PP-091 #9), `Z1_clock_small`, `Z3_table_small_land`, `Z4_clock_tablet` | All three checks on all 24 cells (the fold frame: none, it is mid-animation) |
| `tournament-feature` | `TournamentInteractionTest`, `TournamentRotationTest`, `SetupFoldTest` | none | What each control sends; rotation per device class (Robolectric `+land` shows the table view, `+port` the clock, other tabs portrait on phones, tablets free, state kept through recreation; ✕ in a turned table view holds for that turn only, with the phone's hold faked through `LocalPhoneHold`); the fold plays once and is cut under Reduce motion |
| `tournament-feature` | `PayoutsTabScreenTest` | `S6_payouts_{standard,topheavy,custom,finished}`, `S6_payouts_font2x` | All three, at every scroll position, and locked while the clock runs |
| `bank-feature` | `BankScreensTest` | `S5_bank_{before_buyins,midgame,rebuys_open,no_rebuys,champion,30players}`, `S5_bank_font2x`, `S5b_knockout_sheet`, `S5b_count_sheet`, `S5c_payout_{champion,second}`, `S5c_pool_breakdown`, `Z2_bank_small`, `Z5_bank_tablet` | All three, at every scroll position. A sheet is rendered as its content over the screen behind, since a modal window doesn't capture under Robolectric |

The screens' ViewModels are the real ones over Robolectric's in-memory preferences, set up as the
mockups' game (9 players, $40 buy-in, and so on), so a golden shows what the app shows.

### The tests in core today

| Class | Runs | What |
|---|---|---|
| `ComponentGoldenTest` | 17 galleries x 10 goldens = 170 | Each component's `@Preview` gallery (including `PokerNavRail`), plus `Shell`, the real `PokerAppShell` around a sample screen |
| `ComponentLayoutTest` | 2 x 24 cells = 48 | Every gallery in one scrolling column, and the shell, through all three layout checks |
| `AppShellTest` | 4 x 24 cells = 96 | The bar below 600 dp and the rail from 600 dp, by window width; tab geometry; the screen capped at 720 dp and centred; the shell through all three layout checks |
| `NavBarTest` | 10 | Every screen's tab (tools keep Tools selected, B16); tab taps don't pile up on the back stack and Back returns to Tournament; tapping a tab inside a tool returns to its list |
| `ComponentSemanticsTest` | 11 | TalkBack: roles (checkbox, radio button, tab, button), names ("Ace of spades", "Rebuy, 1 taken, Locked"), headings, field errors; taps; the stepper's ends and repeat-while-held |
| `DesignTokensTest` | 6 | Every contrast pairing in the design spec, computed; the six original colours and the sunset ones unchanged |
| `TypographyTest` | 5 | Barlow loads; `tnum` makes every digit the same width (and without it they differ); the licence ships |
| `UndoSnackbarTest` | 4 | Undo inside the 8 s window counts, after it doesn't (virtual time) |
| `MoneyComponentsTest` | 3 x 24 cells = 72 | `MoneyMeter`, `PlaceBadge` and `PayoutStructureSheet`: goldens on the 10, all three layout checks on all 24 |
| `LayoutAssertionsTest` | 11 | The checks themselves catch what they claim |
| `ScreenOrientationTest` | 3 | Phones portrait unless the screen on show asks for more, and portrait again when it goes; free from 600 dp; a screen can take the full width beside the rail, or the whole window |

## 10. The device matrix: real screens, sizes, fonts and rotation

Section 9 renders composables on the JVM. The device matrix (PP-078 layer 2) runs the **real
app on the real emulator** at several screen sizes, densities, font scales and rotations. It
catches what Robolectric can't: system bars and insets, the soft keyboard, real rotation and the
activity being recreated, gestures and scrolling, and the process staying alive through it all.

```bash
flock /tmp/pokerpayout-emulator.lock scripts/device/matrix.sh --stop   # build, install, the 8 default profiles
scripts/device/matrix.sh --no-build                    # reuse the last APK
scripts/device/matrix.sh --profiles small,tablet       # some profiles (--list shows them; all = every one)
scripts/device/matrix.sh --steps bank,ime,ime-done     # your own steps (launch and profile come first)
scripts/device/matrix.sh --full                        # every tour step on every profile
scripts/device/matrix.sh --release                     # the R8 build
```

Run it under the emulator lock like the tour, and don't edit `matrix.sh` while it runs (bash
reads a running script as it goes).

### Profiles

There is one AVD (`pokerpayout_test`, 1080 x 2400 @ 420 dpi). Each profile overrides its screen
with `adb shell wm size` and `wm density`, and sets `font_scale` and `user_rotation`. That takes
seconds, needs no extra AVDs or system images, and the app really relays out: the `profile`
step checks that the app's window is exactly the overridden screen.

| Profile | Screen | dp | Font | Set | Why |
|---|---|---|---|---|---|
| `small` | 720 x 1280 @ 360 | 320 x 569 | 1.0 | smoke | The smallest phone we support; most screens run below the fold |
| `small-f1.3` | 720 x 1280 @ 360 | 320 x 569 | 1.3 | screens | Large text on it |
| `small-f2.0` | 720 x 1280 @ 360 | 320 x 569 | 2.0 | screens | The worst case: the largest text on the smallest screen |
| `compact` | 1080 x 2400 @ 480 | 360 x 800 | 1.0 | screens | The commonest Android phone width (the mockups' frame) |
| `default` | the emulator's own | 411 x 914 | 1.0 | screens | Pixel 7 class; the plain tour covers it step by step |
| `default-f2.0` | the emulator's own | 411 x 914 | 2.0 | screens | The largest text on a common phone |
| `foldable` | 1768 x 2208 @ 420 | 673 x 841 | 1.0 | screens | A foldable opened flat: the rail, a nearly square window |
| `tablet` | 1600 x 2560 @ 320 | 800 x 1280 | 1.0 | smoke | A 10-inch tablet held upright: the rail, the 720 dp content cap |
| `default-f1.3` | the emulator's own | 411 x 914 | 1.3 | screens | Not in the default run: it found nothing `default` and `default-f2.0` don't |
| `large` | 1440 x 3120 @ 560 | 411 x 891 | 1.0 | screens | Not in the default run: the same dp as `default` at 3.5x, and it found nothing more |
| `tablet-land` | 1600 x 2560 @ 320, turned 90 | 800 x 1280 | 1.0 | smoke | Not in the default run: the tablet turned. Today the app keeps it upright, so it is `tablet` again |

The emulator scales any override onto its panel, so sizes bigger than 1080 x 2400 work too.
Screenshots come out at the profile's own size. SystemUI forgets its demo mode when the size
changes, so the matrix sends it again (the clock stays at 12:00).

### What runs on each profile

Each profile runs one of two step sets (`--list` prints them):

* **smoke** (31 steps, on `small` and `tablet`): launch, the configuration and a rebuy amount,
  Payouts (the folder tab), Blinds and the smallest chip, the collapsed clock; Bank, a rebuy, the
  soft keyboard, the pool summary dialog, the Payouts tab, Tools, Hand ranks, Odds through to the
  exact results, Chip set and its breakdown; back to Tournament, start the clock, the table view
  (also from a display turned to 270), rotation, the tab layout, and the process still alive.
* **screens** (25 steps, everywhere else): the same screens and dialogs without the keyboard, the
  Odds keypad round and the turned table view, none of which change with the text size.

`--full` runs every tour step on every profile instead, with the matrix steps added, and the
`rail` steps (the density trick) on profiles under 600 dp. The sets can name steps the tour
doesn't have (a step renamed on another branch, like the chip set's): the matrix warns once and
skips them.

After a failed step the matrix's tour (`PP_TOUR_RECOVER=1`) presses Back if no tabs are on screen,
so a dialog the failure left open doesn't fail every step after it; the failed step's
screenshot is taken first. The plain tour doesn't do this.

The matrix's own steps are opt-in tour steps in `scripts/device/steps-matrix.sh`:

| Step | Checks |
|---|---|
| `profile` | The app's window is the profile's screen (it relaid out), the font scale took, the orientation is right. Writes `display.env` (size, density, font, insets) for the checks |
| `nav-layout` | Four tabs: a bottom bar below 600 dp, a rail down the left from 600 dp, on the profile as it is |
| `rotate` | `user_rotation` 1, then 3, with the accelerometer off: the app stays upright (portrait-only), its tabs still work while the display is turned, and it is the same process |
| `table-view-land` | The table view opens landscape on any profile (its screenshot is the landscape clock) |
| `table-view-back` | Leaving it returns to portrait; again with the display turned to 270 |
| `table-view-close` | Leaving it returns to portrait (the screens set) |
| `ime`, `ime-done` | The soft keyboard over the lowest name field on the Bank: the field stays above it |
| `payouts-screen` | The Payouts tab's table adds up (the rail step's check, at any width) |

**Scroll mode.** On a 569 dp-tall screen most of the texts a step asserts are below the fold. The
matrix sets `PP_UI_SCROLL=1`, and `ui.py` then looks for a missing target by dragging the page
(see the docstring in `ui.py`). Assertions count a text seen anywhere on the page and put the page
back; taps stop where the target is. Drags rest before lifting, so nothing flings. A check that
reads a whole list (the payout table) uses `page_dump`, one dump merged from the page's top to its
end. The plain tour never sets it and behaves exactly as before. (A selector for a screen's own
text next to a tab with the same name, such as the Payouts folder tab beside the rail's Payouts,
uses the `in-scroll` token: inside a scroller, a ScrollView or list, even one whose content fits.)

**The keyboard.** The AVD has a hardware keyboard (the tour types through it), so when
`show_ime_with_hard_keyboard` is on, Gboard shows only its toolbar strip (about 48 dp). The `ime`
step still proves the app resizes for a keyboard (the insets) and keeps the focused field above
it. A full-height keyboard would need an AVD with `hw.keyboard=no`.

### Automatic layout checks

After the profiles run, `layout_check.py` reads every step's UI dump and screenshot:

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

Measured on this machine (2026-10-06, host load 1 to 6), the default run took **41 minutes**:

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

* `matrix.sh` resets the size, density, font scale (1.0), rotation (0, accelerometer off) and the
  keyboard setting at the start, between profiles, at the end, and on Ctrl-C or any signal (it
  stops its tour first). It prints the display it left behind.
* `tour.sh` puts back whatever its own steps changed (the rail's density, a rotation, the
  keyboard) however it ends: to the profile's values, not the device's.
* If a matrix is killed outright (SIGKILL), the next `boot.sh` (every tour runs it) resets a
  leftover size or density override.

### Adding a profile or a rotation case

* **A profile:** add a line to `PROFILES` in `matrix.sh` (name, `WxH`, dpi, font, rotation,
  turns, step set, description). To run it by default, add its name to `DEFAULT_PROFILES`, and
  keep an eye on the run's time.
* **A rotated profile:** set its rotation to 1 (or 3). `turns` says whether the app's screens
  turn with the display there: `no` today, because the app is portrait-only. Once PP-088 lets
  tablets rotate, set `turns` to `yes` on `tablet-land` and add it to `DEFAULT_PROFILES`. The
  `profile`, `rotate` and `table-view-back` steps then expect landscape there.
* **A rotation step** (for example once the Clock batch rotates the clock): use the helpers in
  `steps-matrix.sh`: `rotate_to land|port|seascape`, `require_orientation land|port` and
  `orientation_at <rotation>`. A step that rotates leaves a mark, so the rotation comes back
  however the tour ends. Note: every `uiautomator dump` re-freezes the rotation at the display's
  current one, so under a portrait-only screen it would turn a user rotation of 90 back to 0;
  `rotate_to` (and a rotated profile) sets `PP_UI_HOLD_ROTATION`, and `ui.py` puts the rotation
  back after each dump. `require_user_rotation N` checks it held. Register it with `extra_step` and add it to `SMOKE_STEPS` (and
  `SCREENS_STEPS` if it should run on every profile).
