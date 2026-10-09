<!--
  Pictures on this page update themselves:
  - the row under the badges is the store listing (metadata/en-US/images/phoneScreenshots/),
    re-shot on GitHub's emulator by .github/workflows/listing.yml;
  - the pictures in "What's inside" are screenshot-test goldens (*/src/test/screenshots/), which CI
    checks on every push and goldens.yml re-records, so they always match the code.
  Renaming a screen test renames its golden: update the path here too.
-->

<p align="center">
  <img src="metadata/en-US/images/featureGraphic.png" alt="Poker Payout: tournament clock, payouts and bank. Free, no ads, offline." width="720"/>
</p>

<p align="center"><b>A free Android app for home poker nights.</b></p>

<p align="center">
  A tournament clock with blinds that fit your chips, payouts that add up to the cent, a bank of
  who paid what, and tools for the table. Offline, no ads, no accounts.
</p>

<p align="center">
  <a href="https://f-droid.org/packages/com.huntercoles.pokerpayout/"><img alt="Get it on F-Droid" src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" height="80"/></a>
  &nbsp;
  <a href="https://github.com/HunterColes/PokerPayout/releases/latest"><img alt="Get it on GitHub" src="https://raw.githubusercontent.com/deckerst/common/main/assets/get-it-on-github.png" height="80"/></a>
</p>

<p align="center">
  <a href="https://github.com/HunterColes/PokerPayout/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/HunterColes/PokerPayout?label=release"/></a>
  <a href="https://github.com/HunterColes/PokerPayout/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/HunterColes/PokerPayout/actions/workflows/ci.yml/badge.svg?branch=master"/></a>
  <img alt="Android 8.0 and up" src="https://img.shields.io/badge/Android-8.0%2B-3ddc84?logo=android&logoColor=white"/>
  <img alt="No internet permission" src="https://img.shields.io/badge/internet%20permission-none-2ea44f"/>
  <a href="LICENSE.md"><img alt="MIT licence" src="https://img.shields.io/badge/licence-MIT-blue"/></a>
  <a href="#support-the-app"><img alt="Support the app" src="https://img.shields.io/badge/support-the%20app-ffd60a"/></a>
</p>

<p align="center">
  <img src="metadata/en-US/images/phoneScreenshots/01_clock.png" alt="The tournament clock" width="23%"/>
  <img src="metadata/en-US/images/phoneScreenshots/03_bank.png" alt="The bank" width="23%"/>
  <img src="metadata/en-US/images/phoneScreenshots/04_payouts.png" alt="Payouts" width="23%"/>
  <img src="metadata/en-US/images/phoneScreenshots/06_odds.png" alt="Odds" width="23%"/>
</p>

## What's inside

### Tournament

- Set up the night once: players, buy-in, bounty, rebuys (with a cutoff level), add-ons and food.
  Save it as a preset and load it next time.
- Blinds from four numbers: game length, level length, starting stack and smallest chip. Every
  level is a multiple of your smallest chip, and a setup that can't work says why and offers the
  nearest fix.
- A clock with big digits, the next level, ±1 minute nudges, breaks with a note and a big-blind
  ante. It keeps time through sleep, restarts and reboots.
- The running clock in the notifications and on the lock screen, with a chime, a vibration or a
  flash at each new level.
- Color-ups on breaks, overtime levels if the game runs long, and a full-screen table view when
  you turn the phone sideways.
- Share the setup as text for the group chat.

<p align="center">
  <img src="tournament-feature/src/test/screenshots/screens/S1_panel_open/S1_panel_open_phone-360x780_font1.0.png" alt="Tournament setup" width="23%"/>
  <img src="tournament-feature/src/test/screenshots/screens/S2_clock_running/S2_clock_running_phone-360x780_font1.0.png" alt="The running clock" width="23%"/>
  <img src="tournament-feature/src/test/screenshots/screens/S4_break_colorup/S4_break_colorup_phone-360x780_font1.0.png" alt="A break with a color-up" width="23%"/>
  <img src="tournament-feature/src/test/screenshots/screens/S15_presets_list/S15_presets_list_phone-360x780_font1.0.png" alt="Saved presets" width="23%"/>
</p>

### Bank

- Who has paid the buy-in, rebuys and add-ons, and who has been paid out, with Undo.
- Knockouts and bounties three ways: standard, progressive (half now, half onto the winner's own
  bounty) or mystery envelopes.
- Settle up at the end of the night: the fewest payments that square everyone, with a tick for
  each, when not everyone paid in at the start.
- Share the settle-up as text.

<p align="center">
  <img src="bank-feature/src/test/screenshots/screens/S5_bank_midgame/S5_bank_midgame_phone-360x780_font1.0.png" alt="The bank during a game" width="23%"/>
  <img src="bank-feature/src/test/screenshots/screens/S5b_knockout_sheet_pko/S5b_knockout_sheet_pko_phone-360x780_font1.0.png" alt="A knockout with a progressive bounty" width="23%"/>
  <img src="bank-feature/src/test/screenshots/screens/S5d_envelope_reveal/S5d_envelope_reveal_phone-360x780_font1.0.png" alt="A mystery bounty envelope" width="23%"/>
  <img src="bank-feature/src/test/screenshots/screens/S5e_settle_up/S5e_settle_up_phone-360x780_font1.0.png" alt="Settling up at the end of the night" width="23%"/>
</p>

### Payouts

- Top-heavy, standard or flat, or your own weights, rounded to $1, $5 or $10.
- The places always add up to the prize pool, to the cent.
- Share the payouts as text, and save the night to History once everyone is paid.

