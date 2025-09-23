package com.huntercoles.fatline.basicfeature.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.fatline.basicfeature.domain.usecase.CalculatePayoutsUseCase
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
    private val timerPreferences: TimerPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    init {
        // Only reset if timer is not currently running (true app startup vs tab switch)
        if (!timerPreferences.getTimerRunning()) {
            resetAllData()
        }
        
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
    }
    
    private fun loadTournamentConfiguration() {
        val savedPlayerCount = tournamentPreferences.getPlayerCount()
        val savedBuyIn = tournamentPreferences.getBuyIn()
        val savedFood = tournamentPreferences.getFoodPerPlayer()
        val savedBounty = tournamentPreferences.getBountyPerPlayer()
        val savedWeights = tournamentPreferences.getPayoutWeights()
        
        val initialConfig = _uiState.value.tournamentConfig.copy(
            numPlayers = savedPlayerCount,
            buyIn = savedBuyIn,
            foodPerPlayer = savedFood,
            bountyPerPlayer = savedBounty,
            payoutWeights = savedWeights
        )
        _uiState.value = _uiState.value.copy(tournamentConfig = initialConfig)
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
        val newConfig = _uiState.value.tournamentConfig.copy(numPlayers = count)
        _uiState.value = _uiState.value.copy(tournamentConfig = newConfig)
        // Save to shared preferences
        tournamentPreferences.setPlayerCount(count)
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
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val payouts = calculatePayoutsUseCase(_uiState.value.tournamentConfig)
            
            _uiState.value = _uiState.value.copy(
                payouts = payouts,
                isLoading = false
            )
        }
    }
}