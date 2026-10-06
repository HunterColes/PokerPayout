package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState

/**
 * S1 v2: the clock being built, live, at the top of setup. Level 1's time and blinds, what comes next,
 * the number of levels and breaks and the total length, recomputed as the fields change. When the
 * blinds can't be built it says so instead ("can't start"), and the verdict below has the fixes.
 */
@Composable
internal fun ReadyTicket(uiState: TimerUiState, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(PokerDimens.CornerCard)
    val playable = uiState.setupProblem == null && uiState.baseBlindLevels.isNotEmpty()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, if (playable) PokerColors.PokerGold else PokerColors.Danger), shape)
            .background(PokerColors.PokerBlack, shape)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (playable) {
            TicketTop(uiState)
            DashedRule()
            TicketShape(uiState)
        } else {
            CantStart()
        }
    }
}

@Composable
private fun TicketTop(uiState: TimerUiState) {
    val formatter = rememberChipFormatter()
    val first = uiState.baseBlindLevels.first()
    val next = uiState.baseBlindLevels.getOrNull(1)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val clock = clockText(uiState.config.roundLengthMinutes * SECONDS_PER_MINUTE)
        val half = maxWidth * CLOCK_SHARE - 6.dp
        val clockSize = rememberFittedSize(clock, PokerType.DisplayL, half, TicketClock)
        val blinds = blindsText(first, formatter)
        val blindsSize = rememberFittedSize(blinds, PokerType.NumberL, half, TicketBlinds)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PokerEyebrow(stringResource(R.string.setup_ticket_ready), color = PokerColors.PokerGold)
                Text(
                    text = clock,
                    style = PokerType.DisplayL.copy(fontSize = clockSize, lineHeight = clockSize),
                    color = PokerColors.PokerGold,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PokerEyebrow(stringResource(R.string.setup_blinds))
                Text(
                    text = blinds,
                    style = PokerType.NumberL.copy(fontSize = blindsSize, lineHeight = blindsSize),
                    color = PokerColors.CardWhite,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                )
                next?.let {
                    Text(
                        text = stringResource(R.string.setup_ticket_next, blindsText(it, formatter)),
                        style = MaterialTheme.typography.bodySmall,
                        color = PokerColors.Chalk,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun CantStart() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PokerEyebrow(stringResource(R.string.setup_ticket_cant_start), color = PokerColors.Danger)
        Text(
            text = stringResource(R.string.setup_ticket_fix_below),
            style = MaterialTheme.typography.titleMedium,
            color = PokerColors.CardWhite,
        )
    }
}

/** "9 levels · 2 breaks" and "3:20 in all". */
@Composable
private fun TicketShape(uiState: TimerUiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = SetupSummary.levelsAndBreaks(uiState),
            style = MaterialTheme.typography.bodySmall,
            color = PokerColors.CardWhite,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.setup_ticket_total, hoursMinutes(uiState.timeline.regularEndSeconds)),
            style = MaterialTheme.typography.bodySmall,
            color = PokerColors.CardWhite,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun DashedRule() {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(1.dp),
    ) {
        drawLine(
            color = PokerColors.FeltEdge,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH)),
        )
    }
}

/** The ticket's clock takes at most this share of its width; the blinds the rest. */
private const val CLOCK_SHARE = 0.5f
private const val DASH = 6f
private val TicketClock = 56.dp
private val TicketBlinds = 30.dp
