package com.huntercoles.pokerpayout.core.backup

import androidx.annotation.StringRes
import com.huntercoles.pokerpayout.core.R
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.time.Instant
import java.time.format.DateTimeParseException

/** Why a file can't be read or written, each with a plain message for the screen. */
enum class BackupProblem(@StringRes val message: Int) {
    /** Not a Poker Payout file at all (a photo, another app's JSON). */
    NotBackup(R.string.backup_problem_not_backup),

    /** A Poker Payout file that has been cut short or changed by hand. */
    Damaged(R.string.backup_problem_damaged),

    /** Written by a later version, in a layout this one can't read. */
    Newer(R.string.backup_problem_newer),

    TooBig(R.string.backup_problem_too_big),

    /** Read fine, but holds nothing this version can put back (or not the kind asked for). */
    Empty(R.string.backup_problem_empty),

    CantRead(R.string.backup_problem_cant_read),

    CantWrite(R.string.backup_problem_cant_write),
}

class BackupException(val problem: BackupProblem, cause: Throwable? = null) : Exception(problem.name, cause)

/** When and by which version a file was written. Either can be missing from a file written by hand. */
data class BackupMeta(val appVersion: String?, val created: Instant?)

/** One section as it stands in a file: the version it was written at and its fields. */
data class RawSection(val version: Int, val payload: JsonObject)

/** A file's header and sections, before any section has read its own. */
data class RawBackup(val meta: BackupMeta, val sections: Map<String, RawSection>)

/**
 * The backup file (and a preset file, which is a backup with only its presets): UTF-8 JSON, written
 * and read through the system's file picker.
 *
 * ```
 * {
 *   "format": "com.huntercoles.pokerpayout.backup",
 *   "schema": 1,
 *   "appVersion": "1.4.0",
 *   "created": "2026-10-08T21:14:03Z",
 *   "sections": {
 *     "presets": { "version": 1, "presets": [ ... ] },
 *     "history": { "version": 1, "nights": [ ... ] },
 *     "game":    { "version": 1, "files": { "tournament_prefs": { "player_count": { "int": 9 }, ... } } },
 *     ...
 *   }
 * }
 * ```
 *
 * - [SCHEMA] is the layout of this envelope. It only goes up for a change older versions can't read;
 *   a file with a higher one is refused with [BackupProblem.Newer].
 * - Each section carries its own `version` ([BackupSection.version]). A section newer than this app
 *   reads is left out, and the rest restores.
 * - Unknown fields and unknown sections are ignored, so a later version can add both.
 * - Amounts are whole cents, dates ISO 8601. Settings are typed values ([PrefsJson]).
 */
object BackupJson {
    const val FORMAT = "com.huntercoles.pokerpayout.backup"
    const val SCHEMA = 1
    const val MIME_TYPE = "application/json"

    /** Far more than ten years of weekly nights; a bigger file isn't a backup. */
    const val MAX_BYTES = 10 * 1024 * 1024

    /** The deepest a backup nests is about six; anything far deeper is damaged, not read. */
    private const val MAX_DEPTH = 32

    private const val KEY_FORMAT = "format"
    private const val KEY_SCHEMA = "schema"
    private const val KEY_APP_VERSION = "appVersion"
    private const val KEY_CREATED = "created"
    private const val KEY_SECTIONS = "sections"
    const val KEY_VERSION = "version"

    /** A file saved by an editor that marks UTF-8 starts with one; it isn't part of the JSON. */
    private val BYTE_ORDER_MARK = Char(0xFEFF)

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /** The file for [sections] (key to payload, each written at its [BackupSection.version]). */
    fun write(meta: BackupMeta, sections: Map<String, Pair<Int, JsonObject>>): String {
        val header = buildMap<String, JsonElement> {
            put(KEY_FORMAT, JsonPrimitive(FORMAT))
            put(KEY_SCHEMA, JsonPrimitive(SCHEMA))
            meta.appVersion?.let { put(KEY_APP_VERSION, JsonPrimitive(it)) }
            meta.created?.let { put(KEY_CREATED, JsonPrimitive(it.toString())) }
        }
        val body = sections.mapValues { (_, section) ->
            val (version, payload) = section
            JsonObject(mapOf(KEY_VERSION to JsonPrimitive(version)) + payload)
        }
        return json.encodeToString(JsonElement.serializer(), JsonObject(header + (KEY_SECTIONS to JsonObject(body)))) + "\n"
    }

    /** The header and sections of [text]; throws [BackupException] when it isn't a backup this version can read. */
    fun read(text: String): RawBackup {
        if (text.length > MAX_BYTES) fail(BackupProblem.TooBig)
        val root = parse(text)
        if ((root[KEY_FORMAT] as? JsonPrimitive)?.content != FORMAT) fail(BackupProblem.NotBackup)
        val schema = (root[KEY_SCHEMA] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull ?: damaged()
        when {
            schema > SCHEMA -> fail(BackupProblem.Newer)
            schema < 1 -> damaged()
        }
        val sections = root[KEY_SECTIONS] as? JsonObject ?: damaged()
        return RawBackup(
            meta = BackupMeta(appVersion = root.text(KEY_APP_VERSION), created = root.text(KEY_CREATED)?.let(::instant)),
            sections = sections.mapValues { (_, section) -> rawSection(section) },
        )
    }

    private fun parse(text: String): JsonObject {
        val trimmed = text.trimStart(BYTE_ORDER_MARK, ' ', '\n', '\r', '\t')
        if (!trimmed.startsWith("{")) fail(BackupProblem.NotBackup)
        // A file that starts like a backup but won't parse was cut short or edited: damaged
        if (depthOf(trimmed) > MAX_DEPTH) damaged()
        val element = try {
            Json.parseToJsonElement(trimmed)
        } catch (expected: SerializationException) {
            fail(if (FORMAT in trimmed) BackupProblem.Damaged else BackupProblem.NotBackup, expected)
        }
        return element as? JsonObject ?: fail(BackupProblem.NotBackup)
    }

    private fun rawSection(element: JsonElement): RawSection {
        val section = element as? JsonObject ?: damaged()
        val version = (section[KEY_VERSION] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull ?: damaged()
        if (version < 1) damaged()
        return RawSection(version, JsonObject(section - KEY_VERSION))
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun instant(text: String): Instant? = try {
        Instant.parse(text)
    } catch (ignored: DateTimeParseException) {
        null
    }

    /** How deeply [text]'s objects and arrays nest, outside strings, so a hostile file can't exhaust the stack. */
    private fun depthOf(text: String): Int {
        var depth = 0
        var deepest = 0
        var inString = false
        var escaped = false
        for (char in text) {
            when {
                escaped -> escaped = false
                inString && char == '\\' -> escaped = true
                char == '"' -> inString = !inString
                inString -> Unit
                char == '{' || char == '[' -> deepest = maxOf(deepest, ++depth)
                char == '}' || char == ']' -> depth--
            }
        }
        return deepest
    }

    private fun damaged(): Nothing = fail(BackupProblem.Damaged)

    private fun fail(problem: BackupProblem, cause: Throwable? = null): Nothing = throw BackupException(problem, cause)
}
