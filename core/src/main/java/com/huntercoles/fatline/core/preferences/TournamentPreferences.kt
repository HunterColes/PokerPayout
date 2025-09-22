package com.huntercoles.fatline.core.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TournamentPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tournament_prefs", Context.MODE_PRIVATE)
    
    private val _playerCount = MutableStateFlow(getPlayerCount())
    val playerCount: Flow<Int> = _playerCount.asStateFlow()
    
    fun setPlayerCount(count: Int) {
        prefs.edit().putInt(PLAYER_COUNT_KEY, count).apply()
        _playerCount.value = count
    }
    
    fun getPlayerCount(): Int {
        return prefs.getInt(PLAYER_COUNT_KEY, 9) // Default to 9 players
    }
    
    companion object {
        private const val PLAYER_COUNT_KEY = "player_count"
    }
}