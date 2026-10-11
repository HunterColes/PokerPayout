package com.huntercoles.pokerpayout.core.domain.model

import com.huntercoles.pokerpayout.core.utils.AppCurrency
import com.huntercoles.pokerpayout.core.utils.FormatUtils
import com.huntercoles.pokerpayout.core.utils.Money
import com.huntercoles.pokerpayout.core.utils.MoneyFormat
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The house rake (PP-145): the share of the prize pool the host keeps, as a percentage set in the
 * Tournament setup. 0% is the default, and what every game, preset, backup and saved night from
 * before PP-145 has, so for them nothing changes.
 *
 * - **The scale:** 0% to 25%, in steps of 0.5% ([STEP_BASIS_POINTS]). Home games and charity nights
 *   take whole or half percents (2.5%, 5%, 10%); a cardroom's fee on a small buy-in is 10% to 20%
 *   ($100 + $20 is 16.7%). Above a quarter it is no longer a rake, and the cap catches a slip of the
 *   finger (50 for 5.0). It is saved in basis points (hundredths of a percent), so finer steps could
 *   come later without a migration.
 * - **What it comes off:** the prize pool only, the buy-ins, rebuys and add-ons. Bounties are never
 *   raked: they go in full to whoever knocks the player out (or the champion), as in cardrooms, where
 *   a bounty tournament's fee comes out of the prize part of the entry and never out of the bounty.
 *   Food is spent, not paid out, so there is nothing to rake.
 * - **Rounding:** [centsOf] rounds down to the cent (to the whole yen in a currency without cents),
 *   so the house never takes more than its percentage and the odd fraction stays with the players.
 * - **Exact:** the places share what is left ([PoolBreakdown.prizePoolCents]), so the payouts plus
 *   the rake add up to the money taken in for prizes, to the cent.
 */
@JvmInline
value class HouseRake private constructor(val basisPoints: Int) {

    /** No rake: everything taken in for prizes is paid out. */
    val isNone: Boolean get() = basisPoints == 0

    /** "5%", "2.5%", "0%". */
    val label: String get() = FormatUtils.formatPercent(basisPoints / BASIS_POINTS_PER_PERCENT.toDouble())

    /**
     * The rake on [prizeInCents] (the buy-ins, rebuys and add-ons), in cents: [basisPoints] of it,
     * rounded down to the cent, or to the whole yen in [currency]s without cents. Exact for any pool.
     */
    fun centsOf(prizeInCents: Long, currency: AppCurrency = MoneyFormat.current): Long {
        if (basisPoints == 0 || prizeInCents <= 0L) return 0L
        // prizeIn * bp / 10,000 rounded down, without overflowing for any Long pool
        val whole = prizeInCents / BASIS_POINTS_PER_WHOLE * basisPoints
        val part = prizeInCents % BASIS_POINTS_PER_WHOLE * basisPoints / BASIS_POINTS_PER_WHOLE
        val coin = if (currency.hasCents) 1L else Money.CENTS_PER_DOLLAR
        return (whole + part) / coin * coin
    }

    /** What the setup's field shows: "0", "5", "2.5" (or "2,5" where the phone writes a decimal comma). */
    fun fieldText(decimalSeparator: Char = '.'): String {
        val whole = basisPoints / BASIS_POINTS_PER_PERCENT
        val half = basisPoints % BASIS_POINTS_PER_PERCENT
        return if (half == 0) "$whole" else "$whole$decimalSeparator${half / HALF_PERCENT_DIGIT}"
    }

    override fun toString(): String = label

    companion object {
        /** 25%: the most the setup takes. */
        const val MAX_BASIS_POINTS = 2_500

        /** 0.5%: the setup's step. */
        const val STEP_BASIS_POINTS = 50

        private const val BASIS_POINTS_PER_PERCENT = 100
        private const val BASIS_POINTS_PER_WHOLE = 10_000L
        private const val HALF_PERCENT_DIGIT = 10
        private const val MAX_WHOLE_DIGITS = 3
        private const val MAX_FRACTION_DIGITS = 2

        val NONE = HouseRake(0)

        /**
         * [basisPoints] on the setup's scale: below 0 is none, above 25% is 25%, and anything between
         * steps goes to the nearest 0.5% (halves up). Saved data and typed text both come in this way.
         */
        fun of(basisPoints: Int): HouseRake {
            val clamped = basisPoints.coerceIn(0, MAX_BASIS_POINTS)
            val snapped = (clamped + STEP_BASIS_POINTS / 2) / STEP_BASIS_POINTS * STEP_BASIS_POINTS
            return HouseRake(snapped.coerceAtMost(MAX_BASIS_POINTS))
        }

        /**
         * A percentage as typed: "5", "2.5", "2,5" (either decimal mark), "5%", or nothing (0%). Put on
         * the scale ([of]); null when it isn't a number, so the field can keep what is saved.
         */
        fun parse(text: String): HouseRake? {
            val typed = text.trim().removeSuffix("%").trim().replace(',', '.')
            return when {
                typed.isEmpty() -> NONE
                !isTypable(typed) || typed.none { it.isDigit() } -> null
                else -> of(BigDecimal(typed).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toInt())
            }
        }

        /**
         * True when [text] could be on its way to a percentage ("", "2", "2.", "2,5"): up to three
         * digits, then a decimal mark and up to two more. What the setup's field lets through.
         */
        fun isTypable(text: String): Boolean {
            val normalized = text.replace(',', '.')
            val whole = normalized.substringBefore('.')
            val fraction = normalized.substringAfter('.', "")
            return normalized.count { it == '.' } <= 1 && (whole + fraction).all { it.isDigit() } &&
                whole.length <= MAX_WHOLE_DIGITS && fraction.length <= MAX_FRACTION_DIGITS
        }
    }
}
