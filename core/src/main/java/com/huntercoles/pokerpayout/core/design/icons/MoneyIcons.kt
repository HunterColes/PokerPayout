package com.huntercoles.pokerpayout.core.design.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The Bank's and Payouts' own icons, from the same mockup `<symbol>` set as [PokerIcons]: the five
 * column icons (with Plus and Crown from [PokerIcons]) and the payout-structure scale. Material
 * Symbols paths (Apache License 2.0, Google), 24 dp, tinted by the caller.
 */
object MoneyIcons {
    /**
     * Buy-in: a banknote (outline, a coin in the middle, a bar at each end). Written out in absolute
     * coordinates and filled even-odd, so the note's inside stays open whatever the drawing order.
     */
    val Cash: ImageVector by lazy {
        icon(
            "Cash",
            "M2 6H22V18H2Z M4 8V16H20V8Z M12 9.5A2.5 2.5 0 1 1 12 14.5A2.5 2.5 0 1 1 12 9.5Z M5 10H7V14H5Z M17 10H19V14H17Z",
            evenOdd = true,
        )
    }

    /** Rebuy: two arrows going round. */
    val Renew: ImageVector by lazy {
        icon(
            "Renew",
            "M12 6v3l4-4-4-4v3a8 8 0 0 0-6.76 12.26L6.7 14.8A5.87 5.87 0 0 1 6 12c0-3.31 2.69-6 6-6zm6.76 1.74L17.3 9.2" +
                "c.44.84.7 1.79.7 2.8 0 3.31-2.69 6-6 6v-3l-4 4 4 4v-3a8 8 0 0 0 6.76-12.26z",
        )
    }

    /** Out: leaving the table. */
    val Exit: ImageVector by lazy {
        icon(
            "Exit",
            "M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5zM4 5h8V3H4c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h8v-2H4V5z",
        )
    }

    /** Paid: a dollar sign with a tick. */
    val PaidCheck: ImageVector by lazy {
        icon(
            "PaidCheck",
            "M11 13v-1c0-.55-.45-1-1-1H6V9h5V7H8.5V6h-2v1H5c-.55 0-1 .45-1 1v3c0 .55.45 1 1 1h4v2H4v2h2.5v1h2v-1H10" +
                "c.55 0 1-.45 1-1zm8.59-.48-5.66 5.65-2.83-2.83-1.41 1.42L13.93 21 21 13.93z",
        )
    }

    /** Payout structure: a balance. */
    val Scale: ImageVector by lazy {
        icon(
            "Scale",
            "M11 3h2v2.1l5.6 1.4-1.1 1.2L20 13a3 3 0 0 1-6 0l2.4-4.9L13 7.3V19h4v2H7v-2h4V7.3L7.6 8.1 10 13a3 3 0 0 1-6 0" +
                "l2.5-5.3-1.1-1.2L11 5.1zm-4 6.3L5.6 12.5h2.8zm10 0-1.4 3.2h2.8z",
        )
    }

    private fun icon(name: String, path: String, evenOdd: Boolean = false): ImageVector =
        ImageVector.Builder(
            name = "MoneyIcons.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = addPathNodes(path),
                pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                fill = SolidColor(Color.Black),
            )
        }.build()
}
