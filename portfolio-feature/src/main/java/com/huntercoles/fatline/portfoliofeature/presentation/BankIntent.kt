package com.huntercoles.fatline.portfoliofeature.presentation

sealed class BankIntent {
    data class PlayerNameChanged(val playerId: Int, val name: String) : BankIntent()
    data class BuyInToggled(val playerId: Int) : BankIntent()
    data class OutToggled(val playerId: Int) : BankIntent()
    data class PayedOutToggled(val playerId: Int) : BankIntent()
    data class PlayerCountChanged(val count: Int) : BankIntent()
    data class PlayerRebuyChanged(val playerId: Int, val rebuys: Int) : BankIntent()
    data class PlayerAddonChanged(val playerId: Int, val addons: Int) : BankIntent()
    object ShowResetDialog : BankIntent()
    object HideResetDialog : BankIntent()
    object ConfirmReset : BankIntent()
}