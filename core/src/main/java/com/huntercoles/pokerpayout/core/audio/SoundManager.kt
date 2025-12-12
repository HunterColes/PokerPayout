package com.huntercoles.pokerpayout.core.audio

import android.content.Context
import android.media.MediaPlayer
import androidx.annotation.RawRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages sound playback for the poker tournament app.
 * Handles resource cleanup and prevents memory leaks.
 */
@Singleton
class SoundManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null
    
    /**
     * Plays a sound effect from raw resources.
     * Automatically releases previous player if still playing.
     */
    fun playSound(@RawRes soundResId: Int) {
        try {
            // Release any existing player
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            
            // Create and start new player
            mediaPlayer = MediaPlayer.create(context, soundResId)?.apply {
                setOnCompletionListener {
                    release()
                    mediaPlayer = null
                }
                start()
            }
        } catch (e: Exception) {
            // Silently handle any playback errors
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }
    
    /**
     * Stops and releases all audio resources.
     * Should be called when app is backgrounded or destroyed.
     */
    fun release() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }
}
