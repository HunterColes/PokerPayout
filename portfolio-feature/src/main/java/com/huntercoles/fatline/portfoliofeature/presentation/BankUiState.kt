package com.huntercoles.fatline.portfoliofeature.presentation

import android.os.Parcelable
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
    val addons: Int = 0           // Number of addons
) : Parcelable

@Parcelize
data class BankUiState(
    val players: List<PlayerData> = emptyList(),
    val totalPool: Double = 0.0,
    val totalPaidIn: Double = 0.0,
    val totalPayedOut: Double = 0.0,
    val prizePool: Double = 0.0,
    val buyInPool: Double = 0.0,
    val foodPool: Double = 0.0,
    val bountyPool: Double = 0.0,
    val rebuyPool: Double = 0.0,
    val addonPool: Double = 0.0,
    val totalRebuyCount: Int = 0,
    val totalAddonCount: Int = 0,
    val activePlayers: Int = 0,
    val payedOutCount: Int = 0,
    val buyInAmount: Double = 20.0,
    val foodAmount: Double = 5.0,
    val bountyAmount: Double = 2.0,
    val rebuyAmount: Double = 0.0,
    val addonAmount: Double = 0.0,
    val showResetDialog: Boolean = false,
    val eliminationOrder: List<Int> = emptyList(),
    val pendingAction: PendingPlayerAction? = null
) : Parcelable

@Parcelize
data class PendingPlayerAction(
    val playerId: Int,
    val actionType: PlayerActionType,
    val apply: Boolean,
    val delta: Int = 0,
    val baseCount: Int = 0,
    val targetCount: Int = 0
) : Parcelable