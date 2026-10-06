package com.huntercoles.pokerpayout.core.design.components

/**
 * A playing card as the UI shows it ([CardFace]): rank "A" to "2" (a ten is "T" or "10"), suit "s",
 * "h", "d" or "c". Separate from the odds engine's packed cards, which are built for speed.
 */
data class PlayingCard(
    val rank: String,
    val suit: String
) {
    override fun toString() = "$rank$suit"
}
