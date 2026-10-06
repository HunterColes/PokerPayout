package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.CardEmphasis
import com.huntercoles.pokerpayout.core.design.components.CardFace
import com.huntercoles.pokerpayout.core.design.components.CardFaceSize
import com.huntercoles.pokerpayout.core.design.components.CardSlot
import com.huntercoles.pokerpayout.core.design.components.CardSlotState
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerPill
import com.huntercoles.pokerpayout.core.design.components.PokerPillTone
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.OddsCalculatorIntent
import com.huntercoles.pokerpayout.tools.presentation.OddsTable
import com.huntercoles.pokerpayout.tools.presentation.SlotRef
import com.huntercoles.pokerpayout.tools.presentation.SlotValue
import java.util.Locale

/** Gap between neighbouring 48 dp card targets: the 44 dp faces end up 6 dp apart, like the mockup. */
internal val CardTargetGap = 2.dp

/** Width of one card target: the 44 dp face plus a 2 dp margin each side makes the 48 dp touch target. */
internal val CardTargetWidth = PokerDimens.MinTouch

/** The board's slots by street: the flop's three, the turn, the river. */
internal val BOARD_GROUPS = listOf(0..2, 3..3, 4..4)

/**
 * A card place you can tap: the face (or the empty, next or random slot) centred in a box at least
 * 48 dp wide, which is the touch target. TalkBack reads [description] ("Player 2, card 2, empty.
 * Next.") instead of the face's own name.
 */
@Composable
internal fun CardTarget(
    value: SlotValue,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: CardTargetStyle = CardTargetStyle(),
) {
    Box(
        modifier = modifier
            .sizeIn(minWidth = CardTargetWidth, minHeight = PokerDimens.MinTouch)
            .clip(RoundedCornerShape(PokerDimens.CornerCardFace))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.clearAndSetSemantics { }) { CardOrSlot(value, style) }
    }
}

/** How a [CardTarget] draws: the keypad's slot pulses gold, a folded hand's cards are dimmed. */
internal data class CardTargetStyle(
    val isTarget: Boolean = false,
    val fourColour: Boolean = false,
    val dim: Boolean = false,
    val size: CardFaceSize = CardFaceSize.Medium,
)

/** A card face, or the slot standing in for a card that isn't there. Display only. */
@Composable
internal fun CardOrSlot(value: SlotValue, style: CardTargetStyle = CardTargetStyle()) {
    when (value) {
        is SlotValue.Known -> CardFace(
            card = value.card.toPlayingCard(),
            size = style.size,
            fourColour = style.fourColour,
            emphasis = if (style.dim) CardEmphasis.Dim else CardEmphasis.Normal,
        )
        SlotValue.Random -> CardSlot(if (style.isTarget) CardSlotState.Next else CardSlotState.Random, size = style.size)
        SlotValue.Empty -> CardSlot(if (style.isTarget) CardSlotState.Next else CardSlotState.Empty, size = style.size)
    }
}

/**
 * The board (S8, S9): five card targets in flop, turn and river groups, each labelled underneath,
 * with the street as a pill. Tapping a slot aims the keypad at it.
 */
