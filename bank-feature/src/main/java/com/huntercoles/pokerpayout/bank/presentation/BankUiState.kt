package com.huntercoles.pokerpayout.bank.presentation

import android.os.Parcelable
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.PayoutSettings
import com.huntercoles.pokerpayout.core.domain.model.PayoutTable
import com.huntercoles.pokerpayout.core.domain.model.PoolBreakdown
import kotlinx.parcelize.Parcelize

const val MAX_PURCHASE_COUNT = 20

@Parcelize
data class PlayerData(
    val id: Int,
    val name: String,
    val buyIn: Boolean = false,    // Buy-In button
    val out: Boolean = false,      // Out button (replaces eliminated)
    val payedOut: Boolean = false, // Payed-Out button
    val rebuys: Int = 0,          // Number of rebuys
    val addons: Int = 0,          // Number of addons
    val eliminatedBy: Int? = null // Player id who eliminated this player
) : Parcelable

/** Bank screen state. Every amount is in cents and comes from one [SettleTournamentUseCase] run. */
data class BankUiState(
    val players: List<PlayerData> = emptyList(),
    val pool: PoolBreakdown = PoolBreakdown.EMPTY,
    /** Entry fees of players marked as bought in, plus every recorded rebuy and add-on. */
    val totalPaidInCents: Long = 0L,
    /** Winnings of the players marked as paid out. */
    val totalPaidOutCents: Long = 0L,
    val totalRebuyCount: Int = 0,
    val totalAddonCount: Int = 0,
    val activePlayers: Int = 0,
    val payedOutCount: Int = 0,
    val money: MoneySettings = MoneySettings.DEFAULT,
    val showResetDialog: Boolean = false,
    val eliminationOrder: List<Int> = emptyList(),
    val pendingAction: PendingPlayerAction? = null,
    val knockoutCounts: Map<Int, Int> = emptyMap(),
    /** Players who have won something: a paid place, a bounty or both. */
    val payoutEligiblePlayerIds: Set<Int> = emptySet(),
    val payoutTable: PayoutTable = PayoutTable.EMPTY,
    val payoutSettings: PayoutSettings = PayoutSettings(
        weights = emptyList(),
        preset = PayoutPreset.DEFAULT,
        rounding = PayoutRounding.DEFAULT
    ),
    /** Player id to finishing place, for places already decided. */
    val placeByPlayer: Map<Int, Int> = emptyMap(),
    val isTimerRunning: Boolean = false,
    val showWeightsDialog: Boolean = false,
    val showPoolSummaryDialog: Boolean = false
) {
    val totalPoolCents: Long get() = pool.totalCents
    val prizePoolCents: Long get() = pool.prizePoolCents

    /** What the bank pays back out in total: the prize pool plus the bounty pool. */
    val payableCents: Long get() = pool.payableCents
    val payoutWeights: List<Int> get() = payoutSettings.weights
    val isRebuyEnabled: Boolean get() = money.rebuyCents > 0L
    val isAddOnEnabled: Boolean get() = money.addOnCents > 0L
}

@Parcelize
data class PendingPlayerAction(
    val playerId: Int,
    val actionType: PlayerActionType,
    val apply: Boolean,
    val delta: Int = 0,
    val baseCount: Int = 0,
    val targetCount: Int = 0,
    val selectablePlayerIds: List<Int> = emptyList(),
    val selectedPlayerId: Int? = null,
    val allowUnassignedSelection: Boolean = false,
    /** Net pay: everything won minus everything paid in. */
    val payoutAmountCents: Long = 0L,
    /** This player's row of the payout table. */
    val buyInPayoutCents: Long = 0L,
    val buyInCostCents: Long = 0L,
    val knockoutBonusCents: Long = 0L,
    val kingsBountyCents: Long = 0L,
    /** Bounties of knockouts nobody was credited with, which the champion collects. */
    val unclaimedBountyCents: Long = 0L,
    val knockoutCount: Int = 0
) : Parcelable
