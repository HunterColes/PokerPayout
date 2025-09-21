package com.huntercoles.fatline.basicfeature.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.huntercoles.fatline.basicfeature.presentation.composable.StockSearchRoute
import com.huntercoles.fatline.core.navigation.NavigationDestination.Search
import com.huntercoles.fatline.core.navigation.NavigationFactory
import javax.inject.Inject

class SearchNavigationFactory @Inject constructor() : NavigationFactory {

    override fun create(builder: NavGraphBuilder) {
        builder.composable<Search> {
            StockSearchRoute()
        }
    }
}
