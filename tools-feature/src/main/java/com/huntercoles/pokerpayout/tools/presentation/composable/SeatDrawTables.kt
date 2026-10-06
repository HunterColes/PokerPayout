package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.LocalReducedMotion
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.CardEmphasis
import com.huntercoles.pokerpayout.core.design.components.CardFace
import com.huntercoles.pokerpayout.core.design.components.CardFaceSize
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerPill
import com.huntercoles.pokerpayout.core.design.components.PokerPillTone
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.SeatDrawText
import com.huntercoles.pokerpayout.tools.presentation.SeatRole
import com.huntercoles.pokerpayout.tools.presentation.roleOf
import com.huntercoles.pokerpayout.tools.seats.DrawnTable
import com.huntercoles.pokerpayout.tools.seats.SeatDraw

/**
 * The draw as table cards, [columns] to a row (two from 600 dp). After a deal for the button the
 * cards land one seat at a time at every table at once, then the button and the blinds show; under
 * Reduce motion (and in screenshots) the deal is already done.
 */
@Composable
internal fun TablesGrid(draw: SeatDraw, columns: Int, dealToAnimate: Int, text: SeatDrawText) {
    val dealtMs = rememberDealClock(dealToAnimate, seats = draw.tables.maxOf { it.seats.size })
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        draw.tables.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                row.forEach { table -> TableCard(table, dealtMs, text, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Milliseconds into the deal numbered [dealToAnimate]; the whole deal's length when there is
 * nothing to play (no fresh deal, already played, or Reduce motion). A deal plays once: rotating
 * the screen doesn't play it again.
 */
@Composable
private fun rememberDealClock(dealToAnimate: Int, seats: Int): Float {
    val reduced = LocalReducedMotion.current
    var played by rememberSaveable { mutableIntStateOf(0) }
    val total = dealMillis(seats).toFloat()
    val clock = remember(dealToAnimate) {
        Animatable(if (dealToAnimate == 0 || dealToAnimate == played || reduced) total else 0f)
    }
    LaunchedEffect(dealToAnimate) {
        if (clock.value < total) clock.animateTo(total, tween(total.toInt(), easing = LinearEasing))
        played = dealToAnimate
    }
    return clock.value
}

/** Each seat's card starts [DEAL_STAGGER_MS] after the last and flips in over [DEAL_FLIP_MS]. */
private fun dealMillis(seats: Int): Int = (seats - 1).coerceAtLeast(0) * DEAL_STAGGER_MS + DEAL_FLIP_MS

/** How far seat [index]'s card has flipped in, 0 to 1, [dealtMs] into the deal. */
private fun flipOf(index: Int, dealtMs: Float): Float = ((dealtMs - index * DEAL_STAGGER_MS) / DEAL_FLIP_MS).coerceIn(0f, 1f)

/**
 * One table: its number and size, then (once dealt) who has the button and why, then a row per
 * seat with the seat number, the name, the card it drew and its pills (Button, Small blind, Big
 * blind). The button's row is washed gold.
 */
@Composable
private fun TableCard(table: DrawnTable, dealtMs: Float, text: SeatDrawText, modifier: Modifier = Modifier) {
    val result = table.button?.takeIf { dealtMs >= dealMillis(table.seats.size) }
    Column(
        modifier = modifier
            .background(PokerColors.FeltGreen, RoundedCornerShape(PokerDimens.CornerCard))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TableHeader(table, text)
        if (result != null) {
            Text(
                text = text.buttonLine(table, result),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = PokerColors.PokerGold,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            text.tieLine(result)?.let { tie ->
                Text(text = tie, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
            }
        }
        val labels = rememberOddsLabels()
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            table.seats.forEachIndexed { index, name ->
                val seat = index + 1
                val card = table.buttonCards?.get(index)
                val flip = flipOf(index, dealtMs)
                val role = result?.roleOf(seat)
                val cardName = card?.takeIf { flip >= 1f }?.let(labels::cardName)
                SeatRow(
                    SeatLine(
                        seat = seat,
                        name = name,
                        card = card,
                        flip = flip,
                        role = role,
                        pills = text.rolePills(role),
                        description = text.seatDescription(table.number, seat, name, cardName, role),
                    ),
                )
            }
        }
    }
}

/** What one seat's row shows, and how TalkBack reads it (one stop per seat). */
private data class SeatLine(
    val seat: Int,
    val name: String,
    val card: Int?,
    val flip: Float,
    val role: SeatRole?,
    val pills: List<String>,
    val description: String,
) {
    val button: Boolean get() = role == SeatRole.Button || role == SeatRole.ButtonAndSmallBlind
}

/** "TABLE 1" in gold, and how many sit there; a heading for TalkBack. */
@Composable
private fun TableHeader(table: DrawnTable, text: SeatDrawText) {
    val players = text.players(table.seats.size)
    val title = stringResource(R.string.seat_draw_table, table.number)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PokerEyebrow(text = title, color = PokerColors.PokerGold, modifier = Modifier.weight(1f))
        Text(text = players, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
    }
}

/** A seat: number, name and pills (wrapping under the name if they must), and the card it drew. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeatRow(line: SeatLine) {
    val shape = RoundedCornerShape(PokerDimens.CornerControl)
    val wash = if (line.button) {
        Modifier
            .background(PokerColors.GoldWash, shape)
            .border(1.dp, PokerColors.PokerGold, shape)
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SeatRowHeight)
            .then(wash)
            // Room at the end for the button card's gold ring inside the row's gold edge
            .padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)
            .clearAndSetSemantics { contentDescription = line.description },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeatNumber(line.seat, gold = line.button)
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = line.name,
                style = MaterialTheme.typography.bodyLarge,
                color = PokerColors.CardWhite,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            line.pills.forEachIndexed { index, pill ->
                PokerPill(
                    text = pill,
                    tone = if (line.button && index == 0) PokerPillTone.Gold else PokerPillTone.Outline,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
        if (line.card != null) DealtCard(line.card, line.flip, highlight = line.button)
    }
}

/** The card a seat drew, flipping in as it's dealt; the button's card gets a gold ring. */
@Composable
private fun DealtCard(card: Int, flip: Float, highlight: Boolean) {
    Box(Modifier.size(PokerDimens.CardFaceSmall)) {
        if (flip > 0f) {
            CardFace(
                card = card.toPlayingCard(),
                size = CardFaceSize.Small,
                emphasis = if (highlight) CardEmphasis.New else CardEmphasis.Normal,
                modifier = Modifier.graphicsLayer {
                    scaleX = flip
                    alpha = flip
                },
            )
        }
    }
}

/** The seat number in a disc: gold for the button. */
@Composable
private fun SeatNumber(seat: Int, gold: Boolean) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = SeatDisc, minHeight = SeatDisc)
            .background(if (gold) PokerColors.PokerGold else PokerColors.FeltDeep, CircleShape)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = seat.toString(),
            style = PokerType.NumberM.copy(fontSize = 17.sp, lineHeight = 20.sp),
            color = if (gold) PokerColors.FeltDeep else PokerColors.CardWhite,
        )
    }
}

private const val DEAL_STAGGER_MS = 110
private const val DEAL_FLIP_MS = 220
private val SeatRowHeight = 40.dp
private val SeatDisc = 32.dp
