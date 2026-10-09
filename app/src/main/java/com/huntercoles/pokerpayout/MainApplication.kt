package com.huntercoles.pokerpayout

import android.app.Application
import android.os.StrictMode
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import timber.log.Timber.DebugTree

@HiltAndroidApp
class MainApplication : Application() {

    override fun onCreate() {
        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
            watchForSlowWork()
        }
        super.onCreate()
    }

    /**
     * Debug builds only: disk or network work on the main thread, and leaked resources, are logged
     * (logcat tag StrictMode) without stopping the app. The device tour counts them in its report.
     */
    private fun watchForSlowWork() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build()
        )
    }
}
