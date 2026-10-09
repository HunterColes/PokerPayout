# Contributing to Poker Payout

Thanks for wanting to help. Bug reports, ideas and code are all welcome.

- **Found a bug or have an idea?** [Open an issue](https://github.com/HunterColes/PokerPayout/issues/new/choose);
  the forms ask for what's needed.
- **Want to write code?** For anything bigger than a small fix, open an issue first so we can agree
  on the approach before you spend the time.
- **Security problem?** Please don't open a public issue: see [SECURITY.md](SECURITY.md).

Everyone taking part follows the [code of conduct](CODE_OF_CONDUCT.md).

## Build it

You need JDK 21 and the Android SDK (API 34). The modules target Java 17 bytecode but build with a
JDK 21 toolchain, which is also what F-Droid's build server has.

```bash
./gradlew assembleDebug        # debug APK in app/build/outputs/apk/debug/
```

| Module | What lives there |
|---|---|
| `app` | The app's entry point and the release build setup |
| `core` | The design system (`core/design`), navigation, saved settings, money and blind maths, and the test kit for screen sizes |
| `tournament-feature` | The Tournament tab (setup, presets, clock, blinds, table view), the live clock notification, and the Payouts tab |
| `bank-feature` | The Bank tab: buy-ins, rebuys, add-ons, knockouts and bounties, settling up, and cash games |
| `tools-feature` | The Tools tab: Odds and Run it out, Chip set, Hand ranks, Seat draw, History, Sound |

## The check every change must pass

```bash
./gradlew testDebugUnitTest lintDebug detekt
```

CI runs exactly this on every push and pull request, and `master` only takes pull requests where
it's green. Pull requests are squashed into one commit when they merge.

- The unit tests include the **screenshot tests** (below), so a change to a screen fails until its
  pictures are updated on purpose.
- **Lint and detekt** compare against checked-in baselines and fail only on new findings. Please
  fix the finding; don't regenerate a baseline to get green.
- A guard fails the build if a module's tests quietly stop running.

## How the code is written

A few rules keep the app correct and consistent. In plain terms:

- **Money is whole cents.** Amounts are `Long` cents everywhere (`core/utils/Money.kt`), never
  floating point. One payout calculation feeds every screen, and payouts always add up to the
  prize pool to the cent.
- **The clock never counts ticks.** Play time comes from a fixed starting point on the phone's
  monotonic clock, so sleep, the app being closed, or a reboot can't make it drift. Don't save
  settings on every tick.
- **Use the design system.** Colours, type and components come from `PokerTheme`, `PokerColors`,
  `PokerType` and `core/design/components`. Please don't add raw colours.
- **Everyone can use it.** Everything tappable is at least 48 dp and has a TalkBack label. Nothing
  relies on colour alone.
- **Words go in `strings.xml`,** plain and short ("Paid", not "Payed"; no "professional" or
  superlatives).
- **Screens are MVI:** a thin route, a ViewModel with its `*UiState` and `*Intent`, and a stateless
  `*Content(state, onIntent)` composable.
- **Keep working features.** A redesign may move something, never drop it. If something moved,
  say where in the pull request.
- **Saved data survives updates.** Never rename a preference key. If saved data has to change
  shape, migrate it once, with a test.
- **Tests are deterministic.** JUnit 5; use fake clocks and seeded random numbers, never the wall
  clock or time limits.
- **Leave the version and the changelog alone.** The maintainer sets `versionName`, `versionCode`
  and the `CHANGELOG.md` entry when a change lands.

## Screenshot tests

Each screen is drawn on the JVM (Roborazzi on Robolectric) and compared with a checked-in picture,
a *golden*, in `*/src/test/screenshots/`. Every screen has a phone golden, and the main ones also
have small-phone, large-font, tablet and landscape goldens. On top of that, layout checks run every screen at 8 sizes and 3 font scales and fail on clipped or cut-off
text, text off the screen, targets under 48 dp and overlapping targets.

When you change how a screen looks, record its goldens again on purpose:

- **On GitHub:** run the "Record goldens" workflow on your branch (`gh workflow run goldens.yml
  --ref <branch>`, or from the Actions tab of your fork). It records, compresses the pictures
  losslessly, commits them to your branch and starts CI again. Pull afterwards.
- **Locally:** `./gradlew recordRoborazziDebug`.

Then look at every new picture in the diff before asking for a review. Some goldens appear in the
README, which is how its pictures stay current; if you rename a screen test, update the README's
path to its golden too.

## The device tour

Every pull request also runs the app on an Android emulator on GitHub (the "Device tour" workflow,
debug and release builds). It taps through the app's main paths and uploads a report with
screenshots, the screen contents and the log. It isn't a required check yet, but please look at it
when your change touches a screen. To run it on any branch: `gh workflow run device.yml --ref
<branch>`. To run it locally with an emulator: `scripts/device/tour.sh --stop`.

The full guide to all of this is [docs/TESTING.md](docs/TESTING.md). Releases, signing and
F-Droid's reproducible builds are in [docs/RELEASING.md](docs/RELEASING.md).

## Licence

Poker Payout is MIT licensed. By sending a pull request you agree that your contribution is
released under the same [licence](LICENSE.md).
