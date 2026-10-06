package com.huntercoles.pokerpayout.core.domain

import com.huntercoles.pokerpayout.core.domain.model.MysteryBounty
import com.huntercoles.pokerpayout.core.domain.model.MysteryBounty.EnvelopeGroup
import com.huntercoles.pokerpayout.core.domain.usecase.DrawEnvelopeUseCase
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The mystery-bounty envelopes (PP-035): what is dealt, what is left, and the draw. */
class MysteryBountyTest {

    @Test
    fun `nine players at five dollars get one big envelope, two middling and six small`() {
        assertEquals(listOf(1_500L, 600L, 600L, 300L, 300L, 300L, 300L, 300L, 300L), MysteryBounty.envelopes(9, 500))
        assertEquals(
            listOf(EnvelopeGroup(1_500L, 1), EnvelopeGroup(600L, 2), EnvelopeGroup(300L, 6)),
            MysteryBounty.groups(MysteryBounty.envelopes(9, 500))
        )
    }

    @Test
    fun `a ten dollar bounty deals in two-fifty units`() {
        assertEquals(listOf(4_000L, 1_000L, 1_000L, 500L, 500L, 500L, 500L, 500L, 500L), MysteryBounty.envelopes(9, 1_000))
    }

    @Test
    fun `thirty players get three big envelopes`() {
        assertEquals(
            listOf(EnvelopeGroup(1_700L, 3), EnvelopeGroup(600L, 6), EnvelopeGroup(300L, 21)),
            MysteryBounty.groups(MysteryBounty.envelopes(30, 500))
        )
    }

    @Test
    fun `no bounty or no players means no envelopes`() {
        assertEquals(emptyList(), MysteryBounty.envelopes(9, 0))
        assertEquals(emptyList(), MysteryBounty.envelopes(0, 500))
        assertEquals(emptyList(), MysteryBounty.envelopes(-3, 500))
    }

    @Test
    fun `a bounty too small to split gives every envelope the bounty`() {
        assertEquals(List(9) { 1L }, MysteryBounty.envelopes(9, 1))
    }

    /** Every deal: one envelope per player, players × bounty exactly, biggest first, none empty. */
    @Test
    fun `every deal adds up to players times the bounty, one envelope each`() {
        val bounties = listOf(
            1L, 2L, 3L, 7L, 25L, 99L, 100L, 250L, 333L, 500L, 707L, 1_000L, 1_250L, 2_000L, 2_500L, 10_000L, 123_456L
        )
        for (players in 1..30) {
            for (bounty in bounties) {
                val envelopes = MysteryBounty.envelopes(players, bounty)
                val context = "$players players at $bounty: $envelopes"
                assertEquals(players, envelopes.size, context)
                assertEquals(players * bounty, envelopes.sum(), context)
                assertEquals(envelopes.sortedDescending(), envelopes, context)
                assertTrue(envelopes.all { it > 0L }, context)
            }
        }
    }

    /** "A few big prizes, many small ones": from five players and a dollar up, small ones outnumber big ones. */
    @Test
    fun `big envelopes are few and small ones many`() {
        for (players in 5..30) {
            for (bounty in listOf(100L, 500L, 1_000L, 2_000L, 2_500L)) {
                val envelopes = MysteryBounty.envelopes(players, bounty)
                val context = "$players players at $bounty: $envelopes"
                val biggest = envelopes.first()
                val smallest = envelopes.last()
                assertTrue(biggest >= 3 * smallest, context)
                assertTrue(envelopes.count { it == smallest } > envelopes.count { it == biggest }, context)
                assertTrue(smallest < bounty, "the small ones are worth less than a bounty: $context")
            }
        }
    }

    @Test
    fun `what is left is the deal less one envelope per amount drawn`() {
        val deal = listOf(1_500L, 600L, 600L, 300L, 300L)
        assertEquals(listOf(1_500L, 600L, 300L), MysteryBounty.remaining(deal, listOf(600L, 300L)))
        assertEquals(deal, MysteryBounty.remaining(deal, emptyList()))
        // An amount the deal doesn't hold (the settings changed since) takes nothing out
        assertEquals(deal, MysteryBounty.remaining(deal, listOf(999L)))
    }

    @Test
    fun `the same seed draws the same envelopes, and an empty pool draws nothing`() {
        val deal = MysteryBounty.envelopes(9, 500)
        fun drawAll(seed: Int): List<Long> {
            val draw = DrawEnvelopeUseCase(Random(seed))
            var left = deal
            return List(deal.size) {
                val cents = requireNotNull(draw(left))
                left = MysteryBounty.remaining(left, listOf(cents))
                cents
            }
        }
        assertEquals(drawAll(35), drawAll(35))
        // Drawing every envelope draws each exactly once
        assertEquals(deal.sorted(), drawAll(7).sorted())
        assertNull(DrawEnvelopeUseCase(Random(1))(emptyList()))
    }

    /** Over many seeded draws from 1 × $15, 2 × $6, 6 × $3, each envelope comes up about as often. */
    @Test
    fun `each envelope is as likely as the next`() {
        val deal = MysteryBounty.envelopes(9, 500)
        val draw = DrawEnvelopeUseCase(Random(2026))
        val counts = List(DRAWS) { requireNotNull(draw(deal)) }.groupingBy { it }.eachCount()
        val perEnvelope = DRAWS.toDouble() / deal.size
        assertEquals(perEnvelope * 1, counts.getValue(1_500L).toDouble(), perEnvelope * TOLERANCE)
        assertEquals(perEnvelope * 2, counts.getValue(600L).toDouble(), perEnvelope * 2 * TOLERANCE)
        assertEquals(perEnvelope * 6, counts.getValue(300L).toDouble(), perEnvelope * 6 * TOLERANCE)
    }

    private companion object {
        const val DRAWS = 90_000
        const val TOLERANCE = 0.05
    }
}
