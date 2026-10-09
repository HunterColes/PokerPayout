package com.huntercoles.pokerpayout.tournament.live

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * PP-081, PP-137: whether the first Start asks for the live clock's notification comes from Android
 * on this phone, never from saved data. So a phone restored from a backup (which used to bring back
 * the app's own "asked already" flag, and then never asked) asks; a host who said no on this phone
 * isn't asked again, after the update as before; and an allowed or older phone isn't asked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationsAskTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val timerPrefs get() = context.getSharedPreferences("timer_prefs", Context.MODE_PRIVATE)

    @Before
    fun wipe() {
        timerPrefs.edit().clear().commit()
    }

    private fun activity(): Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    /** Android's flag after one "Don't allow" on this phone. */
    private fun refusedOnThisPhone() =
        shadowOf(context.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, true)

    /** As 1.4.6 left timer_prefs once the first Start had asked, on this phone or on the one a backup came from. */
    private fun savedAskedFlag() = timerPrefs.edit().putBoolean("notifications_permission_asked", true).commit()

    @Test
    fun `the rule`() {
        val android13 = Build.VERSION_CODES.TIRAMISU
        assertTrue("never answered", NotificationsAsk.shouldAsk(android13, granted = false, refusedBefore = false))
        assertFalse("allowed", NotificationsAsk.shouldAsk(android13, granted = true, refusedBefore = false))
        assertFalse("said no", NotificationsAsk.shouldAsk(android13, granted = false, refusedBefore = true))
        assertFalse("Android 12", NotificationsAsk.shouldAsk(Build.VERSION_CODES.S_V2, granted = false, refusedBefore = false))
    }

    @Test
    fun `a new phone asks at the first Start`() {
        assertTrue(NotificationsAsk.shouldAsk(activity()))
    }

    @Test
    fun `a phone restored from a backup asks, whatever the backup brought back`() {
        // Android's backup of 1.4.6 on the old phone brings back its flag; this phone has never asked
        savedAskedFlag()
        TimerPreferences(context) // the app starting: the flag goes
        assertFalse(timerPrefs.contains("notifications_permission_asked"))
        assertTrue(NotificationsAsk.shouldAsk(activity()))
    }

    @Test
    fun `a host who said no on this phone isn't asked again, after the update as before`() {
        savedAskedFlag()
        refusedOnThisPhone()
        TimerPreferences(context)
        assertFalse(NotificationsAsk.shouldAsk(activity()))
    }

    @Test
    fun `allowed, it isn't asked`() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(NotificationsAsk.shouldAsk(activity()))
    }
}
