package com.huntercoles.fatline.portfoliofeature.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.fatline.core.constants.TournamentConstants
import com.huntercoles.fatline.core.preferences.BankPreferences
import com.huntercoles.fatline.core.preferences.TournamentPreferences
import com.huntercoles.fatline.portfoliofeature.presentation.BankIntent.CancelPlayerAction
import com.huntercoles.fatline.portfoliofeature.presentation.BankIntent.ConfirmPlayerAction
import com.huntercoles.fatline.portfoliofeature.presentation.BankIntent.PlayerAddonChanged
import com.huntercoles.fatline.portfoliofeature.presentation.BankIntent.PlayerCountChanged
import com.huntercoles.fatline.portfoliofeature.presentation.BankIntent.PlayerNameChanged
import com.huntercoles.fatline.portfoliofeature.presentation.BankIntent.PlayerRebuyChanged
import com.huntercoles.fatline.portfoliofeature.presentation.BankIntent.ShowPlayerActionDialog
import com.huntercoles.fatline.portfoliofeature.presentation.PendingPlayerAction
import com.huntercoles.fatline.portfoliofeature.presentation.PlayerActionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

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

        // Keep elimination order in sync with preferences
        viewModelScope.launch {
            bankPreferences.eliminationOrder.collect { order ->
                _uiState.update { it.copy(eliminationOrder = order) }
            }
        }
    }

    fun acceptIntent(intent: BankIntent) {
        when (intent) {
            is PlayerNameChanged -> updatePlayerName(intent.playerId, intent.name)
            is BankIntent.BuyInToggled -> toggleBuyIn(intent.playerId)
            is BankIntent.OutToggled -> toggleOut(intent.playerId)
            is BankIntent.PayedOutToggled -> togglePayedOut(intent.playerId)
            is PlayerCountChanged -> updatePlayerCount(intent.count)
            is PlayerRebuyChanged -> updatePlayerRebuys(intent.playerId, intent.rebuys)
            is PlayerAddonChanged -> updatePlayerAddons(intent.playerId, intent.addons)
            is ShowPlayerActionDialog -> showPlayerActionDialog(intent.playerId, intent.action)
            is ConfirmPlayerAction -> confirmPendingAction()
            is CancelPlayerAction -> clearPendingAction()
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
                payedOut = bankPreferences.getPlayerPayedOutStatus(playerNum),
                rebuys = bankPreferences.getPlayerRebuys(playerNum),
                addons = bankPreferences.getPlayerAddons(playerNum)
            )
        }
        val validIds = players.map { it.id }.toSet()
        val storedOrder = bankPreferences.getEliminationOrder()
        val sanitizedOrder = storedOrder.filter { it in validIds }.distinct()
        val missingEliminations = players
            .filter { it.out && it.id !in sanitizedOrder }
            .map { it.id }
        val normalizedOrder = (sanitizedOrder + missingEliminations)
        if (normalizedOrder != storedOrder) {
            bankPreferences.saveEliminationOrder(normalizedOrder)
        }

        _uiState.update { it.copy(players = players, eliminationOrder = normalizedOrder) }
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
        val validIds = newPlayers.map { it.id }.toSet()
        val adjustedOrder = bankPreferences.getEliminationOrder()
            .filter { it in validIds }
            .distinct()
        if (adjustedOrder != _uiState.value.eliminationOrder) {
            bankPreferences.saveEliminationOrder(adjustedOrder)
        }

        _uiState.update { it.copy(players = newPlayers, eliminationOrder = adjustedOrder) }
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

        if (_uiState.value.eliminationOrder.contains(playerId)) {
            bankPreferences.saveEliminationOrder(_uiState.value.eliminationOrder)
        }
    }

    private fun toggleBuyIn(playerId: Int) {
        updatePlayerPayment(
            playerId = playerId,
            updateFunction = { it.copy(buyIn = !it.buyIn) }
        )
    }

    private fun toggleOut(playerId: Int) {
        updatePlayerPayment(
            playerId = playerId,
            updateFunction = { it.copy(out = !it.out) }
        ) { updatedPlayer ->
            updateEliminationOrder(updatedPlayer.id, updatedPlayer.out)
        }
    }

    private fun togglePayedOut(playerId: Int) {
        updatePlayerPayment(
            playerId = playerId,
            updateFunction = { it.copy(payedOut = !it.payedOut) }
        )
    }

    private fun showPlayerActionDialog(playerId: Int, actionType: PlayerActionType) {
        val player = _uiState.value.players.firstOrNull { it.id == playerId } ?: return
        val pending = when (actionType) {
            PlayerActionType.OUT -> {
                val apply = !player.out
                if (!apply && !player.out) return
                PendingPlayerAction(playerId, actionType, apply)
            }
            PlayerActionType.BUY_IN -> {
                val apply = !player.buyIn
                PendingPlayerAction(playerId, actionType, apply)
            }
            PlayerActionType.PAYED_OUT -> {
                val apply = !player.payedOut
                PendingPlayerAction(playerId, actionType, apply)
            }
            PlayerActionType.REBUY -> {
                val apply = player.rebuys == 0
                val delta = if (apply) 1 else -player.rebuys
                if (!apply && player.rebuys == 0) return
                PendingPlayerAction(playerId, actionType, apply, delta)
            }
            PlayerActionType.ADDON -> {
                val apply = player.addons == 0
                val delta = if (apply) 1 else -player.addons
                if (!apply && player.addons == 0) return
                PendingPlayerAction(playerId, actionType, apply, delta)
            }
        }

        _uiState.update { state -> state.copy(pendingAction = pending) }
    }

    private fun clearPendingAction() {
        _uiState.update { it.copy(pendingAction = null) }
    }

    private fun confirmPendingAction() {
        val pendingAction = _uiState.value.pendingAction ?: return
        val player = _uiState.value.players.firstOrNull { it.id == pendingAction.playerId }
        if (player == null) {
            clearPendingAction()
            return
        }

        when (pendingAction.actionType) {
            PlayerActionType.OUT -> setPlayerOut(player.id, pendingAction.apply)
            PlayerActionType.BUY_IN -> setPlayerBuyIn(player.id, pendingAction.apply)
            PlayerActionType.PAYED_OUT -> setPlayerPayedOut(player.id, pendingAction.apply)
            PlayerActionType.REBUY -> {
                val newCount = (player.rebuys + pendingAction.delta).coerceAtLeast(0)
                updatePlayerRebuys(player.id, newCount)
            }
            PlayerActionType.ADDON -> {
                val newCount = (player.addons + pendingAction.delta).coerceAtLeast(0)
                updatePlayerAddons(player.id, newCount)
            }
        }

        clearPendingAction()
    }

    private fun setPlayerBuyIn(playerId: Int, value: Boolean) {
        updatePlayerPayment(
            playerId = playerId,
            updateFunction = { player ->
                if (player.buyIn == value) player else player.copy(buyIn = value)
            }
        )
    }

    private fun setPlayerOut(playerId: Int, value: Boolean) {
        updatePlayerPayment(
            playerId = playerId,
            updateFunction = { player ->
                if (player.out == value) player else player.copy(out = value)
            }
        ) { updatedPlayer ->
            updateEliminationOrder(updatedPlayer.id, updatedPlayer.out)
        }
    }

    private fun setPlayerPayedOut(playerId: Int, value: Boolean) {
        updatePlayerPayment(
            playerId = playerId,
            updateFunction = { player ->
                if (player.payedOut == value) player else player.copy(payedOut = value)
            }
        )
    }

    private fun updatePlayerRebuys(playerId: Int, rebuys: Int) {
        val sanitized = rebuys.coerceIn(0, 1)

        // Save to preferences
        bankPreferences.savePlayerRebuys(playerId, sanitized)
        
        _uiState.update { state ->
            val updatedPlayers = state.players.map { player ->
                if (player.id == playerId) player.copy(rebuys = sanitized) else player
            }
            state.copy(players = updatedPlayers)
        }
        updateCalculations()
    }

    private fun updatePlayerAddons(playerId: Int, addons: Int) {
        val sanitized = addons.coerceIn(0, 1)

        // Save to preferences
        bankPreferences.savePlayerAddons(playerId, sanitized)
        
        _uiState.update { state ->
            val updatedPlayers = state.players.map { player ->
                if (player.id == playerId) player.copy(addons = sanitized) else player
            }
            state.copy(players = updatedPlayers)
        }
        updateCalculations()
    }

    private fun updatePlayerPayment(
        playerId: Int,
        updateFunction: (PlayerData) -> PlayerData,
        afterUpdate: ((PlayerData) -> Unit)? = null
    ) {
        var updatedPlayer: PlayerData? = null
        _uiState.update { state ->
            val updatedPlayers = state.players.map { player ->
                if (player.id == playerId) {
                    val playerUpdate = updateFunction(player)
                    
                    // Save to preferences
                    bankPreferences.savePlayerBuyInStatus(playerId, playerUpdate.buyIn)
                    bankPreferences.savePlayerOutStatus(playerId, playerUpdate.out)
                    bankPreferences.savePlayerPayedOutStatus(playerId, playerUpdate.payedOut)
                    
                    updatedPlayer = playerUpdate
                    playerUpdate
                } else player
            }
            state.copy(players = updatedPlayers)
        }
        updatedPlayer?.let { afterUpdate?.invoke(it) }
        updateCalculations()
    }

    private fun updateEliminationOrder(playerId: Int, isOut: Boolean) {
        val totalPlayers = _uiState.value.players.size
        val currentOrder = bankPreferences.getEliminationOrder()
            .filter { it in 1..totalPlayers }
            .distinct()
        val filteredOrder = currentOrder.filterNot { it == playerId }
        val nextOrder = if (isOut) filteredOrder + playerId else filteredOrder

        bankPreferences.saveEliminationOrder(nextOrder)
        _uiState.update { it.copy(eliminationOrder = nextOrder) }
    }

    private fun updateCalculations() {
        val currentState = _uiState.value
        val playerCount = currentState.players.size

        // Get tournament config from preferences
        val tournamentConfig = tournamentPreferences.getCurrentTournamentConfig()
        val totalPerPlayer = tournamentConfig.totalPerPlayer

        // Calculate total pool
        val totalPool = playerCount * totalPerPlayer

        // Calculate total paid in (only count players who have bought in)
        val totalPaidIn = currentState.players.count { it.buyIn } * totalPerPlayer

        // Calculate total paid out using tournament payouts and elimination order
        val prizePool = playerCount * tournamentConfig.buyIn
        val payouts = calculatePayoutPositions(tournamentConfig)
        val sanitizedElimination = currentState.eliminationOrder
            .filter { it in 1..playerCount }
            .distinct()
        val eliminationSet = sanitizedElimination.toSet()

        val totalPayedOut = payouts.sumOf { payout ->
            val playerId = determinePlayerForPosition(
                position = payout.position,
                numPlayers = playerCount,
                eliminationOrder = sanitizedElimination,
                eliminationSet = eliminationSet
            )

            val isPayedOut = playerId?.let { id ->
                currentState.players.firstOrNull { it.id == id }?.payedOut == true
            } ?: false

            if (isPayedOut) payout.payout else 0.0
        }

        // Count various player states
        val outCount = currentState.players.count { it.out }
        val payedOutCount = currentState.players.count { it.payedOut }
        val activePlayers = playerCount - outCount

        _uiState.update {
            it.copy(
                totalPool = totalPool,
                totalPaidIn = totalPaidIn,
                totalPayedOut = totalPayedOut,
                prizePool = prizePool,
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

private data class PayoutPosition(
    val position: Int,
    val payout: Double
)

private fun calculatePayoutPositions(
    config: TournamentPreferences.TournamentConfigData
): List<PayoutPosition> {
    val maxPayingPositions = max(1, config.numPlayers / 3)
    val defaultWeights = TournamentConstants.DEFAULT_PAYOUT_WEIGHTS.take(maxPayingPositions)
    val isUsingDefaultWeights = when {
        config.payoutWeights == defaultWeights -> true
        config.payoutWeights == TournamentConstants.DEFAULT_PAYOUT_WEIGHTS -> true
        else -> false
    }

    val actualPayingPositions = if (isUsingDefaultWeights) {
        min(maxPayingPositions, defaultWeights.size)
    } else {
        config.payoutWeights.size
    }

    val payingWeights = if (isUsingDefaultWeights) {
        defaultWeights.take(actualPayingPositions)
    } else {
        config.payoutWeights.take(actualPayingPositions)
    }

    val totalWeight = payingWeights.sum()
    if (totalWeight == 0) return emptyList()

    return payingWeights.mapIndexed { index, weight ->
        val payout = (weight.toDouble() / totalWeight) * config.prizePool
        PayoutPosition(position = index + 1, payout = payout)
    }
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