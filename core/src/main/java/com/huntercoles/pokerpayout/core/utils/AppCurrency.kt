package com.huntercoles.pokerpayout.core.utils

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.huntercoles.pokerpayout.core.utils.DigitStyle.APOSTROPHE
import com.huntercoles.pokerpayout.core.utils.DigitStyle.COMMA
import com.huntercoles.pokerpayout.core.utils.DigitStyle.LAKH
import com.huntercoles.pokerpayout.core.utils.DigitStyle.POINT
import com.huntercoles.pokerpayout.core.utils.DigitStyle.SPACE
import com.huntercoles.pokerpayout.core.utils.SymbolPlacement.AFTER_SPACED
import com.huntercoles.pokerpayout.core.utils.SymbolPlacement.BEFORE
import com.huntercoles.pokerpayout.core.utils.SymbolPlacement.BEFORE_SPACED
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

/** Where an amount's symbol goes: "$5" ([BEFORE]), "R$ 5" ([BEFORE_SPACED]) or "5 €" ([AFTER_SPACED]). */
enum class SymbolPlacement { BEFORE, BEFORE_SPACED, AFTER_SPACED }

/**
 * How a currency writes the number itself: the mark between groups of digits, the decimal mark, and
 * whether the groups after the first thousand go in twos (lakhs and crores, [LAKH]).
 */
enum class DigitStyle(val groupSeparator: Char, val decimalMark: Char, val indianGroups: Boolean = false) {
    /** 1,234,567.50 */
    POINT(',', '.'),

    /** 1.234.567,50 */
    COMMA('.', ','),

    /** 1 234 567,50, the groups held together by no-break spaces. */
    SPACE(NO_BREAK_SPACE, ','),

    /** 1’234’567.50 */
    APOSTROPHE('’', '.'),

    /** 12,34,567.50 */
    LAKH(',', '.', indianGroups = true),
}

/**
 * A currency the host can show amounts in (PP-114, Tools > Currency). Only the display changes:
 * amounts stay whole cents ([Money]) whatever is picked, so switching back and forth changes nothing
 * saved, and the maths (payouts, settle-up, bounties) is the same in every currency.
 *
 * Each one carries its own conventions, fixed here rather than read from the phone's locale, so a
 * pick looks the same on every phone and in every share text: where the symbol goes (held to the
 * number by a no-break space when spaced) and how the digits are written ([digits]). A minus goes in
 * front of everything: "-$10", "-10 €".
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
    val digits: DigitStyle,
    val decimals: Int = 2,
) {
    DOLLAR("dollar", "$", BEFORE, POINT),
    EURO("euro", "€", AFTER_SPACED, COMMA),
    EURO_FIRST("euro_first", "€", BEFORE, POINT),
    POUND("pound", "£", BEFORE, POINT),
    RUPEE("rupee", "₹", BEFORE, LAKH),
    REAL("real", "R$", BEFORE_SPACED, COMMA),
    KRONA("krona", "kr", AFTER_SPACED, SPACE),
    YEN("yen", "¥", BEFORE, POINT, decimals = 0),
    YUAN("yuan", "¥", BEFORE, POINT),
    FRANC("franc", "CHF", BEFORE_SPACED, APOSTROPHE),
    ZLOTY("zloty", "zł", AFTER_SPACED, SPACE),
    RUBLE("ruble", "₽", AFTER_SPACED, SPACE),

    /** Plain numbers ("1,234.50"), for a game played for points or chips, or a currency not listed. */
    NONE("none", "", BEFORE, POINT),
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
        val decimalPart = if (showFraction) "${digits.decimalMark}${fraction.toString().padStart(decimals, '0')}" else ""
        val number = group(units) + decimalPart
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

    /** "1,234,567", or in lakhs and crores "12,34,567": the last three digits, then threes (or twos). */
    private fun group(units: Long): String {
        val text = units.toString()
        if (text.length <= THOUSAND_DIGITS) return text
        val size = if (digits.indianGroups) INDIAN_GROUP else THOUSAND_DIGITS
        val separator = digits.groupSeparator
        val head = text.dropLast(THOUSAND_DIGITS).reversed().chunked(size).joinToString("$separator").reversed()
        return head + separator + text.takeLast(THOUSAND_DIGITS)
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
            val euroFirst = code == "EUR" && locale.language in EURO_FIRST_LANGUAGES
            return if (euroFirst) EURO_FIRST else BY_CODE[code] ?: NONE
        }

        private const val THOUSAND_DIGITS = 3
        private const val INDIAN_GROUP = 2

        /** ISO 4217 codes to the currency that writes them: every "$" currency is the dollar, every krona or krone "kr". */
        private val BY_CODE: Map<String, AppCurrency> =
            listOf("USD", "CAD", "AUD", "NZD", "SGD", "HKD", "TWD", "MXN", "ARS", "CLP", "COP").associateWith { DOLLAR } +
                listOf("SEK", "NOK", "DKK", "ISK").associateWith { KRONA } +
                mapOf(
                    "EUR" to EURO, "GBP" to POUND, "INR" to RUPEE, "BRL" to REAL, "JPY" to YEN, "CNY" to YUAN,
                    "CHF" to FRANC, "PLN" to ZLOTY, "RUB" to RUBLE,
                )

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
    private val state = mutableStateOf(AppCurrency.DEFAULT)

    /**
     * Set in a snapshot of its own, applied at once: open compositions see it as any state change,
     * and nothing waits on the main thread's next frame to publish it (a change made with no screen
     * showing, at start or in a test, is done when this returns).
     */
    var current: AppCurrency
        get() = state.value
        set(value) {
            Snapshot.withMutableSnapshot { state.value = value }
        }
}
