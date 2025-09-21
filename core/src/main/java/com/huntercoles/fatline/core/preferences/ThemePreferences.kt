package com.huntercoles.fatline.core.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
    
    private val _darkModeEnabled = MutableStateFlow(getDarkModePreference())
    val darkModeEnabled: Flow<Boolean> = _darkModeEnabled.asStateFlow()
    
    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(DARK_MODE_KEY, enabled).apply()
        _darkModeEnabled.value = enabled
    }
    
    fun toggleDarkMode() {
        setDarkMode(!_darkModeEnabled.value)
    }
    
    fun getDarkModePreference(): Boolean {
        return prefs.getBoolean(DARK_MODE_KEY, false)
    }
    
    companion object {
        private const val DARK_MODE_KEY = "dark_mode_enabled"
    }
}

@Composable
fun ThemePreferences.isDarkTheme(): Boolean {
    val darkModeEnabled by darkModeEnabled.collectAsState(initial = getDarkModePreference())
    val systemDarkTheme = isSystemInDarkTheme()
    
    // For now, just return the preference value directly
    return darkModeEnabled
}
