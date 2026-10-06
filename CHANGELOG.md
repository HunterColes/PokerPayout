# Changelog

Versions on master step up as each batch of work lands; a version is published (GitHub release, then
F-Droid) only when it has a tag. Published versions link to their release notes.

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
