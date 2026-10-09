package com.huntercoles.pokerpayout.core.audio.music

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.preferences.MusicPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

/**
 * The music's saved settings and playlist: a file of their own (`music_prefs`, so no key of the
 * chime's or the clock's can be touched), defaults for a fresh install, the playlist back exactly
 * as saved (songs, order, the one playing, shuffle and its order, repeat), and text that can't be
 * read coming back as an empty playlist rather than a crash.
 */
@RunWith(RobolectricTestRunner::class)
class MusicPreferencesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(MusicPreferences.FILE, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun prefs() = MusicPreferences(context)

    private fun raw() = context.getSharedPreferences(MusicPreferences.FILE, Context.MODE_PRIVATE)

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
        assertEquals(setOf("volume", "auto_play", "break_music", "position_ref", "position_ms"), raw().all.keys)
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
            raw().edit().putString("playlist", text).commit()
            assertTrue(text, prefs().getPlaylist().isEmpty)
        }
    }

    @Test
    fun aSavedPlaylistWithBadPartsKeepsWhatItCan() {
        val text = """
            {"format":1,"tracks":[{"ref":"a","title":"A"},{"title":"no ref"},{"ref":"b"},{"ref":"a","title":"again"}],
             "current":"gone","shuffle":false,"order":[],"repeat":"SOMETIMES","later":"ignored"}
        """.trimIndent()
        raw().edit().putString("playlist", text).commit()
        val playlist = prefs().getPlaylist()
        assertEquals(listOf(MusicTrack("a", "A"), MusicTrack("b", "b")), playlist.tracks)
        assertEquals("a", playlist.currentRef)
        assertEquals(RepeatMode.OFF, playlist.repeat)
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
