package com.huntercoles.pokerpayout.core.audio.music

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.preferences.MusicPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import kotlin.random.Random

/**
 * The music player against players that only write down what they're asked to do, and a library
 * whose files can go missing: it plays the current song once loaded, keeps the place on a pause,
 * goes on at the end of a song, passes over songs whose files have gone (and stops when none is
 * left), and dips under a cue and on a quiet break. Robolectric's AudioManager grants focus.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MusicPlayerTest {

    private lateinit var context: Context
    private lateinit var preferences: MusicPreferences
    private val library = FakeLibrary()
    private val players = mutableListOf<RecordingPlayer>()
    private lateinit var music: MusicPlayer

    private val a = MusicTrack("content://music/a", "A")
    private val b = MusicTrack("content://music/b", "B")
    private val c = MusicTrack("content://music/c", "C")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(MusicPreferences.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        preferences = MusicPreferences(context)
        music = newMusic()
    }

    private fun newMusic() = MusicPlayer(context, preferences, library).apply {
        newPlayer = { RecordingPlayer().also { players += it } }
        random = Random(11)
    }

    private val last get() = players.last()

    /** Plays [tracks] from the first, loaded and started. */
    private fun playing(vararg tracks: MusicTrack) {
        music.add(tracks.toList())
        music.play()
        last.finishPreparing()
    }

    // ------------------------------------------------------------------ playing

    @Test
    fun playLoadsTheCurrentSongAndStartsItOnceReady() {
        music.add(listOf(a, b))
        music.play()
        assertTrue(music.state.value.playing) // at once, so the button flips
        assertEquals(listOf(a.ref), library.opened)
        assertFalse(last.started)

        last.finishPreparing()
        assertTrue(last.started)
        assertEquals(MusicPreferences.DEFAULT_VOLUME, last.volume, 0f)
    }

    @Test
    fun withNoSongsPlayDoesNothing() {
        music.play()
        assertFalse(music.state.value.playing)
        assertTrue(players.isEmpty())
    }

    @Test
    fun aPauseKeepsThePlaceInTheSong() {
        playing(a, b)
        last.position = 42_000
        music.pause()
        assertFalse(last.started)
        assertFalse(music.state.value.playing)
        assertEquals(42_000, preferences.getPosition(a.ref))

        music.play() // the same player, on from where it was
        assertEquals(1, players.size)
        assertTrue(last.started)
    }

    @Test
    fun afterARestartTheSongCarriesOnWhereItWasPaused() {
        playing(a, b)
        last.position = 42_000
        music.pause()

        val later = newMusic()
        assertEquals(a, later.state.value.playlist.current)
        later.play()
        last.finishPreparing()
        assertEquals(listOf(42_000), last.seeks)
        assertTrue(last.started)
    }

    @Test
    fun theEndOfASongPlaysTheNextAndTheEndOfTheListStops() {
        playing(a, b)
        last.finishPlaying()
        assertEquals(b.ref, library.opened.last())
        last.finishPreparing()
        assertTrue(last.started)

        last.finishPlaying() // repeat is off: the list has ended
        assertFalse(music.state.value.playing)
        assertEquals(a, music.state.value.playlist.current)
        assertEquals(2, players.size)
    }

    @Test
    fun nextWhilePausedMovesOnWithoutPlaying() {
        music.add(listOf(a, b, c))
        music.next()
        assertEquals(b, music.state.value.playlist.current)
        assertTrue(players.isEmpty())
        assertFalse(music.state.value.playing)
    }

    @Test
    fun previousRestartsASongPastItsFirstSecondsAndGoesBackBefore() {
        playing(a, b, c)
        music.next()
        last.finishPreparing()
        assertEquals(b, music.state.value.playlist.current)

        last.position = 30_000
        music.previous()
        assertEquals(listOf(0), last.seeks)
        assertEquals(b, music.state.value.playlist.current)

        last.position = 1_000
        music.previous()
        assertEquals(a, music.state.value.playlist.current)
        assertEquals(a.ref, library.opened.last())
    }

    @Test
    fun aTapOnASongPlaysItFromTheStart() {
        playing(a, b, c)
        music.playTrack(c.ref)
        assertEquals(c, music.state.value.playlist.current)
        assertEquals(c.ref, library.opened.last())
        last.finishPreparing()
        assertTrue(last.started)
        assertTrue(players.first().released)
    }

    // ------------------------------------------------------------------ files that have gone

    @Test
    fun aSongWhoseFileHasGoneIsPassedOver() {
        library.gone += b.ref
        playing(a, b, c)
        music.next()
        // B couldn't be opened: marked missing, and C plays instead
        assertEquals(setOf(b.ref), music.state.value.missing)
        assertEquals(c, music.state.value.playlist.current)
        last.finishPreparing()
        assertTrue(last.started)
    }

    @Test
    fun aSongThatFailsWhilePlayingIsPassedOverToo() {
        playing(a, b)
        last.fail()
        assertEquals(setOf(a.ref), music.state.value.missing)
        assertEquals(b, music.state.value.playlist.current)
        last.finishPreparing()
        assertTrue(last.started)
    }

    @Test
    fun withEveryFileGoneTheMusicStops() {
        library.gone += listOf(a.ref, b.ref)
        music.add(listOf(a, b))
        music.play()
        assertFalse(music.state.value.playing)
        assertTrue(music.state.value.nothingPlayable)
        assertEquals(setOf(a.ref, b.ref), music.state.value.missing)
    }

    @Test
    fun aMissingSongTappedIsTriedAgain() {
        library.gone += b.ref
        playing(a, b, c)
        music.next()
        assertEquals(setOf(b.ref), music.state.value.missing)

        library.gone.clear() // the card is back in
        music.playTrack(b.ref)
        assertTrue(music.state.value.missing.isEmpty())
        assertEquals(b, music.state.value.playlist.current)
    }

    // ------------------------------------------------------------------ the list

    @Test
    fun removingTheSongPlayingPlaysTheNextAndLetsGoOfTheFile() {
        playing(a, b)
        music.remove(a.ref)
        assertEquals(listOf(b), music.state.value.playlist.tracks)
        assertEquals(b.ref, library.opened.last())
        assertEquals(listOf(a.ref), library.forgotten)
        last.finishPreparing()
        assertTrue(last.started)
    }

    @Test
    fun removingTheLastSongStops() {
        playing(a)
        music.remove(a.ref)
        assertTrue(music.state.value.playlist.isEmpty)
        assertFalse(music.state.value.playing)
        assertNull(music.state.value.playlist.current)
    }

    @Test
    fun theListIsSavedAsItChanges() {
        music.add(listOf(a, b, c))
        music.move(2, 0)
        music.setRepeat(RepeatMode.ALL)
        music.setShuffle(true)
        assertEquals(music.state.value.playlist, preferences.getPlaylist())
        assertEquals(listOf(c, a, b), preferences.getPlaylist().tracks)
        assertTrue(preferences.getPlaylist().shuffle)
    }

    // ------------------------------------------------------------------ volume

    @Test
    fun theMusicDipsUnderACueAndComesBackAfter() {
        preferences.setVolume(0.5f)
        playing(a)
        assertEquals(0.5f, last.volume, 0f)

        music.cueStarted()
        assertEquals(0.5f * MusicVolume.DUCKED, last.volume, 0.0001f)
        music.cueEnded()
        assertEquals(0.5f, last.volume, 0f)
    }

    @Test
    fun aCueThatNeverEndsLetsTheMusicBackUpAfterAWhile() {
        preferences.setVolume(0.5f)
        playing(a)
        music.cueStarted()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(16))
        assertEquals(0.5f, last.volume, 0f)
    }

    @Test
    fun aQuietBreakPlaysQuieterAndACueDipsItFurther() {
        preferences.setVolume(1f)
        playing(a)
        music.setQuiet(true)
        assertEquals(MusicVolume.QUIET, last.volume, 0.0001f)
        music.cueStarted()
        assertEquals(MusicVolume.QUIET * MusicVolume.DUCKED, last.volume, 0.0001f)
        music.cueEnded()
        music.setQuiet(false)
        assertEquals(1f, last.volume, 0f)
    }

    @Test
    fun theVolumeSliderMovesTheMusicAtOnce() {
        playing(a)
        music.setVolume(0.25f)
        assertEquals(0.25f, last.volume, 0f)
        assertEquals(0.25f, preferences.getVolume(), 0f)
    }

    @Test
    fun theVolumeIsTheMusicsOwnLessOnABreakAndUnderACue() {
        assertEquals(0.8f, MusicVolume.of(0.8f, quiet = false, ducked = false), 0f)
        assertEquals(0.8f * MusicVolume.QUIET, MusicVolume.of(0.8f, quiet = true, ducked = false), 0.0001f)
        assertEquals(0.8f * MusicVolume.DUCKED, MusicVolume.of(0.8f, quiet = false, ducked = true), 0.0001f)
        assertEquals(1f, MusicVolume.of(3f, quiet = false, ducked = false), 0f)
    }

    // ------------------------------------------------------------------ fakes

    /** Files in [gone] can't be opened; it writes down what it opens and lets go of. */
    private class FakeLibrary : MusicLibrary {
        val gone = mutableSetOf<String>()
        val opened = mutableListOf<String>()
        val forgotten = mutableListOf<String>()

        override fun open(player: MediaPlayer, ref: String): Boolean {
            opened += ref
            return ref !in gone
        }

        override fun exists(ref: String) = ref !in gone

        override fun keep(uris: List<String>) = uris.map { MusicTrack(it, it.substringAfterLast('/')) }

        override fun forget(ref: String) {
            forgotten += ref
        }
    }

    /** A player that only writes down its volume, its seeks, and whether it plays. */
    private class RecordingPlayer : MediaPlayer() {
        var started = false
        var released = false
        var volume = -1f
        var position = 0
        val seeks = mutableListOf<Int>()
        private var onPrepared: OnPreparedListener? = null
        private var onCompletion: OnCompletionListener? = null
        private var onError: OnErrorListener? = null

        fun finishPreparing() = onPrepared?.onPrepared(this)

        fun finishPlaying() {
            started = false
            onCompletion?.onCompletion(this)
        }

        fun fail() {
            onError?.onError(this, MEDIA_ERROR_UNKNOWN, 0)
        }

        override fun setOnPreparedListener(listener: OnPreparedListener?) {
            onPrepared = listener
        }

        override fun setOnCompletionListener(listener: OnCompletionListener?) {
            onCompletion = listener
        }

        override fun setOnErrorListener(listener: OnErrorListener?) {
            onError = listener
        }

        override fun setAudioAttributes(attributes: AudioAttributes?) = Unit

        override fun setWakeMode(context: Context?, mode: Int) = Unit

        override fun prepareAsync() = Unit

        override fun isPlaying(): Boolean = started

        override fun start() {
            started = true
        }

        override fun pause() {
            started = false
        }

        override fun stop() {
            started = false
        }

        override fun reset() = Unit

        override fun release() {
            released = true
        }

        override fun seekTo(msec: Int) {
            seeks += msec
            position = msec
        }

        override fun getCurrentPosition(): Int = position

        override fun setVolume(leftVolume: Float, rightVolume: Float) {
            volume = leftVolume
        }
    }
}
