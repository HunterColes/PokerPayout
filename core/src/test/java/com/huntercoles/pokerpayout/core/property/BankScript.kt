package com.huntercoles.pokerpayout.core.property

import com.huntercoles.pokerpayout.core.domain.model.BankPlayer
import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.Settlement
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.core.domain.usecase.SettleTournamentUseCase
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long

/**
 * One thing the Bank records, as plain numbers so a failing night shrinks to its fewest, smallest
 * steps. [kind] picks the action, [who] and [credit] pick players (modulo how many there are), and
 * [pick] picks the mystery envelope drawn or a price paid.
 */
internal data class Step(val kind: Int, val who: Int, val credit: Int, val pick: Int)

/** A night at the Bank: the setup, then the steps recorded, in order. */
internal data class NightScript(
    val players: Int,
    val money: MoneySettings,
    val weights: List<Int>,
    val rounding: PayoutRounding,
    val steps: List<Step>,
    /** Players may join late and no-shows may leave before the first knockout. */
    val lateRegistration: Boolean,
)

/**
 * Replays a [NightScript] onto the records the Bank keeps ([BankPlayer]s and the elimination
 * order) exactly as the Bank tab writes them, and settles them with the app's one settlement.
 *
 * Knockouts are credited to someone still in, to nobody, to someone already out (recorded late), or,
 * as stale saved data can, to the knocked-out player themself or to an id nobody has. A credited
 * mystery knockout draws one of the envelopes left, as the Bank does. A player joining late gets the
 * next id; a no-show leaving removes the highest id, as the player count's stepper does.
 */
internal class BankNight(val script: NightScript) {
    private val settle = SettleTournamentUseCase(CalculatePayoutsUseCase())
    private val money get() = script.money

    val players = sortedMapOf<Int, BankPlayer>().apply {
        (1..script.players).forEach { put(it, BankPlayer(it, boughtIn = true)) }
    }
    val order = mutableListOf<Int>()

    fun settlement(): Settlement = settle(players.values.toList(), order, money, script.weights, script.rounding)

    fun stillIn(): List<Int> = players.keys.filter { it !in order }

    /** Plays every step, calling [after] with the settlement after each. */
    fun play(after: (step: Int, Settlement) -> Unit = { _, _ -> }) {
        script.steps.forEachIndexed { index, step ->
            apply(step)
            after(index, settlement())
        }
    }

    /** Knocks out everyone but one, each credited to the next player still in. */
    fun finish() {
        while (stillIn().size > 1) {
            val (victim, eliminator) = stillIn()
            knockOut(victim, eliminator, pick = victim)
        }
    }

    /** Marks everyone paid, as the host does at the end of the night. */
    fun payEveryone() {
        players.replaceAll { _, player -> player.copy(paidOut = true) }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun apply(step: Step) {
        val inPlay = stillIn()
        when (step.kind) {
            in 0..4 -> if (inPlay.size > 1) {
                val victim = inPlay[step.who.mod(inPlay.size)]
                knockOut(victim, creditFor(victim, step.credit), step.pick)
            }
            5 -> order.lastOrNull()?.let(::bringBack)
            6 -> if (order.isNotEmpty()) bringBack(order[step.who.mod(order.size)])
            7 -> purchase(step) { player, prices -> player.copy(rebuyPricesCents = prices) }
            8 -> purchase(step) { player, prices -> player.copy(addOnPricesCents = prices) }
            9 -> if (script.lateRegistration) {
                val id = players.lastKey() + 1
                players[id] = BankPlayer(id, boughtIn = true)
            }
            else -> if (script.lateRegistration && order.isEmpty() && players.size > 2) players.remove(players.lastKey())
        }
    }

    private fun creditFor(victim: Int, credit: Int): Int? {
        val others = stillIn().filter { it != victim }
        return when (credit.mod(CREDIT_KINDS)) {
            0 -> null
            1 -> order.getOrNull(credit.mod(order.size.coerceAtLeast(1)))
            2 -> victim
            3 -> UNKNOWN_ID
            else -> others[credit.mod(others.size)]
        }
    }

    private fun knockOut(victim: Int, credit: Int?, pick: Int) {
        val draw = if (credit != null && money.bountyMode == BountyMode.MYSTERY) {
            settlement().envelopesLeft.takeIf { it.isNotEmpty() }?.let { left -> left[pick.mod(left.size)] }
        } else {
            null
        }
        order += victim
        players[victim] = players.getValue(victim).copy(eliminatedBy = credit, bountyDrawCents = draw)
    }

    /** Back in the game: the knockout, its credit and any envelope drawn for it are taken back. */
    private fun bringBack(id: Int) {
        order.remove(id)
        players[id] = players.getValue(id).copy(eliminatedBy = null, bountyDrawCents = null)
    }

    private fun purchase(step: Step, record: (BankPlayer, List<Long>) -> BankPlayer) {
        val id = players.keys.elementAt(step.who.mod(players.size))
        val player = players.getValue(id)
        val price = PRICES[step.pick.mod(PRICES.size)]
        val current = if (step.kind == 7) player.rebuyPricesCents.orEmpty() else player.addOnPricesCents.orEmpty()
        players[id] = record(player, current + price)
    }

    companion object {
        private const val CREDIT_KINDS = 6
        const val UNKNOWN_ID = 99
        private val PRICES = listOf(0L, 1L, 500L, 999L, 1_000L, 1_234L, 2_000L)

        private val amounts = listOf(0L, 1L, 7L, 200L, 354L, 500L, 777L, 1_000L, 1_414L, 2_500L, 10_000L)

        /** An amount: a round one, an odd one, or any up to $1,000. */
        private val amount = Arb.bind(Arb.element(amounts), Arb.long(0L..100_000L), Arb.int(0..2)) { listed, any, which ->
            if (which == 0) any else listed
        }

        val settings = Arb.bind(amount, amount, amount, amount, amount, Arb.element(BountyMode.entries)) {
                buyIn, food, bounty, rebuy, addOn, mode ->
            MoneySettings(buyIn, food, bounty, rebuy, addOn, mode)
        }

        private val structures = Arb.bind(
            Arb.element(PayoutPreset.entries),
            Arb.int(1..9),
            Arb.list(Arb.int(1..999), 1..9),
            Arb.int(0..1),
        ) { preset, places, raw, handEdited ->
            if (handEdited == 1) raw else preset.weightsFor(places)
        }

        val steps = Arb.bind(Arb.int(0..10), Arb.int(0..30), Arb.int(0..30), Arb.int(0..30), ::Step)

        fun scripts(maxSteps: Int = 40, lateRegistration: Boolean = false) = Arb.bind(
            Arb.int(2..14),
            settings,
            structures,
            Arb.element(PayoutRounding.entries),
            Arb.list(steps, 0..maxSteps),
        ) { players, money, weights, rounding, steps -> NightScript(players, money, weights, rounding, steps, lateRegistration) }
    }
}
