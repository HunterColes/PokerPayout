package com.huntercoles.pokerpayout.core.presentation

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * Told when the app comes to the front and when it leaves it: Home, another app, or the screen
 * locking (PP-081: the live clock notification shows only while the app is out of sight). A rotation
 * is neither. Called on the main thread by [MainActivity].
 */
interface AppVisibilityListener {
    fun onAppVisible()

    fun onAppHidden()
}

/** The listeners, contributed by the feature modules with `@IntoSet`; none is fine. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppVisibilityModule {
    @Multibinds
    abstract fun appVisibilityListeners(): Set<AppVisibilityListener>
}
