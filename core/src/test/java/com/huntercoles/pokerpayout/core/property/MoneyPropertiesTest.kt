package com.huntercoles.pokerpayout.core.property

import com.huntercoles.pokerpayout.core.utils.FormatUtils
import com.huntercoles.pokerpayout.core.utils.Money
import com.huntercoles.pokerpayout.core.utils.MoneyInput
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Locale

/**
 * Money at the edges of the app, as properties: what a money field shows in any of the JVM's
 * locales types back, key by key, to the same cents; nothing anyone can type makes the parser throw
 * or invent an amount; the displayed "$1,234.56" reads back exactly; and v1.1's saved Floats come
 * back to the cent below $131,072, as [Money.centsOfLegacyFloat] promises.
 */
class MoneyPropertiesTest {

    @Test
    fun `what a money field shows types back key by key to the same cents, in every locale`() =
        forAll(seed = 2026_1008_31L, iterations = 5_000, gen = amountsAndLocales) { (cents, locale) ->
            val shown = MoneyInput.format(cents, locale)
            val read = MoneyInput.parseCents(shown)
            expect(read == cents) { "$cents shows as \"$shown\" in $locale and reads back as $read" }
            (1..shown.length).forEach { typed ->
                val prefix = shown.take(typed)
                expect(MoneyInput.isAcceptable(prefix)) { "typing \"$shown\" in $locale is refused at \"$prefix\"" }
            }
        }

    @Test
    fun `nothing typed makes the parser throw or read more than the field allows`() =
        forAll(seed = 2026_1008_32L, iterations = 10_000, gen = typed) { text ->
            val cents = MoneyInput.parseCents(text)
            expect(cents == null || cents in 0L..MAX_FIELD_CENTS) { "\"$text\" reads as $cents" }
            // Text the field lets stand, with a digit in it, is always an amount
            if (MoneyInput.isAcceptable(text) && text.any { it in '0'..'9' }) {
                expect(cents != null) { "\"$text\" may stand in the field but reads as no amount" }
            }
        }

    @Test
    fun `the amounts on screen read back to the exact cents`() =
        forAll(seed = 2026_1008_33L, iterations = 5_000, gen = Arb.long(-MAX_SHOWN_CENTS..MAX_SHOWN_CENTS)) { cents ->
            val shown = FormatUtils.formatCents(cents)
            expect(shown.matches(CENTS_PATTERN)) { "$cents shows as \"$shown\"" }
            expect(dollarsIn(shown).movePointRight(2).longValueExact() == cents) { "$cents shows as \"$shown\"" }
            val short = FormatUtils.formatMoney(cents)
            expect(dollarsIn(short).movePointRight(2).longValueExact() == cents) { "$cents shows as \"$short\"" }
        }

    @Test
    fun `amounts saved as Floats by v1_1 come back to the exact cent below 131,072 dollars`() =
        forAll(seed = 2026_1008_34L, iterations = 10_000, gen = Arb.long(0L until LEGACY_EXACT_BELOW_CENTS)) { cents ->
            // What v1.1 stored: the amount typed, parsed to a Float
            val stored = BigDecimal.valueOf(cents).movePointLeft(2).toPlainString().toFloat()
            val back = Money.centsOfLegacyFloat(stored)
            expect(back == cents) { "$cents stored as ${stored}f comes back as $back" }
        }

    private fun dollarsIn(shown: String): BigDecimal = BigDecimal(shown.replace("$", "").replace(",", ""))

    private companion object {
        val MAX_FIELD_CENTS = "9".repeat(MoneyInput.MAX_WHOLE_DIGITS).toLong() * Money.CENTS_PER_DOLLAR + 99
        const val MAX_SHOWN_CENTS = 1_000_000_000_000_000L
        const val LEGACY_EXACT_BELOW_CENTS = 13_107_200L
        val CENTS_PATTERN = Regex("""-?\$\d{1,3}(,\d{3})*\.\d{2}""")

        /** Every amount a field holds, small ones as often as large, in any of the JVM's locales. */
        val amountsAndLocales = Arb.bind(
            Arb.long(0L..MAX_FIELD_CENTS),
            Arb.element(100L, 10_000L, 1_000_000L, MAX_FIELD_CENTS + 1),
            Arb.element(Locale.getAvailableLocales().toList()),
        ) { any, below, locale -> any % below to locale }

        /** What a keyboard can type into a money field: digits, separators, signs, spaces, other scripts' digits. */
        private val keys = listOf(
            '0', '1', '2', '5', '9', '.', ',', ' ', '-', '+', '\'', 'e', 'E', '$',
            ' ', // no-break space
            '٫', // Arabic decimal separator
            '٣', // Arabic-Indic three
            '３', // fullwidth three
            '१', // Devanagari one
        )
        val typed = Arb.list(Arb.element(keys), 0..14).map { it.joinToString("") }
    }
}
