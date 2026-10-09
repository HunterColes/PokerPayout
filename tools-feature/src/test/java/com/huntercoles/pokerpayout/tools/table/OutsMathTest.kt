package com.huntercoles.pokerpayout.tools.table

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Outs and pot odds against the counts: 47 unseen cards on the flop (1,081 pairs to come), 46 on
 * the turn, and the textbook chances (a flush draw is 35.0% by the river and 19.1% on the next card).
 */
class OutsMathTest {

    @Test
    fun `two cards to come miss only when both are blanks`() {
        // Nine outs: the 38 blanks make C(38, 2) = 703 of the 1,081 pairs, so 378 hit.
        assertEquals(Chance(378, 1_081), OutsMath.byRiver(9, Street.Flop))
        assertEquals(Chance(340, 1_081), OutsMath.byRiver(8, Street.Flop))
        assertEquals(Chance(178, 1_081), OutsMath.byRiver(4, Street.Flop))
        assertEquals(Chance(585, 1_081), OutsMath.byRiver(15, Street.Flop))
    }

    @Test
    fun `the textbook chances`() {
        assertEquals("35.0", oneDecimal(OutsMath.byRiver(9, Street.Flop).percent))
        assertEquals("19.1", oneDecimal(OutsMath.nextCard(9, Street.Flop).percent))
        assertEquals("19.6", oneDecimal(OutsMath.byRiver(9, Street.Turn).percent))
        assertEquals("31.5", oneDecimal(OutsMath.byRiver(8, Street.Flop).percent))
        assertEquals("16.5", oneDecimal(OutsMath.byRiver(4, Street.Flop).percent))
        assertEquals("8.7", oneDecimal(OutsMath.byRiver(4, Street.Turn).percent))
        assertEquals("54.1", oneDecimal(OutsMath.byRiver(15, Street.Flop).percent))
    }

    @Test
    fun `one card to come is outs over unseen cards, and on the turn the river is the next card`() {
        assertEquals(Chance(9, 47), OutsMath.nextCard(9, Street.Flop))
        assertEquals(Chance(9, 46), OutsMath.nextCard(9, Street.Turn))
        assertEquals(OutsMath.nextCard(9, Street.Turn), OutsMath.byRiver(9, Street.Turn))
    }

    @Test
    fun `the rules of 4 and 2`() {
        assertEquals(36, OutsMath.ruleOfThumb(9, cards = 2))
        assertEquals(18, OutsMath.ruleOfThumb(9, cards = 1))
        assertEquals(60, OutsMath.ruleOfThumb(15, cards = 2))
    }

    @Test
    fun `odds against are misses for each hit`() {
        // 703 misses to 378 hits: 1.86 to 1; 38 to 9 on the next card: 4.22 to 1.
        assertEquals(703.0 / 378, OutsMath.byRiver(9, Street.Flop).againstToOne!!, 1e-12)
        assertEquals(38.0 / 9, OutsMath.nextCard(9, Street.Flop).againstToOne!!, 1e-12)
        assertNull(Chance(0, 47).againstToOne)
        assertNull(Chance(47, 47).againstToOne)
    }

    @Test
    fun `a call needs its share of the pot it plays for`() {
        // 300 in the middle (with the bet), 100 to call: 100 of 400 is 25%, pot odds 3 to 1.
        assertEquals(25.0, PotOdds.equityNeeded(pot = 300, call = 100)!!, 1e-12)
        assertEquals(3.0, PotOdds.toOne(pot = 300, call = 100)!!, 1e-12)
        // A pot-sized bet: 100 into 100 makes 200, so 100 to win 200 needs a third.
        assertEquals(100.0 / 3, PotOdds.equityNeeded(pot = 200, call = 100)!!, 1e-12)
        assertNull(PotOdds.equityNeeded(pot = 0, call = 100))
        assertNull(PotOdds.equityNeeded(pot = 300, call = 0))
    }

    private fun oneDecimal(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)
}
