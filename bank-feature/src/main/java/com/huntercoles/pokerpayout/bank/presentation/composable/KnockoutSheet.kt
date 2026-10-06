package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.bank.R
import com.huntercoles.pokerpayout.bank.presentation.BankSheet
import com.huntercoles.pokerpayout.bank.presentation.KnockoutCandidate
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.KnockoutBadge
import com.huntercoles.pokerpayout.core.design.components.LocalWidthClass
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerPill
import com.huntercoles.pokerpayout.core.design.components.PokerPillTone
import com.huntercoles.pokerpayout.core.design.components.PokerSheet
import com.huntercoles.pokerpayout.core.design.components.WidthClass
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.domain.model.ordinalOf
import com.huntercoles.pokerpayout.core.utils.FormatUtils

/** "Nobody" in the pick list: the knockout goes uncredited and the bounty to the champion. */
private const val NOBODY = -1

/** The knockout sheet (S5b) as a modal bottom sheet. */
@Composable
internal fun KnockoutSheet(sheet: BankSheet.Knockout, onKnockOut: (Int?) -> Unit, onDismiss: () -> Unit) {
    PokerSheet(onDismissRequest = onDismiss) {
        KnockoutSheetContent(sheet, onKnockOut, onDismiss)
    }
}

/**
 * Who knocked [BankSheet.Knockout.name] out: one question, every answer visible. The sheet says the
 * outcome first (the place, and where the bounty goes), then every player still in as a 48 dp
 * choice (with their knockouts so far), the players already out, and "Nobody". The confirm is named
 * after the action and applies at once; Undo follows on the snackbar.
 */
@Composable
internal fun KnockoutSheetContent(
    sheet: BankSheet.Knockout,
    onKnockOut: (Int?) -> Unit,
    onDismiss: () -> Unit,
    initialChoice: Int? = sheet.preselectedId,
) {
    var choice by rememberSaveable(sheet.playerId) { mutableStateOf(initialChoice) }
    val hasBounty = sheet.bountyCents > 0L
    val perRow = if (LocalWidthClass.current == WidthClass.Small || LocalDensity.current.fontScale > ONE_PER_ROW_ABOVE) 1 else 2
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
    ) {
        SheetHeading(
            title = stringResource(R.string.bank_ko_title, sheet.name),
            pill = { PokerPill(stringResource(R.string.bank_ko_place, ordinalOf(sheet.place)), tone = PokerPillTone.Danger) },
        )
        Text(
            text = if (hasBounty) {
                stringResource(R.string.bank_ko_bounty, sheet.name, FormatUtils.formatMoney(sheet.bountyCents))
            } else {
                stringResource(R.string.bank_ko_no_bounty, sheet.name)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = PokerColors.Chalk,
        )
        PokerEyebrow(stringResource(R.string.bank_ko_by))
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceGrid(sheet.candidates.filterNot { it.isOut }, choice, perRow) { choice = it }
            val out = sheet.candidates.filter { it.isOut }
            if (out.isNotEmpty()) {
                PokerEyebrow(stringResource(R.string.bank_ko_already_out), Modifier.padding(top = 4.dp))
                ChoiceGrid(out, choice, perRow) { choice = it }
            }
            Choice(
                label = stringResource(if (hasBounty) R.string.bank_ko_nobody_bounty else R.string.bank_ko_nobody),
                selected = choice == NOBODY,
                icon = PokerIcons.Person,
                muted = true,
                onClick = { choice = NOBODY },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SheetButtons(
            dismissLabel = stringResource(R.string.bank_cancel),
            onDismiss = onDismiss,
            confirmLabel = stringResource(R.string.bank_ko_confirm, sheet.name),
            confirmEnabled = choice != null,
            onConfirm = { choice?.let { onKnockOut(it.takeIf { id -> id != NOBODY }) } },
        )
    }
}

private const val ONE_PER_ROW_ABOVE = 1.15f

/** The confirm button takes a little more of the row than the dismiss one. */
private const val CONFIRM_WEIGHT = 1.4f

/** [perRow] choices to a row. */
@Composable
private fun ChoiceGrid(candidates: List<KnockoutCandidate>, choice: Int?, perRow: Int, onChoose: (Int) -> Unit) {
    candidates.chunked(perRow).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { candidate ->
                Choice(
                    label = candidate.name,
                    selected = choice == candidate.playerId,
                    knockouts = candidate.knockouts,
                    onClick = { onChoose(candidate.playerId) },
                    modifier = Modifier.weight(1f),
                )
            }
            repeat(perRow - pair.size) { Row(Modifier.weight(1f)) {} }
        }
    }
}

@Suppress("LongParameterList") // one choice: its words, state, icon, knockouts and tap
@Composable
private fun Choice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    knockouts: Int = 0,
    muted: Boolean = false,
) {
    val shape = RoundedCornerShape(PokerDimens.CornerControl)
    Row(
        modifier = modifier
            .heightIn(min = PokerDimens.MinTouch)
            .clip(shape)
            .background(if (selected) PokerColors.FeltHigh else PokerColors.DarkGreen)
            .border(if (selected) 2.dp else 1.dp, if (selected) PokerColors.PokerGold else PokerColors.FeltEdge, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val color = when {
            selected -> PokerColors.PokerGold
            muted -> PokerColors.Chalk
            else -> PokerColors.CardWhite
        }
        when {
            selected -> Icon(PokerIcons.Check, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            icon != null -> Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            color = color,
            modifier = Modifier.weight(1f)
        )
        if (knockouts > 0) KnockoutBadge(count = knockouts)
    }
}

/** A sheet's big title with a pill on the right, as in S5b and S5c. */
@Composable
internal fun SheetHeading(title: String, pill: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = PokerType.Title.copy(fontSize = 30.sp, lineHeight = 34.sp),
            color = PokerColors.CardWhite,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        pill()
    }
}

/** Dismiss on the left as a text button, confirm on the right, named after what it does. */
@Suppress("LongParameterList") // two buttons: labels, actions and whether confirm is ready
@Composable
internal fun SheetButtons(
    dismissLabel: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    confirmVariant: PokerButtonVariant = PokerButtonVariant.Primary,
    confirmIcon: ImageVector? = null,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        PokerButton(
            text = dismissLabel,
            onClick = onDismiss,
            variant = PokerButtonVariant.Text,
            size = PokerButtonSize.Small,
            modifier = Modifier.weight(1f),
        )
        PokerButton(
            text = confirmLabel,
            onClick = onConfirm,
            enabled = confirmEnabled,
            variant = confirmVariant,
            icon = confirmIcon,
            size = PokerButtonSize.Small,
            modifier = Modifier.weight(CONFIRM_WEIGHT),
        )
    }
}
