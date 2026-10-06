package com.huntercoles.pokerpayout.core.design

import androidx.compose.material3.SnackbarHostState
import com.huntercoles.pokerpayout.core.design.components.SnackbarController
import com.huntercoles.pokerpayout.core.design.components.UNDO_WINDOW_MS
import com.huntercoles.pokerpayout.core.design.components.showUndo
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Undo is offered for 8 s of (virtual) time, and only a press inside the window counts. */
class UndoSnackbarTest {
    @Test
    fun `pressing Undo inside the window returns true`() = runTest {
        val host = SnackbarHostState()
        val undone = async { host.showUndo("Rita is out in 8th", "Undo") }
        runCurrent()
        assertEquals("Rita is out in 8th", host.currentSnackbarData?.visuals?.message)
        assertEquals("Undo", host.currentSnackbarData?.visuals?.actionLabel)

        advanceTimeBy(UNDO_WINDOW_MS - 1)
        runCurrent()
        host.currentSnackbarData?.performAction()

        assertTrue(undone.await())
    }

    @Test
    fun `the snackbar goes away after the window and nothing is undone`() = runTest {
        val host = SnackbarHostState()
        val undone = async { host.showUndo("New hand", "Undo") }
        advanceTimeBy(UNDO_WINDOW_MS + 1)
        runCurrent()

        assertFalse(undone.await())
        assertNull(host.currentSnackbarData)
    }

    @Test
    fun `dismissing it (a swipe) does not undo`() = runTest {
        val host = SnackbarHostState()
        val undone = async { host.showUndo("New hand", "Undo") }
        runCurrent()
        host.currentSnackbarData?.dismiss()

        assertFalse(undone.await())
    }

    @Test
    fun `the controller shows on its own host`() = runTest {
        val controller = SnackbarController()
        val undone = async { controller.showUndo("Reset to defaults", "Undo") }
        runCurrent()
        assertEquals("Reset to defaults", controller.hostState.currentSnackbarData?.visuals?.message)
        controller.hostState.currentSnackbarData?.performAction()

        assertTrue(undone.await())
    }
}
