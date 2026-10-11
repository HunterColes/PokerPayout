package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.model.MysteryBounty
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigIntent
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigUiState

/**
 * The money per player: buy-in, bounty and how bounties pay (PP-035), rebuy (and its cutoff, before
 * the start), add-on, food, and until when a player can join late or re-enter (PP-116). Only a
 * committed $0 rebuy or add-on can clear recorded purchases, and only after asking (PP-014).
 */
@Composable
internal fun MoneyGrid(setup: TournamentConfigUiState, timer: TimerUiState, actions: TournamentActions) {
    val amounts = setup.money
    val onSetup = actions.onSetupIntent
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BuyInField(amounts, onSetup, Modifier.weight(1f))
            BountyField(amounts, onSetup, Modifier.weight(1f), isLocked = setup.bountyAmountLocked)
        }
        BountyTypeField(setup, onSetup)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            RebuyField(amounts, onSetup, Modifier.weight(1f))
            RebuysUntilSelect(timer, actions.onTimerIntent, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AddOnField(amounts, onSetup, Modifier.weight(1f))
            FoodField(amounts, onSetup, Modifier.weight(1f))
        }
        // Late arrivals and re-entries pay the entry above (PP-116)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            LateEntryUntilSelect(setup, timer, onSetup, Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
        }
    }
}

/** The same amounts in the mid-game panel once unlocked; the rebuy cutoff is already open above them. */
@Composable
internal fun PanelMoneyGrid(setup: TournamentConfigUiState, onSetup: (TournamentConfigIntent) -> Unit) {
    val amounts = setup.money
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BuyInField(amounts, onSetup, Modifier.weight(1f))
            BountyField(amounts, onSetup, Modifier.weight(1f), isLocked = setup.bountyAmountLocked)
        }
        BountyTypeField(setup, onSetup)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RebuyField(amounts, onSetup, Modifier.weight(1f))
            AddOnField(amounts, onSetup, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FoodField(amounts, onSetup, Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * How knockouts pay (PP-035), under the bounty while there is one: Standard, Progressive or Mystery
 * as three radio chips that wrap onto a second line on a small phone (a segment would have to break
 * "Progressive"), then a line saying what the choice means. Mystery lists its envelopes before the
 * start ("1 × $15 · 2 × $6 · 6 × $3"). The type is fixed from the first knockout in the Bank (the
 * line says so).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BountyTypeField(setup: TournamentConfigUiState, onSetup: (TournamentConfigIntent) -> Unit) {
    if (setup.money.bountyCents <= 0L) return
    val labels = mapOf(
        BountyMode.STANDARD to stringResource(R.string.setup_bounty_standard),
        BountyMode.PROGRESSIVE to stringResource(R.string.setup_bounty_progressive),
        BountyMode.MYSTERY to stringResource(R.string.setup_bounty_mystery),
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.setup_bounty_type), style = SetupFieldStyle.Label, color = PokerColors.Chalk)
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BountyMode.entries.forEach { mode ->
                BountyTypeChip(
                    label = labels.getValue(mode),
                    selected = mode == setup.bountyMode,
                    enabled = !setup.bountyTypeLocked,
                    onClick = { onSetup(TournamentConfigIntent.UpdateBountyMode(mode)) },
                )
            }
        }
        Text(bountyTypeNote(setup), style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
    }
}

/** One bounty type: a 48 dp radio chip; the chosen one on FeltHigh with a gold edge and a tick. */
@Composable
private fun BountyTypeChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = SetupFieldStyle.Shape
    val color = when {
        selected -> PokerColors.PokerGold
        enabled -> PokerColors.CardWhite
        else -> PokerColors.ChalkDim
    }
    Row(
        modifier = Modifier
            .heightIn(min = PokerDimens.MinTouch)
            .clip(shape)
            .background(if (selected) PokerColors.FeltHigh else PokerColors.DarkGreen)
            .border(if (selected) 2.dp else 1.dp, if (selected) PokerColors.PokerGold else PokerColors.FeltEdge, shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (selected) Icon(PokerIcons.Check, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), color = color)
    }
}

/** What the bounty type means, in a sentence; and once a knockout is in, that it is fixed. */
@Composable
private fun bountyTypeNote(setup: TournamentConfigUiState): String {
    val bounty = money(setup.money.bountyCents)
    val note = when (setup.bountyMode) {
        BountyMode.STANDARD -> stringResource(R.string.setup_bounty_note_standard, bounty)
        BountyMode.PROGRESSIVE -> stringResource(R.string.setup_bounty_note_progressive)
        BountyMode.MYSTERY -> {
            val envelopes = MysteryBounty.groups(setup.envelopes)
                .map { group -> stringResource(R.string.setup_bounty_envelopes, group.count, money(group.cents)) }
                .joinToString(stringResource(R.string.strip_separator))
            stringResource(R.string.setup_bounty_note_mystery, envelopes)
        }
    }
    return if (setup.bountyTypeLocked) "$note ${stringResource(R.string.setup_bounty_locked)}" else note
}

@Composable
private fun BuyInField(amounts: MoneySettings, onSetup: (TournamentConfigIntent) -> Unit, modifier: Modifier) {
    MoneyTextField(
        valueCents = amounts.buyInCents,
        onAmount = amountHandler({ onSetup(TournamentConfigIntent.UpdateBuyIn(it)) }),
        label = stringResource(R.string.setup_buy_in),
        isLocked = false,
        modifier = modifier,
    )
}

@Composable
private fun BountyField(
    amounts: MoneySettings,
    onSetup: (TournamentConfigIntent) -> Unit,
    modifier: Modifier,
    isLocked: Boolean,
) {
    MoneyTextField(
        valueCents = amounts.bountyCents,
        onAmount = amountHandler({ onSetup(TournamentConfigIntent.UpdateBountyPerPlayer(it)) }),
        label = stringResource(R.string.setup_bounty),
        isLocked = isLocked,
        modifier = modifier,
    )
}

@Composable
private fun RebuyField(amounts: MoneySettings, onSetup: (TournamentConfigIntent) -> Unit, modifier: Modifier) {
    MoneyTextField(
        valueCents = amounts.rebuyCents,
        onAmount = amountHandler(
            onTyped = { onSetup(TournamentConfigIntent.UpdateRebuyAmount(it)) },
            onCommitted = { onSetup(TournamentConfigIntent.CommitRebuyAmount(it.cents, it.centsBeforeEdit)) },
        ),
        label = stringResource(R.string.setup_rebuy),
        isLocked = false,
        modifier = modifier,
    )
}

@Composable
private fun AddOnField(amounts: MoneySettings, onSetup: (TournamentConfigIntent) -> Unit, modifier: Modifier) {
    MoneyTextField(
        valueCents = amounts.addOnCents,
        onAmount = amountHandler(
            onTyped = { onSetup(TournamentConfigIntent.UpdateAddOnAmount(it)) },
            onCommitted = { onSetup(TournamentConfigIntent.CommitAddOnAmount(it.cents, it.centsBeforeEdit)) },
        ),
        label = stringResource(R.string.setup_add_on),
        isLocked = false,
        modifier = modifier,
    )
}

@Composable
private fun FoodField(amounts: MoneySettings, onSetup: (TournamentConfigIntent) -> Unit, modifier: Modifier) {
    MoneyTextField(
        valueCents = amounts.foodCents,
        onAmount = amountHandler({ onSetup(TournamentConfigIntent.UpdateFoodPerPlayer(it)) }),
        label = stringResource(R.string.setup_food),
        isLocked = false,
        modifier = modifier,
    )
}
