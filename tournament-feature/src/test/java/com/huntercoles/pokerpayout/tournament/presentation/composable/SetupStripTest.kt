package com.huntercoles.pokerpayout.tournament.presentation.composable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The setup strip shows whole settings, as many as fit, and never a cut one (the device matrix
 * found "5 players · $20 buy-in · 20-…" at 320 dp). The screens' layout checks (TournamentScreenGoldenTest)
 * hold the drawn strip to it on every size and font scale; this pins the choice itself.
 */
class SetupStripTest {

    private val parts = listOf("9 players", "$40 buy-in", "20-min levels", "5,000 chips")
    private val separator = " · "

    private fun shown(room: Int) = fittingPrefix(parts, separator) { it.length <= room }

    @Test
    fun `everything shows when there is room`() {
        assertEquals("9 players · $40 buy-in · 20-min levels · 5,000 chips", shown(room = 100))
    }

    @Test
    fun `a narrow strip drops whole settings from the end`() {
        assertEquals("9 players · $40 buy-in · 20-min levels", shown(room = 40))
        assertEquals("9 players · $40 buy-in", shown(room = 30))
        assertEquals("9 players · $40 buy-in", shown(room = "9 players · $40 buy-in".length))
    }

    @Test
    fun `the first setting always shows, even when it has to wrap`() {
        assertEquals("9 players", shown(room = 12))
        assertEquals("9 players", shown(room = 3))
    }

    @Test
    fun `no settings, no line`() {
        assertEquals("", fittingPrefix(emptyList(), separator) { true })
    }

    @Test
    fun `it stops at the first setting that doesn't fit, even if a shorter one follows`() {
        val uneven = listOf("9 players", "a very long setting indeed", "5,000 chips")
        assertEquals("9 players", fittingPrefix(uneven, separator) { it.length <= 30 })
    }
}
