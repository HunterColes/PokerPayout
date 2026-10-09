package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.bank.R
import com.huntercoles.pokerpayout.bank.presentation.BankIntent
import com.huntercoles.pokerpayout.bank.presentation.BankSheet
import com.huntercoles.pokerpayout.bank.presentation.BankUiState
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.KnockoutBadge
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerIconButton
import com.huntercoles.pokerpayout.core.design.components.PokerPill
import com.huntercoles.pokerpayout.core.design.components.PokerPillTone
import com.huntercoles.pokerpayout.core.design.icons.MoneyIcons
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.domain.model.ordinalOf
import com.huntercoles.pokerpayout.core.utils.FormatUtils
import java.text.NumberFormat
import java.util.Locale

/**
 * The quick knockout (PP-135) over the full-screen clock: a dim the clock still reads through (a tap
 * on it puts the knockout away), and the panel. Sideways the panel is a side sheet over the blinds,
 * so the time stays in view; upright (a tablet that won't turn) it is a bottom sheet.
 *
 * The panel follows the Bank's sheet: none is "Who's out?", [BankSheet.Knockout] (opened by picking
 * who is out) is "Who knocked them out?", [BankSheet.Envelope] is a mystery knockout's envelope, and
 * [BankSheet.LateEntry] (Re-entry, PP-116) is "Who's back in?".
 * A pick applies at once ([BankIntent.KnockOut]); Undo on the snackbar is the way back.
 */
@Composable
internal fun QuickKnockoutOverlay(
    state: BankUiState,
    onIntent: (BankIntent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val sideways = maxWidth > maxHeight
        Box(
            Modifier
                .fillMaxSize()
                .background(PokerColors.PokerBlack.copy(alpha = SCRIM_ALPHA))
                .pointerInput(onClose) { detectTapGestures { onClose() } },
        )
        val shape = if (sideways) SideSheetShape else BottomSheetShape
        val placed = if (sideways) {
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width((maxWidth * PANEL_SHARE).coerceIn(PanelMinWidth, PanelMaxWidth).coerceAtMost(maxWidth))
        } else {
            Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = PanelMaxWidth)
                .fillMaxWidth()
                .heightIn(max = maxHeight * PANEL_MAX_HEIGHT_UPRIGHT)
        }
        val cutout = if (sideways) WindowInsetsSides.End + WindowInsetsSides.Vertical else WindowInsetsSides.Horizontal
        Column(
            modifier = placed
                // Not clipped: the panel takes taps as the rectangle it is laid out in, and its
                // content keeps clear of the rounded corners anyway
                .shadow(PokerDimens.ElevationOverlay, shape, clip = false)
                .background(PokerColors.FeltGreen, shape)
                // A tap on the panel between its buttons stays on it, rather than reaching the dim
                .pointerInput(Unit) { detectTapGestures {} }
                .windowInsetsPadding(WindowInsets.displayCutout.only(cutout))
                .padding(start = PokerDimens.Gutter, end = PokerDimens.Gutter, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingSmall),
        ) {
            when (val sheet = state.sheet) {
                is BankSheet.Knockout -> WhoKnockedOut(sheet, onIntent, onClose)
                is BankSheet.Envelope -> EnvelopeSheetContent(sheet, onDismiss = { onIntent(BankIntent.DismissSheet) })
                is BankSheet.LateEntry -> WhoIsBackIn(sheet, state, onIntent, onClose)
                else -> WhoIsOut(state, onIntent, onClose)
            }
        }
    }
}

