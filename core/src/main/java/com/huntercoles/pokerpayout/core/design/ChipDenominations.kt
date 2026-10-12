package com.huntercoles.pokerpayout.core.design

import androidx.compose.ui.graphics.Color

/**
 * Standard poker chip colors and denominations. Their names are resources, by colour
 * ([com.huntercoles.pokerpayout.core.design.components.chipColourName]).
 */
object ChipDenominations {
    data class ChipInfo(
        val value: Int,
        val color: Color
    )

    val WHITE = ChipInfo(1, Color(0xFFFFFFFF))
    val RED = ChipInfo(5, Color(0xFFDC143C))
    val BLUE = ChipInfo(10, Color(0xFF4169E1))
    val GREY = ChipInfo(20, Color(0xFF808080))
    val GREEN = ChipInfo(25, Color(0xFF228B22))
    val ORANGE = ChipInfo(50, Color(0xFFFF8C00))
    val BLACK = ChipInfo(100, Color(0xFF000000))
    val PINK = ChipInfo(250, Color(0xFFFF69B4))
    val PURPLE = ChipInfo(500, Color(0xFF800080))
    val YELLOW = ChipInfo(1000, Color(0xFFFFD700))
    val LIGHT_BLUE = ChipInfo(2000, Color(0xFF87CEEB))
    val BROWN = ChipInfo(5000, Color(0xFF8B4513))

    val ALL_CHIPS = listOf(
        WHITE, RED, BLUE, GREY, GREEN, ORANGE,
        BLACK, PINK, PURPLE, YELLOW, LIGHT_BLUE, BROWN
    )

    // Get chip by value
    fun getChipByValue(value: Int): ChipInfo? {
        return ALL_CHIPS.firstOrNull { it.value == value }
    }
}
