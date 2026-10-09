package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerIconButton
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.domain.presets.PresetSetup
import com.huntercoles.pokerpayout.tournament.domain.presets.StarterSetup
import com.huntercoles.pokerpayout.tournament.presentation.presets.PresetsIntent
import com.huntercoles.pokerpayout.tournament.presentation.presets.PresetsUiState

/** The starter nights (PP-113), fitted to tonight, under their own heading; none listed, nothing shown. */
@Composable
internal fun StartersList(state: PresetsUiState, onIntent: (PresetsIntent) -> Unit) {
    if (state.starters.isEmpty()) return
    PokerEyebrow(stringResource(R.string.presets_starters))
    Column {
        state.starters.forEachIndexed { index, starter ->
            if (index > 0) HorizontalDivider(color = PokerColors.FeltLine)
            StarterItem(starter, state.canLoad, onIntent)
        }
    }
}

/** One starter: its name and what it holds tonight. A tap loads it; ⋮ copies it into the saved presets. */
@Composable
private fun StarterItem(starter: StarterSetup, canLoad: Boolean, onIntent: (PresetsIntent) -> Unit) {
    val name = stringResource(starter.starter.title)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = PokerDimens.RowMinHeight)
                .clip(RoundedCornerShape(PokerDimens.CornerControl))
                .clickable(
                    enabled = canLoad,
                    onClickLabel = stringResource(R.string.presets_load),
                    role = Role.Button,
                    onClick = { onIntent(PresetsIntent.LoadStarter(starter.starter)) },
                )
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = if (canLoad) PokerColors.CardWhite else PokerColors.Chalk,
            )
            Text(starterSummary(starter.setup), style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
        }
        StarterMenu(starter, name, onIntent)
    }
}

/** ⋮ on a starter: Copy to my presets (with Undo). Works mid-game too: it doesn't touch the game. */
@Composable
private fun StarterMenu(starter: StarterSetup, name: String, onIntent: (PresetsIntent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        PokerIconButton(
            icon = PokerIcons.More,
            contentDescription = stringResource(R.string.presets_starter_more, name),
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = PokerColors.DarkGreen) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.presets_copy_starter), color = PokerColors.CardWhite) },
                onClick = {
                    open = false
                    onIntent(PresetsIntent.CopyStarter(starter.starter))
                },
            )
        }
    }
}

/** "3 h · 20-min levels · 5,000 chips · $20 buy-in · $5 bounty", with "ante from L7" when it has one. */
@Composable
private fun starterSummary(setup: PresetSetup): String {
    val formatter = rememberChipFormatter()
    val blinds = setup.blinds
    return listOfNotNull(
        stringResource(R.string.strip_hours, blinds.durationMinutes / MINUTES_PER_HOUR),
        stringResource(R.string.strip_level_length, blinds.roundLengthMinutes),
        stringResource(R.string.strip_chips, formatter.format(blinds.startingChips)),
        blinds.anteFromLevel.takeIf { it > 0 }?.let { stringResource(R.string.strip_ante_from, it) },
        stringResource(R.string.strip_buy_in, money(setup.money.buyInCents)),
        setup.money.bountyCents.takeIf { it > 0L }?.let { stringResource(R.string.strip_bounty, money(it)) },
    ).joinToString(stringResource(R.string.strip_separator))
}

private const val MINUTES_PER_HOUR = 60
