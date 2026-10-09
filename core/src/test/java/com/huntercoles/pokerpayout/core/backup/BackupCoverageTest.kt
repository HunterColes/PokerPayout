package com.huntercoles.pokerpayout.core.backup

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The guard that keeps backups whole: every SharedPreferences file any module's code opens must be in
 * [BackupCatalog] (saved by a settings group or a section of its own), so new data can't be left out
 * of backups by accident. Every key in a listed file is saved (typed), apart from the few a file marks
 * as about this phone ([SettingsFile.phoneOnly]); [BackupsTest] proves they all come back.
 *
 * The sources are read as text: a call to `getSharedPreferences` with a string, or with a constant
 * from the same file. Saved data anywhere else (a file in the app's files folder, a database,
 * DataStore) fails here too, until it has a backup section and an entry in [OTHER_STORAGE].
 */
class BackupCoverageTest {

    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    /** Every main Kotlin source of every module. */
    private val sources: List<File> = root.listFiles().orEmpty()
        .filter { File(it, "src/main").isDirectory }
        .flatMap { module -> File(module, "src/main").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    private fun File.relative(): String = relativeTo(root).path

    @Test
    fun `every SharedPreferences file the app opens is in a backup`() {
        val opened = mutableMapOf<String, MutableList<String>>()
        val unresolved = mutableListOf<String>()
        sources.forEach { file ->
            val text = file.readText()
            OPENS.findAll(text).forEach { call ->
                val argument = call.groupValues[1].trim()
                val name = LITERAL.matchEntire(argument)?.groupValues?.get(1)
                    ?: Regex("""\bval\s+$argument\s*(?::\s*String\s*)?=\s*"([^"]+)"""").find(text)?.groupValues?.get(1)
                when {
                    name != null -> opened.getOrPut(name) { mutableListOf() } += file.relative()
                    file.relative() !in OPENS_BY_NAME -> unresolved += "${file.relative()}: getSharedPreferences($argument"
                }
            }
        }
        assertTrue(unresolved.isEmpty(), "Can't tell which file these open; use a constant in the same file:\n$unresolved")
        assertTrue(opened.isNotEmpty(), "found no SharedPreferences at all under $root")
        val missing = opened.keys - BackupCatalog.FILES
        assertTrue(
            missing.isEmpty(),
            "Not in any backup: ${missing.associateWith { opened[it] }}. List each in BackupCatalog (core/backup): " +
                "SETTINGS to save it whole with a settings group, or COLLECTIONS with a BackupSection of its own.",
        )
        assertEquals(BackupCatalog.FILES, opened.keys, "BackupCatalog lists a file nothing opens any more")
    }

    @Test
    fun `nothing is saved outside SharedPreferences without a backup section`() {
        val found = sources.flatMap { file ->
            val text = file.readText()
            OTHER_STORAGE_APIS.filter { it in text }.map { "${file.relative()}: $it" }
        }.filter { finding -> OTHER_STORAGE.keys.none { finding.startsWith(it) } }
        if (found.isNotEmpty()) {
            fail(
                "Saved data outside SharedPreferences isn't in backups: $found. Give it a BackupSection (core/backup) " +
                    "and list the file in OTHER_STORAGE here with that section's key.",
            )
        }
    }

    @Test
    fun `the catalog's sections are unique and every settings file has one group`() {
        val names = BackupCatalog.SETTINGS.map { it.name }
        assertEquals(names.distinct(), names, "a settings file listed twice")
        assertTrue(BackupCatalog.COLLECTIONS.keys.none { it in names }, "a file both saved whole and item by item")
        val keys = SettingsGroup.entries.map { it.key } + BackupCatalog.COLLECTIONS.values
        assertEquals(keys.distinct(), keys, "two sections with one key")
        SettingsGroup.entries.forEach { group -> assertTrue(BackupCatalog.filesOf(group).isNotEmpty(), "$group has no files") }
    }

    private companion object {
        val OPENS = Regex("""getSharedPreferences\(\s*([^,)]+),""")
        val LITERAL = Regex(""""([^"]+)"""")

        /** Code that opens the files the catalog names, by name: the backup itself. */
        val OPENS_BY_NAME = setOf("core/src/main/java/com/huntercoles/pokerpayout/core/backup/SettingsSection.kt")

        /** APIs that keep data outside SharedPreferences. The cache (shared files) isn't saved data. */
        val OTHER_STORAGE_APIS = listOf(
            "openFileOutput(", ".filesDir", "getDir(", "getDatabasePath(", "SQLiteOpenHelper", "Room.databaseBuilder",
            "dataStore", "DataStore<", "getExternalFilesDir(", "noBackupFilesDir",
        )

        /** Source file to the backup section that saves what it keeps there. */
        val OTHER_STORAGE: Map<String, String> = emptyMap()
    }
}
