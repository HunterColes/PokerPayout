<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" alt="Poker Payout Icon" width="200"/>
</p>

<h1 align="center">Poker Payout</h1>

<p align="center">
  <img alt="Android API 26+" src="https://img.shields.io/badge/API%2026+-50f270?logo=android&logoColor=black&style=for-the-badge"/>
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white&style=for-the-badge"/>
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white&style=for-the-badge"/>
</p>

<p align="center">
  <img alt="Latest Release" src="https://img.shields.io/github/v/tag/HunterColes/PokerPayout?label=Latest%20Release&style=for-the-badge"/>
  <img alt="License MIT" src="https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge"/>
</p>

<h4 align="center">
  A free app for home poker nights: a tournament clock with blinds that fit your chips and your
  evening, payouts that add up to the cent, a bank of who paid what, and tools for the table.
  No ads, no accounts, no tracking. It works offline and doesn't even ask for internet access.
</h4>

# Download

<p align="center">
  <a href="https://github.com/HunterColes/PokerPayout/releases">
    <img alt="Get it on GitHub" src="https://raw.githubusercontent.com/deckerst/common/main/assets/get-it-on-github.png" height="80"/>
  </a>
  &nbsp;&nbsp;&nbsp;
  <a href="https://f-droid.org/packages/com.huntercoles.pokerpayout/">
    <img alt="Get it on F-Droid" src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" height="80"/>
  </a>
</p>

<p align="center">
  <a href="crypto/DONATIONS.md">
    <img alt="Donate Ethereum" src="https://img.shields.io/badge/Ξ-Ethereum-627EEA?logo=ethereum&logoColor=white&style=for-the-badge"/>
  </a>
  &nbsp;&nbsp;
  <a href="crypto/DONATIONS.md">
    <img alt="Donate Monero" src="https://img.shields.io/badge/Ӿ-Monero-FF6600?logo=monero&logoColor=white&style=for-the-badge"/>
  </a>
</p>

<p align="center">
  <img src="tournament-feature/src/test/screenshots/screens/S2_clock_running/S2_clock_running_phone-360x780_font1.0.png" alt="The tournament clock" width="160"/>
  <img src="bank-feature/src/test/screenshots/screens/S5_bank_midgame/S5_bank_midgame_phone-360x780_font1.0.png" alt="The bank" width="160"/>
  <img src="tournament-feature/src/test/screenshots/screens/S6_payouts_standard/S6_payouts_standard_phone-360x780_font1.0.png" alt="Payouts" width="160"/>
  <img src="tools-feature/src/test/screenshots/screens/S9_odds_flop_exact/S9_odds_flop_exact_phone-360x780_font1.0.png" alt="Odds" width="160"/>
</p>

<p align="center"><sub>Pictures from the screenshot tests, so they always show the current app.</sub></p>

# Features

• **Tournament**
  ◦ Set up the night once: players, buy-in, bounty, rebuys (with a cutoff level), add-ons and food
  ◦ A blind clock with big digits, the next level, ±1 minute nudges, breaks with a note, and a
    big-blind ante; it keeps time through sleep, restarts and reboots
  ◦ Blinds built from four numbers (game length, level length, starting stack, smallest chip):
    every level is a multiple of the smallest chip and grows 1.3x to 2x, and a setup that can't
    work says why and offers the nearest fix
  ◦ Color-ups on breaks, and up to three overtime levels if the game runs long
  ◦ Turn the phone sideways for a full-screen table view

• **Payouts and the bank**
  ◦ Top-heavy, standard and flat presets, or your own weights, rounded to $1, $5 or $10; the
    places always add up to the prize pool to the cent
  ◦ The bank: who has paid the buy-in, rebuys, add-ons, knockouts and bounties, and who has been
    paid out, with Undo
  ◦ A cash game mode: buy-ins and top-ups, a chip count check, and who pays whom at the end
  ◦ Share the payouts or the settle-up as text

• **Tools**
  ◦ Odds for two to ten hands (exact, or a close estimate when there are too many runouts), and run
    it out card by card
  ◦ Chip set: stacks from the chips you own, and a color-up plan
  ◦ Seat draw: random, balanced seats across your tables, and the high card for the button
  ◦ Hand ranks, with how often each hand comes up

• **Free and private**
  ◦ No ads, no accounts, no tracking, no internet permission: everything stays on your phone
  ◦ Free and open source (MIT)

---

# Build

Linux, macOS or Windows with JDK 21 and the Android SDK (API 34). The modules target Java 17
bytecode but build with a JDK 21 toolchain, the same JDK F-Droid builds with.

```bash
./gradlew assembleDebug                         # debug APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest lintDebug detekt    # what CI checks on every pull request
```

Testing, including the screenshot tests and the device tour, is described in
[docs/TESTING.md](docs/TESTING.md). Releases, signing and F-Droid's reproducible builds are in
[docs/RELEASING.md](docs/RELEASING.md).

# Contribute

Pull requests are welcome. You can have a look at [issues](https://github.com/HunterColes/PokerPayout/issues) for contribution opportunities. For other changes, please open an issue first to discuss what you would like to change.

**How to contribute:**
1. Fork the repository
2. Create a feature branch
3. Open a pull request

For help or to discuss ideas, open an issue or a discussion on GitHub.

# Libraries & Architecture

• Kotlin, Jetpack Compose and Material 3
• One module per tab (tournament, bank, tools) on a shared core with the design system
• MVI: each screen is a ViewModel, its UI state and intents, and a stateless composable
• Hilt for dependency injection; settings and game state in SharedPreferences
• Kotlin Coroutines and Flow
• Version catalog for dependencies

# License

• [MIT License](LICENSE.md)
• Free and open source software
• Bundled font: [Barlow Condensed](https://github.com/jpt/barlow) by Jeremy Tribby, SIL Open Font License 1.1 ([licence text](core/src/main/assets/licenses/OFL-BarlowCondensed.txt), shipped in the app)
• Icons: Material Symbols paths (Apache License 2.0), plus suits, chips and cards drawn for Poker Payout
• See also: [CONTRIBUTING.md](CONTRIBUTING.md) • [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)