package com.huntercoles.pokerpayout.core.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChipCalculatorPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("chip_calculator_prefs", Context.MODE_PRIVATE)

    private val _customTotalChips = MutableStateFlow(getCustomTotalChips())
    val customTotalChips: Flow<Int> = _customTotalChips.asStateFlow()

    private val _selectedCurve = MutableStateFlow(getSelectedCurve())
    val selectedCurve: Flow<String> = _selectedCurve.asStateFlow()

    private val _denominationCount = MutableStateFlow(getDenominationCount())
    val denominationCount: Flow<Int> = _denominationCount.asStateFlow()

    private val _chipBreakdown = MutableStateFlow(getChipBreakdown())
    val chipBreakdown: Flow<List<Pair<Int, Int>>> = _chipBreakdown.asStateFlow()

    fun getCustomTotalChips(): Int {
        return prefs.getInt(CUSTOM_TOTAL_CHIPS_KEY, 0) // 0 means use tournament config value
    }

    fun setCustomTotalChips(total: Int) {
        prefs.edit().putInt(CUSTOM_TOTAL_CHIPS_KEY, total).apply()
        _customTotalChips.value = total
    }

    fun clearCustomTotal() {
        prefs.edit().remove(CUSTOM_TOTAL_CHIPS_KEY).apply()
        _customTotalChips.value = 0
    }

    fun resetAllData() {
        prefs.edit().clear().apply()
        _customTotalChips.value = 0
        _selectedCurve.value = getSelectedCurve()
        _denominationCount.value = getDenominationCount()
        _chipBreakdown.value = getChipBreakdown()
    }

    fun isInDefaultState(): Boolean {
        return getCustomTotalChips() == 0
    }

    fun getSelectedCurve(): String {
        return prefs.getString(SELECTED_CURVE_KEY, "Linear Steep") ?: "Linear Steep"
    }

    fun setSelectedCurve(curveName: String) {
        prefs.edit().putString(SELECTED_CURVE_KEY, curveName).apply()
        _selectedCurve.value = curveName
    }

    fun getDenominationCount(): Int {
        return prefs.getInt(DENOMINATION_COUNT_KEY, 5)
    }

    fun setDenominationCount(count: Int) {
        prefs.edit().putInt(DENOMINATION_COUNT_KEY, count).apply()
        _denominationCount.value = count
    }

    fun getChipBreakdown(): List<Pair<Int, Int>> {
        val breakdownString = prefs.getString(CHIP_BREAKDOWN_KEY, "") ?: ""
        if (breakdownString.isEmpty()) return emptyList()
        
        return breakdownString.split(";").mapNotNull { pairString ->
            val parts = pairString.split(",")
            if (parts.size == 2) {
                val denom = parts[0].toIntOrNull()
                val qty = parts[1].toIntOrNull()
                if (denom != null && qty != null) denom to qty else null
            } else null
        }
    }

    fun setChipBreakdown(breakdown: List<Pair<Int, Int>>) {
        val breakdownString = breakdown.joinToString(";") { "${it.first},${it.second}" }
        prefs.edit().putString(CHIP_BREAKDOWN_KEY, breakdownString).apply()
        _chipBreakdown.value = breakdown
    }

    fun getFitScore(): Double? {
        val score = prefs.getFloat(FIT_SCORE_KEY, -1f)
        return if (score >= 0) score.toDouble() else null
    }

    fun setFitScore(score: Double?) {
        if (score != null) {
            prefs.edit().putFloat(FIT_SCORE_KEY, score.toFloat()).apply()
        } else {
            prefs.edit().remove(FIT_SCORE_KEY).apply()
        }
    }

    fun getTotalPhysicalChips(): Int {
        return prefs.getInt(TOTAL_PHYSICAL_CHIPS_KEY, 0)
    }

    fun setTotalPhysicalChips(total: Int) {
        prefs.edit().putInt(TOTAL_PHYSICAL_CHIPS_KEY, total).apply()
    }

    companion object {
        private const val CUSTOM_TOTAL_CHIPS_KEY = "custom_total_chips"
        private const val SELECTED_CURVE_KEY = "selected_curve"
        private const val DENOMINATION_COUNT_KEY = "denomination_count"
        private const val CHIP_BREAKDOWN_KEY = "chip_breakdown"
        private const val FIT_SCORE_KEY = "fit_score"
        private const val TOTAL_PHYSICAL_CHIPS_KEY = "total_physical_chips"
    }
}
