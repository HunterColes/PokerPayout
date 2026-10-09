package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The table tools' own icons for the Tools list, in the style of PokerIcons: Material Symbols paths
 * (Apache License 2.0, Google), 24 dp, tinted by the caller.
 */
internal object TableToolIcons {
    /** Outs & pot odds: a per cent sign. */
    val Percent: ImageVector by lazy {
        icon(
            "Percent",
            "M7.5 11C9.43 11 11 9.43 11 7.5S9.43 4 7.5 4 4 5.57 4 7.5 5.57 11 7.5 11zm0-5C8.33 6 9 6.67 9 7.5S8.33 9 7.5 9 " +
                "6 8.33 6 7.5 6.67 6 7.5 6zM4.0025 18.5832 18.5832 4.0025l1.4142 1.4142L5.4167 19.9974zM16.5 13c-1.93 0-3.5 " +
                "1.57-3.5 3.5s1.57 3.5 3.5 3.5 3.5-1.57 3.5-3.5-1.57-3.5-3.5-3.5zm0 5c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 " +
                "1.5.67 1.5 1.5-.67 1.5-1.5 1.5z",
        )
    }

    /** Side pots: a pot cut into shares (a pie chart). */
    val SplitPot: ImageVector by lazy {
        icon(
            "SplitPot",
            "M11 2v20c-5.07-.5-9-4.79-9-10s3.93-9.5 9-10zm2.03 0v8.99H22c-.47-4.74-4.24-8.52-8.97-8.99zm0 11.01V22c4.73-.47 " +
                "8.5-4.25 8.97-8.99h-8.97z",
        )
    }

    /** Deal maker: one way splitting in two, the chop. */
    val Chop: ImageVector by lazy {
        icon("Chop", "M14 4l2.29 2.29-2.88 2.88 1.42 1.42 2.88-2.88L20 10V4zm-4 0H4v6l2.29-2.29 4.71 4.7V20h2v-8.41l-5.29-5.3z")
    }

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(
            name = "TableToolIcons.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
        }.build()
}
