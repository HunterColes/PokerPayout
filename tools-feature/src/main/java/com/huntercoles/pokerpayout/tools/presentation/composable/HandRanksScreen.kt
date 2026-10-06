package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.CardEmphasis
import com.huntercoles.pokerpayout.core.design.components.CardFace
import com.huntercoles.pokerpayout.core.design.components.CardFaceSize
import com.huntercoles.pokerpayout.core.design.components.PlayingCard
import com.huntercoles.pokerpayout.core.design.components.PokerTopBar
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.poker.HandRank
import com.huntercoles.pokerpayout.tools.poker.SevenCardFrequencies
import com.huntercoles.pokerpayout.tools.presentation.HandRanksViewModel
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import com.huntercoles.pokerpayout.core.R as CoreR

/** One hand: its name, an example (cards that make it bright, kickers dim) and how ties break. */
private class HandExample(
    val rank: HandRank,
    @StringRes val name: Int,
    @StringRes val rule: Int,
    /** "As Kd 10h": rank then suit letter; a trailing "*" marks a kicker. */
    val cards: String,
)

private val Hands = listOf(
    HandExample(HandRank.RoyalFlush, R.string.hand_rank_royal_flush, R.string.hand_rule_royal_flush, "As Ks Qs Js 10s"),
    HandExample(HandRank.StraightFlush, R.string.hand_rank_straight_flush, R.string.hand_rule_straight_flush, "9h 8h 7h 6h 5h"),
    HandExample(HandRank.FourOfAKind, R.string.hand_rank_four_of_a_kind, R.string.hand_rule_four_of_a_kind, "Qc Qd Qh Qs 7c*"),
    HandExample(HandRank.FullHouse, R.string.hand_rank_full_house, R.string.hand_rule_full_house, "Ks Kh Kd 5c 5s"),
    HandExample(HandRank.Flush, R.string.hand_rank_flush, R.string.hand_rule_flush, "Ah Jh 8h 4h 2h"),
    HandExample(HandRank.Straight, R.string.hand_rank_straight, R.string.hand_rule_straight, "10s 9d 8c 7h 6s"),
    HandExample(
        HandRank.ThreeOfAKind, R.string.hand_rank_three_of_a_kind, R.string.hand_rule_three_of_a_kind, "Jc Jd Jh 9s* 4c*"
    ),
    HandExample(HandRank.TwoPair, R.string.hand_rank_two_pair, R.string.hand_rule_two_pair, "As Ah Kc Kd 8s*"),
    HandExample(HandRank.OnePair, R.string.hand_rank_one_pair, R.string.hand_rule_one_pair, "Qc Qd 10s* 7h* 3c*"),
    HandExample(HandRank.HighCard, R.string.hand_rank_high_card, R.string.hand_rule_high_card, "As Jd* 9c* 8h* 5s*"),
)

/** From this width the ten hands sit in two columns of five (tablets, phones on their side). */
private val TwoColumnWidth = 600.dp

/** The hand ranks route: [HandRanksContent] with the Odds four-colour deck setting. */
@Composable
fun HandRanksScreen(onBack: () -> Unit, viewModel: HandRanksViewModel = hiltViewModel()) {
    HandRanksContent(fourColour = viewModel.fourColourDeck, onBack = onBack)
}

/**
 * Hand ranks (S12): the ten hands best to worst, each with an example on small card faces (the
 * cards that make the hand bright, kickers dim), how ties break, and how often it comes up by the
 * river ([SevenCardFrequencies]). Two columns from 600 dp wide.
 */
@Composable
fun HandRanksContent(fourColour: Boolean, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(PokerColors.PokerBlack)) {
        PokerTopBar(
            title = stringResource(R.string.hand_ranks_title),
            subtitle = stringResource(R.string.hand_ranks_subtitle),
            onBack = onBack,
        )
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val twoColumns = maxWidth >= TwoColumnWidth
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = PokerDimens.Gutter, end = PokerDimens.Gutter, top = 4.dp, bottom = PokerDimens.Gutter),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (twoColumns) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HandList(Hands.take(Hands.size / 2), 1, fourColour, Modifier.weight(1f))
                        HandList(Hands.drop(Hands.size / 2), Hands.size / 2 + 1, fourColour, Modifier.weight(1f))
                    }
                } else {
                    HandList(Hands, 1, fourColour, Modifier.fillMaxWidth())
                }
                Text(
                    text = stringResource(R.string.hand_ranks_footer, format("#,##0", SevenCardFrequencies.TOTAL.toDouble())),
                    style = MaterialTheme.typography.bodySmall,
                    color = PokerColors.Chalk,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}

