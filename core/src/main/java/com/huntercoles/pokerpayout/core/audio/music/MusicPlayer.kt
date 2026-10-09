package com.huntercoles.pokerpayout.core.audio.music

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.huntercoles.pokerpayout.core.audio.CueDucking
import com.huntercoles.pokerpayout.core.preferences.MusicPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** What the music is doing, for the screens. */
data class MusicState(
    val playlist: Playlist = Playlist(),
    /** Playing, or about to (the song is loading). */
    val playing: Boolean = false,
    /** Songs whose files can't be found or played (moved, deleted, on a card that's out). */
    val missing: Set<String> = emptySet(),
) {
    /** Songs in the list, and not one of them can be played. */
    val nothingPlayable: Boolean get() = !playlist.isEmpty && playlist.tracks.all { it.ref in missing }
}

/** What the tournament clock may do to the music ("Play with the clock"). */
interface MusicControls {
    val isPlaying: Boolean

    fun play()

    fun pause()

    /** Quieter for a break, or back to the music's own volume. */
    fun setQuiet(quiet: Boolean)
}

/** How loud the music plays: its own volume, less on a quiet break, much less under a cue or another app's sound. */
object MusicVolume {
    /** A break played quieter keeps this much of the volume. */
    const val QUIET = 0.4f

    /** Under a cue or another app's notification, the music keeps this much. */
    const val DUCKED = 0.2f

    fun of(volume: Float, quiet: Boolean, ducked: Boolean): Float =
        volume.coerceIn(0f, 1f) * (if (quiet) QUIET else 1f) * (if (ducked) DUCKED else 1f)
}

/**
 * The music player (Tools > Sound > Music): one platform [MediaPlayer] at a time, playing the
 * [Playlist] from [MusicPreferences], which it saves whenever the list changes (never per tick).
 *
 * The platform player keeps the APK small and reads every format the phone does, from the files the
 * host picked as from bundled ones. It holds a partial wake lock only while a song plays, so the
 * music carries on with the screen off; that permission (WAKE_LOCK) is the live clock's already.
 *
 * Audio focus: it asks for full focus to play; another app's call or alarm pauses it until that's
 * over, another music app stops it, and a notification dips it. The clock's own cues dip it too,
 * through [CueDucking], which the cue player calls. Headphones coming out pause it.
 *
 * A song whose file has gone is marked missing and passed over; when none is left it stops.
 * Called on the main thread.
 */
