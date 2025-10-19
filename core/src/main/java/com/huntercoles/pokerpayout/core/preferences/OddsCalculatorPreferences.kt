package com.huntercoles.pokerpayout.core.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OddsCalculatorPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("odds_calculator_prefs", Context.MODE_PRIVATE)

    fun getPlayerCount(): Int {
        return prefs.getInt(PLAYER_COUNT_KEY, DEFAULT_PLAYER_COUNT)
    }

    fun setPlayerCount(count: Int) {
        prefs.edit().putInt(PLAYER_COUNT_KEY, count).apply()
    }

    fun getPlayerCards(playerId: Int): String {
        return prefs.getString("$PLAYER_CARDS_PREFIX$playerId", "") ?: ""
    }

    fun setPlayerCards(playerId: Int, cards: String) {
        prefs.edit().putString("$PLAYER_CARDS_PREFIX$playerId", cards).apply()
    }

    fun getCommunityCards(): String {
        return prefs.getString(COMMUNITY_CARDS_KEY, "") ?: ""
    }

    fun setCommunityCards(cards: String) {
        prefs.edit().putString(COMMUNITY_CARDS_KEY, cards).apply()
    }

    fun resetAllData() {
        prefs.edit().clear().apply()
    }

    fun isInDefaultState(): Boolean {
        val playerCount = getPlayerCount()
        if (playerCount != DEFAULT_PLAYER_COUNT) return false
        
        // Check if any player has cards
        for (i in 1..10) {
            if (getPlayerCards(i).isNotEmpty()) return false
        }
        
        // Check if community cards exist
        if (getCommunityCards().isNotEmpty()) return false
        
        return true
    }

    companion object {
        private const val PLAYER_COUNT_KEY = "player_count"
        private const val PLAYER_CARDS_PREFIX = "player_cards_"
        private const val COMMUNITY_CARDS_KEY = "community_cards"
        private const val DEFAULT_PLAYER_COUNT = 2
    }
}
