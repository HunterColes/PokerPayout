package com.huntercoles.pokerpayout.core.utils

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/**
 * Formatting for display.
 *
 * Amounts are always shown as "$1,234.56", whatever the device locale. The app's text is English
 * and its money sign is "$", and a German device used to get "$1.234,56", half one convention and
 * half the other. The symbols are therefore pinned to [Locale.US] here.
 *
 * Text that the user edits (money input fields) follows the device locale instead; see [MoneyInput].
 */
object FormatUtils {
    private val displaySymbols = DecimalFormatSymbols(Locale.US)

    // DecimalFormat isn't thread-safe, so build one per call; this is not a hot path.
    private fun decimalFormat(pattern: String) = DecimalFormat(pattern, displaySymbols).apply {
        roundingMode = RoundingMode.HALF_UP
    }

    /**
     * Format a Double as currency with dollar sign
     * Example: 1234.56 -> "$1,234.56", -10.5 -> "-$10.50"
     */
    fun formatCurrency(amount: Double): String {
        val digits = decimalFormat("#,##0.00").format(abs(amount))
        val sign = if (amount < 0 && digits != "0.00") "-" else ""
        return sign + "$" + digits
    }

    /**
     * Format a whole number of cents, exactly.
     * Example: 123456 -> "$1,234.56", -1050 -> "-$10.50"
     */
    fun formatCents(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val magnitude = abs(cents)
        val dollars = magnitude / Money.CENTS_PER_DOLLAR
        val remainder = magnitude % Money.CENTS_PER_DOLLAR
        val grouped = dollars.toString().reversed().chunked(DIGITS_PER_GROUP).joinToString(",").reversed()
        return sign + "$" + grouped + "." + remainder.toString().padStart(2, '0')
    }

    /**
     * A whole number of cents the makeover way (copy rules, design spec section 8): cents only when
     * there are some. Example: 45000 -> "$450", 9550 -> "$95.50", -1000 -> "-$10".
     */
    fun formatMoney(cents: Long): String {
        val full = formatCents(cents)
        return if (cents % Money.CENTS_PER_DOLLAR == 0L) full.removeSuffix(".00") else full
    }

    /**
     * Format a Double as currency without cents (whole dollars)
     * Example: 1234.56 -> "$1,235"
     */
    fun formatCurrencyWhole(amount: Double): String {
        return "$" + decimalFormat("#,##0").format(amount)
    }

    /**
     * Format a Double as negative currency
     * Example: 1234.56 -> "-$1,234.56"
     */
    fun formatNegativeCurrency(amount: Double): String {
        return "-$" + decimalFormat("#,##0.00").format(abs(amount))
    }

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

    private const val DIGITS_PER_GROUP = 3
}
