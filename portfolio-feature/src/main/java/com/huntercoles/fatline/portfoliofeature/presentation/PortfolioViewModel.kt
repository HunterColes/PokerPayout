package com.huntercoles.fatline.portfoliofeature.presentation

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import com.huntercoles.fatline.core.presentation.mvi.BaseViewModel
import com.huntercoles.fatline.portfoliofeature.presentation.PortfolioEvent.NavigateToStockDetails
import com.huntercoles.fatline.portfoliofeature.presentation.PortfolioIntent.RefreshPortfolio
import com.huntercoles.fatline.portfoliofeature.presentation.PortfolioIntent.StockClicked
import com.huntercoles.fatline.portfoliofeature.presentation.PortfolioUiState.PartialState
import com.huntercoles.fatline.portfoliofeature.presentation.PortfolioUiState.PartialState.Error
import com.huntercoles.fatline.portfoliofeature.presentation.PortfolioUiState.PartialState.Fetched
import com.huntercoles.fatline.portfoliofeature.presentation.PortfolioUiState.PartialState.Loading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val portfolioUiState: PortfolioUiState,
) : BaseViewModel<PortfolioUiState, PartialState, PortfolioEvent, PortfolioIntent>(
    savedStateHandle = savedStateHandle,
    initialState = portfolioUiState,
) {

    override fun mapIntents(intent: PortfolioIntent): Flow<PartialState> = when (intent) {
        RefreshPortfolio -> refreshPortfolio()
        is StockClicked -> stockClicked(intent.symbol)
    }

    override fun reduceUiState(
        previousState: PortfolioUiState,
        partialState: PartialState,
    ): PortfolioUiState = when (partialState) {
        is Loading -> previousState.copy(isLoading = true, isError = false)
        is Fetched -> previousState.copy(
            isLoading = false,
            isError = false,
            holdings = partialState.holdings,
            portfolioValue = partialState.holdings.sumOf { it.totalValue },
            totalChange = partialState.holdings.sumOf { it.totalReturn },
            totalChangePercent = if (partialState.holdings.isNotEmpty()) {
                val totalCost = partialState.holdings.sumOf { it.averageCost * it.shares }
                if (totalCost > 0) ((partialState.holdings.sumOf { it.totalValue } - totalCost) / totalCost) * 100 else 0.0
            } else 0.0
        )
        is Error -> {
            Timber.e(partialState.throwable)
            previousState.copy(isLoading = false, isError = true)
        }
    }

    private fun refreshPortfolio(): Flow<PartialState> = flow {
        // TODO: Implement actual portfolio fetching from repository
        // For now, emit empty list as a placeholder
        emit(Loading)
        emit(Fetched(emptyList()))
    }

    private fun stockClicked(symbol: String): Flow<PartialState> = flow {
        setEvent(NavigateToStockDetails(symbol))
    }
}
