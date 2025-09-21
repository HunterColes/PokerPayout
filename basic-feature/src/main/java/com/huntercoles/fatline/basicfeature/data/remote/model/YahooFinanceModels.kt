package com.huntercoles.fatline.basicfeature.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class YahooFinanceResponse(
    @SerialName("quoteResponse")
    val quoteResponse: QuoteResponse
)

@Serializable
data class QuoteResponse(
    @SerialName("result")
    val result: List<YahooStock>?,
    @SerialName("error")
    val error: String?
)

@Serializable
data class YahooStock(
    @SerialName("symbol")
    val symbol: String,
    @SerialName("shortName")
    val shortName: String?,
    @SerialName("longName") 
    val longName: String?,
    @SerialName("regularMarketPrice")
    val regularMarketPrice: Double?,
    @SerialName("regularMarketChange")
    val regularMarketChange: Double?,
    @SerialName("regularMarketChangePercent")
    val regularMarketChangePercent: Double?,
    @SerialName("marketCap")
    val marketCap: Long?,
    @SerialName("regularMarketVolume")
    val regularMarketVolume: Long?,
    @SerialName("fullExchangeName")
    val fullExchangeName: String?,
    @SerialName("currency")
    val currency: String?
)

// For search functionality
@Serializable
data class YahooSearchResponse(
    @SerialName("quotes")
    val quotes: List<YahooSearchResult>
)

@Serializable
data class YahooSearchResult(
    @SerialName("symbol")
    val symbol: String,
    @SerialName("shortname")
    val shortName: String?,
    @SerialName("longname")
    val longName: String?,
    @SerialName("exchange")
    val exchange: String?,
    @SerialName("quoteType")
    val quoteType: String?
)
