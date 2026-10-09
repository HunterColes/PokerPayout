package com.huntercoles.pokerpayout.core.utils

import com.huntercoles.pokerpayout.core.utils.AppCurrency.DOLLAR
import com.huntercoles.pokerpayout.core.utils.AppCurrency.EURO
import com.huntercoles.pokerpayout.core.utils.AppCurrency.EURO_FIRST
import com.huntercoles.pokerpayout.core.utils.AppCurrency.FRANC
import com.huntercoles.pokerpayout.core.utils.AppCurrency.KRONA
import com.huntercoles.pokerpayout.core.utils.AppCurrency.NONE
import com.huntercoles.pokerpayout.core.utils.AppCurrency.POUND
import com.huntercoles.pokerpayout.core.utils.AppCurrency.REAL
import com.huntercoles.pokerpayout.core.utils.AppCurrency.RUBLE
import com.huntercoles.pokerpayout.core.utils.AppCurrency.RUPEE
import com.huntercoles.pokerpayout.core.utils.AppCurrency.YEN
import com.huntercoles.pokerpayout.core.utils.AppCurrency.YUAN
import com.huntercoles.pokerpayout.core.utils.AppCurrency.ZLOTY
import org.junit.jupiter.api.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * PP-114: how each currency writes an amount. The table is the spec: one row per currency, the same
 * amounts in each (a big one, one with cents, a whole one, a settle-up debt, zero), written as a host
 * in that country expects. `_` stands for the no-break space that holds a symbol to its number (and
 * groups together in kr, zł and ₽), so the table stays readable.
 */
class AppCurrencyTest {

    /** Cents shown always ([FormatUtils.formatCents]): 1,234,567.89 / 12.50 / 450 / -47.25 / 0. */
    private val alwaysCents = mapOf(
        DOLLAR to listOf("$1,234,567.89", "$12.50", "$450.00", "-$47.25", "$0.00"),
        EURO to listOf("1.234.567,89_€", "12,50_€", "450,00_€", "-47,25_€", "0,00_€"),
        EURO_FIRST to listOf("€1,234,567.89", "€12.50", "€450.00", "-€47.25", "€0.00"),
        POUND to listOf("£1,234,567.89", "£12.50", "£450.00", "-£47.25", "£0.00"),
        RUPEE to listOf("₹12,34,567.89", "₹12.50", "₹450.00", "-₹47.25", "₹0.00"),
        REAL to listOf("R\$_1.234.567,89", "R\$_12,50", "R\$_450,00", "-R\$_47,25", "R\$_0,00"),
        KRONA to listOf("1_234_567,89_kr", "12,50_kr", "450,00_kr", "-47,25_kr", "0,00_kr"),
        YEN to listOf("¥1,234,568", "¥13", "¥450", "-¥47", "¥0"),
        YUAN to listOf("¥1,234,567.89", "¥12.50", "¥450.00", "-¥47.25", "¥0.00"),
        FRANC to listOf("CHF_1’234’567.89", "CHF_12.50", "CHF_450.00", "-CHF_47.25", "CHF_0.00"),
        ZLOTY to listOf("1_234_567,89_zł", "12,50_zł", "450,00_zł", "-47,25_zł", "0,00_zł"),
        RUBLE to listOf("1_234_567,89_₽", "12,50_₽", "450,00_₽", "-47,25_₽", "0,00_₽"),
        NONE to listOf("1,234,567.89", "12.50", "450.00", "-47.25", "0.00"),
    )

