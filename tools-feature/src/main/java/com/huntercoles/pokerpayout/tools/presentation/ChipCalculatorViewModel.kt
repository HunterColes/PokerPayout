package com.huntercoles.pokerpayout.tools.presentation

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.core.coroutines.DefaultDispatcher
import com.huntercoles.pokerpayout.core.design.ChipDenominations
import com.huntercoles.pokerpayout.core.preferences.ChipCalculatorPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.utils.ChipDistributionCurve
import com.huntercoles.pokerpayout.core.utils.ChipDistributionOptimizer
import com.huntercoles.pokerpayout.core.utils.ChipDistributionOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ChipBreakdown(
    val value: Int,
    val count: Int,
    val color: Color,
    val name: String
)

data class ChipCalculatorUiState(
    val totalChips: Int = 5000,
    val customTotalChips: Int = 0, // 0 means use tournament config
    val chipBreakdown: List<ChipBreakdown> = emptyList(),
    val showResetDialog: Boolean = false,
    val selectedCurve: ChipDistributionCurve = ChipDistributionCurve.LinearSteep,
    val denominationCount: Int = 5,
    val fitScore: Double? = null,
    val totalPhysicalChips: Int = 0,
    val smallestChip: Int = 10,
    /** True while Generate is computing a breakdown. */
    val isCalculating: Boolean = false,
    /** One plain line for the user: why there is no breakdown, or a note about an adjusted input. */
    val message: String? = null
)

@HiltViewModel
class ChipCalculatorViewModel @Inject constructor(
    private val chipPreferences: ChipCalculatorPreferences,
    private val tournamentPreferences: TournamentPreferences,
    @DefaultDispatcher private val computeDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChipCalculatorUiState())
    val uiState: StateFlow<ChipCalculatorUiState> = _uiState.asStateFlow()

    private var calculation: Job? = null

    init {
        loadSavedState()
        observeTournamentStartingChips()
        observePreferences()
    }

    private fun observeTournamentStartingChips() {
        viewModelScope.launch {
            // Get initial starting chips value
            val startingChips = tournamentPreferences.getStartingChips()
            val smallestChip = tournamentPreferences.getSmallestChip()
            if (_uiState.value.customTotalChips == 0) {
                _uiState.update { it.copy(totalChips = startingChips, smallestChip = smallestChip) }
            } else {
                _uiState.update { it.copy(smallestChip = smallestChip) }
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            chipPreferences.settings.collect { settings ->
                _uiState.update { it.copy(selectedCurve = settings.shape, denominationCount = settings.maxColours) }
            }
        }
    }

    fun setTournamentStartingChips(chips: Int) {
        // Update the tournament config value, but only use it if no custom value is set
        if (_uiState.value.customTotalChips == 0) {
            _uiState.update { it.copy(totalChips = chips) }
        }
    }

    fun updateTotalChips(chips: Int) {
        chipPreferences.setStackOverride(chips)
        _uiState.update { it.copy(totalChips = chips, customTotalChips = chips) }
    }

    fun updateCurveSelection(curve: ChipDistributionCurve) {
        _uiState.update { it.copy(selectedCurve = curve) }
        chipPreferences.setShape(curve)
    }

    fun updateDenominationCount(count: Int) {
        val validCount = count.coerceIn(3, 8) // Min 3, max 8 denominations
        _uiState.update { it.copy(denominationCount = validCount) }
        chipPreferences.setMaxColours(validCount)
    }

    fun updateSmallestChip(chip: Int) {
        val validChip = chip.coerceIn(1, 100) // Reasonable range
        _uiState.update { it.copy(smallestChip = validChip) }
        // Note: smallest chip is synced from tournament preferences, so we might not save it separately
    }

    /**
     * Compute the breakdown off the main thread. The screen shows a loading state meanwhile, then
     * either the breakdown or one message line explaining why there isn't one.
     */
    fun calculateChipBreakdown() {
        val inputs = _uiState.value
        calculation?.cancel()
        _uiState.update { it.copy(isCalculating = true, message = null) }
        calculation = viewModelScope.launch {
            val outcome = withContext(computeDispatcher) {
                ChipDistributionOptimizer.optimize(
                    targetValue = inputs.totalChips,
                    smallestChip = inputs.smallestChip,
                    denominationCount = inputs.denominationCount,
                    curve = inputs.selectedCurve
                )
            }
            when (outcome) {
                is ChipDistributionOutcome.Success -> {
                    val result = outcome.distribution
                    val pairs = result.denominations.zip(result.quantities)
                    // Every displayed number comes from this one result.
                    _uiState.update {
                        it.copy(
                            chipBreakdown = pairs.map { (value, count) -> chipBreakdown(value, count) },
                            fitScore = result.fitScore,
                            totalPhysicalChips = result.totalChips,
                            isCalculating = false,
                            message = outcome.note
                        )
                    }
                }
                is ChipDistributionOutcome.Failure -> {
                    _uiState.update {
                        it.copy(
                            chipBreakdown = emptyList(),
                            fitScore = null,
                            totalPhysicalChips = 0,
                            isCalculating = false,
                            message = outcome.message
                        )
                    }
                }
            }
        }
    }

    fun showResetDialog() {
        if (chipPreferences.current().stackOverride != null || _uiState.value.chipBreakdown.isNotEmpty()) {
            _uiState.update { it.copy(showResetDialog = true) }
        }
    }

    fun hideResetDialog() {
        _uiState.update { it.copy(showResetDialog = false) }
    }

    fun confirmReset() {
        calculation?.cancel()
        chipPreferences.resetAllData()
        val tournamentStartingChips = tournamentPreferences.getStartingChips()
        val tournamentSmallestChip = tournamentPreferences.getSmallestChip()
        _uiState.update {
            it.copy(
                totalChips = tournamentStartingChips,
                customTotalChips = 0,
                smallestChip = tournamentSmallestChip,
                chipBreakdown = emptyList(),
                showResetDialog = false,
                selectedCurve = ChipDistributionCurve.LinearSteep,
                denominationCount = 5,
                fitScore = null,
                totalPhysicalChips = 0,
                isCalculating = false,
                message = null
            )
        }
        // Removed auto-calculation - user must manually generate
    }

    private fun loadSavedState() {
        // Interim, until the chip set screen replaces this one: the breakdown is no longer saved.
        val settings = chipPreferences.current()
        val customTotal = settings.stackOverride ?: 0
        val selectedCurve = settings.shape
        val denominationCount = settings.maxColours
        val savedBreakdown = emptyList<Pair<Int, Int>>()

        _uiState.update {
            it.copy(
                customTotalChips = customTotal,
                totalChips = if (customTotal > 0) customTotal else it.totalChips,
                selectedCurve = selectedCurve,
                denominationCount = denominationCount,
                chipBreakdown = savedBreakdown.map { (value, count) -> chipBreakdown(value, count) },
                fitScore = null,
                totalPhysicalChips = savedBreakdown.sumOf { (_, count) -> count }
            )
        }
    }

    private fun chipBreakdown(value: Int, count: Int): ChipBreakdown {
        val chipInfo = ChipDenominations.getChipByValue(value)
        return ChipBreakdown(
            value = value,
            count = count,
            color = chipInfo?.color ?: Color.Gray,
            name = chipInfo?.name ?: "Chip"
        )
    }
}
