package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.components.PokerNumberField
import com.huntercoles.pokerpayout.tournament.presentation.TimerIntent
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigIntent
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigUiState

@Composable
fun FolderTab(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .widthIn(max = 160.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
    ) {
        // Tab background - selected appears raised, unselected appears recessed
        Surface(
            modifier = Modifier
                .fillMaxSize(),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = if (isSelected) PokerColors.SurfaceSecondary.copy(alpha = 0.4f) else PokerColors.FeltGreen,
            shadowElevation = if (isSelected) 4.dp else 0.dp,
            border = if (isSelected) null else BorderStroke(
                width = 2.dp,
                color = PokerColors.CardWhite.copy(alpha = 0.3f)
            )
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = text,
                    color = if (isSelected) PokerColors.CardWhite else PokerColors.CardWhite.copy(alpha = 0.7f),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 16.sp
                )
            }
        }
    }
}

private const val PANEL_PLAYER = "player"
private const val PANEL_BLINDS = "blinds"
private const val PANEL_PAYOUTS = "payouts"

/** The player slider, then the Player / Blinds / Payouts folder tabs and the selected panel. */
@Composable
fun PoolConfigurationSection(
    uiState: TournamentConfigUiState,
    onIntent: (TournamentConfigIntent) -> Unit,
    onTimerIntent: (TimerIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val isLocked = uiState.isTournamentLocked
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PlayerCountSlider(
            playerCount = uiState.playerCount,
            onPlayerCountChange = { onIntent(TournamentConfigIntent.UpdatePlayerCount(it)) },
            isLocked = isLocked
        )

        // Tabs and content grouped together with no spacing between them
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                listOf(PANEL_PLAYER to "Player", PANEL_BLINDS to "Blinds", PANEL_PAYOUTS to "Payouts")
                    .forEach { (panel, title) ->
                        FolderTab(
                            text = title,
                            isSelected = uiState.selectedPanel == panel,
                            onClick = { onIntent(TournamentConfigIntent.UpdateSelectedPanel(panel)) },
                            // Three tabs share the width; at a fixed 120+ dp each the third was squeezed
                            modifier = Modifier.weight(1f)
                        )
                    }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = PokerColors.SurfaceSecondary.copy(alpha = 0.4f)
            ) {
                when (uiState.selectedPanel) {
                    PANEL_BLINDS -> BlindsPanel(uiState, onIntent, onTimerIntent)
                    PANEL_PAYOUTS -> PayoutsPanel(uiState, onIntent)
                    else -> PlayerPanel(uiState, onIntent)
                }
            }
        }
    }
}

@Composable
private fun PlayerPanel(uiState: TournamentConfigUiState, onIntent: (TournamentConfigIntent) -> Unit) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EntryFeeRow(uiState, onIntent)
        PurchaseRow(uiState, onIntent)
    }
}