/** "Who's out?": every player still in, in seat order. A pick asks who did it. */
@Composable
private fun ColumnScope.WhoIsOut(state: BankUiState, onIntent: (BankIntent) -> Unit, onClose: () -> Unit) {
    PanelHeader(stringResource(R.string.quick_ko_who), onClose = onClose)
    val choices = state.players.filter { !it.out }.map { player ->
        PanelChoice(
            playerId = player.id,
            label = player.name,
            knockouts = state.knockoutCounts[player.id] ?: 0,
            clickLabel = stringResource(R.string.bank_ko_confirm, player.name),
        )
    }
    ChoiceList(Modifier.weight(1f, fill = false)) { perRow ->
        ChoiceGrid(choices, perRow) { id -> id?.let { onIntent(BankIntent.OpenKnockout(it)) } }
    }
    // PP-116: someone who is out buying back in, while late entry is open
    if (state.reEntries.isNotEmpty()) {
        ChoiceTile(
            choice = PanelChoice(
                playerId = null,
                label = stringResource(R.string.quick_ko_re_entry),
                muted = true,
                icon = MoneyIcons.Renew,
            ),
            onClick = { onIntent(BankIntent.OpenLateEntry) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * "Who's back in?" (PP-116): the players who are out, as on the Bank's late entry sheet, with what a
 * re-entry costs and the stack it gets. A pick re-enters them at once ([BankIntent.ReEnter]); Undo on
 * the snackbar is the way back. A late arrival's name is typed in the Bank.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.WhoIsBackIn(
    sheet: BankSheet.LateEntry,
    state: BankUiState,
    onIntent: (BankIntent) -> Unit,
    onClose: () -> Unit,
) {
    PanelHeader(
        title = stringResource(R.string.quick_ko_back_in),
        onClose = onClose,
        onBack = { onIntent(BankIntent.DismissSheet) },
    )
    val chips = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }.format(sheet.startingChips)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val price = FormatUtils.formatMoney(sheet.price.totalCents)
        PokerPill(stringResource(R.string.bank_late_price, price, chips), tone = PokerPillTone.Gold)
    }
    val choices = state.reEntries.map { candidate ->
        PanelChoice(
            playerId = candidate.playerId,
            label = candidate.name,
            detail = candidate.place?.let { stringResource(R.string.bank_late_out_in, ordinalOf(it)) },
            clickLabel = stringResource(R.string.bank_late_re_enter, candidate.name),
        )
    }
    ChoiceList(Modifier.weight(1f, fill = false)) { perRow ->
        ChoiceGrid(choices, perRow) { id -> id?.let { onIntent(BankIntent.ReEnter(it)) } }
    }
}

/**
 * "Who knocked Rita out?": the place and the bounty at stake, then the players still in (with the
 * bounty on each head in a progressive game), those already out, and Nobody, always in view: the
 * knockout sheet's choices (S5b). Whoever is picked is credited at once, with no confirm.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.WhoKnockedOut(sheet: BankSheet.Knockout, onIntent: (BankIntent) -> Unit, onClose: () -> Unit) {
    val credit = { eliminatorId: Int? -> onIntent(BankIntent.KnockOut(sheet.playerId, eliminatorId)) }
    PanelHeader(
        title = stringResource(R.string.quick_ko_by, sheet.name),
        onClose = onClose,
        onBack = { onIntent(BankIntent.DismissSheet) },
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PokerPill(stringResource(R.string.bank_ko_place, ordinalOf(sheet.place)), tone = PokerPillTone.Danger)
        bountyPill(sheet)?.let { PokerPill(it, tone = PokerPillTone.Gold) }
    }
    val progressive = sheet.mode == BountyMode.PROGRESSIVE && sheet.hasBounty
    val choices = sheet.candidates.map { candidate ->
        PanelChoice(
            playerId = candidate.playerId,
            label = candidate.name,
            knockouts = candidate.knockouts,
            detail = if (progressive && !candidate.isOut) {
                stringResource(R.string.bank_ko_candidate_bounty, FormatUtils.formatMoney(candidate.bountyCents))
            } else {
                null
            },
            muted = candidate.isOut,
        )
    }
    ChoiceList(Modifier.weight(1f, fill = false)) { perRow ->
        ChoiceGrid(choices.filterNot { it.muted }, perRow, credit)
        val out = choices.filter { it.muted }
        if (out.isNotEmpty()) {
            PokerEyebrow(stringResource(R.string.bank_ko_already_out), Modifier.padding(top = 4.dp))
            ChoiceGrid(out, perRow, credit)
        }
    }
    ChoiceTile(
        choice = PanelChoice(playerId = null, label = nobodyLabel(sheet), muted = true),
        onClick = { credit(null) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The bounty at stake, as a pill: "$5 bounty", or in a mystery game the envelopes left. */
@Composable
private fun bountyPill(sheet: BankSheet.Knockout): String? = when {
    !sheet.hasBounty -> null
    sheet.mode == BountyMode.MYSTERY ->
        pluralStringResource(R.plurals.quick_ko_envelopes, sheet.envelopesLeft, sheet.envelopesLeft)
    else -> stringResource(R.string.quick_ko_bounty, FormatUtils.formatMoney(sheet.bountyCents))
}

/** The panel's question as its heading, with Back (on the second question) and Close. */
@Composable
private fun PanelHeader(title: String, onClose: () -> Unit, onBack: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            PokerIconButton(PokerIcons.Back, stringResource(R.string.quick_ko_back), onBack, tint = PokerColors.CardWhite)
        }
        Text(
            text = title,
            style = PokerType.Title.copy(fontSize = 22.sp, lineHeight = 26.sp),
            color = PokerColors.CardWhite,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
                .semantics { heading() },
        )
        PokerIconButton(PokerIcons.Close, stringResource(R.string.quick_ko_close), onClose)
    }
}

