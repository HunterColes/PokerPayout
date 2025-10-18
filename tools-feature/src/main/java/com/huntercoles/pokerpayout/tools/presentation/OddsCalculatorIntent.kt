package com.huntercoles.pokerpayout.tools.presentation

import com.huntercoles.pokerpayout.tools.presentation.composable.CardType

sealed class OddsCalculatorIntent {
    data class PlayerCountChanged(val count: Int) : OddsCalculatorIntent()
    data class ShowCardPickerForPlayer(val playerId: Int) : OddsCalculatorIntent()
    object ShowCardPickerForCommunity : OddsCalculatorIntent()
    data class CardSelected(val cardString: String) : OddsCalculatorIntent()
    data class PlayerCardRemoved(val playerId: Int, val cardIndex: Int) : OddsCalculatorIntent()
    data class CommunityCardRemoved(val cardIndex: Int) : OddsCalculatorIntent()
    object HideCardPicker : OddsCalculatorIntent()
    data class StartSimulation(val players: List<com.huntercoles.pokerpayout.tools.presentation.composable.Player>, val communityCards: List<com.huntercoles.pokerpayout.core.design.components.PlayingCard>) : OddsCalculatorIntent()
    data class SimulationComplete(val players: List<com.huntercoles.pokerpayout.tools.presentation.composable.Player>) : OddsCalculatorIntent()
    object ShowResetDialog : OddsCalculatorIntent()
    object HideResetDialog : OddsCalculatorIntent()
    object ConfirmReset : OddsCalculatorIntent()
}
