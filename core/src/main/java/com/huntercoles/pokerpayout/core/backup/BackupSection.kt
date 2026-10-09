package com.huntercoles.pokerpayout.core.backup

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import kotlinx.serialization.json.JsonObject

/**
 * One kind of saved data in a backup file ([BackupJson]): presets, the nights in History, a group of
 * settings files. The app's sections are the Hilt set of [BackupSection]s.
 *
 * To put new data in backups:
 * - Settings in a SharedPreferences file of their own: list the file in [BackupCatalog.SETTINGS]
 *   under a [SettingsGroup] (a new group needs a key, an order and a preview line). That is all: the
 *   file is saved whole, every key with its type, and put back on Replace.
 * - Items that should merge with what's on the phone (as presets and nights do): implement this
 *   interface, bind it with `@Binds @IntoSet` in your module, and list its file in
 *   [BackupCatalog.COLLECTIONS]. `BackupCoverageTest` fails until every file the app opens is listed.
 */
interface BackupSection {
    /** The section's name in the file. Never rename it: old backups would lose it. */
    val key: String

    /** The newest version of this section's fields that this app writes and reads. */
    val version: Int

    /** Where its line goes in a backup's preview; lower first. */
    val order: Int

    /** What's saved now, as this section's fields (an empty list when there's nothing). */
    fun export(): JsonObject

    /**
     * The section in a file, written at [version] (never above [BackupSection.version]). Throws
     * [BackupException] with [BackupProblem.Damaged] when its fields aren't what they should be;
     * an item this version can't read is left out and counted in [SectionData.skipped].
     */
    fun read(payload: JsonObject, version: Int): SectionData
}

/** A section read from a file: what the preview says about it, and how it goes back on the phone. */
interface SectionData {
    /** Its line in the preview ("3 presets", "Chip set"), or null when it holds nothing. */
    val line: BackupLine?

    /** Items in the file this version can't read; they are left out. */
    val skipped: Int get() = 0

    /** True when the app must start again after [replace]: settings are read once, at the start. */
    val restartsApp: Boolean

    /** True when [merge] can add to what's on the phone; settings can only be replaced. */
    val canMerge: Boolean get() = false

    /** Adds what's new to what's on the phone, or null when this section can only replace. */
    fun merge(): Merged? = null

    /** Makes what's on the phone exactly what the file holds. */
    fun replace()
}

/** What a merge added ([added] items), and how to take them out again (Undo). */
class Merged(val added: Int, val undo: () -> Unit)

/** A line of a backup's preview: "3 presets" ([Counted]) or "Chip set" ([Named]). */
sealed interface BackupLine {
    data class Counted(@PluralsRes val plural: Int, val count: Int) : BackupLine

    data class Named(@StringRes val text: Int) : BackupLine
}
