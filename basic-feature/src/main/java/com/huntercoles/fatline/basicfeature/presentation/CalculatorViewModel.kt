package com.huntercoles.fatline.basicfeature.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.fatline.basicfeature.domain.usecase.CalculatePayoutsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CalculatorViewModel @Inject constructor(
    private val calculatePayoutsUseCase: CalculatePayoutsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    init {
        calculatePayouts()
    }

    fun acceptIntent(intent: CalculatorIntent) {
        when (intent) {
            is CalculatorIntent.UpdatePlayerCount -> updatePlayerCount(intent.count)
            is CalculatorIntent.UpdateBuyIn -> updateBuyIn(intent.buyIn)
            is CalculatorIntent.UpdateFoodPerPlayer -> updateFoodPerPlayer(intent.food)
            is CalculatorIntent.UpdateBountyPerPlayer -> updateBountyPerPlayer(intent.bounty)
            is CalculatorIntent.UpdateWeights -> updateWeights(intent.weights)
        }
    }

    private fun updatePlayerCount(count: Int) {
        val newConfig = _uiState.value.tournamentConfig.copy(numPlayers = count)
        _uiState.value = _uiState.value.copy(tournamentConfig = newConfig)
        calculatePayouts()
    }

    private fun updateBuyIn(buyIn: Double) {
        val newConfig = _uiState.value.tournamentConfig.copy(buyIn = buyIn)
        _uiState.value = _uiState.value.copy(tournamentConfig = newConfig)
        calculatePayouts()
    }

    private fun updateFoodPerPlayer(food: Double) {
        val newConfig = _uiState.value.tournamentConfig.copy(foodPerPlayer = food)
        _uiState.value = _uiState.value.copy(tournamentConfig = newConfig)
        calculatePayouts()
    }

    private fun updateBountyPerPlayer(bounty: Double) {
        val newConfig = _uiState.value.tournamentConfig.copy(bountyPerPlayer = bounty)
        _uiState.value = _uiState.value.copy(tournamentConfig = newConfig)
        calculatePayouts()
    }

    private fun updateWeights(weights: List<Int>) {
        val newConfig = _uiState.value.tournamentConfig.copy(payoutWeights = weights)
        _uiState.value = _uiState.value.copy(tournamentConfig = newConfig)
        calculatePayouts()
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