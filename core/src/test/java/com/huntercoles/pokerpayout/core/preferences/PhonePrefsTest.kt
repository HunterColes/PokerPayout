package com.huntercoles.pokerpayout.core.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.time.ClockAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PP-137: what's about this phone alone moves, once, into the file no backup takes (`phone_prefs`),
 * keeping each key's name, type and value; and the "asked for notifications" flag 1.4.6 kept with the
 * clock goes, since Android's own answer replaces it (the tournament's `NotificationsAskTest`).
 * Written as 1.4.6 wrote them, into Robolectric's real SharedPreferences.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhonePrefsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun prefs(name: String): SharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private val old get() = prefs("old_prefs")
    private val phone get() = prefs(PhonePrefs.FILE)

    @Before
    fun wipe() {
        listOf("old_prefs", PhonePrefs.FILE, "timer_prefs").forEach { prefs(it).edit().clear().commit() }
    }

    @Test
    fun `the keys move with their names, types and values, and the rest stays`() {
        old.edit()
            .putString("text", "Night Owl")
            .putInt("int", 42)
            .putLong("long", 9_000_000_000L)
            .putFloat("float", 0.35f)
            .putBoolean("bool", true)
            .putStringSet("set", setOf("a", "b"))
            .putString("setting", "stays")
            .commit()
        val moving = setOf("text", "int", "long", "float", "bool", "set")

        PhonePrefs.moveOnce(old, phone, moving)

        assertEquals(
            mapOf(
                "text" to "Night Owl", "int" to 42, "long" to 9_000_000_000L, "float" to 0.35f, "bool" to true,
                "set" to setOf("a", "b"),
            ),
            phone.all,
        )
        assertEquals(mapOf("setting" to "stays"), old.all)
    }

    @Test
    fun `once moved there is nothing to move, and the phone's values stay as they are`() {
        old.edit().putInt("int", 1).commit()
        PhonePrefs.moveOnce(old, phone, setOf("int"))
        phone.edit().putInt("int", 2).commit()

        PhonePrefs.moveOnce(old, phone, setOf("int"))
        assertEquals(mapOf("int" to 2), phone.all)
        assertTrue(old.all.isEmpty())
    }

    @Test
    fun `an old copy turning up again doesn't overwrite this phone's own, and goes`() {
        phone.edit().putString("text", "this phone's").commit()
        old.edit().putString("text", "from elsewhere").commit()

        PhonePrefs.moveOnce(old, phone, setOf("text"))
        assertEquals("this phone's", phone.getString("text", null))
        assertFalse(old.contains("text"))
    }

    @Test
    fun `nothing to move writes nothing`() {
        old.edit().putString("setting", "stays").commit()
        PhonePrefs.moveOnce(old, phone, setOf("text"))
        assertTrue(phone.all.isEmpty())
        assertEquals(mapOf("setting" to "stays"), old.all)
    }

    @Test
    fun `the 1_4_6 notifications flag goes once, and the clock and its settings stay`() {
        // As 1.4.6 left timer_prefs after the first Start asked (or as Android's backup brings it back)
        prefs("timer_prefs").edit()
            .putBoolean("notifications_permission_asked", true)
            .putString("break_message", "Last rebuy")
            .commit()
        TimerPreferences(context).saveClock(ClockAnchor.stopped(90_000L))
        prefs("timer_prefs").edit().putBoolean("notifications_permission_asked", true).commit()

        val timer = TimerPreferences(context)
        assertFalse(prefs("timer_prefs").contains("notifications_permission_asked"))
        assertEquals("Last rebuy", timer.getBreakMessage())
        assertEquals(90_000L, timer.getClock()?.elapsedMillis)

        // And again: nothing left to remove, nothing else touched
        val before = prefs("timer_prefs").all
        TimerPreferences(context)
        assertEquals(before, prefs("timer_prefs").all)
    }
}
