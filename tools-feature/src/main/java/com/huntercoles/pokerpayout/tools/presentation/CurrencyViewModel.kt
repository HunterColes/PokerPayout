package com.huntercoles.pokerpayout.tools.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.core.preferences.CurrencyPreferences
import com.huntercoles.pokerpayout.core.utils.AppCurrency
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Currency (S25, PP-114): every currency the app has, each with a sample amount, and the one picked. */
data class CurrencyUiState(
    val picked: AppCurrency = AppCurrency.DEFAULT,
    val choices: List<AppCurrency> = AppCurrency.entries,
)

sealed interface CurrencyIntent {
    data class Pick(val currency: AppCurrency) : CurrencyIntent
}

/**
 * The currency picker. A pick is saved (`currency` in currency_prefs) and shows at once everywhere:
 * every screen, the share texts and the CSV export format through the same [CurrencyPreferences].
 */
@HiltViewModel
class CurrencyViewModel @Inject constructor(
    private val preferences: CurrencyPreferences,
) : ViewModel() {

    val uiState: StateFlow<CurrencyUiState> = preferences.currency
        .map { CurrencyUiState(picked = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = CurrencyUiState(picked = preferences.getCurrency()),
        )

    fun acceptIntent(intent: CurrencyIntent) {
        when (intent) {
            is CurrencyIntent.Pick -> preferences.setCurrency(intent.currency)
        }
    }
}
