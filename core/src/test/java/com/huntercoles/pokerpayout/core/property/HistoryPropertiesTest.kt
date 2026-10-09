package com.huntercoles.pokerpayout.core.property

import com.huntercoles.pokerpayout.core.domain.history.NightCodec
import com.huntercoles.pokerpayout.core.domain.history.NightCsv
import com.huntercoles.pokerpayout.core.domain.history.NightResults
import com.huntercoles.pokerpayout.core.domain.history.SavedNight
import com.huntercoles.pokerpayout.core.testing.expect
import com.huntercoles.pokerpayout.core.testing.forAll
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate

/**
 * History (PP-037) end to end, as properties over random finished nights from the Bank ([BankNight]),
 * with player names in any script, quotes, commas, line breaks and emoji:
 *
 * - every night the Bank can finish saves and reads back exactly ([NightCodec]), so no saved night
 *   is ever skipped as unreadable;
 * - the CSV export, read back by an independent RFC 4180 reader, has one row of 18 fields per player
 *   with every name and amount intact.
 *
 * Robolectric, for the platform's org.json.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryPropertiesTest {

    /** A finished night as History would save it, from a random Bank night with random names. */
    private data class Finished(val night: SavedNight)

    @Test
    fun `every night the Bank can finish saves and reads back exactly`() =
        forAll(seed = 2026_1008_41L, iterations = 400, gen = finishedNights) { (night) ->
            val text = NightCodec.encode(night)
            val back = NightCodec.decode(text)
            expect(back == night) { "saved as $text\nread back as $back\nexpected $night" }
        }

    @Test
    fun `the CSV reads back with every name and amount intact`() =
        forAll(seed = 2026_1008_42L, iterations = 400, gen = Arb.list(finishedNights, 1..4)) { finished ->
            val nights = finished.mapIndexed { index, (night) -> night.copy(id = index + 1L) }
            val rows = readCsv(NightCsv.of(nights))
            expect(rows.first() == NightCsv.HEADER) { "header ${rows.first()}" }
            val players = nights.sortedWith(compareBy<SavedNight> { it.date }.thenBy { it.id }).flatMap { night ->
                night.players.map { night to it }
            }
            expect(rows.size == players.size + 1) { "${rows.size - 1} rows for ${players.size} players" }
            rows.drop(1).zip(players).forEach { (row, nightAndPlayer) ->
                val (night, player) = nightAndPlayer
                expect(row.size == NightCsv.HEADER.size) { "a row of ${row.size} fields: $row" }
                expect(row[PLAYER].removeFormulaGuard() == player.name) { "\"${player.name}\" reads back as \"${row[PLAYER]}\"" }
                expect(row[STRUCTURE].removeFormulaGuard() == night.structureName.orEmpty()) { "structure ${row[STRUCTURE]}" }
                expect(row[PRIZE].cents() == player.prizeCents && row[NET].cents() == player.netCents) { "amounts in $row" }
                expect(row[PLACE].toInt() == player.place) { "place in $row" }
            }
        }

    /**
     * An independent RFC 4180 reader: fields split on commas, a quoted field may hold commas, line
     * breaks and doubled quotes, records end in CRLF.
     */
    @Suppress("CyclomaticComplexMethod") // one state machine, clearer in one piece
    private fun readCsv(text: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var fields = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                quoted && char == '"' && text.getOrNull(index + 1) == '"' -> field.append('"').also { index++ }
                quoted && char == '"' -> quoted = false
                quoted -> field.append(char)
                char == '"' -> quoted = true
                char == ',' -> fields += field.toString().also { field.clear() }
                char == '\r' && text.getOrNull(index + 1) == '\n' -> {
                    fields += field.toString().also { field.clear() }
                    records += fields
                    fields = mutableListOf()
                    index++
                }
                else -> field.append(char)
            }
            index++
        }
        expect(!quoted && field.isEmpty() && fields.isEmpty()) { "the CSV doesn't end with a complete record" }
        return records
    }

    /** The CSV puts an apostrophe before text a spreadsheet would run as a formula. */
    private fun String.removeFormulaGuard(): String =
        if (startsWith("'") && getOrNull(1) in FORMULA_STARTS) drop(1) else this

    private fun String.cents(): Long = BigDecimal(this).movePointRight(2).longValueExact()

    private companion object {
        const val STRUCTURE = 1
        const val PLACE = 4
        const val PLAYER = 5
        const val PRIZE = 13
        const val NET = 17
        const val NAMES = 16
        val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r')

        /** Pieces of names: letters in several scripts, emoji, and everything CSV and JSON must escape. */
        private val pieces = listOf(
            "Dana", "O'Neil", "Smith, Jr", "\"Ace\"", "line\nbreak", "cr\rlf", "tab\there", "back\\slash",
            "=1+1", "+44", "-x", "@home", " ", "  ",
            "Zoë", "Ægir", "Søren", "Łukasz", "Петя", "Νίκος", "דנה", "علي", "राज", "王小明", "さくら",
            "\ud83c\udca1", "\ud83d\ude00", "e\u0301", "\u200d", "</script>", "{\"json\":1}", "\u0000", "\u2028",
        )

        /** A name as typed in the Bank; it keeps it trimmed, and "Player N" when left empty. */
        private val names = Arb.list(Arb.element(pieces), 0..4).map { it.joinToString("") }

        private val dates = Arb.long(LocalDate.of(2020, 1, 1).toEpochDay()..LocalDate.of(2035, 12, 31).toEpochDay())
            .map(LocalDate::ofEpochDay)

        /** A preset's name, as saved: trimmed and not blank, or none. */
        private val structureNames = names.map { it.trim().ifEmpty { "Friday" } }.orNull(nullProbability = 0.3)

        val finishedNights = Arb.bind(
            BankNight.scripts(maxSteps = 25),
            Arb.list(names, NAMES..NAMES),
            dates,
            structureNames,
            Arb.int(1..9_999),
        ) { script, nameList, date, structure, id ->
            val bank = BankNight(script).apply {
                play()
                finish()
                payEveryone()
            }
            val settlement = bank.settlement()
            val named = bank.players.keys.associateWith { nameList[it % NAMES] }
            val players = checkNotNull(NightResults.of(settlement, bank.players.values.toList(), named)) { "unfinished: $script" }
            Finished(SavedNight(id.toLong(), date, structure, settlement.pool.prizePoolCents, players))
        }
    }
}
