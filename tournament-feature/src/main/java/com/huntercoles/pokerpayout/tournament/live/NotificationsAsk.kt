package com.huntercoles.pokerpayout.tournament.live

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build

/**
 * PP-081, PP-137: whether the first Start asks for permission to post the live clock notification.
 *
 * Android 13 and up ask once on each phone: never when it's allowed, and never again once the host
 * has said no. The app doesn't remember having asked: Android does, per phone, and Android's backup
 * never carries that to another phone. (A flag the app kept did travel in its backup, so a phone
 * restored from one never asked, and the live clock stayed off; PP-137.)
 *
 * After one "Don't allow", Android says to explain before asking again (the rationale); the app
 * takes that as the answer and leaves it there, as it always has: everything works without the
 * notification, and Tools, Sound offers the way back to it. After a second no, or "Don't ask again",
 * Android answers any ask by itself, without a dialog. Dismissed without an answer, the question
 * comes back at the next game's first Start.
 */
object NotificationsAsk {

    /**
     * Whether to ask, from what Android says on this phone: the [sdk] level, whether the permission
     * is [granted], and whether the host [refusedBefore] (Android's rationale flag).
     */
    fun shouldAsk(sdk: Int, granted: Boolean, refusedBefore: Boolean): Boolean =
        sdk >= Build.VERSION_CODES.TIRAMISU && !granted && !refusedBefore

    /** Whether to ask on this phone now, asking Android through [activity]. */
    fun shouldAsk(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val permission = Manifest.permission.POST_NOTIFICATIONS
        return shouldAsk(
            sdk = Build.VERSION.SDK_INT,
            granted = activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED,
            refusedBefore = activity.shouldShowRequestPermissionRationale(permission),
        )
    }
}
