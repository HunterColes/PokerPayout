package com.huntercoles.fatline.settingsfeature.presentation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import com.huntercoles.fatline.core.preferences.ThemePreferences
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePreferences: ThemePreferences
) : ViewModel() {

    val isDarkModeEnabled: Flow<Boolean> = themePreferences.darkModeEnabled

    fun toggleDarkMode() {
        themePreferences.toggleDarkMode()
    }
    
    fun setDarkMode(enabled: Boolean) {
        themePreferences.setDarkMode(enabled)
    }
}
