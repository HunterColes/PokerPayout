package com.huntercoles.pokerpayout.tournament.domain.presets

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The saved presets (PP-032), in a SharedPreferences file of their own: one key per preset, its JSON
 * ([PresetCodec]) under `preset_<id>`, and the next id. A preset that can't be read (corrupt, or from
 * a format this version doesn't read) is skipped and left where it is; the others load as usual.
 */
@Singleton
class PresetStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _presets = MutableStateFlow(readAll())

    /** Every preset, the last used first. */
    val presets: StateFlow<List<TournamentPreset>> = _presets.asStateFlow()

    fun get(id: Long): TournamentPreset? = _presets.value.firstOrNull { it.id == id }

    /** The preset called [name], whatever its case and spacing, or null. */
    fun named(name: String): TournamentPreset? {
        val clean = TournamentPreset.cleanName(name)
        return _presets.value.firstOrNull { it.name.equals(clean, ignoreCase = true) }
    }

    /**
     * Saves [setup] as [name], used at [nowMillis]: a new preset, or in place of the one already called
     * that (it keeps its id). Returns what was saved.
     */
    fun save(name: String, setup: PresetSetup, nowMillis: Long): TournamentPreset {
        val clean = TournamentPreset.cleanName(name)
        val preset = TournamentPreset(id = named(clean)?.id ?: nextId(), name = clean, lastUsedMillis = nowMillis, setup = setup)
        put(preset)
        return preset
    }

    /** Writes [preset] as it is: a change, or Undo putting one back. */
    fun put(preset: TournamentPreset) {
        prefs.edit().putString("$KEY_PREFIX${preset.id}", PresetCodec.encode(preset)).apply()
        publish()
    }

    fun rename(id: Long, name: String) {
        get(id)?.let { put(it.copy(name = TournamentPreset.cleanName(name))) }
    }

    /** Loaded at [nowMillis]: it moves to the top of the list. */
    fun markUsed(id: Long, nowMillis: Long) {
        get(id)?.let { put(it.copy(lastUsedMillis = nowMillis)) }
    }

    fun delete(id: Long) {
        prefs.edit().remove("$KEY_PREFIX$id").apply()
        publish()
    }

    private fun publish() {
        _presets.value = readAll()
    }

    /** Every readable preset; one whose key and id disagree is as unreadable as a corrupt one. */
    private fun readAll(): List<TournamentPreset> = prefs.all.mapNotNull { (key, value) ->
        val id = key.removePrefix(KEY_PREFIX).toLongOrNull()?.takeIf { key.startsWith(KEY_PREFIX) }
        PresetCodec.decode(value as? String)?.takeIf { it.id == id }
    }.sortedWith(compareByDescending<TournamentPreset> { it.lastUsedMillis }.thenByDescending { it.id })

    /**
     * Never an id in use, nor one a deleted preset had (Undo can bring that one back), nor the key of
     * a preset this version can't read (it is left as it is).
     */
    private fun nextId(): Long {
        var next = maxOf(prefs.getLong(NEXT_ID_KEY, 1L), (_presets.value.maxOfOrNull { it.id } ?: 0L) + 1L)
        while (prefs.contains("$KEY_PREFIX$next")) next++
        prefs.edit().putLong(NEXT_ID_KEY, next + 1L).apply()
        return next
    }

    private companion object {
        // The saved format: a new file and new keys (PP-032); never rename them.
        const val PREFS_NAME = "tournament_presets"
        const val KEY_PREFIX = "preset_"
        const val NEXT_ID_KEY = "next_preset_id"
    }
}
