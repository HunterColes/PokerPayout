package com.huntercoles.pokerpayout.tournament.presentation.presets

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.R as CoreR
import com.huntercoles.pokerpayout.core.audio.SoundManager
import com.huntercoles.pokerpayout.core.backup.Backups
import com.huntercoles.pokerpayout.core.design.components.SnackbarController
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.ChipCalculatorPreferences
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.testing.FakeDocumentFiles
import com.huntercoles.pokerpayout.core.time.TimeSource
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockCues
import com.huntercoles.pokerpayout.tournament.domain.clock.CueVibrator
import com.huntercoles.pokerpayout.tournament.domain.presets.CurrentSetup
import com.huntercoles.pokerpayout.tournament.domain.presets.PresetFiles
import com.huntercoles.pokerpayout.tournament.domain.presets.PresetStore
import com.huntercoles.pokerpayout.tournament.domain.presets.PresetsBackup
import com.huntercoles.pokerpayout.tournament.presentation.FakeChipSets
import com.huntercoles.pokerpayout.tournament.presentation.TimerIntent
import com.huntercoles.pokerpayout.tournament.presentation.TimerViewModel
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigViewModel
import io.mockk.mockk
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

/**
 * Saved setups (PP-032) through the real ViewModel over Robolectric's preferences, on virtual time:
 * save, load (asking first only when something would be replaced), rename and delete, each with Undo
 * on the snackbar; nothing loads once the clock has started; and the clock and the setup page follow
 * a loaded preset.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PresetsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val snackbars = SnackbarController()
    private val clock = WallClock()
    private val store = ViewModelStore()
    private lateinit var tournament: TournamentPreferences
    private lateinit var timer: TimerPreferences
    private lateinit var bank: BankPreferences
    private lateinit var chips: ChipCalculatorPreferences
    private lateinit var presets: PresetStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        listOf("tournament_prefs", "timer_prefs", "bank_prefs", "chip_calculator_prefs", "audio_prefs", "tournament_presets")
            .forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        tournament = TournamentPreferences(context)
        timer = TimerPreferences(context)
        bank = BankPreferences(context)
        chips = ChipCalculatorPreferences(context, tournament)
        presets = PresetStore(context)
        tournament.setPlayerCount(9)
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun settle() = dispatcher.scheduler.runCurrent()

    private val files = FakeDocumentFiles()

    private fun viewModel(): PresetsViewModel = inStore("presets") {
        val section = PresetsBackup(presets)
        val presetFiles = PresetFiles(Backups(setOf(section), clock, context), section, files, dispatcher)
        PresetsViewModel(presets, CurrentSetup(tournament, timer, chips, bank), snackbars, PresetMessages(context), clock, presetFiles)
    }

    /** A ViewModel in [store], so [tearDown] cancels its coroutines. */
    private inline fun <reified T : ViewModel> inStore(key: String, crossinline build: () -> T): T {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <V : ViewModel> create(modelClass: Class<V>): V = build() as V
        }
        return ViewModelProvider(store, factory)[key, T::class.java].also { settle() }
    }

    private fun PresetsViewModel.send(vararg intents: PresetsIntent) {
        intents.forEach { acceptIntent(it) }
        settle()
    }

    private val PresetsViewModel.state get() = uiState.value

    /** The snackbar showing now: its message, and Undo pressed if [undo]. */
    private fun snackbar(undo: Boolean = false): String {
        val shown = requireNotNull(snackbars.hostState.currentSnackbarData) { "no snackbar" }
        if (undo) {
            shown.performAction()
            settle()
        }
        return shown.visuals.message
    }

    /** The Friday game: $40 buy-in, 15-minute levels. Saved as "Friday", then the setup goes back to a new tournament's. */
    private fun savedFriday(viewModel: PresetsViewModel): Long {
        tournament.setBuyInCents(4_000L)
        tournament.setRoundLengthMinutes(15)
        viewModel.send(PresetsIntent.Open, PresetsIntent.StartSave, PresetsIntent.Save("Friday", includeChipSet = false))
        tournament.resetAllTournamentData()
        timer.resetAllTimerData()
        tournament.setPlayerCount(9)
        return requireNotNull(presets.named("Friday")).id
    }

    @Test
    fun `save keeps the setup under its name and closes the sheet, and Undo takes it back`() {
        val viewModel = viewModel()
        tournament.setBuyInCents(4_000L)
        viewModel.send(PresetsIntent.Open)
        assertEquals(PresetSheet.List, viewModel.state.sheet)
        viewModel.send(PresetsIntent.StartSave)
        assertEquals(PresetSheet.Save, viewModel.state.sheet)

        clock.now = 5_000L
        viewModel.send(PresetsIntent.Save("  Friday  ", includeChipSet = false))

        val saved = viewModel.state.presets.single()
        assertEquals("Friday", saved.name)
        assertEquals(4_000L, saved.setup.money.buyInCents)
        assertNull(saved.setup.chipSet)
        assertEquals(5_000L, saved.lastUsedMillis)
        assertNull(viewModel.state.sheet)
        assertEquals("Friday saved", snackbar(undo = true))
        assertTrue(viewModel.state.presets.isEmpty())
    }

    @Test
    fun `saving under a name in use updates that preset, and Undo puts the old one back`() {
        val viewModel = viewModel()
        val id = savedFriday(viewModel)
        val first = requireNotNull(presets.get(id))
        tournament.setBuyInCents(6_000L)

        viewModel.send(PresetsIntent.Save("friday", includeChipSet = true))
        assertEquals(id, viewModel.state.presets.single().id)
        assertEquals(6_000L, viewModel.state.presets.single().setup.money.buyInCents)
        assertEquals(chips.current(), viewModel.state.presets.single().setup.chipSet)
        assertEquals("friday updated", snackbar(undo = true))
        assertEquals(listOf(first), viewModel.state.presets)
    }

    @Test
    fun `loading onto a new tournament's setup applies at once, and Undo puts the setup back`() {
        val viewModel = viewModel()
        val id = savedFriday(viewModel)
        val usedBefore = requireNotNull(presets.get(id)).lastUsedMillis
        clock.now = 9_000L

        viewModel.send(PresetsIntent.Open, PresetsIntent.Load(id))

        assertNull("nothing to lose: no question", viewModel.state.sheet)
        assertEquals(4_000L, tournament.getMoneySettings().buyInCents)
        assertEquals(15, tournament.getRoundLengthMinutes())
        assertEquals(9_000L, presets.get(id)?.lastUsedMillis)
        assertEquals("Friday loaded", snackbar(undo = true))
        assertEquals(2_000L, tournament.getMoneySettings().buyInCents)
        assertEquals(20, tournament.getRoundLengthMinutes())
        assertEquals(usedBefore, presets.get(id)?.lastUsedMillis)
    }

    @Test
    fun `loading over what the host set asks once, then loads`() {
        val viewModel = viewModel()
        val id = savedFriday(viewModel)
        tournament.setBuyInCents(3_000L)

        viewModel.send(PresetsIntent.Open, PresetsIntent.Load(id))
        assertEquals(PresetSheet.ConfirmLoad(id), viewModel.state.sheet)
        assertEquals("asked, not loaded", 3_000L, tournament.getMoneySettings().buyInCents)

        viewModel.send(PresetsIntent.Open)
        assertEquals("Keep mine: back to the list", PresetSheet.List, viewModel.state.sheet)
        assertEquals(3_000L, tournament.getMoneySettings().buyInCents)

        viewModel.send(PresetsIntent.Load(id), PresetsIntent.ConfirmLoad(id))
        assertNull(viewModel.state.sheet)
        assertEquals(4_000L, tournament.getMoneySettings().buyInCents)
        assertEquals("Friday loaded", snackbar())
    }

    @Test
    fun `once the clock has started nothing loads, and the sheet says why`() {
        val viewModel = viewModel()
        val id = savedFriday(viewModel)
        timer.setHasTimerStarted(true)

        viewModel.send(PresetsIntent.Open)
        assertFalse(viewModel.state.canLoad)
        viewModel.send(PresetsIntent.Load(id), PresetsIntent.ConfirmLoad(id))

        assertEquals(PresetSheet.List, viewModel.state.sheet)
        assertEquals(2_000L, tournament.getMoneySettings().buyInCents)
        assertEquals(20, tournament.getRoundLengthMinutes())

        // Saving, renaming and sharing still work mid-game: none of them touches the game
        viewModel.send(PresetsIntent.Save("Mid-game", includeChipSet = false))
        assertEquals(2, viewModel.state.presets.size)
    }

    @Test
    fun `Undo after the clock has started leaves the game alone`() {
        val viewModel = viewModel()
        val id = savedFriday(viewModel)
        viewModel.send(PresetsIntent.Load(id))
        timer.setHasTimerStarted(true)

        snackbar(undo = true)
        assertEquals(4_000L, tournament.getMoneySettings().buyInCents)
        assertEquals(15, tournament.getRoundLengthMinutes())
    }

    @Test
    fun `rename and delete apply at once, each with Undo`() {
        val viewModel = viewModel()
        val id = savedFriday(viewModel)

        viewModel.send(PresetsIntent.StartRename(id))
        assertEquals(PresetSheet.Rename(id), viewModel.state.sheet)
        viewModel.send(PresetsIntent.Rename(id, "Friday night"))
        assertNull(viewModel.state.sheet)
        assertEquals("Friday night", presets.get(id)?.name)
        assertEquals("Renamed to Friday night", snackbar(undo = true))
        assertEquals("Friday", presets.get(id)?.name)

        val friday = requireNotNull(presets.get(id))
        viewModel.send(PresetsIntent.Delete(id))
        assertNull(presets.get(id))
        assertEquals("Friday deleted", snackbar(undo = true))
        assertEquals(friday, presets.get(id))
    }

    @Test
    fun `a preset can't be renamed to a name another preset has`() {
        val viewModel = viewModel()
        val id = savedFriday(viewModel)
        viewModel.send(PresetsIntent.Save("Turbo", includeChipSet = false))

        viewModel.send(PresetsIntent.StartRename(id), PresetsIntent.Rename(id, "TURBO"))
        assertEquals("Friday", presets.get(id)?.name)
        assertEquals("still open", PresetSheet.Rename(id), viewModel.state.sheet)
    }

    @Test
    fun `the save form offers the chip set once it is set up in Tools`() {
        val viewModel = viewModel()
        viewModel.send(PresetsIntent.StartSave)
        assertFalse(viewModel.state.chipSet.ready)

        chips.setInventory(chips.current().inventory)
        viewModel.send(PresetsIntent.StartSave)
        val home = chips.current().inventory
        assertEquals(ChipSetSummary(colours = home.owned.size, chips = home.totalChips, ready = true), viewModel.state.chipSet)
    }

    /** The clock (activity-scoped) and the setup page keep their own copies; a loaded preset reaches both. */
    @Test
    fun `the clock and the setup page follow a loaded preset`() {
        val viewModel = viewModel()
        val id = savedFriday(viewModel)
        val clockViewModel = inStore("clock") {
            val audio = AudioPreferences(context)
            TimerViewModel(
                timer,
                tournament,
                bank,
                ClockCues(mockk<SoundManager>(relaxed = true), audio, CueVibrator { }, clock),
                clock,
                audio,
                FakeChipSets(),
            )
        }
        val setupViewModel = inStore("setup") { TournamentConfigViewModel(CalculatePayoutsUseCase(), tournament, timer, bank) }
        assertEquals(20, clockViewModel.uiState.value.config.roundLengthMinutes)

        viewModel.send(PresetsIntent.Load(id))

        val clockState = clockViewModel.uiState.value
        assertEquals(15, clockState.config.roundLengthMinutes)
        assertEquals("3 hours of 15-minute levels", 12, clockState.baseBlindLevels.size)
        assertFalse(clockState.hasTimerStarted)
        assertEquals(15, setupViewModel.uiState.value.roundLengthMinutes)
        assertEquals(4_000L, setupViewModel.uiState.value.money.buyInCents)

        // Started, the clock keeps its game whatever is pressed
        clockViewModel.acceptIntent(TimerIntent.ToggleTimer)
        settle()
        snackbar(undo = true)
        assertEquals(15, clockViewModel.uiState.value.config.roundLengthMinutes)
        assertTrue(clockViewModel.uiState.value.isRunning)
    }

    @Test
    fun `a preset shared as a file is a file of that preset alone, named after it`() {
        val viewModel = viewModel()
        val friday = savedFriday(viewModel)
        viewModel.send(PresetsIntent.Open, PresetsIntent.ShareFile(friday))

        val file = requireNotNull(viewModel.state.sharing)
        assertEquals("Friday.json", file.name)
        assertTrue(file.text.contains("\"name\": \"Friday\""))
        assertEquals("the sheet stays open", PresetSheet.List, viewModel.state.sheet)
        viewModel.send(PresetsIntent.FileShared)
        assertNull(viewModel.state.sharing)
    }

    @Test
    fun `a preset file opened adds its presets, closes the sheet, and Undo takes them out`() {
        val viewModel = viewModel()
        val friday = savedFriday(viewModel)
        viewModel.send(PresetsIntent.ShareFile(friday))
        files.texts["content://chat/Friday.json"] = requireNotNull(viewModel.state.sharing).text
        presets.delete(friday)
        viewModel.send(PresetsIntent.Open, PresetsIntent.ImportFile(Uri.parse("content://chat/Friday.json")))

        assertEquals(listOf("Friday"), viewModel.state.presets.map { it.name })
        assertNull(viewModel.state.sheet)
        assertEquals("Added 1 preset", snackbar(undo = true))
        assertTrue(presets.presets.value.isEmpty())
    }

    @Test
    fun `a preset file already here, or one that won't do, says so`() {
        val viewModel = viewModel()
        val friday = savedFriday(viewModel)
        viewModel.send(PresetsIntent.ShareFile(friday))
        files.texts["content://chat/Friday.json"] = requireNotNull(viewModel.state.sharing).text
        files.texts["content://chat/notes.txt"] = "Bring chips"

        viewModel.send(PresetsIntent.Open, PresetsIntent.ImportFile(Uri.parse("content://chat/Friday.json")))
        assertEquals("You have these presets already", snackbar())
        assertEquals(1, presets.presets.value.size)

        viewModel.send(PresetsIntent.Open, PresetsIntent.ImportFile(Uri.parse("content://chat/notes.txt")))
        assertEquals(CoreR.string.backup_problem_not_backup, viewModel.state.fileProblem)
        assertEquals("the list stays open to say why", PresetSheet.List, viewModel.state.sheet)
        viewModel.send(PresetsIntent.Close, PresetsIntent.Open)
        assertNull("a fresh list forgets it", viewModel.state.fileProblem)
    }

    /** A wall clock a test sets by hand. */
    private class WallClock(var now: Long = 1_000L) : TimeSource {
        override fun elapsedRealtimeMillis() = 1_000_000L
        override fun wallClockMillis() = now
        override fun bootCount() = 1
    }
}
