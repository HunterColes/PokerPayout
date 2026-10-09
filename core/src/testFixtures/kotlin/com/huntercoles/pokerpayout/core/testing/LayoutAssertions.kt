package com.huntercoles.pokerpayout.core.testing

import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.huntercoles.pokerpayout.core.design.components.MayTruncate
import org.robolectric.RuntimeEnvironment
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Layout checks that run on every cell of the device matrix, so a layout that stretches, rotates
 * or scales its text badly fails a test instead of waiting for someone to look at a picture.
 *
 * Each check collects every problem it finds and fails once, with a list.
 */
object LayoutAssertions {
    /** Rounding slack, in pixels, for comparing rectangles. */
    private const val SLACK_PX = 1f

    /**
     * No text is cut off: every line of every text node fits the node's width, the lines fit its
     * height, no line is ellipsized, no word is broken across two lines (what a too-narrow box does
     * at large font sizes: "Standa / rd"), and the text isn't pushed past the left or right edge of
     * the screen. Texts marked [MayTruncate] (a top-bar subtitle) may ellipsize.
     *
     * Holds whether or not the node is scrolled into view.
     *
     * On the contrast check's reference cell (`phone` at font 1.0) it also checks that every text
     * has the contrast WCAG AA asks of it ([AccessibilityAssertions.assertTextContrast]).
     */
    fun assertTextFits(rule: SemanticsNodeInteractionsProvider, where: String) {
        val nodes = rule.onAllNodes(hasTextLayout, useUnmergedTree = true).fetchSemanticsNodes()
        check(nodes.isNotEmpty()) { "$where: no text found; did the content render?" }
        val screenWidth = screenBounds().width
        val problems = nodes.flatMap { node ->
            val mayTruncate = node.config.getOrNull(MayTruncate) == true
            val bounds = node.unclippedBoundsInRoot()
            buildList {
                node.textLayouts().forEach { layout ->
                    val ellipsized = (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
                    // Like TextLayoutResult.hasVisualOverflow, but a sub-pixel overflow is rounding.
                    val overflows = layout.multiParagraph.width > layout.size.width + SLACK_PX ||
                        layout.multiParagraph.height > layout.size.height + SLACK_PX
                    // maxLines cut the rest off (a squeezed "FAVOURITE" shows as "FAVOU", no ellipsis).
                    if (!mayTruncate && layout.multiParagraph.didExceedMaxLines) {
                        add("${node.label()} is cut off after ${layout.lineCount} line(s), at \"${layout.visibleText()}\"")
                    }
                    if (!mayTruncate && overflows) {
                        add(
                            "${node.label()} doesn't fit: it needs ${layout.multiParagraph.width.toInt()}x" +
                                "${layout.multiParagraph.height.toInt()}px, has ${layout.size.width}x${layout.size.height}px",
                        )
                    }
                    if (!mayTruncate && ellipsized) add("${node.label()} is ellipsized")
                    layout.brokenWord()?.let { add("${node.label()} breaks the word '$it' across two lines") }
                }
                if (bounds.left < -SLACK_PX || bounds.right > screenWidth + SLACK_PX) {
                    add("${node.label()} runs off the side of the screen (x ${bounds.left}..${bounds.right} of $screenWidth)")
                }
            }
        }
        failIfAny(where, "text that doesn't fit", problems)
        if (AccessibilityAssertions.isReferenceCell()) AccessibilityAssertions.assertTextContrast(rule, where)
    }

    /**
     * No text that is fully in view is clipped by an ancestor (a `clip`, a fixed-size box, a card).
     * "In view" means inside the screen and inside every scrolling container around the text, so
     * run it at each scroll position: text scrolled out of view is judged when it scrolls back in.
     */
    fun assertVisibleTextUnclipped(rule: SemanticsNodeInteractionsProvider, where: String) {
        val nodes = rule.onAllNodes(hasTextLayout, useUnmergedTree = true).fetchSemanticsNodes()
        val problems = nodes.mapNotNull { node ->
            val unclipped = node.unclippedBoundsInRoot()
            val visible = node.boundsInRoot
            val inView = node.viewport().fullyContains(unclipped)
            val clipped = !visible.approximately(unclipped)
            if (inView && clipped) "${node.label()} is clipped to $visible (laid out at $unclipped)" else null
        }
        failIfAny(where, "clipped text", problems)
    }

    /**
     * Every clickable, toggleable or selectable node offers at least [min] by [min] of touch area,
     * and no two touch areas overlap.
     *
     * With [strict], the node itself must be at least [min] in both directions (the design
     * system's own components lay out the full 48 dp box). Otherwise a smaller node counts as
     * Compose's hit test sees it: expanded around its centre to [min], which only works if nothing
     * else is within reach, so the overlap check still catches crowded targets. Overlap is judged
     * on the part of each target that is in view (a target scrolled under a bar can't be tapped).
     *
     * Every target also needs a name TalkBack can read, and every text field a label
     * ([AccessibilityAssertions.assertNamed]).
     */
    fun assertTouchTargets(rule: SemanticsNodeInteractionsProvider, where: String, strict: Boolean, min: Dp = 48.dp) {
        val nodes = rule.onAllNodes(hasClick, useUnmergedTree = true).fetchSemanticsNodes()
        if (nodes.isEmpty()) return
        val minPx = with(nodes.first().layoutInfo.density) { min.toPx() }
        val targets = nodes.map { it to it.touchArea(if (strict) 0f else minPx) }
        val tooSmall = targets.mapNotNull { (node, area) ->
            if (area.width + SLACK_PX < minPx || area.height + SLACK_PX < minPx) {
                "${node.label()} has a ${area.width.toDp(node)} x ${area.height.toDp(node)} touch target"
            } else {
                null
            }
        }
        val reachable = targets.map { (node, area) -> node to area.intersect(node.viewport()) }
        val overlapping = reachable.indices.flatMap { i ->
            (i + 1 until reachable.size).mapNotNull { j ->
                val (a, areaA) = reachable[i]
                val (b, areaB) = reachable[j]
                val overlap = areaA.intersect(areaB)
                val overlaps = overlap.width > SLACK_PX && overlap.height > SLACK_PX
                if (overlaps && !a.isAncestorOf(b) && !b.isAncestorOf(a)) {
                    "${a.label()} and ${b.label()} have overlapping touch targets"
                } else {
                    null
                }
            }
        }
        failIfAny(where, "touch targets", tooSmall + overlapping)
        AccessibilityAssertions.assertNamed(rule, where)
    }

    private val hasTextLayout = SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)

