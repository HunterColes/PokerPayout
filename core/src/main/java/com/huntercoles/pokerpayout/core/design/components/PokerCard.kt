package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens

/**
 * Unified playing card data model for UI display across the app.
 * This is separate from the poker engine's Card(Char, Char) for performance.
 */
data class PlayingCard(
    val rank: String,
    val suit: String
) {
    override fun toString() = "$rank$suit"
    
    fun getSuitSymbol(): String = when(suit.lowercase()) {
        "h" -> "♥"
        "d" -> "♦"
        "c" -> "♣"
        "s" -> "♠"
        else -> suit
    }
    
    fun getSuitColor(): Color = when(suit.lowercase()) {
        "h", "d" -> Color.Red
        "c", "s" -> Color.Black
        else -> PokerColors.CardWhite
    }
}

/**
 * Displays a playing card with rank and suit.
 * Unified component used across rules popup and odds calculator.
 * 
 * @param card The card to display
 * @param onClick Optional click handler (e.g., to remove the card)
 * @param width Card width (defaults to standard)
 * @param height Card height (defaults to standard)
 * @param fontSize Font size for rank and suit
 * @param modifier Additional modifiers
 */
@Composable
fun PlayingCardView(
    card: PlayingCard,
    onClick: (() -> Unit)? = null,
    width: Dp = PokerDimens.CardWidth,
    height: Dp = PokerDimens.CardHeight,
    fontSize: Int = 14,
    modifier: Modifier = Modifier
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable { onClick() }
    } else {
        Modifier
    }
    
    Card(
        modifier = modifier
            .size(width = width, height = height)
            .then(clickModifier),
        colors = CardDefaults.cardColors(containerColor = PokerColors.CardWhite),
        border = BorderStroke(2.dp, card.getSuitColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = card.rank,
                color = card.getSuitColor(),
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = card.getSuitSymbol(),
                color = card.getSuitColor(),
                fontSize = (fontSize + 4).sp
            )
        }
    }
}

/**
 * Compact card view for rules popup and dense displays.
 * Uses fixed smaller dimensions optimized for list items.
 * 
 * @param card The card to display
 * @param modifier Additional modifiers
 */
@Composable
fun CompactPlayingCardView(
    card: PlayingCard,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(26.dp)
            .height(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = card.rank,
                fontSize = 10.sp,
                color = Color.Black,
                textAlign = TextAlign.Center
            )
            Text(
                text = card.getSuitSymbol(),
                fontSize = 10.sp,
                color = Color.Black,
                modifier = Modifier.padding(start = 1.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Converts a list of card strings (e.g., "A♠") to PlayingCard objects.
 * Handles both unicode suit symbols and single-character suit codes.
 */
fun List<String>.toPlayingCards(): List<PlayingCard> {
    return this.mapNotNull { cardStr ->
        if (cardStr.isEmpty()) return@mapNotNull null
        
        val rank = cardStr.dropLast(1)
        val suitChar = cardStr.last()
        
        // Convert unicode symbols to single-letter codes
        val suit = when (suitChar) {
            '♠' -> "s"
            '♥' -> "h"
            '♦' -> "d"
            '♣' -> "c"
            else -> suitChar.toString()
        }
        
        PlayingCard(rank, suit)
    }
}
