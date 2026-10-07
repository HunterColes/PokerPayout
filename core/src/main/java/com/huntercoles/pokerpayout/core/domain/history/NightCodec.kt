package com.huntercoles.pokerpayout.core.domain.history

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * A saved night as text (PP-037): one JSON object, versioned by [FORMAT_KEY], as presets are.
 *
 * Reading is strict and never throws: a night that isn't valid JSON, has another format number, lacks
 * a field or has one out of range (a date that isn't one, a negative amount, places that aren't 1 to
 * the number of players) comes back as null, and the store skips it. Unknown fields are ignored, so a
 * later version can add fields under the same format number.
 *
 * The keys at the foot of this file are the saved format: renaming one would lose every saved night.
 */
internal object NightCodec {
    const val FORMAT_KEY = "format"

    /** The format written. A change that older readers can't read takes the next number. */
    const val FORMAT = 1

    fun encode(night: SavedNight): String {
        val players = JSONArray()
        night.players.forEach { players.put(player(it)) }
        val json = JSONObject()
            .put(FORMAT_KEY, FORMAT)
            .put(ID, night.id)
            .put(DATE, night.date.toString())
            .put(POOL, night.prizePoolCents)
            .put(PLAYERS, players)
        night.structureName?.let { json.put(STRUCTURE, it) }
        return json.toString()
    }

    /** The night in [text], or null when it can't be read (see the class doc). */
    fun decode(text: String?): SavedNight? {
        if (text.isNullOrBlank()) return null
        return try {
            val json = JSONObject(text)
            if (json.optInt(FORMAT_KEY, 0) == FORMAT) read(json) else null
        } catch (ignored: JSONException) {
            null
        } catch (ignored: DateTimeParseException) {
            null
        }
    }

    private fun player(player: NightPlayer): JSONObject = JSONObject()
        .put(NAME, player.name)
        .put(PLACE, player.place)
        .put(ENTRY, player.entryCents)
        .put(REBUYS, player.rebuys)
        .put(REBUY_TOTAL, player.rebuyCents)
        .put(ADD_ONS, player.addOns)
        .put(ADD_ON_TOTAL, player.addOnCents)
        .put(PRIZE, player.prizeCents)
        .put(KNOCKOUTS, player.knockouts)
        .put(BOUNTIES, player.bountyCents)

    /** Format 1. Anything missing or out of range throws, which [decode] turns into a skip. */
    private fun read(json: JSONObject): SavedNight {
        val array = json.getJSONArray(PLAYERS)
        valid(array.length() in 1..MAX_PLAYERS, PLAYERS)
        val players = (0 until array.length()).map { readPlayer(array.getJSONObject(it)) }.sortedBy { it.place }
        valid(players.map { it.place } == (1..players.size).toList(), PLACE)
        return SavedNight(
            id = json.atLeast(ID, 1L),
            date = LocalDate.parse(json.getString(DATE)),
            structureName = json.optString(STRUCTURE, "").trim().ifEmpty { null },
            prizePoolCents = json.atLeast(POOL, 0L),
            players = players,
        )
    }

    private fun readPlayer(json: JSONObject): NightPlayer {
        val name = json.getString(NAME).trim()
        valid(name.isNotEmpty(), NAME)
        return NightPlayer(
            name = name,
            place = json.getInt(PLACE),
            entryCents = json.atLeast(ENTRY, 0L),
            rebuys = json.atLeast(REBUYS, 0),
            rebuyCents = json.atLeast(REBUY_TOTAL, 0L),
            addOns = json.atLeast(ADD_ONS, 0),
            addOnCents = json.atLeast(ADD_ON_TOTAL, 0L),
            prizeCents = json.atLeast(PRIZE, 0L),
            knockouts = json.atLeast(KNOCKOUTS, 0),
            bountyCents = json.atLeast(BOUNTIES, 0L),
        )
    }

    private fun JSONObject.atLeast(key: String, min: Int): Int = getInt(key).also { valid(it >= min, key) }

    private fun JSONObject.atLeast(key: String, min: Long): Long = getLong(key).also { valid(it >= min, key) }

    /** A value out of range makes the whole night unreadable, as a missing one does. */
    private fun valid(ok: Boolean, key: String) {
        if (!ok) throw JSONException("Night field out of range: $key")
    }

    /** More players than any home game has: a list longer than this is corrupt. */
    private const val MAX_PLAYERS = 1_000

    // The saved format's keys (format 1).
    private const val ID = "id"
    private const val DATE = "date"
    private const val STRUCTURE = "structure"
    private const val POOL = "prizePool"
    private const val PLAYERS = "players"
    private const val NAME = "name"
    private const val PLACE = "place"
    private const val ENTRY = "entry"
    private const val REBUYS = "rebuys"
    private const val REBUY_TOTAL = "rebuyCents"
    private const val ADD_ONS = "addOns"
    private const val ADD_ON_TOTAL = "addOnCents"
    private const val PRIZE = "prize"
    private const val KNOCKOUTS = "knockouts"
    private const val BOUNTIES = "bounties"
}
