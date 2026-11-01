package com.huntercoles.pokerpayout.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.RawRes
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays the clock's cue sounds (the picked sound pack's, or a file the host picked: [playFile]).
 * Uses MediaPlayer for longer audio files (10+ seconds) with proper audio focus management.
 *
 * Note: SoundPool has a 1MB limit per sound (~5.6 seconds at 44.1kHz stereo),
 * so MediaPlayer is used instead for tournament notifications.
 *
 * While a cue sounds, [ducking] dips the music under it; the cue also asks for transient, duckable
 * audio focus, so another app's music dips too.
 */
@Suppress("TooManyFunctions") // one player for the pack cues and a picked file, so both live here
@Singleton
class SoundManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioPreferences: AudioPreferences,
    private val ducking: CueDucking,
) {
    private var mediaPlayer: MediaPlayer? = null
    private var currentSoundResId: Int = -1
    private var isPrepared = false

    /** A cue is sounding: the music is dipped until it ends. */
    private var sounding = false

    /** Fades out and stops a file cue at its end ([playFile]); null when none is due. */
    private var fading: Runnable? = null

    private val handler: Handler by lazy { Handler(Looper.getMainLooper()) }

    /** Makes each player; a test puts in one that records what it is asked to do. */
    internal var newPlayer: () -> MediaPlayer = { MediaPlayer() }

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val audioFocusRequest: AudioFocusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).run {
            setAudioAttributes(attributes)
            build()
        }
    }

    private val attributes: AudioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }

    /**
     * Preloads a sound effect without playing it.
     * Call this early to ensure sound is ready when needed. The sound already loaded is kept, so a
     * cue sound that is sounding plays on (a new clock screen preloads while it may be).
     */
    fun preloadSound(@RawRes soundResId: Int) {
        if (soundResId == currentSoundResId && mediaPlayer != null) return
        try {
            releasePlayer()
            // A cue cut short by loading another sound is over: the music comes back up
            if (sounding) ended()
            mediaPlayer = newPrepared(soundResId, startWhenReady = false)
            currentSoundResId = soundResId
        } catch (ignored: Exception) {
            // A sound that can't load stays silent; the clock carries on
            isPrepared = false
        }
    }

    /**
     * Plays a sound effect from raw resources.
     * If the sound is already preloaded and prepared, plays immediately.
     * Otherwise, prepares and plays the sound.
     * Respects volume and mute settings and requests audio focus.
     */
    fun playSound(@RawRes soundResId: Int) {
        if (!audioPreferences.getIsMuted()) start(soundResId)
    }

    /** Plays [soundResId] once so the host can hear it (the cue sound picker), even with the sound off. */
    fun previewSound(@RawRes soundResId: Int) = start(soundResId)

    /**
     * Plays the sound file at [uri] (a content URI the host picked) from [fromMillis] into it, and
     * stops it at [endMillis] with a short fade (0 plays it to the end). Respects mute, ducks the music
     * and takes the audio focus like [playSound]. False when the file can't be opened: the caller then
     * plays the pack's sound. A preloaded pack sound is let go and loads again on its next play.
     */
    fun playFile(uri: String, fromMillis: Long = 0, endMillis: Long = 0): Boolean =
        audioPreferences.getIsMuted() || startFile(uri, fromMillis, endMillis)

    /** Plays the file at [uri] like [playFile] so the host can hear it, even with the sound off. */
    fun previewFile(uri: String, fromMillis: Long = 0, endMillis: Long = 0): Boolean =
        startFile(uri, fromMillis, endMillis)

    private fun startFile(uri: String, fromMillis: Long, endMillis: Long): Boolean {
        releasePlayer()
        // A cue cut short by this one is over: the music comes back up
        if (sounding) ended()
        val player = newPlayer()
        val opened = try {
            player.setAudioAttributes(attributes)
            player.setDataSource(context, Uri.parse(uri))
            true
        } catch (ignored: Exception) {
            false
        }
        val focused = opened &&
            audioManager.requestAudioFocus(audioFocusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!focused) {
            runCatching { player.release() }
            // No focus (a call): the file is fine, so the caller doesn't fall back to the pack
            return opened
        }
        mediaPlayer = player
        return try {
            player.loadFile(fromMillis.coerceAtLeast(0), endMillis)
            true
        } catch (ignored: Exception) {
            releasePlayer()
            ended()
            false
        }
    }

    private fun MediaPlayer.loadFile(fromMillis: Long, endMillis: Long) {
        val volume = audioPreferences.getVolume()
        setVolume(volume, volume)
        setOnPreparedListener { player ->
            if (player !== mediaPlayer) return@setOnPreparedListener
            if (fromMillis > 0) player.seekTo(fromMillis.toInt())
            begin(player)
            if (endMillis > 0) stopAt(player, endMillis - fromMillis, volume)
        }
        setOnCompletionListener { player ->
            if (player !== mediaPlayer) return@setOnCompletionListener
            cancelFade()
            ended()
        }
        setOnErrorListener { player, _, _ ->
            if (player === mediaPlayer) {
                cancelFade()
                ended()
            }
            true // handled
        }
        prepareAsync()
    }

    /** Fades [player] out over the last [FADE_MILLIS] (at most) of [playMillis], then stops it. */
    private fun stopAt(player: MediaPlayer, playMillis: Long, volume: Float) {
        val fadeMillis = FADE_MILLIS.coerceAtMost(playMillis.coerceAtLeast(0))
        val stepMillis = fadeMillis / FADE_STEPS
        val fade = object : Runnable {
            private var step = 0

            override fun run() {
                if (player !== mediaPlayer) return
                step++
                if (step >= FADE_STEPS) {
                    fading = null
                    runCatching { player.stop() }
                    releasePlayer()
                    ended()
                } else {
                    val left = volume * (FADE_STEPS - step) / FADE_STEPS
                    runCatching { player.setVolume(left, left) }
                    handler.postDelayed(this, stepMillis)
                }
            }
        }
        fading = fade
        handler.postDelayed(fade, (playMillis - fadeMillis + stepMillis).coerceAtLeast(0))
    }

    private fun cancelFade() {
        fading?.let { handler.removeCallbacks(it) }
        fading = null
    }

    private fun start(@RawRes soundResId: Int) {
        try {
            if (audioManager.requestAudioFocus(audioFocusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
            val ready = mediaPlayer?.takeIf { currentSoundResId == soundResId && isPrepared }
            if (ready != null) {
                if (ready.isPlaying) ready.seekTo(0) // Restart if already playing
                // The volume slider may have moved since the player was prepared (B12)
                val volume = audioPreferences.getVolume()
                ready.setVolume(volume, volume)
                begin(ready)
            } else {
                releasePlayer()
                mediaPlayer = newPrepared(soundResId, startWhenReady = true)
                currentSoundResId = soundResId
            }
        } catch (ignored: Exception) {
            // Silently handle any playback errors and release audio focus
            ended()
        }
    }

    /**
     * A player for [soundResId] at the current volume, preparing; it starts once ready if
     * [startWhenReady]. One whose sound can't be loaded is released before the failure goes on.
     */
    private fun newPrepared(@RawRes soundResId: Int, startWhenReady: Boolean): MediaPlayer {
        val player = newPlayer()
        var loaded = false
        try {
            player.load(soundResId, startWhenReady)
            loaded = true
        } finally {
            if (!loaded) runCatching { player.release() }
        }
        return player
    }

    private fun MediaPlayer.load(@RawRes soundResId: Int, startWhenReady: Boolean) {
        setAudioAttributes(attributes)
        context.resources.openRawResourceFd(soundResId)?.use {
            setDataSource(it.fileDescriptor, it.startOffset, it.length)
        }
        val volume = audioPreferences.getVolume()
        setVolume(volume, volume)
        setOnPreparedListener { player ->
            isPrepared = true
            if (startWhenReady) begin(player)
        }
        setOnCompletionListener {
            // Back to the beginning, so it can be played again
            seekTo(0)
            isPrepared = true
            ended()
        }
        setOnErrorListener { _, _, _ ->
            isPrepared = false
            ended()
            true // handled
        }
        prepareAsync()
    }

    /** Starts [player], with the music dipped under it. */
    private fun begin(player: MediaPlayer) {
        if (!sounding) {
            sounding = true
            ducking.cueStarted()
        }
        player.start()
    }

    /** The cue is over (or failed): the music comes back up and the focus goes. */
    private fun ended() {
        if (sounding) {
            sounding = false
            ducking.cueEnded()
        }
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
    }

    /**
     * Internal helper to release the MediaPlayer instance.
     */
    private fun releasePlayer() {
        cancelFade()
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                reset()
                release()
            }
        } catch (ignored: Exception) {
            // Ignore any errors during cleanup
        } finally {
            mediaPlayer = null
            currentSoundResId = -1
            isPrepared = false
        }
    }

    private companion object {
        /** A file cue with an end fades out over this long before it (or over all of it, if shorter). */
        const val FADE_MILLIS = 300L
        const val FADE_STEPS = 6
    }
}
