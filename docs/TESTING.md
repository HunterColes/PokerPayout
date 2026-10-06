# Testing Poker Payout

Everything here runs headless on a Linux workstation: no phone, no emulator window, no
sound. It is written so that a person *or* an AI agent can run it unattended.

| Tier | Command | Needs | Typical time |
|------|---------|-------|--------------|
| JVM unit tests | `./gradlew testDebugUnitTest` | JDK 21 | ~15 s warm (394 tests) |
| Device smoke tour (screenshots + UI dumps + logcat) | `scripts/device/tour.sh` | emulator (auto-booted) | ~6 min incl. build |
| Instrumented tests | `./gradlew connectedDebugAndroidTest` | running emulator | compiles; there are 0 instrumented tests (see below) |
| JVM screenshot goldens + layout checks (Roborazzi, section 9) | part of `./gradlew testDebugUnitTest`; re-record with `./gradlew recordRoborazziDebug` | JDK 21 | ~20 s for core's 140 goldens and 48 matrix checks |

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
| `install.sh [--release] [--no-build] [--clear]` | `./gradlew :app:assembleDebug` (or `assembleRelease`), then `adb install -r`. If the signatures don't match, it uninstalls and installs again. |
| `shot.sh <name> [outdir] [--ui]` | `adb exec-out screencap -p` to `<outdir>/<name>.png` (default `build/device/shots`), checks the PNG signature, and also writes the UI dump when given `--ui`. |
| `ui.py` | Stdlib-only UI driver over `uiautomator dump` + `adb shell input` (see below). |
| `tour.sh [--release] [--no-build] [--keep-going] [--stop]` | The one-command smoke tour (section 5). `--release` passes `--release` to `install.sh`. |

Deterministic device settings applied by `boot.sh`:

* all three animation scales set to 0
* en-US locale (verified; changing it needs root), UTC timezone, 24 h clock
* show-touches and pointer-location off
* screen always on, keyguard dismissed
* rotation locked to portrait, font scale 1.0
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

1. **Tournament** (22 steps).
   * Launch. Type the buy-in 12.50 one key at a time (v1.1.12 turned it into 120.5), bounty 5,
     and move the players slider to 11.
   * **Payouts tab (S6):** one row per place paid, adding up to the prize pool in the top bar
     and to "Adds up to" under the rows, to the cent, and never increasing down the table. Then
     the Top-heavy preset, whose "what 1st gets" preview must be what the 1st row pays; $5
     rounding on the page, where every place below 1st must be a whole $5; the payout structure
     sheet; and Share, which must open the system share sheet with the payouts in it.
   * **Blinds tab:**
     * The smallest-chip menu must offer real chips (1, 5, 10, 25, 50, 100, 250 ...); pick 25.
     * Type 25-minute rounds and require the reason ("doesn't divide") and the "Use 20-min
       rounds (9 levels)" fix, then apply it.
     * Breaks every 4 levels with the note "Last rebuy"; the verdict must say "2 breaks · ends
       at 3:20". Enter in the note must leave the field, not open Reset.
   * Collapse the config. Before the start the clock must be labelled: LEVEL 1, READY, LEVEL TIME
     LEFT, 20:00, BLINDS 25 / 50, "Next: 50 / 100".
   * Start; skip to level 2; at level 4 require "Next: Break · 10 min" and the break in the
     schedule; skip into the break (BREAK, BREAK TIME LEFT, Last rebuy, "Next: Level 5 · ...");
     pause.
   * Table view: require a landscape screen on the paused break, then resume and skip to
     level 5 from the table view's own controls (still landscape); leave it and require
     portrait again.
   * Reset: the clock is back at LEVEL 1, 20:00, 50 / 100.
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
   the note under the list must say why; then reset the tournament. Until setup has its "Rebuys
   until" field (M3), the tour writes the preference with `run-as`, so on a release build these
   two steps only check that the column is open with no cutoff.
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
   10 green 25s for 5 players"; reset applies at once with Undo; the stack settings keep 2
   stacks back and the color-up plan counts 7 stacks in play.
