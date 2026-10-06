package com.huntercoles.pokerpayout.core.design.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The app's icon set: 24 dp vectors, tinted by the caller.
 *
 * The UI icons use Material Symbols paths (Apache License 2.0, Google). The suits, skull, crown,
 * chip, cards and delta triangles were drawn for Poker Payout. Generated from the makeover mockups'
 * `<symbol>` set, so the app and the mockups draw the same shapes. Replaces
 * `material-icons-extended` once every screen has moved over (PP-039).
 */
object PokerIcons {
    /** Spade suit pip (drawn for Poker Payout). */
    val Spade: ImageVector by lazy {
        icon(
            "Spade",
            "M12 1.5C9.4 5.6 3 9.4 3 14.2a4.6 4.6 0 0 0 7.7 3.4L9.4 22.5h5.2l-1.3-4.9A4.6 4.6 0 0 0 21" +
                " 14.2c0-4.8-6.4-8.6-9-12.7z",
        )
    }
    /** Heart suit pip (drawn for Poker Payout). */
    val Heart: ImageVector by lazy {
        icon(
            "Heart",
            "M12 21.6 10.6 20.3C5.4 15.6 2 12.5 2 8.6 2 5.5 4.4 3 7.5 3c1.7 0 3.4.8 4.5 2.1C13.1 3.8 14.8 3 16.5" +
                " 3 19.6 3 22 5.5 22 8.6c0 3.9-3.4 7-8.6 11.7z",
        )
    }
    /** Diamond suit pip (drawn for Poker Payout). */
    val Diamond: ImageVector by lazy { icon("Diamond", "M12 1.5 20.5 12 12 22.5 3.5 12z") }
    /** Club suit pip (drawn for Poker Payout). */
    val Club: ImageVector by lazy {
        icon(
            "Club",
            "M13.1 13.6a4.6 4.6 0 1 0 9.2 0a4.6 4.6 0 1 0 -9.2 0z",
            "M1.7 13.6a4.6 4.6 0 1 0 9.2 0a4.6 4.6 0 1 0 -9.2 0z",
            "M7.4 6.6a4.6 4.6 0 1 0 9.2 0a4.6 4.6 0 1 0 -9.2 0z",
            "M10.4 12h3.2l1.3 10.5H9.1z",
        )
    }
    val Play: ImageVector by lazy { icon("Play", "M8 5v14l11-7z") }
    val Pause: ImageVector by lazy { icon("Pause", "M6 19h4V5H6v14zm8-14v14h4V5h-4z") }
    val Previous: ImageVector by lazy { icon("Previous", "M6 6h2v12H6zm3.5 6 8.5 6V6z") }
    val Next: ImageVector by lazy { icon("Next", "M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z") }
    val Plus: ImageVector by lazy { icon("Plus", "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z") }
    val Minus: ImageVector by lazy { icon("Minus", "M19 13H5v-2h14v2z") }
    val Back: ImageVector by lazy { icon("Back", "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z") }
    val More: ImageVector by lazy {
        icon(
            "More",
            "M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0" +
                " 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z",
        )
    }
    val Undo: ImageVector by lazy {
        icon(
            "Undo",
            "M12.5 8c-2.65 0-5.05.99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 3.16-1.88 5.12-1.88 3.54 0 6.55 2.31" +
                " 7.6 5.5l2.37-.78C21.08 11.03 17.15 8 12.5 8z",
        )
    }
    val Restart: ImageVector by lazy {
        icon(
            "Restart",
            "M17.65 6.35A7.96 7.96 0 0 0 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55" +
                " 7.73-6h-2.08A5.99 5.99 0 0 1 12 18c-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13" +
                " 11h7V4l-2.35 2.35z",
        )
    }
    val Fullscreen: ImageVector by lazy {
        icon(
            "Fullscreen",
            "M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z",
        )
    }
    val Bell: ImageVector by lazy {
        icon(
            "Bell",
            "M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2" +
                " 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6" +
                " 11v5l-2 2v1h16v-1l-2-2z",
        )
    }
    val Tune: ImageVector by lazy {
        icon(
            "Tune",
            "M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14" +
                " 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z",
        )
    }
    val Edit: ImageVector by lazy {
        icon(
            "Edit",
            "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0" +
                " 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z",
        )
    }
    val Share: ImageVector by lazy {
        icon(
            "Share",
            "M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11A2.99 2.99" +
                " 0 0 0 21 5c0-1.66-1.34-3-3-3s-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81A2.99 2.99 0 0 0 3 12a2.99 2.99" +
                " 0 0 0 5.04 2.19l7.12 4.16c-.05.21-.08.43-.08.65a2.92 2.92 0 1 0 2.92-2.92z",
        )
    }
    val Trophy: ImageVector by lazy {
        icon(
            "Trophy",
            "M19 5h-2V3H7v2H5c-1.1 0-2 .9-2 2v1c0 2.55 1.92 4.63 4.39 4.94A5.01 5.01 0 0 0 11" +
                " 15.9V19H7v2h10v-2h-4v-3.1a5.01 5.01 0 0 0 3.61-2.96C19.08 12.63 21 10.55 21 8V7c0-1.1-.9-2-2-2zM5" +
                " 8V7h2v3.82C5.84 10.4 5 9.3 5 8zm14 0c0 1.3-.84 2.4-2 2.82V7h2v1z",
        )
    }
    val Timer: ImageVector by lazy {
        icon(
            "Timer",
            "M15 1H9v2h6V1zm-4 13h2V8h-2v6zm8.03-6.61 1.42-1.42c-.43-.51-.9-.99-1.41-1.41l-1.42 1.42A8.96 8.96 0" +
                " 0 0 12 4c-4.97 0-9 4.03-9 9s4.02 9 9 9 9-4.03 9-9c0-2.12-.74-4.07-1.97-5.61zM12 20c-3.87" +
                " 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z",
        )
    }
    val Wallet: ImageVector by lazy {
        icon(
            "Wallet",
            "M21 18v1c0 1.1-.9 2-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h14c1.1 0 2 .9 2 2v1h-9a2 2 0 0 0-2 2v8a2 2" +
                " 0 0 0 2 2h9zm-9-2h10V8H12v8zm4-2.5c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67" +
                " 1.5-1.5 1.5z",
        )
    }
    val Wrench: ImageVector by lazy {
        icon(
            "Wrench",
            "M22.7 19l-9.1-9.1c.9-2.3.4-5-1.5-6.9-2-2-5-2.4-7.4-1.3L9 6 6 9 1.6 4.7C.4 7.1.9 10.1 2.9 12.1c1.9" +
                " 1.9 4.6 2.4 6.9 1.5l9.1 9.1c.4.4 1 .4 1.4 0l2.3-2.3c.5-.4.5-1.1.1-1.4z",
        )
    }
    val Check: ImageVector by lazy { icon("Check", "M9 16.17 4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z") }
    val Lock: ImageVector by lazy {
        icon(
            "Lock",
            "M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9" +
                " 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71" +
                " 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z",
        )
    }
    val Close: ImageVector by lazy {
        icon(
            "Close",
            "M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41" +
                " 12z",
        )
    }
    val ChevronRight: ImageVector by lazy { icon("ChevronRight", "M10 6 8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z") }
    val ChevronDown: ImageVector by lazy { icon("ChevronDown", "M7.41 8.59 12 13.17l4.59-4.58L18 10l-6 6-6-6z") }
    val Coffee: ImageVector by lazy {
        icon(
            "Coffee",
            "M20 3H4v10c0 2.21 1.79 4 4 4h6c2.21 0 4-1.79 4-4v-3h2a2 2 0 0 0 2-2V5c0-1.11-.89-2-2-2zm0" +
                " 5h-2V5h2v3zM4 19h16v2H4z",
        )
    }
    val Dice: ImageVector by lazy {
        icon(
            "Dice",
            "M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zM7.5 18a1.5 1.5 0 1 1" +
                " 0-3 1.5 1.5 0 0 1 0 3zm0-9a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm4.5 4.5a1.5 1.5 0 1 1 0-3 1.5 1.5" +
                " 0 0 1 0 3zm4.5 4.5a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm0-9a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3z",
        )
    }
    val Volume: ImageVector by lazy {
        icon(
            "Volume",
            "M3 9v6h4l5 5V4L7 9H3zm13.5 3A4.5 4.5 0 0 0 14 7.97v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14" +
                " 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z",
        )
    }
    val VolumeMute: ImageVector by lazy { icon("VolumeMute", "M7 9v6h4l5 5V4l-5 5H7z") }
    val List: ImageVector by lazy {
        icon(
            "List",
            "M2 17h2v.5H3v1h1v.5H2v1h3v-4H2v1zm1-9h1V4H2v1h1v3zm-1 3h1.8L2 13.1v.9h3v-1H3.2L5" +
                " 10.9V10H2v1zm5-6v2h14V5H7zm0 14h14v-2H7v2zm0-6h14v-2H7v2z",
        )
    }
    val Seat: ImageVector by lazy {
        icon(
            "Seat",
            "M4 18v3h3v-3h10v3h3v-6H4v3zm15-8h3v3h-3v-3zM2 10h3v3H2v-3zm15 3H7V5c0-1.1.9-2 2-2h6c1.1 0 2 .9 2" +
                " 2v8z",
        )
    }
    /** Knockout skull (drawn for Poker Payout). */
    val Skull: ImageVector by lazy {
        icon(
            "Skull",
            "M12 2C6.6 2 3 5.6 3 10.2c0 2.6 1.2 4.6 3 5.8V19c0 .6.4 1 1 1h1.5v-2.2h1.6V20h3.8v-2.2h1.6V20H17c.6" +
                " 0 1-.4 1-1v-3c1.8-1.2 3-3.2 3-5.8C21 5.6 17.4 2 12 2zM8.6 14.2a2.1 2.1 0 1 1 0-4.2 2.1 2.1 0 0 1 0" +
                " 4.2zm6.8 0a2.1 2.1 0 1 1 0-4.2 2.1 2.1 0 0 1 0 4.2z",
        )
    }
    /** Champion crown (drawn for Poker Payout). */
    val Crown: ImageVector by lazy { icon("Crown", "M3 18h18v2.5H3zM2.5 7l5.2 4.2L12 4.5l4.3 6.7L21.5 7 19.6 16H4.4z") }
    /** Two cards (drawn for Poker Payout). */
    val Cards: ImageVector by lazy {
        icon(
            "Cards",
            "M9.2 3.1 3.6 4.6a1.5 1.5 0 0 0-1.1 1.8l3.4 12.8a1.5 1.5 0 0 0 1.8 1.1l1.3-.4V5.6c0-.9.4-1.8" +
                " 1.1-2.4zM12 4h8.5c.8 0 1.5.7 1.5 1.5v15c0 .8-.7 1.5-1.5 1.5H12c-.8 0-1.5-.7-1.5-1.5v-15c0-.8.7-1.5" +
                " 1.5-1.5zm4.3 4.2c-1 1.6-3.3 3-3.3 4.9a1.8 1.8 0 0 0 2.9 1.4l-.5 2h1.8l-.5-2a1.8 1.8 0 0 0" +
                " 2.9-1.4c0-1.9-2.3-3.3-3.3-4.9z",
        )
    }
    /** Poker chip (drawn for Poker Payout). */
    val Chip: ImageVector by lazy {
        icon(
            "Chip",
            "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm-1 2.06V6.1a6 6 0 0 0-2.65 1.1L6.9 5.76A7.96 7.96 0 0 1 11" +
                " 4.06zm2 0a7.96 7.96 0 0 1 4.1 1.7l-1.45 1.44A6 6 0 0 0 13 6.1zM5.76 6.9 7.2 8.35A6 6 0 0 0 6.1" +
                " 11H4.06a7.96 7.96 0 0 1 1.7-4.1zm12.48 0a7.96 7.96 0 0 1 1.7 4.1H17.9a6 6 0 0 0-1.1-2.65zM12 8a4 4" +
                " 0 1 1 0 8 4 4 0 0 1 0-8zm-7.94 5H6.1a6 6 0 0 0 1.1 2.65L5.76 17.1A7.96 7.96 0 0 1 4.06 13zm13.84" +
                " 0h2.04a7.96 7.96 0 0 1-1.7 4.1l-1.44-1.45A6 6 0 0 0 17.9 13zm-9.55 3.8A6 6 0 0 0 11 17.9v2.04a7.96" +
                " 7.96 0 0 1-4.1-1.7zm7.3 0 1.45 1.44A7.96 7.96 0 0 1 13 19.94V17.9a6 6 0 0 0 2.65-1.1z",
        )
    }
    val Swap: ImageVector by lazy { icon("Swap", "M16 17.01V10h-2v7.01h-3L15 21l4-3.99h-3zM9 3 5 6.99h3V14h2V6.99h3L9 3z") }
    val Person: ImageVector by lazy {
        icon(
            "Person",
            "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8" +
                " 4v2h16v-2c0-2.66-5.33-4-8-4z",
        )
    }
    val Backspace: ImageVector by lazy {
        icon(
            "Backspace",
            "M22 3H7c-.69 0-1.23.35-1.59.88L0 12l5.41 8.11c.36.53.9.89 1.59.89h15c1.1 0 2-.9" +
                " 2-2V5c0-1.1-.9-2-2-2zm-3 12.59L17.59 17 14 13.41 10.41 17 9 15.59 12.59 12 9 8.41 10.41 7 14 10.59" +
                " 17.59 7 19 8.41 15.41 12 19 15.59z",
        )
    }
    val Info: ImageVector by lazy { icon("Info", "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z") }
    /** Up delta (drawn for Poker Payout). */
    val TriangleUp: ImageVector by lazy { icon("TriangleUp", "M12 5 20 17H4z") }
    /** Down delta (drawn for Poker Payout). */
    val TriangleDown: ImageVector by lazy { icon("TriangleDown", "M12 19 4 7h16z") }

    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(
            name = "PokerIcons.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            paths.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) }
        }.build()
}
