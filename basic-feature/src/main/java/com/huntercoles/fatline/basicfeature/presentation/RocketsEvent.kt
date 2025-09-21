package com.huntercoles.fatline.basicfeature.presentation

sealed class StockSearchEvent {
    data class ShowStockDetails(val symbol: String) : StockSearchEvent()
    
    data class ShowMessage(val message: String) : StockSearchEvent()
    
    data class AddedToPortfolio(val symbol: String) : StockSearchEvent()
}