    /**
     * The node's text laid out again, exactly as its layout did: same text, style, line and
     * overflow settings, and the constraints the node was measured with. (The result that
     * semantics hands out in Compose 1.7 skips resolving style defaults, so a text without an
     * explicit font size comes back laid out at 14 px; measuring again gives the real layout.)
     */
    private fun SemanticsNode.textLayouts(): List<TextLayoutResult> {
        val reported = mutableListOf<TextLayoutResult>()
        config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(reported)
        val density = layoutInfo.density
        return reported.map { result ->
            val input = result.layoutInput
            TextMeasurer(input.fontFamilyResolver, density, input.layoutDirection, cacheSize = 0).measure(
                text = input.text,
                style = input.style,
                overflow = input.overflow,
                softWrap = input.softWrap,
                maxLines = input.maxLines,
                placeholders = input.placeholders,
                constraints = input.constraints,
                layoutDirection = input.layoutDirection,
                density = density,
                fontFamilyResolver = input.fontFamilyResolver,
            )
        }
    }

    private val hasClick = SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)

    private fun TextLayoutResult.visibleText(): String =
        layoutInput.text.text.take(getLineEnd(lineCount - 1, visibleEnd = true))

    /** The first word split between two lines (letters or digits on both sides of a line break), if any. */
    private fun TextLayoutResult.brokenWord(): String? {
        val text = layoutInput.text.text
        val breakAt = (0 until lineCount - 1).map { getLineEnd(it) }.firstOrNull { end ->
            end in 1 until text.length && text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit()
        } ?: return null
        val start = text.lastIndexOfAny(charArrayOf(' ', '\n'), breakAt) + 1
        val end = text.indexOfAny(charArrayOf(' ', '\n'), breakAt).let { if (it < 0) text.length else it }
        return text.substring(start, end)
    }

    /** The part of the screen this node can be seen in: the screen, cut down by every scrolling ancestor. */
    private fun SemanticsNode.viewport(): Rect =
        generateSequence(parent) { it.parent }
            .filter {
                it.config.contains(SemanticsProperties.VerticalScrollAxisRange) ||
                    it.config.contains(SemanticsProperties.HorizontalScrollAxisRange)
            }
            .fold(screenBounds()) { view, scroller -> view.intersect(scroller.boundsInRoot) }

    private fun Rect.fullyContains(other: Rect): Boolean =
        other.left >= left - SLACK_PX && other.top >= top - SLACK_PX &&
            other.right <= right + SLACK_PX && other.bottom <= bottom + SLACK_PX

    private fun failIfAny(where: String, what: String, problems: List<String>) {
        if (problems.isNotEmpty()) {
            throw AssertionError("$where: ${problems.size} problem(s) with $what:\n  " + problems.joinToString("\n  "))
        }
    }

    private fun Rect.approximately(other: Rect): Boolean =
        abs(left - other.left) <= SLACK_PX && abs(top - other.top) <= SLACK_PX &&
            abs(right - other.right) <= SLACK_PX && abs(bottom - other.bottom) <= SLACK_PX

    private fun SemanticsNode.touchArea(minPx: Float): Rect {
        val bounds = unclippedBoundsInRoot()
        val growX = max(0f, minPx - bounds.width) / 2
        val growY = max(0f, minPx - bounds.height) / 2
        return Rect(bounds.left - growX, bounds.top - growY, bounds.right + growX, bounds.bottom + growY)
    }

    private fun SemanticsNode.isAncestorOf(other: SemanticsNode): Boolean =
        generateSequence(other.parent) { it.parent }.any { it.id == id }

    private fun Float.toDp(node: SemanticsNode): String =
        with(node.layoutInfo.density) { String.format(Locale.US, "%.1fdp", toDp().value) }

    private fun SemanticsNode.label(): String {
        val text = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ")
            ?: config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
            ?: config.getOrNull(SemanticsProperties.EditableText)?.text
            ?: children.firstNotNullOfOrNull { child -> child.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") }
            ?: "node #$id"
        return "\"$text\""
    }
}

/** The node's laid-out bounds in the root, not clipped by its ancestors or the screen. */
fun SemanticsNode.unclippedBoundsInRoot(): Rect =
    Rect(positionInRoot, Size(size.width.toFloat(), size.height.toFloat()))

/**
 * The app's window, in pixels: the content area of the resumed activity, which is the configured
 * screen minus the status and navigation bars that Robolectric draws. (The composition's root
 * only wraps its content, so it can be smaller than this.) Falls back to the whole display.
 */
fun screenBounds(): Rect {
    val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
    val content = activity?.findViewById<View>(android.R.id.content)
    if (content != null && content.width > 0 && content.height > 0) {
        return Rect(Offset.Zero, Size(content.width.toFloat(), content.height.toFloat()))
    }
    val metrics = RuntimeEnvironment.getApplication().resources.displayMetrics
    return Rect(Offset.Zero, Size(metrics.widthPixels.toFloat(), metrics.heightPixels.toFloat()))
}
