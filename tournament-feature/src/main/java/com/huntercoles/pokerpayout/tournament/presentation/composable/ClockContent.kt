package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigUiState

/**
 * S2, the clock, level-first (PP-025): the setup strip, then the level's time left as the hero with
 * its progress, the blinds (big) and what's next (labelled, dimmer), the controls, the table's
 * numbers, next break / end time / rebuys, and the schedule. On a break the middle is S4
 * ([BreakContent]). Z1 (under 360 dp) tightens it; Z4 (840 dp and up) splits it 62/38.
 */
@Composable
internal fun ClockContent(
    setup: TournamentConfigUiState,
    timer: TimerUiState,
    actions: TournamentActions,
    layout: ClockLayout,
    gutter: Dp,
) {
    val openPanel = { actions.updateUi { it.openPanel() } }
    if (layout == ClockLayout.TwoPane) {
        Column(Modifier.fillMaxSize().padding(horizontal = gutter)) {
            SetupStrip(SetupSummary.strip(setup, timer, full = true), openPanel)
            Row(Modifier.weight(1f).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(
                    modifier = Modifier.weight(LEFT_PANE).verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ClockMain(timer, actions, layout)
                }
                Column(
                    modifier = Modifier.weight(1f - LEFT_PANE).verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ClockSide(setup, timer, showSubs = true)
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = gutter, end = gutter, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SetupStrip(SetupSummary.strip(setup, timer), openPanel)
            ClockMain(timer, actions, layout)
            ClockSide(setup, timer, showSubs = layout != ClockLayout.Small)
        }
    }
}

/** The level (or the break): hero, progress, blinds, controls. */
@Composable
private fun ClockMain(timer: TimerUiState, actions: TournamentActions, layout: ClockLayout) {
    val heroCap = if (layout == ClockLayout.TwoPane) TabletHero else PhoneHero
    when {
        timer.currentSegment == null -> NoSchedule(actions)
        timer.isOnBreak -> WithWidth(Modifier.fillMaxWidth()) { width -> BreakContent(timer, actions, width, heroCap) }
        else -> {
            WithWidth(Modifier.fillMaxWidth()) { width -> ClockHero(timer, width, heroCap, actions.onTimerIntent) }
            ClockProgress(timer)
            BlindsCard(timer, wide = layout == ClockLayout.TwoPane)
            ClockControls(timer, actions.onTimerIntent, compact = layout == ClockLayout.Small)
        }
    }
}

/** The table's numbers, the info list and the schedule. */
@Composable
private fun ClockSide(setup: TournamentConfigUiState, timer: TimerUiState, showSubs: Boolean) {
    ClockStats(timer, setup.paidPlaces, showSubs = showSubs)
    ClockInfo(timer)
    PokerEyebrow(stringResource(R.string.clock_schedule), modifier = Modifier.padding(top = 4.dp))
    ScheduleCard(timer)
}

/** A started clock whose saved setup can't be played (it shouldn't happen): say so, and open setup. */
@Composable
private fun NoSchedule(actions: TournamentActions) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.clock_no_blinds),
            style = MaterialTheme.typography.bodyLarge,
            color = PokerColors.CardWhite,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        PokerButton(
            text = stringResource(R.string.clock_fix_setup),
            onClick = { actions.updateUi { it.openPanel() } },
            variant = PokerButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Z4: the clock pane's share of the width. */
private const val LEFT_PANE = 0.62f
private val PhoneHero = 112.dp
private val TabletHero = 230.dp
