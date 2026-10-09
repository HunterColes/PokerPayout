package com.huntercoles.pokerpayout.core.domain.history

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The saved nights (PP-037), in a SharedPreferences file of their own: one key per night, its JSON
 * ([NightCodec]) under `night_<id>`, and the next id. A night that can't be read (corrupt, or from a
 * format this version doesn't read) is skipped and left where it is; the others load as usual. Nothing
 * else in the app reads or clears this file: resetting the Bank or the tournament leaves History alone.
 */
@Singleton
class NightStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _nights = MutableStateFlow(readAll())

    /** Every saved night, the latest first. */
    val nights: StateFlow<List<SavedNight>> = _nights.asStateFlow()

    fun get(id: Long): SavedNight? = _nights.value.firstOrNull { it.id == id }

    /** Saves [night] as a new night, with an id of its own; returns what was saved. */
    fun add(night: SavedNight): SavedNight = night.copy(id = nextId()).also { put(it) }

    /** Writes [night] as it is: Undo putting a deleted one back. */
    fun put(night: SavedNight) {
        prefs.edit().putString("$KEY_PREFIX${night.id}", NightCodec.encode(night)).apply()
        _nights.value = readAll()
    }

    fun delete(id: Long) {
        prefs.edit().remove("$KEY_PREFIX$id").apply()
        _nights.value = readAll()
    }

    /** Saves [nights] as new nights, each with an id of its own, in one write (a backup merged in). */
    fun addAll(nights: List<SavedNight>): List<SavedNight> {
        var next = nextId()
        val saved = nights.map { night ->
            while (prefs.contains("$KEY_PREFIX$next")) next++
            night.copy(id = next++)
        }
        val editor = prefs.edit().putLong(NEXT_ID_KEY, next)
        saved.forEach { editor.putString("$KEY_PREFIX${it.id}", NightCodec.encode(it)) }
        editor.apply()
        _nights.value = readAll()
        return saved
    }

    /** Deletes [ids] in one write (Undo after a backup's nights were added). */
    fun deleteAll(ids: Collection<Long>) {
        val editor = prefs.edit()
        ids.forEach { editor.remove("$KEY_PREFIX$it") }
        editor.apply()
        _nights.value = readAll()
    }

    /**
     * Makes the saved nights exactly [nights], ids and all (a backup restored in place of these), in one
     * write that is on disk when it returns. Nights this version can't read go too.
     */
    fun replaceAll(nights: List<SavedNight>) {
        val editor = prefs.edit().clear()
        nights.forEach { editor.putString("$KEY_PREFIX${it.id}", NightCodec.encode(it)) }
        editor.commit()
        _nights.value = readAll()
    }

    /** Every readable night; one whose key and id disagree is as unreadable as a corrupt one. */
    private fun readAll(): List<SavedNight> = prefs.all.mapNotNull { (key, value) ->
        val id = key.removePrefix(KEY_PREFIX).toLongOrNull()?.takeIf { key.startsWith(KEY_PREFIX) }
        NightCodec.decode(value as? String)?.takeIf { it.id == id }
    }.sortedWith(compareByDescending<SavedNight> { it.date }.thenByDescending { it.id })

    /**
     * Never an id in use, nor one a deleted night had (Undo can bring that one back), nor the key of a
     * night this version can't read (it is left as it is).
     */
    private fun nextId(): Long {
        var next = maxOf(prefs.getLong(NEXT_ID_KEY, 1L), (_nights.value.maxOfOrNull { it.id } ?: 0L) + 1L)
        while (prefs.contains("$KEY_PREFIX$next")) next++
        prefs.edit().putLong(NEXT_ID_KEY, next + 1L).apply()
        return next
    }

    private companion object {
        // The saved format: a new file and new keys (PP-037); never rename them.
        const val PREFS_NAME = "night_history"
        const val KEY_PREFIX = "night_"
        const val NEXT_ID_KEY = "next_night_id"
    }
}
