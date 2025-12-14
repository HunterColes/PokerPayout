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
 * Manages sound playback for the poker tournament app.
 * Uses MediaPlayer for longer audio files (10+ seconds) with proper audio focus management.
 * 
 * Note: SoundPool has a 1MB limit per sound (~5.6 seconds at 44.1kHz stereo),
 * so MediaPlayer is used instead for tournament notifications.
 */
@Singleton
class SoundManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioPreferences: AudioPreferences
) {
    private var mediaPlayer: MediaPlayer? = null
    private var currentSoundResId: Int = -1
    private var isPrepared = false
    
    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    
    private val audioFocusRequest: AudioFocusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).run {
            setAudioAttributes(AudioAttributes.Builder().run {
                setUsage(AudioAttributes.USAGE_GAME)
                setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                build()
            })
            build()
        }
    }
    
    /**
     * Preloads a sound effect without playing it.
     * Call this early to ensure sound is ready when needed.
     */
    fun preloadSound(@RawRes soundResId: Int) {
        try {
            // Release any existing player
            releasePlayer()
            
            // Create and prepare new MediaPlayer
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                
                // Set data source
                val afd = context.resources.openRawResourceFd(soundResId)
                afd?.use {
                    setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
                
                // Set volume based on preferences
                val volume = audioPreferences.getVolume()
                setVolume(volume, volume)
                
                // Prepare asynchronously
                setOnPreparedListener {
                    isPrepared = true
                }
                
                setOnCompletionListener {
                    // Reset to beginning after completion so it can be played again
                    seekTo(0)
                    isPrepared = true
                    // Abandon audio focus after playback completes
                    audioManager.abandonAudioFocusRequest(audioFocusRequest)
                }
                
                setOnErrorListener { _, _, _ ->
                    // Log error silently and reset state
                    isPrepared = false
                    true // Return true to indicate we handled the error
                }
                
                prepareAsync()
            }
            
            currentSoundResId = soundResId
        } catch (e: Exception) {
            // Silently handle any loading errors
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
        try {
            // Check if muted
            if (audioPreferences.getIsMuted()) {
                return
            }
            
            // Request audio focus before playing
            val result = audioManager.requestAudioFocus(audioFocusRequest)
            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                // Failed to get audio focus, don't play
                return
            }
            
            // If already prepared with the right sound, just play it
            if (currentSoundResId == soundResId && isPrepared && mediaPlayer != null) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        player.seekTo(0) // Restart if already playing
                    }
                    player.start()
                }
                return
            }
            
            // Otherwise, prepare and play
            releasePlayer()
            
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                
                // Set data source
                val afd = context.resources.openRawResourceFd(soundResId)
                afd?.use {
                    setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
                
                // Set volume based on preferences
                val volume = audioPreferences.getVolume()
                setVolume(volume, volume)
                
                // Prepare and play immediately
                setOnPreparedListener { player ->
                    isPrepared = true
                    player.start()
                }
                
                setOnCompletionListener {
                    // Reset to beginning after completion
                    seekTo(0)
                    isPrepared = true
                    // Abandon audio focus after playback completes
                    audioManager.abandonAudioFocusRequest(audioFocusRequest)
                }
                
                setOnErrorListener { _, _, _ ->
                    // Log error silently and reset state
                    isPrepared = false
                    audioManager.abandonAudioFocusRequest(audioFocusRequest)
                    true
                }
                
                prepareAsync()
            }
            
            currentSoundResId = soundResId
        } catch (e: Exception) {
            // Silently handle any playback errors and release audio focus
            audioManager.abandonAudioFocusRequest(audioFocusRequest)
        }
    }
    
    /**
     * Releases all audio resources.
     * Should be called when the app is shutting down or audio is no longer needed.
     */
    fun release() {
        releasePlayer()
        // Abandon audio focus when releasing
        try {
            audioManager.abandonAudioFocusRequest(audioFocusRequest)
        } catch (e: Exception) {
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
        } catch (e: Exception) {
            // Ignore any errors during cleanup
        } finally {
            mediaPlayer = null
            currentSoundResId = -1
            isPrepared = false
        }
    }
}
