package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons

/**
 * The three card sizes: S 28 x 40 (hand ranks, history), M 44 x 62 (odds seats, board), L 58 x 82
 * (run it out). The rank and pip are drawn at a fixed size, like the card itself, so a card never
 * outgrows its face at large font scales; TalkBack reads the card's name instead.
 */
enum class CardFaceSize(
    internal val size: DpSize,
    internal val corner: Dp,
    internal val rank: Dp,
    internal val rankInset: DpSize,
    internal val pip: Dp,
    internal val pipInset: DpSize,
) {
    Small(PokerDimens.CardFaceSmall, 4.dp, 15.dp, DpSize(4.dp, 3.dp), 13.dp, DpSize(3.dp, 4.dp)),
    Medium(PokerDimens.CardFaceMedium, PokerDimens.CornerCardFace, 22.dp, DpSize(5.dp, 4.dp), 22.dp, DpSize(4.dp, 5.dp)),
    Large(PokerDimens.CardFaceLarge, 7.dp, 30.dp, DpSize(6.dp, 5.dp), 30.dp, DpSize(5.dp, 6.dp)),
}

/** How a card face stands out: dimmed (a kicker that doesn't play) or newly dealt (gold ring). */
enum class CardEmphasis { Normal, Dim, New }

/**
 * A playing card face: the rank top-left in Barlow 700 ("10", never "T") and a vector suit pip
 * bottom-right. Red and black by default; [fourColour] makes diamonds blue and clubs green.
 * TalkBack reads "Ace of spades".
 */
@Composable
fun CardFace(
    card: PlayingCard,
    modifier: Modifier = Modifier,
    size: CardFaceSize = CardFaceSize.Medium,
    emphasis: CardEmphasis = CardEmphasis.Normal,
    fourColour: Boolean = false,
) {
    val suit = CardSuit.of(card.suit)
    val rank = displayRank(card.rank)
    val name = stringResource(R.string.card_name, spokenRank(rank), stringResource(suit.spokenName))
    val shape = RoundedCornerShape(size.corner)
    val ink = suit.ink(fourColour)
    Box(
        modifier = modifier
            .size(size.size)
            .then(if (emphasis == CardEmphasis.Dim) Modifier.alpha(DIM_ALPHA) else Modifier)
            .then(if (emphasis == CardEmphasis.New) Modifier.drawBehind { drawNewCardGlow(size.corner.toPx()) } else Modifier)
            .shadow(1.dp, shape)
            .background(PokerColors.CardWhite, shape)
            .clearAndSetSemantics { contentDescription = name },
    ) {
        Text(
            text = rank,
            color = ink,
            style = PokerType.CardRank.copy(fontSize = size.rank.fixedSp(), lineHeight = size.rank.fixedSp()),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = size.rankInset.width, top = size.rankInset.height),
        )
        Icon(
            imageVector = suit.icon,
            contentDescription = null,
            tint = ink,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = size.pipInset.width, bottom = size.pipInset.height)
                .size(size.pip),
        )
    }
}

private fun DrawScope.drawNewCardGlow(corner: Float) {
    val ring = 2.dp.toPx()
    val glow = 6.dp.toPx()
    drawRoundRect(
        color = PokerColors.PokerGold.copy(alpha = GLOW_ALPHA),
        topLeft = Offset(-glow, -glow),
        size = Size(size.width + glow * 2, size.height + glow * 2),
        cornerRadius = CornerRadius(corner + glow),
    )
    drawRoundRect(
        color = PokerColors.PokerGold,
        topLeft = Offset(-ring, -ring),
        size = Size(size.width + ring * 2, size.height + ring * 2),
        cornerRadius = CornerRadius(corner + ring),
    )
}

/** A size given in dp, as a font size that ignores the font scale (the card is a fixed graphic). */
@Composable
internal fun Dp.fixedSp(): TextUnit = with(LocalDensity.current) { this@fixedSp.toSp() }

/** "T" and "10" both show as "10"; everything else upper-cased ("a" -> "A"). */
internal fun displayRank(rank: String): String = when (val r = rank.trim().uppercase()) {
    "T" -> "10"
    else -> r
}

@Composable
private fun spokenRank(rank: String): String = when (rank) {
    "A" -> stringResource(R.string.card_rank_ace)
    "K" -> stringResource(R.string.card_rank_king)
    "Q" -> stringResource(R.string.card_rank_queen)
    "J" -> stringResource(R.string.card_rank_jack)
    else -> rank
}

/** The four suits, from either a letter ("s") or a symbol ("♠"). */
internal enum class CardSuit(val icon: ImageVector, val spokenName: Int, private val ink: Color, private val ink4: Color) {
    Spades(PokerIcons.Spade, R.string.card_suit_spades, PokerColors.SuitBlack, PokerColors.SuitBlack),
    Hearts(PokerIcons.Heart, R.string.card_suit_hearts, PokerColors.SuitRed, PokerColors.SuitRed),
    Diamonds(PokerIcons.Diamond, R.string.card_suit_diamonds, PokerColors.SuitRed, PokerColors.SuitDiamond4),
    Clubs(PokerIcons.Club, R.string.card_suit_clubs, PokerColors.SuitBlack, PokerColors.SuitClub4),
    ;

    fun ink(fourColour: Boolean): Color = if (fourColour) ink4 else ink

    companion object {
        fun of(suit: String): CardSuit = when (suit.trim().lowercase()) {
            "s", "♠" -> Spades
            "h", "♥" -> Hearts
            "d", "♦" -> Diamonds
            "c", "♣" -> Clubs
            else -> throw IllegalArgumentException("Unknown suit \"$suit\"")
        }
    }
}

private const val DIM_ALPHA = 0.42f
private const val GLOW_ALPHA = 0.3f

@OptIn(ExperimentalLayoutApi::class)
@Preview(name = "CardFace", widthDp = 360, showBackground = true, backgroundColor = 0xFF0B0B0B)
@Composable
internal fun CardFacePreview() {
    PokerPreviewPage {
        PokerStage(felt = true) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CardFace(PlayingCard("A", "s"), size = CardFaceSize.Large)
                CardFace(PlayingCard("Q", "h"), size = CardFaceSize.Large)
                CardFace(PlayingCard("T", "s"), emphasis = CardEmphasis.New)
                CardFace(PlayingCard("K", "d"), fourColour = true)
                CardFace(PlayingCard("2", "c"), fourColour = true)
                CardSlot(CardSlotState.Next)
                CardSlot(CardSlotState.Empty)
                CardSlot(CardSlotState.Random)
                CardFace(PlayingCard("J", "c"), size = CardFaceSize.Small)
                CardFace(PlayingCard("7", "d"), size = CardFaceSize.Small, emphasis = CardEmphasis.Dim)
                CardFace(PlayingCard("10", "h"), size = CardFaceSize.Small)
            }
        }
    }
}
