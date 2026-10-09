package com.huntercoles.pokerpayout.core.preferences

import android.content.Context
import com.huntercoles.pokerpayout.core.backup.BackupCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * PP-113: tells a new install from an update, once. The app's start (MainApplication) calls [settle]
 * before anything else runs, so the saved files hold only what earlier versions wrote: a phone with
 * anything in any of them ([BackupCatalog.FILES]: a game, a preset, a night in History, a setting)
 * is an update, or a restore from Android's backup, and never sees the welcome; a phone with nothing
 * is a new install, and sees it until it is dismissed. Once settled, later starts change nothing
 * ([TimerPreferences.settleWelcome]).
 */
class FirstRun @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timerPreferences: TimerPreferences,
) {
    fun settle() = timerPreferences.settleWelcome(isNewInstall = { BackupCatalog.FILES.none(::holdsData) })

    private fun holdsData(file: String): Boolean = context.getSharedPreferences(file, Context.MODE_PRIVATE).all.isNotEmpty()
}
