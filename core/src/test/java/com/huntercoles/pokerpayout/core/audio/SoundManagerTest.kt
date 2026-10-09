package com.huntercoles.pokerpayout.core.audio

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetFileDescriptor
import android.content.res.Resources
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The chime plays at the volume the Sound slider is at now, not the one it was at when the chime
 * was loaded (B12, PP-019: moving the slider did nothing until the app restarted). The player
 * records what it is asked to do; Robolectric's AudioManager grants focus.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SoundManagerTest {

    private lateinit var audioPreferences: AudioPreferences
    private lateinit var player: RecordingPlayer
    private lateinit var sound: SoundManager

    /** What the music was told: "dip" when a cue starts, "up" when it ends. */
    private val music = mutableListOf<String>()
    private val ducking = object : CueDucking {
        override fun cueStarted() {
            music += "dip"
        }

        override fun cueEnded() {
            music += "up"
        }
    }

    @Before
    fun setUp() {
        val app: Context = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("audio_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        audioPreferences = AudioPreferences(app)
        player = RecordingPlayer()
        sound = SoundManager(NoSoundFiles(app), audioPreferences, ducking)
        sound.newPlayer = { player }
    }

    @Test
    fun `the music dips while a cue sounds and comes back up after`() {
        sound.preloadSound(CHIME)
        player.finishPreparing()

        sound.playSound(CHIME)
        assertEquals(listOf("dip"), music)

        player.finishPlaying()
        assertEquals(listOf("dip", "up"), music)
    }

    @Test
    fun `a cue that fails lets the music back up`() {
        sound.playSound(CHIME) // not loaded: loads, then starts once ready
        player.finishPreparing()
        assertEquals(listOf("dip"), music)

        player.fail()
        assertEquals(listOf("dip", "up"), music)
    }

    @Test
    fun `with the sound off nothing dips`() {
        audioPreferences.setMuted(true)
        sound.preloadSound(CHIME)
        player.finishPreparing()
        sound.playSound(CHIME)
        assertEquals(emptyList<String>(), music)
    }

    @Test
    fun `a preview plays even with the sound off`() {
        sound.preloadSound(CHIME)
        player.finishPreparing()
        player.calls.clear()

        audioPreferences.setMuted(true)
        sound.previewSound(CHIME)

        assertEquals(listOf("volume 1.0", "start"), player.calls)
    }

    @Test
    fun `a loaded chime plays at the volume the slider is at now (B12)`() {
        audioPreferences.setVolume(0.8f)
        sound.preloadSound(CHIME)
        player.finishPreparing()
        player.calls.clear()

        audioPreferences.setVolume(0.3f)
        sound.playSound(CHIME)

        assertEquals(listOf("volume 0.3", "start"), player.calls)
    }

    @Test
    fun `turned down to nothing, the chime is silent`() {
        sound.preloadSound(CHIME)
        player.finishPreparing()
        player.calls.clear()

        audioPreferences.setVolume(0f)
        sound.playSound(CHIME)

        assertEquals(listOf("volume 0.0", "start"), player.calls)
    }

    @Test
    fun `with the sound off, the chime doesn't play`() {
        sound.preloadSound(CHIME)
        player.finishPreparing()
        player.calls.clear()

        audioPreferences.setMuted(true)
        sound.playSound(CHIME)

        assertEquals(emptyList<String>(), player.calls)
    }

    /** The app's context, minus the sound files: the player here never reads one. */
    private class NoSoundFiles(base: Context) : ContextWrapper(base) {
        @Suppress("DEPRECATION")
        private val noFiles = object : Resources(base.assets, base.resources.displayMetrics, base.resources.configuration) {
            override fun openRawResourceFd(id: Int): AssetFileDescriptor? = null
        }

        override fun getResources(): Resources = noFiles
    }

    /** A player that only writes down the volume it is given and when it starts. */
    private class RecordingPlayer : MediaPlayer() {
        val calls = mutableListOf<String>()
        private var onPrepared: MediaPlayer.OnPreparedListener? = null
        private var onCompletion: MediaPlayer.OnCompletionListener? = null
        private var onError: MediaPlayer.OnErrorListener? = null

        fun finishPreparing() {
            onPrepared?.onPrepared(this)
        }

        fun finishPlaying() {
            onCompletion?.onCompletion(this)
        }

        fun fail() {
            onError?.onError(this, MEDIA_ERROR_UNKNOWN, 0)
        }

        override fun setOnPreparedListener(listener: MediaPlayer.OnPreparedListener?) {
            onPrepared = listener
        }

        override fun setOnCompletionListener(listener: MediaPlayer.OnCompletionListener?) {
            onCompletion = listener
        }

        override fun setOnErrorListener(listener: MediaPlayer.OnErrorListener?) {
            onError = listener
        }

        override fun seekTo(msec: Int) = Unit

        override fun setAudioAttributes(attributes: AudioAttributes?) = Unit

        override fun prepareAsync() = Unit

        override fun isPlaying(): Boolean = false

        override fun setVolume(leftVolume: Float, rightVolume: Float) {
            calls += "volume $leftVolume"
        }

        override fun start() {
            calls += "start"
        }
    }

    private companion object {
        const val CHIME = 1
    }
}
