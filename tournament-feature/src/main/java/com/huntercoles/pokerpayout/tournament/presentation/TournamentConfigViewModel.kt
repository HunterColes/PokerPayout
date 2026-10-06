package com.huntercoles.pokerpayout.tournament.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.core.domain.model.PayoutPlaces
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PoolBreakdown
import com.huntercoles.pokerpayout.core.domain.model.Standings
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
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
class TournamentConfigViewModel @Inject constructor(
    private val calculatePayoutsUseCase: CalculatePayoutsUseCase,
    private val tournamentPreferences: TournamentPreferences,
    private val timerPreferences: TimerPreferences,
    private val bankPreferences: BankPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(TournamentConfigUiState())
    val uiState: StateFlow<TournamentConfigUiState> = _uiState.asStateFlow()

    init {
        // Load all tournament configuration from preferences
        loadTournamentConfiguration()

        // Listen for tournament lock state changes
        viewModelScope.launch {
            tournamentPreferences.tournamentLocked.collect { isLocked ->
                _uiState.update { it.copy(isTournamentLocked = isLocked) }
            }
        }

        // Listen for config expanded state changes
        viewModelScope.launch {
            tournamentPreferences.isConfigExpanded.collect { isExpanded ->
                _uiState.update { it.copy(isConfigExpanded = isExpanded) }
            }
        }

        // The pool and the payout table follow every settings change (including the Bank's
        // weights editor) and every purchase or knockout recorded in the Bank.
        viewModelScope.launch {
            tournamentPreferences.config.collect { refreshPayouts() }
        }
        viewModelScope.launch {
            bankPreferences.revision.collect { refreshPayouts() }
        }
    }

    fun acceptIntent(intent: TournamentConfigIntent) {
        when (intent) {
            is TournamentConfigIntent.UpdatePlayerCount -> updatePlayerCount(intent.count)
            is TournamentConfigIntent.UpdateBuyIn -> updateSettings { setBuyInCents(intent.cents) }
            is TournamentConfigIntent.UpdateFoodPerPlayer -> updateSettings { setFoodCents(intent.cents) }
            is TournamentConfigIntent.UpdateBountyPerPlayer -> updateSettings { setBountyCents(intent.cents) }
            is TournamentConfigIntent.UpdateRebuyAmount -> updatePurchaseAmount(PurchaseKind.REBUY, intent.cents)
            is TournamentConfigIntent.UpdateAddOnAmount -> updatePurchaseAmount(PurchaseKind.ADD_ON, intent.cents)
            is TournamentConfigIntent.CommitRebuyAmount ->
                commitPurchaseAmount(PurchaseKind.REBUY, intent.cents, intent.centsBeforeEdit)
            is TournamentConfigIntent.CommitAddOnAmount ->
                commitPurchaseAmount(PurchaseKind.ADD_ON, intent.cents, intent.centsBeforeEdit)
            TournamentConfigIntent.ConfirmClearPurchases -> confirmClearPurchases()
            TournamentConfigIntent.DismissClearPurchases -> keepPurchases()
            is TournamentConfigIntent.UpdateWeights -> updateWeights(intent.weights)
            is TournamentConfigIntent.UpdatePayoutSettings -> {
                _uiState.update { it.copy(showWeightsEditor = false) }
                updateSettings { setPayoutSettings(intent.settings) }
            }
            is TournamentConfigIntent.ApplyPayoutPreset -> applyPreset(intent.preset, currentPaidPlaces())
            is TournamentConfigIntent.SetPaidPlaces ->
                applyPreset(_uiState.value.payoutPreset ?: PayoutPreset.DEFAULT, intent.places)
            is TournamentConfigIntent.UpdatePayoutRounding -> updateSettings { setPayoutRounding(intent.rounding) }
            TournamentConfigIntent.ShowWeightsEditor -> _uiState.update { it.copy(showWeightsEditor = true) }
            TournamentConfigIntent.HideWeightsEditor -> _uiState.update { it.copy(showWeightsEditor = false) }
            is TournamentConfigIntent.ToggleConfigExpanded -> toggleConfigExpanded(intent.isExpanded)
            is TournamentConfigIntent.ToggleBlindConfigExpanded -> toggleBlindConfigExpanded(intent.isExpanded)
            is TournamentConfigIntent.UpdateGameDurationHours -> updateGameDurationHours(intent.hours)
            is TournamentConfigIntent.UpdateRoundLength -> updateRoundLength(intent.minutes)
            is TournamentConfigIntent.UpdateSmallestChip -> updateSmallestChip(intent.chip)
            is TournamentConfigIntent.UpdateStartingChips -> updateStartingChips(intent.chips)
            is TournamentConfigIntent.UpdateSelectedPanel -> updateSelectedPanel(intent.panel)
            TournamentConfigIntent.ShowResetDialog -> showResetDialog()
            TournamentConfigIntent.HideResetDialog -> hideResetDialog()
            TournamentConfigIntent.ConfirmReset -> confirmReset()
        }
    }

    private fun loadTournamentConfiguration() {
        _uiState.update {
            it.copy(
                gameDurationHours = tournamentPreferences.getGameDurationHours(),
                roundLengthMinutes = tournamentPreferences.getRoundLengthMinutes(),
                smallestChip = tournamentPreferences.getSmallestChip(),
                startingChips = tournamentPreferences.getStartingChips(),
                selectedPanel = tournamentPreferences.getSelectedPanel(),
                isConfigExpanded = tournamentPreferences.getIsConfigExpanded()
            )
        }
        bankPreferences.removePlayersAbove(tournamentPreferences.getPlayerCount())
        refreshPayouts()
    }

    /** Recomputes the pool, the payout table and who holds each decided place. */
    private fun refreshPayouts() {
        val config = tournamentPreferences.getCurrentTournamentConfig()
        val rebuys = bankPreferences.getTotalRebuyCount()
        val addOns = bankPreferences.getTotalAddonCount()
        val pool = PoolBreakdown.withRecordedPurchases(
            config.money,
            config.numPlayers,
            bankPreferences.getRecordedRebuyCents(),
            bankPreferences.getRecordedAddOnCents(),
        )
        val table = calculatePayoutsUseCase(
            prizePoolCents = pool.prizePoolCents,
            weights = config.payoutWeights,
            playerCount = config.numPlayers,
            rounding = config.payoutRounding
        )
        val standings = Standings((1..config.numPlayers).toList(), bankPreferences.getEliminationOrder())
        val placeNames = table.places.mapNotNull { row ->
            standings.playerAt(row.place)?.let { playerId -> row.place to bankPreferences.getPlayerName(playerId) }
        }.toMap()

        _uiState.update {
            it.copy(
                config = config,
                pool = pool,
                payoutTable = table,
                payoutPreset = tournamentPreferences.getPayoutPreset(),
                recommendedPlaces = PayoutPlaces.recommended(config.numPlayers),
                placeNames = placeNames,
                rebuyPurchases = rebuys,
                addOnPurchases = addOns
            )
        }
    }

    private fun updatePlayerCount(count: Int) {
        tournamentPreferences.setPlayerCount(count)
        // Removed players are gone for good, in the Bank too (they used to come back after a restart).
        bankPreferences.removePlayersAbove(count)
        refreshPayouts()
    }

    private inline fun updateSettings(write: TournamentPreferences.() -> Unit) {
        tournamentPreferences.write()
        refreshPayouts()
    }

    private fun purchaseCount(kind: PurchaseKind): Int = when (kind) {
        PurchaseKind.REBUY -> _uiState.value.rebuyPurchases
        PurchaseKind.ADD_ON -> _uiState.value.addOnPurchases
    }

    private fun savedAmount(kind: PurchaseKind): Long = when (kind) {
        PurchaseKind.REBUY -> tournamentPreferences.getMoneySettings().rebuyCents
        PurchaseKind.ADD_ON -> tournamentPreferences.getMoneySettings().addOnCents
    }

    private fun saveAmount(kind: PurchaseKind, cents: Long) = updateSettings {
        when (kind) {
            PurchaseKind.REBUY -> setRebuyCents(cents)
            PurchaseKind.ADD_ON -> setAddOnCents(cents)
        }
    }

    /**
     * An amount typed into the Rebuy or Add-on field. A zero while purchases are recorded is not
     * saved: it is usually the field being cleared to type a new amount. Leaving the field at zero
     * asks first ([commitPurchaseAmount]).
     */
    private fun updatePurchaseAmount(kind: PurchaseKind, cents: Long) {
        if (cents > 0L || purchaseCount(kind) == 0) {
            saveAmount(kind, cents)
        }
    }

    /**
     * The Rebuy or Add-on field was left at [cents]. Leaving it at zero while purchases are recorded
     * asks first; "Keep" puts back [centsBeforeEdit], since backspacing "15" saved "1" on the way.
     */
    private fun commitPurchaseAmount(kind: PurchaseKind, cents: Long, centsBeforeEdit: Long) {
        val count = purchaseCount(kind)
        if (cents == 0L && count > 0) {
            val kept = centsBeforeEdit.takeIf { it > 0L } ?: savedAmount(kind)
            _uiState.update { it.copy(purchaseClearPrompt = PurchaseClearPrompt(kind, count, keptAmountCents = kept)) }
        } else {
            updatePurchaseAmount(kind, cents)
        }
    }

    /** "Keep": the purchases stay and so does the amount from before the edit. */
    private fun keepPurchases() {
        val prompt = _uiState.value.purchaseClearPrompt ?: return
        _uiState.update { it.copy(purchaseClearPrompt = null) }
        if (prompt.keptAmountCents > 0L && prompt.keptAmountCents != savedAmount(prompt.kind)) {
            saveAmount(prompt.kind, prompt.keptAmountCents)
        }
    }

    /** The user confirmed: the amount goes to zero and the recorded purchases are cleared. */
    private fun confirmClearPurchases() {
        val prompt = _uiState.value.purchaseClearPrompt ?: return
        when (prompt.kind) {
            PurchaseKind.REBUY -> bankPreferences.clearAllRebuys()
            PurchaseKind.ADD_ON -> bankPreferences.clearAllAddons()
        }
        _uiState.update { it.copy(purchaseClearPrompt = null) }
        saveAmount(prompt.kind, 0L)
    }

    private fun currentPaidPlaces(): Int =
        _uiState.value.paidPlaces.takeIf { it > 0 } ?: PayoutPlaces.recommended(_uiState.value.playerCount)

    private fun applyPreset(preset: PayoutPreset, places: Int) {
        val maxPlaces = PayoutPlaces.maxFor(_uiState.value.playerCount)
        tournamentPreferences.setPayoutPreset(preset, places.coerceIn(1, maxPlaces))
        refreshPayouts()
    }

    private fun updateWeights(weights: List<Int>) {
        tournamentPreferences.setPayoutWeights(weights)
        _uiState.update { it.copy(showWeightsEditor = false) }
        refreshPayouts()
    }

    private fun toggleConfigExpanded(isExpanded: Boolean) {
        tournamentPreferences.setIsConfigExpanded(isExpanded)
        _uiState.update { it.copy(isConfigExpanded = isExpanded) }
    }

    private fun showResetDialog() {
        // Only show dialog if not already in default state
        if (!isInDefaultState()) {
            _uiState.update { it.copy(showResetDialog = true) }
        }
    }

    private fun hideResetDialog() {
        _uiState.update { it.copy(showResetDialog = false) }
    }

    private fun isInDefaultState(): Boolean {
        // Also ensure UI-only blind fields match their defaults so changing them enables Reset
        val defaultUi = TournamentConfigUiState()
        val ui = _uiState.value
        return tournamentPreferences.isInDefaultState() &&
            timerPreferences.isInDefaultState() &&
            ui.gameDurationHours == defaultUi.gameDurationHours &&
            ui.roundLengthMinutes == defaultUi.roundLengthMinutes &&
            ui.smallestChip == defaultUi.smallestChip &&
            ui.startingChips == defaultUi.startingChips
            // Note: selectedPanel is already checked in tournamentPreferences.isInDefaultState()
    }

    private fun confirmReset() {
        resetAllData()
        _uiState.update { it.copy(showResetDialog = false) }
    }

    private fun resetAllData() {
        // Preserve current selected panel
        val currentSelectedPanel = _uiState.value.selectedPanel

        // The reset sets the rebuy and add-on amounts to zero; the dialog said the purchases
        // recorded at the old amounts go too, so the Bank never holds purchases worth nothing.
        if (_uiState.value.rebuyPurchases > 0) bankPreferences.clearAllRebuys()
        if (_uiState.value.addOnPurchases > 0) bankPreferences.clearAllAddons()

        tournamentPreferences.resetAllTournamentData()
        timerPreferences.resetAllTimerData()

        // Restore the selected panel to what it was before reset
        tournamentPreferences.setSelectedPanel(currentSelectedPanel)

        // Reload tournament configuration from preferences
        loadTournamentConfiguration()

        // Reset UI-only blind-related fields to their defaults. Use timer preference for duration.
        val defaultUi = TournamentConfigUiState()
        val defaultHours = (timerPreferences.getGameDurationMinutes() / MINUTES_PER_HOUR).coerceAtLeast(1)
        _uiState.update {
            it.copy(
                gameDurationHours = defaultHours,
                roundLengthMinutes = defaultUi.roundLengthMinutes,
                smallestChip = defaultUi.smallestChip,
                startingChips = defaultUi.startingChips,
                selectedPanel = currentSelectedPanel // Preserve the selected panel
            )
        }
    }

    private fun toggleBlindConfigExpanded(isExpanded: Boolean) {
        _uiState.update { it.copy(isBlindConfigExpanded = isExpanded) }
    }

    private fun updateGameDurationHours(hours: Int) {
        _uiState.update { it.copy(gameDurationHours = hours) }
        tournamentPreferences.setGameDurationHours(hours)
    }

    private fun updateRoundLength(minutes: Int) {
        _uiState.update { it.copy(roundLengthMinutes = minutes) }
        tournamentPreferences.setRoundLengthMinutes(minutes)
    }

    private fun updateSmallestChip(chip: Int) {
        _uiState.update { it.copy(smallestChip = chip) }
        tournamentPreferences.setSmallestChip(chip)
    }

    private fun updateStartingChips(chips: Int) {
        _uiState.update { it.copy(startingChips = chips) }
        tournamentPreferences.setStartingChips(chips)
    }

    private fun updateSelectedPanel(panel: String) {
        _uiState.update { it.copy(selectedPanel = panel) }
        tournamentPreferences.setSelectedPanel(panel)
    }

    private companion object {
        const val MINUTES_PER_HOUR = 60
    }
}
