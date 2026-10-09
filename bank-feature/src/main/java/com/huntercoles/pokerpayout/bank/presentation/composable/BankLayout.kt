package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.bank.R
import com.huntercoles.pokerpayout.bank.presentation.BankCell
import com.huntercoles.pokerpayout.bank.presentation.BankColumn
import com.huntercoles.pokerpayout.bank.presentation.BankRowModel
import com.huntercoles.pokerpayout.bank.presentation.BankUiState
import com.huntercoles.pokerpayout.bank.presentation.CellStatus
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.WidthClass
import com.huntercoles.pokerpayout.core.domain.model.PurchaseWindow
import com.huntercoles.pokerpayout.core.domain.model.ordinalOf
import com.huntercoles.pokerpayout.core.utils.FormatUtils

/**
 * How the Bank lays its columns out at this width and font size (S5 v2, Z2, Z5):
 * - [columns] are the cells each row shows. Rebuy and Add-on hide when they cost $0. On a small
 *   phone (under 360 dp) a closed Rebuy or Add-on moves into the line under the name ([collapsed]);
 *   open columns never move.
 * - [cellWidth] is 48 dp, or 64 dp on a tablet, which also shows each player's In and Owed amounts.
 * - Above a 1.3 font scale the header drops its words and keeps the icons ([iconsOnly]); each cell
 *   still says its column to TalkBack.
 */
internal data class BankLayout(
    val columns: List<BankColumn>,
    val collapsed: List<BankColumn>,
    val cellWidth: Dp,
    val showAmounts: Boolean,
    val iconsOnly: Boolean,
    val gutter: Dp
) {
    companion object {
        @Composable
        fun of(state: BankUiState, widthClass: WidthClass): BankLayout {
            val shown = BankColumn.entries.filter { column ->
                when (column) {
                    BankColumn.REBUY -> state.isRebuyEnabled
                    BankColumn.ADD_ON -> state.isAddOnEnabled
                    else -> true
                }
            }
            val collapsed = if (widthClass == WidthClass.Small) {
                shown.filter { column ->
                    (column == BankColumn.REBUY && !state.rebuyWindow.isOpen) ||
                        (column == BankColumn.ADD_ON && !state.addOnWindow.isOpen)
                }
            } else {
                emptyList()
            }
            val expanded = widthClass == WidthClass.Expanded
            return BankLayout(
                columns = shown - collapsed.toSet(),
                collapsed = collapsed,
                cellWidth = if (expanded) 64.dp else PokerDimens.MinTouch,
                showAmounts = expanded,
                iconsOnly = LocalDensity.current.fontScale > ICONS_ONLY_ABOVE,
                gutter = if (widthClass == WidthClass.Small) 12.dp else PokerDimens.Gutter
            )
        }

        private const val ICONS_ONLY_ABOVE = 1.3f
    }
}

/** The column's name, as in the header. */
@Composable
internal fun BankColumn.label(): String = stringResource(
    when (this) {
        BankColumn.BUY_IN -> R.string.bank_column_buy_in
        BankColumn.REBUY -> R.string.bank_column_rebuy
        BankColumn.ADD_ON -> R.string.bank_column_add_on
        BankColumn.OUT -> R.string.bank_column_out
        BankColumn.PAID -> R.string.bank_column_paid
    }
)

/** "closed after level 4" / "closed after break 1", or null while open. */
@Composable
internal fun PurchaseWindow.closedReason(): String? = when (this) {
    is PurchaseWindow.ClosedAfterLevel -> stringResource(R.string.bank_closed_after_level, level)
    is PurchaseWindow.ClosedAfterBreak -> stringResource(R.string.bank_closed_after_break, number)
    else -> null
}

/**
 * What TalkBack reads for a cell: the player, the column and the state ("Dana, buy-in, paid",
 * "Marcus, rebuy, closed after level 4, 1 taken", "Rita, out, 8th, knocked out by Marcus").
 */
@Composable
internal fun cellDescription(row: BankRowModel, column: BankColumn, state: BankUiState): String {
    val cell: BankCell = row.cell(column)
    return when (column) {
        BankColumn.BUY_IN -> stringResource(
            if (cell.status == CellStatus.Done) R.string.bank_cell_buy_in_paid else R.string.bank_cell_buy_in_not_paid,
            row.name
        )
        BankColumn.REBUY -> purchaseDescription(
            row.name,
            stringResource(R.string.bank_rebuy_lower),
            row.rebuys,
            state.rebuyWindow
        )
        BankColumn.ADD_ON -> purchaseDescription(
            row.name,
            stringResource(R.string.bank_add_on_lower),
            row.addOns,
            state.addOnWindow
        )
        BankColumn.OUT -> outDescription(row, cell)
        BankColumn.PAID -> paidDescription(row, cell)
    }
}

@Composable
private fun outDescription(row: BankRowModel, cell: BankCell): String = when (cell.status) {
    CellStatus.Champion -> stringResource(R.string.bank_cell_champion, row.name)
    // An entry the player re-entered after can't be brought back (PP-116): no "Bring back" then
    CellStatus.OutPlace -> row.knockedOutByName
        ?.let { by ->
            stringResource(
                if (cell.enabled) R.string.bank_cell_out else R.string.bank_cell_out_re_entered,
                row.name,
                ordinalOf(cell.place),
                by,
            )
        }
        ?: stringResource(
            if (cell.enabled) R.string.bank_cell_out_unclaimed else R.string.bank_cell_out_unclaimed_re_entered,
            row.name,
            ordinalOf(cell.place),
        )
    else -> stringResource(R.string.bank_cell_knock_out, row.name)
}

@Composable
private fun paidDescription(row: BankRowModel, cell: BankCell): String = when (cell.status) {
    CellStatus.Paid -> stringResource(R.string.bank_cell_paid, row.name)
    CellStatus.Owed -> stringResource(R.string.bank_cell_owed, row.name, FormatUtils.formatMoney(cell.amountCents))
    else -> stringResource(R.string.bank_cell_nothing_owed, row.name)
}

@Composable
private fun purchaseDescription(name: String, column: String, count: Int, window: PurchaseWindow): String {
    val closed = window.closedReason()
    return when {
        closed != null -> pluralStringResource(R.plurals.bank_cell_purchase_closed, count, name, column, closed, count)
        count == 0 -> stringResource(R.string.bank_cell_purchase_none, name, column)
        else -> pluralStringResource(R.plurals.bank_cell_purchase_taken, count, name, column, count)
    }
}
