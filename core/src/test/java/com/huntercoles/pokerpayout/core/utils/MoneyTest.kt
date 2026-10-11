package com.huntercoles.pokerpayout.core.utils

import com.huntercoles.pokerpayout.core.testing.withCurrency
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Money in cents, money input text, and display formatting, in US, German and French locales. */
class MoneyTest {

    private lateinit var savedLocale: Locale

    @BeforeEach
    fun saveLocale() {
        savedLocale = Locale.getDefault()
    }

    @AfterEach
    fun restoreLocale() {
        Locale.setDefault(savedLocale)
    }

    private fun <T> inLocale(locale: Locale, block: () -> T): T {
        Locale.setDefault(locale)
        return try {
            block()
        } finally {
            Locale.setDefault(savedLocale)
        }
    }

    // ---- dollars and legacy Floats to cents

    @Test
    fun `dollars convert to the nearest cent, half up`() {
        assertEquals(1250L, Money.centsOf(12.5))
        assertEquals(1235L, Money.centsOf(12.345))
        assertEquals(1L, Money.centsOf(0.005))
        assertEquals(12345678L, Money.centsOf(123456.78))
        assertEquals(0L, Money.centsOf(Double.NaN))
    }

    @Test
    fun `legacy Float amounts below 131072 dollars come back to the exact cent`() {
        // What v1.1.12 stored for amounts users typed
        assertEquals(1250L, Money.centsOfLegacyFloat(12.5f))
        assertEquals(10L, Money.centsOfLegacyFloat(0.1f))
        assertEquals(3333L, Money.centsOfLegacyFloat(33.33f))
        assertEquals(9999999L, Money.centsOfLegacyFloat(99999.99f))
        assertEquals(12345678L, Money.centsOfLegacyFloat(123456.78f)) // stored as 123456.78125
        // Every cent amount up to $1,000 survives the Float round trip
        (0L..100_000L).forEach { cents ->
            assertEquals(cents, Money.centsOfLegacyFloat((cents / 100.0).toFloat()), "for $cents cents")
        }
    }

    // ---- what money fields accept

    @Test
    fun `typing 12,50 or 12_50 keystroke by keystroke is accepted in every locale`() {
        listOf(Locale.US, Locale.GERMANY, Locale.FRANCE).forEach { locale ->
            inLocale(locale) {
                listOf("1", "12", "12.", "12.5", "12.50", "12,", "12,5", "12,50", ",5", "").forEach { text ->
                    assertTrue(MoneyInput.isAcceptable(text), "'$text' in $locale")
                }
            }
        }
    }

    @Test
    fun `grouping, signs, letters, a second separator and a third decimal are rejected`() {
        listOf("1,234.56", "1.234,56", "-5", "+5", "12a", "1 000", "12.505", "12,505", "1.2.3", "1234567890")
            .forEach { assertFalse(MoneyInput.isAcceptable(it), "'$it'") }
    }

    @Test
    fun `parsing reads comma and dot decimals the same in Germany and France`() {
        listOf(Locale.GERMANY, Locale.FRANCE, Locale.US).forEach { locale ->
            inLocale(locale) {
                assertEquals(1250L, MoneyInput.parseCents("12,50"), "$locale")
                assertEquals(1250L, MoneyInput.parseCents("12.50"), "$locale")
                assertEquals(1250L, MoneyInput.parseCents("12,5"), "$locale")
                assertEquals(1200L, MoneyInput.parseCents("12,"), "$locale")
                assertEquals(50L, MoneyInput.parseCents(",5"), "$locale")
                assertEquals(2500L, MoneyInput.parseCents("25"), "$locale")
                assertEquals(99_999_999_999L, MoneyInput.parseCents("999999999,99"), "$locale")
            }
        }
    }

    @Test
    fun `an empty or separator-only field has no amount yet`() {
        assertNull(MoneyInput.parseCents(""))
        assertNull(MoneyInput.parseCents(" "))
        assertNull(MoneyInput.parseCents(","))
        assertNull(MoneyInput.parseCents("."))
        assertNull(MoneyInput.parseCents("1.234,5"))
    }

