package com.huntercoles.pokerpayout.core.domain.history

import com.huntercoles.pokerpayout.core.domain.history.Nights.night
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * A saved night as text (PP-037): every field comes back exactly, and anything that isn't a readable
 * format-1 night (corrupt, another format, a field missing or out of range) is skipped, never thrown.
 * Robolectric, for the platform's org.json.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NightCodecTest {

    /** Every field off its default, with text that needs escaping. */
    private val everyField = SavedNight(
        id = 12L,
        date = LocalDate.of(2026, 10, 5),
        structureName = "Friday \"deep\" stack, slow",
        prizePoolCents = 45_050L,
        players = listOf(
            NightPlayer("Dana ♠", 1, 5_000L, 2, 8_050L, 1, 1_000L, 22_500L, 3, 1_500L),
            NightPlayer("Marcus, Jr", 2, 5_000L, 0, 0L, 1, 1_000L, 13_050L, 1, 500L),
            NightPlayer("Line\nbreak", 3, 5_000L, 1, 4_000L, 0, 0L, 9_500L, 0, 0L),
        ),
    )

    private fun json(night: SavedNight = everyField) = JSONObject(NightCodec.encode(night))

    @Test
    fun `every field comes back exactly`() {
        assertEquals(everyField, NightCodec.decode(NightCodec.encode(everyField)))
        val unnamed = night("2025-12-31", listOf("Priya", "Theo"), id = 3, prizes = listOf(10_000L))
        assertEquals(unnamed, NightCodec.decode(NightCodec.encode(unnamed)))
    }

    @Test
    fun `players come back in finishing order, whatever order they were written in`() {
        val text = json().apply {
            val players = getJSONArray("players")
            put("players", JSONArray().put(players.get(2)).put(players.get(0)).put(players.get(1)))
        }.toString()
        assertEquals(everyField, NightCodec.decode(text))
    }

    @Test
    fun `unknown fields are ignored, so a later version can add some`() {
        val text = json().put("venue", "Dana's").apply { getJSONArray("players").getJSONObject(0).put("seat", 4) }.toString()
        assertEquals(everyField, NightCodec.decode(text))
    }

    @Test
    fun `anything else is skipped, never thrown`() {
        val broken = listOf(
            null,
            "",
            "   ",
            "{not json",
            "[1, 2]",
            json().put("format", 2).toString(),
            json().apply { remove("format") }.toString(),
            json().apply { remove("date") }.toString(),
            json().put("date", "2026-02-30").toString(),
            json().put("date", "last friday").toString(),
            json().put("id", 0).toString(),
            json().put("prizePool", -1).toString(),
            json().put("players", JSONArray()).toString(),
            json().put("players", "Dana").toString(),
            json().apply { getJSONArray("players").getJSONObject(1).put("place", 1) }.toString(),
            json().apply { getJSONArray("players").getJSONObject(2).put("place", 4) }.toString(),
            json().apply { getJSONArray("players").getJSONObject(0).put("name", "  ") }.toString(),
            json().apply { getJSONArray("players").getJSONObject(0).remove("prize") }.toString(),
            json().apply { getJSONArray("players").getJSONObject(1).put("rebuyCents", -100) }.toString(),
            json().apply { getJSONArray("players").getJSONObject(1).put("knockouts", "two") }.toString(),
        )
        broken.forEach { text -> assertNull("read: $text", NightCodec.decode(text)) }
    }

    /** The saved format: renaming a key would lose every saved night. */
    @Test
    fun `the saved keys`() {
        val saved = json()
        assertEquals(setOf("format", "id", "date", "structure", "prizePool", "players"), saved.keys().asSequence().toSet())
        assertEquals(1, saved.getInt("format"))
        assertEquals("2026-10-05", saved.getString("date"))
        assertEquals(
            setOf("name", "place", "entry", "rebuys", "rebuyCents", "addOns", "addOnCents", "prize", "knockouts", "bounties"),
            saved.getJSONArray("players").getJSONObject(0).keys().asSequence().toSet(),
        )
        // A night played with no preset has no structure key
        assertFalse(json(night("2026-01-01", listOf("Dana"), id = 1)).has("structure"))
    }
}
