package com.huntercoles.pokerpayout.tournament.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.huntercoles.pokerpayout.tournament.presentation.composable.TournamentScreen
import com.huntercoles.pokerpayout.core.navigation.NavigationDestination
import com.huntercoles.pokerpayout.core.navigation.NavigationFactory
import javax.inject.Inject

class TournamentNavigationFactory @Inject constructor() : NavigationFactory {

    override fun create(builder: NavGraphBuilder) {
        builder.composable<NavigationDestination.Tournament> {
            TournamentScreen()
        }
    }
}