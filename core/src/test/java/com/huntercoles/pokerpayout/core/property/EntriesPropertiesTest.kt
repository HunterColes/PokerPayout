package com.huntercoles.pokerpayout.core.property

import com.huntercoles.pokerpayout.core.domain.history.NightResults
import com.huntercoles.pokerpayout.core.domain.model.BankPlayer
import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.domain.model.EntryPrice
import com.huntercoles.pokerpayout.core.domain.model.Settlement
import com.huntercoles.pokerpayout.core.domain.settle.SettleUp
import com.huntercoles.pokerpayout.core.domain.settle.SettleUpUseCase
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.core.domain.usecase.SettleTournamentUseCase
import com.huntercoles.pokerpayout.core.testing.expect
import com.huntercoles.pokerpayout.core.testing.forAll
import org.junit.jupiter.api.Test

/**
 * Late entries and re-entries (PP-116) as properties over random nights ([BankNight] with
 * `entries`): players join late and players who are out buy back in, each entry at a price of its
 * own (today's, or the amounts from before a change), with knockouts, purchases, Bring back and
 * mystery draws in between.
 *
 * - **Pool = entries in, to the cent:** the buy-in, food and bounty pools are exactly what every
 *   entry paid to sit down, and the payout table splits exactly the prize pool.
 * - **Bounty pool conserved:** at every step the bounties won, the champion's and the envelopes
 *   still in the pool never hold more than the bounty pool; once there is a champion they hold all
 *   of it. Mystery: the envelopes drawn and the ones left are exactly the bounty pool at every step,
 *   because a late entry adds one envelope and nothing is dealt again.
 * - **Entries count:** places run 1 to the number of entries, each once.
 * - **The settle-up and History count players:** a player's entries settle as one party, and the
 *   night lists each player once, with everything their entries paid and won.
 */
class EntriesPropertiesTest {

    private val settle = SettleTournamentUseCase(CalculatePayoutsUseCase())
    private val settleUp = SettleUpUseCase()

    @Test
    fun `the pools are exactly what the entries paid, after every step`() =
        forAll(seed = 2026_1009_11L, iterations = 1_500, gen = BankNight.scripts(entries = true)) { script ->
            val night = BankNight(script)
            night.play { step, settlement -> checkPools(night, settlement) { "after step $step ${script.steps[step]}" } }
            night.finish()
            checkPools(night, night.settlement()) { "at the end" }
        }

    @Test
    fun `every bounty has exactly one owner once there is a champion, whoever re-entered`() =
        forAll(seed = 2026_1009_12L, iterations = 1_500, gen = BankNight.scripts(entries = true)) { script ->
            val night = BankNight(script)
            night.play { step, settlement -> checkBounties(settlement) { "after step $step ${script.steps[step]}" } }
            night.finish()
            val finished = night.settlement()
            expect(finished.isComplete) { "no champion after the knockouts: ${night.order}" }
            checkBounties(finished) { "at the end" }
            expect(finished.assignedCents == finished.pool.payableCents) {
                "owes ${finished.assignedCents} of ${finished.pool.payableCents}"
            }
        }

    @Test
    fun `places count entries, each place once`() =
        forAll(seed = 2026_1009_13L, iterations = 1_000, gen = BankNight.scripts(entries = true)) { script ->
            val night = BankNight(script).apply {
                play()
                finish()
            }
            val places = night.settlement().standings.placeByPlayer.values.sorted()
            expect(places == (1..night.players.size).toList()) { "${night.players.size} entries finish in $places" }
        }

    @Test
    fun `a late entry adds one envelope and deals nothing again`() =
        forAll(seed = 2026_1009_14L, iterations = 1_000, gen = BankNight.scripts(entries = true)) { script ->
            val mystery = script.copy(money = script.money.copy(bountyMode = BountyMode.MYSTERY))
            val night = BankNight(mystery).apply { play() }
            val before = night.settlement().envelopesLeft
            val price = EntryPrice.of(mystery.money).copy(bountyCents = LATE_BOUNTY)
            val id = night.players.lastKey() + 1
            night.players[id] = BankPlayer(id, boughtIn = true, entryPrice = price)
            val after = night.settlement().envelopesLeft
            expect(after == (before + LATE_BOUNTY).sortedDescending()) { "envelopes $before became $after" }
        }

