package com.huntercoles.fatline.portfoliofeature.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.fatline.core.preferences.TournamentPreferences
import com.huntercoles.fatline.core.preferences.BankPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BankViewModel @Inject constructor(
    private val tournamentPreferences: TournamentPreferences,
    private val bankPreferences: BankPreferences
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
            is BankIntent.OutToggled -> toggleOut(intent.playerId)
            is BankIntent.PayedOutToggled -> togglePayedOut(intent.playerId)
            is BankIntent.PlayerCountChanged -> updatePlayerCount(intent.count)
            is BankIntent.ShowResetDialog -> {
                // Only show dialog if not in default state
                if (!isInDefaultState()) {
                    showResetDialog()
                }
            }
            is BankIntent.HideResetDialog -> hideResetDialog()
            is BankIntent.ConfirmReset -> {
                resetBankData()
                hideResetDialog()
            }
        }
    }

    private fun initializePlayers(count: Int) {
        val players = (1..count).map { playerNum ->
            PlayerData(
                id = playerNum,
                name = bankPreferences.getPlayerName(playerNum),
                buyIn = bankPreferences.getPlayerBuyInStatus(playerNum),
                out = bankPreferences.getPlayerOutStatus(playerNum),
                payedOut = bankPreferences.getPlayerPayedOutStatus(playerNum)
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
        // Save to preferences
        bankPreferences.savePlayerName(playerId, name)
        
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

    private fun toggleOut(playerId: Int) {
        updatePlayerPayment(playerId) { it.copy(out = !it.out) }
    }

    private fun togglePayedOut(playerId: Int) {
        updatePlayerPayment(playerId) { it.copy(payedOut = !it.payedOut) }
    }

    private fun updatePlayerPayment(playerId: Int, updateFunction: (PlayerData) -> PlayerData) {
        _uiState.update { state ->
            val updatedPlayers = state.players.map { player ->
                if (player.id == playerId) {
                    val updatedPlayer = updateFunction(player)
                    
                    // Save to preferences
                    bankPreferences.savePlayerBuyInStatus(playerId, updatedPlayer.buyIn)
                    bankPreferences.savePlayerOutStatus(playerId, updatedPlayer.out)
                    bankPreferences.savePlayerPayedOutStatus(playerId, updatedPlayer.payedOut)
                    
                    updatedPlayer
                } else player
            }
            state.copy(players = updatedPlayers)
        }
        updateCalculations()
    }

    private fun updateCalculations() {
        val currentState = _uiState.value
        val playerCount = currentState.players.size

        // Get tournament config from preferences
        val tournamentConfig = tournamentPreferences.getCurrentTournamentConfig()
        val totalPerPlayer = tournamentConfig.totalPerPlayer

        // Calculate total pool
        val totalPool = playerCount * totalPerPlayer

        // Calculate total paid (only count players who have bought in)
        val totalPaid = currentState.players.count { it.buyIn } * totalPerPlayer

        // Count various player states
        val outCount = currentState.players.count { it.out }
        val payedOutCount = currentState.players.count { it.payedOut }
        val activePlayers = playerCount - outCount

        val percentPaid = if (totalPool > 0) (totalPaid / totalPool) * 100 else 0.0

        _uiState.update {
            it.copy(
                totalPool = totalPool,
                totalPaid = totalPaid,
                percentPaid = percentPaid,
                activePlayers = activePlayers,
                payedOutCount = payedOutCount,
                buyInAmount = tournamentConfig.buyIn,
                foodAmount = tournamentConfig.foodPerPlayer,
                bountyAmount = tournamentConfig.bountyPerPlayer
            )
        }
    }
    
    private fun showResetDialog() {
        _uiState.update { it.copy(showResetDialog = true) }
    }
    
    private fun hideResetDialog() {
        _uiState.update { it.copy(showResetDialog = false) }
    }
    
    private fun resetBankData() {
        // Reset bank preferences
        bankPreferences.resetAllBankData()
        
        // Reinitialize players with fresh data
        val savedPlayerCount = tournamentPreferences.getPlayerCount()
        initializePlayers(savedPlayerCount)
    }
    
    private fun isInDefaultState(): Boolean {
        val currentPlayerCount = _uiState.value.players.size
        return bankPreferences.isInDefaultState(currentPlayerCount)
    }
}