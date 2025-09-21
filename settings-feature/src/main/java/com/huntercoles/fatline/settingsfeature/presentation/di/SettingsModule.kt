package com.huntercoles.fatline.settingsfeature.presentation.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import com.huntercoles.fatline.settingsfeature.presentation.TimerNavigationFactory
import com.huntercoles.fatline.core.navigation.NavigationFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface TimerSingletonModule {

    @Singleton
    @Binds
    @IntoSet
    fun bindTimerNavigationFactory(factory: TimerNavigationFactory): NavigationFactory
}
