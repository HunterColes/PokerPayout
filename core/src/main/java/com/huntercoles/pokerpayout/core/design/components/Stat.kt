package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.ui.graphics.Color
import com.huntercoles.pokerpayout.core.design.PokerColors

/** One number on a [StatStrip]: "PLAYERS / 7 / of 9". [valueColor] is gold for money that's live. */
data class Stat(
    val label: String,
    val value: String,
    val sub: String? = null,
    val valueColor: Color = PokerColors.CardWhite,
)