7. Back to Tournament.
8. **Rail** (4 steps). `wm density 240` makes the phone's window 720 dp wide: the tabs must
   move to a rail down the left edge (PP-087), with the screen recreated where it was; Tools and
   Payouts on the rail; then `wm density reset` brings the bottom bar back with the tab kept. The
   tour resets the density however it ends, since the emulator keeps it across reboots.
9. Check that the app process is still alive.

The app is locked to portrait (`android:screenOrientation` on `MainActivity` in `core`'s
manifest), so there is no landscape step apart from the table view's own rotation; freeing
rotation (and tablets' landscape) is M3's.

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
that UI dump for the step's `.xml`, which saves a second dump.

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
| tournament-feature | 72 | 0 | TournamentConfigViewModel (rebuy/add-on edits that can't wipe purchases, presets, paid places capped at the player count), the Float-to-cents preference migration. The clock: TimerViewModel on virtual time with a fake monotonic clock (late ticks, sleep gaps, process death mid-level and mid-overtime, reboot, v1.1 migration, chimes including the end chime after a resume, breaks, ante, write cadence, table numbers) and the break/overtime timeline. `BreakMessageFieldTest` is a Robolectric Compose UI test: hardware Enter in the break note must not click Reset. |

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
| `tools-feature` | `ChipSetScreenTest` | `S11_chipset_ok`, `S11_chipset_short`, `S11_chipset_ok_end` | All three, at every scroll position of each pane; also the unfolded stack settings and the colour sheet |
| `tournament-feature` | `TournamentTabsScreenTest` | `Shell_tournament` | Tournament (M3 restyles the body): touch targets |
| `tournament-feature` | `PayoutsTabScreenTest` | `S6_payouts_{standard,topheavy,custom,finished}`, `S6_payouts_font2x` | All three, at every scroll position, and locked while the clock runs |
| `bank-feature` | `BankScreensTest` | `S5_bank_{before_buyins,midgame,rebuys_open,no_rebuys,champion,30players}`, `S5_bank_font2x`, `S5b_knockout_sheet`, `S5b_count_sheet`, `S5c_payout_{champion,second}`, `S5c_pool_breakdown`, `Z2_bank_small`, `Z5_bank_tablet` | All three, at every scroll position. A sheet is rendered as its content over the screen behind, since a modal window doesn't capture under Robolectric |

The screens' ViewModels are the real ones over Robolectric's in-memory preferences, set up as the
mockups' game (9 players, $40 buy-in, and so on), so a golden shows what the app shows.

### The tests in core today

| Class | Runs | What |
|---|---|---|
| `ComponentGoldenTest` | 15 galleries x 10 goldens = 150 | Each component's `@Preview` gallery (including `PokerNavRail`), plus `Shell`, the real `PokerAppShell` around a sample screen |
| `ComponentLayoutTest` | 2 x 24 cells = 48 | Every gallery in one scrolling column, and the shell, through all three layout checks |
| `AppShellTest` | 4 x 24 cells = 96 | The bar below 600 dp and the rail from 600 dp, by window width; tab geometry; the screen capped at 720 dp and centred; the shell through all three layout checks |
| `NavBarTest` | 10 | Every screen's tab (tools keep Tools selected, B16); tab taps don't pile up on the back stack and Back returns to Tournament; tapping a tab inside a tool returns to its list |
| `ComponentSemanticsTest` | 11 | TalkBack: roles (checkbox, radio button, tab, button), names ("Ace of spades", "Rebuy, 1 taken, Locked"), headings, field errors; taps; the stepper's ends and repeat-while-held |
| `DesignTokensTest` | 6 | Every contrast pairing in the design spec, computed; the six original colours and the sunset ones unchanged |
| `TypographyTest` | 5 | Barlow loads; `tnum` makes every digit the same width (and without it they differ); the licence ships |
| `UndoSnackbarTest` | 4 | Undo inside the 8 s window counts, after it doesn't (virtual time) |
| `MoneyComponentsTest` | 3 x 24 cells = 72 | `MoneyMeter`, `PlaceBadge` and `PayoutStructureSheet`: goldens on the 10, all three layout checks on all 24 |
| `LayoutAssertionsTest` | 11 | The checks themselves catch what they claim |
