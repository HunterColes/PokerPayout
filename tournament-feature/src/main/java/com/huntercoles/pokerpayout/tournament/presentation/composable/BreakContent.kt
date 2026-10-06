package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.LevelProgress
import com.huntercoles.pokerpayout.core.design.components.LevelTone
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerPill
import com.huntercoles.pokerpayout.core.design.components.PokerPillTone
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.utils.ChipSetChips
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSegment
import com.huntercoles.pokerpayout.tournament.presentation.TimerIntent
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState

/**
 * S4: a break is its own state, not a recoloured level. A BREAK pill saying where play resumes, white
 * digits, then the to-do: the color-up spelled out with real chips and a "Color-up done" tick
 * (PP-026), the add-on tracker that jumps to the Bank, the level that follows, and "End break now"
 * for the "everyone's back early" case.
 */
@Composable
internal fun BreakContent(uiState: TimerUiState, actions: TournamentActions, width: Dp, heroCap: Dp) {
    val segment = uiState.currentBreak ?: return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BreakHero(uiState, segment, width, heroCap)
        if (segment.colorUp.isNotEmpty()) ColorUpCard(segment, uiState.colorUpDone, uiState.chipSet, actions.onTimerIntent)
        if (uiState.purchases.addOnCents > 0) AddOnCard(uiState, actions.openBank)
        ThenCard(uiState)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PokerButton(
                text = stringResource(R.string.break_end_now),
                onClick = { actions.onTimerIntent(TimerIntent.EndBreakNow) },
                variant = PokerButtonVariant.Secondary,
                enabled = !uiState.isFinished,
                modifier = Modifier.weight(1f),
            )
            PlayPauseButton(uiState, ControlSize) { actions.onTimerIntent(TimerIntent.ToggleTimer) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BreakHero(uiState: TimerUiState, segment: BreakSegment, width: Dp, cap: Dp) {
    val time = clockText(uiState.segmentRemainingSeconds)
    val size = rememberFittedSize(time, PokerType.Clock, width, cap)
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            PokerPill(clockEyebrow(uiState), tone = PokerPillTone.Gold, icon = PokerIcons.Coffee)
            clockPills(uiState).forEach { PokerPill(it.text, tone = it.tone) }
        }
        Text(
            text = time,
            style = PokerType.Clock.copy(fontSize = size, lineHeight = size * HERO_LINE_HEIGHT),
            color = heroColor(uiState),
            maxLines = 1,
            softWrap = false,
        )
        LevelProgress(progress = uiState.segmentProgress, tone = LevelTone.Low, modifier = Modifier.fillMaxWidth())
        if (segment.message.isNotBlank()) {
            Text(
                segment.message,
                style = MaterialTheme.typography.titleMedium,
                color = PokerColors.CardWhite,
                textAlign = TextAlign.Center,
            )
        }
        if (closesAddOns(uiState, segment)) {
            Text(
                text = stringResource(R.string.break_add_ons_close),
                style = MaterialTheme.typography.bodySmall,
                color = PokerColors.Chalk,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Add-ons close at the end of the first break after the rebuy cutoff (as the Bank gates them). */
private fun closesAddOns(uiState: TimerUiState, segment: BreakSegment): Boolean {
    val cutoff = uiState.rebuyUntilLevel
    if (uiState.purchases.addOnCents <= 0L || cutoff <= 0) return false
    val first = uiState.timeline.segments.filterIsInstance<BreakSegment>().firstOrNull { it.afterLevel >= cutoff }
    return first?.number == segment.number
}

/**
 * The color-up, with real chips: four 25s for one 100, the chip race, then a "done" tick. With your
 * chip set ([chipSet], PP-091 #9) they are your chips, in your colours.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorUpCard(segment: BreakSegment, done: Boolean, chipSet: ChipSetChips?, onIntent: (TimerIntent) -> Unit) {
    val formatter = rememberChipFormatter()
    val chips = chipList(segment.colorUp, formatter)
    val nextLevel = segment.afterLevel + 1
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(PokerColors.FeltGreen)
            .then(if (done) Modifier else Modifier.border(1.dp, PokerColors.DarkGold, CardShape))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (done) {
            ColorUpDoneRow(chips) { onIntent(TimerIntent.MarkColorUpDone(done = false)) }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PokerEyebrow(colorUpText(segment.colorUp, formatter), color = PokerColors.PokerGold)
                Text(
                    text = stringResource(R.string.break_not_needed, chips, nextLevel),
                    style = MaterialTheme.typography.bodySmall,
                    color = PokerColors.Chalk,
                )
            }
            segment.colorUpSwaps.forEach { swap -> ColorUpExchange(swap, chipSet) }
            ColorUpSteps(segment.colorUpSwaps, chipSet)
            PokerButton(
                text = stringResource(R.string.break_color_up_done),
                onClick = { onIntent(TimerIntent.MarkColorUpDone()) },
                variant = PokerButtonVariant.Secondary,
                size = PokerButtonSize.Small,
                icon = PokerIcons.Check,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorUpDoneRow(chips: String, onUndo: () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(PokerIcons.Check, contentDescription = null, tint = PokerColors.Live, modifier = Modifier.size(22.dp))
            ColorUpDoneWords(chips)
        }
        PokerButton(
            text = stringResource(R.string.break_undo),
            onClick = onUndo,
            variant = PokerButtonVariant.Text,
            size = PokerButtonSize.Small,
            icon = PokerIcons.Undo,
        )
    }
}

@Composable
private fun ColorUpDoneWords(chips: String) {
    Column {
        Text(stringResource(R.string.break_color_up_done), style = MaterialTheme.typography.titleMedium, color = PokerColors.Live)
        Text(
            text = stringResource(R.string.break_color_up_out, chips),
            style = MaterialTheme.typography.bodySmall,
            color = PokerColors.Chalk,
        )
    }
}

/** "Prize pool now $430 · Record in Bank ›", over a bar of the add-ons taken. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddOnCard(uiState: TimerUiState, openBank: () -> Unit) {
    val purchases = uiState.purchases
    val players = uiState.table.playersLeft.coerceAtLeast(1)
    val taken = purchases.addOnsTaken.coerceAtMost(players)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(PokerColors.FeltGreen)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PokerEyebrow(stringResource(R.string.break_add_ons, money(purchases.addOnCents)), modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.break_add_ons_count, taken, players),
                style = PokerType.NumberM,
                color = PokerColors.CardWhite,
            )
        }
        LevelProgress(progress = taken.toFloat() / players, tone = LevelTone.Low)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val pool = money(uiState.table.prizePoolCents)
            val sentence = stringResource(R.string.break_prize_pool_now, pool)
            Text(
                text = buildAnnotatedString {
                    append(sentence)
                    val at = sentence.indexOf(pool)
                    if (at >= 0) addStyle(SpanStyle(color = PokerColors.PokerGold), at, at + pool.length)
                },
                style = MaterialTheme.typography.bodySmall,
                color = PokerColors.Chalk,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            PokerButton(
                text = stringResource(R.string.break_record_in_bank),
                onClick = openBank,
                variant = PokerButtonVariant.Text,
                size = PokerButtonSize.Small,
                icon = PokerIcons.Wallet,
            )
        }
    }
}

/** "THEN · LEVEL 5  200 / 400 ante 400". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThenCard(uiState: TimerUiState) {
    val next = uiState.nextLevelSegment ?: return
    val formatter = rememberChipFormatter()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(PokerColors.FeltGreen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PokerEyebrow(stringResource(R.string.clock_then_level, next.level.level))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = blindsText(next.level, formatter),
                style = PokerType.NumberL,
                color = PokerColors.CardWhite,
                modifier = Modifier.alignByBaseline(),
            )
            if (next.level.ante > 0) {
                Text(
                    text = stringResource(R.string.clock_ante, formatter.format(next.level.ante)),
                    style = PokerType.NumberM.copy(fontSize = PokerType.NumberS.fontSize),
                    color = PokerColors.Chalk,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
    }
}
