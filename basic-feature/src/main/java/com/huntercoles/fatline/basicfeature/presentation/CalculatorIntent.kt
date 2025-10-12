package com.huntercoles.fatline.basicfeature.presentation

/**
 * User intents for the calculator screen
 */
sealed class CalculatorIntent {
    data class UpdatePlayerCount(val count: Int) : CalculatorIntent()
    data class UpdateBuyIn(val buyIn: Double) : CalculatorIntent()
    data class UpdateFoodPerPlayer(val food: Double) : CalculatorIntent()
    data class UpdateBountyPerPlayer(val bounty: Double) : CalculatorIntent()
    data class UpdateRebuyAmount(val rebuy: Double) : CalculatorIntent()
    data class UpdateAddOnAmount(val addOn: Double) : CalculatorIntent()
    data class UpdateWeights(val weights: List<Int>) : CalculatorIntent()
    data class ToggleConfigExpanded(val isExpanded: Boolean) : CalculatorIntent()
    data class ToggleBlindConfigExpanded(val isExpanded: Boolean) : CalculatorIntent()
    data class UpdateGameDurationHours(val hours: Int) : CalculatorIntent()
    data class UpdateRoundLength(val minutes: Int) : CalculatorIntent()
    data class UpdateSmallestChip(val chip: Int) : CalculatorIntent()
    data class UpdateStartingChips(val chips: Int) : CalculatorIntent()
    object ShowResetDialog : CalculatorIntent()
    object HideResetDialog : CalculatorIntent()
    object ConfirmReset : CalculatorIntent()
}