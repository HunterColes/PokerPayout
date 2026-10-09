package com.huntercoles.pokerpayout.tournament.domain.presets

import android.net.Uri
import com.huntercoles.pokerpayout.core.backup.BackupException
import com.huntercoles.pokerpayout.core.backup.BackupLine
import com.huntercoles.pokerpayout.core.backup.BackupProblem
import com.huntercoles.pokerpayout.core.backup.BackupSection
import com.huntercoles.pokerpayout.core.backup.Backups
import com.huntercoles.pokerpayout.core.backup.DocumentFiles
import com.huntercoles.pokerpayout.core.backup.MergeResult
import com.huntercoles.pokerpayout.core.backup.Merged
import com.huntercoles.pokerpayout.core.backup.SectionData
import com.huntercoles.pokerpayout.core.backup.safeFileName
import com.huntercoles.pokerpayout.core.coroutines.IoDispatcher
import com.huntercoles.pokerpayout.core.R as CoreR
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

/**
 * The presets (PP-032) in a backup, each the object the presets sheet saves ([PresetCodec]):
 * `{"version": 1, "presets": [{"format": 1, "id": 2, "name": "Friday", "money": {...}, ...}]}`.
 * A preset file, to share one with a friend, is a backup with this section alone ([PresetFiles]).
 *
 * Merging never changes a preset that's here. A preset from the file is added unless one here
 * already has its setup under its name (or under the "Friday 2" a merge before gave it); one whose
 * name is taken by another setup comes in as "Friday 2".
 */
class PresetsBackup @Inject constructor(private val store: PresetStore) : BackupSection {
    override val key: String = KEY
    override val version: Int = VERSION
    override val order: Int = ORDER

    override fun export(): JsonObject = payloadOf(store.presets.value)

    /** The section's fields for [presets]. */
    fun payloadOf(presets: List<TournamentPreset>): JsonObject = JsonObject(
        mapOf(PRESETS to JsonArray(presets.sortedBy { it.id }.map { Json.parseToJsonElement(PresetCodec.encode(it)) })),
    )

    override fun read(payload: JsonObject, version: Int): SectionData {
        val items = payload[PRESETS] as? JsonArray ?: throw BackupException(BackupProblem.Damaged)
        val presets = items.mapNotNull { PresetCodec.decode((it as? JsonObject)?.toString()) }
        if (presets.map { it.id }.toSet().size != presets.size) throw BackupException(BackupProblem.Damaged)
        return Presets(presets, skipped = items.size - presets.size)
    }

    private inner class Presets(private val presets: List<TournamentPreset>, override val skipped: Int) : SectionData {
        override val line: BackupLine? =
            BackupLine.Counted(CoreR.plurals.backup_line_presets, presets.size).takeIf { presets.isNotEmpty() }
        override val restartsApp: Boolean = false
        override val canMerge: Boolean = true

        override fun merge(): Merged {
            val here = store.presets.value
            val new = mutableListOf<TournamentPreset>()
            presets.sortedBy { it.id }.forEach { preset ->
                if ((here + new).none { it.isCopyOf(preset) }) {
                    new += preset.copy(name = store.freeName(preset.name, taken = new.map { it.name }))
                }
            }
            val added = store.addAll(new).map { it.id }
            return Merged(added.size) { store.deleteAll(added) }
        }

        override fun replace() = store.replaceAll(presets)
    }

    /** This preset is [other] already: the same setup, under its name or the "Name 2" a merge gives it. */
    private fun TournamentPreset.isCopyOf(other: TournamentPreset): Boolean {
        val copyName = Regex("${Regex.escape(other.name)} \\d+", RegexOption.IGNORE_CASE)
        return setup == other.setup && (name.equals(other.name, ignoreCase = true) || copyName.matches(name))
    }

    companion object {
        const val KEY = "presets"
        const val VERSION = 1
        private const val ORDER = 0
        private const val PRESETS = "presets"
    }
}

/** A preset as a file to share: its name ("Friday.json") and its text. */
class PresetFile(val name: String, val text: String) {
    companion object {
        const val MIME_TYPE = "application/json"
    }
}

/**
 * Presets as small files (PP-032): one shared with a friend, and a file of presets opened and added
 * here. A preset file is a backup holding only presets ([PresetsBackup]), so the Backup screen opens
 * one too.
 */
class PresetFiles @Inject constructor(
    private val backups: Backups,
    private val section: PresetsBackup,
    private val files: DocumentFiles,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    fun fileOf(preset: TournamentPreset): PresetFile =
        PresetFile(name = safeFileName("${preset.name}.json"), text = backups.export(section, section.payloadOf(listOf(preset))))

    /**
     * Adds the presets in the file at [uri] ([PresetsBackup]'s merge). Throws [BackupException]; a file
     * without presets is [BackupProblem.Empty].
     */
    suspend fun import(uri: Uri): MergeResult = withContext(io) {
        val opened = backups.open(files.read(uri))
        if (PresetsBackup.KEY !in opened.keys) throw BackupException(BackupProblem.Empty)
        backups.merge(opened, keys = setOf(PresetsBackup.KEY))
    }
}
