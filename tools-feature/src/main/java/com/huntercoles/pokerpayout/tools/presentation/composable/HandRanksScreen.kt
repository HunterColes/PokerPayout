package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.components.CompactPlayingCardView
import com.huntercoles.pokerpayout.core.design.components.PokerTopBar
import com.huntercoles.pokerpayout.core.design.components.toPlayingCards
import com.huntercoles.pokerpayout.tools.R

/** The ten hands, best first: an example hand and what it is. */
private val HandRanks: List<Pair<List<String>, Int>> = listOf(
    listOf("A♠", "K♠", "Q♠", "J♠", "10♠") to R.string.hand_rank_royal_flush,
    listOf("9♥", "8♥", "7♥", "6♥", "5♥") to R.string.hand_rank_straight_flush,
    listOf("Q♣", "Q♦", "Q♥", "Q♠", "7♣") to R.string.hand_rank_four_of_a_kind,
    listOf("K♠", "K♥", "K♦", "5♣", "5♠") to R.string.hand_rank_full_house,
    listOf("A♥", "J♥", "8♥", "4♥", "2♥") to R.string.hand_rank_flush,
    listOf("10♠", "9♦", "8♣", "7♥", "6♠") to R.string.hand_rank_straight,
    listOf("J♣", "J♦", "J♥", "9♠", "4♣") to R.string.hand_rank_three_of_a_kind,
    listOf("A♠", "A♥", "K♣", "K♦", "8♠") to R.string.hand_rank_two_pair,
    listOf("Q♣", "Q♦", "10♠", "7♥", "3♣") to R.string.hand_rank_one_pair,
    listOf("A♠", "J♦", "9♣", "8♥", "5♠") to R.string.hand_rank_high_card,
)

/**
 * The poker hand rankings, from the Tools tab. (This is also everything the unused "Rules" popup
 * showed, which M2 removed.) The list is restyled as S12 in M6.
 */
@Composable
fun HandRanksScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        PokerTopBar(
            title = stringResource(R.string.hand_ranks_title),
            subtitle = stringResource(R.string.hand_ranks_subtitle),
            onBack = onBack,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PokerColors.FeltGreen)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            HandRanks.forEach { (cards, description) -> HandRankItem(visual = cards, description = description) }
        }
    }
}

@Composable
private fun HandRankItem(visual: List<String>, @StringRes description: Int) {
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
            val text = stringResource(description)
            val separator = " - "
            val handTitle = text.substringBefore(separator)
            val details = text.substringAfter(separator, "")
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
