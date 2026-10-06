package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.Stat
import com.huntercoles.pokerpayout.core.design.components.StatStrip
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSegment
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import java.text.NumberFormat

internal val CardShape = RoundedCornerShape(PokerDimens.CornerCard)

/**
 * The current blinds, big and white, with the BB ante in gold beside them; then NEXT, labelled and
 * dimmer, so the two can't be confused. [wide] lays all three out in one row (tablets, Z4).
 */
@Composable
internal fun BlindsCard(uiState: TimerUiState, modifier: Modifier = Modifier, wide: Boolean = false) {
    val formatter = rememberChipFormatter()
    val level = uiState.currentLevelSegment?.level ?: return
    val blinds = blindsText(level, formatter)
    val ante = level.ante.takeIf { it > 0 }?.let { formatter.format(it) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(PokerColors.FeltGreen)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (wide) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                CurrentBlinds(blinds, Modifier.weight(BLINDS_WEIGHT), cap = WideBlinds)
                ante?.let { AnteColumn(it, Modifier.weight(ANTE_WEIGHT), PokerType.DisplayM.fontSize) }
                NextBlinds(uiState, Modifier.weight(NEXT_WEIGHT), alignEnd = true)
            }
        } else {
            WithWidth { width ->
                // Blinds and ante share one fitted size, the ante a little smaller, so neither crowds out the other.
                val together = listOfNotNull(blinds, ante).joinToString("  ")
                val size = rememberFittedSize(together, PokerType.DisplayL, width, PhoneBlinds)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PokerEyebrow(stringResource(R.string.clock_blinds))
                        WithWidth { column ->
                            val shared = with(LocalDensity.current) { size.toDp() }
                            val fitted = rememberFittedSize(blinds, PokerType.DisplayL, column, shared)
                            Text(
                                text = blinds,
                                style = PokerType.DisplayL.copy(fontSize = fitted, lineHeight = fitted),
                                color = PokerColors.CardWhite,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                    ante?.let { AnteColumn(it, Modifier, (size * ANTE_TO_BLINDS)) }
                }
            }
            HorizontalDivider(color = PokerColors.FeltLine)
            NextBlinds(uiState, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CurrentBlinds(blinds: String, modifier: Modifier, cap: Dp) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PokerEyebrow(stringResource(R.string.clock_blinds))
        WithWidth { width -> FittedNumber(blinds, width, cap, PokerColors.CardWhite) }
    }
}

@Composable
private fun AnteColumn(ante: String, modifier: Modifier, size: TextUnit) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PokerEyebrow(stringResource(R.string.clock_bb_ante))
        Text(
            text = ante,
            style = PokerType.DisplayL.copy(fontSize = size, lineHeight = size),
            color = PokerColors.PokerGold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** "NEXT · LEVEL 7  400 / 800 ante 800", "NEXT · BREAK  10 min", or "Final level". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NextBlinds(uiState: TimerUiState, modifier: Modifier = Modifier, alignEnd: Boolean = false) {
    val formatter = rememberChipFormatter()
    val upcomingBreak = uiState.timeline.segments.getOrNull(uiState.currentSegmentIndex + 1) as? BreakSegment
    val next = uiState.nextLevelSegment
    val align = if (alignEnd) Alignment.End else Alignment.Start
    Column(modifier, horizontalAlignment = align, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            uiState.isFinished || (upcomingBreak == null && next == null) ->
                PokerEyebrow(stringResource(R.string.clock_next_none))
            upcomingBreak != null -> {
                PokerEyebrow(stringResource(R.string.clock_next_break))
                Text(
                    text = stringResource(R.string.clock_break_minutes, upcomingBreak.durationSeconds / SECONDS_PER_MINUTE),
                    style = PokerType.NumberL,
                    color = PokerColors.Chalk,
                )
                breakDetails(upcomingBreak, formatter)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
                }
            }
            next != null -> {
                val label = if (next.isOvertime) R.string.clock_next_overtime else R.string.clock_next_level
                PokerEyebrow(stringResource(label, next.level.level))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, if (alignEnd) Alignment.End else Alignment.Start)) {
                    Text(
                        text = blindsText(next.level, formatter),
                        style = PokerType.NumberL,
                        color = PokerColors.Chalk,
                        modifier = Modifier.alignByBaseline(),
                    )
                    if (next.level.ante > 0) {
                        Text(
                            text = stringResource(R.string.clock_ante, formatter.format(next.level.ante)),
                            style = PokerType.NumberS.copy(fontSize = PokerType.NumberM.fontSize),
                            color = PokerColors.Chalk,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                }
            }
        }
    }
}

/** "Color up the 100s · last orders": what a break holds. */
@Composable
internal fun breakDetails(segment: BreakSegment, formatter: NumberFormat): String? {
    val parts = listOfNotNull(
        segment.colorUp.takeIf { it.isNotEmpty() }?.let { colorUpText(it, formatter) },
        segment.message.takeIf { it.isNotBlank() },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(stringResource(R.string.strip_separator))
}

/** Players left, average stack (and in big blinds), prize pool (and places paid). */
@Composable
internal fun ClockStats(uiState: TimerUiState, paidPlaces: Int, modifier: Modifier = Modifier, showSubs: Boolean = true) {
    val formatter = rememberChipFormatter()
    val table = uiState.table
    val bigBlind = (uiState.currentLevelSegment ?: uiState.nextLevelSegment)?.level?.bigBlind ?: 0
    val avg = table.averageStack
    StatStrip(
        stats = listOf(
            Stat(
                label = stringResource(R.string.clock_stat_players),
                value = "${table.playersLeft}",
                sub = stringResource(R.string.clock_stat_of, table.playerCount),
            ),
            Stat(
                label = stringResource(R.string.clock_stat_avg),
                value = if (avg > 0) formatter.format(avg) else "—",
                sub = if (avg > 0 && bigBlind > 0) {
                    stringResource(R.string.clock_stat_bb, bigBlinds(avg, bigBlind, formatter))
                } else {
                    null
                },
            ),
            Stat(
                label = stringResource(R.string.clock_stat_pool),
                value = money(table.prizePoolCents),
                sub = stringResource(R.string.strip_paid, paidPlaces),
                valueColor = PokerColors.PokerGold,
            ),
        ),
        modifier = modifier,
        showSubs = showSubs,
    )
}

private const val BLINDS_WEIGHT = 1.4f
private const val ANTE_WEIGHT = 0.6f
private const val NEXT_WEIGHT = 1f
private val PhoneBlinds = 56.dp
private const val ANTE_TO_BLINDS = 0.72f
private val WideBlinds = 72.dp
