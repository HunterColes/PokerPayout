# Changelog

Versions on master step up as each batch of work lands; a version is published (GitHub release, then
F-Droid) only when it has a tag. Published versions link to their release notes.

## 1.4.5 (on master, not published)

A test lab that found five bugs, all fixed:

- **Mystery bounties:** a player joining after an envelope was drawn could make the night pay up to
  $9 more in bounties than was collected (nine players' $15 envelope wasn't in ten players' deal).
  The money not yet drawn is now dealt again into the envelopes still to come.
- **Live clock:** leaving the app and coming straight back, over and over, with the clock running
  could crash it (found by the monkey). The notification's service no longer stops while a new
  start is on its way.
- **Huge stacks:** overtime no longer doubles a big blind past what can be counted (a 500,000,000
  stack showed a negative big blind), and the setup advice never suggests such a stack.
- **Payout editor:** a turned tablet or a font change kept the weights and rounding you hadn't
  saved yet, as every other sheet does.
- **Payouts:** a place that would round to $0 isn't paid (27 players at $10 rounded to $10 paid 9th
  nothing); the places stepper stops at the most places that each pay something.
- **Easier to read:** each payout preset's 1st-place amount is full gold, the odds grid's cards
  already on the table are lighter, and Music's "On breaks" heading stays readable with the clock
  switch off.
- **New tests:** property tests that throw thousands of random nights, setups and amounts at the
  money and blind maths; checks on every screen for TalkBack names and WCAG text contrast; the
  clock, the Bank and the odds table surviving the app being killed at random; a seeded monkey on
  GitHub's emulator for every pull request; and mutation testing, by hand (79% of core's maths).

## 1.4.4 (on master, not published)

House cleaning:

- **Mystery bounties:** once an envelope is drawn, the player count can't go lower (fewer players
  would deal fewer envelopes and could leave the champion's pool at $0); the Players hint says so.
  A late entry still comes in, and Undo or a new tournament lifts it. Loading a preset keeps the
  bounty that dealt the envelopes.
- **No white flash at launch:** the launch screen, and Android 12's splash, are the felt green.
- **Snackbars:** a new one replaces the one showing at once, on every screen, instead of waiting
  behind it; the one replaced takes its Undo with it.
- Under the hood: release.sh finds the last release by the highest version tag, the device tour's
  break check allows any time up to 10:00, unused and duplicate launcher drawables are gone, and
  the changelog marks 1.3.14 as published.

## 1.4.3 (on master, not published)

