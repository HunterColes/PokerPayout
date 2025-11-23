package com.huntercoles.pokerpayout.tournament.presentation

/**
 * User intents for the tournament config screen
 */
sealed class TournamentConfigIntent {
    data class UpdatePlayerCount(val count: Int) : TournamentConfigIntent()
    data class UpdateBuyIn(val buyIn: Double) : TournamentConfigIntent()
    data class UpdateFoodPerPlayer(val food: Double) : TournamentConfigIntent()
    data class UpdateBountyPerPlayer(val bounty: Double) : TournamentConfigIntent()
    data class UpdateRebuyAmount(val rebuy: Double) : TournamentConfigIntent()
    data class UpdateAddOnAmount(val addOn: Double) : TournamentConfigIntent()
    data class UpdateWeights(val weights: List<Int>) : TournamentConfigIntent()
    data class ToggleConfigExpanded(val isExpanded: Boolean) : TournamentConfigIntent()
    data class ToggleBlindConfigExpanded(val isExpanded: Boolean) : TournamentConfigIntent()
    data class UpdateGameDurationHours(val hours: Int) : TournamentConfigIntent()
    data class UpdateRoundLength(val minutes: Int) : TournamentConfigIntent()
    data class UpdateSmallestChip(val chip: Int) : TournamentConfigIntent()
    data class UpdateStartingChips(val chips: Int) : TournamentConfigIntent()
    data class UpdateSelectedPanel(val panel: String) : TournamentConfigIntent()
    object ShowResetDialog : TournamentConfigIntent()
    object HideResetDialog : TournamentConfigIntent()
    object ConfirmReset : TournamentConfigIntent()
}