package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.poker.Cards
import com.huntercoles.pokerpayout.tools.presentation.OddsCalculatorUiState
import com.huntercoles.pokerpayout.tools.presentation.OddsCalculatorIntent
import com.huntercoles.pokerpayout.tools.presentation.OddsTable
import com.huntercoles.pokerpayout.tools.presentation.SlotRef
import java.util.Locale
import com.huntercoles.pokerpayout.core.R as CoreR

/**
 * The docked rank-then-suit keypad (S8). Pick a rank (it turns gold), then a suit: the card goes in
 * the slot the keypad is filling, and the keypad moves to the next empty slot. A suit already on
 * the table for that rank is greyed and struck; a rank with all four suits out is struck too.
 * Every key is at least 48 dp each way (suits 56 dp tall at the bottom, 48 dp at the side and when
 * they share the ranks' rows).
 */
@Composable
internal fun CardKeypad(
    state: OddsCalculatorUiState,
    onIntent: (OddsCalculatorIntent) -> Unit,
    modifier: Modifier = Modifier,
    dock: KeypadDock = KeypadDock.Bottom,
) {
    val target = state.keypad.target ?: return
    Column(
        modifier = modifier
            .clip(dock.shape)
            .background(PokerColors.FeltDeep)
            .drawBehind { drawLine(PokerColors.DarkGold, Offset.Zero, Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
            .padding(start = 8.dp - KEY_INSET, end = 8.dp - KEY_INSET, top = 6.dp, bottom = 10.dp),
    ) {
        KeypadHeader(target, onIntent)
        KeyRows(state, target, onIntent, dock)
    }
}

/**
 * Where the keypad docks: along the bottom (portrait), along the bottom of a short window, or down
 * the side of a short landscape window. [suitsShareRows]: on a phone too narrow for seven keys a row,
 * the four suits fill the ranks' last row (six keys a row), so the keys take three rows, not four.
 */
internal enum class KeypadDock(val shape: Shape, val suitHeight: Dp, val suitsShareRows: Boolean = false) {
    Bottom(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), PokerDimens.SuitKeyHeight),
    BottomShort(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), PokerDimens.SuitKeyHeight, suitsShareRows = true),
    Side(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp), PokerDimens.KeypadKeyHeight),
}

/** One key: a rank, delete, or a suit sharing the ranks' rows. */
private sealed interface PadKey {
    data class Rank(val rank: Int) : PadKey

    data object Delete : PadKey

    data class Suit(val suit: Int) : PadKey
}

/**
 * The thirteen ranks and delete, seven keys a row when each gets 48 dp, else five (three rows), so no
 * key is narrower than 48 dp; then the four suits in a row of their own. With [KeypadDock.suitsShareRows]
 * and five a row, the suits fill the ranks' last row instead: A K Q J 10 9 / 8 7 6 5 4 3 / 2 ⌫ ♠ ♥ ♦ ♣.
 */
@Composable
private fun KeyRows(state: OddsCalculatorUiState, target: SlotRef, onIntent: (OddsCalculatorIntent) -> Unit, dock: KeypadDock) {
    val table = state.table
    val picked = state.keypad.rank
    val fourColour = state.fourColourDeck
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val ranksPerRow = COLUMN_CHOICES.firstOrNull { maxWidth / it >= PokerDimens.MinTouch } ?: COLUMN_CHOICES.last()
        val sixFit = maxWidth / SHARED_COLUMNS >= PokerDimens.MinTouch
        val shared = dock.suitsShareRows && ranksPerRow < COLUMN_CHOICES.first() && sixFit
        val columns = if (shared) SHARED_COLUMNS else ranksPerRow
        // Sharing a row with ranks, a suit is as tall as a rank key.
        val suitHeight = if (shared) PokerDimens.KeypadKeyHeight else dock.suitHeight
        val suits = SUIT_ORDER.map { PadKey.Suit(it) }
        val keys = if (shared) RANK_KEYS + suits else RANK_KEYS
        Column {
            keys.chunked(columns).forEach { row ->
                Row {
                    row.forEach { key ->
                        when (key) {
                            is PadKey.Rank -> RankKey(key.rank, table, target, picked == key.rank, onIntent)
                            PadKey.Delete -> BackspaceKey(onIntent)
                            is PadKey.Suit -> SuitKey(key.suit, picked, table, target, fourColour, onIntent, suitHeight)
                        }
                    }
                    if (row.size < columns) Spacer(Modifier.weight((columns - row.size).toFloat()))
                }
            }
            if (!shared) {
                Row { suits.forEach { SuitKey(it.suit, picked, table, target, fourColour, onIntent, suitHeight) } }
            }
        }
    }
}

