package com.huntercoles.fatline.portfoliofeature.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.fatline.core.constants.TournamentConstants
import com.huntercoles.fatline.core.preferences.BankPreferences
import com.huntercoles.fatline.core.preferences.TimerPreferences
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
    private val bankPreferences: BankPreferences,
    private val timerPreferences: TimerPreferences
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

        viewModelScope.launch {
            tournamentPreferences.rebuyPerPlayer.collect { amount ->
                if (amount <= 0.0) {
                    resetAllRebuys()
                }
            }
        }

        viewModelScope.launch {
            tournamentPreferences.addOnPerPlayer.collect { amount ->
                if (amount <= 0.0) {
                    resetAllAddons()
                }
            }
        }

        // Listen for timer running state changes
        viewModelScope.launch {
            timerPreferences.timerRunning.collect { isRunning ->
                _uiState.update { it.copy(isTimerRunning = isRunning) }
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
            is BankIntent.ConfirmPlayerActionWithCount -> confirmPendingAction(intent.count, intent.selectedPlayerId)
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
            is BankIntent.ShowWeightsDialog -> showWeightsDialog()
            is BankIntent.HideWeightsDialog -> hideWeightsDialog()
            is BankIntent.UpdateWeights -> updateWeights(intent.weights)
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
                addons = bankPreferences.getPlayerAddons(playerNum),
                eliminatedBy = bankPreferences.getPlayerEliminatedBy(playerNum)
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
        val player = _uiState.value.players.firstOrNull { it.id == playerId } ?: return
        val apply = !player.out
        setPlayerOut(playerId, apply, if (apply) player.eliminatedBy else null)
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
                val isLastActive = _uiState.value.players.count { !it.out } <= 1
                if ((apply && isLastActive) || (!apply && !player.out)) return

                val selectableIds = if (apply) {
                    buildPlayerDisplayModels(_uiState.value.players, _uiState.value.eliminationOrder)
                        .map { it.player.id }
                        .filter { it != playerId }
                } else emptyList()

                val initialSelection = when {
                    player.eliminatedBy != null && selectableIds.contains(player.eliminatedBy) -> player.eliminatedBy
                    else -> selectableIds.firstOrNull()
                }

                PendingPlayerAction(
                    playerId = playerId,
                    actionType = actionType,
                    apply = apply,
                    selectablePlayerIds = selectableIds,
                    selectedPlayerId = if (apply) initialSelection else null,
                    allowUnassignedSelection = apply
                )
            }
            PlayerActionType.BUY_IN -> {
                val apply = !player.buyIn
                val tournamentConfig = tournamentPreferences.getCurrentTournamentConfig()
                val buyInCost = if (apply) {
                    tournamentConfig.buyIn + tournamentConfig.foodPerPlayer + tournamentConfig.bountyPerPlayer + 
                    (player.addons * tournamentConfig.addOnPerPlayer) + (player.rebuys * tournamentConfig.rebuyPerPlayer)
                } else 0.0
                PendingPlayerAction(playerId, actionType, apply, buyInCost = buyInCost)
            }
            PlayerActionType.PAYED_OUT -> {
                val apply = !player.payedOut
                val (payoutAmount, buyInPayout, knockoutBonus, kingsBounty, buyInCost) = if (apply) {
                    // Calculate the payout breakdown for this player
                    val currentState = _uiState.value
                    val tournamentConfig = tournamentPreferences.getCurrentTournamentConfig()
                    val prizePool = currentState.buyInPool + currentState.rebuyPool + currentState.addonPool
                    val payouts = calculatePayoutPositions(
                        config = tournamentConfig,
                        prizePool = prizePool,
                        playerCount = currentState.players.size
                    )
                    val sanitizedElimination = currentState.eliminationOrder
                        .filter { it in 1..currentState.players.size }
                        .distinct()
                    val eliminationSet = sanitizedElimination.toSet()
                    val knockoutCounts = currentState.players
                        .filter { it.eliminatedBy != null }
                        .groupingBy { it.eliminatedBy!! }
                        .eachCount()

                    // Find the player's leaderboard payout
                    val payoutPosition = payouts.firstOrNull { payout ->
                        determinePlayerForPosition(
                            position = payout.position,
                            numPlayers = currentState.players.size,
                            eliminationOrder = sanitizedElimination,
                            eliminationSet = eliminationSet
                        ) == playerId
                    }
                    val leaderboardPayout = payoutPosition?.payout ?: 0.0

                    // Calculate knockout bonus
                    val playerKnockouts = knockoutCounts[playerId] ?: 0
                    val knockoutBonusAmount = playerKnockouts * tournamentConfig.bountyPerPlayer

                    // King's bounty - winner gets their own bounty back
                    val kingsBountyAmount = if (payoutPosition?.position == 1) tournamentConfig.bountyPerPlayer else 0.0

                    // Calculate player's buy-in cost (buy-in + food + bounty + addons + rebuys)
                    val playerBuyInCost = tournamentConfig.buyIn + tournamentConfig.foodPerPlayer + tournamentConfig.bountyPerPlayer + 
                        (player.addons * tournamentConfig.addOnPerPlayer) + (player.rebuys * tournamentConfig.rebuyPerPlayer)

                    // Total payout (net pay = winnings - buy-in cost)
                    val netPay = leaderboardPayout + knockoutBonusAmount + kingsBountyAmount - playerBuyInCost

                    listOf(netPay, leaderboardPayout, knockoutBonusAmount, kingsBountyAmount, playerBuyInCost)
                } else listOf(0.0, 0.0, 0.0, 0.0, 0.0)
                PendingPlayerAction(
                    playerId, actionType, apply,
                    payoutAmount = payoutAmount,
                    buyInPayout = buyInPayout,
                    knockoutBonus = knockoutBonus,
                    kingsBounty = kingsBounty,
                    buyInCost = buyInCost,
                    knockoutCount = if (apply) {
                        val currentState = _uiState.value
                        val knockoutCounts = currentState.players
                            .filter { it.eliminatedBy != null }
                            .groupingBy { it.eliminatedBy!! }
                            .eachCount()
                        knockoutCounts[playerId] ?: 0
                    } else 0
                )
            }
            PlayerActionType.REBUY -> {
                if (_uiState.value.rebuyAmount <= 0.0) return
                val baseCount = player.rebuys.coerceAtLeast(0)
                val suggested = (baseCount + 1).coerceAtMost(MAX_PURCHASE_COUNT)
                PendingPlayerAction(
                    playerId = playerId,
                    actionType = actionType,
                    apply = true,
                    baseCount = baseCount,
                    targetCount = suggested
                )
            }
            PlayerActionType.ADDON -> {
                if (_uiState.value.addonAmount <= 0.0) return
                val baseCount = player.addons.coerceAtLeast(0)
                val suggested = (baseCount + 1).coerceAtMost(MAX_PURCHASE_COUNT)
                PendingPlayerAction(
                    playerId = playerId,
                    actionType = actionType,
                    apply = true,
                    baseCount = baseCount,
                    targetCount = suggested
                )
            }
        }

        _uiState.update { state -> state.copy(pendingAction = pending) }
    }

    private fun clearPendingAction() {
        _uiState.update { it.copy(pendingAction = null) }
    }

    private fun confirmPendingAction(targetCountOverride: Int? = null, selectedPlayerId: Int? = null) {
        val pendingAction = _uiState.value.pendingAction ?: return
        val player = _uiState.value.players.firstOrNull { it.id == pendingAction.playerId }
        if (player == null) {
            clearPendingAction()
            return
        }

        val sanitizedOverride = targetCountOverride?.coerceIn(0, MAX_PURCHASE_COUNT)
        val sanitizedSelection = selectedPlayerId?.takeIf { pendingAction.selectablePlayerIds.contains(it) }

        val selectionToApply = if (pendingAction.allowUnassignedSelection) {
            sanitizedSelection
        } else {
            sanitizedSelection ?: pendingAction.selectedPlayerId
        }

        when (pendingAction.actionType) {
            PlayerActionType.OUT -> {
                val eliminatedBy = if (pendingAction.apply) selectionToApply else null
                setPlayerOut(player.id, pendingAction.apply, eliminatedBy)
            }
            PlayerActionType.BUY_IN -> setPlayerBuyIn(player.id, pendingAction.apply)
            PlayerActionType.PAYED_OUT -> setPlayerPayedOut(player.id, pendingAction.apply)
            PlayerActionType.REBUY -> {
                val fallback = if (pendingAction.targetCount >= 0) pendingAction.targetCount else player.rebuys
                val newCount = sanitizedOverride ?: fallback
                updatePlayerRebuys(player.id, newCount)
            }
            PlayerActionType.ADDON -> {
                val fallback = if (pendingAction.targetCount >= 0) pendingAction.targetCount else player.addons
                val newCount = sanitizedOverride ?: fallback
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

    private fun setPlayerOut(playerId: Int, value: Boolean, eliminatedBy: Int?) {
        updatePlayerPayment(
            playerId = playerId,
            updateFunction = { player ->
                val normalizedEliminator = if (value) eliminatedBy else null
                if (player.out == value && player.eliminatedBy == normalizedEliminator) {
                    player
                } else {
                    player.copy(out = value, eliminatedBy = normalizedEliminator)
                }
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
        val sanitized = rebuys.coerceIn(0, MAX_PURCHASE_COUNT)

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
        val sanitized = addons.coerceIn(0, MAX_PURCHASE_COUNT)

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
                    bankPreferences.savePlayerEliminatedBy(playerId, playerUpdate.eliminatedBy)
                    
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
        val basePerPlayer = tournamentConfig.buyIn + tournamentConfig.foodPerPlayer + tournamentConfig.bountyPerPlayer

        val buyInPool = playerCount * tournamentConfig.buyIn
        val foodPool = playerCount * tournamentConfig.foodPerPlayer
        val bountyPool = playerCount * tournamentConfig.bountyPerPlayer

        val totalRebuyCount = currentState.players.sumOf { it.rebuys }
        val totalAddonCount = currentState.players.sumOf { it.addons }

        val rebuyPool = totalRebuyCount * tournamentConfig.rebuyPerPlayer
        val addonPool = totalAddonCount * tournamentConfig.addOnPerPlayer

        val totalPool = buyInPool + foodPool + bountyPool + rebuyPool + addonPool

        val knockoutCounts = currentState.players
            .filter { it.eliminatedBy != null }
            .groupingBy { it.eliminatedBy!! }
            .eachCount()

        val totalPaidInBase = currentState.players.sumOf { player ->
            if (player.buyIn) basePerPlayer else 0.0
        }
        val totalPaidIn = totalPaidInBase + rebuyPool + addonPool

        // Calculate total paid out using tournament payouts and elimination order
        val prizePool = buyInPool + rebuyPool + addonPool
        val payouts = calculatePayoutPositions(
            config = tournamentConfig,
            prizePool = prizePool,
            playerCount = playerCount
        )
        val sanitizedElimination = currentState.eliminationOrder
            .filter { it in 1..playerCount }
            .distinct()
        val eliminationSet = sanitizedElimination.toSet()

        val payoutEligibleIds = mutableSetOf<Int>()

        // Calculate leaderboard payouts for each position
        val leaderboardPayouts = mutableMapOf<Int, Double>()
        val winnerId = payouts.firstOrNull()?.let { payout ->
            determinePlayerForPosition(
                position = payout.position,
                numPlayers = playerCount,
                eliminationOrder = sanitizedElimination,
                eliminationSet = eliminationSet
            )
        }

        payouts.forEach { payout ->
            val playerId = determinePlayerForPosition(
                position = payout.position,
                numPlayers = playerCount,
                eliminationOrder = sanitizedElimination,
                eliminationSet = eliminationSet
            )
            playerId?.let { 
                payoutEligibleIds.add(it)
                leaderboardPayouts[it] = payout.payout
            }
        }

        // Calculate total paid out: leaderboard payouts + knockout bonuses + king's bounty
        val totalPayedOut = currentState.players.sumOf { player ->
            if (!player.payedOut) 0.0 else {
                val leaderboardPayout = leaderboardPayouts[player.id] ?: 0.0
                val knockoutBonus = (knockoutCounts[player.id] ?: 0) * tournamentConfig.bountyPerPlayer
                val kingsBounty = if (player.id == winnerId) tournamentConfig.bountyPerPlayer else 0.0
                leaderboardPayout + knockoutBonus + kingsBounty
            }
        }

        // Create formatted payout positions for UI
        val formattedPayoutPositions = payouts.map { payout ->
            val percentage = if (prizePool > 0) (payout.payout / prizePool) * 100 else 0.0
            val positionSuffix = when {
                payout.position % 100 in 10..20 -> "th"
                payout.position % 10 == 1 -> "st"
                payout.position % 10 == 2 -> "nd"
                payout.position % 10 == 3 -> "rd"
                else -> "th"
            }
            PayoutPosition(
                position = payout.position,
                payout = payout.payout,
                formattedPayout = "$${String.format("%.2f", payout.payout)}",
                formattedPercentage = "${String.format("%.1f", percentage)}%",
                positionSuffix = positionSuffix
            )
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
                buyInPool = buyInPool,
                foodPool = foodPool,
                bountyPool = bountyPool,
                rebuyPool = rebuyPool,
                addonPool = addonPool,
                totalRebuyCount = totalRebuyCount,
                totalAddonCount = totalAddonCount,
                activePlayers = activePlayers,
                payedOutCount = payedOutCount,
                buyInAmount = tournamentConfig.buyIn,
                foodAmount = tournamentConfig.foodPerPlayer,
                bountyAmount = tournamentConfig.bountyPerPlayer,
                rebuyAmount = tournamentConfig.rebuyPerPlayer,
                addonAmount = tournamentConfig.addOnPerPlayer,
                knockoutCounts = knockoutCounts,
                payoutEligiblePlayerIds = payoutEligibleIds,
                payoutPositions = formattedPayoutPositions,
                payoutWeights = tournamentConfig.payoutWeights
            )
        }
    }

    private fun resetAllRebuys() {
        val currentPlayers = _uiState.value.players
        if (currentPlayers.none { it.rebuys != 0 }) {
            bankPreferences.clearAllRebuys()
            return
        }

        bankPreferences.clearAllRebuys()
        val updatedPlayers = currentPlayers.map { player ->
            if (player.rebuys != 0) player.copy(rebuys = 0) else player
        }
        _uiState.update { it.copy(players = updatedPlayers) }
        updateCalculations()
    }

    private fun resetAllAddons() {
        val currentPlayers = _uiState.value.players
        if (currentPlayers.none { it.addons != 0 }) {
            bankPreferences.clearAllAddons()
            return
        }

        bankPreferences.clearAllAddons()
        val updatedPlayers = currentPlayers.map { player ->
            if (player.addons != 0) player.copy(addons = 0) else player
        }
        _uiState.update { it.copy(players = updatedPlayers) }
        updateCalculations()
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
    
    private fun updateWeights(weights: List<Int>) {
        tournamentPreferences.setPayoutWeights(weights)
        updateCalculations()
        hideWeightsDialog()
    }
    
    private fun showWeightsDialog() {
        _uiState.update { it.copy(showWeightsDialog = true) }
    }

    private fun hideWeightsDialog() {
        _uiState.update { it.copy(showWeightsDialog = false) }
    }

    private fun isInDefaultState(): Boolean {
        val currentPlayerCount = _uiState.value.players.size
        return bankPreferences.isInDefaultState(currentPlayerCount)
    }
}

private data class PayoutPositionInternal(
    val position: Int,
    val payout: Double
)

private fun calculatePayoutPositions(
    config: TournamentPreferences.TournamentConfigData,
    prizePool: Double,
    playerCount: Int
): List<PayoutPositionInternal> {
    val maxPayingPositions = max(1, playerCount / 3)
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
        val payout = (weight.toDouble() / totalWeight) * prizePool
        PayoutPositionInternal(position = index + 1, payout = payout)
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

private fun calculatePlayerTotalPayout(
    playerId: Int,
    payouts: List<PayoutPositionInternal>,
    knockoutCounts: Map<Int, Int>,
    bountyPerPlayer: Double,
    playerCount: Int,
    eliminationOrder: List<Int>,
    eliminationSet: Set<Int>
): Double {
    val payoutPosition = payouts.firstOrNull { payout ->
        determinePlayerForPosition(
            position = payout.position,
            numPlayers = playerCount,
            eliminationOrder = eliminationOrder,
            eliminationSet = eliminationSet
        ) == playerId
    }

    val leaderboardPayout = payoutPosition?.payout ?: 0.0
    val knockoutBonus = (knockoutCounts[playerId] ?: 0) * bountyPerPlayer

    return leaderboardPayout + knockoutBonus
}