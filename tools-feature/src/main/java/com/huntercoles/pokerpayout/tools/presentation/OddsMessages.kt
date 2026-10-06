package com.huntercoles.pokerpayout.tools.presentation

import android.content.Context
import com.huntercoles.pokerpayout.tools.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The few strings the odds ViewModel shows itself: snackbars and error fallbacks. */
class OddsMessages @Inject constructor(@ApplicationContext private val context: Context) {
    val newHand: String get() = context.getString(R.string.odds_snackbar_new_hand)
    val tableCleared: String get() = context.getString(R.string.odds_snackbar_table_cleared)
    val undo: String get() = context.getString(R.string.odds_undo)
    val cantCalculate: String get() = context.getString(R.string.odds_error_cant_calculate)

    fun calculationFailed(detail: String): String = context.getString(R.string.odds_error_failed, detail)
}

/** Seeds for run it out: random in the app, fixed in tests so runs replay. */
fun interface RunOutSeeds {
    fun next(): Long
}
