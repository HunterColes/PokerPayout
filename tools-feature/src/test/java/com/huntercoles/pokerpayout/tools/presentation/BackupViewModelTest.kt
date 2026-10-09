package com.huntercoles.pokerpayout.tools.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.R as CoreR
import com.huntercoles.pokerpayout.core.backup.BackupCatalog
import com.huntercoles.pokerpayout.core.backup.BackupLine
import com.huntercoles.pokerpayout.core.backup.BackupModule
import com.huntercoles.pokerpayout.core.backup.Backups
import com.huntercoles.pokerpayout.core.backup.HistoryBackup
import com.huntercoles.pokerpayout.core.design.components.SnackbarController
import com.huntercoles.pokerpayout.core.domain.history.NightStore
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import com.huntercoles.pokerpayout.core.testing.FakeDocumentFiles
import com.huntercoles.pokerpayout.core.time.TimeSource
import com.huntercoles.pokerpayout.tools.presentation.composable.HistoryFixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The Backup screen through the real ViewModel and the core's backup sections, over Robolectric's
 * preferences and in-memory files, on virtual time: a backup saved where the player picked; a file
 * opened shows what it holds and changes nothing; Add brings in the nights with Undo; Replace puts
 * the backup in place and starts the app again; a file that won't do says why.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val snackbars = SnackbarController()
    private val viewModels = ViewModelStore()
    private val files = FakeDocumentFiles()
    private var restarts = 0
    private lateinit var nights: NightStore

    private val time = object : TimeSource {
        override fun elapsedRealtimeMillis(): Long = 0L

        override fun wallClockMillis(): Long = 1_791_460_800_000L // 8 October 2026, noon UTC

        override fun bootCount(): Int = -1
    }

    private val saved = Uri.parse("content://drive/poker-payout-2026-10-08.json")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        BackupCatalog.FILES.forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        nights = NightStore(context)
    }

    @After
    fun tearDown() {
        viewModels.clear()
        Dispatchers.resetMain()
    }

    private fun settle() = dispatcher.scheduler.runCurrent()

    private fun viewModel(): BackupViewModel {
        val backups = Backups(
            BackupModule.settingsSections(context, time) + BackupModule.historySection(HistoryBackup(nights)),
            time,
            context,
        )
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                BackupViewModel(backups, files, { restarts++ }, snackbars, BackupMessages(context), dispatcher) as T
        }
        return ViewModelProvider(viewModels, factory)[BackupViewModel::class.java].also { settle() }
    }

    private fun BackupViewModel.send(intent: BackupIntent) {
        acceptIntent(intent)
        settle()
    }

    private val BackupViewModel.state get() = uiState.value

    private val snackbar get() = snackbars.hostState.currentSnackbarData?.visuals

    /** The fixtures' three nights and a quieter chime, saved to [saved]; then the phone starts afresh. */
    private fun backupSaved() {
        HistoryFixtures.nights.reversed().forEach { nights.add(it) }
        AudioPreferences(context).setVolume(0.4f)
        viewModel().send(BackupIntent.Save(saved))
        viewModels.clear()
        setUp()
    }

    @Test
    fun `a backup is saved where the player picked, and the snackbar names the file`() {
        HistoryFixtures.nights.forEach { nights.add(it) }
        val viewModel = viewModel()
        viewModel.send(BackupIntent.Save(saved))

        val text = files.texts.getValue(saved.toString())
        assertTrue(text.startsWith("{\n  \"format\": \"com.huntercoles.pokerpayout.backup\""))
        assertEquals("Backup saved: poker-payout-2026-10-08.json", snackbar?.message)
        assertNull("nothing to undo", snackbar?.actionLabel)
        assertFalse(viewModel.state.busy)
    }

    @Test
    fun `opening a backup shows what it holds and changes nothing`() {
        backupSaved()
        val viewModel = viewModel()
        viewModel.send(BackupIntent.Open(saved))

        assertEquals(
            BackupPreview(
                fileName = "poker-payout-2026-10-08.json",
                saved = LocalDate.of(2026, 10, 8),
                appVersion = null, // Robolectric's package has no version name
                lines = listOf(
                    BackupLine.Counted(CoreR.plurals.backup_line_nights, 3),
                    BackupLine.Named(CoreR.string.backup_line_sound),
                ),
                canMerge = true,
            ),
            viewModel.state.preview?.copy(appVersion = null),
        )
        assertTrue(nights.nights.value.isEmpty())
        assertEquals(1f, AudioPreferences(context).getVolume())

        viewModel.send(BackupIntent.ClosePreview)
        assertNull(viewModel.state.preview)
        assertTrue(nights.nights.value.isEmpty())
    }

    @Test
    fun `Add brings in the nights the phone doesn't have, keeps its settings, and Undo takes them out`() {
        backupSaved()
        nights.add(HistoryFixtures.friday)
        val viewModel = viewModel()
        viewModel.send(BackupIntent.Open(saved))
        viewModel.send(BackupIntent.Merge)

        assertNull(viewModel.state.preview)
        assertEquals(3, nights.nights.value.size)
        assertEquals(1f, AudioPreferences(context).getVolume())
        assertEquals("Added 2 nights in History", snackbar?.message)
        assertEquals("Undo", snackbar?.actionLabel)
        assertEquals(0, restarts)

        snackbars.hostState.currentSnackbarData?.performAction()
        settle()
        assertEquals(1, nights.nights.value.size)
    }

    @Test
    fun `Add with nothing new says so`() {
        backupSaved()
        HistoryFixtures.nights.forEach { nights.add(it) }
        val viewModel = viewModel()
        viewModel.send(BackupIntent.Open(saved))
        viewModel.send(BackupIntent.Merge)
        assertEquals("Nothing new: this phone has it all already.", snackbar?.message)
        assertEquals(3, nights.nights.value.size)
    }

    @Test
    fun `Replace puts the backup in place and starts the app again`() {
        backupSaved()
        nights.add(HistoryFixtures.friday.copy(structureName = "Mine"))
        val viewModel = viewModel()
        viewModel.send(BackupIntent.Open(saved))
        viewModel.send(BackupIntent.Replace)

        assertEquals(1, restarts)
        assertEquals(HistoryFixtures.nights.map { it.date }, NightStore(context).nights.value.map { it.date })
        assertEquals(0.4f, AudioPreferences(context).getVolume())
        assertNull(viewModel.state.preview)
    }

    @Test
    fun `a file that won't do says why, and nothing changes`() {
        files.texts["content://a/photo.jpg"] = "JFIF"
        files.texts["content://a/cut.json"] = "{\"format\": \"com.huntercoles.pokerpayout.backup\", \"schema\": 1, \"sec"
        files.texts["content://a/later.json"] =
            """{"format": "com.huntercoles.pokerpayout.backup", "schema": 7, "sections": {}}"""
        val viewModel = viewModel()
        mapOf(
            "content://a/photo.jpg" to CoreR.string.backup_problem_not_backup,
            "content://a/cut.json" to CoreR.string.backup_problem_damaged,
            "content://a/later.json" to CoreR.string.backup_problem_newer,
            "content://a/gone.json" to CoreR.string.backup_problem_cant_read,
        ).forEach { (uri, problem) ->
            viewModel.send(BackupIntent.Open(Uri.parse(uri)))
            assertEquals(uri, problem, viewModel.state.problem)
            assertNull(viewModel.state.preview)
            assertFalse(viewModel.state.busy)
        }
        viewModel.send(BackupIntent.Merge)
        viewModel.send(BackupIntent.Replace)
        assertEquals(0, restarts)
    }

    @Test
    fun `a backup that can't be written says so`() {
        files.failWrites = true
        val viewModel = viewModel()
        viewModel.send(BackupIntent.Save(saved))
        assertEquals(CoreR.string.backup_problem_cant_write, viewModel.state.problem)
        assertNull(snackbar)
    }

    @Test
    fun `the snackbar lists everything added`() {
        val messages = BackupMessages(context)
        assertEquals(
            "Added 2 nights in History",
            messages.added(listOf(BackupLine.Counted(CoreR.plurals.backup_line_nights, 2))),
        )
        assertEquals(
            "Added 1 night in History, Chip set and Sound settings",
            messages.added(
                listOf(
                    BackupLine.Counted(CoreR.plurals.backup_line_nights, 1),
                    BackupLine.Named(CoreR.string.backup_line_chip_set),
                    BackupLine.Named(CoreR.string.backup_line_sound),
                ),
            ),
        )
    }
}
