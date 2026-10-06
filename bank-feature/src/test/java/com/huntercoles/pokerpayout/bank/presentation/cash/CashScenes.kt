package com.huntercoles.pokerpayout.bank.presentation.cash

/**
 * The mockup's cash night (S13) as states of the real [CashViewModel]: Dana, Priya, Jo, Sam, Marcus
 * and Theo, $260 in. Dana +72, Priya +28, Jo +12, Sam −20, Marcus −45, Theo −47.
 */
object CashScenes {
    /** Nobody has sat down. */
    fun empty(kit: CashTestKit): CashViewModel = kit.newCashViewModel()

    /** Everyone in, Sam and Theo still to count. */
    fun counting(kit: CashTestKit): CashViewModel = with(kit) {
        val viewModel = newCashViewModel()
        s13(viewModel)
        viewModel.cashOut("Sam", null)
        viewModel.cashOut("Theo", null)
        viewModel
    }

    /** S13: counted, balanced, five payments. */
    fun balanced(kit: CashTestKit): CashViewModel = with(kit) {
        val viewModel = newCashViewModel()
        s13(viewModel)
        viewModel
    }

    /** Dana's chips counted at $117: off by $5, settle-up waiting. */
    fun off(kit: CashTestKit): CashViewModel = with(kit) {
        val viewModel = balanced(kit)
        viewModel.cashOut("Dana", 117)
        viewModel
    }

    /** Off by $5, and the players chose to split it. */
    fun split(kit: CashTestKit): CashViewModel = with(kit) {
        val viewModel = off(kit)
        viewModel.send(CashIntent.SplitDifference)
        viewModel
    }

    /** Balanced, Theo has paid Dana. */
    fun settling(kit: CashTestKit): CashViewModel = with(kit) {
        val viewModel = balanced(kit)
        viewModel.send(CashIntent.SetPaid(viewModel.state.transfers.first(), true))
        viewModel
    }
}
