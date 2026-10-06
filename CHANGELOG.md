# Changelog

Versions on master step up as each batch of work lands; a version is published (GitHub release, then
F-Droid) only when it has a tag. Published versions link to their release notes.

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
