package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerStepper
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.presentation.TimerIntent
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigIntent
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigUiState

/** The players stepper's range (as the slider it replaces). */
internal val PlayerRange = 3..30

private val SectionShape = RoundedCornerShape(PokerDimens.CornerCard)

/**
 * S1 v2, before the start: the whole page is setup, under the [ReadyTicket] that previews the clock
 * it builds. People, money, blinds, payouts, in the order hosts decide them, then a sticky "Start
 * clock". Money and blinds fold to one-line summaries.
 */
@Composable
internal fun SetupContent(
    setup: TournamentConfigUiState,
    timer: TimerUiState,
    actions: TournamentActions,
    gutter: Dp,
) {
    val playable = timer.setupProblem == null && timer.baseBlindLevels.isNotEmpty()
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = gutter, end = gutter, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReadyTicket(timer)
            PlayersCard(setup.playerCount, hint = stringResource(R.string.setup_players_hint)) {
                actions.onSetupIntent(TournamentConfigIntent.UpdatePlayerCount(it))
            }
            var moneyOpen by rememberSaveable { mutableStateOf(true) }
            SetupSection(
                title = stringResource(R.string.setup_money_title),
                note = stringResource(R.string.setup_money_to_sit_down, money(setup.money.entryCents)),
                summary = SetupSummary.moneyLine(setup, timer),
                expanded = moneyOpen,
                onToggle = { moneyOpen = !moneyOpen },
            ) {
                MoneyGrid(setup.money, timer, actions)
                PrizePoolNote(setup)
            }
            var blindsOpen by rememberSaveable { mutableStateOf(true) }
            SetupSection(
                title = stringResource(R.string.setup_blinds),
                note = SetupSummary.levelsAndBreaks(timer),
                summary = SetupSummary.blindsLine(timer),
                expanded = blindsOpen,
                onToggle = { blindsOpen = !blindsOpen },
            ) {
                BlindsConfigPanel(uiState = timer, onIntent = actions.onTimerIntent)
            }
            PayoutsRow(setup, actions.openPayouts)
        }
        StartBar(enabled = playable, gutter = gutter) {
            actions.updateUi { it.pressStart() }
            actions.onTimerIntent(TimerIntent.ToggleTimer)
        }
    }
}

/** A setup card that folds to its one-line [summary]; [note] sits beside the title ("$50 to sit down"). */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongParameterList") // a section: title, note, summary, state, toggle, body
@Composable
internal fun SetupSection(
    title: String,
    note: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val action = stringResource(if (expanded) R.string.setup_collapse_section else R.string.setup_expand_section, title)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SectionShape)
            .background(PokerColors.FeltGreen)
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PokerDimens.MinTouch)
                .clickable(onClickLabel = action, role = Role.Button, onClick = onToggle)
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                PokerEyebrow(title, modifier = Modifier.padding(end = 8.dp))
                Text(note, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk, textAlign = TextAlign.End)
            }
            Icon(
                imageVector = PokerIcons.ChevronDown,
                contentDescription = null,
                tint = PokerColors.Chalk,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (expanded) HALF_TURN else 0f),
            )
        }
        if (expanded) {
            content()
        } else {
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = PokerColors.CardWhite)
        }
    }
}

/** "Players · Bank gets one row per player" with a stepper. Mid-game: "A late entry adds a Bank row". */
@Composable
internal fun PlayersCard(
    count: Int,
    hint: String,
    modifier: Modifier = Modifier,
    framed: Boolean = true,
    onChange: (Int) -> Unit,
) {
    val label = stringResource(R.string.setup_players)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (framed) {
                    Modifier
                        .clip(SectionShape)
                        .background(PokerColors.FeltGreen)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = PokerColors.CardWhite)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
        }
        PokerStepper(value = count, onValueChange = onChange, range = PlayerRange, label = label)
    }
}

/** The rebuy cutoff (PP-030): no cutoff, or the end of a level. The Bank and the clock both obey it. */
@Composable
internal fun RebuysUntilSelect(timer: TimerUiState, onTimerIntent: (TimerIntent) -> Unit, modifier: Modifier = Modifier) {
    val resources = LocalContext.current.resources
    val last = maxOf(timer.regularLevelCount, timer.rebuyUntilLevel, 1)
    SetupSelectField(
        label = stringResource(R.string.setup_rebuys_until),
        options = listOf(0) + (1..last),
        selected = timer.rebuyUntilLevel,
        optionText = { level ->
            if (level == 0) {
                resources.getString(R.string.setup_rebuys_until_none)
            } else {
                resources.getString(R.string.setup_rebuys_until_level, level)
            }
        },
        onPick = { onTimerIntent(TimerIntent.UpdateRebuyUntil(it)) },
        modifier = modifier,
    )
}

/** "Prize pool starts at $360 and grows with rebuys and add-ons." */
@Composable
private fun PrizePoolNote(setup: TournamentConfigUiState) {
    val amount = money(setup.money.buyInCents * setup.playerCount)
    val sentence = stringResource(R.string.setup_prize_pool_starts, amount)
    val at = sentence.indexOf(amount)
    Text(
        text = buildAnnotatedString {
            append(sentence)
            if (at >= 0) addStyle(SpanStyle(color = PokerColors.PokerGold), at, at + amount.length)
        },
        style = MaterialTheme.typography.bodySmall,
        color = PokerColors.Chalk,
    )
}

/** "Payouts · Standard · 3 places · rounded to $5 ›": opens the Payouts tab, where it's set. */
@Composable
private fun PayoutsRow(setup: TournamentConfigUiState, openPayouts: () -> Unit) {
    val places = setup.paidPlaces
    val summary = stringResource(
        R.string.setup_payouts_summary,
        setup.payoutPreset?.label ?: stringResource(R.string.setup_payouts_custom),
        pluralStringResource(R.plurals.setup_places, places, places),
        setup.config.payoutRounding.label,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SectionShape)
            .background(PokerColors.FeltGreen)
            .clickable(onClickLabel = stringResource(R.string.setup_payouts_open), role = Role.Button, onClick = openPayouts)
            .heightIn(min = PokerDimens.RowMinHeight)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.setup_payouts),
                style = MaterialTheme.typography.titleMedium,
                color = PokerColors.CardWhite,
            )
            Text(summary, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
        }
        Icon(PokerIcons.ChevronRight, contentDescription = null, tint = PokerColors.Chalk)
    }
}

/** "Start clock", sticky under the page. Off while the blinds can't be built (the verdict says why). */
@Composable
private fun StartBar(enabled: Boolean, gutter: Dp, onStart: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = PokerColors.FeltLine)
        PokerButton(
            text = stringResource(R.string.setup_start_clock),
            onClick = onStart,
            icon = PokerIcons.Play,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = gutter, vertical = 10.dp),
        )
    }
}

private const val HALF_TURN = 180f
