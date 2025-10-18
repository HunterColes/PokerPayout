package com.huntercoles.pokerpayout.core.design

import androidx.compose.ui.graphics.Color

/**
 * Standard poker chip colors and denominations
 */
object ChipDenominations {
    data class ChipInfo(
        val value: Int,
        val color: Color,
        val name: String
    )

    val WHITE = ChipInfo(1, Color(0xFFFFFFFF), "White")
    val RED = ChipInfo(5, Color(0xFFDC143C), "Red")
    val BLUE = ChipInfo(10, Color(0xFF4169E1), "Blue")
    val GREY = ChipInfo(20, Color(0xFF808080), "Grey")
    val GREEN = ChipInfo(25, Color(0xFF228B22), "Green")
    val ORANGE = ChipInfo(50, Color(0xFFFF8C00), "Orange")
    val BLACK = ChipInfo(100, Color(0xFF000000), "Black")
    val PINK = ChipInfo(250, Color(0xFFFF69B4), "Pink")
    val PURPLE = ChipInfo(500, Color(0xFF800080), "Purple")
    val YELLOW = ChipInfo(1000, Color(0xFFFFD700), "Yellow")
    val LIGHT_BLUE = ChipInfo(2000, Color(0xFF87CEEB), "Light Blue")
    val BROWN = ChipInfo(5000, Color(0xFF8B4513), "Brown")

    val ALL_CHIPS = listOf(
        WHITE, RED, BLUE, GREY, GREEN, ORANGE,
        BLACK, PINK, PURPLE, YELLOW, LIGHT_BLUE, BROWN
    )

    // Get chips up to a certain value
    fun getChipsUpTo(maxValue: Int): List<ChipInfo> {
        return ALL_CHIPS.filter { it.value <= maxValue }
    }

    // Get chip by value
    fun getChipByValue(value: Int): ChipInfo? {
        return ALL_CHIPS.firstOrNull { it.value == value }
    }
}