@Singleton
@Suppress("TooManyFunctions") // a player: one small function per button, plus the clock's and the cues' hooks
class MusicPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: MusicPreferences,
    private val library: MusicLibrary,
) : MusicControls, CueDucking {

    /** Makes each player; a test puts in one that records what it is asked to do. */
    internal var newPlayer: () -> MediaPlayer = { MediaPlayer() }

    /** Deals the shuffle; a test seeds it. */
    internal var random: Random = Random.Default

    private val _state = MutableStateFlow(MusicState(playlist = preferences.getPlaylist()))
    val state: StateFlow<MusicState> = _state.asStateFlow()

    override val isPlaying: Boolean get() = _state.value.playing

    private var player: MediaPlayer? = null

    /** The song [player] holds, ready or loading. */
    private var loadedRef: String? = null
    private var prepared = false

    /** Music is wanted: start once loaded, and go on to the next song at the end of this one. */
    private var wantPlaying = false

    private var quiet = false
    private var cueDucked = false
    private var focusDucked = false
    private var hasFocus = false

    /** Paused for a call or an alarm; it plays again when that's over. */
    private var pausedForFocus = false

    private val handler = Handler(Looper.getMainLooper())

    /** A cue that never says it ended (its player failed) lets the music back up after this long. */
    private val cueOver = Runnable { cueEnded() }

    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val audioManager: AudioManager? by lazy { context.getSystemService(AudioManager::class.java) }

    private val focusRequest: AudioFocusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(::onFocusChange, handler)
            .build()
    }

    /** Headphones out: pause, as every music player does, rather than play to the room. */
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = pause()
    }
    private var listeningForNoisy = false

    // ------------------------------------------------------------------ the buttons

    /** Plays the current song from where it was paused (the first song with none yet). */
    override fun play() {
        val ref = _state.value.playlist.current?.ref
        if (ref == null || !requestFocus()) return
        wantPlaying = true
        pausedForFocus = false
        setPlaying(true)
        if (ref == loadedRef && prepared) startLoaded() else load(ref, startAt = preferences.getPosition(ref))
    }

    override fun pause() {
        pauseHere()
        pausedForFocus = false
        abandonFocus()
    }

    fun toggle() = if (isPlaying) pause() else play()

    /** On to the next song (a new shuffle pass after the last; with repeat off, it stops there). */
    fun next() {
        val step = _state.value.playlist.advance(manual = true, missing(), random)
        moveTo(step.playlist, ended = step.ended)
    }

    /** Back to the start of the song, or, in its first seconds, to the song before. */
    fun previous() {
        val loaded = player?.takeIf { prepared }
        val at = loaded?.let { runCatching { it.currentPosition }.getOrDefault(0) } ?: 0
        if (loaded != null && at > RESTART_MILLIS) {
            runCatching { loaded.seekTo(0) }
            preferences.setPosition(loadedRef, 0)
        } else {
            moveTo(_state.value.playlist.previous(missing()), ended = false)
        }
    }

    /** Plays song [ref] from its start (a tap on it in the list). One marked missing is tried again. */
    fun playTrack(ref: String) {
        val playlist = _state.value.playlist
        if (playlist.tracks.none { it.ref == ref }) return
        if (ref != loadedRef) releasePlayer()
        preferences.setPosition(null, 0)
        _state.update { it.copy(missing = it.missing - ref) }
        save(playlist.select(ref))
        play()
    }

    // ------------------------------------------------------------------ the list

    fun add(tracks: List<MusicTrack>) {
        _state.update { it.copy(missing = it.missing - tracks.map { track -> track.ref }.toSet()) }
        save(_state.value.playlist.add(tracks, random))
    }

    /** Takes song [ref] off the list; if it was playing, the next one plays. */
    fun remove(ref: String) {
        val before = _state.value.playlist
        val after = before.remove(ref)
        if (after == before) return
        library.forget(ref)
        _state.update { it.copy(missing = it.missing - ref) }
        if (ref == before.currentRef) moveTo(after, ended = after.isEmpty) else save(after)
    }

    fun move(from: Int, to: Int) = save(_state.value.playlist.move(from, to))

    fun setShuffle(on: Boolean) = save(_state.value.playlist.withShuffle(on, random))

    fun setRepeat(mode: RepeatMode) = save(_state.value.playlist.copy(repeat = mode))

    /** What a look at the files found: [refs] can't be read. Playing carries on. */
    fun setMissing(refs: Set<String>) = _state.update { it.copy(missing = refs) }

    // ------------------------------------------------------------------ volume

    fun setVolume(volume: Float) {
        preferences.setVolume(volume)
        applyVolume()
    }

    override fun setQuiet(quiet: Boolean) {
        if (this.quiet == quiet) return
        this.quiet = quiet
        applyVolume()
    }

    override fun cueStarted() {
        cueDucked = true
        handler.removeCallbacks(cueOver)
        handler.postDelayed(cueOver, MAX_CUE_MILLIS)
        applyVolume()
    }

    override fun cueEnded() {
        handler.removeCallbacks(cueOver)
        cueDucked = false
        applyVolume()
    }

    // ------------------------------------------------------------------ inside

    private fun missing(): Set<String> = _state.value.missing

    private fun save(playlist: Playlist) {
        _state.update { it.copy(playlist = playlist) }
        preferences.setPlaylist(playlist)
    }

    /** Goes to [playlist]'s current song: playing it if music was playing and the list hasn't [ended]. */
    private fun moveTo(playlist: Playlist, ended: Boolean) {
        val keepPlaying = wantPlaying && !ended
        releasePlayer()
        preferences.setPosition(null, 0)
        save(playlist)
        val next = playlist.current
        if (keepPlaying && next != null) load(next.ref, startAt = 0) else stopped()
    }

    private fun load(ref: String, startAt: Int) {
        releasePlayer()
        val fresh = newPlayer()
        player = fresh
        loadedRef = ref
        // The CPU stays awake while a song plays, so it carries on with the screen off (best effort)
        runCatching { fresh.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK) }
        val opened = runCatching { fresh.setAudioAttributes(attributes) }.isSuccess && library.open(fresh, ref)
        if (!opened) {
            songFailed(ref)
            return
        }
        fresh.setOnPreparedListener { if (it === player) onPrepared(startAt) }
        fresh.setOnCompletionListener { if (it === player) songEnded() }
        fresh.setOnErrorListener { failed, _, _ ->
            if (failed === player) songFailed(ref)
            true
        }
        applyVolume()
        runCatching { fresh.prepareAsync() }.onFailure { songFailed(ref) }
    }

    private fun onPrepared(startAt: Int) {
        prepared = true
        if (startAt > 0) runCatching { player?.seekTo(startAt) }
        if (wantPlaying) startLoaded()
    }

    private fun startLoaded() {
        val loaded = player ?: return
        applyVolume()
        runCatching { loaded.start() }
            .onSuccess { setPlaying(true) }
            .onFailure { songFailed(loadedRef) }
    }

    private fun songEnded() {
        val step = _state.value.playlist.advance(manual = false, missing(), random)
        moveTo(step.playlist, ended = step.ended)
    }

    /** Song [ref] can't be played: marked missing, and the next one that can plays instead. */
    private fun songFailed(ref: String?) {
        releasePlayer()
        if (ref != null) _state.update { it.copy(missing = it.missing + ref) }
        val step = _state.value.playlist.advance(manual = true, missing(), random)
        moveTo(step.playlist, ended = step.ended || _state.value.nothingPlayable)
    }

    /** Pauses, keeping the place in the song for next time. */
    private fun pauseHere() {
        wantPlaying = false
        val loaded = player?.takeIf { prepared }
        if (loaded != null) {
            runCatching { if (loaded.isPlaying) loaded.pause() }
            preferences.setPosition(loadedRef, runCatching { loaded.currentPosition }.getOrDefault(0))
        }
        setPlaying(false)
    }

    private fun stopped() {
        wantPlaying = false
        pausedForFocus = false
        setPlaying(false)
        abandonFocus()
    }

    private fun setPlaying(playing: Boolean) {
        _state.update { it.copy(playing = playing) }
        listenForNoisy(playing)
    }

    private fun applyVolume() {
        val level = MusicVolume.of(preferences.getVolume(), quiet, ducked = cueDucked || focusDucked)
        player?.let { runCatching { it.setVolume(level, level) } }
    }

    private fun releasePlayer() {
        player?.let { old ->
            runCatching {
                if (prepared && old.isPlaying) old.stop()
                old.reset()
                old.release()
            }
        }
        player = null
        loadedRef = null
        prepared = false
    }

    private fun requestFocus(): Boolean {
        if (!hasFocus) {
            hasFocus = audioManager?.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        return hasFocus
    }

    private fun abandonFocus() {
        if (hasFocus) audioManager?.abandonAudioFocusRequest(focusRequest)
        hasFocus = false
        focusDucked = false
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                focusDucked = false
                applyVolume()
                if (pausedForFocus) play()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                focusDucked = true
                applyVolume()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (isPlaying) {
                // Keep the focus request, so the music hears when the call is over
                pauseHere()
                pausedForFocus = true
            }
            AudioManager.AUDIOFOCUS_LOSS -> pause()
        }
    }

    private fun listenForNoisy(on: Boolean) {
        if (on == listeningForNoisy) return
        listeningForNoisy = on
        runCatching {
            if (on) {
                val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                ContextCompat.registerReceiver(context, noisy, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            } else {
                context.unregisterReceiver(noisy)
            }
        }
    }

    private companion object {
        /** Previous, this far into a song, starts it again; earlier, it goes to the song before. */
        const val RESTART_MILLIS = 3_000

        /** The longest a cue sounds: past this the music comes back up whatever the cue says. */
        const val MAX_CUE_MILLIS = 15_000L
    }
}
