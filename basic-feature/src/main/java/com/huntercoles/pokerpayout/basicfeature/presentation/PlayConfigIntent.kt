package com.huntercoles.pokerpayout.basicfeature.presentation

/**
 * User intents for the play config screen
 */
sealed class PlayConfigIntent {
    data class UpdatePlayerCount(val count: Int) : PlayConfigIntent()
    data class UpdateBuyIn(val buyIn: Double) : PlayConfigIntent()
    data class UpdateFoodPerPlayer(val food: Double) : PlayConfigIntent()
    data class UpdateBountyPerPlayer(val bounty: Double) : PlayConfigIntent()
    data class UpdateRebuyAmount(val rebuy: Double) : PlayConfigIntent()
    data class UpdateAddOnAmount(val addOn: Double) : PlayConfigIntent()
    data class UpdateWeights(val weights: List<Int>) : PlayConfigIntent()
    data class ToggleConfigExpanded(val isExpanded: Boolean) : PlayConfigIntent()
    data class ToggleBlindConfigExpanded(val isExpanded: Boolean) : PlayConfigIntent()
    data class UpdateGameDurationHours(val hours: Int) : PlayConfigIntent()
    data class UpdateRoundLength(val minutes: Int) : PlayConfigIntent()
    data class UpdateSmallestChip(val chip: Int) : PlayConfigIntent()
    data class UpdateStartingChips(val chips: Int) : PlayConfigIntent()
    object ShowResetDialog : PlayConfigIntent()
    object HideResetDialog : PlayConfigIntent()
    object ConfirmReset : PlayConfigIntent()
}