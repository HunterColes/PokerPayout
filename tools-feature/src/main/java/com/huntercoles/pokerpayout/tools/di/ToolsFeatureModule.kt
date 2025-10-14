package com.huntercoles.pokerpayout.tools.di

import com.huntercoles.pokerpayout.tools.presentation.ToolsNavigationFactory
import com.huntercoles.pokerpayout.core.navigation.NavigationFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class ToolsFeatureModule {

    @Binds
    @IntoSet
    abstract fun bindToolsNavigationFactory(factory: ToolsNavigationFactory): NavigationFactory
}
