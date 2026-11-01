package com.huntercoles.pokerpayout.core.audio.packs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The sound packs: today's sounds are the default pack (the cue sound at every change, nothing with a
 * minute left), packs are picked by an id that is saved under a key of its own, and a saved id no
 * pack has any more plays the default rather than nothing.
 */
@RunWith(RobolectricTestRunner::class)
class SoundPacksTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("audio_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun theClassicPackIsTodaysSounds() {
        val classic = SoundPacks.Classic
        assertEquals(SoundPacks.default, classic)
        assertEquals(classic, SoundPacks.all.first())
        listOf(CueEvent.LEVEL_UP, CueEvent.BREAK_START, CueEvent.BREAK_END, CueEvent.GAME_OVER).forEach {
            assertEquals("$it", R.raw.blind_level_up, classic.soundFor(it))
        }
        assertNull(classic.soundFor(CueEvent.ONE_MINUTE))
    }

    /** PP-111: the champion gets the cue sound, as the end of the clock does; the other big moments show without a sound. */
    @Test
    fun theClassicPackHasACueSoundForTheChampionOnly() {
        assertEquals(R.raw.blind_level_up, SoundPacks.Classic.soundFor(CueEvent.CHAMPION))
        assertNull(SoundPacks.Classic.soundFor(CueEvent.BIG_MOMENT))
    }

    /** #7: Chimes has a sound of its own for every moment, under an id that never changes. */
    @Test
    fun theChimesPackHasItsOwnSoundForEveryMoment() {
        assertEquals("chimes", SoundPacks.CHIMES_ID)
        assertEquals(SoundPacks.Chimes, SoundPacks.byId("chimes"))
        assertTrue(SoundPacks.Chimes in SoundPacks.all)
        val expected = mapOf(
            CueEvent.LEVEL_UP to R.raw.cue_chimes_level_up,
            CueEvent.ONE_MINUTE to R.raw.cue_chimes_one_minute,
            CueEvent.BREAK_START to R.raw.cue_chimes_break_start,
            CueEvent.BREAK_END to R.raw.cue_chimes_break_end,
            CueEvent.GAME_OVER to R.raw.cue_chimes_game_over,
            CueEvent.BIG_MOMENT to R.raw.cue_chimes_big_moment,
            CueEvent.CHAMPION to R.raw.cue_chimes_champion,
        )
        CueEvent.entries.forEach { assertEquals("$it", expected[it], SoundPacks.Chimes.soundFor(it)) }
        assertEquals("Chimes", context.getString(SoundPacks.Chimes.name))
    }

    @Test
    fun everyPackHasItsOwnIdAndAName() {
        assertEquals(SoundPacks.all.size, SoundPacks.all.map { it.id }.toSet().size)
        SoundPacks.all.forEach { pack ->
            assertTrue(pack.id.isNotBlank())
            assertTrue(context.getString(pack.name).isNotBlank())
            assertTrue(context.getString(pack.description).isNotBlank())
        }
    }

    @Test
    fun aPackIsFoundByItsIdAndAnUnknownIdIsTheDefault() {
        assertEquals(SoundPacks.Classic, SoundPacks.byId(SoundPacks.CLASSIC_ID))
        assertEquals(SoundPacks.default, SoundPacks.byId("gone"))
        assertEquals(SoundPacks.default, SoundPacks.byId(null))
    }

    @Test
    fun aFreshInstallPlaysTheDefaultPack() {
        assertEquals(SoundPacks.default.id, AudioPreferences(context).getSoundPack())
    }

    @Test
    fun thePickedPackIsSavedUnderItsOwnKeyAndNothingElseIsTouched() {
        val audio = AudioPreferences(context)
        audio.setVolume(0.4f)
        audio.setMuted(true)
        audio.setSoundPack("bells")

        val again = AudioPreferences(context)
        assertEquals("bells", again.getSoundPack())
        assertEquals(0.4f, again.getVolume(), 0f)
        assertTrue(again.getIsMuted())
        val raw = context.getSharedPreferences("audio_prefs", Context.MODE_PRIVATE)
        assertEquals("bells", raw.getString("sound_pack", null))
        // The keys that were there before packs are still there, as they were
        assertEquals(0.4f, raw.getFloat("volume", 1f), 0f)
        assertTrue(raw.getBoolean("is_muted", false))
        assertFalse(raw.contains("vibrate_cues"))
    }
}
