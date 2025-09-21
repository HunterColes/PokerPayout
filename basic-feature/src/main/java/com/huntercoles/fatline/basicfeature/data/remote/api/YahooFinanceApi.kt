package com.huntercoles.fatline.basicfeature.data.remote.api

import com.huntercoles.fatline.basicfeature.data.remote.model.YahooFinanceResponse
import com.huntercoles.fatline.basicfeature.data.remote.model.YahooSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface YahooFinanceApi {
    
    @GET("v7/finance/quote")
    suspend fun getQuote(
        @Query("symbols") symbols: String
    ): YahooFinanceResponse
    
    @GET("v1/finance/search")
    suspend fun searchStocks(
        @Query("q") query: String,
        @Query("lang") lang: String = "en",
        @Query("region") region: String = "US",
        @Query("quotesCount") quotesCount: Int = 10,
        @Query("newsCount") newsCount: Int = 0
    ): YahooSearchResponse
    
    companion object {
        const val BASE_URL = "https://query1.finance.yahoo.com/"
    }
}
