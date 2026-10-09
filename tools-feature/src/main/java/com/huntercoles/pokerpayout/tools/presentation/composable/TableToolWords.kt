package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.huntercoles.pokerpayout.tools.R

/** A number of chips as the tools show it: "2,350". */
internal fun chips(count: Long): String = OddsFormat.grouped(count)

/** A share in per cent to one decimal, as the odds screens show it: "35.0". */
internal fun percent(value: Double): String = OddsFormat.oneDecimal(value)

/** What a table tool calls the player in row [index]: their name, or "Player N" while it is blank. */
@Composable
internal fun playerName(index: Int, name: String): String =
    name.trim().ifEmpty { stringResource(R.string.table_tools_default_player, index + 1) }

/** "Dana", "Dana or Sam", "Dana, Sam or Theo". */
@Composable
internal fun joinWithOr(items: List<String>): String {
    if (items.size < 2) return items.firstOrNull().orEmpty()
    val head = items.dropLast(1).joinToString(stringResource(R.string.table_tools_list_separator))
    return stringResource(R.string.table_tools_list_or, head, items.last())
}
