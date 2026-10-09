package com.huntercoles.pokerpayout.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.annotation.RawRes
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays the clock's cue sounds (the chime, or the sound pack's sounds).
 * Uses MediaPlayer for longer audio files (10+ seconds) with proper audio focus management.
 *
 * Note: SoundPool has a 1MB limit per sound (~5.6 seconds at 44.1kHz stereo),
 * so MediaPlayer is used instead for tournament notifications.
 *
 * While a cue sounds, [ducking] dips the music under it; the cue also asks for transient, duckable
 * audio focus, so another app's music dips too.
 */
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
     * Call this early to ensure sound is ready when needed.
     */
    fun preloadSound(@RawRes soundResId: Int) {
        try {
            releasePlayer()
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

    /** A player for [soundResId] at the current volume, preparing; it starts once ready if [startWhenReady]. */
    private fun newPrepared(@RawRes soundResId: Int, startWhenReady: Boolean): MediaPlayer = newPlayer().apply {
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
     * Releases all audio resources.
     * Should be called when the app is shutting down or audio is no longer needed.
     */
    fun release() {
        releasePlayer()
        try {
            ended()
        } catch (ignored: Exception) {
            // Ignore any errors during cleanup
        }
    }

    /**
     * Internal helper to release the MediaPlayer instance.
     */
    private fun releasePlayer() {
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
}
