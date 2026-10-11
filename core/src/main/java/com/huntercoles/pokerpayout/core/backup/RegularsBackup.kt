package com.huntercoles.pokerpayout.core.backup

import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.domain.history.Season
import com.huntercoles.pokerpayout.core.domain.players.KnownPlayer
import com.huntercoles.pokerpayout.core.domain.players.PlayerMerges
import com.huntercoles.pokerpayout.core.domain.players.RegularsCodec
import com.huntercoles.pokerpayout.core.domain.players.RegularsStore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

/**
 * The regulars (PP-110): the names the Bank has used and the names merged in History, as the store
 * keeps them ([RegularsCodec]): `{"players": [{"name": "Dana", "seen": "2026-10-05"}], "merges":
 * [{"from": "Mike R.", "into": "Mike"}]}`. The players of saved nights come back with the nights.
 *
 * Merging adds the names the phone doesn't know yet, and the merges of spellings it hasn't merged
 * itself (one that would make two names one twice over is left out); Undo takes out what it added.
 */
class RegularsBackup @Inject constructor(private val store: RegularsStore) : BackupSection {
    override val key: String = KEY
    override val version: Int = VERSION
    override val order: Int = ORDER

    override fun export(): JsonObject = JsonObject(
        mapOf(
            PLAYERS to RegularsCodec.encodePlayers(store.players.value),
            MERGES to RegularsCodec.encodeMerges(store.merges.value),
        ),
    )

    override fun read(payload: JsonObject, version: Int): SectionData {
        val players = payload[PLAYERS] as? JsonArray ?: throw BackupException(BackupProblem.Damaged)
        val merges = payload[MERGES] as? JsonArray ?: throw BackupException(BackupProblem.Damaged)
        val readPlayers = RegularsCodec.decodePlayers(players)
        val readMerges = RegularsCodec.decodeMerges(merges)
        return Names(
            players = readPlayers.items.distinctBy { Season.key(it.name) },
            merges = PlayerMerges.of(readMerges.items),
            skipped = readPlayers.skipped + readMerges.skipped,
        )
    }

    private inner class Names(
        private val players: List<KnownPlayer>,
        private val merges: PlayerMerges,
        override val skipped: Int,
    ) : SectionData {
        /** "12 names in Regulars": each person once, under any of their spellings. */
        private val count = (players.map { it.name } + merges.all.flatMap { listOf(it.from, it.into) })
            .map(merges::key)
            .toSet()
            .size

        override val line: BackupLine? = BackupLine.Counted(R.plurals.backup_line_regulars, count).takeIf { count > 0 }
        override val restartsApp: Boolean = false
        override val canMerge: Boolean = true

        override fun merge(): Merged {
            val before = store.merges.value
            val added = store.addPlayers(players)
            val after = before + merges
            store.setMerges(after)
            val newMerges = after.all.count { merge -> before.all.none { Season.key(it.from) == Season.key(merge.from) } }
            return Merged(added.size + newMerges) {
                store.removePlayers(added)
                store.setMerges(before)
            }
        }

        override fun replace() = store.replaceAll(players, merges)
    }

    companion object {
        const val KEY = "regulars"
        const val VERSION = 1
        private const val ORDER = 15
        private const val PLAYERS = "players"
        private const val MERGES = "merges"
    }
}
