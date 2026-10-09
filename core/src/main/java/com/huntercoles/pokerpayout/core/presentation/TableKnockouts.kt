package com.huntercoles.pokerpayout.core.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * PP-135: knockouts from the full-screen clock. The Bank owns knockouts (who is out, who gets the
 * bounty, the mystery envelopes, places and Undo), so bank-feature provides this, and the
 * Tournament tab's table view shows [Panel] over the clock when its Knock out button is tapped. The
 * clock knows only this way in, never the rules.
 */
interface TableKnockouts {
    /**
     * The quick knockout, filling the table view: who is out, then who knocked them out (or
     * nobody). [onClose] once it is recorded (or its envelope seen) or put away.
     */
    @Composable
    fun Panel(onClose: () -> Unit)
}

/** The app's [TableKnockouts]; null where there are none (a screen test that doesn't need them). */
val LocalTableKnockouts = staticCompositionLocalOf<TableKnockouts?> { null }
