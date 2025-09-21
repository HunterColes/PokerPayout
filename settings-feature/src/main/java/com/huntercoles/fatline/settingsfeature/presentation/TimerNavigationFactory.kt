package com.huntercoles.fatline.settingsfeature.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.huntercoles.fatline.settingsfeature.presentation.composable.TimerRoute
import com.huntercoles.fatline.core.navigation.NavigationDestination.Timer
import com.huntercoles.fatline.core.navigation.NavigationFactory
import javax.inject.Inject

class TimerNavigationFactory @Inject constructor() : NavigationFactory {

    override fun create(builder: NavGraphBuilder) {
        builder.composable<Timer> {
            TimerRoute()
        }
    }
}