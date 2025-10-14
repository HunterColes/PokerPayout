package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale

/**
 * Standard modifier for horizontally inverting icons (typically used for reset/refresh icons)
 */
fun Modifier.invertHorizontally(): Modifier = this.scale(-1f, 1f)
