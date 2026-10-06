package com.huntercoles.pokerpayout.tournament.presentation.payouts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.components.PokerTopBar
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigIntent
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigUiState
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigViewModel
import com.huntercoles.pokerpayout.tournament.presentation.composable.PayoutDialogs
import com.huntercoles.pokerpayout.tournament.presentation.composable.PayoutsPanel
import com.huntercoles.pokerpayout.core.R as CoreR

/**
 * The Payouts tab (D1): the payout table from the v1.3.0 money work, with its presets and the
 * structure editor, now one tap away. It runs on the same [TournamentConfigViewModel] as the
 * Tournament tab, so both follow the same saved settings and Bank results. M4 restyles it (S6).
 */
@Composable
fun PayoutsScreen(viewModel: TournamentConfigViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PayoutsContent(uiState = uiState, onIntent = viewModel::acceptIntent)
}

/** Stateless Payouts tab: [PayoutsPanel] as it is in the Tournament tab, under the tab's top bar. */
@Composable
fun PayoutsContent(
    uiState: TournamentConfigUiState,
    onIntent: (TournamentConfigIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        PokerTopBar(
            title = stringResource(CoreR.string.navigation_payouts),
            // The presets and "Pay N" go quiet once the clock starts; say why.
            subtitle = if (uiState.isTournamentLocked) stringResource(R.string.payouts_locked) else null,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            PayoutDialogs(uiState = uiState, onIntent = onIntent)
            // The same container the panel has in the Tournament tab's configuration card
            @Suppress("DEPRECATION")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = PokerColors.SurfaceSecondary.copy(alpha = 0.4f),
            ) {
                PayoutsPanel(uiState, onIntent)
            }
        }
    }
}
