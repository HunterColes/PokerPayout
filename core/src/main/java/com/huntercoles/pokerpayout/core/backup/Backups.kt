package com.huntercoles.pokerpayout.core.backup

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.huntercoles.pokerpayout.core.time.TimeSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backups of everything the app saves, in one file ([BackupJson]), made of the app's
 * [BackupSection]s: [export] writes one; [open] reads one back for its preview; then [merge] adds
 * what's new, or [replace] puts the backup in place of what's here.
 */
@Singleton
class Backups @Inject constructor(
    private val sections: Set<@JvmSuppressWildcards BackupSection>,
    private val time: TimeSource,
    @ApplicationContext private val context: Context,
) {
    /** Every section, as one file. */
    fun export(): String =
        BackupJson.write(meta(), sections.sortedBy { it.order }.associate { it.key to (it.version to it.export()) })

    /** A file with [section] alone, holding [payload] (a preset file is the presets section with one preset). */
    fun export(section: BackupSection, payload: JsonObject): String =
        BackupJson.write(meta(), mapOf(section.key to (section.version to payload)))

    /**
     * The backup in [text], each of its sections read, for a preview. Sections this version doesn't
     * know, or knows only at an older version, are left out ([OpenedBackup.partial]). Throws
     * [BackupException] when it isn't a backup, is damaged, or holds nothing to restore.
     */
    fun open(text: String): OpenedBackup {
        val raw = BackupJson.read(text)
        val known = sections.associateBy { it.key }
        var unread = 0
        val parts = raw.sections.mapNotNull { (key, section) ->
            val reader = known[key]
            if (reader == null || section.version > reader.version) {
                unread++
                null
            } else {
                Part(reader, reader.read(section.payload, section.version))
            }
        }.sortedBy { it.section.order }
        if (parts.none { it.data.line != null }) throw BackupException(BackupProblem.Empty)
        return OpenedBackup(raw.meta, parts, unread + parts.sumOf { it.data.skipped })
    }

    /**
     * Adds what [backup] has that the phone doesn't, section by section (only the [keys] given, when
     * given); settings, which can't merge, are left as they are.
     */
    fun merge(backup: OpenedBackup, keys: Set<String>? = null): MergeResult {
        val merged = backup.parts
            .filter { keys == null || it.section.key in keys }
            .mapNotNull { part -> part.data.merge()?.let { part.section.key to it } }
            .toMap()
        return MergeResult(added = merged.mapValues { it.value.added }) { merged.values.forEach { it.undo() } }
    }

    /** Puts [backup] in place of what's here, section by section. True when the app must start again. */
    fun replace(backup: OpenedBackup): Boolean {
        backup.parts.forEach { it.data.replace() }
        return backup.parts.any { it.data.restartsApp }
    }

    private fun meta() = BackupMeta(appVersion = appVersion(), created = Instant.ofEpochMilli(time.wallClockMillis()))

    /** The installed version ("1.4.0"), or null if the system won't say. */
    private fun appVersion(): String? = try {
        val packages = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packages.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packages.getPackageInfo(context.packageName, 0)
        }
        info.versionName
    } catch (ignored: PackageManager.NameNotFoundException) {
        null
    }

    internal class Part(val section: BackupSection, val data: SectionData)
}

/**
 * A backup read from a file, before anything is restored.
 *
 * @property unread sections and items this version can't read (from a later version); they are left out.
 */
class OpenedBackup internal constructor(
    val meta: BackupMeta,
    internal val parts: List<Backups.Part>,
    val unread: Int,
) {
    /** The preview: one line per section with something in it, in the sections' order. */
    val lines: List<BackupLine> get() = parts.mapNotNull { it.data.line }

    /** Section keys with something in them. */
    val keys: Set<String> get() = parts.filter { it.data.line != null }.map { it.section.key }.toSet()

    /** True when [Backups.merge] has something to add from (presets or nights). */
    val canMerge: Boolean get() = parts.any { it.data.line != null && it.data.canMerge }

    val partial: Boolean get() = unread > 0

    /** What [result] added, in the preview's words: "2 presets", "3 nights in History". */
    fun linesFor(result: MergeResult): List<BackupLine> = parts.mapNotNull { part ->
        val added = result.added[part.section.key]?.takeIf { it > 0 }
        (part.data.line as? BackupLine.Counted)?.takeIf { added != null }?.copy(count = added ?: 0)
    }
}

/** What [Backups.merge] added, per section key, and how to take it all out again (Undo). */
class MergeResult(val added: Map<String, Int>, val undo: () -> Unit) {
    val total: Int get() = added.values.sum()
}