<p align="center">
  <img src="tournament-feature/src/test/screenshots/screens/S6_payouts_standard/S6_payouts_standard_phone-360x780_font1.0.png" alt="Payouts for three places" width="23%"/>
  <img src="tournament-feature/src/test/screenshots/screens/S6_payouts_save/S6_payouts_save_phone-360x780_font1.0.png" alt="Saving the night when everyone is paid" width="23%"/>
</p>

### Tools

- **Odds** for two to ten hands (exact, or a close estimate when there are too many runouts),
  and run it out card by card.
- **Hand ranks:** what beats what, with how often each hand comes up.
- **Chip set:** stacks from the chips you own, and a color-up plan.
- **Seat draw:** random, balanced seats across your tables, and the high card for the button.
- **History:** saved nights, season points and the player of the year, and a CSV export for a
  spreadsheet.
- **Sound:** a chime before each level, break and the end; vibrate and flash for quiet rooms.

<p align="center">
  <img src="tools-feature/src/test/screenshots/screens/S9_odds_flop_exact/S9_odds_flop_exact_phone-360x780_font1.0.png" alt="Odds on the flop" width="23%"/>
  <img src="tools-feature/src/test/screenshots/screens/S10_runout_river_p1/S10_runout_river_p1_phone-360x780_font1.0.png" alt="Running it out to the river" width="23%"/>
  <img src="tools-feature/src/test/screenshots/screens/S12_ranks_default/S12_ranks_default_phone-360x780_font1.0.png" alt="Hand ranks" width="23%"/>
</p>
<p align="center">
  <img src="tools-feature/src/test/screenshots/screens/S11_chipset_ok/S11_chipset_ok_phone-360x780_font1.0.png" alt="Chip set" width="23%"/>
  <img src="tools-feature/src/test/screenshots/screens/S14_seats_two_tables/S14_seats_two_tables_phone-360x780_font1.0.png" alt="Seats across two tables" width="23%"/>
  <img src="tools-feature/src/test/screenshots/screens/S16_history_list/S16_history_list_phone-360x780_font1.0.png" alt="History and season points" width="23%"/>
</p>

<sub>The pictures in this section come from the app's screenshot tests. A change to a screen can't
merge until they're updated, so they always show the app as it is today.</sub>

## Private by design

- **No internet permission.** The app can't go online, so nothing you type can leave your phone
  through it.
- **No ads, no accounts, no analytics, no tracking.**
- **Your data stays on your phone,** in the app's own storage. Something leaves only when you tap
  Share or Export and pick where it goes.

The details are in [PRIVACY.md](PRIVACY.md).

## Install

- **[F-Droid](https://f-droid.org/packages/com.huntercoles.pokerpayout/)** (recommended): updates
  arrive by themselves. F-Droid builds each release from this source and checks it matches ours
  byte for byte, so it can be a few days behind GitHub.
- **[GitHub releases](https://github.com/HunterColes/PokerPayout/releases/latest):** download
  `PokerPayout-v….apk` on your phone and open it. Android may ask you to allow installs from your
  browser or files app.

Needs Android 8.0 or newer.

> **Coming from 1.1.x?** The signing key changed in 1.2.0, so Android can't update over it.
> Uninstall Poker Payout once, then install the new version. Uninstalling deletes saved data.
> Later updates install normally.

## Support the app

Poker Payout is free: no ads, nothing locked, nothing to sign up for. If it has run a few of your
poker nights and you'd like to say thanks, a donation is the nicest way. It pays for the time that
goes into new features and fixes.

**[Donate in Ethereum or Monero](crypto/DONATIONS.md)** (addresses and QR codes)

No crypto? These help just as much, and cost nothing:

- Star the repository, so more people find it.
- Tell your poker group, or the next home game you play in.
- [Report a bug or suggest an idea](https://github.com/HunterColes/PokerPayout/issues/new/choose).

Thank you. Every bit of support is noticed.

## Build and contribute

Bug reports and ideas are welcome: the [issue forms](https://github.com/HunterColes/PokerPayout/issues/new/choose)
ask for what's needed. For code, start with [CONTRIBUTING.md](CONTRIBUTING.md).

You need JDK 21 and the Android SDK (API 34). The modules target Java 17 bytecode but build with
a JDK 21 toolchain, the same JDK F-Droid builds with.

```bash
./gradlew assembleDebug                         # debug APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest lintDebug detekt    # what CI checks on every push and pull request
```

- [docs/TESTING.md](docs/TESTING.md): unit tests, screenshot tests and the device tour.
- [docs/RELEASING.md](docs/RELEASING.md): releases, signing and F-Droid's reproducible builds.

**Built with** Kotlin, Jetpack Compose and Material 3. One module per tab (tournament, bank,
tools) on a shared core with the design system. Each screen is MVI: a ViewModel, its UI state and
intents, and a stateless composable. Hilt for dependency injection, Kotlin Coroutines and Flow,
settings and game state in SharedPreferences, and a version catalog for dependencies.

## Licence and credits

Made by **Hunter Coles** (owner and maintainer) with **Joshua Glenn** (co-author).

- [MIT License](LICENSE.md): free and open source.
- Font: [Barlow Condensed](https://github.com/jpt/barlow) by Jeremy Tribby, SIL Open Font License
  1.1 ([licence text](core/src/main/assets/licenses/OFL-BarlowCondensed.txt), shipped in the app).
- Icons: Material Symbols paths (Apache License 2.0), plus suits, chips and cards drawn for Poker
  Payout.

[Contributing](CONTRIBUTING.md) · [Code of conduct](CODE_OF_CONDUCT.md) ·
[Security](SECURITY.md) · [Privacy](PRIVACY.md) · [Changelog](CHANGELOG.md)
