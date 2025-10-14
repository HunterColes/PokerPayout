package com.huntercoles.pokerpayout.core.utils

import java.text.DecimalFormat

/**
 * Centralized formatting utilities for consistent display across the app
 */
object FormatUtils {
    private val currencyFormatter = DecimalFormat("#,##0.00")
    private val currencyFormatterNoCents = DecimalFormat("#,##0")
    private val decimalFormatter = DecimalFormat("0.##")
    private val percentFormatter = DecimalFormat("0.##")
    
    /**
     * Format a Double as currency with dollar sign
     * Example: 1234.56 -> "$1,234.56"
     */
    fun formatCurrency(amount: Double): String {
        return "$" + currencyFormatter.format(amount)
    }
    
    /**
     * Format a Double as currency without cents (whole dollars)
     * Example: 1234.56 -> "$1,235"
     */
    fun formatCurrencyWhole(amount: Double): String {
        return "$" + currencyFormatterNoCents.format(amount)
    }
    
    /**
     * Format a Double as negative currency
     * Example: 1234.56 -> "-$1,234.56"
     */
    fun formatNegativeCurrency(amount: Double): String {
        val absValue = kotlin.math.abs(amount)
        return "-$" + currencyFormatter.format(absValue)
    }
    
    /**
     * Format a Double as decimal, removing trailing zeros
     * Example: 5.00 -> "5", 5.50 -> "5.5", 5.123 -> "5.12"
     */
    fun formatDecimal(value: Double): String {
        return String.format("%.2f", value).trimEnd('0').trimEnd('.')
    }
    
    /**
     * Format a Double as percentage (value is already in percentage form, not decimal)
     * Example: 50.0 -> "50%", 33.333333 -> "33.33%", 25.25 -> "25.25%"
     */
    fun formatPercent(value: Double): String {
        return "${percentFormatter.format(value)}%"
    }
    
    /**
     * Format a Double as multiplier
     * Example: 1.5 -> "1.5x", 2.0 -> "2x"
     */
    fun formatMultiplier(value: Double): String {
        return "${decimalFormatter.format(value)}x"
    }
}
