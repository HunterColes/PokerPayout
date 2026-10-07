# Poker Payout: notes for Claude

A free, offline Android app for home poker nights: tournament clock and blinds, payouts, a bank
that tracks who paid what, and tools (odds, chip set, hand ranks). Published on F-Droid and as
GitHub releases. No ads, no accounts, no internet permission.

## Layout

| Module | What lives there |
|---|---|
| `app` | `MainActivity` wiring, release build config (R8, signing from `keystore.properties`) |
| `core` | Design system (`core/design`: `PokerTheme`, `PokerColors`, `PokerType`, components), navigation, preferences, money and blind maths, the device-matrix test kit (`src/testFixtures`) |
| `tournament-feature` | Tournament tab (setup, presets, clock, blinds, table view), the live clock notification and its service, and the Payouts tab |
| `bank-feature` | Bank tab: buy-ins, rebuys, add-ons, knockouts and bounty types, settlement, and the cash game |
| `tools-feature` | Tools hub (with Sound), Odds and Run it out, Chip set, Hand ranks, Seat draw |

Jetpack Compose (Material 3), Hilt, MVI (`*Intent`, `*UiState`, `*ViewModel`), SharedPreferences.
Screens are a thin route plus a stateless `*Content(state, onIntent)` composable.

## Conventions

- **Money is whole cents (`Long`)** everywhere (`core/utils/Money.kt`). One payout calculation
  (`CalculatePayoutsUseCase`) feeds every screen; payouts always add up to the pool to the cent.
- **The clock never counts ticks.** Play time comes from a monotonic anchor, so sleep, process death
  and reboots can't make it drift (`TimerViewModel`; see `TimerViewModelTest`). Don't write
  preferences on every tick.
- **Design system first:** use `PokerTheme`, `PokerColors` tokens, `PokerType` and the components in
  `core/design/components` (the old off-palette colours are gone since 1.3.9; don't add raw colours).
  48 dp touch targets and TalkBack labels on everything tappable.
- **Strings go in `strings.xml`** as a file is touched. Copy is plain and short ("Paid", not "Payed").
- **Keep working features.** The owner's rule: a redesign may move a capability, never drop it. List
  anything removed and where its function went.
- **Saved data must survive updates.** Never rename a preference key; migrate once, with a test.

## Blind structure terms

- **Blind configuration:** game duration (hours), round length (minutes), smallest chip, starting
  chips. Regular levels = duration / round length (at least 2).
- **Ladder rules** (`BlindFittingAlgorithm`, `BlindLadderSearch`): the first small blind is the
  smallest chip, the last regular one is the starting stack, every level is a multiple of the
  smallest chip, and every step grows between 1.3x and 2.0x (`BlindStructureConstants`). Among valid
  ladders it picks the one closest (in log space) to the ideal geometric curve, preferring values
  players expect (300 over 275). Setups with no valid ladder are rejected with a plain reason and
  the nearest fix (`BlindSetupAdvisor`).
- **Overtime:** up to 3 extra levels that double, revealed as play passes the planned duration.
- **Breaks** every N levels with a note; color-ups land on breaks; optional big-blind ante.

## Build and check

JDK 21 toolchain (F-Droid's buildserver has only JDK 21; bytecode targets 17). The gate CI runs:

```bash
./gradlew testDebugUnitTest lintDebug detekt
```

- Lint and detekt use baselines: **never regenerate a baseline** to get green.
- When several builds run at once, pass `-Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx1g`.
- Never run `./gradlew --stop`, and never `pkill -f` with a pattern that can match your own shell.

## Testing (details: docs/TESTING.md)

- **Unit tests** are JUnit 5 plus vintage; a guard fails the build if a module's tests silently stop
  running. Use fake clocks and seeded RNGs, never wall-clock budgets.
- **Screenshot goldens** (Roborazzi on Robolectric) live in `*/src/test/screenshots/`. Plain
  `testDebugUnitTest` verifies them; re-record on purpose with `goldens.yml` on GitHub. They are
  recorded on 6 cells (`DeviceMatrix.goldens`, plus `pinned` ones); review new ones by eye.
- **Layout checks** (`core` test fixtures: `DeviceMatrix`, `ScreenTestRule`, `LayoutAssertions`) run
  every screen at 8 sizes x 3 font scales and fail on clipped, ellipsized, broken-word or off-screen
  text, targets under 48 dp, and overlapping targets.
- **Device tour** on a headless emulator (never ask the owner to plug in a phone):

  ```bash
  flock /tmp/pokerpayout-emulator.lock scripts/device/tour.sh --stop
  flock /tmp/pokerpayout-emulator.lock scripts/device/tour.sh --release --stop
  ```

  The lock is shared by every agent and session; always take it. Check `uptime` first: heavy load
  (the owner runs Unity) breaks uiautomator. Reports land in `build/device-reports/<run>/`.
- **On CI instead of this machine:** every pull request also runs the tour on GitHub's emulator
  (`.github/workflows/device.yml`, debug and release; `gh workflow run device.yml --ref <branch>`
  for any branch). `gh workflow run goldens.yml --ref <branch>` re-records the goldens on GitHub
  and commits them to the branch. Local builds can starve the owner's machine (it runs Unity), so
  don't build locally unless the owner has said builds are fine; never run several at once.

## Git, CI and releases (details: docs/RELEASING.md)

- `master` is protected: PRs only, and the "Unit tests, lint, detekt" check must pass. Never try to
  bypass it. Auto-merge is on: open the PR, then `gh pr merge --auto --squash` with a short
  `--subject` ("1.3.8: Live clock in the notifications (#33)") and `--body`. **Squash every PR**
  (the owner's rule: one tidy commit per batch on master). The one exception is `release.sh`'s
  release PR, which keeps a merge commit: the APK embeds its commit hash, so the tag must be the
  very commit that was built and verified, and that commit has to be on master. Merged branches are
  deleted automatically.
- Waiting on CI: one silent background `gh pr checks <n> --watch`; don't poll from the conversation.
- **Versions:** each landed batch steps the patch version on master (`versionName`, `versionCode` + 1)
  and gets a `CHANGELOG.md` entry. Publishing is separate and only with the owner:
  `scripts/release/release.sh --publish` (PR, CI, merge, tag, GitHub release). F-Droid then builds
  the tag itself and must reproduce the APK byte for byte (`--repro-check`).
- **Secrets never get read, printed or tested:** `keystore.properties`, `*.keystore` and
  `gitlab.token` sit untracked at the repo root (chmod 600). Signing checks go through
  `scripts/release/verify-signing.sh`, which prints only pass or fail.
- F-Droid metadata mirror: `metadata/com.huntercoles.pokerpayout.yml`. Changes to fdroiddata go
  through `scripts/release/fdroid-mr.sh`; track them with `scripts/release/fdroid-status.sh`.
