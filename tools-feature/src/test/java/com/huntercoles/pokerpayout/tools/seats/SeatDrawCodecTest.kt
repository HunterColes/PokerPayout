package com.huntercoles.pokerpayout.tools.seats

import com.huntercoles.pokerpayout.tools.poker.Cards
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** A saved draw reads back exactly, whatever the names hold, and a damaged one reads as no draw. */
class SeatDrawCodecTest {

    private val awkward = listOf("Ann, Jr.", "Bo|b", "C+D 100%", "Zoë 🂡", "  spaced  ", "new\nline", "", "seats:1")

    @Test
    fun `names with commas, pipes, plus signs, percents, emoji and line breaks survive`() {
        assertEquals(awkward, SeatDrawCodec.decodeNames(SeatDrawCodec.encodeNames(awkward)))
    }

    @Test
    fun `a draw reads back as it was, before and after the button deal`() {
        val seated = SeatDrawer.drawSeats(awkward + listOf("Eve", "Fay", "Gus", "Hal", "Ivy", "Jo", "Kai"), 7, Random(11))
        assertEquals(seated, SeatDrawCodec.decode(SeatDrawCodec.encode(seated)))
        val dealt = SeatDrawer.dealButtons(seated, Random(12))
        assertEquals(dealt, SeatDrawCodec.decode(SeatDrawCodec.encode(dealt)))
    }

    @Test
    fun `the saved form is a version line, then one line per table`() {
        val draw = SeatDraw(
            listOf(
                DrawnTable(1, listOf("Dana", "Marcus"), Cards.parseAll("Ks Kh")),
                DrawnTable(2, listOf("Priya", "Theo, Jr.")),
            ),
        )
        assertEquals("seats:1\nDana,Marcus|Ks,Kh\nPriya,Theo%2C+Jr.|", SeatDrawCodec.encode(draw))
    }

    @Test
    fun `anything damaged or from another version reads as no draw`() {
        listOf(
            "",
            "seats:1",
            "seats:2\nA,B|",
            "seats:1\nA|",
            "seats:1\nA,B|As",
            "seats:1\nA,B|As,As",
            "seats:1\nA,B|As,Zz",
            "seats:1\nA,B|As,Kd,Qc",
        ).forEach { assertNull(SeatDrawCodec.decode(it), "\"$it\"") }
    }
}
