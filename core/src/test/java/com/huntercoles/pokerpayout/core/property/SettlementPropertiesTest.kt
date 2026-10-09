package com.huntercoles.pokerpayout.core.property

import com.huntercoles.pokerpayout.core.domain.model.BankPlayer
import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.domain.model.PlayerSettlement
import com.huntercoles.pokerpayout.core.domain.model.ProgressiveBounty
import com.huntercoles.pokerpayout.core.domain.model.Settlement
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.core.domain.usecase.SettleTournamentUseCase
import com.huntercoles.pokerpayout.core.testing.expect
import com.huntercoles.pokerpayout.core.testing.forAll
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.long
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * The Bank's settlement ([SettleTournamentUseCase]) as properties over random nights, in all three
 * bounty modes ([BankNight] replays them step by step, stale records included):
 *
 * - **Conservation**: after every step nothing is owed beyond what was paid in (prize pool plus
 *   bounty pool, food aside), nothing is negative, and once there is a champion every cent of both
 *   pools has exactly one owner, even with players joining late.
 * - **Metamorphic**: who is who is only a label (renumbering the players moves their results with
 *   them), the order the Bank lists them in doesn't matter, and recording one more knockout never
 *   takes money away from anyone.
 *
 * Existing example-based and seeded-random tests (SettleTournamentUseCaseTest, BountyModesTest)
 * pin exact amounts; these look for the case nobody thought to write down.
 */
class SettlementPropertiesTest {

    private val settle = SettleTournamentUseCase(CalculatePayoutsUseCase())

    @Test
    fun `money is conserved after every step of any night, in every bounty mode`() =
        forAll(seed = 2026_1008_11L, iterations = 1_500, gen = BankNight.scripts()) { script ->
            val night = BankNight(script)
            night.play { step, settlement -> checkConserved(settlement) { "after step $step ${script.steps[step]}" } }
            night.finish()
            val finished = night.settlement()
            expect(finished.isComplete) { "no champion after the knockouts: ${night.order}" }
            checkConserved(finished) { "at the end" }
        }

    /**
     * Late registration: players join after knockouts (and envelopes) have been recorded, and
     * no-shows leave before the first. The pools grow with every player, and every cent of them
     * still ends with exactly one player.
     */
    @Test
    fun `money is conserved when players join late or leave before the first knockout`() =
        forAll(seed = 2026_1008_12L, iterations = 1_500, gen = BankNight.scripts(lateRegistration = true)) { script ->
            val night = BankNight(script)
            night.play { step, settlement -> checkConserved(settlement) { "after step $step ${script.steps[step]}" } }
            night.finish()
            checkConserved(night.settlement()) { "at the end, ${night.players.size} players" }
        }

    @Test
    fun `renumbering the players moves every result with them`() =
        forAll(seed = 2026_1008_13L, iterations = 1_000, gen = Arb.bind(BankNight.scripts(), seeds, ::Pair)) { (script, seed) ->
            val night = BankNight(script).apply { play() }
            val before = night.settlement()
            // A new number for every player; an unknown id stays unknown
            val ids = night.players.keys.toList()
            val renumber = ids.zip(ids.map { it + RENUMBERED }.shuffled(Random(seed))).toMap()
            fun Int.renumbered() = renumber[this] ?: this
            val players = night.players.values.map { player ->
                player.copy(id = player.id.renumbered(), eliminatedBy = player.eliminatedBy?.renumbered())
            }
            val after = settleAgain(night, players, night.order.map { it.renumbered() })
            night.players.keys.forEach { id ->
                val expected = before.forPlayer(id)?.copy(playerId = id.renumbered())
                val actual = after.forPlayer(id.renumbered())
                expect(actual == expected) { "player $id as ${id.renumbered()}: $actual, expected $expected" }
            }
            expect(after.championId == before.championId?.renumbered()) { "champion ${after.championId}" }
            expect(after.envelopesLeft == before.envelopesLeft) { "envelopes ${after.envelopesLeft} vs ${before.envelopesLeft}" }
        }

    @Test
    fun `the order the Bank lists the players in changes nothing`() =
        forAll(seed = 2026_1008_14L, iterations = 1_000, gen = Arb.bind(BankNight.scripts(), seeds, ::Pair)) { (script, seed) ->
            val night = BankNight(script).apply { play() }
            val before = night.settlement()
            val after = settleAgain(night, night.players.values.shuffled(Random(seed)), night.order)
            night.players.keys.forEach { id ->
                val (now, was) = after.forPlayer(id) to before.forPlayer(id)
                expect(now == was) { "player $id: $now, was $was" }
            }
            expect(after.payoutTable == before.payoutTable && after.pool == before.pool) { "pool or table changed" }
        }

