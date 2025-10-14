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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.components.CompactPlayingCardView
import com.huntercoles.pokerpayout.core.design.components.toPlayingCards

/**
 * Full-screen rules popup displaying poker hand rankings.
 * Uses centralized card rendering components for consistency.
 */
@Composable
fun RulesPopup(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = PokerColors.DarkGreen
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header with Rules title and X button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Rules",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = PokerColors.PokerGold
                    )

                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "X",
                            color = PokerColors.PokerGold,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
                    // Poker Hand Ranks Header
                    Text(
                        text = "Poker Hand Ranks",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = PokerColors.PokerGold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Hand Rankings
                    PokerHandRankItem(
                        visual = listOf("A♠", "K♠", "Q♠", "J♠", "10♠"),
                        description = "Royal Flush - A, K, Q, J, 10 suited; the unbeatable hand"
                    )
                    PokerHandRankItem(
                        visual = listOf("9♥", "8♥", "7♥", "6♥", "5♥"),
                        description = "Straight Flush - Five suited in sequence; highest card wins ties"
                    )
                    PokerHandRankItem(
                        visual = listOf("Q♣", "Q♦", "Q♥", "Q♠", "7♣"),
                        description = "Four of a Kind - Four matching ranks plus a kicker to break ties"
                    )
                    PokerHandRankItem(
                        visual = listOf("K♠", "K♥", "K♦", "5♣", "5♠"),
                        description = "Full House - Three of one rank with a pair; higher triplet wins"
                    )
                    PokerHandRankItem(
                        visual = listOf("A♥", "J♥", "8♥", "4♥", "2♥"),
                        description = "Flush - Any five suited cards; compare the highest then kickers"
                    )
                    PokerHandRankItem(
                        visual = listOf("10♠", "9♦", "8♣", "7♥", "6♠"),
                        description = "Straight - Five consecutive ranks; ace may play high or low"
                    )
                    PokerHandRankItem(
                        visual = listOf("J♣", "J♦", "J♥", "9♠", "4♣"),
                        description = "Three of a Kind - Three matching ranks with two kickers for ties"
                    )
                    PokerHandRankItem(
                        visual = listOf("A♠", "A♥", "K♣", "K♦", "8♠"),
                        description = "Two Pair - Two distinct pairs plus a kicker; top pair sets the rank"
                    )
                    PokerHandRankItem(
                        visual = listOf("Q♣", "Q♦", "10♠", "7♥", "3♣"),
                        description = "One Pair - One matching pair and three kickers; kickers decide ties"
                    )
                    PokerHandRankItem(
                        visual = listOf("A♠", "J♦", "9♣", "8♥", "5♠"),
                        description = "High Card - No combinations; rely on top card then remaining kickers"
                    )
                }
            }
        }
    }
}

/**
 * Individual poker hand rank item showing cards and description.
 * Uses the unified CompactPlayingCardView for consistency.
 */
@Composable
fun PokerHandRankItem(visual: List<String>, description: String) {
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
