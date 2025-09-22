package com.huntercoles.fatline.portfoliofeature.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.fatline.core.preferences.TournamentPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BankViewModel @Inject constructor(
    private val tournamentPreferences: TournamentPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(BankUiState())
    val uiState: StateFlow<BankUiState> = _uiState.asStateFlow()

    init {
        // Initialize with saved player count
        val savedPlayerCount = tournamentPreferences.getPlayerCount()
        initializePlayers(savedPlayerCount)
        
        // Listen for player count changes from calculator
        viewModelScope.launch {
            tournamentPreferences.playerCount.collect { newPlayerCount ->
                if (newPlayerCount != _uiState.value.players.size) {
                    updatePlayerCount(newPlayerCount)
                }
            }
        }
    }

    fun acceptIntent(intent: BankIntent) {
        when (intent) {
            is BankIntent.PlayerNameChanged -> updatePlayerName(intent.playerId, intent.name)
            is BankIntent.BuyInToggled -> toggleBuyIn(intent.playerId)
            is BankIntent.FoodToggled -> toggleFood(intent.playerId)
            is BankIntent.BountyToggled -> toggleBounty(intent.playerId)
            is BankIntent.AllToggled -> toggleAll(intent.playerId)
            is BankIntent.EliminatedToggled -> toggleEliminated(intent.playerId)
            is BankIntent.PayedOutToggled -> togglePayedOut(intent.playerId)
            is BankIntent.PlayerCountChanged -> updatePlayerCount(intent.count)
        }
    }

    private fun initializePlayers(count: Int) {
        val players = (1..count).map { playerNum ->
            PlayerData(
                id = playerNum,
                name = "Player $playerNum"
            )
        }
        _uiState.update { it.copy(players = players) }
        updateCalculations()
    }

    private fun updatePlayerCount(count: Int) {
        val currentPlayers = _uiState.value.players
        val newPlayers = if (count > currentPlayers.size) {
            // Add players
            val additionalPlayers = (currentPlayers.size + 1..count).map { playerNum ->
                PlayerData(
                    id = playerNum,
                    name = "Player $playerNum"
                )
            }
            currentPlayers + additionalPlayers
        } else {
            // Remove players
            currentPlayers.take(count)
        }
        _uiState.update { it.copy(players = newPlayers) }
        updateCalculations()
    }

    private fun updatePlayerName(playerId: Int, name: String) {
        _uiState.update { state ->
            val updatedPlayers = state.players.map { player ->
                if (player.id == playerId) player.copy(name = name) else player
            }
            state.copy(players = updatedPlayers)
        }
    }

    private fun toggleBuyIn(playerId: Int) {
        updatePlayerPayment(playerId) { it.copy(buyIn = !it.buyIn) }
    }

    private fun toggleFood(playerId: Int) {
        updatePlayerPayment(playerId) { it.copy(food = !it.food) }
    }

    private fun toggleBounty(playerId: Int) {
        updatePlayerPayment(playerId) { it.copy(bounty = !it.bounty) }
    }

    private fun toggleAll(playerId: Int) {
        val currentState = _uiState.value
        val player = currentState.players.find { it.id == playerId }
        if (player != null) {
            val allChecked = !player.all
            updatePlayerPayment(playerId) {
                it.copy(
                    buyIn = allChecked,
                    food = allChecked,
                    bounty = allChecked,
                    all = allChecked
                )
            }
        }
    }

    private fun toggleEliminated(playerId: Int) {
        updatePlayerPayment(playerId) { it.copy(eliminated = !it.eliminated) }
    }

    private fun togglePayedOut(playerId: Int) {
        updatePlayerPayment(playerId) { it.copy(payedOut = !it.payedOut) }
    }

    private fun updatePlayerPayment(playerId: Int, updateFunction: (PlayerData) -> PlayerData) {
        _uiState.update { state ->
            val updatedPlayers = state.players.map { player ->
                if (player.id == playerId) {
                    val updatedPlayer = updateFunction(player)
                    // Auto-update "all" checkbox based on individual payments
                    val allChecked = updatedPlayer.buyIn && updatedPlayer.food && updatedPlayer.bounty
                    updatedPlayer.copy(all = allChecked)
                } else player
            }
            state.copy(players = updatedPlayers)
        }
        updateCalculations()
    }

    private fun updateCalculations() {
        val currentState = _uiState.value
        val playerCount = currentState.players.size

        // Calculate total pool
        val totalPool = playerCount * (currentState.buyInAmount + currentState.foodAmount + currentState.bountyAmount)

        // Calculate total paid
        var totalPaid = 0.0
        var eliminatedCount = 0
        var payedOutCount = 0

        for (player in currentState.players) {
            if (player.buyIn) totalPaid += currentState.buyInAmount
            if (player.food) totalPaid += currentState.foodAmount
            if (player.bounty) totalPaid += currentState.bountyAmount
            if (player.eliminated) eliminatedCount++
            if (player.payedOut) payedOutCount++
        }

        val activePlayers = playerCount - eliminatedCount
        val percentPaid = if (totalPool > 0) (totalPaid / totalPool) * 100 else 0.0

        _uiState.update {
            it.copy(
                totalPool = totalPool,
                totalPaid = totalPaid,
                percentPaid = percentPaid,
                activePlayers = activePlayers,
                payedOutCount = payedOutCount
            )
        }
    }
}