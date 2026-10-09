package com.huntercoles.pokerpayout

import android.app.Application
import android.os.StrictMode
import com.huntercoles.pokerpayout.core.preferences.FirstRun
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import timber.log.Timber.DebugTree
import java.io.File
import javax.inject.Inject
import kotlin.concurrent.thread

@HiltAndroidApp
class MainApplication : Application() {

    /** PP-113: a new install or an update, told apart before anything saves a thing. */
    @Inject
    lateinit var firstRun: FirstRun

    override fun onCreate() {
        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
            watchForSlowWork()
        }
        warmUpSavedSettings()
        super.onCreate()
        // Injected now, and nothing else has run: the files hold only what earlier versions saved
        firstRun.settle()
    }

    /**
     * Every saved settings file (SharedPreferences) starts loading on a background thread as the app
     * starts, so the clock, the bank and the tools find them in memory instead of each waiting on
     * the disk on the main thread when its screen first opens (the device tour's StrictMode log showed
     * one for every file). A file never written yet has nothing to load. It only reads: the files
     * and their keys are untouched.
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
