package com.huntercoles.pokerpayout.core.testing

import androidx.compose.ui.test.SemanticsNodeInteraction
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage

/**
 * Golden images live in the module's `src/test/screenshots/<group>/<name>/<name>_<config id>.png`,
 * e.g. `core/src/test/screenshots/components/PokerButton/PokerButton_small-320x640_font2.0.png`, so a
 * reviewer can open one component across the whole matrix, or one device across every component.
 * The name repeats in the file name because Roborazzi writes its diffs (`*_compare.png`) to one flat
 * folder, `build/outputs/roborazzi`.
 */
object Goldens {
    private const val DEFAULT_DIR = "src/test/screenshots"

    /** Strict: any pixel that differs beyond anti-aliasing noise fails verification. */
    val options = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f),
    )

    fun path(group: String, name: String, config: ScreenConfig): String {
        val root = System.getProperty("roborazzi.output.dir") ?: DEFAULT_DIR
        return "$root/$group/$name/${name}_${config.id}.png"
    }
}

/**
 * Records, compares or verifies this node against its golden, depending on the Gradle task
 * (`recordRoborazziDebug`, `compareRoborazziDebug`, `verifyRoborazziDebug`; a plain
 * `testDebugUnitTest` verifies). Fails first if the node doesn't fit on the screen, because the
 * picture would silently cut it off.
 */
fun SemanticsNodeInteraction.captureGolden(group: String, name: String, config: ScreenConfig) {
    val node = fetchSemanticsNode()
    val screenHeight = screenBounds().height
    val bottom = node.positionInRoot.y + node.size.height
    check(bottom <= screenHeight + 1) {
        "$name doesn't fit on ${config.id}: it is ${node.size.height}px tall from y=${node.positionInRoot.y}, " +
            "the screen is ${screenHeight}px. Split the gallery into smaller pieces."
    }
    captureRoboImage(Goldens.path(group, name, config), Goldens.options)
}
