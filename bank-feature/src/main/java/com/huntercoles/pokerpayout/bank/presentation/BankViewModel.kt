package com.huntercoles.pokerpayout.bank.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.bank.presentation.BankIntent.CancelPlayerAction
import com.huntercoles.pokerpayout.bank.presentation.BankIntent.ConfirmPlayerAction
import com.huntercoles.pokerpayout.bank.presentation.BankIntent.PlayerAddonChanged
import com.huntercoles.pokerpayout.bank.presentation.BankIntent.PlayerCountChanged
import com.huntercoles.pokerpayout.bank.presentation.BankIntent.PlayerNameChanged
import com.huntercoles.pokerpayout.bank.presentation.BankIntent.PlayerRebuyChanged
import com.huntercoles.pokerpayout.bank.presentation.BankIntent.ShowPlayerActionDialog
import com.huntercoles.pokerpayout.core.domain.model.BankPlayer
import com.huntercoles.pokerpayout.core.domain.model.PayoutSettings
import com.huntercoles.pokerpayout.core.domain.model.Settlement
import com.huntercoles.pokerpayout.core.domain.usecase.SettleTournamentUseCase
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
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
    private val bankPreferences: BankPreferences,
    private val timerPreferences: TimerPreferences,
    private val settleTournament: SettleTournamentUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(BankUiState())
    val uiState: StateFlow<BankUiState> = _uiState.asStateFlow()

    /** The latest money picture; every amount on screen comes from here. */
    private var settlement: Settlement? = null

    /** Non-zero while this ViewModel writes Bank data, so it doesn't "reload" its own writes. */
    private var ownWrites = 0

    init {
        // Initialize with saved player count, dropping data of players removed while we were away
        updatePlayerCount(tournamentPreferences.getPlayerCount())

        // Follow the Tournament tab live: player count, amounts, payout structure. The Bank used to
        // show the totals from when it was opened until its next action.
        viewModelScope.launch {
            tournamentPreferences.config.collect { config ->
                if (config.numPlayers != _uiState.value.players.size) {
                    updatePlayerCount(config.numPlayers)
                } else {
                    updateCalculations()
                }
            }
        }

        // Bank data changed elsewhere, e.g. the Tournament tab cleared purchases after asking.
        viewModelScope.launch {
            bankPreferences.revision.collect { reloadIfChangedElsewhere() }
        }

        // Listen for timer running state changes
        viewModelScope.launch {
            timerPreferences.timerRunning.collect { isRunning ->
                _uiState.update { it.copy(isTimerRunning = isRunning) }
            }
        }
    }

    fun acceptIntent(intent: BankIntent) {
        ownWrites++
        try {
            handle(intent)
        } finally {
            ownWrites--
        }
    }

    @Suppress("CyclomaticComplexMethod") // One branch per intent; each delegates.
    private fun handle(intent: BankIntent) {
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
            is BankIntent.UpdatePayoutSettings -> updatePayoutSettings(intent.settings)
            is BankIntent.ShowPoolSummaryDialog -> showPoolSummaryDialog()
            is BankIntent.HidePoolSummaryDialog -> hidePoolSummaryDialog()
        }
    }

    private fun readPlayer(playerId: Int) = PlayerData(
        id = playerId,
        name = bankPreferences.getPlayerName(playerId),
        buyIn = bankPreferences.getPlayerBuyInStatus(playerId),
        out = bankPreferences.getPlayerOutStatus(playerId),
        payedOut = bankPreferences.getPlayerPayedOutStatus(playerId),
        rebuys = bankPreferences.getPlayerRebuys(playerId),
        addons = bankPreferences.getPlayerAddons(playerId),
        eliminatedBy = bankPreferences.getPlayerEliminatedBy(playerId)
    )

    /** The stored elimination order, limited to [players] and including every player marked out. */
    private fun normalizedEliminationOrder(players: List<PlayerData>): List<Int> {
        val validIds = players.map { it.id }.toSet()
        val sanitizedOrder = bankPreferences.getEliminationOrder().filter { it in validIds }.distinct()
        val missingEliminations = players.filter { it.out && it.id !in sanitizedOrder }.map { it.id }
        return sanitizedOrder + missingEliminations
    }

    private fun initializePlayers(count: Int) {
        val players = (1..count).map { readPlayer(it) }
        val normalizedOrder = normalizedEliminationOrder(players)
        if (normalizedOrder != bankPreferences.getEliminationOrder()) {
            bankPreferences.saveEliminationOrder(normalizedOrder)
        }

        _uiState.update { it.copy(players = players, eliminationOrder = normalizedOrder) }
        updateCalculations()
    }

    private fun reloadIfChangedElsewhere() {
        if (ownWrites > 0) return
        val state = _uiState.value
        val stored = state.players.map { readPlayer(it.id) }
        val storedOrder = normalizedEliminationOrder(stored)
        if (stored != state.players || storedOrder != state.eliminationOrder) {
            _uiState.update { it.copy(players = stored, eliminationOrder = storedOrder) }
            updateCalculations()
        }
    }

    private fun updatePlayerCount(count: Int) {
        ownWrites++
        try {
            // Players above the new count are removed for good, so they can't come back after a restart.
            bankPreferences.removePlayersAbove(count)
            initializePlayers(count)
        } finally {
            ownWrites--
        }
    }

    private fun updatePlayerName(playerId: Int, name: String) {
        val normalized = name.ifBlank { "Player $playerId" }
        bankPreferences.savePlayerName(playerId, normalized)

        _uiState.update { state ->
            val updatedPlayers = state.players.map { player ->
                if (player.id == playerId) player.copy(name = normalized) else player
            }
            state.copy(players = updatedPlayers)
        }
        updateCalculations()
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
            PlayerActionType.OUT -> knockoutAction(player)
            PlayerActionType.BUY_IN -> PendingPlayerAction(
                playerId = playerId,
                actionType = actionType,
                apply = !player.buyIn,
                buyInCostCents = if (!player.buyIn) settlement?.forPlayer(playerId)?.costCents ?: 0L else 0L
            )
            PlayerActionType.PAYED_OUT -> payOutAction(player)
            PlayerActionType.REBUY -> purchaseAction(player, actionType, _uiState.value.isRebuyEnabled, player.rebuys)
            PlayerActionType.ADDON -> purchaseAction(player, actionType, _uiState.value.isAddOnEnabled, player.addons)
        }

        _uiState.update { state -> state.copy(pendingAction = pending) }
    }

    private fun knockoutAction(player: PlayerData): PendingPlayerAction? {
        val apply = !player.out
        val isLastActive = _uiState.value.players.count { !it.out } <= 1
        if (apply && isLastActive) return null

        val selectableIds = if (apply) {
            buildPlayerDisplayModels(_uiState.value.players, _uiState.value.eliminationOrder)
                .map { it.player.id }
                .filter { it != player.id }
        } else {
            emptyList()
        }
        val initialSelection = player.eliminatedBy?.takeIf { it in selectableIds } ?: selectableIds.firstOrNull()

        return PendingPlayerAction(
            playerId = player.id,
            actionType = PlayerActionType.OUT,
            apply = apply,
            selectablePlayerIds = selectableIds,
            selectedPlayerId = if (apply) initialSelection else null,
            allowUnassignedSelection = apply
        )
    }

    /** The Pay-Out dialog's breakdown for [player], straight from the settlement. */
    private fun payOutAction(player: PlayerData): PendingPlayerAction {
        val apply = !player.payedOut
        val owed = settlement?.forPlayer(player.id)?.takeIf { apply }
        return PendingPlayerAction(
            playerId = player.id,
            actionType = PlayerActionType.PAYED_OUT,
            apply = apply,
            payoutAmountCents = owed?.netCents ?: 0L,
            buyInPayoutCents = owed?.prizeCents ?: 0L,
            buyInCostCents = owed?.costCents ?: 0L,
            knockoutBonusCents = owed?.knockoutBountyCents ?: 0L,
            kingsBountyCents = owed?.kingsBountyCents ?: 0L,
            unclaimedBountyCents = owed?.unclaimedBountyCents ?: 0L,
            knockoutCount = owed?.knockouts ?: 0
        )
    }

    private fun purchaseAction(
        player: PlayerData,
        actionType: PlayerActionType,
        enabled: Boolean,
        currentCount: Int
    ): PendingPlayerAction? {
        if (!enabled) return null
        val baseCount = currentCount.coerceAtLeast(0)
        return PendingPlayerAction(
            playerId = player.id,
            actionType = actionType,
            apply = true,
            baseCount = baseCount,
            targetCount = (baseCount + 1).coerceAtMost(MAX_PURCHASE_COUNT)
        )
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
        _uiState.update { state ->
            state.copy(players = state.players.map { if (it.id == playerId) it.copy(rebuys = sanitized) else it })
        }
        bankPreferences.savePlayerRebuys(playerId, sanitized)
        updateCalculations()
    }

    private fun updatePlayerAddons(playerId: Int, addons: Int) {
        val sanitized = addons.coerceIn(0, MAX_PURCHASE_COUNT)
        _uiState.update { state ->
            state.copy(players = state.players.map { if (it.id == playerId) it.copy(addons = sanitized) else it })
        }
        bankPreferences.savePlayerAddons(playerId, sanitized)
        updateCalculations()
    }

    private fun updatePlayerPayment(
        playerId: Int,
        updateFunction: (PlayerData) -> PlayerData,
        afterUpdate: ((PlayerData) -> Unit)? = null
    ) {
        val current = _uiState.value.players.firstOrNull { it.id == playerId } ?: return
        val updated = updateFunction(current)
        _uiState.update { state ->
            state.copy(players = state.players.map { if (it.id == playerId) updated else it })
        }
        // State first, then preferences: a write bumps the Bank revision, and the reload it
        // triggers must find nothing to change.
        bankPreferences.savePlayerBuyInStatus(playerId, updated.buyIn)
        bankPreferences.savePlayerOutStatus(playerId, updated.out)
        bankPreferences.savePlayerPayedOutStatus(playerId, updated.payedOut)
        bankPreferences.savePlayerEliminatedBy(playerId, updated.eliminatedBy)
        afterUpdate?.invoke(updated)
        updateCalculations()
    }

    private fun updateEliminationOrder(playerId: Int, isOut: Boolean) {
        val totalPlayers = _uiState.value.players.size
        val currentOrder = bankPreferences.getEliminationOrder()
            .filter { it in 1..totalPlayers }
            .distinct()
        val filteredOrder = currentOrder.filterNot { it == playerId }
        val nextOrder = if (isOut) filteredOrder + playerId else filteredOrder

        _uiState.update { it.copy(eliminationOrder = nextOrder) }
        bankPreferences.saveEliminationOrder(nextOrder)
    }

    private fun PlayerData.toBankPlayer() = BankPlayer(
        id = id,
        boughtIn = buyIn,
        paidOut = payedOut,
        rebuys = rebuys,
        addOns = addons,
        eliminatedBy = eliminatedBy
    )

    /** Recomputes every amount from one settlement of the current state. */
    private fun updateCalculations() {
        val state = _uiState.value
        val config = tournamentPreferences.getCurrentTournamentConfig()
        val result = settleTournament(
            players = state.players.map { it.toBankPlayer() },
            eliminationOrder = state.eliminationOrder,
            money = config.money,
            weights = config.payoutWeights,
            rounding = config.payoutRounding
        )
        settlement = result

        _uiState.update {
            it.copy(
                pool = result.pool,
                totalPaidInCents = result.paidInCents,
                totalPaidOutCents = result.paidOutCents,
                totalRebuyCount = state.players.sumOf { player -> player.rebuys },
                totalAddonCount = state.players.sumOf { player -> player.addons },
                activePlayers = state.players.count { player -> !player.out },
                payedOutCount = state.players.count { player -> player.payedOut },
                money = config.money,
                knockoutCounts = result.players.filter { owed -> owed.knockouts > 0 }
                    .associate { owed -> owed.playerId to owed.knockouts },
                payoutEligiblePlayerIds = result.players.filter { owed -> owed.winningsCents > 0L }
                    .map { owed -> owed.playerId }
                    .toSet(),
                payoutTable = result.payoutTable,
                payoutSettings = tournamentPreferences.getPayoutSettings(),
                placeByPlayer = result.standings.placeByPlayer
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
        // Reset bank preferences (player names and payment states only)
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

    private fun updatePayoutSettings(settings: PayoutSettings) {
        tournamentPreferences.setPayoutSettings(settings)
        updateCalculations()
        hideWeightsDialog()
    }

    private fun showWeightsDialog() {
        _uiState.update { it.copy(showWeightsDialog = true) }
    }

    private fun hideWeightsDialog() {
        _uiState.update { it.copy(showWeightsDialog = false) }
    }

    private fun showPoolSummaryDialog() {
        _uiState.update { it.copy(showPoolSummaryDialog = true) }
    }

    private fun hidePoolSummaryDialog() {
        _uiState.update { it.copy(showPoolSummaryDialog = false) }
    }

    private fun isInDefaultState(): Boolean {
        val currentPlayerCount = _uiState.value.players.size
        return bankPreferences.isInDefaultState(currentPlayerCount)
    }
}
