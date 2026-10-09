package com.huntercoles.pokerpayout.tournament.presentation.payouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tournament.R

/**
 * PP-112: "Tip the dealer?", under a saved night, at most twice ever and never while the clock runs
 * (the rules are in `TipJar`). A card in the page, not a dialog: nothing is in the way. It says what
 * the app is and what a tip does, then three plain answers of the same small size: Leave a tip (the
 * Tip the dealer page), Not now, and Don't ask again, which ends the asks with one tap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TipDealerCard(onLeaveTip: () -> Unit, onIntent: (PayoutsIntent) -> Unit) {
    PayoutsCard {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(PokerIcons.Heart, contentDescription = null, tint = PokerColors.PokerGold, modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(R.string.payouts_tip_title),
                style = PokerType.Title,
                color = PokerColors.CardWhite,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        Text(
            text = stringResource(R.string.payouts_tip_body),
            style = MaterialTheme.typography.bodyMedium,
            color = PokerColors.Chalk,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PokerButton(
                text = stringResource(R.string.payouts_tip_leave),
                onClick = onLeaveTip,
                variant = PokerButtonVariant.Secondary,
                size = PokerButtonSize.Small,
            )
            PokerButton(
                text = stringResource(R.string.payouts_tip_not_now),
                onClick = { onIntent(PayoutsIntent.TipNotNow) },
                variant = PokerButtonVariant.Text,
                size = PokerButtonSize.Small,
            )
            PokerButton(
                text = stringResource(R.string.payouts_tip_never),
                onClick = { onIntent(PayoutsIntent.TipNever) },
                variant = PokerButtonVariant.Text,
                size = PokerButtonSize.Small,
            )
        }
    }
}
