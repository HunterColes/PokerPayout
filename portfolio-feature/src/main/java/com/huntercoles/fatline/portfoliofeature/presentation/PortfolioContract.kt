package com.huntercoles.fatline.portfoliofeature.presentation

sealed class PortfolioIntent {
    data object RefreshPortfolio : PortfolioIntent()
    data class StockClicked(val symbol: String) : PortfolioIntent()
}

sealed class PortfolioEvent {
    data class NavigateToStockDetails(val symbol: String) : PortfolioEvent()
}