    @Test
    fun `a finished night settles each player once and History lists each player once`() =
        forAll(seed = 2026_1009_15L, iterations = 800, gen = BankNight.scripts(entries = true)) { script ->
            val night = BankNight(script).apply {
                play()
                finish()
            }
            val bank = night.players.values.toList()
            val settlement = night.settlement()
            val people = BankPlayer.people(bank)
            val plan = checkNotNull(settleUp(settlement, bank, script.money)) { "no settle-up with a champion" }
            val parties = people.values.toSet() + SettleUp.BANK_ID
            expect(plan.balancesCents.keys == parties) { "parties ${plan.balancesCents.keys}, players ${people.values.toSet()}" }
            expect(plan.balancesCents.values.sum() == 0L) { "balances ${plan.balancesCents} don't add up to 0" }

            night.payEveryone()
            val paid = night.players.values.toList()
            val lines = checkNotNull(NightResults.of(night.settlement(), paid, emptyMap())) { "unfinished" }
            expect(lines.size == people.values.toSet().size) { "${lines.size} lines for ${people.values.toSet().size} players" }
            expect(lines.map { it.place }.distinct().size == lines.size) { "a place twice: $lines" }
            val paidIn = settlement.players.sumOf { it.costCents }
            expect(lines.sumOf { it.paidInCents } == paidIn) { "History paid in ${lines.sumOf { it.paidInCents }} of $paidIn" }
            expect(lines.sumOf { it.wonCents } == settlement.pool.payableCents) { "History won ${lines.sumOf { it.wonCents }}" }
        }

    /** The pools hold exactly what each entry paid; the table splits the prize pool exactly. */
    private fun checkPools(night: BankNight, settlement: Settlement, where: () -> String) {
        val money = night.script.money
        val entries = night.players.values.map { it.entry(money) }
        val pool = settlement.pool
        expect(pool.buyInCents == entries.sumOf { it.buyInCents }) { "${where()}: buy-ins ${pool.buyInCents}, entries $entries" }
        expect(pool.foodCents == entries.sumOf { it.foodCents }) { "${where()}: food ${pool.foodCents}, entries $entries" }
        expect(pool.bountyCents == entries.sumOf { it.bountyCents }) { "${where()}: bounties ${pool.bountyCents}, entries $entries" }
        val costs = settlement.players.sumOf { it.costCents }
        expect(costs == pool.totalCents) { "${where()}: entries cost $costs, pool ${pool.totalCents}" }
        if (settlement.payoutTable.places.isNotEmpty()) {
            expect(settlement.payoutTable.totalCents == pool.prizePoolCents) { "${where()}: the table doesn't add up" }
        }
        val boughtIn = night.players.values.filter { it.boughtIn }.sumOf { it.entry(money).totalCents }
        expect(settlement.paidInCents == boughtIn + pool.rebuyCents + pool.addOnCents) { "${where()}: paid in ${settlement.paidInCents}" }
    }

    /** Nothing owed beyond the bounty pool; mystery: drawn and left are the pool exactly. */
    private fun checkBounties(settlement: Settlement, where: () -> String) {
        val bounties = settlement.players.sumOf { it.knockoutBountyCents + it.kingsBountyCents + it.unclaimedBountyCents }
        val pool = settlement.pool.bountyPoolCents
        expect(bounties <= pool) { "${where()}: bounties $bounties of $pool" }
        if (settlement.isComplete) expect(bounties == pool) { "${where()}: champion known but bounties $bounties of $pool" }
        if (settlement.bountyMode == BountyMode.MYSTERY && !settlement.isComplete) {
            val drawn = settlement.players.sumOf { it.knockoutBountyCents }
            val left = settlement.envelopesLeft.sum()
            expect(drawn + left == pool) { "${where()}: drawn $drawn and left $left (${settlement.envelopesLeft}) of $pool" }
        }
    }

    private companion object {
        /** A late entry's bounty that no deal of round amounts holds. */
        const val LATE_BOUNTY = 777L
    }
}
