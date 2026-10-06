package com.huntercoles.pokerpayout.core.design

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Barlow Condensed is bundled, loads, and its numbers are tabular: with `tnum` every digit is the
 * same width, so a ticking clock doesn't wobble. Barlow's default figures are proportional, so the
 * test also proves the feature is doing the work.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TypographyTest {
    private val context = RuntimeEnvironment.getApplication()
    private val measurer = TextMeasurer(createFontFamilyResolver(context), Density(context), LayoutDirection.Ltr)

    private fun width(text: String, style: TextStyle): Int = measurer.measure(text, style).size.width

    @Test
    fun numberStylesUseTabularFigures() {
        listOf(PokerType.Clock, PokerType.DisplayL, PokerType.NumberM, PokerType.CardRank).forEach { numbers ->
            val style = numbers.copy(fontSize = PokerType.NumberM.fontSize)
            val widths = (0..9).map { digit -> width(digit.toString().repeat(4), style) }
            assertEquals("every digit the same width in $style", 1, widths.distinct().size)
        }
    }

    @Test
    fun withoutTnumBarlowFiguresAreProportional() {
        val proportional = PokerType.NumberM.copy(fontFeatureSettings = null)
        assertNotEquals(width("1111", proportional), width("0000", proportional))
    }

    @Test
    fun theClockReadsTheSameWidthWhateverTheTime() {
        assertEquals(width("11:11", PokerType.Clock), width("08:48", PokerType.Clock))
    }

    @Test
    fun barlowIsCondensedComparedWithTheSystemFont() {
        val barlow = width("12:41", PokerType.Clock)
        val system = width("12:41", PokerType.Clock.copy(fontFamily = null, fontFeatureSettings = null))
        assertTrue("Barlow $barlow px vs system $system px", barlow < system)
    }

    @Test
    fun theFontLicenceShipsWithTheApp() {
        val licence = context.assets.open("licenses/OFL-BarlowCondensed.txt").bufferedReader().use { it.readText() }
        assertTrue(licence.contains("SIL OPEN FONT LICENSE Version 1.1"))
        assertTrue(licence.contains("Copyright 2017 The Barlow Project Authors"))
    }
}
