package com.huntercoles.pokerpayout.tools.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.design.components.SnackbarController
import com.huntercoles.pokerpayout.core.domain.history.NightStore
import com.huntercoles.pokerpayout.tools.presentation.composable.HistoryFixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * History (PP-037) through the real ViewModel over Robolectric's preferences, on virtual time: the
 * nights the latest first with the season's standings; a year's season and its player of the year;
 * a night opened and closed; delete with Undo on the snackbar; and the night shared as text.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val snackbars = SnackbarController()
    private val viewModels = ViewModelStore()
    private lateinit var store: NightStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context.getSharedPreferences("night_history", Context.MODE_PRIVATE).edit().clear().commit()
        store = NightStore(context)
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
                HistoryViewModel(store, snackbars, HistoryMessages(context)) as T
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
}
