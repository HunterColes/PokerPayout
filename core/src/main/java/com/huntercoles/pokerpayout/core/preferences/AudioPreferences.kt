package com.huntercoles.pokerpayout.core.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("audio_prefs", Context.MODE_PRIVATE)
    
    private val _volume = MutableStateFlow(getVolume())
    val volume: Flow<Float> = _volume.asStateFlow()
    
    private val _isMuted = MutableStateFlow(getIsMuted())
    val isMuted: Flow<Boolean> = _isMuted.asStateFlow()
    
    fun setVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        prefs.edit().putFloat(VOLUME_KEY, clampedVolume).apply()
        _volume.value = clampedVolume
    }
    
    fun getVolume(): Float {
        return prefs.getFloat(VOLUME_KEY, DEFAULT_VOLUME)
    }
    
    fun setMuted(muted: Boolean) {
        prefs.edit().putBoolean(IS_MUTED_KEY, muted).apply()
        _isMuted.value = muted
    }
    
    fun getIsMuted(): Boolean {
        return prefs.getBoolean(IS_MUTED_KEY, false)
    }
    
    fun toggleMute() {
        setMuted(!getIsMuted())
    }
    
    companion object {
        private const val VOLUME_KEY = "volume"
        private const val IS_MUTED_KEY = "is_muted"
        private const val DEFAULT_VOLUME = 1.0f
    }
}