/** One choice: a player (or Nobody, with no id), their knockouts so far and an optional second line. */
@Suppress("LongParameterList") // one choice: who, its words, knockouts, a second line, tone, TalkBack's action, icon
private class PanelChoice(
    val playerId: Int?,
    val label: String,
    val knockouts: Int = 0,
    val detail: String? = null,
    val muted: Boolean = false,
    val clickLabel: String? = null,
    icon: ImageVector? = null,
) {
    /** Nobody shows a person; a player's name stands alone; Re-entry shows its own [icon]. */
    val icon: ImageVector? = icon ?: if (playerId == null) PokerIcons.Person else null
}

/**
 * The choices, scrolling when they don't fit: two to a row where the panel is wide enough at the
 * system's text size, one otherwise (as in the knockout sheet).
 */
@Composable
private fun ChoiceList(modifier: Modifier, content: @Composable ColumnScope.(perRow: Int) -> Unit) {
    BoxWithConstraints(modifier) {
        val perRow = if (maxWidth < TwoPerRowFrom || LocalDensity.current.fontScale > ONE_PER_ROW_ABOVE) 1 else 2
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content(perRow)
        }
    }
}

@Composable
private fun ChoiceGrid(choices: List<PanelChoice>, perRow: Int, onPick: (Int?) -> Unit) {
    choices.chunked(perRow).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { choice ->
                ChoiceTile(choice, onClick = { onPick(choice.playerId) }, modifier = Modifier.weight(1f))
            }
            repeat(perRow - row.size) { Box(Modifier.weight(1f)) }
        }
    }
}

/** A big target for a busy table: 56 dp tall, the name in full (it wraps, never cuts off). */
@Composable
private fun ChoiceTile(choice: PanelChoice, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(PokerDimens.CornerControl)
    Row(
        modifier = modifier
            .heightIn(min = PokerDimens.RowMinHeight)
            .clip(shape)
            .background(if (choice.muted) PokerColors.FeltDeep else PokerColors.DarkGreen)
            .border(1.dp, PokerColors.FeltEdge, shape)
            .clickable(onClickLabel = choice.clickLabel, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val color = if (choice.muted) PokerColors.Chalk else PokerColors.CardWhite
        choice.icon?.let { Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f)) {
            Text(text = choice.label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp), color = color)
            choice.detail?.let { Text(text = it, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk) }
        }
        if (choice.knockouts > 0) KnockoutBadge(count = choice.knockouts)
    }
}

/** The clock reads through the dim. */
private const val SCRIM_ALPHA = 0.55f

/** Sideways, the panel takes about half the width (the blinds' side), within these bounds. */
private const val PANEL_SHARE = 0.5f
private val PanelMinWidth = 300.dp
private val PanelMaxWidth = 460.dp

/** Upright, the panel is a bottom sheet at most this share of the height. */
private const val PANEL_MAX_HEIGHT_UPRIGHT = 0.8f

/** Two choices to a row from this width, at up to this text size. */
private val TwoPerRowFrom = 260.dp
private const val ONE_PER_ROW_ABOVE = 1.15f

private val SideSheetShape = RoundedCornerShape(topStart = PokerDimens.CornerSheet, bottomStart = PokerDimens.CornerSheet)
private val BottomSheetShape = RoundedCornerShape(topStart = PokerDimens.CornerSheet, topEnd = PokerDimens.CornerSheet)
