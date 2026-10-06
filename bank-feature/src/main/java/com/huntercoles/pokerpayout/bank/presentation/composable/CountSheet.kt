package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.bank.R
import com.huntercoles.pokerpayout.bank.presentation.BankSheet
import com.huntercoles.pokerpayout.bank.presentation.Purchase
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerSheet
import com.huntercoles.pokerpayout.core.design.components.PokerStepper
import com.huntercoles.pokerpayout.core.utils.FormatUtils.formatMoney

/** Hold Rebuy or Add-on: the count sheet, as a modal bottom sheet. */
@Composable
internal fun CountSheet(sheet: BankSheet.Count, onSet: (Int) -> Unit, onDismiss: () -> Unit) {
    PokerSheet(onDismissRequest = onDismiss) {
        CountSheetContent(sheet, onSet, onDismiss)
    }
}

/**
 * An exact count of rebuys or add-ons (up to 20), replacing the old dialog's ‹ 2 › carousel with
 * a stepper. It says what one more costs now and what the recorded ones cost (PP-085: each keeps its
 * price). After the cutoff the count can only go down, to take back one recorded by mistake.
 */
@Composable
internal fun CountSheetContent(sheet: BankSheet.Count, onSet: (Int) -> Unit, onDismiss: () -> Unit) {
    var count by rememberSaveable(sheet.playerId, sheet.kind) { mutableIntStateOf(sheet.taken) }
    val rebuys = sheet.kind == Purchase.REBUY
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
    ) {
        Text(
            text = stringResource(
                if (rebuys) R.string.bank_count_title_rebuys else R.string.bank_count_title_add_ons,
                sheet.name
            ),
            style = PokerType.Title.copy(fontSize = 30.sp, lineHeight = 34.sp),
            color = PokerColors.CardWhite,
            modifier = Modifier.semantics { heading() },
        )
        PokerStepper(
            value = count,
            onValueChange = { count = it },
            range = 0..sheet.maxCount,
            label = stringResource(if (rebuys) R.string.bank_count_rebuys else R.string.bank_count_add_ons),
            modifier = Modifier.fillMaxWidth(),
        )
        val closed = sheet.window.closedReason()
        if (closed != null) {
            Note(stringResource(if (rebuys) R.string.bank_count_closed_rebuys else R.string.bank_count_closed_add_ons, closed))
        } else if (sheet.priceCents > 0L) {
            Note(stringResource(R.string.bank_count_price, formatMoney(sheet.priceCents)))
        }
        if (sheet.prices.isNotEmpty()) {
            val groups = sheet.prices.groupBy { it }.map { (price, same) ->
                stringResource(R.string.bank_count_group, same.size, formatMoney(price))
            }
            Note(stringResource(R.string.bank_count_recorded, groups.joinToString(stringResource(R.string.bank_part_join))))
        }
        SheetButtons(
            dismissLabel = stringResource(R.string.bank_cancel),
            onDismiss = onDismiss,
            confirmLabel = pluralStringResource(
                if (rebuys) R.plurals.bank_count_save_rebuys else R.plurals.bank_count_save_add_ons,
                count,
                count,
            ),
            onConfirm = { onSet(count) },
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = PokerColors.Chalk)
}
