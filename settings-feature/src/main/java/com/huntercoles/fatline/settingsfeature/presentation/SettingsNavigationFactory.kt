package com.huntercoles.fatline.settingsfeature.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.huntercoles.fatline.settingsfeature.presentation.composable.SettingsRoute
import com.huntercoles.fatline.core.navigation.NavigationDestination.Settings
import com.huntercoles.fatline.core.navigation.NavigationFactory
import javax.inject.Inject

class SettingsNavigationFactory @Inject constructor() : NavigationFactory {

    override fun create(builder: NavGraphBuilder) {
        builder.composable<Settings> {
            SettingsRoute()
        }
    }
}
