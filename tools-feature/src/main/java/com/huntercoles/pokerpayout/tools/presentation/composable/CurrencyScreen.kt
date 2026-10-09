package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerTopBar
import com.huntercoles.pokerpayout.core.utils.AppCurrency
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.CurrencyIntent
import com.huntercoles.pokerpayout.tools.presentation.CurrencyUiState
import com.huntercoles.pokerpayout.tools.presentation.CurrencyViewModel

/** The Currency route (Tools > Currency). */
@Composable
fun CurrencyRoute(onBack: () -> Unit, viewModel: CurrencyViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CurrencyContent(state = state, onIntent = viewModel::acceptIntent, onBack = onBack)
}

/**
 * Currency (S25, PP-114), stateless: each currency by name with a sample amount written its way
 * ("123.456,50 €", "₹1,23,456.50"), the one picked marked, one tap to pick another. Then what a pick
 * changes: only the look, never what is saved.
 */
@Composable
fun CurrencyContent(
    state: CurrencyUiState,
    onIntent: (CurrencyIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(PokerColors.PokerBlack)) {
        PokerTopBar(
            title = stringResource(R.string.currency_title),
            subtitle = stringResource(R.string.currency_subtitle),
            onBack = onBack,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = PokerDimens.Gutter, end = PokerDimens.Gutter, top = 4.dp, bottom = PokerDimens.Gutter),
            verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(PokerDimens.CornerCard))
                    .background(PokerColors.FeltGreen)
                    .padding(horizontal = PokerDimens.SpacingMedium, vertical = PokerDimens.SpacingSmall)
                    .selectableGroup(),
            ) {
                state.choices.forEachIndexed { index, currency ->
                    if (index > 0) HorizontalDivider(color = PokerColors.FeltLine)
                    CurrencyRow(
                        currency = currency,
                        picked = currency == state.picked,
                        onPick = { onIntent(CurrencyIntent.Pick(currency)) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.currency_note),
                style = MaterialTheme.typography.bodySmall,
                color = PokerColors.Chalk,
            )
        }
    }
}

/** One currency: the radio, its name, and the sample written its way. The whole row picks it. */
@Composable
private fun CurrencyRow(currency: AppCurrency, picked: Boolean, onPick: () -> Unit) {
    val note = currencyNote(currency)?.let { stringResource(it) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .selectable(selected = picked, role = Role.RadioButton, onClick = onPick)
            .padding(vertical = PokerDimens.SpacingSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
    ) {
        RadioButton(
            selected = picked,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = PokerColors.PokerGold, unselectedColor = PokerColors.Chalk),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(currencyName(currency)),
                style = MaterialTheme.typography.bodyLarge,
                color = if (picked) PokerColors.PokerGold else PokerColors.CardWhite,
            )
            Text(
                text = currency.format(AppCurrency.SAMPLE_CENTS, alwaysCents = true),
                style = SampleStyle,
                color = PokerColors.CardWhite,
            )
            if (note != null) Text(text = note, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
        }
    }
}

/** The currency's name ("Euro"), for this screen and the Tools row. */
@StringRes
internal fun currencyName(currency: AppCurrency): Int = when (currency) {
    AppCurrency.DOLLAR -> R.string.currency_dollar
    AppCurrency.EURO -> R.string.currency_euro
    AppCurrency.EURO_FIRST -> R.string.currency_euro_first
    AppCurrency.POUND -> R.string.currency_pound
    AppCurrency.RUPEE -> R.string.currency_rupee
    AppCurrency.REAL -> R.string.currency_real
    AppCurrency.KRONA -> R.string.currency_krona
    AppCurrency.YEN -> R.string.currency_yen
    AppCurrency.YUAN -> R.string.currency_yuan
    AppCurrency.FRANC -> R.string.currency_franc
    AppCurrency.ZLOTY -> R.string.currency_zloty
    AppCurrency.RUBLE -> R.string.currency_ruble
    AppCurrency.NONE -> R.string.currency_none
}

/** A line under the sample where the currency needs one: the yen has no cents, "No symbol" is for points. */
@StringRes
private fun currencyNote(currency: AppCurrency): Int? = when {
    currency == AppCurrency.NONE -> R.string.currency_plain
    !currency.hasCents -> R.string.currency_whole_only
    else -> null
}

private val RowMinHeight = 56.dp

/** The sample amount in the number face, a little smaller than a stat. */
private val SampleStyle: TextStyle = PokerType.NumberM.copy(fontSize = 16.sp, lineHeight = 20.sp)
