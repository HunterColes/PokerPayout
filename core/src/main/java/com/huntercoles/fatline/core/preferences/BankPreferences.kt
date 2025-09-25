package com.huntercoles.fatline.core.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BankPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("bank_prefs", Context.MODE_PRIVATE)
    
    fun savePlayerName(playerId: Int, name: String) {
        prefs.edit().putString("player_name_$playerId", name).apply()
    }
    
    fun getPlayerName(playerId: Int): String {
        return prefs.getString("player_name_$playerId", "Player $playerId") ?: "Player $playerId"
    }
    
    fun savePlayerBuyInStatus(playerId: Int, buyIn: Boolean) {
        prefs.edit().putBoolean("player_buyin_$playerId", buyIn).apply()
    }
    
    fun getPlayerBuyInStatus(playerId: Int): Boolean {
        return prefs.getBoolean("player_buyin_$playerId", false)
    }
    
    fun savePlayerOutStatus(playerId: Int, out: Boolean) {
        prefs.edit().putBoolean("player_out_$playerId", out).apply()
    }
    
    fun getPlayerOutStatus(playerId: Int): Boolean {
        return prefs.getBoolean("player_out_$playerId", false)
    }
    
    fun savePlayerPayedOutStatus(playerId: Int, payedOut: Boolean) {
        prefs.edit().putBoolean("player_payedout_$playerId", payedOut).apply()
    }
    
    fun getPlayerPayedOutStatus(playerId: Int): Boolean {
        return prefs.getBoolean("player_payedout_$playerId", false)
    }
    
    /**
     * Check if bank data is in default state (all default names, no boxes checked)
     */
    fun isInDefaultState(playerCount: Int): Boolean {
        for (playerId in 1..playerCount) {
            // Check if name was changed from default
            val savedName = getPlayerName(playerId)
            if (savedName != "Player $playerId") {
                return false
            }
            
            // Check if any boxes are checked
            if (getPlayerBuyInStatus(playerId) || 
                getPlayerOutStatus(playerId) || 
                getPlayerPayedOutStatus(playerId)) {
                return false
            }
        }
        return true
    }
    
    /**
     * Reset all bank data to default values
     */
    fun resetAllBankData() {
        // Clear all bank preferences
        val editor = prefs.edit()
        prefs.all.keys.forEach { key ->
            if (key.startsWith("player_")) {
                editor.remove(key)
            }
        }
        editor.apply()
    }
}