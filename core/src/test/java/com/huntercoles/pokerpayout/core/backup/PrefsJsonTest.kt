package com.huntercoles.pokerpayout.core.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

/**
 * Settings in a backup ([PrefsJson]): every type SharedPreferences holds comes back as exactly that
 * type and value (a Float stays a Float, a Long beyond what a double holds stays exact), and a value
 * that isn't what its type says makes the file damaged. A type from a later version is skipped.
 */
class PrefsJsonTest {

    private fun roundTrip(values: Map<String, Any>): Map<String, Any> =
        PrefsJson.decode(Json.parseToJsonElement(PrefsJson.encode(values).toString()).jsonObject)

    private fun decode(json: String): Map<String, Any> = PrefsJson.decode(Json.parseToJsonElement(json).jsonObject)

    @Test
    fun `every type comes back as itself`() {
        val values = mapOf(
            "name" to "Dana, \"Ace\" Zoë 🂡\nnext line",
            "empty" to "",
            "players" to 9,
            "negative" to -3,
            "cents" to 4_000L,
            "huge" to Long.MAX_VALUE,
            "odd" to 9_007_199_254_740_993L, // 2^53 + 1: a double can't hold it
            "volume" to 0.8f,
            "tiny" to 1.0E-7f,
            "muted" to true,
            "flash" to false,
            "colours" to setOf("red", "green"),
            "none" to emptySet<String>(),
        )
        val back = roundTrip(values)
        assertEquals(values, back)
        assertEquals(Long::class, back.getValue("cents")::class)
        assertEquals(Float::class, back.getValue("volume")::class)
        assertEquals(Int::class, back.getValue("players")::class)
    }

    @Test
    fun `the file names each value's type and reads plainly`() {
        val json = PrefsJson.encode(mapOf("player_count" to 9, "buy_in_cents" to 4_000L, "volume" to 0.8f, "is_muted" to false))
        assertEquals(
            """{"buy_in_cents":{"long":4000},"is_muted":{"boolean":false},"player_count":{"int":9},"volume":{"float":0.8}}""",
            json.toString(),
        )
    }

    @Test
    fun `a value SharedPreferences can't hold is left out`() {
        assertEquals(JsonObject(emptyMap()), PrefsJson.encode(mapOf("nothing" to null, "nan" to Float.NaN, "list" to listOf(1))))
    }

    @Test
    fun `a value that isn't what its type says is damaged`() {
        listOf(
            """{"a": {"int": "9"}}""",
            """{"a": {"int": 9.5}}""",
            """{"a": {"int": 3000000000}}""",
            """{"a": {"long": "4000"}}""",
            """{"a": {"float": "0.8"}}""",
            """{"a": {"boolean": "true"}}""",
            """{"a": {"boolean": 1}}""",
            """{"a": {"string": 9}}""",
            """{"a": {"string": null}}""",
            """{"a": {"strings": "red"}}""",
            """{"a": {"strings": [1]}}""",
            """{"a": 9}""",
            """{"a": {"int": 1, "long": 1}}""",
            """{"a": {}}""",
        ).forEach { json ->
            val problem = assertThrows<BackupException>(json) { decode(json) }.problem
            assertEquals(BackupProblem.Damaged, problem, json)
        }
    }

    @Test
    fun `a type from a later version is skipped, the rest read`() {
        assertEquals(mapOf("b" to 2), decode("""{"a": {"bytes": "AAEC"}, "b": {"int": 2}}"""))
    }
}
