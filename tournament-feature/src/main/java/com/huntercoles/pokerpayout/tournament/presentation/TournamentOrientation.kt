package com.huntercoles.pokerpayout.tournament.presentation

import android.content.pm.ActivityInfo
import com.huntercoles.pokerpayout.core.presentation.OrientationPolicy

/**
 * How the Tournament tab turns (PP-079, PP-088, S3).
 *
 * - **Phones**: portrait until a clock exists. From then on the tab follows the phone's own rotation
 *   setting ([ActivityInfo.SCREEN_ORIENTATION_USER]): turned sideways it shows the table view,
 *   upright the clock. ⤢ forces landscape until ✕, which is the way in with rotation locked. ✕ in a
 *   table view the phone was turned into keeps this visit portrait. Other tabs stay portrait.
 * - **Tablets**: free, as every screen; ⤢ still forces landscape for the table view.
 */
object TournamentOrientation {

    /** The orientation the tab asks for. */
    fun requested(smallestScreenWidthDp: Int, clockExists: Boolean, tableViewForced: Boolean, rotationPaused: Boolean): Int =
        when {
            tableViewForced -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            OrientationPolicy.isTablet(smallestScreenWidthDp) -> OrientationPolicy.base(smallestScreenWidthDp)
            clockExists && !rotationPaused -> ActivityInfo.SCREEN_ORIENTATION_USER
            else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

    /**
     * Whether the tab shows the table view: forced with ⤢, or a phone held sideways once a clock
     * exists (unless ✕ paused that). A tablet held sideways shows the two-pane clock instead (Z4).
     */
    fun showsTableView(smallestScreenWidthDp: Int, landscape: Boolean, clock: TableViewInputs): Boolean {
        val turnedPhone = landscape && !OrientationPolicy.isTablet(smallestScreenWidthDp)
        return clock.forced || (turnedPhone && clock.exists && !clock.rotationPaused)
    }

    /** The clock's side of [showsTableView]. */
    data class TableViewInputs(val exists: Boolean, val forced: Boolean, val rotationPaused: Boolean)
}
