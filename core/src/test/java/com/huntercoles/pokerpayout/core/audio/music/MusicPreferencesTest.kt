package com.huntercoles.pokerpayout.core.audio.music

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.preferences.MusicPreferences
import com.huntercoles.pokerpayout.core.preferences.PhonePrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

/**
 * The music's saved settings and playlist: the settings in a file of their own (`music_prefs`, so no
 * key of the chime's or the clock's can be touched), the playlist and the paused song's place in the
 * phone's own file (`phone_prefs`, which no backup takes, PP-137), moved there once from where 1.4.6
 * saved them; defaults for a fresh install, the playlist back exactly as saved (songs, order, the one
 * playing, shuffle and its order, repeat), and text that can't be read coming back as an empty
 * playlist rather than a crash.
 */
@RunWith(RobolectricTestRunner::class)
class MusicPreferencesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        listOf(MusicPreferences.FILE, PhonePrefs.FILE).forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun prefs() = MusicPreferences(context)

    private fun raw() = context.getSharedPreferences(MusicPreferences.FILE, Context.MODE_PRIVATE)

    private fun phone() = context.getSharedPreferences(PhonePrefs.FILE, Context.MODE_PRIVATE)

    private val songs = listOf(
        MusicTrack("content://com.android.providers.media.documents/document/audio%3A12", "Night Owl"),
        MusicTrack("content://com.android.externalstorage.documents/document/primary%3AMusic%2Frain.ogg", "Rain, \"live\""),
        MusicTrack(BundledTracks.ref("blues"), "Blues"),
    )

    @Test
    fun aFreshInstallHasNoSongsAndTheLinkOff() {
        val prefs = prefs()
        assertTrue(prefs.getPlaylist().isEmpty)
        assertEquals(MusicPreferences.DEFAULT_VOLUME, prefs.getVolume(), 0f)
        assertFalse(prefs.getAutoPlay())
        assertEquals(BreakMusic.KEEP, prefs.getBreakMusic())
        assertEquals(0, prefs.getPosition(songs[0].ref))
    }

    @Test
    fun thePlaylistComesBackExactlyAsSaved() {
        val playlist = Playlist(repeat = RepeatMode.ONE)
            .add(songs, Random(0))
            .select(songs[1].ref)
            .withShuffle(true, Random(3))
        prefs().setPlaylist(playlist)
        assertEquals(playlist, prefs().getPlaylist())

        val plain = playlist.withShuffle(false, Random(0)).copy(repeat = RepeatMode.ALL).move(0, 2)
        prefs().setPlaylist(plain)
        assertEquals(plain, prefs().getPlaylist())
    }

    @Test
    fun theSettingsAreSavedUnderTheirOwnKeys() {
        prefs().apply {
            setVolume(0.35f)
            setAutoPlay(true)
            setBreakMusic(BreakMusic.QUIET)
            setPosition(songs[0].ref, 61_000)
        }
        val again = prefs()
        assertEquals(0.35f, again.getVolume(), 0f)
        assertTrue(again.getAutoPlay())
        assertEquals(BreakMusic.QUIET, again.getBreakMusic())
        assertEquals(61_000, again.getPosition(songs[0].ref))
        // Where a song was paused belongs to that song alone
        assertEquals(0, again.getPosition(songs[1].ref))
        assertEquals(setOf("volume", "auto_play", "break_music"), raw().all.keys)
        assertEquals(setOf("position_ref", "position_ms"), phone().all.keys)
        assertEquals("QUIET", raw().getString("break_music", null))
        // Nothing went to the chime's file
        assertTrue(context.getSharedPreferences("audio_prefs", Context.MODE_PRIVATE).all.isEmpty())
    }

    @Test
    fun theVolumeStaysBetweenSilentAndFull() {
        prefs().setVolume(1.4f)
        assertEquals(1f, prefs().getVolume(), 0f)
        prefs().setVolume(-1f)
        assertEquals(0f, prefs().getVolume(), 0f)
    }

    @Test
    fun anUnknownBreakChoiceReadsAsTheDefault() {
        raw().edit().putString("break_music", "DANCE").commit()
        assertEquals(MusicPreferences.DEFAULT_BREAK_MUSIC, prefs().getBreakMusic())
    }

    @Test
    fun aPlaylistThatCantBeReadIsEmptyNotACrash() {
        listOf("", "not json", "[]", "{\"format\":99,\"tracks\":[]}", "{\"tracks\":\"no\"}").forEach { text ->
            phone().edit().putString("playlist", text).commit()
            assertTrue(text, prefs().getPlaylist().isEmpty)
        }
    }

    @Test
    fun aSavedPlaylistWithBadPartsKeepsWhatItCan() {
        val text = """
            {"format":1,"tracks":[{"ref":"a","title":"A"},{"title":"no ref"},{"ref":"b"},{"ref":"a","title":"again"}],
             "current":"gone","shuffle":false,"order":[],"repeat":"SOMETIMES","later":"ignored"}
        """.trimIndent()
        phone().edit().putString("playlist", text).commit()
        val playlist = prefs().getPlaylist()
        assertEquals(listOf(MusicTrack("a", "A"), MusicTrack("b", "b")), playlist.tracks)
        assertEquals("a", playlist.currentRef)
        assertEquals(RepeatMode.OFF, playlist.repeat)
    }

    @Test
    fun anInstallFrom146KeepsItsPlaylistWhichMovesOnceToThePhonesOwnFile() {
        // As 1.4.6 saved it: everything in music_prefs
        val playlist = Playlist().add(songs, Random(0)).select(songs[1].ref)
        raw().edit()
            .putString("playlist", PlaylistCodec.encode(playlist))
            .putString("position_ref", songs[1].ref)
            .putInt("position_ms", 42_000)
            .putFloat("volume", 0.35f)
            .putBoolean("auto_play", true)
            .commit()

        val prefs = prefs()
        assertEquals(playlist, prefs.getPlaylist())
        assertEquals(42_000, prefs.getPosition(songs[1].ref))
        assertEquals(0.35f, prefs.getVolume(), 0f)
        assertTrue(prefs.getAutoPlay())
        // The phone's own keys moved, under the same names and types; the settings stayed
        assertEquals(setOf("volume", "auto_play"), raw().all.keys)
        assertEquals(setOf("playlist", "position_ref", "position_ms"), phone().all.keys)
        assertEquals(42_000, phone().getInt("position_ms", 0))

        // Once: the next start has nothing to move, and what's saved after stays
        prefs().setPosition(songs[1].ref, 50_000)
        val again = prefs()
        assertEquals(playlist, again.getPlaylist())
        assertEquals(50_000, again.getPosition(songs[1].ref))
        assertEquals(setOf("volume", "auto_play"), raw().all.keys)
    }

    @Test
    fun aMoveCutShortFinishesWithoutTouchingThePhonesPlaylist() {
        // Copied to the phone's file, then the app was killed before the old copy went
        val moved = Playlist().add(songs, Random(0))
        val text = PlaylistCodec.encode(moved)
        phone().edit().putString("playlist", text).commit()
        raw().edit().putString("playlist", PlaylistCodec.encode(Playlist().add(songs.take(1), Random(0)))).commit()

        assertEquals(moved, prefs().getPlaylist())
        assertFalse(raw().contains("playlist"))
        assertEquals(text, phone().getString("playlist", null))
    }

    @Test
    fun aTitleFromAFileName() {
        assertEquals("Night Owl", TrackTitles.fromFileName("Night_Owl.mp3"))
        assertEquals("01 - Intro", TrackTitles.fromFileName("Music/Album/01 - Intro.flac"))
        assertEquals(".hidden", TrackTitles.fromFileName(".hidden"))
        assertEquals("no extension", TrackTitles.fromFileName("no extension"))
        assertEquals("", TrackTitles.fromFileName(""))
    }
}
