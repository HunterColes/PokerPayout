package com.huntercoles.pokerpayout.core.preferences

import android.content.Context
import android.content.SharedPreferences
import com.huntercoles.pokerpayout.core.constants.TournamentConstants
import com.huntercoles.pokerpayout.core.constants.TournamentDefaults
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class TournamentPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tournament_prefs", Context.MODE_PRIVATE)
    
    private val _playerCount = MutableStateFlow(getPlayerCount())
    val playerCount: Flow<Int> = _playerCount.asStateFlow()
    
    private val _tournamentLocked = MutableStateFlow(getTournamentLocked())
    val tournamentLocked: Flow<Boolean> = _tournamentLocked.asStateFlow()
    
    private val _buyIn = MutableStateFlow(getBuyIn())
    val buyIn: Flow<Double> = _buyIn.asStateFlow()
    
    private val _foodPerPlayer = MutableStateFlow(getFoodPerPlayer())
    val foodPerPlayer: Flow<Double> = _foodPerPlayer.asStateFlow()
    
    private val _bountyPerPlayer = MutableStateFlow(getBountyPerPlayer())
    val bountyPerPlayer: Flow<Double> = _bountyPerPlayer.asStateFlow()

    private val _rebuyPerPlayer = MutableStateFlow(getRebuyAmount())
    val rebuyPerPlayer: Flow<Double> = _rebuyPerPlayer.asStateFlow()
    
    private val _addOnPerPlayer = MutableStateFlow(getAddOnAmount())
    val addOnPerPlayer: Flow<Double> = _addOnPerPlayer.asStateFlow()
    
    private val _payoutWeights = MutableStateFlow(getPayoutWeights())
    val payoutWeights: Flow<List<Int>> = _payoutWeights.asStateFlow()
    
    fun setPlayerCount(count: Int) {
        val oldCount = getPlayerCount()
        prefs.edit().putInt(PLAYER_COUNT_KEY, count).apply()
        _playerCount.value = count
        
        // If weights haven't been set before, set defaults
        if (!prefs.contains(PAYOUT_WEIGHTS_KEY)) {
            _payoutWeights.value = defaultPayoutWeightsFor(count)
        } else {
            // If current weights match the default for the old count, update to new defaults
            val currentWeights = getPayoutWeights()
            val oldDefaults = defaultPayoutWeightsFor(oldCount)
            if (currentWeights == oldDefaults) {
                val newDefaults = defaultPayoutWeightsFor(count)
                setPayoutWeights(newDefaults)
            }
        }
    }
    
    fun getPlayerCount(): Int {
        return prefs.getInt(PLAYER_COUNT_KEY, DEFAULT_PLAYER_COUNT)
    }
    
    fun setTournamentLocked(locked: Boolean) {
        prefs.edit().putBoolean(TOURNAMENT_LOCKED_KEY, locked).apply()
        _tournamentLocked.value = locked
    }
    
    fun getTournamentLocked(): Boolean {
        return prefs.getBoolean(TOURNAMENT_LOCKED_KEY, false)
    }
    
    fun setBuyIn(buyIn: Double) {
        prefs.edit().putFloat(BUY_IN_KEY, buyIn.toFloat()).apply()
        _buyIn.value = buyIn
    }
    
    fun getBuyIn(): Double {
        return prefs.getFloat(BUY_IN_KEY, TournamentDefaults.BUY_IN.toFloat()).toDouble()
    }
    
    fun setFoodPerPlayer(food: Double) {
        prefs.edit().putFloat(FOOD_PER_PLAYER_KEY, food.toFloat()).apply()
        _foodPerPlayer.value = food
    }
    
    fun getFoodPerPlayer(): Double {
        return prefs.getFloat(FOOD_PER_PLAYER_KEY, TournamentDefaults.FOOD_PER_PLAYER.toFloat()).toDouble()
    }
    
    fun setBountyPerPlayer(bounty: Double) {
        prefs.edit().putFloat(BOUNTY_PER_PLAYER_KEY, bounty.toFloat()).apply()
        _bountyPerPlayer.value = bounty
    }
    
    fun getBountyPerPlayer(): Double {
        return prefs.getFloat(BOUNTY_PER_PLAYER_KEY, TournamentDefaults.BOUNTY_PER_PLAYER.toFloat()).toDouble()
    }

    fun setRebuyAmount(rebuy: Double) {
        prefs.edit().putFloat(REBUY_PER_PLAYER_KEY, rebuy.toFloat()).apply()
        _rebuyPerPlayer.value = rebuy
    }

    fun getRebuyAmount(): Double {
        return prefs.getFloat(REBUY_PER_PLAYER_KEY, TournamentDefaults.REBUY_PER_PLAYER.toFloat()).toDouble()
    }

    fun setAddOnAmount(addOn: Double) {
        prefs.edit().putFloat(ADDON_PER_PLAYER_KEY, addOn.toFloat()).apply()
        _addOnPerPlayer.value = addOn
    }

    fun getAddOnAmount(): Double {
        return prefs.getFloat(ADDON_PER_PLAYER_KEY, TournamentDefaults.ADDON_PER_PLAYER.toFloat()).toDouble()
    }
    
    fun setPayoutWeights(weights: List<Int>) {
        val weightsString = weights.joinToString(",")
        prefs.edit().putString(PAYOUT_WEIGHTS_KEY, weightsString).apply()
        _payoutWeights.value = weights
    }
    
    fun getPayoutWeights(): List<Int> {
        val weightsString = prefs.getString(PAYOUT_WEIGHTS_KEY, null)
        val parsedWeights = weightsString
            ?.split(",")
            ?.mapNotNull { it.toIntOrNull() }
            ?.filter { it > 0 }
            ?.takeIf { it.isNotEmpty() }

        return parsedWeights ?: defaultPayoutWeightsFor()
    }
    
    // Blind Configuration Persistence
    fun setGameDurationHours(hours: Int) {
        prefs.edit().putInt(GAME_DURATION_HOURS_KEY, hours).apply()
    }
    
    fun getGameDurationHours(): Int {
        return prefs.getInt(GAME_DURATION_HOURS_KEY, TournamentDefaults.GAME_DURATION_HOURS)
    }
    
    fun setRoundLengthMinutes(minutes: Int) {
        prefs.edit().putInt(ROUND_LENGTH_MINUTES_KEY, minutes).apply()
    }
    
    fun getRoundLengthMinutes(): Int {
        return prefs.getInt(ROUND_LENGTH_MINUTES_KEY, TournamentDefaults.ROUND_LENGTH_MINUTES)
    }
    
    fun setSmallestChip(chip: Int) {
        prefs.edit().putInt(SMALLEST_CHIP_KEY, chip).apply()
    }
    
    fun getSmallestChip(): Int {
        return prefs.getInt(SMALLEST_CHIP_KEY, TournamentDefaults.SMALLEST_CHIP)
    }
    
    fun setStartingChips(chips: Int) {
        prefs.edit().putInt(STARTING_CHIPS_KEY, chips).apply()
    }
    
    fun getStartingChips(): Int {
        return prefs.getInt(STARTING_CHIPS_KEY, TournamentDefaults.STARTING_CHIPS)
    }
    
    /**
     * Get complete tournament configuration as a simple data holder
     * This can be used by other modules that need access to tournament settings
     */
    data class TournamentConfigData(
        val numPlayers: Int,
        val buyIn: Double,
        val foodPerPlayer: Double,
        val bountyPerPlayer: Double,
        val rebuyPerPlayer: Double,
        val addOnPerPlayer: Double,
        val payoutWeights: List<Int>
    ) {
    val totalPerPlayer: Double get() = buyIn + foodPerPlayer + bountyPerPlayer + addOnPerPlayer
        val prizePool: Double get() = numPlayers * buyIn
        val foodPool: Double get() = numPlayers * foodPerPlayer
        val bountyPool: Double get() = numPlayers * bountyPerPlayer
    val addOnPool: Double get() = numPlayers * addOnPerPlayer
    val totalPool: Double get() = prizePool + foodPool + bountyPool + addOnPool
    }
    
    fun getCurrentTournamentConfig(): TournamentConfigData {
        return TournamentConfigData(
            numPlayers = getPlayerCount(),
            buyIn = getBuyIn(),
            foodPerPlayer = getFoodPerPlayer(),
            bountyPerPlayer = getBountyPerPlayer(),
            rebuyPerPlayer = getRebuyAmount(),
            addOnPerPlayer = getAddOnAmount(),
            payoutWeights = getPayoutWeights()
        )
    }
    
    /**
     * Check if tournament settings are in default state
     */
    fun isInDefaultState(): Boolean {
        val playerCount = getPlayerCount()
         return playerCount == DEFAULT_PLAYER_COUNT &&
         getBuyIn() == TournamentDefaults.BUY_IN &&
         getFoodPerPlayer() == TournamentDefaults.FOOD_PER_PLAYER &&
         getBountyPerPlayer() == TournamentDefaults.BOUNTY_PER_PLAYER &&
         getRebuyAmount() == TournamentDefaults.REBUY_PER_PLAYER &&
         getAddOnAmount() == TournamentDefaults.ADDON_PER_PLAYER &&
         getPayoutWeights() == defaultPayoutWeightsFor(playerCount) &&
         !getTournamentLocked() &&
         getGameDurationHours() == TournamentDefaults.GAME_DURATION_HOURS &&
         getRoundLengthMinutes() == TournamentDefaults.ROUND_LENGTH_MINUTES &&
         getSmallestChip() == TournamentDefaults.SMALLEST_CHIP &&
         getStartingChips() == TournamentDefaults.STARTING_CHIPS
    }    /**
     * Reset all tournament data to default values
     */
    fun resetAllTournamentData() {
        // Reset specific keys instead of clearing all preferences
        prefs.edit()
            .putBoolean(TOURNAMENT_LOCKED_KEY, false)
            .putInt(PLAYER_COUNT_KEY, DEFAULT_PLAYER_COUNT)
            .putFloat(BUY_IN_KEY, TournamentDefaults.BUY_IN.toFloat())
            .putFloat(FOOD_PER_PLAYER_KEY, TournamentDefaults.FOOD_PER_PLAYER.toFloat())
            .putFloat(BOUNTY_PER_PLAYER_KEY, TournamentDefaults.BOUNTY_PER_PLAYER.toFloat())
            .putFloat(REBUY_PER_PLAYER_KEY, TournamentDefaults.REBUY_PER_PLAYER.toFloat())
            .putFloat(ADDON_PER_PLAYER_KEY, TournamentDefaults.ADDON_PER_PLAYER.toFloat())
            .remove(PAYOUT_WEIGHTS_KEY)
            .putInt(GAME_DURATION_HOURS_KEY, TournamentDefaults.GAME_DURATION_HOURS)
            .putInt(ROUND_LENGTH_MINUTES_KEY, TournamentDefaults.ROUND_LENGTH_MINUTES)
            .putInt(SMALLEST_CHIP_KEY, TournamentDefaults.SMALLEST_CHIP)
            .putInt(STARTING_CHIPS_KEY, TournamentDefaults.STARTING_CHIPS)
            .apply()
        
        // Reset all state flows to default values (keep current player count)
        _tournamentLocked.value = false
        _playerCount.value = DEFAULT_PLAYER_COUNT
        _buyIn.value = TournamentDefaults.BUY_IN
        _foodPerPlayer.value = TournamentDefaults.FOOD_PER_PLAYER
        _bountyPerPlayer.value = TournamentDefaults.BOUNTY_PER_PLAYER
        _rebuyPerPlayer.value = TournamentDefaults.REBUY_PER_PLAYER
        _addOnPerPlayer.value = TournamentDefaults.ADDON_PER_PLAYER
        _payoutWeights.value = defaultPayoutWeightsFor(DEFAULT_PLAYER_COUNT)
    }

    private fun defaultPayoutWeightsFor(playerCount: Int = getPlayerCount()): List<Int> {
        val defaultCount = max(1, playerCount / 3)
        return TournamentConstants.DEFAULT_PAYOUT_WEIGHTS.take(defaultCount)
    }
    
    companion object {
        private const val PLAYER_COUNT_KEY = "player_count"
        private const val TOURNAMENT_LOCKED_KEY = "tournament_locked"
        private const val BUY_IN_KEY = "buy_in"
        private const val FOOD_PER_PLAYER_KEY = "food_per_player"
        private const val BOUNTY_PER_PLAYER_KEY = "bounty_per_player"
        private const val REBUY_PER_PLAYER_KEY = "rebuy_per_player"
        private const val ADDON_PER_PLAYER_KEY = "addon_per_player"
        private const val PAYOUT_WEIGHTS_KEY = "payout_weights"
        private const val GAME_DURATION_HOURS_KEY = "game_duration_hours"
        private const val ROUND_LENGTH_MINUTES_KEY = "round_length_minutes"
        private const val SMALLEST_CHIP_KEY = "smallest_chip"
        private const val STARTING_CHIPS_KEY = "starting_chips"
        private const val DEFAULT_PLAYER_COUNT = TournamentDefaults.PLAYER_COUNT
    }
}