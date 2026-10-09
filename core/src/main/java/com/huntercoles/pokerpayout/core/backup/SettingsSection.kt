package com.huntercoles.pokerpayout.core.backup

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.json.JsonObject

/**
 * A [SettingsGroup]'s files, saved whole: every key, with its type ([PrefsJson]), except the
 * [SettingsFile.phoneOnly] ones. Settings don't merge: Replace makes each file in the backup exactly
 * what the backup holds, then the app starts again so everything reads them afresh. A file of the
 * group that the backup doesn't have (one added in a later version) is left as it is.
 *
 * Keys are the app's own preference keys, which are never renamed, so an old backup puts back keys
 * an old version wrote, and the app's usual migrations bring them up to date on the next start.
 *
 * @param prepare turns a file's values into what the backup keeps (a running clock is saved paused).
 */
class SettingsSection(
    private val context: Context,
    private val group: SettingsGroup,
    private val prepare: (file: String, values: Map<String, Any?>) -> Map<String, Any?> = { _, values -> values },
) : BackupSection {
    override val key: String = group.key
    override val version: Int = VERSION
    override val order: Int = group.order

    private val files = BackupCatalog.filesOf(group)

    override fun export(): JsonObject = JsonObject(
        mapOf(
            FILES to JsonObject(
                files.associate { file ->
                    // Prepared from everything saved (a running clock needs its phone-only reading), then trimmed
                    file.name to PrefsJson.encode(prepare(file.name, prefs(file.name).all).filterKeys { it !in file.phoneOnly })
                },
            ),
        ),
    )

    override fun read(payload: JsonObject, version: Int): SectionData {
        val saved = payload[FILES] as? JsonObject ?: throw BackupException(BackupProblem.Damaged)
        // A file this version doesn't know (from a later one) is ignored
        val values = files.mapNotNull { file ->
            val json = saved[file.name] ?: return@mapNotNull null
            file to PrefsJson.decode(json as? JsonObject ?: throw BackupException(BackupProblem.Damaged))
        }
        return Settings(values)
    }

    private inner class Settings(private val values: List<Pair<SettingsFile, Map<String, Any>>>) : SectionData {
        override val line: BackupLine? = BackupLine.Named(group.line).takeIf { values.any { it.second.isNotEmpty() } }
        override val restartsApp: Boolean = true

        override fun replace() {
            values.forEach { (file, saved) -> write(prefs(file.name), saved, file.phoneOnly) }
        }
    }

    private fun prefs(name: String): SharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    companion object {
        const val VERSION = 1
        const val FILES = "files"

        /**
         * Makes [prefs] hold exactly [values], apart from the [keep] keys, which stay as they are. In one
         * synchronous write, so it is on disk before the app starts again.
         */
        fun write(prefs: SharedPreferences, values: Map<String, Any>, keep: Set<String>) {
            val editor = prefs.edit()
            prefs.all.keys.filter { it !in keep }.forEach { editor.remove(it) }
            values.filterKeys { it !in keep }.forEach { (key, value) -> editor.put(key, value) }
            editor.commit()
        }

        @Suppress("UNCHECKED_CAST") // PrefsJson reads a set of strings only
        private fun SharedPreferences.Editor.put(key: String, value: Any) {
            when (value) {
                is String -> putString(key, value)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Float -> putFloat(key, value)
                is Boolean -> putBoolean(key, value)
                is Set<*> -> putStringSet(key, value as Set<String>)
            }
        }
    }
}
