package com.huntercoles.fatline.basicfeature.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.fatline.basicfeature.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.fatline.core.preferences.BankPreferences
import com.huntercoles.fatline.core.preferences.TournamentPreferences
import com.huntercoles.fatline.core.preferences.TimerPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CalculatorViewModel @Inject constructor(
    private val calculatePayoutsUseCase: CalculatePayoutsUseCase,
    private val tournamentPreferences: TournamentPreferences,
    private val timerPreferences: TimerPreferences,
    private val bankPreferences: BankPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    init {
        // Load all tournament configuration from preferences
        loadTournamentConfiguration()
        
        // Listen for tournament lock state changes
        viewModelScope.launch {
            tournamentPreferences.tournamentLocked.collect { isLocked ->
                _uiState.value = _uiState.value.copy(
                    isTournamentLocked = isLocked,
                    // Auto-collapse when tournament is locked (timer started)
                    isConfigExpanded = if (isLocked) false else _uiState.value.isConfigExpanded
                )
            }
        }

        viewModelScope.launch {
            bankPreferences.eliminationOrder.collect { order ->
                refreshLeaderboard(order)
            }
        }

        viewModelScope.launch {
            bankPreferences.totalRebuys.collect { count ->
                updatePurchaseCounts(rebuyCount = count)
            }
        }

        viewModelScope.launch {
            bankPreferences.totalAddons.collect { count ->
                updatePurchaseCounts(addOnCount = count)
            }
        }
    }
    
    private fun loadTournamentConfiguration() {
        val savedPlayerCount = tournamentPreferences.getPlayerCount()
        val savedBuyIn = tournamentPreferences.getBuyIn()
        val savedFood = tournamentPreferences.getFoodPerPlayer()
        val savedBounty = tournamentPreferences.getBountyPerPlayer()
        val savedRebuy = tournamentPreferences.getRebuyAmount()
        val savedAddOn = tournamentPreferences.getAddOnAmount()
        val savedWeights = tournamentPreferences.getPayoutWeights()
        
        val initialConfig = _uiState.value.tournamentConfig.copy(
            numPlayers = savedPlayerCount,
            buyIn = savedBuyIn,
            foodPerPlayer = savedFood,
            bountyPerPlayer = savedBounty,
            payoutWeights = savedWeights
        )
        // include rebuy and add-on in the initial config
        val withRebuy = initialConfig.copy(rebuyPerPlayer = savedRebuy, addOnPerPlayer = savedAddOn)
        val initialRebuyCount = bankPreferences.getTotalRebuyCount()
        val initialAddOnCount = bankPreferences.getTotalAddonCount()
        _uiState.value = _uiState.value.copy(
            tournamentConfig = withRebuy,
            rebuyPurchases = initialRebuyCount,
            addOnPurchases = initialAddOnCount
        )
        calculatePayouts()
    }

    fun acceptIntent(intent: CalculatorIntent) {
        when (intent) {
            is CalculatorIntent.UpdatePlayerCount -> {
                // Don't allow changes if tournament is locked
                if (_uiState.value.isTournamentLocked) return
                updatePlayerCount(intent.count)
            }
            is CalculatorIntent.UpdateBuyIn -> {
                // Don't allow changes if tournament is locked
                if (_uiState.value.isTournamentLocked) return
                updateBuyIn(intent.buyIn)
            }
            is CalculatorIntent.UpdateFoodPerPlayer -> {
                // Don't allow changes if tournament is locked
                if (_uiState.value.isTournamentLocked) return
                updateFoodPerPlayer(intent.food)
            }
            is CalculatorIntent.UpdateBountyPerPlayer -> {
                // Don't allow changes if tournament is locked
                if (_uiState.value.isTournamentLocked) return
                updateBountyPerPlayer(intent.bounty)
            }
            is CalculatorIntent.UpdateRebuyAmount -> {
                // Don't allow changes if tournament is locked
                if (_uiState.value.isTournamentLocked) return
                updateRebuyPerPlayer(intent.rebuy)
            }
            is CalculatorIntent.UpdateAddOnAmount -> {
                if (_uiState.value.isTournamentLocked) return
                updateAddOnPerPlayer(intent.addOn)
            }
            is CalculatorIntent.UpdateWeights -> {
                // Don't allow changes if tournament is locked
                if (_uiState.value.isTournamentLocked) return
                updateWeights(intent.weights)
            }
            is CalculatorIntent.ToggleConfigExpanded -> {
                // Allow manual toggle even when locked
                toggleConfigExpanded(intent.isExpanded)
            }
            is CalculatorIntent.ShowResetDialog -> {
                showResetDialog()
            }
            is CalculatorIntent.HideResetDialog -> {
                hideResetDialog()
            }
            is CalculatorIntent.ConfirmReset -> {
                resetAllData()
                hideResetDialog()
            }
        }
    }

    private fun updatePlayerCount(count: Int) {
        tournamentPreferences.setPlayerCount(count)
        val updatedWeights = tournamentPreferences.getPayoutWeights()

        val newConfig = _uiState.value.tournamentConfig.copy(
            numPlayers = count,
            payoutWeights = updatedWeights
        )
        _uiState.value = _uiState.value.copy(tournamentConfig = newConfig)
        calculatePayouts()
    }

    private fun updateBuyIn(buyIn: Double) {
        val newConfig = _uiState.value.tournamentConfig.copy(buyIn = buyIn)
        _uiState.value = _uiState.value.copy(tournamentConfig = newConfig)
        // Save to shared preferences
        tournamentPreferences.setBuyIn(buyIn)
        calculatePayouts()
    }

    private fun updateFoodPerPlayer(food: Double) {
        val newConfig = _uiState.value.tournamentConfig.copy(foodPerPlayer = food)
        _uiState.value = _uiState.value.copy(tournamentConfig = newConfig)
        // Save to shared preferences
        tournamentPreferences.setFoodPerPlayer(food)
        calculatePayouts()
    }

    private fun updateBountyPerPlayer(bounty: Double) {
        val newConfig = _uiState.value.tournamentConfig.copy(bountyPerPlayer = bounty)
        _uiState.value = _uiState.value.copy(tournamentConfig = newConfig)
        // Save to shared preferences
        tournamentPreferences.setBountyPerPlayer(bounty)
        calculatePayouts()
    }

    private fun updateRebuyPerPlayer(rebuy: Double) {
        val newConfig = _uiState.value.tournamentConfig.copy(rebuyPerPlayer = rebuy)
        _uiState.value = _uiState.value.copy(tournamentConfig = newConfig)
        // Save to shared preferences
        tournamentPreferences.setRebuyAmount(rebuy)
        if (rebuy <= 0.0) {
            bankPreferences.clearAllRebuys()
        }
        calculatePayouts()
    }

    private fun updateAddOnPerPlayer(addOn: Double) {
        val newConfig = _uiState.value.tournamentConfig.copy(addOnPerPlayer = addOn)
        _uiState.value = _uiState.value.copy(tournamentConfig = newConfig)
        // Save to shared preferences
        tournamentPreferences.setAddOnAmount(addOn)
        if (addOn <= 0.0) {
            bankPreferences.clearAllAddons()
        }
        calculatePayouts()
    }

    private fun updateWeights(weights: List<Int>) {
        val newConfig = _uiState.value.tournamentConfig.copy(payoutWeights = weights)
        _uiState.value = _uiState.value.copy(tournamentConfig = newConfig)
        // Save to shared preferences
        tournamentPreferences.setPayoutWeights(weights)
        calculatePayouts()
    }

    private fun toggleConfigExpanded(isExpanded: Boolean) {
        _uiState.value = _uiState.value.copy(isConfigExpanded = isExpanded)
    }
    
    private fun showResetDialog() {
        // Only show dialog if not already in default state
        if (!isInDefaultState()) {
            _uiState.value = _uiState.value.copy(showResetDialog = true)
        }
    }
    
    private fun hideResetDialog() {
        _uiState.value = _uiState.value.copy(showResetDialog = false)
    }
    
    private fun isInDefaultState(): Boolean {
        return tournamentPreferences.isInDefaultState() && timerPreferences.isInDefaultState()
    }
    
    private fun resetAllData() {
        tournamentPreferences.resetAllTournamentData()
        timerPreferences.resetAllTimerData()
        loadTournamentConfiguration()
    }

    private fun calculatePayouts() {
        viewModelScope.launch {
            val currentState = _uiState.value
            _uiState.value = currentState.copy(isLoading = true)

            val stateForCalculation = _uiState.value
            val rebuyPool = stateForCalculation.tournamentConfig.rebuyPerPlayer * stateForCalculation.rebuyPurchases
            val addOnPool = stateForCalculation.tournamentConfig.addOnPerPlayer * stateForCalculation.addOnPurchases
            val prizePoolOverride = stateForCalculation.tournamentConfig.prizePool + rebuyPool + addOnPool
            val adjustedConfig = if (stateForCalculation.tournamentConfig.numPlayers > 0) {
                stateForCalculation.tournamentConfig.copy(
                    buyIn = prizePoolOverride / stateForCalculation.tournamentConfig.numPlayers
                )
            } else {
                stateForCalculation.tournamentConfig
            }

            val payouts = calculatePayoutsUseCase(adjustedConfig)

            _uiState.value = stateForCalculation.copy(
                payouts = payouts,
                isLoading = false
            )

            refreshLeaderboard()
        }
    }

    private fun updatePurchaseCounts(rebuyCount: Int? = null, addOnCount: Int? = null) {
        val currentState = _uiState.value
        val updatedState = currentState.copy(
            rebuyPurchases = rebuyCount ?: currentState.rebuyPurchases,
            addOnPurchases = addOnCount ?: currentState.addOnPurchases
        )
        if (updatedState != currentState) {
            _uiState.value = updatedState
            calculatePayouts()
        }
    }

    private fun refreshLeaderboard(eliminationOrderOverride: List<Int>? = null) {
        val payouts = _uiState.value.payouts
        if (payouts.isEmpty()) {
            if (_uiState.value.leaderboardNames.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(leaderboardNames = emptyMap())
            }
            return
        }

        val numPlayers = _uiState.value.tournamentConfig.numPlayers
        if (numPlayers <= 0) {
            _uiState.value = _uiState.value.copy(leaderboardNames = emptyMap())
            return
        }

        val eliminationOrder = (eliminationOrderOverride ?: bankPreferences.getEliminationOrder())
            .filter { it in 1..numPlayers }
            .distinct()

        val leaderboard = mutableMapOf<Int, String>()
        val eliminationSet = eliminationOrder.toSet()

        payouts.forEach { payout ->
            val playerId = determinePlayerForPosition(payout.position, numPlayers, eliminationOrder, eliminationSet)
            if (playerId != null) {
                val name = bankPreferences.getPlayerName(playerId).takeIf { it.isNotBlank() }
                if (name != null) {
                    leaderboard[payout.position] = name
                }
            }
        }

        _uiState.value = _uiState.value.copy(leaderboardNames = leaderboard)
    }

    private fun determinePlayerForPosition(
        position: Int,
        numPlayers: Int,
        eliminationOrder: List<Int>,
        eliminationSet: Set<Int>
    ): Int? {
        if (position < 1 || position > numPlayers) return null

        return if (position == 1) {
            when {
                eliminationOrder.size >= numPlayers -> eliminationOrder.lastOrNull()
                numPlayers - eliminationOrder.size == 1 -> {
                    (1..numPlayers).firstOrNull { it !in eliminationSet }
                }
                else -> null
            }
        } else {
            val eliminationIndex = numPlayers - position
            if (eliminationIndex in eliminationOrder.indices) eliminationOrder[eliminationIndex] else null
        }
    }
}