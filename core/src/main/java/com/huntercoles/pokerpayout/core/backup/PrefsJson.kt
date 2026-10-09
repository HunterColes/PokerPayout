package com.huntercoles.pokerpayout.core.backup

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * A SharedPreferences file's values as JSON, each with its type, so they come back exactly as they
 * were saved: `{"player_count": {"int": 9}, "buy_in_cents": {"long": 4000}, "volume": {"float": 0.8},
 * "is_muted": {"boolean": false}, "break_message": {"string": "Last rebuy"}}`. A set of strings is
 * `{"strings": ["a", "b"]}`.
 *
 * Reading is strict about the types it knows (an "int" that isn't a whole number in range makes the
 * file damaged) and skips a value of a type it doesn't know, so a later version could add one.
 */
object PrefsJson {
    const val STRING = "string"
    const val INT = "int"
    const val LONG = "long"
    const val FLOAT = "float"
    const val BOOLEAN = "boolean"
    const val STRINGS = "strings"

    /** [values] as one JSON object; a value of a type SharedPreferences can't hold is left out. */
    fun encode(values: Map<String, *>): JsonObject = JsonObject(
        values.entries
            .sortedBy { it.key }
            .mapNotNull { (key, value) -> typed(value)?.let { key to it } }
            .toMap(),
    )

    /** The values in [json]; throws [BackupException] ([BackupProblem.Damaged]) for a value that isn't what its type says. */
    fun decode(json: JsonObject): Map<String, Any> = json.mapNotNull { (key, element) ->
        val typed = element as? JsonObject ?: damaged()
        if (typed.size != 1) damaged()
        val (type, value) = typed.entries.first()
        read(type, value)?.let { key to it }
    }.toMap()

    private fun typed(value: Any?): JsonObject? = when (value) {
        is String -> tagged(STRING, JsonPrimitive(value))
        is Int -> tagged(INT, JsonPrimitive(value))
        is Long -> tagged(LONG, JsonPrimitive(value))
        // Written as the Float prints, so 0.8f is 0.8 in the file and reads back as 0.8f
        is Float -> if (value.isFinite()) tagged(FLOAT, JsonPrimitive(value)) else null
        is Boolean -> tagged(BOOLEAN, JsonPrimitive(value))
        is Set<*> -> tagged(STRINGS, JsonArray(value.filterIsInstance<String>().sorted().map(::JsonPrimitive)))
        else -> null
    }

    private fun tagged(type: String, value: JsonElement) = JsonObject(mapOf(type to value))

    private fun read(type: String, value: JsonElement): Any? = when (type) {
        STRING -> value.string()
        INT -> value.number()?.intOrNull ?: damaged()
        LONG -> value.number()?.longOrNull ?: damaged()
        FLOAT -> value.number()?.content?.toFloatOrNull()?.takeIf { it.isFinite() } ?: damaged()
        BOOLEAN -> value.number()?.booleanOrNull ?: damaged()
        STRINGS -> (value as? JsonArray ?: damaged()).map { it.string() }.toSet()
        else -> null // a type from a later version: skipped
    }

    private fun JsonElement.string(): String = (this as? JsonPrimitive)?.takeIf { it.isString }?.content ?: damaged()

    /** A primitive that isn't a string (numbers and true/false aren't quoted). */
    private fun JsonElement.number(): JsonPrimitive? = (this as? JsonPrimitive)?.takeUnless { it.isString }

    private fun damaged(): Nothing = throw BackupException(BackupProblem.Damaged)
}