    /** Cents only when there are some ([FormatUtils.formatMoney]), the same amounts. */
    private val centsWhenAny = mapOf(
        DOLLAR to listOf("$1,234,567.89", "$12.50", "$450", "-$47.25", "$0"),
        EURO to listOf("1.234.567,89_€", "12,50_€", "450_€", "-47,25_€", "0_€"),
        EURO_FIRST to listOf("€1,234,567.89", "€12.50", "€450", "-€47.25", "€0"),
        POUND to listOf("£1,234,567.89", "£12.50", "£450", "-£47.25", "£0"),
        RUPEE to listOf("₹12,34,567.89", "₹12.50", "₹450", "-₹47.25", "₹0"),
        REAL to listOf("R\$_1.234.567,89", "R\$_12,50", "R\$_450", "-R\$_47,25", "R\$_0"),
        KRONA to listOf("1_234_567,89_kr", "12,50_kr", "450_kr", "-47,25_kr", "0_kr"),
        YEN to listOf("¥1,234,568", "¥13", "¥450", "-¥47", "¥0"),
        YUAN to listOf("¥1,234,567.89", "¥12.50", "¥450", "-¥47.25", "¥0"),
        FRANC to listOf("CHF_1’234’567.89", "CHF_12.50", "CHF_450", "-CHF_47.25", "CHF_0"),
        ZLOTY to listOf("1_234_567,89_zł", "12,50_zł", "450_zł", "-47,25_zł", "0_zł"),
        RUBLE to listOf("1_234_567,89_₽", "12,50_₽", "450_₽", "-47,25_₽", "0_₽"),
        NONE to listOf("1,234,567.89", "12.50", "450", "-47.25", "0"),
    )

    private val amounts = listOf(123_456_789L, 1_250L, 45_000L, -4_725L, 0L)

    private fun readable(text: String) = text.replace(NO_BREAK_SPACE, '_')

    @Test
    fun `every currency is in both tables`() {
        assertEquals(AppCurrency.entries.toSet(), alwaysCents.keys)
        assertEquals(AppCurrency.entries.toSet(), centsWhenAny.keys)
    }

    @Test
    fun `each currency writes amounts its own way, cents always`() {
        alwaysCents.forEach { (currency, expected) ->
            assertEquals(expected, amounts.map { readable(currency.format(it, alwaysCents = true)) }, "$currency")
            assertEquals(expected, amounts.map { readable(FormatUtils.formatCents(it, currency)) }, "$currency")
        }
    }

    @Test
    fun `each currency writes amounts its own way, cents only when there are some`() {
        centsWhenAny.forEach { (currency, expected) ->
            assertEquals(expected, amounts.map { readable(currency.format(it)) }, "$currency")
            assertEquals(expected, amounts.map { readable(FormatUtils.formatMoney(it, currency)) }, "$currency")
        }
    }

    @Test
    fun `a symbol is held to its number, so an amount never breaks across lines`() {
        AppCurrency.entries.forEach { currency ->
            val text = currency.format(123_456_789L)
            assertTrue(' ' !in text, "$currency: '$text' has a breaking space")
        }
    }

    @Test
    fun `lakhs and crores in rupees`() {
        assertEquals("₹999", RUPEE.format(99_900))
        assertEquals("₹1,000", RUPEE.format(100_000))
        assertEquals("₹99,999", RUPEE.format(9_999_900))
        assertEquals("₹1,00,000", RUPEE.format(10_000_000))
        assertEquals("₹1,00,00,000", RUPEE.format(1_000_000_000))
    }

    @Test
    fun `yen round half up to whole yen, and a debt that rounds to nothing has no minus`() {
        assertEquals("¥3", YEN.format(250))
        assertEquals("¥2", YEN.format(249))
        assertEquals("-¥3", YEN.format(-250))
        assertEquals("-¥2", YEN.format(-249))
        assertEquals("¥0", YEN.format(-49))
        assertEquals("¥1", YEN.format(50))
        assertEquals("¥0", YEN.format(49))
    }

    @Test
    fun `a cent of debt keeps its minus where cents show`() {
        assertEquals("-$0.01", DOLLAR.format(-1))
        assertEquals("-0,01 €", EURO.format(-1))
        assertEquals("-0.01", NONE.format(-1))
    }

