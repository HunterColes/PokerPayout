package com.huntercoles.fatline.basicfeature.presentation

sealed class StockSearchIntent {
    data object RefreshStocks : StockSearchIntent()
    
    data class SearchQueryChanged(val query: String) : StockSearchIntent()
    
    data class StockClicked(val symbol: String) : StockSearchIntent()
    
    data class AddToPortfolio(val symbol: String) : StockSearchIntent()
}