    /**
     * Played back one knockout at a time, a finished night never takes money from anyone: what
     * each player is owed only grows as knockouts are recorded (so Undo, which records one fewer,
     * only ever gives back the last knockout's money).
     */
    @Test
    fun `recording one more knockout never takes money from anyone`() =
        forAll(seed = 2026_1008_15L, iterations = 1_000, gen = BankNight.scripts()) { script ->
            val night = BankNight(script).apply {
                play()
                finish()
            }
            var previous: Map<Int, Long> = emptyMap()
            (0..night.order.size).forEach { recorded ->
                val out = night.order.take(recorded)
                val players = night.players.values.map { player ->
                    if (player.id in out) player else player.copy(eliminatedBy = null, bountyDrawCents = null)
                }
                val owed = settleAgain(night, players, out).players.associate { it.playerId to it.winningsCents }
                previous.forEach { (id, cents) ->
                    expect(owed.getValue(id) >= cents) {
                        "knockout $recorded of ${night.order.size} (${out.last()}) cut player $id from $cents to ${owed[id]}"
                    }
                }
                previous = owed
            }
        }

    /** A progressive knockout splits any bounty, odd cents included, into two halves that add up to it. */
    @Test
    fun `a progressive split adds up to the bounty and the head half is never the smaller`() =
        forAll(seed = 2026_1008_16L, iterations = 2_000, gen = Arb.long(0L..10_000_000_000L)) { bounty ->
            val split = ProgressiveBounty.split(bounty)
            expect(split.cashCents + split.headCents == bounty) { "$bounty splits into $split" }
            expect(split.headCents - split.cashCents in 0L..1L) { "$bounty splits into $split" }
        }

    private fun settleAgain(night: BankNight, players: List<BankPlayer>, order: List<Int>): Settlement =
        settle(players, order, night.script.money, night.script.weights, night.script.rounding)

    /**
     * Nothing owed beyond the pools, nothing negative, the payout table adds up to the prize pool,
     * and with a champion every cent of both pools is owed to someone.
     */
    private fun checkConserved(settlement: Settlement, where: () -> String) {
        val payable = settlement.pool.payableCents
        val owed = settlement.assignedCents
        expect(owed <= payable) { "${where()}: owes $owed of $payable paid in (${settlement.describe()})" }
        expect(settlement.players.all { it.isNonNegative() }) { "${where()}: a negative amount: ${settlement.players}" }
        if (settlement.payoutTable.places.isNotEmpty()) {
            expect(settlement.payoutTable.totalCents == settlement.pool.prizePoolCents) { "${where()}: the table doesn't add up" }
        }
        if (settlement.isComplete) {
            expect(owed == payable) { "${where()}: champion known but owes $owed of $payable (${settlement.describe()})" }
        }
        if (settlement.bountyMode == BountyMode.MYSTERY) {
            val drawn = settlement.players.sumOf { it.knockoutBountyCents }
            val left = settlement.envelopesLeft.sum()
            expect(drawn + left <= settlement.pool.bountyPoolCents) {
                "${where()}: envelopes drawn ($drawn) and left ($left) hold more than the bounty pool " +
                    "${settlement.pool.bountyPoolCents}: ${settlement.envelopesLeft}"
            }
        }
    }

    private fun PlayerSettlement.isNonNegative() =
        prizeCents >= 0 && knockoutBountyCents >= 0 && kingsBountyCents >= 0 && unclaimedBountyCents >= 0 && headBountyCents >= 0

    private fun Settlement.describe() =
        "prizes ${players.sumOf { it.prizeCents }}, knockouts ${players.sumOf { it.knockoutBountyCents }}, " +
            "champion ${players.sumOf { it.kingsBountyCents + it.unclaimedBountyCents }}, pool $pool, $bountyMode"

    private companion object {
        /** Renumbered players get ids from here up, clear of the ids a night uses and of [BankNight.UNKNOWN_ID]. */
        const val RENUMBERED = 1_000
        val seeds = Arb.long(0L..Long.MAX_VALUE)
    }
}