@Composable
private fun EntryFeeRow(uiState: TournamentConfigUiState, onIntent: (TournamentConfigIntent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MoneyTextField(
            valueCents = uiState.money.buyInCents,
            onAmount = amountHandler({ onIntent(TournamentConfigIntent.UpdateBuyIn(it)) }),
            label = "Buy-in ($)",
            isLocked = uiState.isTournamentLocked,
            modifier = Modifier.weight(1f)
        )
        MoneyTextField(
            valueCents = uiState.money.foodCents,
            onAmount = amountHandler({ onIntent(TournamentConfigIntent.UpdateFoodPerPlayer(it)) }),
            label = "Food ($)",
            isLocked = uiState.isTournamentLocked,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Bounty, rebuy and add-on. Only a committed zero rebuy or add-on can clear purchases (PP-014). */
@Composable
private fun PurchaseRow(uiState: TournamentConfigUiState, onIntent: (TournamentConfigIntent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MoneyTextField(
            valueCents = uiState.money.bountyCents,
            onAmount = amountHandler({ onIntent(TournamentConfigIntent.UpdateBountyPerPlayer(it)) }),
            label = "Bounty ($)",
            isLocked = uiState.isTournamentLocked,
            modifier = Modifier.weight(1f)
        )
        MoneyTextField(
            valueCents = uiState.money.rebuyCents,
            onAmount = amountHandler(
                onTyped = { onIntent(TournamentConfigIntent.UpdateRebuyAmount(it)) },
                onCommitted = { onIntent(TournamentConfigIntent.CommitRebuyAmount(it.cents, it.centsBeforeEdit)) }
            ),
            label = "Rebuy ($)",
            isLocked = uiState.isTournamentLocked,
            modifier = Modifier.weight(1f)
        )
        MoneyTextField(
            valueCents = uiState.money.addOnCents,
            onAmount = amountHandler(
                onTyped = { onIntent(TournamentConfigIntent.UpdateAddOnAmount(it)) },
                onCommitted = { onIntent(TournamentConfigIntent.CommitAddOnAmount(it.cents, it.centsBeforeEdit)) }
            ),
            label = "Add-on ($)",
            isLocked = uiState.isTournamentLocked,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BlindsPanel(
    uiState: TournamentConfigUiState,
    onIntent: (TournamentConfigIntent) -> Unit,
    onTimerIntent: (TimerIntent) -> Unit
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BlindsTimeRow(uiState, onIntent, onTimerIntent)
        BlindsChipsRow(uiState, onIntent, onTimerIntent)
    }
}

@Composable
private fun BlindsTimeRow(
    uiState: TournamentConfigUiState,
    onIntent: (TournamentConfigIntent) -> Unit,
    onTimerIntent: (TimerIntent) -> Unit
) {
    val isLocked = uiState.isTournamentLocked
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PokerNumberField(
            value = uiState.gameDurationHours,
            onValueChange = { hours ->
                val cappedHours = hours.coerceIn(1, MAX_DURATION_HOURS)
                if (!isLocked) {
                    onIntent(TournamentConfigIntent.UpdateGameDurationHours(cappedHours))
                    onTimerIntent(TimerIntent.GameDurationHoursChanged(cappedHours))
                }
            },
            label = "Duration (Hours)",
            isLocked = isLocked,
            minValue = 1,
            maxValue = MAX_DURATION_HOURS,
            modifier = Modifier.weight(1f)
        )

        PokerNumberField(
            value = uiState.roundLengthMinutes,
            onValueChange = { minutes ->
                if (!isLocked) {
                    onIntent(TournamentConfigIntent.UpdateRoundLength(minutes))
                    onTimerIntent(TimerIntent.UpdateRoundLength(minutes))
                }
            },
            label = "Round Length (Min)",
            isLocked = isLocked,
            minValue = 1,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BlindsChipsRow(
    uiState: TournamentConfigUiState,
    onIntent: (TournamentConfigIntent) -> Unit,
    onTimerIntent: (TimerIntent) -> Unit
) {
    val isLocked = uiState.isTournamentLocked
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PokerNumberField(
            value = uiState.smallestChip,
            onValueChange = { chip ->
                if (!isLocked) {
                    onIntent(TournamentConfigIntent.UpdateSmallestChip(chip))
                    onTimerIntent(TimerIntent.UpdateSmallestChip(chip))
                }
            },
            label = "Smallest Chip",
            isLocked = isLocked,
            minValue = 1,
            modifier = Modifier.weight(1f)
        )

        PokerNumberField(
            value = uiState.startingChips,
            onValueChange = { chips ->
                if (!isLocked) {
                    onIntent(TournamentConfigIntent.UpdateStartingChips(chips))
                    onTimerIntent(TimerIntent.UpdateStartingChips(chips))
                }
            },
            label = "Starting Chips",
            isLocked = isLocked,
            minValue = 1,
            modifier = Modifier.weight(1f)
        )
    }
}

private const val MAX_DURATION_HOURS = 24
