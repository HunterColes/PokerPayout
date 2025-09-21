package com.huntercoles.fatline.basicfeature.presentation

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import com.huntercoles.fatline.basicfeature.presentation.model.StockDisplayable
import kotlinx.parcelize.Parcelize

@Immutable
@Parcelize
data class StockSearchUiState(
    val isLoading: Boolean = false,
    val stocks: List<StockDisplayable> = emptyList(),
    val searchQuery: String = "",
    val isError: Boolean = false,
) : Parcelable {

    sealed class PartialState {
        data object Loading : PartialState() // for simplicity: initial loading & refreshing

        data class Fetched(val list: List<StockDisplayable>) : PartialState()

        data class SearchQueryChanged(val query: String) : PartialState()

        data class Error(val throwable: Throwable) : PartialState()
    }
}
