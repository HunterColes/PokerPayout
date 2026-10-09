package com.huntercoles.pokerpayout.tools.presentation

import android.content.Context
import com.huntercoles.pokerpayout.tools.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What was typed into the table tools (Side pots, Deal maker, Outs & pot odds), kept while the app
 * runs. Switching tabs closes a tool's screen; coming back finds the numbers where they were. A hand
 * or a deal is over long before the app is closed, so nothing is written to storage.
 */
@Singleton
class TableToolsMemory @Inject constructor() {
    var sidePots: SidePotsUiState? = null
    var deal: DealUiState? = null
    var outs: OutsUiState? = null
}

/** The strings the table tools' ViewModels need themselves: their snackbars. */
class TableToolMessages @Inject constructor(@ApplicationContext private val context: Context) {
    val newHand: String get() = context.getString(R.string.side_pots_snackbar_new_hand)
    val startedOver: String get() = context.getString(R.string.deal_snackbar_started_over)
    val undo: String get() = context.getString(R.string.table_tools_undo)
}

/** Long enough for any real name; short enough to keep a row on one or two lines. */
internal const val MAX_PLAYER_NAME = 24

/** At most nine digits of chips (999,999,999), as in a money field's dollars. */
internal const val MAX_CHIP_DIGITS = 9

/** [this] list with [index] replaced by [transform] of it; unchanged when [index] is out of range. */
internal fun <T> List<T>.updated(index: Int, transform: (T) -> T): List<T> =
    if (index in indices) toMutableList().also { it[index] = transform(it[index]) } else this
