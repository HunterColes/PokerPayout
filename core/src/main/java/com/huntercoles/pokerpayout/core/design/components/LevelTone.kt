package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.ui.graphics.Color
import com.huntercoles.pokerpayout.core.design.PokerColors

/**
 * How urgent a level's time left is, which sets [LevelProgress]'s colour. Colour is never the only
 * signal: the clock adds a "Final minutes" pill and the played time next to the bar.
 */
enum class LevelTone(val color: Color) {
    /** Plenty of time: Live. */
    Normal(PokerColors.Live),

    /** The last quarter of a level, and breaks: gold. */
    Low(PokerColors.PokerGold),

    /** The last tenth: Danger, pulsing gently (static under Reduce motion). */
    Critical(PokerColors.Danger),
}
