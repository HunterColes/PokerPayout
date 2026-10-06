package com.huntercoles.pokerpayout.core.domain

import com.huntercoles.pokerpayout.core.domain.model.ClockStatus
import com.huntercoles.pokerpayout.core.domain.model.PurchaseWindow
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** The rebuy and add-on windows (PP-030), level by level, at every boundary. */
class PurchaseWindowTest {

    private fun at(level: Int, onBreak: Boolean = false, breaks: List<Int> = listOf(4, 8), finished: Boolean = false) =
        ClockStatus(started = true, level = level, onBreak = onBreak, breakAfterLevels = breaks, finished = finished)

    @Test
    fun `no cutoff is open all night, started or not`() {
        assertEquals(PurchaseWindow.NoCutoff, PurchaseWindow.rebuys(0, at(20)))
        assertEquals(PurchaseWindow.NoCutoff, PurchaseWindow.addOns(0, at(20, finished = true)))
    }

    @Test
    fun `before the clock starts both are open`() {
        assertEquals(PurchaseWindow.OpenUntilLevel(4), PurchaseWindow.rebuys(4, ClockStatus.NOT_STARTED))
        assertEquals(PurchaseWindow.OpenUntilLevel(4), PurchaseWindow.addOns(4, ClockStatus.NOT_STARTED))
    }

    @Test
    fun `rebuys are open through the cutoff level and close when it ends`() {
        val expected = listOf(true, true, true, true, false, false)
        assertEquals(expected, (1..6).map { PurchaseWindow.rebuys(4, at(it)).isOpen })
        // The break after level 4 starts when level 4 ends
        assertEquals(PurchaseWindow.ClosedAfterLevel(4), PurchaseWindow.rebuys(4, at(4, onBreak = true)))
        // A break before the cutoff doesn't close anything
        assertEquals(PurchaseWindow.OpenUntilLevel(4), PurchaseWindow.rebuys(4, at(2, onBreak = true, breaks = listOf(2))))
    }

    @Test
    fun `add-ons run to the end of the first break after the cutoff`() {
        assertEquals(PurchaseWindow.OpenUntilBreak(1, afterLevel = 4), PurchaseWindow.addOns(4, at(3)))
        assertEquals(PurchaseWindow.OpenUntilBreak(1, afterLevel = 4), PurchaseWindow.addOns(4, at(4)))
        assertEquals(PurchaseWindow.OpenUntilBreak(1, afterLevel = 4), PurchaseWindow.addOns(4, at(4, onBreak = true)))
        assertEquals(PurchaseWindow.ClosedAfterBreak(1), PurchaseWindow.addOns(4, at(5)))
        // Cutoff 5 with breaks after 4 and 8: break 2 is the first after it
        assertEquals(PurchaseWindow.OpenUntilBreak(2, afterLevel = 8), PurchaseWindow.addOns(5, at(8, onBreak = true)))
        assertEquals(PurchaseWindow.ClosedAfterBreak(2), PurchaseWindow.addOns(5, at(9)))
    }

    @Test
    fun `with no break after the cutoff add-ons close with the rebuys`() {
        assertEquals(PurchaseWindow.OpenUntilLevel(9), PurchaseWindow.addOns(9, at(9)))
        assertEquals(PurchaseWindow.ClosedAfterLevel(9), PurchaseWindow.addOns(9, at(10)))
        assertEquals(PurchaseWindow.ClosedAfterLevel(2), PurchaseWindow.addOns(2, at(3, breaks = emptyList())))
    }

    @Test
    fun `a finished clock closes both`() {
        assertEquals(PurchaseWindow.ClosedAfterLevel(12), PurchaseWindow.rebuys(12, at(9, finished = true)))
        assertEquals(PurchaseWindow.ClosedAfterBreak(1), PurchaseWindow.addOns(4, at(4, finished = true)))
    }
}