/**
 * "Player 2 · card 2" (the slot being filled, its card number in gold), then Random and Done. At
 * large font sizes the buttons move under the label rather than squeeze it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeypadHeader(target: SlotRef, onIntent: (OddsCalculatorIntent) -> Unit) {
    val labels = rememberOddsLabels()
    val (what, which) = when (target) {
        is SlotRef.Hole -> labels.player(target.seat) to stringResource(R.string.odds_keypad_card, target.index + 1)
        is SlotRef.Board -> when (target.index) {
            in 0..2 -> stringResource(R.string.odds_flop) to stringResource(R.string.odds_keypad_card, target.index + 1)
            OddsTable.BOARD_SLOTS - 2 -> stringResource(R.string.odds_turn) to null
            else -> stringResource(R.string.odds_river) to null
        }
    }
    val separator = stringResource(R.string.odds_separator)
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = buildAnnotatedString {
                append(what)
                if (which != null) {
                    append(separator)
                    withStyle(SpanStyle(color = PokerColors.PokerGold)) { append(which) }
                }
            },
            style = MaterialTheme.typography.titleSmall,
            color = PokerColors.CardWhite,
            modifier = Modifier.weight(1f).heightIn(min = PokerDimens.MinTouch).wrapContentHeight(Alignment.CenterVertically),
        )
        // The two buttons wrap together, under the label, when the label needs the room.
        Row(Modifier.align(Alignment.CenterVertically), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (target is SlotRef.Hole) {
                PokerButton(
                    text = stringResource(R.string.odds_keypad_random),
                    onClick = { onIntent(OddsCalculatorIntent.RandomHand) },
                    variant = PokerButtonVariant.Text,
                    size = PokerButtonSize.Small,
                    icon = PokerIcons.Dice,
                )
            }
            PokerButton(
                text = stringResource(R.string.odds_keypad_done),
                onClick = { onIntent(OddsCalculatorIntent.CloseKeypad) },
                variant = PokerButtonVariant.Text,
                size = PokerButtonSize.Small,
            )
        }
    }
}

/** One rank key: gold once picked, struck when all four of its cards are on the table. */
@Composable
private fun RowScope.RankKey(
    rank: Int,
    table: OddsTable,
    target: SlotRef,
    picked: Boolean,
    onIntent: (OddsCalculatorIntent) -> Unit,
) {
    val free = (0 until Cards.SUITS).any { table.canPlace(target, Cards.of(rank, it)) }
    val spoken = rememberOddsLabels().rankName(rank)
    val description = if (free) spoken else stringResource(R.string.odds_keypad_rank_used, spoken)
    Key(
        background = if (picked) PokerColors.PokerGold else PokerColors.DarkGreen,
        enabled = free,
        description = description,
        onClick = { onIntent(OddsCalculatorIntent.PickRank(rank)) },
        modifier = Modifier.semantics { selected = picked },
    ) {
        Text(
            text = rankText(rank),
            style = PokerType.NumberM.copy(fontSize = RANK_GLYPH.fixedSp(), lineHeight = RANK_GLYPH.fixedSp()),
            color = when {
                picked -> PokerColors.FeltDeep
                free -> PokerColors.CardWhite
                else -> PokerColors.ChalkDim
            },
            textDecoration = if (free) null else TextDecoration.LineThrough,
        )
    }
}

@Composable
private fun RowScope.BackspaceKey(onIntent: (OddsCalculatorIntent) -> Unit) {
    Key(
        background = PokerColors.DarkGreen,
        enabled = true,
        description = stringResource(R.string.odds_keypad_backspace),
        onClick = { onIntent(OddsCalculatorIntent.Backspace) },
    ) {
        Icon(PokerIcons.Backspace, contentDescription = null, tint = PokerColors.Chalk, modifier = Modifier.size(22.dp))
    }
}

