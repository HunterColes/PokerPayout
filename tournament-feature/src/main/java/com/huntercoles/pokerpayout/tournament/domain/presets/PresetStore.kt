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
@Suppress("TooManyFunctions") // one function per change the presets sheet and backups make, each one write
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

    /**
     * Saves [presets] as new presets, each with an id of its own, in one write (a backup merged in, a
     * preset file opened). Their names must already be free ([freeName]).
     */
    fun addAll(presets: List<TournamentPreset>): List<TournamentPreset> {
        var next = nextId()
        val saved = presets.map { preset ->
            while (prefs.contains("$KEY_PREFIX$next")) next++
            preset.copy(id = next++)
        }
        val editor = prefs.edit().putLong(NEXT_ID_KEY, next)
        saved.forEach { editor.putString("$KEY_PREFIX${it.id}", PresetCodec.encode(it)) }
        editor.apply()
        publish()
        return saved
    }

    /** Deletes [ids] in one write (Undo after presets were added from a file). */
    fun deleteAll(ids: Collection<Long>) {
        val editor = prefs.edit()
        ids.forEach { editor.remove("$KEY_PREFIX$it") }
        editor.apply()
        publish()
    }

    /** Makes the presets exactly [presets], ids and all (a backup restored in place of these), on disk when it returns. */
    fun replaceAll(presets: List<TournamentPreset>) {
        val editor = prefs.edit().clear()
        presets.forEach { editor.putString("$KEY_PREFIX${it.id}", PresetCodec.encode(it)) }
        editor.commit()
        publish()
    }

    /**
     * [name] if no preset has it (whatever its case), else the first of "[name] 2", "[name] 3"... that is
     * free, among the presets here and [taken] (names about to be added alongside).
     */
    fun freeName(name: String, taken: Collection<String> = emptyList()): String {
        val clean = TournamentPreset.cleanName(name)
        val used = (_presets.value.map { it.name } + taken).map { it.lowercase() }.toSet()
        if (clean.lowercase() !in used) return clean
        return generateSequence(2) { it + 1 }
            .map { number ->
                val suffix = " $number"
                TournamentPreset.cleanName(clean.take(TournamentPreset.MAX_NAME_LENGTH - suffix.length) + suffix)
            }
            .first { it.lowercase() !in used }
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
