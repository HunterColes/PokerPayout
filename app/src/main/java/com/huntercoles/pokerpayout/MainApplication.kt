package com.huntercoles.pokerpayout

import android.app.Application
import com.huntercoles.pokerpayout.core.preferences.CurrencyPreferences
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import timber.log.Timber.DebugTree
import javax.inject.Inject

@HiltAndroidApp
class MainApplication : Application() {

    /**
     * PP-114: made here, before any screen or the live clock service, so every amount shows in the
     * host's currency from the first frame (and a first start picks it before anything else saves).
     */
    @Inject
    lateinit var currencyPreferences: CurrencyPreferences

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        }
    }
}
