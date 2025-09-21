package com.huntercoles.fatline.basicfeature.presentation

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CalculatorUiState(
    val playerCount: Int = 9,
    val buyIn: Double = 20.0,
    val foodPool: Double = 5.0,
    val bountyPool: Double = 2.0,
    val totalPerPlayer: Double = 27.0,
    val prizePool: Double = 180.0,
    val totalPool: Double = 225.0,
    val payouts: List<Pair<Int, Double>> = emptyList()
) : Parcelable