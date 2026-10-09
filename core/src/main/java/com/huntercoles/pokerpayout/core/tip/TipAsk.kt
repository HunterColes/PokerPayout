package com.huntercoles.pokerpayout.core.tip

/**
 * The "Tip the dealer?" card's rules (PP-112), as a value: the card is a friendly ask, never a nag.
 *
 * - It comes after the host's third night saved from the Payouts tab ([FIRST_ASK_AT]), a moment the
 *   app has just been useful, and at most [MAX_ASKS] times ever, [NIGHTS_BETWEEN_ASKS] saves apart.
 * - Never on the app's first run, and never while the clock runs: a save then waits for the next one.
 * - "Don't ask again" ends it for good with one tap, and so does "Leave a tip" (whoever went to the
 *   page has seen it). "Not now" puts this card away; the next is the last.
 *
 * Only saves on this phone count: nights merged in from a backup file don't.
 *
 * @property nightsSaved nights the host saved from the Payouts tab on this phone.
 * @property asksShown cards shown so far.
 * @property nextAskAt the save count from which the next card may show.
 * @property stopped no more cards, ever.
 * @property cardNight the saved night the card shows under on the Payouts tab, or null.
 */
data class TipAsk(
    val nightsSaved: Int = 0,
    val asksShown: Int = 0,
    val nextAskAt: Int = FIRST_ASK_AT,
    val stopped: Boolean = false,
    val cardNight: Long? = null,
) {
    /** Whether a save now would show the card, the clock and the first run aside. */
    val due: Boolean get() = !stopped && asksShown < MAX_ASKS && nightsSaved + 1 >= nextAskAt

    /**
     * Night [nightId] was just saved. The card shows under it if one is due and it is neither the
     * app's first run nor a running clock; otherwise it waits for a later save.
     */
    fun afterSave(nightId: Long, clockRunning: Boolean, firstRun: Boolean): TipAsk {
        val saved = nightsSaved + 1
        return if (due && !clockRunning && !firstRun) {
            copy(nightsSaved = saved, asksShown = asksShown + 1, nextAskAt = saved + NIGHTS_BETWEEN_ASKS, cardNight = nightId)
        } else {
            copy(nightsSaved = saved, cardNight = null)
        }
    }

    /** "Not now": this card goes; another may come [NIGHTS_BETWEEN_ASKS] saves on, if it isn't the last. */
    fun notNow(): TipAsk = copy(cardNight = null)

    /** "Don't ask again", or "Leave a tip": no card ever again. */
    fun stop(): TipAsk = copy(stopped = true, cardNight = null)

    companion object {
        /** The first card comes with the third night saved. */
        const val FIRST_ASK_AT = 3

        /** Saves between the first card and the second. */
        const val NIGHTS_BETWEEN_ASKS = 3

        /** Cards ever. */
        const val MAX_ASKS = 2
    }
}
