package com.huntercoles.pokerpayout.tools.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.core.design.ChipDenominations
import com.huntercoles.pokerpayout.core.preferences.ChipCalculatorPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.utils.ChipDistributionCurve
import com.huntercoles.pokerpayout.core.utils.ChipDistributionOptimizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChipBreakdown(
    val value: Int,
    val count: Int,
    val color: androidx.compose.ui.graphics.Color,
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
    val smallestChip: Int = 10
)

@HiltViewModel
class ChipCalculatorViewModel @Inject constructor(
    private val chipPreferences: ChipCalculatorPreferences,
    private val tournamentPreferences: TournamentPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChipCalculatorUiState())
    val uiState: StateFlow<ChipCalculatorUiState> = _uiState.asStateFlow()

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
            chipPreferences.selectedCurve.collect { curveName ->
                val curve = ChipDistributionCurve.getCurveByName(curveName) ?: ChipDistributionCurve.LinearSteep
                _uiState.update { it.copy(selectedCurve = curve) }
            }
        }

        viewModelScope.launch {
            chipPreferences.denominationCount.collect { count ->
                _uiState.update { it.copy(denominationCount = count) }
            }
        }

        viewModelScope.launch {
            chipPreferences.chipBreakdown.collect { breakdown ->
                if (breakdown.isNotEmpty()) {
                    val fitScore = chipPreferences.getFitScore()
                    val totalPhysicalChips = chipPreferences.getTotalPhysicalChips()
                    
                    // Convert breakdown to ChipBreakdown objects
                    val chipBreakdowns = breakdown.map { (value, count) ->
                        val chipInfo = ChipDenominations.getChipByValue(value)
                        ChipBreakdown(
                            value = value,
                            count = count,
                            color = chipInfo?.color ?: androidx.compose.ui.graphics.Color.Gray,
                            name = chipInfo?.name ?: "Chip"
                        )
                    }
                    
                    _uiState.update { 
                        it.copy(
                            chipBreakdown = chipBreakdowns,
                            fitScore = fitScore,
                            totalPhysicalChips = totalPhysicalChips
                        )
                    }
                }
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
        chipPreferences.setCustomTotalChips(chips)
        _uiState.update { it.copy(totalChips = chips, customTotalChips = chips) }
    }

    fun updateCurveSelection(curve: ChipDistributionCurve) {
        _uiState.update { it.copy(selectedCurve = curve) }
        chipPreferences.setSelectedCurve(curve.displayName)
    }

    fun updateDenominationCount(count: Int) {
        val validCount = count.coerceIn(3, 8) // Min 3, max 8 denominations
        _uiState.update { it.copy(denominationCount = validCount) }
        chipPreferences.setDenominationCount(validCount)
    }

    fun calculateChipBreakdown() {
        val total = _uiState.value.totalChips
        val smallestChip = tournamentPreferences.getSmallestChip()
        val denomCount = _uiState.value.denominationCount
        val curve = _uiState.value.selectedCurve
        
        // Use curve-based optimization
        val result = ChipDistributionOptimizer.optimize(
            targetValue = total,
            smallestChip = smallestChip,
            denominationCount = denomCount,
            curve = curve
        )
        
        // Convert to ChipBreakdown with colors
        val breakdown = result.denominations.zip(result.quantities).map { (value, count) ->
            val chipInfo = ChipDenominations.getChipByValue(value)
            ChipBreakdown(
                value = value,
                count = count,
                color = chipInfo?.color ?: androidx.compose.ui.graphics.Color.Gray,
                name = chipInfo?.name ?: "Chip"
            )
        }
        
        _uiState.update {
            it.copy(
                chipBreakdown = breakdown,
                fitScore = result.fitScore,
                totalPhysicalChips = result.totalChips
            )
        }
        
        // Save results to preferences
        val breakdownPairs = result.denominations.zip(result.quantities)
        chipPreferences.setChipBreakdown(breakdownPairs)
        chipPreferences.setFitScore(result.fitScore)
        chipPreferences.setTotalPhysicalChips(result.totalChips)
    }

    fun showResetDialog() {
        if (!chipPreferences.isInDefaultState()) {
            _uiState.update { it.copy(showResetDialog = true) }
        }
    }

    fun hideResetDialog() {
        _uiState.update { it.copy(showResetDialog = false) }
    }

    fun confirmReset() {
        chipPreferences.resetAllData()
        val tournamentStartingChips = tournamentPreferences.getStartingChips()
        _uiState.update { 
            it.copy(
                totalChips = tournamentStartingChips,
                customTotalChips = 0,
                chipBreakdown = emptyList(),
                showResetDialog = false,
                selectedCurve = ChipDistributionCurve.LinearSteep,
                denominationCount = 5,
                fitScore = null,
                totalPhysicalChips = 0
            )
        }
    }

    private fun loadSavedState() {
        val customTotal = chipPreferences.getCustomTotalChips()
        val selectedCurveName = chipPreferences.getSelectedCurve()
        val selectedCurve = ChipDistributionCurve.getCurveByName(selectedCurveName) ?: ChipDistributionCurve.LinearSteep
        val denominationCount = chipPreferences.getDenominationCount()
        
        _uiState.update { 
            it.copy(
                customTotalChips = customTotal,
                totalChips = if (customTotal > 0) customTotal else it.totalChips,
                selectedCurve = selectedCurve,
                denominationCount = denominationCount
            )
        }
    }
}
