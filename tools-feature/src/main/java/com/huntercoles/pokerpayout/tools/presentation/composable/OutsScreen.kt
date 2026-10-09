package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerSegmentedControl
import com.huntercoles.pokerpayout.core.design.components.PokerStepper
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.OutsIntent
import com.huntercoles.pokerpayout.tools.presentation.OutsUiState
import com.huntercoles.pokerpayout.tools.presentation.OutsViewModel
import com.huntercoles.pokerpayout.tools.table.OutsMath
import com.huntercoles.pokerpayout.tools.table.Street

/** The outs route ("Outs & pot odds" in the Tools list). */
@Composable
fun OutsRoute(onBack: () -> Unit, viewModel: OutsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OutsContent(state = state, onIntent = viewModel::acceptIntent, onBack = onBack)
}

/**
 * Outs & pot odds (S20): the street and your outs (or a common draw), then the exact chance to hit
 * with the rule of 2 and 4 next to it, then the pot odds: the share of the pot a call needs, and
 * whether your chance covers it.
 */
@Composable
fun OutsContent(
    state: OutsUiState,
    onIntent: (OutsIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtitle = stringResource(
        R.string.outs_subtitle,
        pluralStringResource(R.plurals.outs_count, state.outs, state.outs),
        stringResource(if (state.street == Street.Flop) R.string.outs_on_flop else R.string.outs_on_turn),
    )
    TableToolPage(
        title = stringResource(R.string.outs_title),
        subtitle = subtitle,
        onBack = onBack,
        modifier = modifier,
        inputs = {
            YourOutsCard(state, onIntent)
            PotOddsCard(state, onIntent)
        },
        results = { ChanceCard(state) },
    )
}

/** A draw people name, and its outs. */
private class CommonDraw(@StringRes val name: Int, val outs: Int)

private val CommonDraws = listOf(
    CommonDraw(R.string.outs_draw_flush, outs = 9),
    CommonDraw(R.string.outs_draw_open_ended, outs = 8),
    CommonDraw(R.string.outs_draw_gutshot, outs = 4),
    CommonDraw(R.string.outs_draw_overcards, outs = 6),
    CommonDraw(R.string.outs_draw_flush_open_ended, outs = 15),
    CommonDraw(R.string.outs_draw_set, outs = 2),
)

/** Flop or turn, the outs on a stepper, and the common draws as one-tap shortcuts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YourOutsCard(state: OutsUiState, onIntent: (OutsIntent) -> Unit) {
    val flop = stringResource(R.string.outs_street_flop)
    val turn = stringResource(R.string.outs_street_turn)
    val outsLabel = stringResource(R.string.outs_outs)
    ChipSetSection {
        SectionHeader(title = stringResource(R.string.outs_your_outs), note = null)
        PokerSegmentedControl(
            options = Street.entries,
            selected = state.street,
            onSelect = { onIntent(OutsIntent.SetStreet(it)) },
            label = { if (it == Street.Flop) flop else turn },
            modifier = Modifier.fillMaxWidth(),
        )
        SideBySideOrStacked(
            modifier = Modifier.fillMaxWidth(),
            first = { Text(text = outsLabel, style = MaterialTheme.typography.titleSmall, color = PokerColors.CardWhite) },
            second = {
                PokerStepper(
                    value = state.outs,
                    onValueChange = { onIntent(OutsIntent.SetOuts(it)) },
                    range = OutsMath.MIN_OUTS..OutsMath.MAX_OUTS,
                    label = outsLabel,
                    modifier = Modifier.widthIn(min = StepperWidth),
                )
            },
        )
        Text(text = stringResource(R.string.outs_common), style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CommonDraws.forEach { draw ->
                val name = stringResource(draw.name)
                val outs = pluralStringResource(R.plurals.outs_count, draw.outs, draw.outs)
                val spoken = stringResource(R.string.outs_draw_spoken, name, outs)
                PokerButton(
                    text = stringResource(R.string.outs_draw_button, name, draw.outs),
                    onClick = { onIntent(OutsIntent.SetOuts(draw.outs)) },
                    variant = PokerButtonVariant.Secondary,
                    size = PokerButtonSize.Small,
                    modifier = Modifier.semantics { contentDescription = spoken },
                )
            }
        }
    }
}

/** Wide enough for two-digit outs without the number squeezing the buttons. */
private val StepperWidth = 148.dp
