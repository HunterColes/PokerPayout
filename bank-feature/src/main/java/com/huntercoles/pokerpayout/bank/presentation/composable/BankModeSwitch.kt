package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.huntercoles.pokerpayout.bank.R
import com.huntercoles.pokerpayout.core.design.components.PokerSegmentedControl
import com.huntercoles.pokerpayout.core.domain.cash.BankMode

/**
 * The Bank's Tournament / Cash game switch (D4, S13), at the top of either game's screen. It only
 * changes which game the Bank shows: both keep their state, and the choice is remembered.
 */
@Composable
fun BankModeSwitch(mode: BankMode, onSwitch: (BankMode) -> Unit, modifier: Modifier = Modifier) {
    val tournament = stringResource(R.string.cash_mode_tournament)
    val cash = stringResource(R.string.cash_mode_cash)
    PokerSegmentedControl(
        options = BankMode.entries,
        selected = mode,
        onSelect = onSwitch,
        label = { if (it == BankMode.CASH) cash else tournament },
        modifier = modifier,
    )
}
