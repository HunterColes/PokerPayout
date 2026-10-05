# Testing Poker Payout

Everything here runs headless on a Linux workstation: no phone, no emulator window, no
sound. It is written so that a person *or* an AI agent can run it unattended.

| Tier | Command | Needs | Typical time |
|------|---------|-------|--------------|
| JVM unit tests | `./gradlew testDebugUnitTest` | JDK 21 | ~10 s warm (284 tests) |
| Device smoke tour (screenshots + UI dumps + logcat) | `scripts/device/tour.sh` | emulator (auto-booted) | ~6 min incl. build |
| Instrumented tests | `./gradlew connectedDebugAndroidTest` | running emulator | compiles; there are 0 instrumented tests (see below) |
| JVM screenshot tests (Roborazzi) | not set up yet | n/a | n/a |

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
[tour] PASS: 37/37 steps passed, 0 fatal, 0 ANR, 471s total
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
bash function of `ui.py` calls that ends in assertions. The steps are:

1. **Tournament.** Launch; enter buy-in 25 and bounty 5; move the players slider; open the
   Blinds tab; collapse the config; start the timer; skip a level; pause; open the reset
   dialog and confirm it.
2. **Bank.** Rename Player 1 to Alice and commit with Enter; confirm a buy-in (the dialog
   must say "Alice has paid"); knock out Player 2; open the payout-weights editor; open the
   pool-summary dialog; scroll.
3. **Tools.** Open the grid and the Settings tile (volume dialog).
4. **Odds.** Empty state; card picker; AsKs vs QhQd; a JsTs2c flop (the picker scrolls to
   find 2c); calculate and require the exact answer, **56.06%** under Player 1 and **43.94%**
   under Player 2 (555 and 435 of 990 runouts; v1.1.12 showed about 49.25 / 50.75 because of
   the kicker-order bug); add the 9h turn and require the old numbers to disappear; switch to
   4 players; reset.
5. **Hand rankings, then the chip calculator.** In the chip calculator: Generate and require
   Total Chips to be non-zero and equal to the sum of the "× N" rows (26 for the defaults;
   v1.1.12 showed 0), and Denominations to equal the number of rows; open the advanced
   settings; scroll.
6. Back to Tournament, then check that the app process is still alive.

After every step, the tour fails it if logcat's crash buffer has a `FATAL EXCEPTION` for the
app or if an `ANR in com.huntercoles.pokerpayout` appears. Use `--keep-going` to run all
steps even after a failure.

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

Last measured (v1.2.0, batch A integrated): **284 tests, 275 pass, 9 skipped, 0 fail**, about
10 s with compilation up to date. The slowest classes are `HandEvaluatorTest` (all 133,784,560
seven-card hands, about 4 s) and `ChipDistributionOptimizerTest` (a 115,500-call input sweep,
about 2 s).

| Module | Tests | Skipped | What they cover |
|---|---|---|---|
| core | 83 | 3 | Blind engine: 6,600-config property sweep plus exact ladders. Chip optimizer: reported crashes, typed failures, a 115,500-call input sweep and a brute-force oracle. FormatUtils. |
| bank-feature | 36 | 2 | BankViewModel money flows on real prefs: buy-ins, rebuys, knockouts, money conservation over 14 configs, weights, reset. |
| tools-feature | 139 | 1 | Odds: 100 golden hand-ranking and equity tests, exhaustive 5- and 7-card evaluator checks, the engine (exact, Monte Carlo, cancellation) and its ViewModel. Chip calculator ViewModel. `OddsBenchmark` is skipped unless `ODDS_BENCH=1`. |
| tournament-feature | 26 | 3 | Payout use case, TournamentConfigViewModel, TimerViewModel on virtual time: countdown, levels, sound cue, overtime, validation. |

Before v1.2.0 the green run proved little: 245 executions but 101 unique tests (core's ran 3
times), and 54 tests in JUnit 4/5-mismatched modules never ran. Turning them on surfaced 43
failures.

### Skipped tests are specs for open board items

A skipped test asserts what the code *should* do, and is disabled until its board item lands.
Gradle prints each one as `SKIPPED` on every run. To enable one, remove its
`@Ignore`/`@Disabled` together with the fix.

| Test | Board item |
|---|---|
| `BankViewModelTest.purchasesSurviveTheAmountBeingClearedAndRetyped`, `TournamentConfigViewModelTest.purchasesSurviveTheAmountBeingClearedAndRetyped` | PP-014 |
| `CalculatePayoutsUseCaseTest` / `TournamentConfigViewModelWeightsTest`: `never pays more places than there are players` | PP-016 |
| `BankViewModelTest.bankTotalsFollowTournamentConfigChanges` | PP-018 |
| `BlindStructureCalculatorTest`: `every step of an accepted schedule stays within the documented growth bounds` | PP-020 |
| `BlindFittingAlgorithmTest`: `levels strictly increase, or the configuration is rejected`; `every level is a multiple of the smallest chip, or the configuration is rejected` | PP-020 |

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
  clock, step it with `advanceTimeBy` + `runCurrent`. `TimerViewModelTest` shows the pattern.

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

The device tour (section 5) covers on-device behaviour. The proposed Roborazzi tier (section 9)
would cover composables on the JVM. Library modules use the stock `AndroidJUnitRunner`.

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
| Low RAM | The emulator uses about 2 GB. The scripts cap Gradle at `-Xmx4g` and the Kotlin daemon at 2 GB. Run one Gradle build at a time. |

## 9. Next tier: JVM screenshot tests (proposal)

Roborazzi (with Robolectric native graphics) would render composables to PNG in a plain
JVM test, with no emulator and in seconds. It would compare them to golden images committed
under `src/test/screenshots`. An agent would run `./gradlew verifyRoborazziDebug`, read
`build/test-results/roborazzi/results-summary.json`, and open the `*_compare.png` diffs for
any failures. The versions that fit this toolchain, and the setup steps, are in the
device-harness report. In short: Roborazzi **1.60.0**, the last release whose Kotlin
metadata Kotlin 2.0.21 can read, plus Robolectric 4.14.1. The JUnit vintage engine it needs is
already on the test classpath.
