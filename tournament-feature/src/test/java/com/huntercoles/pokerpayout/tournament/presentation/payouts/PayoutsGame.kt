package com.huntercoles.pokerpayout.tournament.presentation.payouts

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.core.domain.usecase.SettleTournamentUseCase
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences

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

    init {
        listOf("tournament_prefs", "timer_prefs", "bank_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
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
                CalculatePayoutsUseCase()
            ) as T
        }
        return ViewModelProvider(store, factory)[PayoutsViewModel::class.java]
    }

    fun clear() = store.clear()

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
