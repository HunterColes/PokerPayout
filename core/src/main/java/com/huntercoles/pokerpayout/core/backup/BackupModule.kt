package com.huntercoles.pokerpayout.core.backup

import android.content.Context
import android.content.Intent
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.time.TimeSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import dagger.multibindings.IntoSet
import javax.inject.Inject

/** Starts the app again from its first screen, so every setting a restore wrote is read afresh. */
fun interface AppRestarter {
    fun restart()
}

/**
 * Ends this process and opens the app anew in a fresh one: every screen and saved setting is read
 * again, as after a reboot. Used once a restore has replaced settings that are read at the start.
 */
class ProcessRestarter @Inject constructor(@ApplicationContext private val context: Context) : AppRestarter {
    override fun restart() {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component ?: return
        context.startActivity(Intent.makeRestartActivityTask(launch))
        Runtime.getRuntime().exit(0)
    }
}

/** The core's backup sections: the settings groups and History. Feature modules add their own (presets). */
@Module
@InstallIn(SingletonComponent::class)
object BackupModule {

    @Provides
    @ElementsIntoSet
    fun settingsSections(@ApplicationContext context: Context, time: TimeSource): Set<BackupSection> =
        SettingsGroup.entries.map { group ->
            SettingsSection(context, group) { file, values ->
                if (file == TIMER_PREFS) TimerPreferences.pausedForBackup(values, time) else values
            }
        }.toSet()

    @Provides
    @IntoSet
    fun historySection(section: HistoryBackup): BackupSection = section

    private const val TIMER_PREFS = "timer_prefs"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupBindings {
    @Binds
    abstract fun bindDocumentFiles(files: ResolverDocumentFiles): DocumentFiles

    @Binds
    abstract fun bindRestarter(restarter: ProcessRestarter): AppRestarter
}
