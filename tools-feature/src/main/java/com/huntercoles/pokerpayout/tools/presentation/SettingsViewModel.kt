package com.huntercoles.pokerpayout.tools.presentation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import com.huntercoles.pokerpayout.core.preferences.ThemePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePreferences: ThemePreferences
) : ViewModel() {

    val isDarkModeEnabled: Flow<Boolean> = themePreferences.darkModeEnabled

    private val _showRulesPopup = MutableStateFlow(false)
    val showRulesPopup: StateFlow<Boolean> = _showRulesPopup.asStateFlow()

    fun toggleDarkMode() {
        themePreferences.toggleDarkMode()
    }
    
    fun setDarkMode(enabled: Boolean) {
        themePreferences.setDarkMode(enabled)
    }

    fun showRulesPopup() {
        _showRulesPopup.value = true
    }

    fun hideRulesPopup() {
        _showRulesPopup.value = false
    }
}
