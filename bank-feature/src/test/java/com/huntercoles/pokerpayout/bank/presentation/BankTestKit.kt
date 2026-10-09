package com.huntercoles.pokerpayout.bank.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.design.components.SnackbarController
import com.huntercoles.pokerpayout.core.domain.model.ClockStatus
import com.huntercoles.pokerpayout.core.domain.model.ClockStatusProvider
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.PayoutSettings
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.core.domain.usecase.DrawEnvelopeUseCase
import com.huntercoles.pokerpayout.core.domain.usecase.SettleTournamentUseCase
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlin.random.Random

/** The clock as the Bank sees it, set by hand. */
class FakeClockStatus(start: ClockStatus = ClockStatus.NOT_STARTED) : ClockStatusProvider {
    override val status = MutableStateFlow(start)

    /** Level [level] being played (or, with [onBreak], the break after it), with breaks after [breaks]. */
    fun at(level: Int, onBreak: Boolean = false, breaks: List<Int> = emptyList(), finished: Boolean = false) {
        status.value = ClockStatus(
            started = true,
            level = level,
            onBreak = onBreak,
            breakAfterLevels = breaks,
            finished = finished
        )
    }
}

/**
 * Everything a [BankViewModel] runs on, real where it can be: Robolectric's in-memory preferences,
 * the real settlement, a real snackbar queue; only the clock is a [FakeClockStatus]. The ViewModels
 * live in a store, so [clear] cancels their coroutines. Drive time with [dispatcher].
 */
class BankTestKit(private val dispatcher: TestDispatcher) {
    val context: Context = ApplicationProvider.getApplicationContext()
    var tournamentPreferences: TournamentPreferences
        private set
    var bankPreferences: BankPreferences
        private set
    var timerPreferences: TimerPreferences
        private set
    var audioPreferences: AudioPreferences
        private set
    val clock = FakeClockStatus()
    val snackbars = SnackbarController()
    private val stores = mutableListOf<ViewModelStore>()

    /** Where mystery-bounty envelopes are drawn from (PP-035): seeded, so a draw is the same every run. */
    var draws: Random = Random(DRAW_SEED)

    init {
        listOf("tournament_prefs", "bank_prefs", "timer_prefs", "audio_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        tournamentPreferences = TournamentPreferences(context)
        bankPreferences = BankPreferences(context)
        timerPreferences = TimerPreferences(context)
        audioPreferences = AudioPreferences(context)
    }

    fun newViewModel(): BankViewModel {
        val store = ViewModelStore().also { stores += it }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = BankViewModel(
                tournamentPreferences,
                bankPreferences,
                timerPreferences,
                SettleTournamentUseCase(CalculatePayoutsUseCase()),
                clock,
                audioPreferences,
                BankFeedback(context, snackbars),
                DrawEnvelopeUseCase(draws)
            ) as T
        }
        val viewModel = ViewModelProvider(store, factory)[BankViewModel::class.java]
        settle()
        return viewModel
    }

    fun clear() {
        stores.forEach { it.clear() }
        stores.clear()
    }

    /**
     * Process death and a cold start: every ViewModel cleared, every preference object built again
     * from what is saved (nothing kept in memory survives), and a new Bank on top.
     */
    fun restartProcess(): BankViewModel {
        clear()
        tournamentPreferences = TournamentPreferences(context)
        bankPreferences = BankPreferences(context)
        timerPreferences = TimerPreferences(context)
        audioPreferences = AudioPreferences(context)
        return newViewModel()
    }

    /** Runs everything due, including the 8 s Undo windows (virtual time). */
    fun settle() = dispatcher.scheduler.advanceUntilIdle()

    /** Runs what is due now, leaving the Undo snackbar up. */
    fun runCurrent() = dispatcher.scheduler.runCurrent()

    @Suppress("LongParameterList") // the Tournament tab's money fields
    fun configure(
        players: Int,
        buyIn: Double,
        food: Double = 0.0,
        bounty: Double = 0.0,
        rebuy: Double = 0.0,
        addOn: Double = 0.0,
        weights: List<Int>? = null
    ) {
        tournamentPreferences.setPlayerCount(players)
        tournamentPreferences.setBuyIn(buyIn)
        tournamentPreferences.setFoodPerPlayer(food)
        tournamentPreferences.setBountyPerPlayer(bounty)
        tournamentPreferences.setRebuyAmount(rebuy)
        tournamentPreferences.setAddOnAmount(addOn)
        weights?.let { tournamentPreferences.setPayoutWeights(it) }
    }

    fun BankViewModel.send(vararg intents: BankIntent) {
        intents.forEach { acceptIntent(it) }
        settle()
    }

    /** Sends [intent] and runs what is due now: the Undo snackbar is still up afterwards. */
    fun BankViewModel.act(intent: BankIntent) {
        acceptIntent(intent)
        runCurrent()
    }

    fun BankViewModel.player(id: Int) = uiState.value.players.first { it.id == id }

    fun BankViewModel.row(id: Int) = uiState.value.rows.first { it.playerId == id }

    /** The knockout sheet's answer: [eliminatorId] knocks [playerId] out (null: nobody). */
    fun BankViewModel.knockOut(playerId: Int, eliminatorId: Int?) = send(BankIntent.KnockOut(playerId, eliminatorId))

    /** The old Out toggle: knocked out by nobody, or back in. */
    fun BankViewModel.toggleOut(playerId: Int) = send(
        if (player(playerId).out) BankIntent.BringBack(playerId) else BankIntent.KnockOut(playerId, null)
    )

    fun BankViewModel.togglePaid(playerId: Int) = send(BankIntent.SetPaid(playerId, !player(playerId).paidOut))

    fun BankViewModel.setRebuys(playerId: Int, count: Int) = send(BankIntent.SetCount(playerId, Purchase.REBUY, count))

    fun BankViewModel.setAddOns(playerId: Int, count: Int) = send(BankIntent.SetCount(playerId, Purchase.ADD_ON, count))

    fun BankViewModel.saveWeights(weights: List<Int>) =
        send(BankIntent.UpdatePayoutSettings(PayoutSettings(weights, preset = null, rounding = PayoutRounding.DEFAULT)))

    /** The pay-out sheet for [playerId], closed again. */
    fun BankViewModel.payOutSheet(playerId: Int): BankSheet.PayOut {
        send(BankIntent.OpenPayOut(playerId))
        val sheet = uiState.value.sheet as BankSheet.PayOut
        send(BankIntent.DismissSheet)
        return sheet
    }

    /** Net pay the pay-out sheet shows for [playerId] (winnings minus everything they paid). */
    fun BankViewModel.netPayShownFor(playerId: Int): Long = payOutSheet(playerId).owed.netCents

    /** Everything the Bank recorded, for comparing before and after (names included). */
    fun BankViewModel.recorded() = uiState.value.players to uiState.value.eliminationOrder

    /** Taps Undo on the snackbar showing now. */
    fun pressSnackbarUndo() {
        runCurrent()
        val data = requireNotNull(snackbars.hostState.currentSnackbarData) { "no snackbar is showing" }
        data.performAction()
        settle()
    }

    fun snackbarMessage(): String? {
        runCurrent()
        return snackbars.hostState.currentSnackbarData?.visuals?.message
    }

    private companion object {
        const val DRAW_SEED = 35
    }
}
