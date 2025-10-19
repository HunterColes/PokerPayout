package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.components.CompactPlayingCardView
import com.huntercoles.pokerpayout.core.design.components.toPlayingCards

/**
 * Standalone screen displaying poker hand rankings.
 * Can be navigated to from the tools home screen.
 */
@Composable
fun HandRanksScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PokerColors.FeltGreen)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "📋 Poker Hand Rankings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = PokerColors.PokerGold
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(PokerColors.PokerGold)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Content area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            // Hand Rankings
            HandRankItem(
                visual = listOf("A♠", "K♠", "Q♠", "J♠", "10♠"),
                description = "Royal Flush - A, K, Q, J, 10 suited; the unbeatable hand"
            )
            HandRankItem(
                visual = listOf("9♥", "8♥", "7♥", "6♥", "5♥"),
                description = "Straight Flush - Five suited in sequence; highest card wins ties"
            )
            HandRankItem(
                visual = listOf("Q♣", "Q♦", "Q♥", "Q♠", "7♣"),
                description = "Four of a Kind - Four matching ranks plus a kicker to break ties"
            )
            HandRankItem(
                visual = listOf("K♠", "K♥", "K♦", "5♣", "5♠"),
                description = "Full House - Three of one rank with a pair; higher triplet wins"
            )
            HandRankItem(
                visual = listOf("A♥", "J♥", "8♥", "4♥", "2♥"),
                description = "Flush - Any five suited cards; compare the highest then kickers"
            )
            HandRankItem(
                visual = listOf("10♠", "9♦", "8♣", "7♥", "6♠"),
                description = "Straight - Five consecutive ranks; ace may play high or low"
            )
            HandRankItem(
                visual = listOf("J♣", "J♦", "J♥", "9♠", "4♣"),
                description = "Three of a Kind - Three matching ranks with two kickers for ties"
            )
            HandRankItem(
                visual = listOf("A♠", "A♥", "K♣", "K♦", "8♠"),
                description = "Two Pair - Two distinct pairs plus a kicker; top pair sets the rank"
            )
            HandRankItem(
                visual = listOf("Q♣", "Q♦", "10♠", "7♥", "3♣"),
                description = "One Pair - One matching pair and three kickers; kickers decide ties"
            )
            HandRankItem(
                visual = listOf("A♠", "J♦", "9♣", "8♥", "5♠"),
                description = "High Card - No combinations; rely on top card then remaining kickers"
            )
        }
    }
}

@Composable
private fun HandRankItem(visual: List<String>, description: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, PokerColors.PokerGold),
        shape = RoundedCornerShape(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            // Card visuals using centralized component
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                visual.toPlayingCards().forEach { card ->
                    CompactPlayingCardView(card = card)
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(PokerColors.PokerGold)
            )
            
            // Hand description with bold title
            val separator = " - "
            val handTitle = description.substringBefore(separator)
            val details = description.substringAfter(separator, "")
            Text(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(handTitle)
                    }
                    if (details.isNotEmpty()) {
                        append(separator)
                        append(details)
                    }
                },
                fontSize = 12.sp,
                color = PokerColors.CardWhite,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
        }
    }
}
