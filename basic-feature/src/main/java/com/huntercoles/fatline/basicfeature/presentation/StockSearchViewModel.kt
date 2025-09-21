package com.huntercoles.fatline.basicfeature.presentation

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import com.huntercoles.fatline.core.presentation.mvi.BaseViewModel
import com.huntercoles.fatline.basicfeature.presentation.StockSearchEvent.AddedToPortfolio
import com.huntercoles.fatline.basicfeature.presentation.StockSearchEvent.ShowMessage
import com.huntercoles.fatline.basicfeature.presentation.StockSearchEvent.ShowStockDetails
import com.huntercoles.fatline.basicfeature.presentation.StockSearchIntent.AddToPortfolio
import com.huntercoles.fatline.basicfeature.presentation.StockSearchIntent.RefreshStocks
import com.huntercoles.fatline.basicfeature.presentation.StockSearchIntent.SearchQueryChanged
import com.huntercoles.fatline.basicfeature.presentation.StockSearchIntent.StockClicked
import com.huntercoles.fatline.basicfeature.presentation.StockSearchUiState.PartialState
import com.huntercoles.fatline.basicfeature.presentation.StockSearchUiState.PartialState.Error
import com.huntercoles.fatline.basicfeature.presentation.StockSearchUiState.PartialState.Fetched
import com.huntercoles.fatline.basicfeature.presentation.StockSearchUiState.PartialState.Loading
import com.huntercoles.fatline.basicfeature.presentation.StockSearchUiState.PartialState.SearchQueryChanged as SearchQueryChangedState
import com.huntercoles.fatline.basicfeature.presentation.model.StockDisplayable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class StockSearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val stockSearchUiState: StockSearchUiState,
) : BaseViewModel<StockSearchUiState, PartialState, StockSearchEvent, StockSearchIntent>(
    savedStateHandle = savedStateHandle,
    initialState = stockSearchUiState,
) {

    override fun mapIntents(intent: StockSearchIntent): Flow<PartialState> = when (intent) {
        RefreshStocks -> refreshStocks()
        is SearchQueryChanged -> searchQueryChanged(intent.query)
        is StockClicked -> stockClicked(intent.symbol)
        is AddToPortfolio -> addToPortfolio(intent.symbol)
    }

    override fun reduceUiState(
        previousState: StockSearchUiState,
        partialState: PartialState,
    ): StockSearchUiState = when (partialState) {
        is Loading -> previousState.copy(isLoading = true, isError = false)
        is Fetched -> previousState.copy(
            isLoading = false,
            isError = false,
            stocks = partialState.list
        )
        is SearchQueryChangedState -> previousState.copy(
            searchQuery = partialState.query,
            stocks = emptyList(), // Clear previous results
            isError = false
        )
        is Error -> {
            Timber.e(partialState.throwable)
            previousState.copy(isLoading = false, isError = true)
        }
    }

    private fun refreshStocks(): Flow<PartialState> = flow {
        emit(Loading)
        delay(1000) // Simulate network call
        // For now, return empty list - we'll implement real API later
        emit(Fetched(emptyList()))
    }

    private fun searchQueryChanged(query: String): Flow<PartialState> = flow {
        emit(SearchQueryChangedState(query))
        
        if (query.length >= 2) { // Start searching after 2 characters
            emit(Loading)
            delay(500) // Debounce
            
            // Mock data for now - replace with real API call
            val mockStocks = createMockStocks(query)
            emit(Fetched(mockStocks))
        }
    }

    private fun stockClicked(symbol: String): Flow<PartialState> = flow {
        setEvent(ShowStockDetails(symbol))
    }

    private fun addToPortfolio(symbol: String): Flow<PartialState> = flow {
        // TODO: Implement actual portfolio addition
        setEvent(AddedToPortfolio(symbol))
        setEvent(ShowMessage("$symbol added to portfolio"))
    }

    // Mock data for demonstration
    private fun createMockStocks(query: String): List<StockDisplayable> {
        val mockData = mapOf(
            "AAPL" to StockDisplayable(
                symbol = "AAPL",
                name = "Apple Inc.",
                price = "$150.25",
                change = "+2.50",
                changePercent = "+1.67%",
                marketCapFormatted = "2.4T",
                volumeFormatted = "75.2M",
                exchange = "NASDAQ",
                isPositive = true
            ),
            "TSLA" to StockDisplayable(
                symbol = "TSLA",
                name = "Tesla, Inc.",
                price = "$245.80",
                change = "-5.20",
                changePercent = "-2.08%",
                marketCapFormatted = "780B",
                volumeFormatted = "45.8M",
                exchange = "NASDAQ",
                isPositive = false
            ),
            "MSFT" to StockDisplayable(
                symbol = "MSFT",
                name = "Microsoft Corporation",
                price = "$378.90",
                change = "+8.40",
                changePercent = "+2.27%",
                marketCapFormatted = "2.8T",
                volumeFormatted = "22.1M",
                exchange = "NASDAQ",
                isPositive = true
            ),
            "GOOGL" to StockDisplayable(
                symbol = "GOOGL",
                name = "Alphabet Inc.",
                price = "$142.15",
                change = "+1.85",
                changePercent = "+1.32%",
                marketCapFormatted = "1.8T",
                volumeFormatted = "18.5M",
                exchange = "NASDAQ",
                isPositive = true
            )
        )
        
        return mockData.entries
            .filter { it.key.contains(query.uppercase()) || it.value.name.contains(query, ignoreCase = true) }
            .map { it.value }
    }
}
