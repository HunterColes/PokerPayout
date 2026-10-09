package com.huntercoles.pokerpayout.core.testing

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnitType
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.robolectric.RuntimeEnvironment
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Accessibility checks on the semantics tree and the rendered pixels, run by the layout checks on the
 * device matrix ([LayoutAssertions.assertTouchTargets] checks names on every cell,
 * [LayoutAssertions.assertTextFits] checks contrast on the [reference cell][isReferenceCell]).
 *
 * - Every tappable thing has a name TalkBack can read, and every text field a label.
 * - Every text has the contrast WCAG 2.1 AA asks of it against what is drawn behind it.
 */
object AccessibilityAssertions {
    /** WCAG 2.1 AA (1.4.3): text needs 4.5:1, large text 3:1. */
    private const val AA_TEXT = 4.5
    private const val AA_LARGE_TEXT = 3.0

    /** Large text: 18 sp and up, or 14 sp and up in bold (WCAG's 18 pt and 14 pt bold, as Android reads them). */
    private const val LARGE_SP = 18f
    private const val LARGE_BOLD_SP = 14f
    private const val DEFAULT_SP = 14f

    /** A text this transparent, or a box this small, isn't judged (a fade, a hidden measuring copy). */
    private const val MIN_ALPHA = 0.1f
    private const val MIN_PIXELS = 4

    /** A text is judged when at least this much of it is in view (not scrolled out or under a bar). */
    private const val MIN_VISIBLE_SHARE = 0.9f

    /** Pixels this close to the text's own colour are its glyphs and their anti-aliased edges, not what is behind. */
    private const val SAME_COLOUR_DISTANCE = 48

    /**
     * Text and background colours below AA that were on screen when this check came in (wave 9),
     * each an open question for the owner. They pass, so the check can guard everything else: any
     * other pair below AA fails. Take a pair out when its screen is fixed, or say here why it stays.
     */
    private val KNOWN_BELOW_AA: Map<Pair<Int, Int>, String> = mapOf(
        (0xFFFFFF to 0x228B22) to "a chip's value on the green 25 chip, its physical colour: 4.39:1",
        (0xFFFFFF to 0x808080) to "a chip's value on the grey 20 chip, its physical colour: 3.95:1",
        (0xE8CC07 to 0x146349) to "the selected segment's second line, PokerGold at 90% on FeltHigh: 4.49:1",
        (0x6F8B82 to 0x072A20) to "ChalkDim ranks of cards on the table in the odds insight grid: 4.18:1",
        (0x0A7A3D to 0x6D8A81) to "a faded club in Hand ranks with the four-colour deck: 1.45:1",
    )

    /**
     * Contrast is a matter of colours, not of size, so one cell is enough: `phone`, upright, at font
     * 1.0, where the most text is below the large-text size and needs 4.5:1.
     */
    fun isReferenceCell(): Boolean {
        val configuration = RuntimeEnvironment.getApplication().resources.configuration
        return configuration.fontScale == 1f &&
            configuration.screenWidthDp == Device.Phone.widthDp &&
            configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    }

