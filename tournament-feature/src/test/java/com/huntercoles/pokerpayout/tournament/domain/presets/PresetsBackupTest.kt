package com.huntercoles.pokerpayout.tournament.domain.presets

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.backup.BackupCatalog
import com.huntercoles.pokerpayout.core.backup.BackupException
import com.huntercoles.pokerpayout.core.backup.BackupLine
import com.huntercoles.pokerpayout.core.backup.BackupProblem
import com.huntercoles.pokerpayout.core.backup.Backups
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.preferences.ChipSetSettings
import com.huntercoles.pokerpayout.core.testing.FakeDocumentFiles
import com.huntercoles.pokerpayout.core.time.TimeSource
import com.huntercoles.pokerpayout.core.R as CoreR
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The presets in backups and preset files ([PresetsBackup], [PresetFiles]): they come back exactly;
 * merging never changes a preset that's here, skips one already here and renames one whose name is
 * taken by another setup ("Friday 2"), however often it is merged; a preset file is a backup with one
 * preset, and a file without presets says so.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PresetsBackupTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: PresetStore
    private lateinit var section: PresetsBackup
    private val files = FakeDocumentFiles()

    private val time = object : TimeSource {
        override fun elapsedRealtimeMillis(): Long = 0L

        override fun wallClockMillis(): Long = 1_791_460_800_000L

        override fun bootCount(): Int = -1
    }

    @Before
    fun clear() {
        context.getSharedPreferences("tournament_presets", Context.MODE_PRIVATE).edit().clear().commit()
        store = PresetStore(context)
        section = PresetsBackup(store)
    }

    private fun backups() = Backups(setOf(section), time, context)

    private fun setup(buyInCents: Long, chipSet: ChipSetSettings? = null) = PresetSetup(
        money = MoneySettings(buyInCents = buyInCents, foodCents = 500L, bountyCents = 0L, rebuyCents = 2_000L, addOnCents = 0L),
        rebuyUntilLevel = 4,
        blinds = PresetBlinds.DEFAULT.copy(breakEveryLevels = 4, breakNote = "Last rebuy, \"then\" color up"),
        payouts = PresetPayouts(PayoutPreset.STANDARD.weightsFor(3), PayoutPreset.STANDARD, PayoutRounding.FIVE_DOLLARS, true),
        chipSet = chipSet,
    )

    @Test
    fun `the presets section is the catalog's for its file`() {
        assertEquals(PresetsBackup.KEY, BackupCatalog.COLLECTIONS["tournament_presets"])
    }

    @Test
    fun `presets come back exactly on a replace, ids and all`() {
        val friday = store.save("Friday", setup(4_000L, ChipSetSettings()), nowMillis = 100L)
        val turbo = store.save("Turbo", setup(2_000L), nowMillis = 300L)
        val text = backups().export()
        store.delete(friday.id)
        store.save("Mine", setup(1_000L), nowMillis = 400L)

        val opened = backups().open(text)
        assertEquals(listOf(BackupLine.Counted(CoreR.plurals.backup_line_presets, 2)), opened.lines)
        assertFalse(backups().replace(opened))
        assertEquals(listOf(turbo, friday), PresetStore(context).presets.value)
    }

    @Test
    fun `merging adds what's new, skips what's here, and renames a different setup under a taken name`() {
        store.save("Friday", setup(4_000L), nowMillis = 100L)
        store.save("Turbo", setup(2_000L), nowMillis = 200L)
        store.save("Deep", setup(6_000L), nowMillis = 300L)
        val text = backups().export()
        clear()
        val mine = store.save("friday", setup(5_000L), nowMillis = 900L) // another Friday
        val turbo = store.save("Turbo", setup(2_000L), nowMillis = 950L) // the same Turbo

        val result = backups().merge(backups().open(text))
        assertEquals(2, result.total)
        val byName = store.presets.value.associateBy { it.name }
        assertEquals(setOf("friday", "Turbo", "Friday 2", "Deep"), byName.keys)
        assertEquals(mine, byName["friday"])
        assertEquals(turbo, byName["Turbo"])
        assertEquals(setup(4_000L), byName.getValue("Friday 2").setup)
        assertEquals(100L, byName.getValue("Friday 2").lastUsedMillis)

        assertEquals("merging again adds nothing", 0, backups().merge(backups().open(text)).total)

        result.undo()
        assertEquals(listOf(turbo, mine), PresetStore(context).presets.value)
    }

    @Test
    fun `a free name never runs past the longest a name can be`() {
        val long = "A".repeat(TournamentPreset.MAX_NAME_LENGTH)
        store.save(long, setup(1_000L), nowMillis = 1L)
        store.save(long.dropLast(2) + " 2", setup(2_000L), nowMillis = 2L)
        assertEquals(long.dropLast(2) + " 3", store.freeName(long))
        assertEquals("Turbo", store.freeName(" Turbo "))
        assertEquals("Turbo 2", store.freeName("Turbo", taken = listOf("TURBO")))
    }

    @Test
    fun `a preset file is a backup with that one preset, named after it`() {
        val friday = store.save("Friday: deep / slow", setup(4_000L), nowMillis = 100L)
        store.save("Turbo", setup(2_000L), nowMillis = 200L)
        val file = PresetFiles(backups(), section, files, UnconfinedTestDispatcher()).fileOf(friday)

        assertEquals("Friday_ deep _ slow.json", file.name)
        val opened = backups().open(file.text)
        assertEquals(listOf(BackupLine.Counted(CoreR.plurals.backup_line_presets, 1)), opened.lines)
        assertTrue(file.text.contains("\"name\": \"Friday: deep / slow\""))
    }

    @Test
    fun `opening a preset file adds its presets, with Undo`() = runTest {
        val friday = store.save("Friday", setup(4_000L), nowMillis = 100L)
        val presetFiles = PresetFiles(backups(), section, files, UnconfinedTestDispatcher(testScheduler))
        files.texts["content://drive/friday.json"] = presetFiles.fileOf(friday).text
        clear()
        val presetFilesHere = PresetFiles(backups(), section, files, UnconfinedTestDispatcher(testScheduler))

        val result = presetFilesHere.import(Uri.parse("content://drive/friday.json"))
        assertEquals(1, result.total)
        assertEquals(listOf("Friday"), store.presets.value.map { it.name })
        result.undo()
        assertTrue(store.presets.value.isEmpty())
    }

    @Test
    fun `a file without presets, or not a backup, says why`() = runTest {
        val presetFiles = PresetFiles(backups(), section, files, UnconfinedTestDispatcher(testScheduler))
        files.texts["content://a/empty.json"] = backups().export(section, section.payloadOf(emptyList()))
        files.texts["content://a/photo.jpg"] = "not a preset"

        val empty = runCatching { presetFiles.import(Uri.parse("content://a/empty.json")) }.exceptionOrNull()
        assertEquals(BackupProblem.Empty, (empty as BackupException).problem)
        val photo = runCatching { presetFiles.import(Uri.parse("content://a/photo.jpg")) }.exceptionOrNull()
        assertEquals(BackupProblem.NotBackup, (photo as BackupException).problem)
        val missing = runCatching { presetFiles.import(Uri.parse("content://a/gone.json")) }.exceptionOrNull()
        assertEquals(BackupProblem.CantRead, (missing as BackupException).problem)
    }

    @Test
    fun `two presets with one id make the file damaged`() {
        val friday = store.save("Friday", setup(4_000L), nowMillis = 100L)
        val twice = backups().export(section, section.payloadOf(listOf(friday, friday.copy(name = "Copy"))))
        val problem = assertThrows(BackupException::class.java) { backups().open(twice) }.problem
        assertEquals(BackupProblem.Damaged, problem)
    }
}
