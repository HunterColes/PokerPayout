package com.huntercoles.fatline.portfoliofeature.presentation

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PlayerData(
    val id: Int,
    val name: String,
    val buyIn: Boolean = false,    // Buy-In button
    val out: Boolean = false,      // Out button (replaces eliminated)
    val payedOut: Boolean = false  // Payed-Out button
) : Parcelable

@Parcelize
data class BankUiState(
    val players: List<PlayerData> = emptyList(),
    val totalPool: Double = 0.0,
    val totalPaid: Double = 0.0,
    val percentPaid: Double = 0.0,
    val activePlayers: Int = 0,
    val payedOutCount: Int = 0,
    val buyInAmount: Double = 20.0,
    val foodAmount: Double = 5.0,
    val bountyAmount: Double = 2.0,
    val showResetDialog: Boolean = false,
    val eliminationOrder: List<Int> = emptyList()
) : Parcelable