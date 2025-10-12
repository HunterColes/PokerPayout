package com.huntercoles.fatline.basicfeature.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.huntercoles.fatline.basicfeature.presentation.composable.PlayScreen
import com.huntercoles.fatline.core.navigation.NavigationDestination
import com.huntercoles.fatline.core.navigation.NavigationFactory
import javax.inject.Inject

class PlayNavigationFactory @Inject constructor() : NavigationFactory {

    override fun create(builder: NavGraphBuilder) {
        builder.composable<NavigationDestination.Play> {
            PlayScreen()
        }
    }
}