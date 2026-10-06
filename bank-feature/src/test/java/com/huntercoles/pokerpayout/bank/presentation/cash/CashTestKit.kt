package com.huntercoles.pokerpayout.bank.presentation.cash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.huntercoles.pokerpayout.bank.presentation.BankTestKit
import com.huntercoles.pokerpayout.core.domain.cash.SettleCashUseCase
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import kotlinx.coroutines.test.TestDispatcher

/**
 * A [CashViewModel] on the same real, in-memory preferences as the tournament's [BankTestKit], so a
 * test can drive both games side by side. [newCashViewModel] with `fresh = true` reads everything
 * back through new preference objects, as after process death.
 */
class CashTestKit(dispatcher: TestDispatcher) {
    val bank = BankTestKit(dispatcher)
    private val stores = mutableListOf<ViewModelStore>()

    fun newCashViewModel(fresh: Boolean = false): CashViewModel {
        val store = ViewModelStore().also { stores += it }
        val preferences = if (fresh) BankPreferences(bank.context) else bank.bankPreferences
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CashViewModel(preferences, SettleCashUseCase(), CashFeedback(bank.context, bank.snackbars)) as T
        }
        val viewModel = ViewModelProvider(store, factory)[CashViewModel::class.java]
        bank.settle()
        return viewModel
    }

    /** Clears every ViewModel (process death), cash and tournament. */
    fun clear() {
        stores.forEach { it.clear() }
        stores.clear()
        bank.clear()
    }

    fun CashViewModel.send(vararg intents: CashIntent) {
        intents.forEach { acceptIntent(it) }
        bank.settle()
    }

    /** Sends [intent] and runs what is due now: its Undo snackbar is still up afterwards. */
    fun CashViewModel.act(intent: CashIntent) {
        acceptIntent(intent)
        bank.runCurrent()
    }

    val CashViewModel.state: CashUiState get() = uiState.value

    fun CashViewModel.id(name: String): Int = state.players.first { it.name == name }.id

    /** Adds [name] with a buy-in of [dollars], then any [topUps]. */
    fun CashViewModel.sitDown(name: String, dollars: Int, vararg topUps: Int) {
        send(CashIntent.AddPlayer(name, dollars * CENTS))
        val id = id(name)
        topUps.forEach { send(CashIntent.TopUp(id, it * CENTS)) }
    }

    fun CashViewModel.cashOut(name: String, dollars: Int?) = send(CashIntent.SetCashOut(id(name), dollars?.let { it * CENTS }))

    /** The mockup's night (S13): six players, $260 in, counted out $260. */
    fun s13(viewModel: CashViewModel, counted: Boolean = true) = with(viewModel) {
        sitDown("Dana", 40)
        sitDown("Priya", 20)
        sitDown("Jo", 20, 20)
        sitDown("Sam", 20)
        sitDown("Marcus", 40, 20)
        sitDown("Theo", 40, 20, 20)
        if (counted) {
            mapOf("Dana" to 112, "Priya" to 48, "Jo" to 52, "Sam" to 0, "Marcus" to 15, "Theo" to 33)
                .forEach { (name, dollars) -> cashOut(name, dollars) }
        }
    }

    /** "Theo pays Dana 47" lines for the settle-up on screen. */
    fun CashViewModel.payments(): List<String> =
        state.transfers.map { "${state.nameOf(it.fromId)} pays ${state.nameOf(it.toId)} ${it.amountCents / CENTS}" }

    fun pressSnackbarUndo() = bank.pressSnackbarUndo()

    fun snackbarMessage(): String? = bank.snackbarMessage()

    companion object {
        const val CENTS = 100L
    }
}
