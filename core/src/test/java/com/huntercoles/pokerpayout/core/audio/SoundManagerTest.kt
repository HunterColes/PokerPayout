package com.huntercoles.pokerpayout.core.audio

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetFileDescriptor
import android.content.res.Resources
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAudioManager
import java.time.Duration

/**
 * The cue sound plays at the volume the Sound slider is at now, not the one it was at when the cue sound
 * was loaded (B12, PP-019: moving the slider did nothing until the app restarted), and the music
 * dips under a cue only while it sounds. The player records what it is asked to do; Robolectric's
 * AudioManager grants focus.
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
        sound.preloadSound(CUE)
        player.finishPreparing()

        sound.playSound(CUE)
        assertEquals(listOf("dip"), music)

        player.finishPlaying()
        assertEquals(listOf("dip", "up"), music)
    }

    @Test
    fun `a cue that fails lets the music back up`() {
        sound.playSound(CUE) // not loaded: loads, then starts once ready
        player.finishPreparing()
        assertEquals(listOf("dip"), music)

        player.fail()
        assertEquals(listOf("dip", "up"), music)
    }

    @Test
    fun `with the sound off nothing dips`() {
        audioPreferences.setMuted(true)
        sound.preloadSound(CUE)
        player.finishPreparing()
        sound.playSound(CUE)
        assertEquals(emptyList<String>(), music)
    }

    @Test
    fun `a preview plays even with the sound off`() {
        sound.preloadSound(CUE)
        player.finishPreparing()
        player.calls.clear()

        audioPreferences.setMuted(true)
        sound.previewSound(CUE)

        assertEquals(listOf("volume 1.0", "start"), player.calls)
    }

    @Test
    fun `a loaded cue sound plays at the volume the slider is at now (B12)`() {
        audioPreferences.setVolume(0.8f)
        sound.preloadSound(CUE)
        player.finishPreparing()
        player.calls.clear()

        audioPreferences.setVolume(0.3f)
        sound.playSound(CUE)

        assertEquals(listOf("volume 0.3", "start"), player.calls)
    }

    @Test
    fun `turned down to nothing, the cue sound is silent`() {
        sound.preloadSound(CUE)
        player.finishPreparing()
        player.calls.clear()

        audioPreferences.setVolume(0f)
        sound.playSound(CUE)

        assertEquals(listOf("volume 0.0", "start"), player.calls)
    }

    @Test
    fun `with the sound off, the cue sound doesn't play`() {
        sound.preloadSound(CUE)
        player.finishPreparing()
        player.calls.clear()

        audioPreferences.setMuted(true)
        sound.playSound(CUE)

        assertEquals(emptyList<String>(), player.calls)
    }

    @Test
    fun `a new clock screen preloading the cue sound lets the one sounding play on`() {
        sound.preloadSound(CUE)
        player.finishPreparing()
        sound.playSound(CUE)
        player.calls.clear()

        sound.preloadSound(CUE) // what a new clock screen does as it opens
        assertEquals(emptyList<String>(), player.calls)
        assertEquals(listOf("dip"), music)

        player.finishPlaying()
        assertEquals(listOf("dip", "up"), music)
    }

    @Test
    fun `a cue cut short by loading another sound lets the music back up`() {
        sound.preloadSound(CUE)
        player.finishPreparing()
        sound.playSound(CUE)

        sound.preloadSound(OTHER_SOUND)

        assertEquals(listOf("dip", "up"), music)
        // The next cue dips the music again
        player.finishPreparing()
        sound.playSound(OTHER_SOUND)
        assertEquals(listOf("dip", "up", "dip"), music)
    }

    @Test
    fun `a sound that can't be loaded gives its player back and plays nothing`() {
        val app: Context = ApplicationProvider.getApplicationContext()
        val broken = SoundManager(BrokenSoundFiles(app), audioPreferences, ducking)
        broken.newPlayer = { player }

        broken.preloadSound(CUE)
        assertEquals(listOf("release"), player.calls.filter { it == "release" })

        broken.playSound(CUE)
        assertEquals(listOf("release", "release"), player.calls.filter { it == "release" })
        assertEquals(emptyList<String>(), music)
    }

    // ------------------------------------------------------------------ a picked file (playFile)

    @Test
    fun `a file plays from its offset, fades and stops at its end, and lets the music back up`() {
        assertTrue(sound.playFile(FILE, fromMillis = 1500, endMillis = 9000))
        assertEquals(listOf("open $FILE", "volume 1.0", "prepare"), player.calls)
        player.calls.clear()

        player.finishPreparing()
        assertEquals(listOf("seek 1500", "start"), player.calls)
        assertEquals(listOf("dip"), music)
        player.calls.clear()

        // 7500 ms of play to 9000; nothing fades before its last 300 ms
        later(7199)
        assertEquals(emptyList<String>(), player.calls)
        later(250)
        assertTrue(player.calls.isNotEmpty() && player.calls.all { it.startsWith("volume ") })
        assertFalse("stop" in player.calls)
        assertEquals(listOf("dip"), music)

        later(51)
        assertTrue("stop" in player.calls)
        assertEquals(listOf("dip", "up"), music)
        assertNotNull(focus().lastAbandonedAudioFocusRequest)
    }

    @Test
    fun `a file with no end plays to its end`() {
        sound.playFile(FILE, fromMillis = 0, endMillis = 0)
        player.finishPreparing()
        assertEquals(listOf("open $FILE", "volume 1.0", "prepare", "start"), player.calls)

        later(60_000)
        assertFalse("stop" in player.calls)
        player.finishPlaying()
        assertEquals(listOf("dip", "up"), music)
        assertNotNull(focus().lastAbandonedAudioFocusRequest)
    }

    @Test
    fun `with the sound off a file doesn't play`() {
        audioPreferences.setMuted(true)
        assertTrue(sound.playFile(FILE, fromMillis = 1500, endMillis = 9000))
        player.finishPreparing()

        assertEquals(emptyList<String>(), player.calls)
        assertEquals(emptyList<String>(), music)
        assertNull(focus().lastAudioFocusRequest)
    }

    @Test
    fun `a file preview plays even with the sound off`() {
        audioPreferences.setMuted(true)
        assertTrue(sound.previewFile(FILE, fromMillis = 200, endMillis = 0))
        player.finishPreparing()

        assertEquals(listOf("seek 200", "start"), player.calls.takeLast(2))
        assertEquals(listOf("dip"), music)
    }

    @Test
    fun `a file that can't be opened says so and holds no player or focus`() {
        player.openFails = true

        assertFalse(sound.playFile(FILE, fromMillis = 1500, endMillis = 9000))

        assertEquals(listOf("release"), player.calls)
        assertEquals(emptyList<String>(), music)
        assertNull(focus().lastAudioFocusRequest)
    }

    @Test
    fun `the clock's preloaded cue sound still plays after a file (hardening)`() {
        sound.preloadSound(CUE)
        player.finishPreparing()

        sound.playFile(FILE, fromMillis = 1500, endMillis = 9000)
        player.finishPreparing()
        later(7500)
        assertEquals(listOf("dip", "up"), music)
        player.calls.clear()

        sound.playSound(CUE) // loads again, then starts once ready
        player.finishPreparing()
        assertTrue("start" in player.calls)
        assertEquals(listOf("dip", "up", "dip"), music)
    }

    @Test
    fun `a pack cue that cuts a file short stops its fade`() {
        sound.playFile(FILE, fromMillis = 0, endMillis = 2000)
        player.finishPreparing()

        sound.playSound(CUE)
        player.finishPreparing()
        player.calls.clear()

        later(5000)
        assertEquals(emptyList<String>(), player.calls)
        assertEquals(listOf("dip"), music)
    }

    private fun later(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    private fun focus(): ShadowAudioManager {
        val app: Context = ApplicationProvider.getApplicationContext()
        return shadowOf(app.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
    }

    /** The app's context, minus the sound files: the player here never reads one. */
    private class NoSoundFiles(base: Context) : ContextWrapper(base) {
        @Suppress("DEPRECATION")
        private val noFiles = object : Resources(base.assets, base.resources.displayMetrics, base.resources.configuration) {
            override fun openRawResourceFd(id: Int): AssetFileDescriptor? = null
        }

        override fun getResources(): Resources = noFiles
    }

    /** The app's context, with sound files that can't be opened. */
    private class BrokenSoundFiles(base: Context) : ContextWrapper(base) {
        @Suppress("DEPRECATION")
        private val broken = object : Resources(base.assets, base.resources.displayMetrics, base.resources.configuration) {
            override fun openRawResourceFd(id: Int): AssetFileDescriptor = throw NotFoundException("no sound $id")
        }

        override fun getResources(): Resources = broken
    }

    /** A player that only writes down the volume it is given and when it starts. */
    private class RecordingPlayer : MediaPlayer() {
        val calls = mutableListOf<String>()

        /** A picked file that can't be opened (its grant was taken back, or it was deleted). */
        var openFails = false
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

        override fun seekTo(msec: Int) {
            calls += "seek $msec"
        }

        override fun setDataSource(context: Context, uri: Uri) {
            if (openFails) throw java.io.IOException("can't open $uri")
            calls += "open $uri"
        }

        override fun setAudioAttributes(attributes: AudioAttributes?) = Unit

        override fun prepareAsync() {
            calls += "prepare"
        }

        override fun stop() {
            calls += "stop"
        }

        override fun isPlaying(): Boolean = false

        override fun setVolume(leftVolume: Float, rightVolume: Float) {
            calls += "volume $leftVolume"
        }

        override fun start() {
            calls += "start"
        }

        override fun reset() = Unit

        override fun release() {
            calls += "release"
        }
    }

    private companion object {
        const val CUE = 1
        const val OTHER_SOUND = 2
        const val FILE = "content://picked/cue.mp3"
    }
}
