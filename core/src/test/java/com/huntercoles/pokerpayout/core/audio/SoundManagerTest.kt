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

    @Before
    fun setUp() {
        val app: Context = ApplicationProvider.getApplicationContext()
        audioPreferences = AudioPreferences(app)
        player = RecordingPlayer()
        sound = SoundManager(NoSoundFiles(app), audioPreferences).apply { newPlayer = { player } }
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

        fun finishPreparing() {
            onPrepared?.onPrepared(this)
        }

        override fun setOnPreparedListener(listener: MediaPlayer.OnPreparedListener?) {
            onPrepared = listener
        }

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
