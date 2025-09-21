package com.huntercoles.fatline.basicfeature.domain.model

data class Stock(
    val symbol: String,
    val name: String,
    val price: Double,
    val change: Double,
    val changePercent: Double,
    val marketCap: Long?,
    val volume: Long?,
    val exchange: String,
    val currency: String = "USD",
)

data class StockSearchResult(
    val symbol: String,
    val name: String,
    val exchange: String,
    val type: String, // "Stock", "ETF", etc.
)
