package com.huntercoles.pokerpayout.tournament.presentation

import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.PayoutSettings

/**
 * User intents for the tournament config screen. Money is in cents.
 *
 * The `Update*` money intents carry the amount while the user types; the `Commit*` ones arrive when
 * the field is left (focus loss, Done, tab switch). Only a committed zero rebuy or add-on amount can
 * clear recorded purchases, and only after the user confirms it.
 */
sealed class TournamentConfigIntent {
    data class UpdatePlayerCount(val count: Int) : TournamentConfigIntent()
    data class UpdateBuyIn(val cents: Long) : TournamentConfigIntent()
    data class UpdateFoodPerPlayer(val cents: Long) : TournamentConfigIntent()
    data class UpdateBountyPerPlayer(val cents: Long) : TournamentConfigIntent()

    /** Standard, progressive or mystery bounties (PP-035); only before the first knockout. */
    data class UpdateBountyMode(val mode: BountyMode) : TournamentConfigIntent()
    data class UpdateRebuyAmount(val cents: Long) : TournamentConfigIntent()
    data class UpdateAddOnAmount(val cents: Long) : TournamentConfigIntent()
    data class CommitRebuyAmount(val cents: Long, val centsBeforeEdit: Long) : TournamentConfigIntent()
    data class CommitAddOnAmount(val cents: Long, val centsBeforeEdit: Long) : TournamentConfigIntent()
    object ConfirmClearPurchases : TournamentConfigIntent()
    object DismissClearPurchases : TournamentConfigIntent()
    data class UpdateWeights(val weights: List<Int>) : TournamentConfigIntent()
    data class UpdatePayoutSettings(val settings: PayoutSettings) : TournamentConfigIntent()
    data class ApplyPayoutPreset(val preset: PayoutPreset) : TournamentConfigIntent()
    data class SetPaidPlaces(val places: Int) : TournamentConfigIntent()
    data class UpdatePayoutRounding(val rounding: PayoutRounding) : TournamentConfigIntent()
    object ShowWeightsEditor : TournamentConfigIntent()
    object HideWeightsEditor : TournamentConfigIntent()
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
