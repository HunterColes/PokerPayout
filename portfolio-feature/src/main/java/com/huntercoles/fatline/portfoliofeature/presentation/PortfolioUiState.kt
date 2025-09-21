package com.huntercoles.fatline.portfoliofeature.presentation

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PortfolioUiState(
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val portfolioValue: Double = 0.0,
    val totalChange: Double = 0.0,
    val totalChangePercent: Double = 0.0,
    val holdings: List<StockHolding> = emptyList(),
) : Parcelable {

    sealed class PartialState {
        data object Loading : PartialState()
        data class Fetched(val holdings: List<StockHolding>) : PartialState()
        data class Error(val throwable: Throwable) : PartialState()
    }

    val isEmpty: Boolean get() = holdings.isEmpty()
    val isNotEmpty: Boolean get() = holdings.isNotEmpty()
}

@Parcelize
data class StockHolding(
    val symbol: String,
    val name: String,
    val shares: Double,
    val averageCost: Double,
    val currentPrice: Double,
    val totalValue: Double,
    val totalReturn: Double,
    val returnPercent: Double,
) : Parcelable
