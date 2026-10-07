package com.huntercoles.pokerpayout.tournament.presentation.payouts

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.domain.history.NightStore
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.core.domain.usecase.SettleTournamentUseCase
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.ChipCalculatorPreferences
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.time.TimeSource
import com.huntercoles.pokerpayout.tournament.domain.presets.CurrentSetup
import com.huntercoles.pokerpayout.tournament.domain.presets.PresetStore

/**
 * The mockups' game for the Payouts tab (S6), recorded straight into the preferences: 9 players
 * (Dana, Marcus, Priya, Theo, Jo, Sam, Alex, Rita, Ben) at $40, $5 food, $5 bounty; Marcus rebought
 * ($40), five players took the $10 add-on; Ben is out 9th (by Dana) and Rita 8th (by Marcus). A $450
 * prize pool, Standard, rounded to $5: 225 / 130 / 95.
 */
class PayoutsGame {
    val context: Context = ApplicationProvider.getApplicationContext()
    val tournament: TournamentPreferences
    val bank: BankPreferences
    private val store = ViewModelStore()

    val nights: NightStore
    val presets: PresetStore
    val setup: CurrentSetup

    init {
        listOf("tournament_prefs", "timer_prefs", "bank_prefs", "chip_calculator_prefs", "tournament_presets", "night_history")
            .forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        tournament = TournamentPreferences(context).apply {
            setPlayerCount(NAMES.size)
            setBuyIn(40.0)
            setFoodPerPlayer(5.0)
            setBountyPerPlayer(5.0)
            setRebuyAmount(40.0)
            setAddOnAmount(10.0)
            setPayoutPreset(PayoutPreset.STANDARD, 3)
            setPayoutRounding(PayoutRounding.FIVE_DOLLARS)
        }
        bank = BankPreferences(context)
        nights = NightStore(context)
        presets = PresetStore(context)
        setup = CurrentSetup(tournament, TimerPreferences(context), ChipCalculatorPreferences(context, tournament), bank)
    }

    /** Names, buy-ins, Marcus's rebuy and the five add-ons. */
    fun midGame(): PayoutsGame = apply {
        NAMES.forEachIndexed { index, name ->
            bank.savePlayerName(index + 1, name)
            bank.savePlayerBuyInStatus(index + 1, true)
        }
        bank.savePlayerRebuyPrices(MARCUS, listOf(4_000L))
        (1..5).forEach { bank.savePlayerAddonPrices(it, listOf(1_000L)) }
        knockOut(BEN, by = DANA)
        knockOut(RITA, by = MARCUS)
    }

    /** Everyone but Dana out: Marcus 2nd, Priya 3rd, the rest outside the money. */
    fun finished(): PayoutsGame = apply {
        midGame()
        listOf(ALEX, SAM, JO, THEO).forEach { knockOut(it, by = DANA) }
        knockOut(PRIYA, by = null)
        knockOut(MARCUS, by = DANA)
    }

    /** Finished, and everyone owed money paid (Dana, Marcus and Priya): the night can be saved (PP-037). */
    fun settled(): PayoutsGame = apply {
        finished()
        listOf(DANA, MARCUS, PRIYA).forEach { bank.savePlayerPayedOutStatus(it, true) }
    }

    fun knockOut(id: Int, by: Int?) {
        bank.savePlayerOutStatus(id, true)
        bank.savePlayerEliminatedBy(id, by)
        bank.saveEliminationOrder(bank.getEliminationOrder() + id)
    }

    fun viewModel(): PayoutsViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PayoutsViewModel(
                tournament,
                bank,
                SettleTournamentUseCase(CalculatePayoutsUseCase()),
                CalculatePayoutsUseCase(),
                NightRecorder(nights, presets, setup, Midday)
            ) as T
        }
        return ViewModelProvider(store, factory)[PayoutsViewModel::class.java]
    }

    fun clear() = store.clear()

    /** Midday on 5 October 2026 (UTC): a night saved in these tests is dated that day. */
    private object Midday : TimeSource {
        override fun elapsedRealtimeMillis(): Long = 0L

        override fun wallClockMillis(): Long = 1_791_201_600_000L

        override fun bootCount(): Int = -1
    }

    companion object {
        val NAMES = listOf("Dana", "Marcus", "Priya", "Theo", "Jo", "Sam", "Alex", "Rita", "Ben")
        const val DANA = 1
        const val MARCUS = 2
        const val PRIYA = 3
        const val THEO = 4
        const val JO = 5
        const val SAM = 6
        const val ALEX = 7
        const val RITA = 8
        const val BEN = 9
    }
}
