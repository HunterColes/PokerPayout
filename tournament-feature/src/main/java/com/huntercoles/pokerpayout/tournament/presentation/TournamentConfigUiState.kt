package com.huntercoles.pokerpayout.tournament.presentation

import com.huntercoles.pokerpayout.core.constants.TournamentDefaults
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.model.PayoutPlaces
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.PayoutTable
import com.huntercoles.pokerpayout.core.domain.model.PoolBreakdown
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences.TournamentConfigData

/** Which purchases a zero amount would clear. */
enum class PurchaseKind { REBUY, ADD_ON }

/** Asks before a zero rebuy or add-on amount clears [count] recorded purchases. */
data class PurchaseClearPrompt(val kind: PurchaseKind, val count: Int, val keptAmountCents: Long)

/**
 * UI state for the tournament config screen
 */
data class TournamentConfigUiState(
    val config: TournamentConfigData = TournamentConfigData(
        numPlayers = TournamentDefaults.PLAYER_COUNT,
        money = MoneySettings.DEFAULT,
        payoutWeights = PayoutPreset.DEFAULT.weightsFor(PayoutPlaces.recommended(TournamentDefaults.PLAYER_COUNT)),
        payoutRounding = PayoutRounding.DEFAULT
    ),
    val pool: PoolBreakdown = PoolBreakdown.EMPTY,
    val payoutTable: PayoutTable = PayoutTable.EMPTY,
    /** The preset the weights come from; null once edited by hand. */
    val payoutPreset: PayoutPreset? = PayoutPreset.DEFAULT,
    val recommendedPlaces: Int = PayoutPlaces.recommended(TournamentDefaults.PLAYER_COUNT),
    /** Place to player name, for places already decided in the Bank. */
    val placeNames: Map<Int, String> = emptyMap(),
    val isTournamentLocked: Boolean = false,
    val isConfigExpanded: Boolean = true,
    val isBlindConfigExpanded: Boolean = false,
    val gameDurationHours: Int = TournamentDefaults.GAME_DURATION_HOURS,
    val roundLengthMinutes: Int = TournamentDefaults.ROUND_LENGTH_MINUTES,
    val smallestChip: Int = TournamentDefaults.SMALLEST_CHIP,
    val startingChips: Int = TournamentDefaults.STARTING_CHIPS,
    val showResetDialog: Boolean = false,
    val showWeightsEditor: Boolean = false,
    val purchaseClearPrompt: PurchaseClearPrompt? = null,
    val rebuyPurchases: Int = 0,
    val addOnPurchases: Int = 0,
    val selectedPanel: String = "player"
) {
    val money: MoneySettings get() = config.money
    val playerCount: Int get() = config.numPlayers
    val paidPlaces: Int get() = payoutTable.places.size
}
