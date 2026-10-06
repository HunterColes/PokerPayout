package com.huntercoles.pokerpayout.tools.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.tools.poker.Cards
import com.huntercoles.pokerpayout.tools.seats.DrawnTable
import com.huntercoles.pokerpayout.tools.seats.SeatDraw
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The seat draw's words from the real string resources (PP-036): the text it shares (seats per
 * table, the button and the blinds, the rule), the table plan, and the tie line.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeatDrawTextTest {

    /** The rule keeps each suit with its "beats", so a line never starts with a lone symbol. */
    private val nbsp = '\u00A0'

    private val text = SeatDrawText(ApplicationProvider.getApplicationContext<Context>().resources)

    /** Suit symbols carry U+FE0E (text style, not emoji). */
    private fun String.suits() = replace("♠", "♠︎").replace("♥", "♥︎").replace("♦", "♦︎").replace("♣", "♣︎")

    private val seated = SeatDraw(
        listOf(
            DrawnTable(1, listOf("Dana", "Marcus", "Priya", "Theo", "Jo")),
            DrawnTable(2, listOf("Sam", "Alex", "Rita", "Ben")),
        ),
    )

    private val dealt = SeatDraw(
        listOf(
            seated.tables[0].copy(buttonCards = Cards.parseAll("7c Ks 2d Kh 9s")),
            seated.tables[1].copy(buttonCards = Cards.parseAll("Ac 3d 5h 6s")),
        ),
    )

    @Test
    fun `shared text lists each table's seats, then the button with its card, then the rule`() {
        val expected = """
            Seat draw: 9 players at 2 tables

            Table 1
            1. Dana
            2. Marcus · button
            3. Priya · small blind
            4. Theo · big blind
            5. Jo
            Button: Marcus, seat 2, with K♠

            Table 2
            1. Sam · button
            2. Alex · small blind
            3. Rita · big blind
            4. Ben
            Button: Sam, seat 1, with A♣

            High card gets the button. If ranks tie, the suit decides: ♠ beats$nbsp♥ beats$nbsp♦ beats$nbsp♣.
        """.trimIndent().suits()
        assertEquals(expected, text.share(dealt))
    }

    @Test
    fun `before the deal the shared text is just the seats`() {
        val expected = """
            Seat draw: 9 players at 2 tables

            Table 1
            1. Dana
            2. Marcus
            3. Priya
            4. Theo
            5. Jo

            Table 2
            1. Sam
            2. Alex
            3. Rita
            4. Ben
        """.trimIndent()
        assertEquals(expected, text.share(seated))
    }

    @Test
    fun `heads-up the button also posts the small blind, and one table reads in the singular`() {
        val headsUp = SeatDraw(listOf(DrawnTable(1, listOf("Ann", "Bo"), Cards.parseAll("Qd Qc"))))
        val expected = """
            Seat draw: 2 players at 1 table

            Table 1
            1. Ann · button and small blind
            2. Bo · big blind
            Button: Ann, seat 1, with Q♦

            High card gets the button. If ranks tie, the suit decides: ♠ beats$nbsp♥ beats$nbsp♦ beats$nbsp♣.
        """.trimIndent().suits()
        assertEquals(expected, text.share(headsUp))
    }

    @Test
    fun `the plan line, the button line and the tie line`() {
        assertEquals("1 table of 9", text.plan(listOf(9)))
        assertEquals("2 tables of 5", text.plan(listOf(5, 5)))
        assertEquals("2 tables: 6 and 5", text.plan(listOf(6, 5)))
        assertEquals("3 tables: 7, 6 and 6", text.plan(listOf(7, 6, 6)))

        val first = dealt.tables[0]
        assertEquals("Button: Marcus, seat 2", text.buttonLine(first, first.button!!))
        assertEquals("Tie on kings: K♠ wins on suit.".suits(), text.tieLine(first.button!!))
        assertNull(text.tieLine(dealt.tables[1].button!!))
    }
}