/** A felt card of hands, numbered from [first]. */
@Composable
private fun HandList(hands: List<HandExample>, first: Int, fourColour: Boolean, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(PokerColors.FeltGreen, RoundedCornerShape(PokerDimens.CornerCard))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        hands.forEachIndexed { i, hand ->
            if (i > 0) HorizontalDivider(thickness = 1.dp, color = PokerColors.FeltLine)
            HandRow(first + i, hand, fourColour)
        }
    }
}

@Composable
private fun HandRow(number: Int, hand: HandExample, fourColour: Boolean) {
    val oneIn = stringResource(R.string.hand_ranks_one_in, oneInText(hand.rank))
    // One TalkBack stop per hand: "1, Royal flush, 0.003%, 1 in 30,940, Ace of spades, ..., the rule"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = number.toString(),
                style = PokerType.NumberM.copy(fontSize = 18.sp, lineHeight = 22.sp),
                color = PokerColors.Chalk,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = NumberColumn),
            )
            Text(
                text = stringResource(hand.name),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = PokerColors.CardWhite,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = ColumnGap, end = ColumnGap),
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.hand_ranks_share, handShare(hand.rank)),
                    style = PokerType.NumberM.copy(fontSize = 18.sp, lineHeight = 22.sp),
                    color = PokerColors.CardWhite,
                )
                Text(text = oneIn, style = OneInStyle, color = PokerColors.Chalk)
            }
        }
        Row(
            modifier = Modifier.padding(start = NumberColumn + ColumnGap),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            parseExample(hand.cards).forEach { (card, kicker) ->
                if (kicker) {
                    // Read as "7 of clubs, kicker": the dimming alone says nothing to TalkBack
                    val spoken = stringResource(R.string.hand_ranks_kicker, spokenCard(card))
                    Box(Modifier.clearAndSetSemantics { contentDescription = spoken }) {
                        CardFace(card, size = CardFaceSize.Small, emphasis = CardEmphasis.Dim, fourColour = fourColour)
                    }
                } else {
                    CardFace(card, size = CardFaceSize.Small, fourColour = fourColour)
                }
            }
        }
        Text(
            text = stringResource(hand.rule),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
            color = PokerColors.Chalk,
            modifier = Modifier.padding(start = NumberColumn + ColumnGap),
        )
    }
}

private val NumberColumn = 24.dp
private val ColumnGap = 10.dp
private val OneInStyle = TextStyle(fontSize = 11.5.sp, lineHeight = 15.sp)

/** "As Kd 10h 7c*" as card faces; "*" marks a kicker. */
private fun parseExample(cards: String): List<Pair<PlayingCard, Boolean>> = cards.split(" ").map { token ->
    val kicker = token.endsWith("*")
    val text = token.removeSuffix("*")
    PlayingCard(rank = text.dropLast(1), suit = text.takeLast(1)) to kicker
}

/** "Ace of spades", as the card face itself reads it to TalkBack. */
@Composable
private fun spokenCard(card: PlayingCard): String {
    val rank = when (card.rank) {
        "A" -> stringResource(CoreR.string.card_rank_ace)
        "K" -> stringResource(CoreR.string.card_rank_king)
        "Q" -> stringResource(CoreR.string.card_rank_queen)
        "J" -> stringResource(CoreR.string.card_rank_jack)
        else -> card.rank
    }
    val suit = when (card.suit) {
        "s" -> CoreR.string.card_suit_spades
        "h" -> CoreR.string.card_suit_hearts
        "d" -> CoreR.string.card_suit_diamonds
        else -> CoreR.string.card_suit_clubs
    }
    return stringResource(CoreR.string.card_name, rank, stringResource(suit))
}

private val symbols = DecimalFormatSymbols(Locale.US)

private fun format(pattern: String, value: Double): String =
    DecimalFormat(pattern, symbols).apply { roundingMode = RoundingMode.HALF_UP }.format(value)

/** "43.8", "0.17", "0.003": one decimal from 1%, two from 0.01%, three below. */
internal fun handShare(rank: HandRank): String {
    val percent = SevenCardFrequencies.percent(rank)
    return when {
        percent >= 1.0 -> format("0.0", percent)
        percent >= ONE_HUNDREDTH -> format("0.00", percent)
        else -> format("0.000", percent)
    }
}

/** "30,940", "38", "4.3": whole numbers from 10, one decimal below. */
internal fun oneInText(rank: HandRank): String {
    val n = SevenCardFrequencies.oneIn(rank)
    return if (n >= TEN) format("#,##0", n) else format("0.0", n)
}

private const val ONE_HUNDREDTH = 0.01
private const val TEN = 10.0
