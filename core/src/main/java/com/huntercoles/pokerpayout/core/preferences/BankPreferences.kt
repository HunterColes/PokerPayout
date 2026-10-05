package com.huntercoles.pokerpayout.core.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class BankPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("bank_prefs", Context.MODE_PRIVATE)
    private val _eliminationOrder = MutableStateFlow(readEliminationOrderFromPrefs())
    val eliminationOrder: Flow<List<Int>> = _eliminationOrder.asStateFlow()
    private val _totalRebuys = MutableStateFlow(calculateTotalRebuys())
    val totalRebuys: Flow<Int> = _totalRebuys.asStateFlow()
    private val _totalAddons = MutableStateFlow(calculateTotalAddons())
    val totalAddons: Flow<Int> = _totalAddons.asStateFlow()
    private val _revision = MutableStateFlow(0L)

    /** Bumped by every write, so screens that only read the Bank (the Tournament tab) can refresh. */
    val revision: StateFlow<Long> = _revision.asStateFlow()
    
    fun savePlayerName(playerId: Int, name: String) {
        prefs.edit().putString("$PLAYER_NAME_PREFIX$playerId", name).apply()
        changed()
    }
    
    fun getPlayerName(playerId: Int): String {
        return prefs.getString("$PLAYER_NAME_PREFIX$playerId", "Player $playerId") ?: "Player $playerId"
    }
    
    fun savePlayerBuyInStatus(playerId: Int, buyIn: Boolean) {
        prefs.edit().putBoolean("player_buyin_$playerId", buyIn).apply()
        changed()
    }
    
    fun getPlayerBuyInStatus(playerId: Int): Boolean {
        return prefs.getBoolean("player_buyin_$playerId", false)
    }
    
    fun savePlayerOutStatus(playerId: Int, out: Boolean) {
        prefs.edit().putBoolean("player_out_$playerId", out).apply()
        changed()
    }
    
    fun getPlayerOutStatus(playerId: Int): Boolean {
        return prefs.getBoolean("player_out_$playerId", false)
    }
    
    fun savePlayerPayedOutStatus(playerId: Int, payedOut: Boolean) {
        prefs.edit().putBoolean("player_payedout_$playerId", payedOut).apply()
        changed()
    }
    
    fun getPlayerPayedOutStatus(playerId: Int): Boolean {
        return prefs.getBoolean("player_payedout_$playerId", false)
    }

    fun savePlayerEliminatedBy(playerId: Int, eliminatedBy: Int?) {
        val editor = prefs.edit()
        if (eliminatedBy == null) {
            editor.remove("$PLAYER_ELIMINATED_BY_PREFIX$playerId")
        } else {
            editor.putInt("$PLAYER_ELIMINATED_BY_PREFIX$playerId", eliminatedBy)
        }
        editor.apply()
        changed()
    }

    fun getPlayerEliminatedBy(playerId: Int): Int? {
        if (!prefs.contains("$PLAYER_ELIMINATED_BY_PREFIX$playerId")) return null
        return prefs.getInt("$PLAYER_ELIMINATED_BY_PREFIX$playerId", -1)
            .takeIf { it > 0 }
    }
    
    fun savePlayerRebuys(playerId: Int, rebuys: Int) {
        prefs.edit().putInt("$PLAYER_REBUYS_PREFIX$playerId", rebuys).apply()
        changed()
    }
    
    fun getPlayerRebuys(playerId: Int): Int {
        return prefs.getInt("$PLAYER_REBUYS_PREFIX$playerId", 0)
    }
    
    fun savePlayerAddons(playerId: Int, addons: Int) {
        prefs.edit().putInt("$PLAYER_ADDONS_PREFIX$playerId", addons).apply()
        changed()
    }
    
    fun getPlayerAddons(playerId: Int): Int {
        return prefs.getInt("$PLAYER_ADDONS_PREFIX$playerId", 0)
    }

    fun getTotalRebuyCount(): Int = _totalRebuys.value

    fun getTotalAddonCount(): Int = _totalAddons.value

    fun clearAllRebuys() {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(PLAYER_REBUYS_PREFIX) }
            .forEach { editor.remove(it) }
        editor.apply()
        changed()
    }

    fun clearAllAddons() {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(PLAYER_ADDONS_PREFIX) }
            .forEach { editor.remove(it) }
        editor.apply()
        changed()
    }

    fun clearAllEliminatedBy() {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(PLAYER_ELIMINATED_BY_PREFIX) }
            .forEach { editor.remove(it) }
        editor.apply()
        changed()
    }

    fun getEliminationOrder(): List<Int> = _eliminationOrder.value

    fun saveEliminationOrder(order: List<Int>) {
        val sanitized = order
            .filter { it > 0 }
            .distinct()
        prefs.edit()
            .putString(ELIMINATION_ORDER_KEY, sanitized.joinToString(","))
            .apply()
        _eliminationOrder.value = sanitized
        changed()
    }

    /**
     * Forget everything about players numbered above [playerCount]: names, statuses, purchases and
     * knockouts, including knockouts credited to them. The Bank shows removed players as gone, so
     * their data must not come back when the count grows again or after a restart.
     */
    fun removePlayersAbove(playerCount: Int) {
        val stored = prefs.all
        val editor = prefs.edit()
        var removedAny = false
        stored.forEach { (key, value) ->
            val playerId = key.takeIf { it.startsWith(PLAYER_PREFIX) }?.substringAfterLast('_')?.toIntOrNull()
            val creditsRemovedPlayer = key.startsWith(PLAYER_ELIMINATED_BY_PREFIX) && (value as? Int ?: 0) > playerCount
            if ((playerId != null && playerId > playerCount) || creditsRemovedPlayer) {
                editor.remove(key)
                removedAny = true
            }
        }
        editor.apply()
        val order = getEliminationOrder()
        val keptOrder = order.filter { it <= playerCount }
        if (keptOrder != order) {
            saveEliminationOrder(keptOrder)
        } else if (removedAny) {
            changed()
        }
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
            
            // Check if any boxes are checked or any rebuys/addons exist
            val hasStatusChange = getPlayerBuyInStatus(playerId) || 
                getPlayerOutStatus(playerId) || 
                getPlayerPayedOutStatus(playerId)
            val hasRebuyAddon = getPlayerRebuys(playerId) > 0 || getPlayerAddons(playerId) > 0
            val hasEliminationAssignment = getPlayerEliminatedBy(playerId) != null
            
            if (hasStatusChange || hasRebuyAddon || hasEliminationAssignment) {
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
        editor.remove(ELIMINATION_ORDER_KEY)
        editor.apply()
        _eliminationOrder.value = emptyList()
        clearAllEliminatedBy()
    }

    private fun changed() {
        _totalRebuys.value = calculateTotalRebuys()
        _totalAddons.value = calculateTotalAddons()
        _revision.value = _revision.value + 1
    }

    private fun readEliminationOrderFromPrefs(): List<Int> {
        val stored = prefs.getString(ELIMINATION_ORDER_KEY, null)
        if (stored.isNullOrBlank()) return emptyList()
        return stored.split(",")
            .mapNotNull { it.toIntOrNull() }
            .filter { it > 0 }
    }

    private fun calculateTotalRebuys(): Int {
        return prefs.all.entries
            .filter { it.key.startsWith(PLAYER_REBUYS_PREFIX) }
            .sumOf { (it.value as? Number)?.toInt() ?: 0 }
    }

    private fun calculateTotalAddons(): Int {
        return prefs.all.entries
            .filter { it.key.startsWith(PLAYER_ADDONS_PREFIX) }
            .sumOf { (it.value as? Number)?.toInt() ?: 0 }
    }

    companion object {
        private const val ELIMINATION_ORDER_KEY = "elimination_order"
        private const val PLAYER_PREFIX = "player_"
        private const val PLAYER_NAME_PREFIX = "player_name_"
        private const val PLAYER_REBUYS_PREFIX = "player_rebuys_"
        private const val PLAYER_ADDONS_PREFIX = "player_addons_"
        private const val PLAYER_ELIMINATED_BY_PREFIX = "player_eliminated_by_"
    }
}
