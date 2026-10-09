package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerSegmentedControl
import com.huntercoles.pokerpayout.core.design.components.PokerStepper
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.ShotClockIntent
import com.huntercoles.pokerpayout.tools.presentation.ShotClockUiState
import com.huntercoles.pokerpayout.tools.presentation.TimeBankPlayer
import com.huntercoles.pokerpayout.tools.shotclock.ShotClockPhase
import com.huntercoles.pokerpayout.tools.shotclock.ShotClockStore

/** Pause or Resume, and Reset, once a decision is on the clock. Nothing before the first tap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShotClockActions(state: ShotClockUiState, onIntent: (ShotClockIntent) -> Unit) {
    if (state.phase == ShotClockPhase.Ready) return
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (state.phase) {
            ShotClockPhase.Running -> SmallAction(R.string.shot_clock_pause, PokerIcons.Pause) { onIntent(ShotClockIntent.Pause) }
            ShotClockPhase.Paused -> SmallAction(R.string.shot_clock_resume, PokerIcons.Play) { onIntent(ShotClockIntent.Resume) }
            else -> Unit
        }
        SmallAction(R.string.shot_clock_reset, PokerIcons.Restart) { onIntent(ShotClockIntent.Reset) }
    }
}

@Composable
private fun SmallAction(label: Int, icon: ImageVector, onClick: () -> Unit) {
    PokerButton(
        text = stringResource(label),
        onClick = onClick,
        variant = PokerButtonVariant.Secondary,
        size = PokerButtonSize.Small,
        icon = icon,
    )
}

/** The time to act: 30, 45 or 60 seconds. Changed mid-decision, it counts from the next one. */
@Composable
internal fun TimeToActSection(state: ShotClockUiState, onIntent: (ShotClockIntent) -> Unit) {
    ChipSetSection {
        SectionHeader(
            title = stringResource(R.string.shot_clock_time_to_act),
            note = if (state.phase != ShotClockPhase.Ready) stringResource(R.string.shot_clock_applies_next) else null,
        )
        val labels = ShotClockStore.PRESETS.associateWith { stringResource(R.string.shot_clock_seconds_option, it) }
        PokerSegmentedControl(
            options = ShotClockStore.PRESETS,
            selected = state.seconds,
            onSelect = { onIntent(ShotClockIntent.SetSeconds(it)) },
            label = { labels.getValue(it) },
        )
    }
}

/**
 * The time bank: how many cards each player starts with (none turns it off), then a row a player
 * with the cards they have left and a button to play one, which adds 30 s to the decision on the
 * clock. Once anyone has played a card, everyone can have theirs back (with Undo).
 */
@Composable
internal fun TimeBankSection(state: ShotClockUiState, onIntent: (ShotClockIntent) -> Unit) {
    ChipSetSection {
        SectionHeader(
            title = stringResource(R.string.shot_clock_time_bank),
            note = stringResource(R.string.shot_clock_card_adds, ShotClockStore.CARD_SECONDS),
        )
        val label = stringResource(R.string.shot_clock_cards_each_label)
        SideBySideOrStacked(
            modifier = Modifier.fillMaxWidth(),
            first = { Text(text = label, style = MaterialTheme.typography.titleSmall, color = PokerColors.CardWhite) },
            second = {
                PokerStepper(
                    value = state.cardsEach,
                    onValueChange = { onIntent(ShotClockIntent.SetCardsEach(it)) },
                    range = 0..ShotClockStore.MAX_CARDS_EACH,
                    label = label,
                    modifier = Modifier.widthIn(min = StepperWidth),
                )
            },
        )
        if (state.cardsEach == 0) {
            Text(
                text = stringResource(R.string.shot_clock_time_bank_off),
                style = MaterialTheme.typography.bodySmall,
                color = PokerColors.Chalk,
            )
        } else {
            state.players.forEachIndexed { index, player ->
                HorizontalDivider(color = PokerColors.FeltLine)
                TimeBankRow(player, state, onPlay = { onIntent(ShotClockIntent.PlayCard(index)) })
            }
            if (state.anyCardsPlayed) {
                PokerButton(
                    text = stringResource(R.string.shot_clock_give_back),
                    onClick = { onIntent(ShotClockIntent.GiveCardsBack) },
                    variant = PokerButtonVariant.Text,
                    size = PokerButtonSize.Small,
                    icon = PokerIcons.Undo,
                )
            }
        }
    }
}

/** A player, their cards (gold if left, an outline if played), and +30 s to play one. */
@Composable
private fun TimeBankRow(player: TimeBankPlayer, state: ShotClockUiState, onPlay: () -> Unit) {
    val spoken = stringResource(R.string.shot_clock_play_card_description, player.name, ShotClockStore.CARD_SECONDS)
    SideBySideOrStacked(
        modifier = Modifier.fillMaxWidth().heightIn(min = PokerDimens.MinTouch),
        first = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = player.name, style = MaterialTheme.typography.titleSmall, color = PokerColors.CardWhite)
                CardPips(left = player.cardsLeft, total = state.cardsEach)
            }
        },
        second = {
            PokerButton(
                text = stringResource(R.string.shot_clock_play_card, ShotClockStore.CARD_SECONDS),
                onClick = onPlay,
                variant = PokerButtonVariant.Secondary,
                size = PokerButtonSize.Small,
                enabled = state.canPlayCards && player.cardsLeft > 0,
                modifier = Modifier.semantics { contentDescription = spoken },
            )
        },
    )
}

/** The cards as little card backs: gold for each one left, an outline for each played. */
@Composable
private fun CardPips(left: Int, total: Int) {
    val spoken = stringResource(R.string.shot_clock_cards_left, left, total)
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(total) { index ->
            val shape = RoundedCornerShape(3.dp)
            Box(
                Modifier
                    .size(width = 12.dp, height = 16.dp)
                    .then(
                        if (index < left) {
                            Modifier.background(PokerColors.PokerGold, shape)
                        } else {
                            Modifier.border(1.dp, PokerColors.FeltEdge, shape)
                        },
                    ),
            )
        }
    }
}

/** How the warnings work, at the foot of the controls. */
@Composable
internal fun ShotClockNote() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            imageVector = PokerIcons.Info,
            contentDescription = null,
            tint = PokerColors.Chalk,
            modifier = Modifier.padding(top = 1.dp).size(18.dp),
        )
        Text(
            text = stringResource(R.string.shot_clock_how),
            style = MaterialTheme.typography.bodySmall,
            color = PokerColors.Chalk,
            modifier = Modifier.weight(1f),
        )
    }
}

private val StepperWidth = 148.dp
