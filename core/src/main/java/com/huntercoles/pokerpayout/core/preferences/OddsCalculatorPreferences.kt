package com.huntercoles.pokerpayout.core.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The Odds table as it was left (seats, their cards and folds, the board) and the four-colour deck. */
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

    /** Whether seat [playerId] (1-based) folded; its cards are dead. */
    fun getPlayerFolded(playerId: Int): Boolean = prefs.getBoolean("$PLAYER_FOLDED_PREFIX$playerId", false)

    fun setPlayerFolded(playerId: Int, folded: Boolean) {
        prefs.edit().putBoolean("$PLAYER_FOLDED_PREFIX$playerId", folded).apply()
    }

    /** Cards drawn with blue diamonds and green clubs (a display preference). */
    var fourColourDeck: Boolean
        get() = prefs.getBoolean(FOUR_COLOUR_DECK_KEY, false)
        set(enabled) = prefs.edit().putBoolean(FOUR_COLOUR_DECK_KEY, enabled).apply()

    companion object {
        private const val PLAYER_COUNT_KEY = "player_count"
        private const val PLAYER_CARDS_PREFIX = "player_cards_"
        private const val COMMUNITY_CARDS_KEY = "community_cards"
        private const val PLAYER_FOLDED_PREFIX = "player_folded_"
        private const val FOUR_COLOUR_DECK_KEY = "four_colour_deck"
        private const val DEFAULT_PLAYER_COUNT = 2
    }
}
