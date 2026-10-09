package com.huntercoles.pokerpayout.bank.presentation

import com.huntercoles.pokerpayout.core.domain.history.NightPlayer
import com.huntercoles.pokerpayout.core.domain.history.SavedNight
import com.huntercoles.pokerpayout.core.domain.players.KnownPlayer
import com.huntercoles.pokerpayout.core.domain.players.PlayerMerges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Tonight's players picked from the regulars (S25, PP-110), through the real Bank over Robolectric's
 * preferences: the roster from History and the Bank's own names, regulars first, in an order that
 * holds while the sheet is open; a regular takes the first seat nobody named, or a new seat once all
 * have names (the Tournament tab's count too, up to 30), and a tap again frees the seat; quick add;
 * merged spellings as one regular; and every name typed in the Bank joining the regulars, as before.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BankRegularsTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var kit: BankTestKit

    private val today = LocalDate.parse("2026-10-09")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        kit = BankTestKit(dispatcher)
    }

    @After
    fun tearDown() {
        kit.clear()
        Dispatchers.resetMain()
    }

    private fun night(date: String, vararg names: String) = SavedNight(
        id = 0L,
        date = LocalDate.parse(date),
        structureName = null,
        prizePoolCents = 0L,
        players = names.mapIndexed { index, name -> NightPlayer(name, index + 1, 2_000L, 0, 0L, 0, 0L, 0L, 0, 0L) },
    )

    /** Dana every week; Marcus and Priya twice; Theo once, last; Old Al long ago. */
    private fun saveHistory() {
        listOf(
            night("2026-01-17", "Old Al", "Dana"),
            night("2026-09-12", "Dana", "Marcus", "Priya"),
            night("2026-09-19", "Priya", "Dana", "Marcus"),
            night("2026-10-02", "Dana", "Theo"),
        ).forEach { kit.nights.add(it) }
    }

    private fun BankViewModel.sheet(): RegularsModel {
        val state = uiState.value
        return RegularsModel.of(state, (state.sheet as BankSheet.Regulars).order)
    }

    private fun RegularsModel.names() = rows.map { it.regular.name }

    private fun RegularsModel.seated() = rows.filter { it.seated }.associate { it.regular.name to it.seatId }

    private fun BankViewModel.names() = uiState.value.players.map { it.name }

    @Test
    fun `the sheet lists History's players and the Bank's names, regulars first, and keeps its order while open`() = with(kit) {
        saveHistory()
        regularsStore.remember("Bea", LocalDate.parse("2026-10-01"))
        configure(players = 4, buyIn = 20.0)
        val viewModel = newViewModel()
        viewModel.send(BankIntent.ShowRegulars)

        val opened = viewModel.sheet()
        assertEquals(listOf("Dana", "Marcus", "Priya", "Theo", "Old Al", "Bea"), opened.names())
        assertEquals(4, opened.seats)
        assertEquals(0, opened.named)
        assertTrue(opened.seated().isEmpty())

        // Bea and Old Al sit down (seen today now), and nobody moves
        viewModel.send(BankIntent.ToggleRegular("Bea"))
        viewModel.send(BankIntent.ToggleRegular("Old Al"))
        val after = viewModel.sheet()
        assertEquals(opened.names(), after.names())
        assertEquals(mapOf("Old Al" to 2, "Bea" to 1), after.seated())
        assertEquals(listOf("Bea", "Old Al", "Player 3", "Player 4"), viewModel.names())
        // Opened again, seen today counts: Old Al before Theo
        viewModel.send(BankIntent.DismissSheet, BankIntent.ShowRegulars)
        assertEquals(listOf("Dana", "Marcus", "Priya", "Old Al", "Theo", "Bea"), viewModel.sheet().names())
    }

    @Test
    fun `a regular takes the first seat nobody named, and a tap again frees it`() = with(kit) {
        saveHistory()
        configure(players = 3, buyIn = 20.0)
        val viewModel = newViewModel()
        viewModel.send(BankIntent.PlayerNameChanged(2, "Marcus"), BankIntent.ShowRegulars)
        assertEquals(mapOf("Marcus" to 2), viewModel.sheet().seated())

        viewModel.send(BankIntent.ToggleRegular("Dana"))
        viewModel.send(BankIntent.ToggleRegular("Priya"))
        assertEquals(listOf("Dana", "Marcus", "Priya"), viewModel.names())

        viewModel.send(BankIntent.ToggleRegular("Marcus"))
        assertEquals(listOf("Dana", "Player 2", "Priya"), viewModel.names())
        assertEquals(1, viewModel.sheet().open)
        // The money stays with the seat, as after any rename
        assertEquals("Player 2", bankPreferences.getPlayerName(2))
        assertEquals(listOf("Dana", "Player 2", "Priya"), restartProcess().names())
    }

    @Test
    fun `with every seat named, a regular adds a seat, here and in the Tournament tab, up to 30`() = with(kit) {
        saveHistory()
        configure(players = 3, buyIn = 20.0)
        val viewModel = newViewModel()
        listOf("Dana", "Marcus", "Priya").forEach { viewModel.send(BankIntent.ToggleRegular(it)) }
        viewModel.send(BankIntent.ShowRegulars)
        assertEquals(0, viewModel.sheet().open)
        assertTrue(viewModel.sheet().canAddSeat)

        viewModel.send(BankIntent.ToggleRegular("Theo"))
        assertEquals(listOf("Dana", "Marcus", "Priya", "Theo"), viewModel.names())
        assertEquals(4, tournamentPreferences.getPlayerCount())
        assertEquals(4, viewModel.uiState.value.rows.size)
        assertEquals(8_000L, viewModel.uiState.value.totalPoolCents)
        assertEquals(listOf("Dana", "Marcus", "Priya", "Theo"), restartProcess().names())

        // Thirty named: nobody else can sit down
        configure(players = 30, buyIn = 20.0)
        val full = newViewModel()
        (5..30).forEach { full.send(BankIntent.PlayerNameChanged(it, "Guest $it")) }
        full.send(BankIntent.ShowRegulars)
        assertFalse(full.sheet().canSeat)
        assertFalse(full.sheet().canAdd("Old Al"))
        full.send(BankIntent.ToggleRegular("Old Al"))
        full.send(BankIntent.AddRegular("Zed"))
        assertEquals(30, tournamentPreferences.getPlayerCount())
        assertFalse("Old Al" in full.names() || "Zed" in full.names())
    }

    @Test
    fun `quick add seats a new name and keeps it, and a regular's own spelling seats that regular`() = with(kit) {
        saveHistory()
        configure(players = 4, buyIn = 20.0)
        val viewModel = newViewModel()
        viewModel.send(BankIntent.ShowRegulars)

        viewModel.send(BankIntent.AddRegular("  Zoë   B. "))
        viewModel.send(BankIntent.AddRegular("dana"))
        assertEquals(listOf("Zoë B.", "Dana", "Player 3", "Player 4"), viewModel.names())
        // Already at the table, a seat nobody named, or nothing at all: nothing happens
        viewModel.send(BankIntent.AddRegular("DANA"))
        viewModel.send(BankIntent.AddRegular("Player 7"))
        viewModel.send(BankIntent.AddRegular("   "))
        assertEquals(listOf("Zoë B.", "Dana", "Player 3", "Player 4"), viewModel.names())

        // New since the sheet opened: first in the list
        assertEquals("Zoë B.", viewModel.sheet().names().first())
        assertEquals(KnownPlayer("Zoë B.", today), regularsStore.players.value.first { it.name == "Zoë B." })
        assertTrue(viewModel.sheet().canAdd("Theo"))
        assertFalse(viewModel.sheet().canAdd("zoë b."))
        assertEquals(listOf("Zoë B."), viewModel.sheet().matching("zo").map { it.regular.name })
    }

    @Test
    fun `names merged in History are one regular, whichever spelling is at the table`() = with(kit) {
        kit.nights.add(night("2026-09-12", "Mike", "Dana"))
        kit.nights.add(night("2026-09-19", "Mike R.", "Dana"))
        regularsStore.setMerges(PlayerMerges.NONE.merge("Mike R.", "Mike"))
        configure(players = 3, buyIn = 20.0)
        val viewModel = newViewModel()
        viewModel.send(BankIntent.PlayerNameChanged(1, "Mike R."), BankIntent.ShowRegulars)

        assertEquals(listOf("Dana", "Mike"), viewModel.sheet().names().sorted())
        assertEquals(mapOf("Mike" to 1), viewModel.sheet().seated())
        viewModel.send(BankIntent.AddRegular("mike r."))
        assertEquals(listOf("Mike R.", "Player 2", "Player 3"), viewModel.names())
        viewModel.send(BankIntent.ToggleRegular("Mike"))
        assertEquals(listOf("Player 1", "Player 2", "Player 3"), viewModel.names())
        viewModel.send(BankIntent.AddRegular("MIKE R."))
        assertEquals(listOf("Mike", "Player 2", "Player 3"), viewModel.names())
    }

    @Test
    fun `every name typed in the Bank joins the regulars, a seat nobody named doesn't`() = with(kit) {
        configure(players = 3, buyIn = 20.0)
        val viewModel = newViewModel()
        viewModel.send(
            BankIntent.PlayerNameChanged(1, "Bea"),
            BankIntent.PlayerNameChanged(2, ""),
            BankIntent.PlayerNameChanged(3, "Player 3"),
        )
        assertEquals(listOf(KnownPlayer("Bea", today)), regularsStore.players.value)
        assertEquals(listOf("Bea"), viewModel.uiState.value.roster.map { it.name })
        // The Bank's reset clears tonight's names; the regulars keep them
        viewModel.send(BankIntent.ShowResetConfirm, BankIntent.ConfirmReset)
        assertEquals(listOf("Player 1", "Player 2", "Player 3"), viewModel.names())
        assertEquals(listOf("Bea"), restartProcess().uiState.value.roster.map { it.name })
    }
}
