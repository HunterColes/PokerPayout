package com.huntercoles.pokerpayout.tournament.presentation.payouts

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.LocalWidthClass
import com.huntercoles.pokerpayout.core.design.components.PayoutPreview
import com.huntercoles.pokerpayout.core.design.components.PayoutStructureSheet
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerIconButton
import com.huntercoles.pokerpayout.core.design.components.PokerSegmentedControl
import com.huntercoles.pokerpayout.core.design.components.PokerTopBar
import com.huntercoles.pokerpayout.core.design.components.WidthClass
import com.huntercoles.pokerpayout.core.design.components.presetLabel
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.utils.FormatUtils.formatMoney
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.core.R as CoreR

/**
 * The Payouts tab (S6, D1): [PayoutsContent] over [PayoutsViewModel], with Share as text. "Leave a
 * tip" on the tip card (PP-112) opens the Tip the dealer page through [onOpenTip].
 */
@Composable
fun PayoutsScreen(viewModel: PayoutsViewModel = hiltViewModel(), onOpenTip: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    PayoutsContent(
        state = state,
        onIntent = viewModel::acceptIntent,
        onShare = { sharePayouts(context, PayoutsShareText.build(context, state)) },
        onLeaveTip = {
            viewModel.acceptIntent(PayoutsIntent.LeaveTip)
            onOpenTip()
        },
    )
}

/** Sends [text] to any app that takes plain text (the group chat); needs no permission. */
private fun sharePayouts(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.payouts_share)))
}

/**
 * The Payouts tab, stateless (S6): the pool and where it came from, the structure (presets with
 * what 1st would get, rounding, places), what each place pays with its share of the pool, the
 * bubble, and the bounties. Rows fill in with names as players finish. The structure is locked
 * while the clock runs, and says so. A saved night may have the "Tip the dealer?" card under it
 * (PP-112), whose "Leave a tip" calls [onLeaveTip].
 */
@Composable
fun PayoutsContent(
    state: PayoutsUiState,
    onIntent: (PayoutsIntent) -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    onLeaveTip: () -> Unit = {},
) {
    val gutter = if (LocalWidthClass.current == WidthClass.Small) 12.dp else PokerDimens.Gutter
    Column(modifier = modifier.fillMaxSize()) {
        PokerTopBar(
            title = stringResource(CoreR.string.navigation_payouts),
            subtitle = pluralStringResource(
                R.plurals.payouts_subtitle,
                state.places,
                state.places,
                formatMoney(state.pool.prizePoolCents)
            ),
        ) {
            PokerIconButton(PokerIcons.Share, stringResource(R.string.payouts_share), onClick = onShare)
            PokerIconButton(
                icon = PokerIcons.Edit,
                contentDescription = stringResource(R.string.payouts_edit),
                onClick = { onIntent(PayoutsIntent.ShowStructure) },
                tint = PokerColors.PokerGold,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = gutter, end = gutter, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.isLocked) LockedNote()
            if (state.night != NightSave.NotOver) SaveNightCard(state.night, onIntent)
            if (state.tipCard) TipDealerCard(onLeaveTip, onIntent)
            PoolHero(state)
            StructurePicker(state, onIntent)
            RoundingPicker(state, onIntent)
            PlacesCard(state, onIntent)
            BubbleCard(state.bubble)
            BountiesCard(state.bounties)
        }
    }
    if (state.showStructureSheet) {
        PayoutStructureSheet(
            current = state.settings,
            preview = PayoutPreview(prizePoolCents = state.pool.prizePoolCents, playerCount = state.playerCount),
            onSave = { onIntent(PayoutsIntent.SaveStructure(it)) },
            onDismiss = { onIntent(PayoutsIntent.HideStructure) },
            isLocked = state.isLocked,
        )
    }
}

/** Why the structure controls don't respond: the clock is running. */
@Composable
private fun LockedNote() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(PokerIcons.Lock, contentDescription = null, tint = PokerColors.PokerGold, modifier = Modifier.size(18.dp))
        Text(
            text = stringResource(R.string.payouts_locked_running),
            style = MaterialTheme.typography.bodyMedium,
            color = PokerColors.Chalk,
        )
    }
}

@Composable
private fun PoolHero(state: PayoutsUiState) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
        PokerEyebrow(stringResource(R.string.payouts_prize_pool))
        FittedHero(formatMoney(state.pool.prizePoolCents))
        Text(
            text = PayoutsShareText.poolSources(context, state),
            style = MaterialTheme.typography.bodyMedium,
            color = PokerColors.Chalk,
        )
    }
}

/** The pool in big gold digits: 64 sp, or smaller if a big pool at a large font size wouldn't fit. */
@Composable
private fun FittedHero(text: String) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Both sizes go through the same font scale, so comparing them in sp is fair.
        val fitting = with(LocalDensity.current) { (maxWidth / (text.length * HERO_EM_PER_CHAR)).toSp() }
        val size = if (fitting.value < HeroSize.value) fitting else HeroSize
        Text(
            text = text,
            style = PokerType.DisplayL.copy(fontSize = size, lineHeight = size),
            color = PokerColors.PokerGold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private val HeroSize = 64.sp

/** A generous width per character of Barlow Condensed figures, in ems. */
private const val HERO_EM_PER_CHAR = 0.55f

@Composable
private fun StructurePicker(state: PayoutsUiState, onIntent: (PayoutsIntent) -> Unit) {
    val labels = PayoutPreset.entries.associateWith { presetLabel(it) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.payouts_structure_label),
            style = MaterialTheme.typography.bodySmall,
            color = PokerColors.Chalk
        )
        // Hand-edited weights select no preset.
        PokerSegmentedControl<PayoutPreset?>(
            options = PayoutPreset.entries,
            selected = state.preset,
            onSelect = { preset -> preset?.let { onIntent(PayoutsIntent.SelectPreset(it)) } },
            label = { labels[it].orEmpty() },
            secondary = { preset -> state.firstPlaceByPreset[preset]?.let { formatMoney(it) } },
            enabled = !state.isLocked,
        )
    }
}

@Composable
private fun RoundingPicker(state: PayoutsUiState, onIntent: (PayoutsIntent) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.payouts_round_to),
            style = MaterialTheme.typography.bodyMedium,
            color = PokerColors.Chalk,
            modifier = Modifier.weight(1f),
        )
        PokerSegmentedControl(
            options = PayoutRounding.entries,
            selected = state.settings.rounding,
            onSelect = { onIntent(PayoutsIntent.SelectRounding(it)) },
            label = { it.label },
            enabled = !state.isLocked,
            modifier = Modifier.widthIn(max = 196.dp).fillMaxWidth(),
        )
    }
}
