package com.huntercoles.pokerpayout.core.domain

import com.huntercoles.pokerpayout.core.constants.TournamentConstants
import com.huntercoles.pokerpayout.core.domain.model.PayoutPlaces
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.Standings
import com.huntercoles.pokerpayout.core.domain.model.ordinalOf
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Presets, rounding units, the places guideline and finishing places. */
class PayoutStructureTest {

    @Test
    fun `every preset table is strictly decreasing and has one weight per place`() {
        PayoutPreset.entries.forEach { preset ->
            (1..PayoutPlaces.MAX).forEach { places ->
                val weights = preset.weightsFor(places)
                assertEquals(places, weights.size, "$preset $places")
                assertTrue(weights.zipWithNext().all { (a, b) -> a > b }, "$preset $places: $weights")
                assertTrue(weights.all { it > 0 }, "$preset $places: $weights")
            }
        }
    }

    @Test
    fun `top-heavy and flat tables are percentages, standard is the long-standing default`() {
        (1..PayoutPlaces.MAX).forEach { places ->
            assertEquals(100, PayoutPreset.TOP_HEAVY.weightsFor(places).sum(), "top-heavy $places")
            assertEquals(100, PayoutPreset.FLAT.weightsFor(places).sum(), "flat $places")
            assertEquals(TournamentConstants.DEFAULT_PAYOUT_WEIGHTS.take(places), PayoutPreset.STANDARD.weightsFor(places))
        }
        assertEquals(listOf(60, 30, 10), PayoutPreset.TOP_HEAVY.weightsFor(3))
        assertEquals(listOf(45, 32, 23), PayoutPreset.FLAT.weightsFor(3))
    }

    @Test
    fun `presets order 1st place's share from top-heavy to flat`() {
        (2..PayoutPlaces.MAX).forEach { places ->
            fun firstShare(preset: PayoutPreset) = preset.weightsFor(places).let { it.first().toDouble() / it.sum() }
            assertTrue(firstShare(PayoutPreset.TOP_HEAVY) > firstShare(PayoutPreset.STANDARD), "$places places")
            assertTrue(firstShare(PayoutPreset.STANDARD) > firstShare(PayoutPreset.FLAT), "$places places")
        }
    }

    @Test
    fun `weights are recognised as the preset they came from`() {
        assertEquals(PayoutPreset.STANDARD, PayoutPreset.matching(listOf(35, 20, 15)))
        assertEquals(PayoutPreset.TOP_HEAVY, PayoutPreset.matching(listOf(60, 30, 10)))
        assertEquals(PayoutPreset.FLAT, PayoutPreset.matching(listOf(45, 32, 23)))
        assertNull(PayoutPreset.matching(listOf(50, 30, 20)))
        assertNull(PayoutPreset.matching(emptyList()))
        // Out-of-range place counts clamp instead of throwing
        assertEquals(PayoutPreset.FLAT.weightsFor(PayoutPlaces.MAX), PayoutPreset.FLAT.weightsFor(40))
        assertEquals(listOf(35), PayoutPreset.STANDARD.weightsFor(0))
    }

    @Test
    fun `pay about a third of the field, never more places than players`() {
        assertEquals(1, PayoutPlaces.recommended(3))
        assertEquals(1, PayoutPlaces.recommended(5))
        assertEquals(2, PayoutPlaces.recommended(6))
        assertEquals(3, PayoutPlaces.recommended(10))
        assertEquals(9, PayoutPlaces.recommended(30))
        assertEquals(3, PayoutPlaces.maxFor(3))
        assertEquals(9, PayoutPlaces.maxFor(30))
        assertEquals(1, PayoutPlaces.maxFor(0))
    }

    @Test
    fun `rounding units and their stored form`() {
        assertEquals(listOf(100L, 500L, 1_000L), PayoutRounding.entries.map { it.unitCents })
        assertEquals(listOf("$1", "$5", "$10"), PayoutRounding.entries.map { it.label })
        assertEquals(PayoutRounding.FIVE_DOLLARS, PayoutRounding.fromUnitCents(500))
        assertEquals(PayoutRounding.ONE_DOLLAR, PayoutRounding.fromUnitCents(123))
    }

    @Test
    fun `ordinals`() {
        assertEquals(
            listOf("1st", "2nd", "3rd", "4th", "10th", "11th", "12th", "13th", "21st", "22nd", "23rd", "111th"),
            listOf(1, 2, 3, 4, 10, 11, 12, 13, 21, 22, 23, 111).map { ordinalOf(it) }
        )
    }

    // ---- Standings: finishing places from the elimination order (the B21 mapping)

    @Test
    fun `the first player out finishes last and places fill in from the bottom`() {
        val standings = Standings(playerIds = (1..5).toList(), eliminationOrder = listOf(4, 2))

        assertEquals(mapOf(4 to 5, 2 to 4), standings.placeByPlayer)
        assertNull(standings.championId)
        // Nobody is "1st" or "2nd" while three players are still in. v1.1.12's Tournament tab
        // labelled the most recent elimination (player 2) as 1st here (B21).
        assertNull(standings.playerAt(1))
        assertNull(standings.playerAt(2))
        assertEquals(2, standings.playerAt(4))
    }

    @Test
    fun `the last player standing is the champion`() {
        val standings = Standings(playerIds = (1..4).toList(), eliminationOrder = listOf(4, 1, 3))
        assertEquals(2, standings.championId)
        assertEquals(mapOf(4 to 4, 1 to 3, 3 to 2, 2 to 1), standings.placeByPlayer)
    }

    @Test
    fun `unknown ids and repeats in the elimination order are ignored`() {
        val standings = Standings(playerIds = listOf(1, 2, 3), eliminationOrder = listOf(9, 3, 3, 0, 1))
        assertEquals(listOf(3, 1), standings.eliminated)
        assertEquals(2, standings.championId)
        assertEquals(mapOf(3 to 3, 1 to 2, 2 to 1), standings.placeByPlayer)
    }

    @Test
    fun `a single player is the champion from the start`() {
        assertEquals(7, Standings(listOf(7), emptyList()).championId)
    }
}