/** One suit key: a white key with the pip, greyed and struck through when that card is on the table. */
@Suppress("LongParameterList") // one key needs the pick so far, the table and the deck colours
@Composable
private fun RowScope.SuitKey(
    suit: Int,
    rank: Int?,
    table: OddsTable,
    target: SlotRef,
    fourColour: Boolean,
    onIntent: (OddsCalculatorIntent) -> Unit,
    height: Dp,
) {
    val used = rank != null && !table.canPlace(target, Cards.of(rank, suit))
    val name = stringResource(SUIT_NAMES[suit]).replaceFirstChar { it.titlecase(Locale.US) }
    val description = when {
        rank == null -> stringResource(R.string.odds_keypad_suit_wait, name)
        used -> stringResource(R.string.odds_keypad_suit_used, name)
        else -> name
    }
    val ink = if (used) PokerColors.ChalkDim else suitInk(suit, fourColour)
    Key(
        background = if (used) PokerColors.DarkGreen else PokerColors.CardWhite,
        enabled = rank != null && !used,
        description = description,
        onClick = { onIntent(OddsCalculatorIntent.PickSuit(suit)) },
        height = height,
        corner = PokerDimens.CornerControl,
        tile = Modifier.drawWithContent {
            drawContent()
            if (used) {
                val w = size.width * STRIKE_SPAN
                drawLine(
                    PokerColors.ChalkDim,
                    Offset((size.width - w) / 2, size.height * (1 + STRIKE_RISE) / 2),
                    Offset((size.width + w) / 2, size.height * (1 - STRIKE_RISE) / 2),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        },
    ) {
        // Before a rank is picked the suits wait, a little dimmed.
        val tint = if (rank == null) ink.copy(alpha = WAITING_ALPHA) else ink
        Icon(SUIT_ICONS[suit], contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
    }
}

/**
 * A keypad key: a rounded tile ([height] tall) inset 2 dp in its share of the row. The whole cell,
 * margins included, takes the tap, so neighbouring keys' 48 dp targets touch but never overlap.
 */
@Suppress("LongParameterList") // a key's look: colour, size, state and label
@Composable
private fun RowScope.Key(
    background: Color,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = PokerDimens.KeypadKeyHeight,
    corner: Dp = 10.dp,
    tile: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .weight(1f)
            .height(height + KEY_INSET * 2)
            .clickable(interactions, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(KEY_INSET)
            .clip(RoundedCornerShape(corner))
            .background(background)
            .indication(interactions, ripple())
            .then(tile),
        contentAlignment = Alignment.Center,
    ) {
        // The key reads its description ("Ace"); the glyph inside ("A") isn't read again.
        Box(Modifier.clearAndSetSemantics { }) { content() }
    }
}

/** A suit's ink on a white key: red and black, or blue diamonds and green clubs in the four-colour deck. */
private fun suitInk(suit: Int, fourColour: Boolean): Color = when (suit) {
    DIAMONDS -> if (fourColour) PokerColors.SuitDiamond4 else PokerColors.SuitRed
    CLUBS -> if (fourColour) PokerColors.SuitClub4 else PokerColors.SuitBlack
    HEARTS -> PokerColors.SuitRed
    else -> PokerColors.SuitBlack
}

/** Half the 4 dp gap between keys: each key's tile sits this far inside its touch target. */
private val KEY_INSET = 2.dp
private val RANK_GLYPH = 24.dp

/** Keys a row: 7 (two rows), else 5 (three rows), else 4. Six would leave a row of two. */
private val COLUMN_CHOICES = listOf(7, 5, 4)

/** Keys a row when the suits share the ranks' rows: 13 ranks, delete and 4 suits make three rows of six. */
private const val SHARED_COLUMNS = 6

/** Ace down to deuce, then delete. */
private val RANK_KEYS: List<PadKey> = (Cards.ACE downTo Cards.DEUCE).map { PadKey.Rank(it) } + PadKey.Delete

private const val CLUBS = 0
private const val DIAMONDS = 1
private const val HEARTS = 2
private const val SPADES = 3
private const val WAITING_ALPHA = 0.55f
private const val STRIKE_SPAN = 0.4f
private const val STRIKE_RISE = 0.35f

/** Spades, hearts, diamonds, clubs: the order the keypad shows them. */
private val SUIT_ORDER = listOf(SPADES, HEARTS, DIAMONDS, CLUBS)
private val SUIT_ICONS = listOf(PokerIcons.Club, PokerIcons.Diamond, PokerIcons.Heart, PokerIcons.Spade)
private val SUIT_NAMES = listOf(
    CoreR.string.card_suit_clubs,
    CoreR.string.card_suit_diamonds,
    CoreR.string.card_suit_hearts,
    CoreR.string.card_suit_spades,
)
