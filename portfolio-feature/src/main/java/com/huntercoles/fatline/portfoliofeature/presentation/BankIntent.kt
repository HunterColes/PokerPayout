package com.huntercoles.fatline.portfoliofeature.presentation

sealed class BankIntent {
    data class PlayerNameChanged(val playerId: Int, val name: String) : BankIntent()
    data class BuyInToggled(val playerId: Int) : BankIntent()
    data class FoodToggled(val playerId: Int) : BankIntent()
    data class BountyToggled(val playerId: Int) : BankIntent()
    data class AllToggled(val playerId: Int) : BankIntent()
    data class EliminatedToggled(val playerId: Int) : BankIntent()
    data class PayedOutToggled(val playerId: Int) : BankIntent()
    data class PlayerCountChanged(val count: Int) : BankIntent()
}