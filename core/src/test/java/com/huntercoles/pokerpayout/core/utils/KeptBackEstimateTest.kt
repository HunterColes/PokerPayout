package com.huntercoles.pokerpayout.core.utils

import com.huntercoles.pokerpayout.core.preferences.ChipSetSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The stacks the chip set keeps back until you say (PP-091 #3), from the Tournament's settings. */
class KeptBackEstimateTest {

    private fun estimate(players: Int, rebuyCents: Long = 0L, addOnCents: Long = 0L, rebuyUntilLevel: Int = 0) =
        KeptBackEstimate.of(players, rebuyCents, addOnCents, rebuyUntilLevel)

    @Test
    fun `no rebuys and no add-ons keep nothing back`() {
        assertEquals(KeptBackEstimate.NONE.copy(rebuyCutoff = true), estimate(9, rebuyUntilLevel = 4))
        assertEquals(0, estimate(9).stacks)
        assertEquals(0, KeptBackEstimate.NONE.stacks)
    }

    @Test
    fun `rebuys with a cutoff keep back about half the players, rounded up`() {
        val expected = mapOf(2 to 1, 5 to 3, 9 to 5, 10 to 5, 30 to 15)
        expected.forEach { (players, stacks) ->
            val kept = estimate(players, rebuyCents = 4_000, rebuyUntilLevel = 4)
            assertEquals(stacks, kept.rebuyStacks, "$players players")
            assertEquals(stacks, kept.stacks)
            assertTrue(kept.rebuyCutoff)
        }
    }

    @Test
    fun `rebuys open all game keep back one each`() {
        val kept = estimate(9, rebuyCents = 4_000)
        assertEquals(KeptBackEstimate(rebuyStacks = 9, addOnStacks = 0, rebuyCutoff = false), kept)
        assertEquals(9, kept.stacks)
    }

    @Test
    fun `add-ons keep back one each, on top of the rebuys`() {
        assertEquals(9, estimate(9, addOnCents = 1_000).stacks)
        // The mockups' night: 9 players, $40 rebuys until level 4, a $10 add-on
        val night = estimate(9, rebuyCents = 4_000, addOnCents = 1_000, rebuyUntilLevel = 4)
        assertEquals(KeptBackEstimate(rebuyStacks = 5, addOnStacks = 9, rebuyCutoff = true), night)
        assertEquals(14, night.stacks)
        assertFalse(night.capped)
    }

    @Test
    fun `a big table is capped at what the stepper allows`() {
        val kept = estimate(30, rebuyCents = 2_000, addOnCents = 1_000)
        assertEquals(60, kept.wanted)
        assertEquals(ChipSetSettings.RESERVE_RANGE.last, kept.stacks)
        assertTrue(kept.capped)
    }

    @Test
    fun `odd inputs keep nothing back rather than a negative number`() {
        assertEquals(0, estimate(-3, rebuyCents = 4_000, addOnCents = 1_000).stacks)
        assertEquals(0, estimate(9, rebuyCents = -1, addOnCents = -1).stacks)
    }
}
