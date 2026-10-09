package com.huntercoles.pokerpayout.tools.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.design.components.SnackbarController
import com.huntercoles.pokerpayout.core.domain.history.NightCsv
import com.huntercoles.pokerpayout.core.domain.history.NightPlayer
import com.huntercoles.pokerpayout.core.domain.history.NightStore
import com.huntercoles.pokerpayout.core.domain.history.SavedNight
import com.huntercoles.pokerpayout.core.domain.players.PlayerMerges
import com.huntercoles.pokerpayout.core.domain.players.RegularsStore
import com.huntercoles.pokerpayout.core.testing.FakeDocumentFiles
import com.huntercoles.pokerpayout.tools.presentation.composable.HistoryFixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * History (PP-037) through the real ViewModel over Robolectric's preferences, on virtual time: the
 * nights the latest first with the season's standings; a year's season and its player of the year;
 * a night opened and closed; delete with Undo on the snackbar; the night shared as text; and two
 * names of one person merged (PP-110): the season adds up under the name kept, never for two who
 * played the same night, made twice it counts once, and Undo or Separate takes it back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val snackbars = SnackbarController()
    private val viewModels = ViewModelStore()
    private val files = FakeDocumentFiles()
    private lateinit var store: NightStore
    private lateinit var regulars: RegularsStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        listOf("night_history", "regulars").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        store = NightStore(context)
        regulars = RegularsStore(context)
    }

    @After
    fun tearDown() {
        viewModels.clear()
        Dispatchers.resetMain()
    }

    private fun settle() = dispatcher.scheduler.runCurrent()

    /** The fixtures' three nights, saved oldest first, so the store gives them ids 1 to 3 as the fixtures have. */
    private fun saveAll() = HistoryFixtures.nights.reversed().forEach { store.add(it) }

    private fun viewModel(): HistoryViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HistoryViewModel(store, regulars, snackbars, HistoryMessages(context), files, dispatcher) as T
        }
        return ViewModelProvider(viewModels, factory)[HistoryViewModel::class.java].also { settle() }
    }

    private fun HistoryViewModel.send(intent: HistoryIntent) {
        acceptIntent(intent)
        settle()
    }

    private val HistoryViewModel.state get() = uiState.value

    private fun HistoryUiState.table() = standings.map { "${it.rank} ${it.name} ${it.points}" }

    @Test
    fun `the nights the latest first, with all time's standings and the years to pick`() {
        saveAll()
        val state = viewModel().state
        assertEquals(HistoryFixtures.nights, state.nights)
        assertEquals(listOf(2026, 2025), state.years)
        assertNull(state.year)
        assertEquals(listOf("1 Dana 13", "1 Priya 13", "3 Marcus 11", "4 Theo 5", "5 Jo 4", "5 Sam 4", "7 Alex 1"), state.table())
        assertEquals(listOf("Dana", "Priya"), state.leaders.map { it.name })
    }

    @Test
    fun `a year shows its own season and its player of the year, and all time comes back`() {
        saveAll()
        val viewModel = viewModel()
        viewModel.send(HistoryIntent.SelectYear(2026))
        assertEquals(2026, viewModel.state.year)
        assertEquals(
            listOf("1 Marcus 10", "2 Dana 9", "3 Priya 8", "4 Theo 5", "5 Jo 2", "6 Alex 1", "6 Sam 1"),
            viewModel.state.table(),
        )
        assertEquals(listOf("Marcus"), viewModel.state.leaders.map { it.name })

        viewModel.send(HistoryIntent.SelectYear(2019))
        assertNull(viewModel.state.year)
        viewModel.send(HistoryIntent.SelectYear(null))
        assertEquals(HistoryFixtures.list, viewModel.state)
    }

    @Test
    fun `a night opens in full and closes again`() {
        saveAll()
        val viewModel = viewModel()
        viewModel.send(HistoryIntent.Open(HistoryFixtures.friday.id))
        assertEquals(HistoryFixtures.friday, viewModel.state.openNight)
        viewModel.send(HistoryIntent.Close)
        assertNull(viewModel.state.openNight)
        viewModel.send(HistoryIntent.Open(99L))
        assertNull(viewModel.state.openNight)
    }

    @Test
    fun `delete applies at once, and Undo puts the night back as it was`() {
        saveAll()
        val viewModel = viewModel()
        viewModel.send(HistoryIntent.SelectYear(2025))
        viewModel.send(HistoryIntent.Open(HistoryFixtures.holiday.id))
        viewModel.send(HistoryIntent.Delete(HistoryFixtures.holiday.id))

        assertNull(viewModel.state.openNight)
        assertEquals(listOf(HistoryFixtures.friday, HistoryFixtures.september), viewModel.state.nights)
        // 2025 has no night left: the season goes back to all time
        assertNull(viewModel.state.year)
        assertEquals(listOf(2026), viewModel.state.years)
        val shown = requireNotNull(snackbars.hostState.currentSnackbarData) { "no snackbar" }
        assertEquals("Night deleted", shown.visuals.message)
        assertEquals("Undo", shown.visuals.actionLabel)

        shown.performAction()
        settle()
        assertEquals(HistoryFixtures.nights, viewModel.state.nights)
        assertEquals(HistoryFixtures.nights, NightStore(context).nights.value)
    }

    @Test
    fun `a night saved from the Payouts tab shows up at once`() {
        val viewModel = viewModel()
        assertEquals(HistoryFixtures.empty, viewModel.state)
        store.add(HistoryFixtures.holiday)
        settle()
        assertEquals(listOf(HistoryFixtures.holiday), viewModel.state.nights)
        assertEquals("1 Priya 5", viewModel.state.table().first())
    }

    @Test
    fun `every night saved as a CSV file where the player picked, and the snackbar names it`() {
        saveAll()
        val viewModel = viewModel()
        viewModel.send(HistoryIntent.SaveCsv(Uri.parse("content://downloads/poker-nights-2026-10-08.csv")))

        val csv = files.texts.getValue("content://downloads/poker-nights-2026-10-08.csv")
        assertEquals(NightCsv.of(HistoryFixtures.nights), csv)
        assertTrue(csv.startsWith("date,structure,prize_pool,"))
        val shown = requireNotNull(snackbars.hostState.currentSnackbarData) { "no snackbar" }
        assertEquals("Saved poker-nights-2026-10-08.csv", shown.visuals.message)
        assertNull("nothing to undo", shown.visuals.actionLabel)
    }

    @Test
    fun `a CSV file that can't be written says so`() {
        saveAll()
        files.failWrites = true
        viewModel().send(HistoryIntent.SaveCsv(Uri.parse("content://full/nights.csv")))
        assertEquals(
            "Couldn't save the file there. Try another place.",
            snackbars.hostState.currentSnackbarData?.visuals?.message,
        )
    }

    @Test
    fun `a night shared as text lists every player in finishing order`() {
        val text = HistoryText(context.resources)
        assertEquals(
            listOf(
                "Poker night, Oct 2, 2026 (Friday)",
                "Prize pool \$300 · 6 players",
                "",
                "1st Dana: \$205 · 4 knockouts",
                "2nd Marcus: \$125 · 1 knockout",
                "3rd Priya",
                "4th Theo",
                "5th Jo",
                "6th Sam",
            ),
            text.share(HistoryFixtures.friday).lines(),
        )
        assertEquals("Poker night, Sep 12, 2026", text.share(HistoryFixtures.september).lines().first())
        assertEquals(
            "Paid in \$100 · 1 rebuy · 1 add-on · 4 knockouts · bounties \$25",
            text.details(HistoryFixtures.friday.winner),
        )
    }

    // One person under two names (S25b, PP-110) ------------------------------------------------------

    /** The fixtures' nights, and one more where "Dana R." beat Marcus heads-up: 2 points and a win. */
    private fun saveAllWithDanaR() {
        saveAll()
        store.add(
            SavedNight(
                id = 0L,
                date = LocalDate.of(2026, 10, 9),
                structureName = null,
                prizePoolCents = 10_000L,
                players = listOf(
                    NightPlayer("Dana R.", 1, 5_000L, 0, 0L, 0, 0L, 10_000L, 1, 0L),
                    NightPlayer("Marcus", 2, 5_000L, 0, 0L, 0, 0L, 0L, 0, 0L),
                ),
            ),
        )
    }

    private val apart =
        listOf("1 Dana 13", "1 Priya 13", "3 Marcus 12", "4 Theo 5", "5 Jo 4", "5 Sam 4", "7 Dana R. 2", "8 Alex 1")

    @Test
    fun `a player opens with their season and who could be them, likely names first, never someone they played with`() {
        saveAllWithDanaR()
        regulars.remember("Zed", LocalDate.of(2026, 10, 1))
        val viewModel = viewModel()
        assertEquals(apart, viewModel.state.table())

        viewModel.send(HistoryIntent.OpenPlayer("dana r."))
        val panel = requireNotNull(viewModel.state.player)
        assertEquals("Dana R.", panel.name)
        assertEquals(2, panel.standing?.points)
        assertEquals(emptyList<String>(), panel.aliases)
        // Dana looks alike; Marcus played that night with Dana R.; Zed only the Bank has used
        assertEquals(listOf("Dana", "Alex", "Jo", "Priya", "Sam", "Theo", "Zed"), panel.candidates.map { it.name })
        assertEquals(0, panel.candidates.last().nights)
        assertEquals(1, panel.leftOut)

        viewModel.send(HistoryIntent.PickSame("Dana"))
        assertEquals("Dana", viewModel.state.player?.picked?.name)
        viewModel.send(HistoryIntent.PickSame(null))
        assertNull(viewModel.state.player?.picked)
        viewModel.send(HistoryIntent.ClosePlayer)
        assertNull(viewModel.state.player)
    }

    @Test
    fun `a merge adds up the season under the name kept at once, Undo takes it back, and made twice it counts once`() {
        saveAllWithDanaR()
        val viewModel = viewModel()
        viewModel.send(HistoryIntent.OpenPlayer("Dana R."))
        viewModel.send(HistoryIntent.PickSame("Dana"))
        viewModel.send(HistoryIntent.Merge(from = "Dana R.", into = "Dana"))

        // Dana 13 + 2, four nights and two wins; the nights themselves keep the name they were saved with
        assertEquals(
            listOf("1 Dana 15", "2 Priya 13", "3 Marcus 12", "4 Theo 5", "5 Jo 4", "5 Sam 4", "7 Alex 1"),
            viewModel.state.table(),
        )
        assertEquals("4 nights · 2 wins", HistoryText(context.resources).record(viewModel.state.standings.first()))
        assertEquals("Dana R.", viewModel.state.nights.first().winner.name)
        assertNull(viewModel.state.player)
        assertEquals("Dana", RegularsStore(context).merges.value.resolve("Dana R."))
        val shown = requireNotNull(snackbars.hostState.currentSnackbarData) { "no snackbar" }
        assertEquals("Dana R. now counts as Dana", shown.visuals.message)
        assertEquals("Undo", shown.visuals.actionLabel)

        // The same merge again, or the other way round: nothing changes
        val merged = regulars.merges.value
        viewModel.send(HistoryIntent.Merge(from = "dana r.", into = "DANA"))
        viewModel.send(HistoryIntent.Merge(from = "Dana", into = "Dana R."))
        assertEquals(merged, regulars.merges.value)

        shown.performAction()
        settle()
        assertEquals(apart, viewModel.state.table())
        assertEquals(PlayerMerges.NONE, RegularsStore(context).merges.value)
    }

    @Test
    fun `two who played the same night can't be merged`() {
        saveAllWithDanaR()
        val viewModel = viewModel()
        viewModel.send(HistoryIntent.Merge(from = "Dana R.", into = "Marcus"))
        assertEquals(apart, viewModel.state.table())
        assertEquals(PlayerMerges.NONE, regulars.merges.value)
        assertNull(snackbars.hostState.currentSnackbarData)
    }

    @Test
    fun `kept the other way round, the season shows the other name, and a year adds up the same`() {
        saveAllWithDanaR()
        val viewModel = viewModel()
        viewModel.send(HistoryIntent.Merge(from = "Dana", into = "Dana R."))
        assertEquals("1 Dana R. 15", viewModel.state.table().first())
        viewModel.send(HistoryIntent.SelectYear(2026))
        // 2026: Dana 6 + 3 and Dana R. 2, level with Marcus (5 + 5 + 1) but with more wins
        assertEquals("1 Dana R. 11", viewModel.state.table().first())
        assertEquals(listOf("Dana R.", "Marcus"), viewModel.state.leaders.map { it.name })
    }

    @Test
    fun `a name separated counts on its own again, with Undo`() {
        saveAllWithDanaR()
        regulars.setMerges(PlayerMerges.NONE.merge("Dana R.", "Dana"))
        val viewModel = viewModel()
        viewModel.send(HistoryIntent.OpenPlayer("Dana"))
        assertEquals(listOf("Dana R."), viewModel.state.player?.aliases)
        assertEquals(4, viewModel.state.player?.standing?.nights)

        viewModel.send(HistoryIntent.Separate("Dana R."))
        assertEquals(apart, viewModel.state.table())
        assertNull(viewModel.state.player)
        val shown = requireNotNull(snackbars.hostState.currentSnackbarData) { "no snackbar" }
        assertEquals("Dana R. counts on their own again", shown.visuals.message)
        shown.performAction()
        settle()
        assertEquals("1 Dana 15", viewModel.state.table().first())
    }
}
