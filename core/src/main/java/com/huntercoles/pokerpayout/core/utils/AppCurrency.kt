package com.huntercoles.pokerpayout.core.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.huntercoles.pokerpayout.core.utils.SymbolPlacement.AFTER_SPACED
import com.huntercoles.pokerpayout.core.utils.SymbolPlacement.BEFORE
import com.huntercoles.pokerpayout.core.utils.SymbolPlacement.BEFORE_SPACED
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

/** Where an amount's symbol goes: "$5" ([BEFORE]), "R$ 5" ([BEFORE_SPACED]) or "5 €" ([AFTER_SPACED]). */
enum class SymbolPlacement { BEFORE, BEFORE_SPACED, AFTER_SPACED }

/**
 * A currency the host can show amounts in (PP-114, Tools > Currency). Only the display changes:
 * amounts stay whole cents ([Money]) whatever is picked, so switching back and forth changes nothing
 * saved, and the maths (payouts, settle-up, bounties) is the same in every currency.
 *
 * Each one carries its own conventions, fixed here rather than read from the phone's locale, so a
 * pick looks the same on every phone and in every share text: where the symbol goes (always held to
 * the number by a no-break space when spaced), the digit grouping ([indianGrouping]: 1,23,456) and
 * the decimal mark. A minus goes in front of everything: "-$10", "-10 €".
 *
 * [decimals] is 0 for a currency whose cents nobody uses (the yen): amounts show in whole units,
 * rounded half up, and money fields take whole numbers. What is saved keeps its cents.
 *
 * [key] is what is saved (`currency_prefs`); never rename one.
 */
