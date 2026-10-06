package com.huntercoles.pokerpayout.tournament.presentation

import android.content.pm.ActivityInfo
import com.huntercoles.pokerpayout.tournament.presentation.TournamentOrientation.TableViewInputs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The Tournament tab's one state machine (S1 v2): Setup → Folding → Running ⇄ PanelOpen, and back to
 * Setup on a reset; plus where it lets the phone turn (PP-079, PP-088).
 */
class TournamentModeTest {

    @Test
    fun `start folds setup into the clock once, then the strip opens and closes the panel`() {
        var ui = TournamentUi.initial(clockStarted = false)
        assertEquals(TournamentMode.Setup, ui.mode)
        assertEquals(ui, ui.settledFor(clockStarted = false, reducedMotion = false))

        ui = ui.pressStart()
        assertEquals(TournamentMode.Setup, ui.settledFor(clockStarted = false, reducedMotion = false).mode) // not started yet
        ui = ui.settledFor(clockStarted = true, reducedMotion = false)
        assertEquals(TournamentMode.Folding, ui.mode)
        assertFalse(ui.startPressed)
        assertEquals(ui, ui.settledFor(clockStarted = true, reducedMotion = false)) // stays folding until the fold ends

        ui = ui.foldFinished()
        assertEquals(TournamentMode.Running, ui.mode)
        ui = ui.openPanel()
        assertEquals(TournamentMode.PanelOpen, ui.mode)
        ui = ui.unlock()
        assertTrue(ui.panelUnlocked)
        ui = ui.closePanel()
        assertEquals(TournamentMode.Running, ui.mode)
        assertFalse(ui.panelUnlocked) // the lock comes back with the panel closed
    }

    @Test
    fun `reduce motion and a clock that already exists skip the fold`() {
        val pressed = TournamentUi.initial(clockStarted = false).pressStart()
        assertEquals(TournamentMode.Running, pressed.settledFor(clockStarted = true, reducedMotion = true).mode)

        // Started some other way (a restart, the table view): straight to the clock.
        assertEquals(TournamentMode.Running, TournamentUi().settledFor(clockStarted = true, reducedMotion = false).mode)
        assertEquals(TournamentMode.Running, TournamentUi.initial(clockStarted = true).mode)
    }

    @Test
    fun `a reset unfolds setup again from any mood`() {
        TournamentMode.entries.filter { it != TournamentMode.Setup }.forEach { mode ->
            val ui = TournamentUi(mode = mode, panelUnlocked = true, rotationPaused = true)
            val settled = ui.settledFor(clockStarted = false, reducedMotion = false)
            assertEquals(TournamentMode.Setup, settled.mode, "from $mode")
            assertFalse(settled.panelUnlocked)
            assertFalse(settled.rotationPaused)
        }
    }

    @Test
    fun `transitions that don't apply leave the state alone`() {
        val setup = TournamentUi()
        assertEquals(setup, setup.openPanel())
        assertEquals(setup, setup.closePanel())
        assertEquals(setup, setup.unlock())
        assertEquals(setup, setup.foldFinished())
        val running = TournamentUi(mode = TournamentMode.Running)
        assertEquals(running, running.pressStart())
    }

    // ------------------------------------------------------------------ rotation

    @Test
    fun `a phone is portrait until a clock exists, then follows its own rotation setting`() {
        val phone = 360
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, TournamentOrientation.requested(phone, false, false, false))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER, TournamentOrientation.requested(phone, true, false, false))
        // ⤢ forces landscape, clock or not; ✕ after turning the phone keeps it upright for that turn.
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, TournamentOrientation.requested(phone, true, true, false))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, TournamentOrientation.requested(phone, true, false, true))
    }

    @Test
    fun `tablets and foldables turn freely`() {
        listOf(600, 800).forEach { tablet ->
            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                TournamentOrientation.requested(tablet, false, false, false),
            )
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, TournamentOrientation.requested(tablet, true, false, false))
            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                TournamentOrientation.requested(tablet, true, true, false),
            )
        }
    }

    @Test
    fun `the table view is a phone on its side with a clock, or forced`() {
        val clock = TableViewInputs(exists = true, forced = false, rotationPaused = false)
        assertTrue(TournamentOrientation.showsTableView(360, landscape = true, clock = clock))
        assertFalse(TournamentOrientation.showsTableView(360, landscape = false, clock = clock))
        assertFalse(TournamentOrientation.showsTableView(360, landscape = true, clock = clock.copy(exists = false)))
        assertFalse(TournamentOrientation.showsTableView(360, landscape = true, clock = clock.copy(rotationPaused = true)))
        assertTrue(TournamentOrientation.showsTableView(360, landscape = false, clock = clock.copy(forced = true)))
        // A tablet on its side shows the two-pane clock (Z4); ⤢ still opens the table view.
        assertFalse(TournamentOrientation.showsTableView(800, landscape = true, clock = clock))
        assertTrue(TournamentOrientation.showsTableView(800, landscape = true, clock = clock.copy(forced = true)))
    }
}
