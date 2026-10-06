package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigIntent

/**
 * The money per player: buy-in, bounty, rebuy (and its cutoff, before the start), add-on, food. Only
 * a committed $0 rebuy or add-on can clear recorded purchases, and only after asking (PP-014).
 */
@Composable
internal fun MoneyGrid(amounts: MoneySettings, timer: TimerUiState, actions: TournamentActions) {
    val onSetup = actions.onSetupIntent
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BuyInField(amounts, onSetup, Modifier.weight(1f))
            BountyField(amounts, onSetup, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            RebuyField(amounts, onSetup, Modifier.weight(1f))
            RebuysUntilSelect(timer, actions.onTimerIntent, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AddOnField(amounts, onSetup, Modifier.weight(1f))
            FoodField(amounts, onSetup, Modifier.weight(1f))
        }
    }
}

/** The same amounts in the mid-game panel once unlocked; the rebuy cutoff is already open above them. */
@Composable
internal fun PanelMoneyGrid(amounts: MoneySettings, onSetup: (TournamentConfigIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BuyInField(amounts, onSetup, Modifier.weight(1f))
            BountyField(amounts, onSetup, Modifier.weight(1f))
        }
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
private fun BountyField(amounts: MoneySettings, onSetup: (TournamentConfigIntent) -> Unit, modifier: Modifier) {
    MoneyTextField(
        valueCents = amounts.bountyCents,
        onAmount = amountHandler({ onSetup(TournamentConfigIntent.UpdateBountyPerPlayer(it)) }),
        label = stringResource(R.string.setup_bounty),
        isLocked = false,
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