enum class AppCurrency(
    val key: String,
    val symbol: String,
    val placement: SymbolPlacement,
    val groupSeparator: Char,
    val decimalMark: Char,
    val decimals: Int = 2,
    val indianGrouping: Boolean = false,
) {
    DOLLAR("dollar", "$", BEFORE, ',', '.'),
    EURO("euro", "€", AFTER_SPACED, '.', ','),
    EURO_FIRST("euro_first", "€", BEFORE, ',', '.'),
    POUND("pound", "£", BEFORE, ',', '.'),
    RUPEE("rupee", "₹", BEFORE, ',', '.', indianGrouping = true),
    REAL("real", "R$", BEFORE_SPACED, '.', ','),
    KRONA("krona", "kr", AFTER_SPACED, NO_BREAK_SPACE, ','),
    YEN("yen", "¥", BEFORE, ',', '.', decimals = 0),
    YUAN("yuan", "¥", BEFORE, ',', '.'),
    FRANC("franc", "CHF", BEFORE_SPACED, '’', '.'),
    ZLOTY("zloty", "zł", AFTER_SPACED, NO_BREAK_SPACE, ','),
    RUBLE("ruble", "₽", AFTER_SPACED, NO_BREAK_SPACE, ','),

    /** Plain numbers ("1,234.50"), for a game played for points or chips, or a currency not listed. */
    NONE("none", "", BEFORE, ',', '.'),
    ;

    /** True when cents show and can be typed; false for the yen. */
    val hasCents: Boolean get() = decimals > 0

    /** The symbol a money field shows before the amount, or null. */
    val fieldPrefix: String? get() = symbol.takeIf { it.isNotEmpty() && placement != AFTER_SPACED }

    /** The symbol a money field shows after the amount ("€"), or null. */
    val fieldSuffix: String? get() = symbol.takeIf { it.isNotEmpty() && placement == AFTER_SPACED }

    /**
     * [cents] as this currency shows it: "$1,234.50", "1.234,50 €", "₹1,23,456.50", "¥1,235".
     * [alwaysCents] false drops cents that are zero ("$450", "$95.50": copy rules, design spec §8).
     * Exact for every [Long], [Long.MIN_VALUE] included.
     */
    fun format(cents: Long, alwaysCents: Boolean = false): String {
        val whole = abs(cents / Money.CENTS_PER_DOLLAR)
        val fraction = abs(cents % Money.CENTS_PER_DOLLAR)
        val units = if (!hasCents && fraction * 2 >= Money.CENTS_PER_DOLLAR) whole + 1 else whole
        val showFraction = hasCents && (alwaysCents || fraction != 0L)
        val number = group(units) + if (showFraction) "$decimalMark${fraction.toString().padStart(decimals, '0')}" else ""
        val shownAsZero = if (hasCents) cents == 0L else units == 0L
        val sign = if (cents < 0 && !shownAsZero) "-" else ""
        return sign + withSymbol(number)
    }

    private fun withSymbol(number: String): String = when {
        symbol.isEmpty() -> number
        placement == BEFORE -> symbol + number
        placement == BEFORE_SPACED -> "$symbol$NO_BREAK_SPACE$number"
        else -> "$number$NO_BREAK_SPACE$symbol"
    }

    /** "1,234,567", or in lakhs and crores "12,34,567": the last three digits, then twos. */
    private fun group(units: Long): String {
        val digits = units.toString()
        if (digits.length <= THOUSAND_DIGITS) return digits
        val size = if (indianGrouping) INDIAN_GROUP else THOUSAND_DIGITS
        val head = digits.dropLast(THOUSAND_DIGITS).reversed().chunked(size).joinToString("$groupSeparator").reversed()
        return head + groupSeparator + digits.takeLast(THOUSAND_DIGITS)
    }

    companion object {
        /** What the app always showed, and what an install from before PP-114 keeps. */
        val DEFAULT = DOLLAR

        /** The amount the picker shows each currency with: 123,456.50. */
        const val SAMPLE_CENTS = 12_345_650L

        /** The currency saved as [key], or null for none or one this version doesn't know. */
        fun byKey(key: String?): AppCurrency? = entries.firstOrNull { it.key == key }

        /**
         * The phone's currency for a new install: its locale's country's currency when the app has
         * it, plain numbers ([NONE]) when it doesn't (better than a wrong symbol), and the dollar for
         * a locale with no country. The euro goes after the amount ("12,50 €") except in Irish and
         * Maltese English, which write "€12.50".
         */
        fun forLocale(locale: Locale): AppCurrency {
            val code = runCatching { Currency.getInstance(locale)?.currencyCode }.getOrNull() ?: return DEFAULT
            return when (code) {
                "EUR" -> if (locale.language in EURO_FIRST_LANGUAGES) EURO_FIRST else EURO
                "GBP" -> POUND
                "INR" -> RUPEE
                "BRL" -> REAL
                "SEK", "NOK", "DKK", "ISK" -> KRONA
                "JPY" -> YEN
                "CNY" -> YUAN
                "CHF" -> FRANC
                "PLN" -> ZLOTY
                "RUB" -> RUBLE
                in DOLLAR_CODES -> DOLLAR
                else -> NONE
            }
        }

        private const val THOUSAND_DIGITS = 3
        private const val INDIAN_GROUP = 2

        /** Currencies written with a plain "$" where they are spent. */
        private val DOLLAR_CODES = setOf("USD", "CAD", "AUD", "NZD", "SGD", "HKD", "TWD", "MXN", "ARS", "CLP", "COP")

        /** Languages that put the euro sign first, in the English style ("€12.50"). */
        private val EURO_FIRST_LANGUAGES = setOf("en", "ga", "mt")
    }
}

/** Holds a symbol to its number and the groups of a number together, so neither breaks across lines. */
const val NO_BREAK_SPACE = ' '

/**
 * The currency every amount shows in: every screen, share text, snackbar and the CSV export format
 * through [FormatUtils], which reads it here. [current] is Compose state, so a screen that showed an
 * amount draws it again when the host picks another currency. `CurrencyPreferences` sets it when the
 * app starts (before any screen or the live clock) and on each pick; until then, as in tests and
 * previews, it is the dollar.
 */
object MoneyFormat {
    var current: AppCurrency by mutableStateOf(AppCurrency.DEFAULT)
}
