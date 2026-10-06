package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerType
import java.util.Locale

/** A section label in Barlow caps ("LEVEL TIME LEFT"). Chalk by default, gold for the live section. */
@Composable
fun PokerEyebrow(text: String, modifier: Modifier = Modifier, color: Color = PokerColors.Chalk) {
    Text(text = text.uppercase(Locale.ROOT), style = PokerType.Eyebrow, color = color, modifier = modifier)
}
