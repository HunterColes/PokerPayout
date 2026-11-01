package com.huntercoles.pokerpayout.core.audio.custom

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.preferences.CustomCueSoundPreferences
import com.huntercoles.pokerpayout.core.preferences.PhonePrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The picked cue sound file, its peak and its end live in phone_prefs under the six keys, and survive a restart. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomCueSoundPreferencesTest {

    private lateinit var app: Context

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        PhonePrefs.open(app).edit().clear().commit()
    }

    @Test
    fun `nothing picked is off, with no sound and nothing missing`() {
        val prefs = CustomCueSoundPreferences(app)
        assertFalse(prefs.isOn())
        assertNull(prefs.getSound())
        assertFalse(prefs.isMissing())
    }

    @Test
    fun `the picked file, its peak and end survive a restart, under the six keys in phone_prefs`() {
        val before = CustomCueSoundPreferences(app)
        before.setSound(CustomCueSound(uri = URI, lengthMillis = 12_000, peakMillis = 4_000, endMillis = 9_000))
        before.setOn(true)
        before.setMissing(true)

        val after = CustomCueSoundPreferences(app) // a new instance: what a restart reads
        assertTrue(after.isOn())
        assertTrue(after.isMissing())
        assertEquals(CustomCueSound(URI, 12_000, 4_000, 9_000), after.getSound())

        val saved = PhonePrefs.open(app).all
        assertEquals(
            mapOf(
                "cue_custom_on" to true,
                "cue_custom_uri" to URI,
                "cue_custom_length_ms" to 12_000L,
                "cue_custom_peak_ms" to 4_000L,
                "cue_custom_end_ms" to 9_000L,
                "cue_custom_missing" to true,
            ),
            saved,
        )
    }

    @Test
    fun `the peak stays within the file and the end between the peak and the file's end`() {
        val prefs = CustomCueSoundPreferences(app)
        prefs.setSound(CustomCueSound(URI, lengthMillis = 10_000))

        prefs.setPeak(15_000)
        assertEquals(10_000L, prefs.getSound()?.peakMillis)
        prefs.setPeak(-5)
        assertEquals(0L, prefs.getSound()?.peakMillis)

        prefs.setPeak(3_000)
        prefs.setEnd(1_000)
        assertEquals(3_000L, prefs.getSound()?.endMillis)
        prefs.setEnd(20_000)
        assertEquals(10_000L, CustomCueSoundPreferences(app).getSound()?.endMillis)
        prefs.setEnd(0)
        assertEquals(0L, CustomCueSoundPreferences(app).getSound()?.endMillis) // 0 plays to the end

        // A later peak pulls the end along
        prefs.setEnd(5_000)
        prefs.setPeak(7_000)
        assertEquals(CustomCueSound(URI, 10_000, 7_000, 7_000), prefs.getSound())
    }

    @Test
    fun `values saved out of range read back clamped`() {
        PhonePrefs.open(app).edit()
            .putString("cue_custom_uri", URI)
            .putLong("cue_custom_length_ms", 8_000)
            .putLong("cue_custom_peak_ms", 9_000)
            .putLong("cue_custom_end_ms", 99_000)
            .commit()
        assertEquals(CustomCueSound(URI, 8_000, 8_000, 8_000), CustomCueSoundPreferences(app).getSound())
    }

    @Test
    fun `picking a new file clears missing, and clearing forgets the file and turns it off`() {
        val prefs = CustomCueSoundPreferences(app)
        prefs.setSound(CustomCueSound(URI, 10_000))
        prefs.setOn(true)
        prefs.setMissing(true)

        prefs.setPeak(2_000) // the same file: still missing
        assertTrue(prefs.isMissing())
        prefs.setSound(CustomCueSound("content://picked/other.ogg", 5_000))
        assertFalse(prefs.isMissing())

        prefs.clear()
        assertNull(prefs.getSound())
        assertFalse(prefs.isOn())
    }

    private companion object {
        const val URI = "content://picked/cue.mp3"
    }
}