    @Test
    fun `fields show the device locale's decimal separator, and parse back to the same cents`() {
        assertEquals("12.50", MoneyInput.format(1250, Locale.US))
        assertEquals("12,50", MoneyInput.format(1250, Locale.GERMANY))
        assertEquals("12,50", MoneyInput.format(1250, Locale.FRANCE))
        assertEquals("25", MoneyInput.format(2500, Locale.GERMANY))
        assertEquals("0,05", MoneyInput.format(5, Locale.FRANCE))
        listOf(Locale.US, Locale.GERMANY, Locale.FRANCE).forEach { locale ->
            listOf(0L, 1L, 10L, 99L, 100L, 1250L, 123456L, 99_999_999_999L).forEach { cents ->
                assertEquals(cents, MoneyInput.parseCents(MoneyInput.format(cents, locale)), "$cents in $locale")
            }
        }
        assertEquals(',', MoneyInput.decimalSeparator(Locale.GERMANY))
        assertEquals('.', MoneyInput.decimalSeparator(Locale.UK))
    }

    // ---- display

    @Test
    fun `amounts display as dollars with US grouping whatever the device locale`() {
        listOf(Locale.US, Locale.GERMANY, Locale.FRANCE).forEach { locale ->
            inLocale(locale) {
                assertEquals("$1,234.56", FormatUtils.formatCents(123456), "$locale")
                assertEquals("-$10.50", FormatUtils.formatCents(-1050), "$locale")
                assertEquals("$0.05", FormatUtils.formatCents(5), "$locale")
                assertEquals("$1,000,000.00", FormatUtils.formatCents(100_000_000), "$locale")
                assertEquals("33.33%", FormatUtils.formatPercent(33.333333), "$locale")
                assertEquals("12.5", FormatUtils.formatDecimal(12.5), "$locale")
            }
        }
    }

    @Test
    fun `negative currency has the sign before the dollar sign`() {
        assertEquals("-$10.50", FormatUtils.formatCents(-1050))
        assertEquals("-$0.01", FormatUtils.formatCents(-1))
        assertEquals("$0.00", FormatUtils.formatCents(0))
        assertEquals("-$10", FormatUtils.formatMoney(-1000))
    }

    // ---- money fields in a currency without cents (the yen, PP-114)

    @Test
    fun `in yen a field takes whole numbers only`() {
        listOf("", "1", "12", "3000", "999999999").forEach { assertTrue(MoneyInput.isAcceptable(it, decimals = 0), "'$it'") }
        listOf("12.", "12,", "12.5", ",5", "1,000", "1234567890", "-5").forEach {
            assertFalse(MoneyInput.isAcceptable(it, decimals = 0), "'$it'")
        }
        assertEquals(300_000L, MoneyInput.parseCents("3000", decimals = 0))
        assertNull(MoneyInput.parseCents("12.50", decimals = 0))
        assertNull(MoneyInput.parseCents("", decimals = 0))
    }

    @Test
    fun `in yen a field shows whole yen, rounded half up, and parses back to whole yen`() {
        assertEquals("3000", MoneyInput.format(300_000, Locale.JAPAN, decimals = 0))
        assertEquals("13", MoneyInput.format(1_250, Locale.JAPAN, decimals = 0))
        assertEquals("12", MoneyInput.format(1_249, Locale.JAPAN, decimals = 0))
        assertEquals("0", MoneyInput.format(0, Locale.JAPAN, decimals = 0))
        listOf(0L, 100L, 300_000L, 99_999_999_900L).forEach { cents ->
            val shown = MoneyInput.format(cents, Locale.JAPAN, decimals = 0)
            assertEquals(cents, MoneyInput.parseCents(shown, decimals = 0), "$cents")
        }
    }

    @Test
    fun `fields follow the picked currency's cents by default`() {
        withCurrency(AppCurrency.YEN) {
            assertFalse(MoneyInput.isAcceptable("12.5"))
            assertEquals("13", MoneyInput.format(1_250, Locale.US))
            assertEquals(1_200L, MoneyInput.parseCents("12"))
        }
        withCurrency(AppCurrency.EURO) {
            assertTrue(MoneyInput.isAcceptable("12,5"))
            assertEquals("12,50", MoneyInput.format(1_250, Locale.GERMANY))
            assertEquals(1_250L, MoneyInput.parseCents("12,50"))
        }
    }
}
