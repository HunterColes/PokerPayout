package com.huntercoles.pokerpayout.bank.presentation

import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.PayoutSettings
import com.huntercoles.pokerpayout.core.testing.expect
import com.huntercoles.pokerpayout.core.testing.forAll
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

/**
 * The Bank mid-night survives process death, whatever was recorded and in whatever order: random
 * nights on the real ViewModel and real preferences (names, buy-ins, rebuys and add-ons at changing
 * prices, knockouts credited or not, mystery envelopes, paid flags, payout changes, Undo, a player
 * joining late), with the process killed at random points. After each restart, rebuilt from
 * nothing but what was saved, the Bank shows exactly what it showed before: every player, row,
 * place, pool, payout and envelope. Only the open sheet and Undo's history go, as they should.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BankRestoreTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** One thing done at the Bank, or the Tournament tab, or the process dying ([kind] 13). */
    private data class Step(val kind: Int, val who: Int, val arg: Int)

    private data class Night(val players: Int, val mode: BountyMode, val bounty: Double, val steps: List<Step>)

    @Test
    fun `a night at the Bank comes back exactly after process death`() = playAndRestart(seed = 2026_1008_61L, nights)

    /**
     * PP-116: the same, with late arrivals and re-entries among the steps (and Undo taking them back),
     * so the rows the Bank adds, their prices and who re-entered for whom all come back too.
     */
    @Test
    fun `a night with late entries and re-entries comes back exactly after process death`() =
        playAndRestart(seed = 2026_1009_61L, nightsWithEntries)

    private fun playAndRestart(seed: Long, gen: Arb<Night>) =
        forAll(seed = seed, iterations = 50, gen = gen) { night ->
            val kit = BankTestKit(testDispatcher)
            kit.draws = Random(night.steps.size)
            kit.configure(players = night.players, buyIn = 20.0, bounty = night.bounty, rebuy = 20.0, addOn = 10.0)
            kit.tournamentPreferences.setBountyMode(night.mode)
            kit.clock.at(level = 1)
            var viewModel = kit.newViewModel()
            night.steps.forEachIndexed { index, step ->
                if (step.kind == DIE) {
                    val before = viewModel.uiState.value.saved()
                    viewModel = kit.restartProcess()
                    val after = viewModel.uiState.value.saved()
                    expect(after == before) { "after step $index: restarted as\n$after\nbut was\n$before" }
                } else {
                    kit.perform(viewModel, step)
                }
            }
            val before = viewModel.uiState.value.saved()
            val after = kit.restartProcess().uiState.value.saved()
            expect(after == before) { "at the end: restarted as\n$after\nbut was\n$before" }
            kit.clear()
        }

    /** What the Bank must show again after a restart: everything but the sheet and Undo's history. */
    private fun BankUiState.saved() = copy(sheet = null, undoLabel = null)

    @Suppress("CyclomaticComplexMethod") // one branch per kind of step
    private fun BankTestKit.perform(viewModel: BankViewModel, step: Step) {
        val ids = viewModel.uiState.value.players.map { it.id }
        val id = ids[step.who.mod(ids.size)]
        val other = ids[step.arg.mod(ids.size)]
        val intent: BankIntent? = when (step.kind) {
            0 -> BankIntent.PlayerNameChanged(id, NAMES[step.arg.mod(NAMES.size)])
            1 -> BankIntent.BuyInToggled(id)
            2 -> BankIntent.AddPurchase(id, Purchase.REBUY)
            3 -> BankIntent.AddPurchase(id, Purchase.ADD_ON)
            4 -> BankIntent.SetCount(id, if (step.arg % 2 == 0) Purchase.REBUY else Purchase.ADD_ON, step.arg.mod(4))
            5, 6 -> BankIntent.KnockOut(id, other.takeIf { it != id && step.arg % 3 != 0 })
            7 -> BankIntent.BringBack(id)
            8 -> BankIntent.SetPaid(id, step.arg % 2 == 0)
            9 -> BankIntent.Undo
            10 -> BankIntent.UpdatePayoutSettings(
                PayoutSettings(
                    weights = PayoutPreset.entries[step.arg.mod(3)].weightsFor(1 + step.who.mod(4)),
                    preset = PayoutPreset.entries[step.arg.mod(3)],
                    rounding = PayoutRounding.entries[step.who.mod(3)],
                ),
            )
            11 -> {
                // The Tournament tab: a player sits down late, or the rebuy price changes
                if (step.arg % 2 == 0) {
                    tournamentPreferences.setPlayerCount(ids.size + 1)
                } else {
                    tournamentPreferences.setRebuyAmount(LATER_REBUY)
                }
                null
            }
            // PP-116: a late arrival, or a player who is out buying back in
            LATE_ENTRY -> BankIntent.AddLateEntry(NAMES[step.arg.mod(NAMES.size)])
            RE_ENTRY -> viewModel.uiState.value.reEntries.let { out ->
                BankIntent.ReEnter(if (out.isEmpty()) id else out[step.arg.mod(out.size)].playerId)
            }
            else -> BankIntent.ToggleMute
        }
        intent?.let(viewModel::acceptIntent)
        settle()
    }

    private companion object {
        const val DIE = 13
        const val LATE_ENTRY = 14
        const val RE_ENTRY = 15
        const val LATER_REBUY = 15.0
        val NAMES = listOf("Dana", "Priya", "", "  ", "Jo, \"Ace\"", "Zoë")

        val steps = Arb.bind(Arb.int(0..DIE), Arb.int(0..20), Arb.int(0..20), ::Step)
        val nights = Arb.bind(
            Arb.int(2..9),
            Arb.element(BountyMode.entries),
            Arb.element(5.0, 7.75, 10.0),
            Arb.list(steps, 1..40),
            ::Night,
        )

        /** Every kind of step, late entries and re-entries included. */
        private val stepsWithEntries = Arb.bind(Arb.int(0..RE_ENTRY), Arb.int(0..20), Arb.int(0..20), ::Step)
        val nightsWithEntries = Arb.bind(
            Arb.int(2..9),
            Arb.element(BountyMode.entries),
            Arb.element(5.0, 7.75, 10.0),
            Arb.list(stepsWithEntries, 1..40),
            ::Night,
        )
    }
}