- **Shot clock** (Tools): a countdown for each decision, 30, 45 or 60 seconds. The whole face is one
  big button: tap it for the next decision. A warning at 10 seconds and when time is up, with the
  Sound section's settings (a beep unless the sound is off, a buzz with Vibrate, a gold flash with
  Flash the clock). Pause, Resume and Reset. **Time-bank cards:** each player (the Bank's) gets up
  to five, and playing one adds 30 seconds, even just after time ran out; "Give everyone their
  cards back" with Undo. It keeps the screen on while it counts, and never touches the tournament
  clock or its chime.
- **Dealer's choice** (Tools): spin a wheel to pick the next game. Seventeen games with a short
  rules card each (Hold'em, Omaha, Big O, Stud, Razz, 2-7 Triple Draw, Badugi, Pineapple, Crazy
  Pineapple and more), nine on the first wheel; switch any on or off, and add up to eight house
  games. It never picks the game just played twice running, and with Remove animations on, the
  pick shows at once.
- **Equity quiz** (Tools): two or three hands face up, before the flop, on the flop or on the turn.
  Guess who's ahead, or how often the first hand wins; then see every hand's real odds, worked out
  exactly by the Odds screen's engine. Your streak, best streak and score stay on the phone.
- Backups take all three: the shot clock's settings and cards played, the wheel and its house
  games, and the quiz's settings and score.

## 1.4.2 (on master, not published)

- **Music** (Tools > Sound): a playlist of songs from your phone, added with the phone's file
  picker (no new permission, still no internet). Play, pause, previous and next, shuffle, repeat
  (all songs or one), move and remove songs, and a volume of its own. A song whose file has gone
  says "File not found" and is skipped.
- **Play with the clock:** the music starts when the clock runs and pauses when it's paused or
  over; on breaks it keeps playing, pauses or plays quieter, as you choose. It dips under the
  clock's sounds, plays on with the screen off, and keeps going in the background while the clock
  runs.
- **Cue sounds** (Tools > Sound): sound packs with a sound for each moment (a new level, a minute
  left, a break starting and ending, the game over), each played on a tap. *Classic*, the default,
  is the chime as before. Room for built-in songs and new packs (docs/SOUNDS.md); none ship yet.
- Backups take the sound pack and the music's settings; the playlist stays with the phone.

## 1.4.1 (on master, not published)

- **Backup** (Tools): one file with everything the app saves (presets, History and the season's
  points, the chip set, settings, sound and tonight's game), saved and opened through the phone's
  file picker, so it can live in Drive, Downloads or on a computer. No permission and still no
  internet. Opening one shows what it holds before anything changes, then *Add to this phone*
  (the presets and nights the phone doesn't have, with Undo) or *Replace this phone's data* (the
  app starts again on the backup's game; a running clock comes back paused). A file that isn't a
  backup, is damaged or comes from a newer version is turned away with a plain reason.
- **History's CSV as a file:** *Save as CSV file…* writes every night as a spreadsheet file (UTF-8,
  a header row); *Share as CSV* (formerly *Export as CSV*) still sends it as text.
- **Presets as files:** a preset's menu has *Share as file* ("Friday.json" for a friend), and the
  presets list has *Open preset file…*, which adds its presets with Undo ("Friday 2" when the name
  is taken by another setup). The Backup screen opens preset files too.
- Android's own backup now takes the same data as the backup file, and on Android 12 and up copies
  it to Google's cloud only when the copy is end-to-end encrypted, since it holds players' names.

## 1.4.0 (on master, not published)

- **The cash game folds into the Bank.** The Bank's Tournament / Cash game switch and the separate
  cash page are gone: a home night runs one way, through the Tournament, Bank and Payouts tabs.
- **Settle up:** once the night is over and someone's buy-in is still open, the Bank offers
  "Settle up": who pays whom so that everyone is square, counting what the Bank took in and paid
  out (it keeps the food money). It finds the true fewest payments (exactly, for nights of up to 10
  players), with a tick for each payment and Share as text for the group chat. Ticking the last one
  marks every buy-in and payout paid, so the night can go into History; Undo takes it back. When
  everyone paid in at the start, the Paid column does the job as before.
- Gone with the cash page: free-amount buy-ins and top-ups, the chip count check and splitting a
  difference. A cash game saved by 1.3.14 is left untouched on the phone and no longer shown.
- The store listing's fifth picture is now the settle-up.

## [1.3.14](https://github.com/HunterColes/PokerPayout/releases/tag/v1.3.14) (2026-10-07)

A new look, cash games and a clock on your lock screen: everything in 1.3.1 to 1.3.13 below,
published as one release.

## 1.3.13 (on master, not published)

- **Odds on small phones:** on the smallest phones at large text, the card keypad takes a row less
  (the suits join the ranks' last row), and the card being typed always scrolls into view, so the
  seats and "Add player" stay reachable while the keypad is open.

## 1.3.12 (on master, not published)

- **History** (Tools): once everyone owed is marked paid, the Payouts tab offers "Save this night".
  Saved nights keep the players in finishing order with what each paid in and won, knockouts and
  bounties, and the prize pool. History shows the season's points (players in the night minus your
  place, plus 1) with the player of the year, each night's summary to share as text, and an export
  of everything as CSV for a spreadsheet. All on the phone; no accounts.

## 1.3.11 (on master, not published)

- **Bounty types:** next to the bounty, choose Standard, Progressive or Mystery.
  - *Progressive:* a knockout pays half the victim's bounty at once and adds the other half to the
    winner's own bounty, shown under each name in the Bank.
  - *Mystery:* each knockout draws an envelope from a pool worth players × bounty (a few big, many
    small, listed before the start), opened on screen; the champion takes the envelopes left.
  - Undo puts everything back, envelopes included. Saved games stay Standard.

## 1.3.10 (on master, not published)

- **Presets:** save the night's setup (money, blinds, payouts and, if you like, your chip set)
  under a name, and load it next time in one tap, with Undo. Loading waits for a new tournament,
  so a game in progress can't change. Rename and delete them from the list.
- **Share the setup as text:** the money, every level and break with its color-up, and the
  payouts, for the group chat.

## 1.3.9 (on master, not published)

House cleaning and accessibility:

- The setup strip shows whole settings instead of cutting one off, at every phone width.
- The level-length field on the setup page is labelled "Level length" (it said "Levels").
- "Turn rebuys off?" is a sheet like the other confirmations, with a red "Clear N rebuys".
- TalkBack names the chip set's fields, and hears which odds seat is the favourite. Payout weights
  out of order show a warning sign, run-it-out lines have their own patterns, and setup menus tick
  the current choice, so nothing relies on colour alone.
- Under the hood: the unused database, old scaffolding, retired colours and build leftovers are
  gone, and the README and store description say plainly what the app is: free, no ads, no
  accounts, no internet permission.

## 1.3.8 (on master, not published)

- **Live clock in the notifications and on the lock screen:** while the clock runs and the app is
  out of sight, an ongoing notification shows the level, a live countdown, the blinds now and next,
  and Pause / Resume / Open buttons. Pausing there pauses the clock everywhere. On Android 13 and
  up the first Start asks once for permission to show it.
- **Quiet cues:** a vibration and a gold flash of the clock at each level change and with a minute
  left, for quiet rooms and muted phones. Each has its own switch in Tools › Sound; under Reduce
  motion the flash is a steady gold frame.
- New permissions for this: notifications, a foreground service while the clock runs in the
  background, keeping the processor awake for on-time cues, and vibration. Still no internet.

## 1.3.7 (on master, not published)

Small changes the owner chose:

- **Chip set:** the stacks kept back for rebuys and add-ons start from an estimate made from the
  Tournament's settings (about half the players rebuy when there's a cutoff, one each without
  one, one add-on each), with a line saying where it came from. Change it and your number stays;
  one tap goes back to the estimate.
- **Break screen:** once you've set up your chip set, the color-up uses your own chips and
  colours ("4 white 25s for 1 red 100").
- **Table view:** after ✕ in the turned table view, the clock stays upright for that turn only;
  turn the phone again and the table view comes back.
- **Tools:** the Chip set row now reads "Stacks from the chips you own, plus a color-up plan".

## 1.3.6 (on master, not published)

- **Cash game** (Bank): a Tournament / Cash game switch at the top of the Bank. Log buy-ins and
  top-ups, count everyone's chips at the end, and the app checks the chips against the cash before
  anyone settles: if they don't match, recount, or choose to split the difference across the
  stacks. Then it lists who pays whom, in at most one payment fewer than there are players, each
  with a "paid" tick, and shares it as text. Undo works throughout. The cash game is kept apart
  from the tournament: resetting one never touches the other.

## 1.3.5 (on master, not published)

- **Seat draw** (Tools): seats the Bank's players at random across one or two tables, kept within
  one player of each other, then deals a card to every seat for the button. The high card takes it
  (ties go spades, hearts, diamonds, clubs) and the next seats post the blinds. Redraw with Undo,
  and share the seating as text.

## 1.3.4 (on master, not published)

The Tournament tab is rebuilt around the clock.

- **Setup that folds into the clock:** fill in the night (players, money, blinds), press Start, and
  the setup folds away into a one-line strip above a big clock. Tap the strip to change things
  mid-game; money and blind fields stay locked until you choose "Unlock to edit".
- **The clock:** the level and its countdown in big steady digits, a progress bar, the blinds and
  what's next, ±1 minute nudges, and a strip with players, average stack and the prize pool. Below
  it: the next break, the projected end, and whether rebuys are still open.
- **Rebuys until level N** is set in setup and shown on the clock; the Bank closes the Rebuy column
  when it passes.
- **Breaks:** a break screen with the color-up (which chips go, what to change them for) and "End
  break now".
- **Turn your phone for the table view:** on the Tournament tab, turning the phone sideways shows
  the full-screen table view; turning it back shows the clock. Tablets and foldables rotate on every
  screen.
- **Tablets:** the clock and the blind schedule side by side. Small phones get a tighter layout that
  still fits the big digits.
- **Prize pool** on the clock now uses each rebuy's own price, like the Bank.
- **Testing:** the device matrix runs the real app on the emulator at small, standard, foldable and
  tablet sizes, at larger text and rotated, with a contact sheet of every screen.

## 1.3.3 (on master, not published)

The Bank and the Payouts tab are rebuilt.

- **Bank:** one compact row per player under a labelled header (Buy-in, Rebuy, Out, Paid), no more
  unlabelled emoji. A buy-in or rebuy is one tap, with Undo on the snackbar (the last 20 actions can
  be taken back). Knock a player out from a sheet that asks who did it, and their place shows in
  the Out column. The champion and every paid place get a pay-out sheet at the end of the night.
  Money meters show what's in and what's been paid; the pool breakdown shows rebuys and add-ons
  inside the prize pool.
- **Rebuy cutoff:** "rebuys until level N" closes the Rebuy column when the clock passes that level;
  add-ons stay open until the end of the first break after it.
- **Fair rebuy prices:** each rebuy and add-on keeps the price it was bought at, so changing the
  price mid-game no longer re-values earlier purchases.
- **Payouts tab:** the prize pool and where it came from, presets that show what 1st would get,
  rounding, a places stepper, share bars, the bubble, bounties, and Share as text.
- **5 players now pay 2 places** by default (was winner-takes-all).
- **Small phones and tablets:** on narrow screens closed columns fold into each row; on tablets the
  Bank shows In and Owed columns and keeps the breakdown open beside the list.

## 1.3.2 (on master, not published)

Chip set and Hand ranks join the makeover.

- **Chip set** (was the chip calculator): enter the chips you own, colour, value and count, and it
  plans every player's starting stack within what you have, with stacks kept back for rebuys and
  add-ons. If the set comes up short it says exactly what to add ("Short 10 green 25s for 5
  players"). A colour-up plan follows the clock's breaks. It updates live (no Generate button), and
  your old calculator settings carry over (check the counts once).
- **Hand ranks:** real card faces with the kickers dimmed, and how often each hand turns up in seven
  cards ("1 in 39" for a full house). Two columns on tablets; follows the four-colour deck setting.

## 1.3.1 (on master, not published)

The makeover begins: the design system, the new app frame, and the new Odds tool.

- **New look underneath:** Barlow Condensed with steady-width digits, the felt and gold colours
  refined, and shared buttons, fields, cards, chips and sheets with 48 dp touch targets and screen
  reader labels.
- **Four tabs:** Tournament, Bank, Payouts (new) and Tools. Every screen has its own header, Back
  from any tab returns to Tournament, and Tools screens keep the Tools tab selected.
- **Tablets and wide screens:** a side rail replaces the bottom bar from 600 dp, and screens are
  centred at a comfortable width.
- **Tools:** the tools as a list, with the chime volume and a test chime built in.
- **Odds:** pick cards on a docked keypad; odds update live with no Calculate button; each hand is
  described ("Nut flush draw + gutshot"); a grid shows which next cards change the lead; Fold, Swap,
  New hand and Clear table, with Undo; a four-colour deck option.
- **Run it out:** deal the turn and the river one at a time, or run it twice.
- **Fixed:** typing fast in the break note could drop or reorder letters.
- **Testing:** screenshot tests on 8 screen sizes (phones, a foldable, a tablet, landscape) at three
  font sizes, with automatic checks for clipped text and small touch targets.

## [1.3.0](https://github.com/HunterColes/PokerPayout/releases/tag/v1.3.0) (2026-10-06)

A real tournament clock, payouts that add up to the cent, and a 1.5 MB download.

## [1.2.0](https://github.com/HunterColes/PokerPayout/releases/tag/v1.2.0) (2026-10-05)

Correct odds and a crash-proof chip calculator; new signing key.

Older versions: see the [GitHub releases](https://github.com/HunterColes/PokerPayout/releases).
