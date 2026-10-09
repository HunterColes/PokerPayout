package com.huntercoles.pokerpayout.core.backup

import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.domain.history.NightCodec
import com.huntercoles.pokerpayout.core.domain.history.NightStore
import com.huntercoles.pokerpayout.core.domain.history.SavedNight
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

/**
 * The nights in History (PP-037), and with them the season's points, which are worked out from the
 * nights. Each night is the object History saves ([NightCodec]), so a backup reads like the app's own
 * data: `{"version": 1, "nights": [{"format": 1, "id": 3, "date": "2026-10-05", ...}]}`.
 *
 * Merging adds every night the phone doesn't have yet. A night is the same night when everything
 * about it matches (the day, the setup's name, the pool and every player's line); its number on this
 * phone doesn't count, since two phones number their nights separately.
 */
class HistoryBackup @Inject constructor(private val store: NightStore) : BackupSection {
    override val key: String = KEY
    override val version: Int = VERSION
    override val order: Int = ORDER

    override fun export(): JsonObject = JsonObject(
        mapOf(NIGHTS to JsonArray(store.nights.value.sortedBy { it.id }.map { Json.parseToJsonElement(NightCodec.encode(it)) })),
    )

    override fun read(payload: JsonObject, version: Int): SectionData {
        val items = payload[NIGHTS] as? JsonArray ?: throw BackupException(BackupProblem.Damaged)
        val nights = items.mapNotNull { NightCodec.decode((it as? JsonObject)?.toString()) }
        if (nights.map { it.id }.toSet().size != nights.size) throw BackupException(BackupProblem.Damaged)
        return Nights(nights, skipped = items.size - nights.size)
    }

    private inner class Nights(private val nights: List<SavedNight>, override val skipped: Int) : SectionData {
        override val line: BackupLine? =
            BackupLine.Counted(R.plurals.backup_line_nights, nights.size).takeIf { nights.isNotEmpty() }
        override val restartsApp: Boolean = false
        override val canMerge: Boolean = true

        override fun merge(): Merged {
            val here = store.nights.value.map { it.copy(id = 0L) }.toSet()
            val new = nights.sortedBy { it.id }.filter { it.copy(id = 0L) !in here }.distinctBy { it.copy(id = 0L) }
            val added = store.addAll(new).map { it.id }
            return Merged(added.size) { store.deleteAll(added) }
        }

        override fun replace() = store.replaceAll(nights)
    }

    companion object {
        const val KEY = "history"
        const val VERSION = 1
        private const val ORDER = 10
        private const val NIGHTS = "nights"
    }
}
