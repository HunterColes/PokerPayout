package com.huntercoles.pokerpayout

import android.app.Application
import android.os.StrictMode
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import timber.log.Timber.DebugTree
import java.io.File
import kotlin.concurrent.thread

@HiltAndroidApp
class MainApplication : Application() {

    override fun onCreate() {
        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
            watchForSlowWork()
        }
        warmUpSavedSettings()
        super.onCreate()
    }

    /**
     * Every saved settings file (SharedPreferences) starts loading on a background thread as the app
     * starts, so the clock, the bank and the tools find them in memory instead of each waiting on
     * the disk on the main thread when its screen first opens (StrictMode showed 8 such waits in the
     * device tour). It only reads: the files and their keys are untouched.
     */
    private fun warmUpSavedSettings() {
        thread(name = "settings-warm-up", isDaemon = true) {
            File(dataDir, SETTINGS_DIR).listFiles()
                ?.filter { it.name.endsWith(SETTINGS_SUFFIX) }
                ?.forEach { getSharedPreferences(it.name.removeSuffix(SETTINGS_SUFFIX), MODE_PRIVATE) }
        }
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

    private companion object {
        /** Where Android keeps SharedPreferences files: <dataDir>/shared_prefs/<name>.xml. */
        const val SETTINGS_DIR = "shared_prefs"
        const val SETTINGS_SUFFIX = ".xml"
    }
}
