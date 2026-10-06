package com.huntercoles.pokerpayout.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.max
import kotlin.math.min

/**
 * The palette keeps the contrast promises in the design spec (section 2.2): every pairing the
 * components use, computed with the WCAG relative-luminance formula.
 */
class DesignTokensTest {
    @Test
    fun `text pairings meet WCAG AA (4,5 to 1)`() {
        val pairs = listOf(
            "CardWhite on PokerBlack" to contrast(PokerColors.CardWhite, PokerColors.PokerBlack),
            "CardWhite on FeltGreen" to contrast(PokerColors.CardWhite, PokerColors.FeltGreen),
            "CardWhite on DarkGreen" to contrast(PokerColors.CardWhite, PokerColors.DarkGreen),
            "CardWhite on FeltHigh" to contrast(PokerColors.CardWhite, PokerColors.FeltHigh),
            "PokerGold on PokerBlack" to contrast(PokerColors.PokerGold, PokerColors.PokerBlack),
            "PokerGold on FeltGreen" to contrast(PokerColors.PokerGold, PokerColors.FeltGreen),
            "PokerGold on DarkGreen" to contrast(PokerColors.PokerGold, PokerColors.DarkGreen),
            "PokerGold on FeltHigh" to contrast(PokerColors.PokerGold, PokerColors.FeltHigh),
            "PokerGold on GoldWash" to contrast(PokerColors.PokerGold, PokerColors.GoldWash),
            "PokerGold on FeltDeep" to contrast(PokerColors.PokerGold, PokerColors.FeltDeep),
            "FeltDeep on PokerGold" to contrast(PokerColors.FeltDeep, PokerColors.PokerGold),
            "Chalk on FeltDeep" to contrast(PokerColors.Chalk, PokerColors.FeltDeep),
            "Chalk on FeltGreen" to contrast(PokerColors.Chalk, PokerColors.FeltGreen),
            "Chalk on DarkGreen" to contrast(PokerColors.Chalk, PokerColors.DarkGreen),
            "Chalk on PokerBlack" to contrast(PokerColors.Chalk, PokerColors.PokerBlack),
            "Live on FeltGreen" to contrast(PokerColors.Live, PokerColors.FeltGreen),
            "Live on DarkGreen" to contrast(PokerColors.Live, PokerColors.DarkGreen),
            "Danger on PokerBlack" to contrast(PokerColors.Danger, PokerColors.PokerBlack),
            "Danger on FeltGreen" to contrast(PokerColors.Danger, PokerColors.FeltGreen),
            "Danger on DangerWash" to contrast(PokerColors.Danger, PokerColors.DangerWash),
            "White on DangerFill" to contrast(Color.White, PokerColors.DangerFill),
            "SuitRed on CardWhite" to contrast(PokerColors.SuitRed, PokerColors.CardWhite),
            "SuitBlack on CardWhite" to contrast(PokerColors.SuitBlack, PokerColors.CardWhite),
            "SuitDiamond4 on CardWhite" to contrast(PokerColors.SuitDiamond4, PokerColors.CardWhite),
            "SuitClub4 on CardWhite" to contrast(PokerColors.SuitClub4, PokerColors.CardWhite),
        )
        val failing = pairs.filter { (_, ratio) -> ratio < AA_TEXT }
        assertTrue(failing.isEmpty(), "Below 4.5:1: $failing")
    }

    @Test
    fun `control edges are at least 3 to 1 on every felt (WCAG 1,4,11)`() {
        listOf(PokerColors.FeltDeep, PokerColors.FeltGreen, PokerColors.DarkGreen).forEach { felt ->
            assertTrue(contrast(PokerColors.FeltEdge, felt) >= AA_UI, "FeltEdge on $felt")
        }
    }

    @Test
    fun `pairings the spec forbids really are below AA, so the rules stay honest`() {
        // Chalk is never used on FeltHigh, DarkGold never for small text, FeltLine only for dividers.
        assertTrue(contrast(PokerColors.Chalk, PokerColors.FeltHigh) < AA_TEXT)
        assertTrue(contrast(PokerColors.DarkGold, PokerColors.FeltGreen) < AA_TEXT)
        assertTrue(contrast(PokerColors.FeltLine, PokerColors.FeltGreen) < AA_UI)
    }

    @Test
    fun `the six original colours are unchanged`() {
        assertEquals(Color(0xFF0B0B0B), PokerColors.PokerBlack)
        assertEquals(Color(0xFF0A3D2E), PokerColors.FeltGreen)
        assertEquals(Color(0xFF0D4F3C), PokerColors.DarkGreen)
        assertEquals(Color(0xFFFFD700), PokerColors.PokerGold)
        assertEquals(Color(0xFFB8860B), PokerColors.DarkGold)
        assertEquals(Color(0xFFF5F5F5), PokerColors.CardWhite)
    }

    @Test
    fun `the red chip keeps its physical colour`() {
        assertEquals(Color(0xFFDC143C), ChipDenominations.RED.color)
    }

    private fun contrast(foreground: Color, background: Color): Double {
        val a = foreground.luminance().toDouble()
        val b = background.luminance().toDouble()
        return (max(a, b) + FLARE) / (min(a, b) + FLARE)
    }

    private companion object {
        const val AA_TEXT = 4.5
        const val AA_UI = 3.0
        const val FLARE = 0.05
    }
}
