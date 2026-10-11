package com.huntercoles.pokerpayout.core.domain.players

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * The regulars as JSON (PP-110), for their store and for backups: the names the Bank has used,
 * `[{"name": "Dana", "seen": "2026-10-05"}]`, and the merges made in History,
 * `[{"from": "Mike R.", "into": "Mike"}]`.
 *
 * Reading never throws: an entry that can't be read (a field missing, a date that isn't one, a
 * blank name) is left out and counted, and the others load. Unknown fields are ignored, so a later
 * version can add some. The keys at the foot of this file are the saved format: never rename them.
 */
internal object RegularsCodec {

    /** What was read, and how many entries couldn't be. */
    data class Read<T>(val items: List<T>, val skipped: Int)

    fun encodePlayers(players: List<KnownPlayer>): JsonArray = JsonArray(
        players.map { JsonObject(mapOf(NAME to JsonPrimitive(it.name), SEEN to JsonPrimitive(it.lastSeen.toString()))) },
    )

    fun encodeMerges(merges: PlayerMerges): JsonArray = JsonArray(
        merges.all.map { JsonObject(mapOf(FROM to JsonPrimitive(it.from), INTO to JsonPrimitive(it.into))) },
    )

    fun decodePlayers(array: JsonArray): Read<KnownPlayer> = read(array) { entry ->
        val name = PlayerNames.clean(entry.text(NAME) ?: return@read null)
        val seen = entry.text(SEEN)?.let { day -> date(day) } ?: return@read null
        KnownPlayer(name, seen).takeUnless { PlayerNames.isPlaceholder(name) }
    }

    fun decodeMerges(array: JsonArray): Read<Merge> = read(array) { entry ->
        val from = entry.text(FROM)?.trim().orEmpty()
        val into = entry.text(INTO)?.trim().orEmpty()
        Merge(from, into).takeIf { from.isNotEmpty() && into.isNotEmpty() }
    }

    /** The array in [text] (as the store saves it), or null when there is none or it isn't one. */
    fun array(text: String?): JsonArray? = text?.let {
        try {
            Json.parseToJsonElement(it) as? JsonArray
        } catch (ignored: SerializationException) {
            null
        } catch (ignored: IllegalArgumentException) {
            null
        }
    }

    private fun <T> read(array: JsonArray, entry: (JsonObject) -> T?): Read<T> {
        val items = array.mapNotNull { element -> (element as? JsonObject)?.let(entry) }
        return Read(items, skipped = array.size - items.size)
    }

    private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun date(text: String): LocalDate? = try {
        LocalDate.parse(text)
    } catch (ignored: DateTimeParseException) {
        null
    }

    // The saved format's keys.
    private const val NAME = "name"
    private const val SEEN = "seen"
    private const val FROM = "from"
    private const val INTO = "into"
}
