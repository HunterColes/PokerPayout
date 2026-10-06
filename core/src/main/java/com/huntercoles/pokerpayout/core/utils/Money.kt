package com.huntercoles.pokerpayout.core.utils

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Money is a whole number of cents in a [Long]. That is exact for any amount a home game sees, and
 * sums can't drift the way `Double` and `Float` sums do. Dollars appear only at the edges: legacy
 * preferences, test fixtures and display.
 */
object Money {
    const val CENTS_PER_DOLLAR = 100L

    /** The nearest cent to [dollars], half up: 12.345 -> 1235. Non-finite input gives 0. */
    fun centsOf(dollars: Double): Long =
        if (dollars.isFinite()) {
            BigDecimal.valueOf(dollars).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
        } else {
            0L
        }

    /**
     * The nearest cent to a `Float` that v1.1.x stored. The Float holds the amount the user typed to
     * about 7 significant digits, so every amount below $131,072 converts back exactly (12.5f -> 1250,
     * 0.1f -> 10, 123456.78f -> 12345678). Above that the Float had already lost the cents.
     */
    fun centsOfLegacyFloat(value: Float): Long =
        if (value.isFinite()) {
            BigDecimal(value.toDouble()).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
        } else {
            0L
        }

    fun dollarsOf(cents: Long): Double = cents / CENTS_PER_DOLLAR.toDouble()
}

/**
 * The text of a money input field.
 *
 * Parsing doesn't depend on the device locale: both '.' and ',' are decimal separators, because a
 * comma-decimal keyboard (German, French, ...) offers only ',' and a US one only '.'. Grouping
 * separators, signs and more than two decimals are rejected while typing.
 *
 * The field shows amounts with the device locale's decimal separator ("12,50" in Germany), so the
 * key the keyboard offers matches what is on screen.
 */
object MoneyInput {
    /** At most $999,999,999.99. */
    const val MAX_WHOLE_DIGITS = 9
    private const val MAX_FRACTION_DIGITS = 2

    /** True if [text] may stand in the field while the user types; "" and "12," are fine. */
    fun isAcceptable(text: String): Boolean {
        val separatorCount = text.count { it.isSeparator() }
        val wellFormed = separatorCount <= 1 && text.all { it in '0'..'9' || it.isSeparator() }
        val whole = text.takeWhile { !it.isSeparator() }
        val fraction = text.dropWhile { !it.isSeparator() }.drop(1)
        return wellFormed && whole.length <= MAX_WHOLE_DIGITS && fraction.length <= MAX_FRACTION_DIGITS
    }

    /** The amount in [text], in cents; null when there is no amount yet ("", ","). */
    fun parseCents(text: String): Long? {
        val trimmed = text.trim()
        val hasDigits = trimmed.any { it in '0'..'9' }
        if (!hasDigits || !isAcceptable(trimmed)) return null
        val whole = trimmed.takeWhile { !it.isSeparator() }.ifEmpty { "0" }
        val fraction = trimmed.dropWhile { !it.isSeparator() }.drop(1).padEnd(MAX_FRACTION_DIGITS, '0')
        return whole.toLong() * Money.CENTS_PER_DOLLAR + fraction.toLong()
    }

    /** How the field shows [cents]: "25" for whole amounts, else "12.50" or "12,50" by locale. */
    fun format(cents: Long, locale: Locale = Locale.getDefault()): String {
        val whole = cents / Money.CENTS_PER_DOLLAR
        val fraction = cents % Money.CENTS_PER_DOLLAR
        return if (fraction == 0L) {
            whole.toString()
        } else {
            "$whole${decimalSeparator(locale)}${fraction.toString().padStart(MAX_FRACTION_DIGITS, '0')}"
        }
    }

    /** ',' for comma-decimal locales, otherwise '.'. Both parse, whatever the locale. */
    fun decimalSeparator(locale: Locale = Locale.getDefault()): Char =
        if (DecimalFormatSymbols.getInstance(locale).decimalSeparator == ',') ',' else '.'

    private fun Char.isSeparator() = this == '.' || this == ','
}
