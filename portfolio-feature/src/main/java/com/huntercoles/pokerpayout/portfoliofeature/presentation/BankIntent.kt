package com.huntercoles.pokerpayout.portfoliofeature.presentation

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
sealed class BankIntent {
    data class PlayerNameChanged(val playerId: Int, val name: String) : BankIntent()
    data class BuyInToggled(val playerId: Int) : BankIntent()
    data class OutToggled(val playerId: Int) : BankIntent()
    data class PayedOutToggled(val playerId: Int) : BankIntent()
    data class PlayerCountChanged(val count: Int) : BankIntent()
    data class PlayerRebuyChanged(val playerId: Int, val rebuys: Int) : BankIntent()
    data class PlayerAddonChanged(val playerId: Int, val addons: Int) : BankIntent()
    data class ShowPlayerActionDialog(val playerId: Int, val action: PlayerActionType) : BankIntent()
    object ConfirmPlayerAction : BankIntent()
    data class ConfirmPlayerActionWithCount(
        val count: Int? = null,
        val selectedPlayerId: Int? = null
    ) : BankIntent()
    object CancelPlayerAction : BankIntent()
    object ShowResetDialog : BankIntent()
    object HideResetDialog : BankIntent()
    object ConfirmReset : BankIntent()
    object ShowWeightsDialog : BankIntent()
    object HideWeightsDialog : BankIntent()
    data class UpdateWeights(val weights: List<Int>) : BankIntent()
    object ShowPoolSummaryDialog : BankIntent()
    object HidePoolSummaryDialog : BankIntent()
}

@Parcelize
enum class PlayerActionType : Parcelable {
    BUY_IN,
    OUT,
    PAYED_OUT,
    REBUY,
    ADDON
}
