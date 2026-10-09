package com.huntercoles.pokerpayout

import android.app.Application
import com.huntercoles.pokerpayout.core.preferences.FirstRun
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import timber.log.Timber.DebugTree
import javax.inject.Inject

@HiltAndroidApp
class MainApplication : Application() {

    /** PP-113: a new install or an update, told apart before anything saves a thing. */
    @Inject
    lateinit var firstRun: FirstRun

    override fun onCreate() {
        super.onCreate()
        firstRun.settle()

        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        }
    }
}
