package com.huntercoles.pokerpayout.core.preferences

import android.content.Context
import android.content.SharedPreferences
import com.huntercoles.pokerpayout.core.audio.music.BreakMusic
import com.huntercoles.pokerpayout.core.audio.music.Playlist
import com.huntercoles.pokerpayout.core.audio.music.PlaylistCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The music's settings and playlist (Tools > Sound > Music), in a file of their own (`music_prefs`),
 * so nothing here can touch a key the chime or the clock saved. The keys at the foot of this file
 * are saved data: never rename one.
 */
@Singleton
class MusicPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _volume = MutableStateFlow(getVolume())
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _autoPlay = MutableStateFlow(getAutoPlay())

    /** "Play with the clock". */
    val autoPlay: StateFlow<Boolean> = _autoPlay.asStateFlow()

    private val _breakMusic = MutableStateFlow(getBreakMusic())
    val breakMusic: StateFlow<BreakMusic> = _breakMusic.asStateFlow()

    fun getPlaylist(): Playlist = PlaylistCodec.decode(prefs.getString(PLAYLIST_KEY, null))

    /** Saved when the playlist changes (songs, order, shuffle, repeat, the current song); never per tick. */
    fun setPlaylist(playlist: Playlist) {
        prefs.edit().putString(PLAYLIST_KEY, PlaylistCodec.encode(playlist)).apply()
    }

    /** The music's own volume, 0 to 1, apart from the chime's. */
    fun getVolume(): Float = prefs.getFloat(VOLUME_KEY, DEFAULT_VOLUME)

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        prefs.edit().putFloat(VOLUME_KEY, clamped).apply()
        _volume.value = clamped
    }

    fun getAutoPlay(): Boolean = prefs.getBoolean(AUTO_PLAY_KEY, false)

    fun setAutoPlay(on: Boolean) {
        prefs.edit().putBoolean(AUTO_PLAY_KEY, on).apply()
        _autoPlay.value = on
    }

    fun getBreakMusic(): BreakMusic =
        BreakMusic.entries.firstOrNull { it.name == prefs.getString(BREAK_MUSIC_KEY, null) } ?: DEFAULT_BREAK_MUSIC

    fun setBreakMusic(mode: BreakMusic) {
        prefs.edit().putString(BREAK_MUSIC_KEY, mode.name).apply()
        _breakMusic.value = mode
    }

    /** Where song [ref] was paused, to carry on from there; 0 for any other song. */
    fun getPosition(ref: String?): Int =
        if (ref != null && prefs.getString(POSITION_REF_KEY, null) == ref) prefs.getInt(POSITION_MS_KEY, 0) else 0

    /** Saved on a pause, not while playing. */
    fun setPosition(ref: String?, millis: Int) {
        prefs.edit().putString(POSITION_REF_KEY, ref).putInt(POSITION_MS_KEY, millis.coerceAtLeast(0)).apply()
    }

    companion object {
        const val FILE = "music_prefs"
        const val DEFAULT_VOLUME = 0.8f
        val DEFAULT_BREAK_MUSIC = BreakMusic.KEEP

        private const val PLAYLIST_KEY = "playlist"
        private const val VOLUME_KEY = "volume"
        private const val AUTO_PLAY_KEY = "auto_play"
        private const val BREAK_MUSIC_KEY = "break_music"
        private const val POSITION_REF_KEY = "position_ref"
        private const val POSITION_MS_KEY = "position_ms"
    }
}
