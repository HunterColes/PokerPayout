package com.huntercoles.pokerpayout.tools.presentation

sealed class OddsCalculatorIntent {
    data class PlayerCountChanged(val count: Int) : OddsCalculatorIntent()
    data class ShowCardPickerForPlayer(val playerId: Int) : OddsCalculatorIntent()
    object ShowCardPickerForCommunity : OddsCalculatorIntent()
    data class CardSelected(val cardString: String) : OddsCalculatorIntent()
    data class PlayerCardRemoved(val playerId: Int, val cardIndex: Int) : OddsCalculatorIntent()
    data class CommunityCardRemoved(val cardIndex: Int) : OddsCalculatorIntent()
    object HideCardPicker : OddsCalculatorIntent()

    /** Compute odds for the current cards. Results stream into [OddsCalculatorUiState.result]. */
    object Calculate : OddsCalculatorIntent()
    object ShowResetDialog : OddsCalculatorIntent()
    object HideResetDialog : OddsCalculatorIntent()
    object ConfirmReset : OddsCalculatorIntent()
}
