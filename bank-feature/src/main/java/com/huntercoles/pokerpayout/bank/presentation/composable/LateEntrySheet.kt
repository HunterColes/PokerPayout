package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.bank.R
import com.huntercoles.pokerpayout.bank.presentation.BankIntent
import com.huntercoles.pokerpayout.bank.presentation.BankSheet
import com.huntercoles.pokerpayout.bank.presentation.MAX_ENTRY_NAME_LENGTH
import com.huntercoles.pokerpayout.bank.presentation.ReEntryCandidate
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerField
import com.huntercoles.pokerpayout.core.design.components.PokerSheet
import com.huntercoles.pokerpayout.core.design.components.leaveOnHardwareEnter
import com.huntercoles.pokerpayout.core.design.icons.MoneyIcons
import com.huntercoles.pokerpayout.core.domain.model.PurchaseWindow
import com.huntercoles.pokerpayout.core.domain.model.ordinalOf
import com.huntercoles.pokerpayout.core.utils.FormatUtils.formatMoney
import java.text.NumberFormat
import java.util.Locale

/** The late entry sheet (S27, PP-116) as a modal bottom sheet. */
@Composable
internal fun LateEntrySheet(
    sheet: BankSheet.LateEntry,
    reEntries: List<ReEntryCandidate>,
    onIntent: (BankIntent) -> Unit,
    onDismiss: () -> Unit,
) {
    PokerSheet(onDismissRequest = onDismiss) {
        LateEntrySheetContent(
            sheet = sheet,
            reEntries = reEntries,
            onAdd = { onIntent(BankIntent.AddLateEntry(it)) },
            onReEnter = { onIntent(BankIntent.ReEnter(it)) },
            onDismiss = onDismiss,
        )
    }
}

/**
 * S27, late entry (PP-116): what a new entry costs and the stack it starts with, until when entries
 * are open, then the late arrival's name and Add, named after what it takes ("Add · $50 paid"). Below,
 * when anyone is out, each of them as a 48 dp choice: one tap re-enters them as a new entry for the
 * same. Either applies at once; Undo follows on the snackbar.
 */
@Composable
@Suppress("LongParameterList") // a sheet: its model, who can re-enter, two actions, dismiss and a test's name
internal fun LateEntrySheetContent(
    sheet: BankSheet.LateEntry,
    reEntries: List<ReEntryCandidate>,
    onAdd: (String) -> Unit,
    onReEnter: (Int) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val focusManager = LocalFocusManager.current
    val price = formatMoney(sheet.price.totalCents)
    val chips = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }.format(sheet.startingChips)
    val add = {
        focusManager.clearFocus()
        onAdd(name)
    }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
    ) {
        Text(
            text = stringResource(R.string.bank_late_title),
            style = PokerType.Title.copy(fontSize = 30.sp, lineHeight = 34.sp),
            color = PokerColors.CardWhite,
            modifier = Modifier.semantics { heading() },
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.bank_late_price, price, chips),
                style = MaterialTheme.typography.bodyLarge,
                color = PokerColors.CardWhite,
            )
            Text(windowLine(sheet.window), style = MaterialTheme.typography.bodyMedium, color = PokerColors.Chalk)
        }
        val description = stringResource(R.string.bank_late_name_description)
        PokerField(
            value = name,
            onValueChange = { name = it.take(MAX_ENTRY_NAME_LENGTH) },
            label = stringResource(R.string.bank_late_name),
            keyboardType = KeyboardType.Text,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardActions = KeyboardActions(onDone = { add() }),
            fieldModifier = Modifier
                .leaveOnHardwareEnter { focusManager.clearFocus(force = true) }
                .semantics { contentDescription = description },
            modifier = Modifier.fillMaxWidth(),
        )
        SheetButtons(
            dismissLabel = stringResource(R.string.bank_cancel),
            onDismiss = onDismiss,
            confirmLabel = stringResource(R.string.bank_late_add, price),
            onConfirm = add,
        )
        if (reEntries.isNotEmpty()) {
            PokerEyebrow(stringResource(R.string.bank_late_re_enter_title), Modifier.padding(top = 4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reEntries.forEach { candidate -> ReEntryChoice(candidate) { onReEnter(candidate.playerId) } }
            }
        }
    }
}

/** "Open until the end of level 4.", or all night with no cutoff. */
@Composable
private fun windowLine(window: PurchaseWindow): String = when (window) {
    is PurchaseWindow.OpenUntilLevel -> stringResource(R.string.bank_late_open_level, window.level)
    else -> stringResource(R.string.bank_late_open_all)
}

/** A player who is out, as one 48 dp choice: their name and place; a tap re-enters them. */
@Composable
internal fun ReEntryChoice(candidate: ReEntryCandidate, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(PokerDimens.CornerControl)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PokerDimens.RowMinHeight)
            .clip(shape)
            .background(PokerColors.DarkGreen)
            .border(1.dp, PokerColors.FeltEdge, shape)
            .clickable(
                onClickLabel = stringResource(R.string.bank_late_re_enter, candidate.name),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(MoneyIcons.Renew, contentDescription = null, tint = PokerColors.PokerGold, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(candidate.name, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp), color = PokerColors.CardWhite)
            candidate.place?.let { place ->
                Text(
                    text = stringResource(R.string.bank_late_out_in, ordinalOf(place)),
                    style = MaterialTheme.typography.bodySmall,
                    color = PokerColors.Chalk,
                )
            }
        }
    }
}
