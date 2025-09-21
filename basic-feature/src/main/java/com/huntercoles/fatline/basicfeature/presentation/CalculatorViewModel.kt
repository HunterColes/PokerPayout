package com.huntercoles.fatline.basicfeature.presentation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class CalculatorViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    // Default payout weights (same as Python app)
    private val defaultWeights = listOf(35, 20, 15, 10, 8, 6, 3, 2, 1)

    init {
        calculatePayouts()
    }

    fun acceptIntent(intent: CalculatorIntent) {
        when (intent) {
            is CalculatorIntent.PlayerCountChanged -> updatePlayerCount(intent.count)
            is CalculatorIntent.BuyInChanged -> updateBuyIn(intent.amount)
            is CalculatorIntent.FoodPoolChanged -> updateFoodPool(intent.amount)
            is CalculatorIntent.BountyPoolChanged -> updateBountyPool(intent.amount)
        }
    }

    private fun updatePlayerCount(count: Int) {
        _uiState.update { it.copy(playerCount = count) }
        calculatePayouts()
    }

    private fun updateBuyIn(amount: Double) {
        _uiState.update { it.copy(buyIn = amount) }
        updateTotalPerPlayer()
        calculatePayouts()
    }

    private fun updateFoodPool(amount: Double) {
        _uiState.update { it.copy(foodPool = amount) }
        updateTotalPerPlayer()
        calculatePayouts()
    }

    private fun updateBountyPool(amount: Double) {
        _uiState.update { it.copy(bountyPool = amount) }
        updateTotalPerPlayer()
        calculatePayouts()
    }

    private fun updateTotalPerPlayer() {
        val currentState = _uiState.value
        val total = currentState.buyIn + currentState.foodPool + currentState.bountyPool
        _uiState.update { it.copy(totalPerPlayer = total) }
    }

    private fun calculatePayouts() {
        val currentState = _uiState.value

        // Calculate prize pool
        val prizePool = currentState.playerCount * currentState.buyIn

        // Calculate total pool
        val totalPool = prizePool +
                       (currentState.playerCount * currentState.foodPool) +
                       (currentState.playerCount * currentState.bountyPool)

        // Calculate number of paying positions (max 1/3 of players or length of weights)
        val maxPayingPositions = minOf(
            maxOf(1, currentState.playerCount / 3),
            defaultWeights.size
        )

        // Calculate total weight
        val payingWeights = defaultWeights.take(maxPayingPositions)
        val totalWeight = payingWeights.sum()

        // Calculate payouts
        val payouts = if (totalWeight > 0) {
            payingWeights.mapIndexed { index, weight ->
                val payout = (weight.toDouble() / totalWeight) * prizePool
                (index + 1) to payout
            }
        } else {
            emptyList()
        }

        _uiState.update {
            it.copy(
                prizePool = prizePool,
                totalPool = totalPool,
                payouts = payouts
            )
        }
    }
}