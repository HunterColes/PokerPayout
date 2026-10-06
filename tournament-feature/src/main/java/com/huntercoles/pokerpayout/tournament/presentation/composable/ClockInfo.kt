package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.presentation.RebuyState
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import java.util.Date

/** Next break, end time, rebuys: one glance away (S2). */
@Composable
internal fun ClockInfo(uiState: TimerUiState, modifier: Modifier = Modifier) {
    val rows = infoRows(uiState)
    if (rows.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(PokerColors.FeltGreen)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = PokerColors.FeltLine)
            InfoRow(row)
        }
    }
}

private class InfoLine(val icon: ImageVector, val label: String, val value: String, val valueColor: Color = PokerColors.CardWhite)

@Composable
private fun infoRows(uiState: TimerUiState): List<InfoLine> {
    val nextBreak = uiState.nextBreak
    val breakLine = nextBreak?.let { segment ->
        InfoLine(
            PokerIcons.Coffee,
            stringResource(R.string.clock_info_break_after, segment.afterLevel),
            inTime(segment.startSeconds - uiState.elapsedSeconds),
        )
    }
    return listOfNotNull(breakLine, endLine(uiState), rebuyLine(uiState.rebuyState))
}

@Composable
private fun endLine(uiState: TimerUiState): InfoLine {
    val remaining = uiState.tournamentRemainingSeconds
    val endsAt = uiState.endsAtWallClock
    return when {
        uiState.isFinished -> InfoLine(PokerIcons.Timer, stringResource(R.string.clock_info_finished), "")
        remaining <= 0 -> InfoLine(
            PokerIcons.Timer,
            stringResource(R.string.clock_info_overtime),
            stringResource(R.string.clock_info_overtime_by, clockText(-remaining)),
            PokerColors.Danger,
        )
        endsAt == null -> InfoLine(PokerIcons.Timer, stringResource(R.string.clock_info_overtime), clockText(remaining))
        else -> InfoLine(PokerIcons.Timer, stringResource(R.string.clock_info_ends, timeOfDay(endsAt)), clockText(remaining))
    }
}

@Composable
private fun rebuyLine(state: RebuyState): InfoLine? {
    val taken = pluralStringResource(R.plurals.clock_taken, state.taken, state.taken)
    return when (state) {
        is RebuyState.Off -> null
        is RebuyState.Closed ->
            InfoLine(PokerIcons.Lock, stringResource(R.string.clock_info_rebuys_closed, state.afterLevel), taken)
        is RebuyState.Open -> InfoLine(
            PokerIcons.Restart,
            state.untilLevel?.let { stringResource(R.string.clock_info_rebuys_until, it) }
                ?: stringResource(R.string.clock_info_rebuys_open),
            taken,
        )
    }
}

/** Icon, words, value; at very large text the value moves under the words instead of squeezing them. */
@Composable
private fun InfoRow(line: InfoLine) {
    val stacked = LocalDensity.current.fontScale > STACK_ABOVE_FONT_SCALE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(line.icon, contentDescription = null, tint = PokerColors.Chalk, modifier = Modifier.size(18.dp))
        val value = @Composable {
            if (line.value.isNotEmpty()) {
                Text(
                    text = line.value,
                    style = PokerType.NumberS.copy(fontSize = PokerType.NumberM.fontSize),
                    color = line.valueColor,
                    textAlign = TextAlign.End,
                )
            }
        }
        if (stacked) {
            Column(Modifier.weight(1f)) {
                Text(line.label, style = MaterialTheme.typography.bodyMedium, color = PokerColors.Chalk)
                value()
            }
        } else {
            Text(
                line.label,
                style = MaterialTheme.typography.bodyMedium,
                color = PokerColors.Chalk,
                modifier = Modifier.weight(1f),
            )
            value()
        }
    }
}

/** "11:10" (or "11:10 AM"), in the device's own 12- or 24-hour format. */
@Composable
internal fun timeOfDay(wallMillis: Long): String {
    val context = LocalContext.current
    return remember(context, wallMillis) { android.text.format.DateFormat.getTimeFormat(context).format(Date(wallMillis)) }
}

/** Above this font scale two-column rows stack (as the stats strip does, design spec §6.4). */
internal const val STACK_ABOVE_FONT_SCALE = 1.5f