@Composable
internal fun BoardStrip(
    table: OddsTable,
    target: SlotRef?,
    fourColour: Boolean,
    onIntent: (OddsCalculatorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = rememberOddsLabels()
    val streetNames = listOf(R.string.odds_flop, R.string.odds_turn, R.string.odds_river).map { stringResource(it) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PokerColors.FeltGreen, RoundedCornerShape(PokerDimens.CornerCard))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PokerEyebrow(stringResource(R.string.odds_board))
            PokerPill(streetName(table.boardCards.size), tone = PokerPillTone.Muted)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            BOARD_GROUPS.forEachIndexed { group, slots ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(CardTargetGap)) {
                        slots.forEach { i ->
                            val ref = SlotRef.Board(i)
                            val value = table.board[i]
                            val name = if (slots.first == slots.last) {
                                streetNames[group]
                            } else {
                                stringResource(R.string.odds_slot_board_card, streetNames[group], i - slots.first + 1)
                            }
                            CardTarget(
                                value = value,
                                description = slotDescription(name, value, ref == target, labels),
                                onClick = { onIntent(OddsCalculatorIntent.SelectSlot(ref)) },
                                style = CardTargetStyle(isTarget = ref == target, fourColour = fourColour),
                            )
                        }
                    }
                    // Captions under the slots grow with the font only up to 1.3x, so "TURN" and "RIVER"
                    // stay within their 48 dp slots; TalkBack reads each slot's full name anyway.
                    Text(
                        text = streetNames[group].uppercase(Locale.ROOT),
                        style = PokerType.Eyebrow.copy(fontSize = CAPTION.cappedSp(CAPTION_MAX_SCALE), letterSpacing = 1.2.sp),
                        color = PokerColors.Chalk,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.wrapContentWidth(unbounded = true),
                    )
                }
            }
        }
    }
}

/** "Flop card 1, empty. Next." / "Player 1, card 2, Ace of spades". */
@Composable
internal fun slotDescription(name: String, value: SlotValue, isTarget: Boolean, labels: OddsLabels): String {
    val what = when (value) {
        is SlotValue.Known -> labels.cardName(value.card)
        SlotValue.Random -> stringResource(R.string.odds_slot_random)
        SlotValue.Empty -> stringResource(if (isTarget) R.string.odds_slot_next else R.string.odds_slot_empty)
    }
    val described = stringResource(R.string.odds_slot_board, name, what)
    return if (isTarget && value != SlotValue.Empty) stringResource(R.string.odds_slot_picking, described) else described
}

/** The street a board of [cards] cards is on, by name. A part-dealt flop is still "Flop". */
@Composable
internal fun streetName(cards: Int): String = stringResource(
    when (cards) {
        0 -> R.string.odds_preflop
        in 1..OddsTable.BOARD_SLOTS - 2 -> R.string.odds_flop
        OddsTable.BOARD_SLOTS - 1 -> R.string.odds_turn
        else -> R.string.odds_river
    },
)

/**
 * An equity figure: "56.1%" in Barlow with a smaller Chalk "%", "≈67.1%" while estimating, or an
 * em dash when there is no number yet.
 */
@Composable
internal fun EquityText(equityPct: Double?, estimate: Boolean, style: TextStyle, modifier: Modifier = Modifier) {
    val number = equityPct?.let { OddsFormat.oneDecimal(it) }
    val text = buildAnnotatedString {
        if (number == null) {
            withStyle(SpanStyle(color = PokerColors.ChalkDim)) { append("—") }
        } else {
            append(if (estimate) stringResource(R.string.odds_about, number) else number)
            withStyle(SpanStyle(color = PokerColors.Chalk, fontSize = style.fontSize * PERCENT_SCALE)) { append("%") }
        }
    }
    Text(text = text, style = style, color = PokerColors.CardWhite, maxLines = 1, softWrap = false, modifier = modifier)
}

/** The seat equity number: 32 sp on the odds screen, 40 sp in run it out. */
internal fun equityStyle(size: TextUnit = 32.sp): TextStyle =
    PokerType.DisplayM.copy(fontSize = size, lineHeight = size * LINE_RATIO)

/** A size in dp as a font size that ignores the system font scale (for glyphs drawn like graphics). */
@Composable
internal fun Dp.fixedSp(): TextUnit = with(LocalDensity.current) { this@fixedSp.toSp() }

/** A font size that follows the system font scale only up to [maxScale] (captions under fixed-size cards). */
@Composable
internal fun Dp.cappedSp(maxScale: Float): TextUnit {
    val density = LocalDensity.current
    return with(density) { (this@cappedSp * minOf(density.fontScale, maxScale)).toSp() }
}

private val CAPTION = 11.dp
private const val CAPTION_MAX_SCALE = 1.3f
private const val PERCENT_SCALE = 0.53f
private const val LINE_RATIO = 1.1f