    @Test
    fun `the largest and smallest amounts a Long holds are exact`() {
        assertEquals("$92,233,720,368,547,758.07", DOLLAR.format(Long.MAX_VALUE))
        assertEquals("-$92,233,720,368,547,758.08", DOLLAR.format(Long.MIN_VALUE))
        assertEquals("₹92,23,37,20,36,85,47,758.07", RUPEE.format(Long.MAX_VALUE))
        assertEquals("¥92,233,720,368,547,758", YEN.format(Long.MAX_VALUE))
        assertEquals("-¥92,233,720,368,547,758", YEN.format(Long.MIN_VALUE))
    }

    @Test
    fun `the formatter reads the picked currency, the dollar until one is picked`() {
        assertEquals(DOLLAR, MoneyFormat.current)
        assertEquals("$450", FormatUtils.formatMoney(45_000))
    }

    @Test
    fun `money fields put the symbol where the currency does`() {
        assertEquals("$", DOLLAR.fieldPrefix)
        assertNull(DOLLAR.fieldSuffix)
        assertEquals("R$", REAL.fieldPrefix)
        assertNull(EURO.fieldPrefix)
        assertEquals("€", EURO.fieldSuffix)
        assertEquals("kr", KRONA.fieldSuffix)
        assertNull(NONE.fieldPrefix)
        assertNull(NONE.fieldSuffix)
    }

    @Test
    fun `saved keys are unique, and each reads back`() {
        val keys = AppCurrency.entries.map { it.key }
        assertEquals(keys.distinct(), keys)
        AppCurrency.entries.forEach { assertEquals(it, AppCurrency.byKey(it.key)) }
        assertNull(AppCurrency.byKey(null))
        assertNull(AppCurrency.byKey("doubloon"))
    }

    @Test
    fun `saved keys never change`() {
        // Saved data (currency_prefs): renaming one would lose a host's pick on the next update
        assertEquals(
            listOf("dollar", "euro", "euro_first", "pound", "rupee", "real", "krona", "yen", "yuan", "franc", "zloty", "ruble", "none"),
            AppCurrency.entries.map { it.key },
        )
    }

    @Test
    fun `a new install follows the phone's country`() {
        val cases = mapOf(
            Locale.US to DOLLAR,
            Locale.CANADA to DOLLAR,
            Locale("es", "MX") to DOLLAR,
            Locale("en", "AU") to DOLLAR,
            Locale.GERMANY to EURO,
            Locale.ITALY to EURO,
            Locale.FRANCE to EURO,
            Locale("nl", "NL") to EURO,
            Locale("en", "IE") to EURO_FIRST,
            Locale.UK to POUND,
            Locale("en", "IN") to RUPEE,
            Locale("hi", "IN") to RUPEE,
            Locale("pt", "BR") to REAL,
            Locale("sv", "SE") to KRONA,
            Locale("nb", "NO") to KRONA,
            Locale("da", "DK") to KRONA,
            Locale.JAPAN to YEN,
            Locale.CHINA to YUAN,
            Locale("de", "CH") to FRANC,
            Locale("pl", "PL") to ZLOTY,
            Locale("ru", "RU") to RUBLE,
            Locale("id", "ID") to NONE, // rupiah: not in the list, so plain numbers rather than a wrong sign
            Locale("tr", "TR") to NONE,
            Locale.ENGLISH to DOLLAR, // no country: the dollar the app always showed
        )
        cases.forEach { (locale, currency) -> assertEquals(currency, AppCurrency.forLocale(locale), "$locale") }
    }

    @Test
    fun `the picker's sample shows each currency's grouping`() {
        assertEquals("$123,456.50", DOLLAR.format(AppCurrency.SAMPLE_CENTS, alwaysCents = true))
        assertEquals("₹1,23,456.50", RUPEE.format(AppCurrency.SAMPLE_CENTS, alwaysCents = true))
        assertEquals("¥123,457", YEN.format(AppCurrency.SAMPLE_CENTS, alwaysCents = true))
    }
}
