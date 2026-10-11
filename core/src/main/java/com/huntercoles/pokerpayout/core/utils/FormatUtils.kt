package com.huntercoles.pokerpayout.core.utils

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Formatting for display.
 *
 * Amounts show in the currency the host picked ([MoneyFormat.current], PP-114): "$1,234.56",
 * "1.234,56 €", "¥1,235". Each currency's grouping and decimal mark come with it ([AppCurrency]), not
 * from the device locale, so a German phone showing dollars still reads "$1,234.56" rather than half
 * one convention and half the other ("$1.234,56"). Every money amount on screen, in a share text, a
 * snackbar or TalkBack goes through [formatMoney] or [formatCents]; nothing else writes a symbol.
 *
 * Other numbers (percentages, multipliers) keep [Locale.US] symbols: the app's text is English.
 * Text that the user edits (money input fields) follows the device locale instead; see [MoneyInput].
 */
object FormatUtils {
    private val displaySymbols = DecimalFormatSymbols(Locale.US)

    // DecimalFormat isn't thread-safe, so build one per call; this is not a hot path.
    private fun decimalFormat(pattern: String) = DecimalFormat(pattern, displaySymbols).apply {
        roundingMode = RoundingMode.HALF_UP
    }

    /**
     * A whole number of cents, exactly, cents always shown (where the currency has them).
     * Example: 123456 -> "$1,234.56", -1050 -> "-$10.50"; in euros "1.234,56 €", "-10,50 €".
     */
    fun formatCents(cents: Long, currency: AppCurrency = MoneyFormat.current): String =
        currency.format(cents, alwaysCents = true)

    /**
     * A whole number of cents the makeover way (copy rules, design spec section 8): cents only when
     * there are some. Example: 45000 -> "$450", 9550 -> "$95.50", -1000 -> "-$10".
     */
    fun formatMoney(cents: Long, currency: AppCurrency = MoneyFormat.current): String =
        currency.format(cents, alwaysCents = false)

    /**
     * Format a Double as decimal, removing trailing zeros
     * Example: 5.00 -> "5", 5.50 -> "5.5", 5.123 -> "5.12"
     */
    fun formatDecimal(value: Double): String {
        return decimalFormat("0.##").format(value)
    }

    /**
     * Format a Double as percentage (value is already in percentage form, not decimal)
     * Example: 50.0 -> "50%", 33.333333 -> "33.33%", 25.25 -> "25.25%"
     */
    fun formatPercent(value: Double): String {
        return "${decimalFormat("0.##").format(value)}%"
    }

    /**
     * Format a Double as multiplier
     * Example: 1.5 -> "1.5x", 2.0 -> "2x"
     */
    fun formatMultiplier(value: Double): String {
        return "${decimalFormat("0.##").format(value)}x"
    }
}
