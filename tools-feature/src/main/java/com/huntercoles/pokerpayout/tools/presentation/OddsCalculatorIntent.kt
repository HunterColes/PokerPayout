package com.huntercoles.pokerpayout.tools.presentation

/**
 * Everything the odds screen (S8, S9) and run it out (S10) can ask for. Odds are worked out
 * live: any change to the table cancels the running calculation, clears its numbers and starts
 * a new one shortly after.
 */
sealed interface OddsCalculatorIntent {
    // ------------------------------------------------------------------ keypad

    /** Aim the keypad at [slot] (it opens if closed). Tapping a card re-types it. */
    data class SelectSlot(val slot: SlotRef) : OddsCalculatorIntent

    /** Pick a rank (0 = deuce .. 12 = ace); a suit comes next. */
    data class PickRank(val rank: Int) : OddsCalculatorIntent

    /** Pick a suit (0..3 = c, d, h, s) for the picked rank: the card goes in the slot, the keypad moves on. */
    data class PickSuit(val suit: Int) : OddsCalculatorIntent

    /** Put [card] straight into the keypad's slot (rank and suit in one go). */
    data class PlaceCard(val card: Int) : OddsCalculatorIntent

    /** Clear the keypad's slot, or if it's empty, the card before it (like a text backspace). */
    data object Backspace : OddsCalculatorIntent

    /** Leave the keypad seat's missing cards unknown ("a random hand") and move on. */
    data object RandomHand : OddsCalculatorIntent

    /** Close the keypad ("Done"). */
    data object CloseKeypad : OddsCalculatorIntent

    // ------------------------------------------------------------------ seats

    /** Add a seat with an unknown hand (up to 10); the keypad moves to it. */
    data object AddPlayer : OddsCalculatorIntent

    /** Remove [seat] (down to two players). */
    data class RemovePlayer(val seat: Int) : OddsCalculatorIntent

    /** Fold [seat] (its cards become dead) or bring it back. */
    data class Fold(val seat: Int, val folded: Boolean = true) : OddsCalculatorIntent

    /** Swap the hands in seats [a] and [b] (the header's Swap: Player 1 and Player 2). */
    data class Swap(val a: Int = 0, val b: Int = 1) : OddsCalculatorIntent

    /** Clear [seat]'s two cards. */
    data class ClearHand(val seat: Int) : OddsCalculatorIntent

    /** Clear every card and fold, keep the seats; Undo for a few seconds. */
    data object NewHand : OddsCalculatorIntent

    /** Back to two empty seats; Undo for a few seconds. */
    data object ClearTable : OddsCalculatorIntent

    data class SetFourColourDeck(val enabled: Boolean) : OddsCalculatorIntent

    // ------------------------------------------------------------------ run it out

    /** Start run it out on the current hand (every hand known, a board of 0, 3 or 4 cards). */
    data object RunItOut : OddsCalculatorIntent

    /** Turn the next street face up. */
    data object DealNext : OddsCalculatorIntent

    /** A fresh runout from the board run it out started on. */
    data object RunAgain : OddsCalculatorIntent

    /** Deal the rest of the board twice, from one deck, and split the pot between the two runs. */
    data object RunTwice : OddsCalculatorIntent

    /** Turn the landscape (propped-up) view on or off. */
    data class SetRunOutLandscape(val landscape: Boolean) : OddsCalculatorIntent

    /** Back to the odds screen, with the hand as it was. */
    data object ExitRunItOut : OddsCalculatorIntent
}