    /**
     * Every clickable, toggleable or selectable node has something TalkBack can read: its own text,
     * the text it merges, or a content description. A text field may be read by its value (the
     * Bank's name fields are), but an empty one with no label is read out as just "Edit box".
     */
    fun assertNamed(rule: SemanticsNodeInteractionsProvider, where: String) {
        val tappable = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).fetchSemanticsNodes()
        val unnamed = tappable.filter { it.isEditable().not() && it.spokenName().isBlank() }
            .map { "${it.describe()} has no name TalkBack can read: give it a text or a contentDescription" }
        val fields = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetText)).fetchSemanticsNodes()
        val unlabelled = fields.filter { it.spokenName().isBlank() && it.value().isBlank() }
            .map { "${it.describe()} is an empty field without a label: TalkBack reads only \"Edit box\"" }
        fail(where, "names for TalkBack", unnamed + unlabelled)
    }

    private fun SemanticsNode.value(): String = config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    /**
     * Every text that is drawn has at least 4.5:1 contrast with what is behind it, or 3:1 for large
     * text (WCAG 2.1 AA). The text's colour is the one its style declares (blended over the
     * background if it is see-through); the colour behind it is the commonest colour in its box on
     * the rendered screen, leaving out the glyphs' own. Text in a disabled control is exempt, as
     * WCAG exempts inactive components; so is text without a declared colour.
     */
    fun assertTextContrast(rule: SemanticsNodeInteractionsProvider, where: String) {
        if (rule.onAllNodes(isRoot()).fetchSemanticsNodes().size != 1) return // a popup's own window
        val image = renderContent() ?: return
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        val screen = Screen(pixels, image.width, image.height)
        val texts = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
        val problems = texts.filterNot { it.isInDisabledControl() }.flatMap { node -> contrastProblems(node, screen) }
        fail(where, "text contrast (WCAG AA)", problems)
    }

    private class Screen(val pixels: IntArray, val width: Int, val height: Int)

    /**
     * The app's content drawn into a bitmap, as Roborazzi draws it (Compose's own `captureToImage`
     * waits for a frame Robolectric never schedules). Nothing behind the content is drawn, so
     * pixels the content leaves empty are transparent and never count as a background.
     */
    private fun renderContent(): Bitmap? {
        val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
        val content = activity?.findViewById<View>(android.R.id.content)
        if (content == null || content.width <= 0 || content.height <= 0) return null
        return Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888).also { content.draw(Canvas(it)) }
    }

    private fun contrastProblems(node: SemanticsNode, screen: Screen): List<String> {
        val box = node.boundsInRoot.intersect(Rect(0f, 0f, screen.width.toFloat(), screen.height.toFloat()))
        val laidOut = node.unclippedBoundsInRoot()
        // Only text in view: a text scrolled under a bar shows as a sliver of whatever is on top of it
        val inView = box.width * box.height >= MIN_VISIBLE_SHARE * laidOut.width * laidOut.height
        if (box.width * box.height < MIN_PIXELS || !inView) return emptyList()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        return layouts.flatMap { layout ->
            val style = layout.layoutInput.style
            val colours = (listOf(style.color) + layout.layoutInput.text.spanStyles.map { it.item.color })
                .filter { it != Color.Unspecified && it.alpha >= MIN_ALPHA }
                .distinct()
            val sizeSp = if (style.fontSize.type == TextUnitType.Sp) style.fontSize.value else DEFAULT_SP
            val bold = (style.fontWeight ?: FontWeight.Normal) >= FontWeight.Bold
            val large = sizeSp >= LARGE_SP || (bold && sizeSp >= LARGE_BOLD_SP)
            val needed = if (large) AA_LARGE_TEXT else AA_TEXT
            colours.mapNotNull { colour ->
                val behind = background(screen, box, colour) ?: return@mapNotNull null
                val drawn = colour.compositeOver(behind)
                val ratio = contrast(drawn, behind)
                val known = (drawn.toArgb() and RGB to (behind.toArgb() and RGB)) in KNOWN_BELOW_AA
                if (ratio + EPSILON < needed && !known) {
                    "${node.describe()} is ${ratio.format()}:1 (${drawn.hex()} on ${behind.hex()}), needs ${needed.format()}:1" +
                        if (large) " for large text" else ""
                } else {
                    null
                }
            }
        }
    }

    /** The commonest opaque colour in [box] that isn't (nearly) [text]'s; null if there is none. */
    private fun background(screen: Screen, box: Rect, text: Color): Color? {
        val textArgb = text.copy(alpha = 1f).toArgb()
        val counts = HashMap<Int, Int>()
        val left = box.left.roundToInt().coerceIn(0, screen.width)
        val right = box.right.roundToInt().coerceIn(0, screen.width)
        val top = box.top.roundToInt().coerceIn(0, screen.height)
        val bottom = box.bottom.roundToInt().coerceIn(0, screen.height)
        for (y in top until bottom) {
            for (x in left until right) {
                val argb = screen.pixels[y * screen.width + x]
                if (argb ushr ALPHA_SHIFT == OPAQUE && distance(argb, textArgb) > SAME_COLOUR_DISTANCE) {
                    counts[argb] = (counts[argb] ?: 0) + 1
                }
            }
        }
        return counts.maxByOrNull { it.value }?.key?.let { Color(it) }
    }

    private fun distance(a: Int, b: Int): Int =
        max(max(abs8(a shr RED_SHIFT, b shr RED_SHIFT), abs8(a shr GREEN_SHIFT, b shr GREEN_SHIFT)), abs8(a, b))

    private fun abs8(a: Int, b: Int): Int = kotlin.math.abs((a and BYTE) - (b and BYTE))

    /** WCAG contrast ratio: (lighter + 0.05) / (darker + 0.05), from relative luminance. */
    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (max(la, lb) + LUMINANCE_OFFSET) / (min(la, lb) + LUMINANCE_OFFSET)
    }

    private fun SemanticsNode.isInDisabledControl(): Boolean =
        generateSequence(this) { it.parent }.any { it.config.contains(SemanticsProperties.Disabled) }

    private fun SemanticsNode.isEditable(): Boolean = config.contains(SemanticsActions.SetText)

    /** What TalkBack reads as this node's name: its content description, else its text (merged ones included). */
    private fun SemanticsNode.spokenName(): String {
        val description = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
        val text = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
        return description.ifBlank { text }
    }

    private fun SemanticsNode.describe(): String {
        val text = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
            ?: config.getOrNull(SemanticsProperties.EditableText)?.text
            ?: config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        val role = config.getOrNull(SemanticsProperties.Role)?.let { " ($it)" }.orEmpty()
        val bounds = boundsInRoot
        return if (text.isNullOrBlank()) {
            "node #$id$role at ${bounds.left.roundToInt()},${bounds.top.roundToInt()}"
        } else {
            "\"$text\"$role"
        }
    }

    private fun Color.hex(): String = String.format(Locale.US, "#%06X", toArgb() and RGB)

    private fun Double.format(): String = String.format(Locale.US, "%.2f", this)

    private fun fail(where: String, what: String, problems: List<String>) {
        if (problems.isNotEmpty()) {
            throw AssertionError("$where: ${problems.size} problem(s) with $what:\n  " + problems.joinToString("\n  "))
        }
    }

    private const val LUMINANCE_OFFSET = 0.05
    private const val EPSILON = 0.005
    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val OPAQUE = 0xFF
    private const val BYTE = 0xFF
    private const val RGB = 0xFFFFFF
}
