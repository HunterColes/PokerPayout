package com.huntercoles.pokerpayout.core.backup

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The backup file's envelope ([BackupJson]): what it writes reads back; a file that isn't a backup,
 * is damaged, or comes from a later layout is refused with a plain reason; fields and sections it
 * doesn't know are ignored.
 */
class BackupJsonTest {

    private val meta = BackupMeta(appVersion = "1.4.0", created = Instant.parse("2026-10-08T21:14:03Z"))
    private val presets = JsonObject(mapOf("presets" to JsonPrimitive("x")))

    private fun problemOf(text: String): BackupProblem =
        assertThrows<BackupException>(text.take(60)) { BackupJson.read(text) }.problem

    @Test
    fun `what it writes reads back, sections with their versions`() {
        val text = BackupJson.write(meta, mapOf("presets" to (1 to presets), "game" to (3 to JsonObject(emptyMap()))))
        val back = BackupJson.read(text)
        assertEquals(meta, back.meta)
        assertEquals(RawSection(1, presets), back.sections["presets"])
        assertEquals(RawSection(3, JsonObject(emptyMap())), back.sections["game"])
    }

    @Test
    fun `the file opens with its format and schema, indented for reading`() {
        val text = BackupJson.write(meta, mapOf("presets" to (1 to presets)))
        assertTrue(text.startsWith("{\n  \"format\": \"com.huntercoles.pokerpayout.backup\",\n  \"schema\": 1,\n"), text)
        assertTrue("\"created\": \"2026-10-08T21:14:03Z\"" in text, text)
        assertTrue(text.endsWith("}\n"))
    }

    @Test
    fun `a file without a version or date still reads`() {
        val back = BackupJson.read(
            """{"format": "com.huntercoles.pokerpayout.backup", "schema": 1, "created": "last week", "sections": {}}""",
        )
        assertNull(back.meta.appVersion)
        assertNull(back.meta.created)
    }

    @Test
    fun `fields and sections it doesn't know are ignored`() {
        val back = BackupJson.read(
            """
            {"format": "com.huntercoles.pokerpayout.backup", "schema": 1, "device": "Pixel", "sections": {
              "presets": {"version": 1, "presets": [], "note": "later"},
              "music": {"version": 4, "playlists": []}
            }}
            """.trimIndent(),
        )
        assertEquals(setOf("presets", "music"), back.sections.keys)
        assertEquals(1, back.sections.getValue("presets").version)
    }

    @Test
    fun `a byte order mark and leading space are fine`() {
        val text = Char(0xFEFF) + "\n  " + BackupJson.write(meta, emptyMap())
        assertEquals(meta, BackupJson.read(text).meta)
    }

    @Test
    fun `a file that isn't a backup says so`() {
        listOf(
            "",
            "hello",
            "date,structure,prize_pool\r\n2026-10-05,,100.00\r\n",
            "[1, 2, 3]",
            """{"name": "another app's file"}""",
            """{"format": "com.example.other", "schema": 1, "sections": {}}""",
            "{ not json at all",
            "\u0089PNG",
        ).forEach { assertEquals(BackupProblem.NotBackup, problemOf(it), it) }
    }

    @Test
    fun `a backup cut short or edited badly is damaged`() {
        val whole = BackupJson.write(meta, mapOf("presets" to (1 to presets)))
        listOf(
            whole.dropLast(10),
            whole.replace("\"schema\": 1", "\"schema\": \"one\""),
            whole.replace("\"schema\": 1,", ""),
            whole.replace("\"schema\": 1", "\"schema\": 0"),
            """{"format": "com.huntercoles.pokerpayout.backup", "schema": 1}""",
            """{"format": "com.huntercoles.pokerpayout.backup", "schema": 1, "sections": []}""",
            """{"format": "com.huntercoles.pokerpayout.backup", "schema": 1, "sections": {"presets": []}}""",
            """{"format": "com.huntercoles.pokerpayout.backup", "schema": 1, "sections": {"presets": {"presets": []}}}""",
            """{"format": "com.huntercoles.pokerpayout.backup", "schema": 1, "sections": {"presets": {"version": 0}}}""",
            """{"format": "com.huntercoles.pokerpayout.backup", "schema": 1, "sections": {"presets": {"version": "1"}}}""",
        ).forEach { assertEquals(BackupProblem.Damaged, problemOf(it), it) }
    }

    @Test
    fun `a file nested absurdly deep is damaged, not read`() {
        val deep = """{"format": "com.huntercoles.pokerpayout.backup", "schema": 1, "sections": {"x": """ +
            "[".repeat(100_000) + "]".repeat(100_000) + "}}"
        assertEquals(BackupProblem.Damaged, problemOf(deep))
    }

    @Test
    fun `brackets inside text don't count as nesting`() {
        val note = "[{".repeat(100) + "\\\"]"
        val text = """{"format": "com.huntercoles.pokerpayout.backup", "schema": 1, "sections": {}, "note": "$note"}"""
        assertEquals(emptyMap(), BackupJson.read(text).sections)
    }

    @Test
    fun `a backup from a later layout asks for an update`() {
        val later = BackupJson.write(meta, emptyMap()).replace("\"schema\": 1", "\"schema\": 2")
        assertEquals(BackupProblem.Newer, problemOf(later))
    }

    @Test
    fun `a file too big to be a backup isn't read`() {
        assertEquals(BackupProblem.TooBig, problemOf("{" + " ".repeat(BackupJson.MAX_BYTES) + "}"))
    }
}
