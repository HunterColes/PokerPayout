package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.dealers.BuiltInGame
import com.huntercoles.pokerpayout.tools.dealers.GameChoice
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The wheel: a slice per game on felt greens, each named along its middle, a brass rim with a peg
 * between slices, and a gold pointer at the top. [rotation] (degrees, clockwise) is the screen's
 * to turn; [highlight] is the slice that won, drawn in gold once the wheel has stopped. The names
 * are part of the drawing (the list of games under the wheel carries them for TalkBack and large
 * text); the whole wheel is one button that spins it.
 */
@Suppress("LongParameterList") // the games, where the wheel stands, the slice that won, its size, the tap, a modifier
@Composable
internal fun GameWheelDial(
    wheel: List<GameChoice>,
    rotation: Float,
    highlight: Int,
    size: Dp,
    onSpin: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val labels = wheel.map { gameWheelLabel(it) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }
    val minFontPx = with(density) { MinFont.toPx() }
    val layouts = remember(labels, sizePx, minFontPx, highlight) {
        WheelGeometry(sizePx, labels.size, minFontPx).let { geometry ->
            labels.mapIndexed { index, label -> geometry.measure(measurer, label, density, gold = index == highlight) }
        }
    }
    val description = pluralStringResource(R.plurals.dealers_wheel_description, wheel.size, wheel.size)
    val spinLabel = stringResource(R.string.dealers_spin)
    val tap = if (onSpin == null) Modifier else Modifier.clickable(onClickLabel = spinLabel, role = Role.Button, onClick = onSpin)
    Canvas(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(tap)
            .semantics { contentDescription = description },
    ) {
        val geometry = WheelGeometry(this.size.minDimension, wheel.size, minFontPx)
        rotate(rotation) { drawSlices(geometry, layouts, highlight) }
        drawRim(geometry, rotation)
        drawPointer(geometry)
    }
}

/** The sizes of the wheel's parts, from its diameter in pixels. */
private class WheelGeometry(diameter: Float, val slices: Int, minFontPx: Float) {
    val center = diameter / 2
    val rim = diameter * RIM_SHARE
    val pointer = diameter * POINTER_SHARE
    val radius = center - rim / 2
    val face = center - rim
    val hub = diameter * HUB_SHARE
    val sweep = if (slices == 0) FULL_TURN else FULL_TURN / slices

    private val pad = diameter * LABEL_PAD_SHARE

    /** A slice's width at [r] from the middle. */
    private fun chord(r: Float): Float = 2 * r * sin(PI / max(slices, 2)).toFloat()

    /** The labels' font: a share of the face, but no taller than the slice is wide halfway out, and never tiny. */
    val fontPx = min(face * FONT_SHARE, chord((hub + face) / 2) * LINE_FILL).coerceAtLeast(minFontPx)

    /** Where a label ends: just inside the rim. */
    val labelEnd = face - pad

    /** A label's longest: from where the slice is first wide enough for a line, out to [labelEnd]. */
    val labelLength = labelEnd - max(hub + pad, fontPx * LINE_HEIGHT / (chord(1f).coerceAtLeast(MIN_CHORD)))

    fun measure(measurer: TextMeasurer, label: String, density: Density, gold: Boolean): TextLayoutResult {
        val style = TextStyle(
            fontFamily = PokerType.Title.fontFamily,
            fontWeight = PokerType.Title.fontWeight,
            fontSize = with(density) { fontPx.toSp() },
            color = if (gold) PokerColors.PokerGold else PokerColors.CardWhite,
        )
        return measurer.measure(
            text = label,
            style = style,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(maxWidth = labelLength.toInt().coerceAtLeast(1)),
        )
    }
}

private fun DrawScope.drawSlices(geometry: WheelGeometry, labels: List<TextLayoutResult>, highlight: Int) {
    val c = Offset(geometry.center, geometry.center)
    val box = Size(geometry.face * 2, geometry.face * 2)
    val topLeft = Offset(geometry.center - geometry.face, geometry.center - geometry.face)
    labels.forEachIndexed { index, label ->
        val start = TOP + index * geometry.sweep
        drawArc(sliceColor(index, labels.size, highlight), start, geometry.sweep, true, topLeft, box)
        // The name along the slice's middle, reading outwards, ending just inside the rim
        rotate(start + geometry.sweep / 2, pivot = c) {
            val x = geometry.center + geometry.labelEnd - label.size.width
            translate(left = x, top = geometry.center - label.size.height / 2) { drawText(label) }
        }
    }
    // Lines between the slices, then the hub
    if (labels.size > 1) {
        labels.indices.forEach { index ->
            rotate(TOP + index * geometry.sweep, pivot = c) {
                val edge = Offset(geometry.center + geometry.face, geometry.center)
                drawLine(PokerColors.PokerBlack, c, edge, geometry.rim * DIVIDER_SHARE)
            }
        }
    }
    drawCircle(PokerColors.PokerBlack, geometry.hub, c)
    drawCircle(PokerColors.DarkGold, geometry.hub, c, style = Stroke(geometry.rim * HUB_RING_SHARE))
}

private fun sliceColor(index: Int, count: Int, highlight: Int): Color = when {
    index == highlight -> PokerColors.GoldWash
    // An odd number of slices can't simply alternate: the last one gets a third green.
    count % 2 == 1 && index == count - 1 && count > 1 -> PokerColors.FeltDeep
    index % 2 == 0 -> PokerColors.FeltGreen
    else -> PokerColors.DarkGreen
}

/** The brass rim, and a gold peg at each line between slices (they turn with the wheel). */
private fun DrawScope.drawRim(geometry: WheelGeometry, rotation: Float) {
    val c = Offset(geometry.center, geometry.center)
    drawCircle(PokerColors.DarkGold, geometry.radius, c, style = Stroke(geometry.rim))
    if (geometry.slices < 2) return
    rotate(rotation, pivot = c) {
        repeat(geometry.slices) { index ->
            rotate(TOP + index * geometry.sweep, pivot = c) {
                val peg = Offset(geometry.center + geometry.radius, geometry.center)
                drawCircle(PokerColors.PokerGold, geometry.rim * PEG_SHARE, peg)
            }
        }
    }
}

/** The pointer: a gold wedge over the rim at the top, pointing in. */
private fun DrawScope.drawPointer(geometry: WheelGeometry) {
    val half = geometry.pointer / 2
    val path = Path().apply {
        moveTo(geometry.center - half, 0f)
        lineTo(geometry.center + half, 0f)
        lineTo(geometry.center, geometry.pointer * POINTER_LENGTH)
        close()
    }
    drawPath(path, PokerColors.PokerGold)
    drawPath(path, PokerColors.PokerBlack, style = Stroke(geometry.rim * DIVIDER_SHARE))
}

/** A game's name on the wheel: short ("2-7 Triple"), or a house game's own name. */
@Composable
internal fun gameWheelLabel(choice: GameChoice): String =
    choice.game?.let { stringResource(GameTexts.getValue(it).wheel) } ?: choice.houseName.orEmpty()

/** A game's full name ("2-7 Triple Draw"), or a house game's own. */
@Composable
internal fun gameName(choice: GameChoice): String =
    choice.game?.let { stringResource(GameTexts.getValue(it).name) } ?: choice.houseName.orEmpty()

/** The rules lines of one of the app's games. */
@Composable
internal fun gameRules(game: BuiltInGame): List<String> =
    LocalContext.current.resources.getStringArray(GameTexts.getValue(game).rules).toList()

/** Each game's name, its label on the wheel, and its rules. */
private class GameText(@StringRes val name: Int, @StringRes val wheel: Int, @ArrayRes val rules: Int)

private val GameTexts: Map<BuiltInGame, GameText> = mapOf(
    BuiltInGame.HoldEm to GameText(R.string.dealers_game_holdem, R.string.dealers_wheel_holdem, R.array.dealers_rules_holdem),
    BuiltInGame.Omaha to GameText(R.string.dealers_game_omaha, R.string.dealers_wheel_omaha, R.array.dealers_rules_omaha),
    BuiltInGame.OmahaHiLo to
        GameText(R.string.dealers_game_omaha_hilo, R.string.dealers_wheel_omaha_hilo, R.array.dealers_rules_omaha_hilo),
    BuiltInGame.BigO to GameText(R.string.dealers_game_big_o, R.string.dealers_wheel_big_o, R.array.dealers_rules_big_o),
    BuiltInGame.Stud to GameText(R.string.dealers_game_stud, R.string.dealers_wheel_stud, R.array.dealers_rules_stud),
    BuiltInGame.StudHiLo to
        GameText(R.string.dealers_game_stud_hilo, R.string.dealers_wheel_stud_hilo, R.array.dealers_rules_stud_hilo),
    BuiltInGame.Razz to GameText(R.string.dealers_game_razz, R.string.dealers_wheel_razz, R.array.dealers_rules_razz),
    BuiltInGame.TripleDraw to
        GameText(R.string.dealers_game_triple_draw, R.string.dealers_wheel_triple_draw, R.array.dealers_rules_triple_draw),
    BuiltInGame.Badugi to GameText(R.string.dealers_game_badugi, R.string.dealers_wheel_badugi, R.array.dealers_rules_badugi),
    BuiltInGame.Pineapple to
        GameText(R.string.dealers_game_pineapple, R.string.dealers_wheel_pineapple, R.array.dealers_rules_pineapple),
    BuiltInGame.CrazyPineapple to GameText(
        R.string.dealers_game_crazy_pineapple,
        R.string.dealers_wheel_crazy_pineapple,
        R.array.dealers_rules_crazy_pineapple,
    ),
    BuiltInGame.FiveCardDraw to GameText(
        R.string.dealers_game_five_card_draw,
        R.string.dealers_wheel_five_card_draw,
        R.array.dealers_rules_five_card_draw,
    ),
    BuiltInGame.ShortDeck to
        GameText(R.string.dealers_game_short_deck, R.string.dealers_wheel_short_deck, R.array.dealers_rules_short_deck),
    BuiltInGame.Irish to GameText(R.string.dealers_game_irish, R.string.dealers_wheel_irish, R.array.dealers_rules_irish),
    BuiltInGame.Courchevel to
        GameText(R.string.dealers_game_courchevel, R.string.dealers_wheel_courchevel, R.array.dealers_rules_courchevel),
    BuiltInGame.FollowTheQueen to GameText(
        R.string.dealers_game_follow_the_queen,
        R.string.dealers_wheel_follow_the_queen,
        R.array.dealers_rules_follow_the_queen,
    ),
    BuiltInGame.HighChicago to
        GameText(R.string.dealers_game_high_chicago, R.string.dealers_wheel_high_chicago, R.array.dealers_rules_high_chicago),
)

/**
 * The angle (degrees, 0 to 360) at which the wheel rests with slice [index] of [slices] under the
 * pointer, [landing] (a share of a slice) from the slice's middle.
 */
internal fun restingAngle(index: Int, slices: Int, landing: Float): Float {
    if (slices == 0 || index < 0) return 0f
    val sweep = FULL_TURN / slices
    return ((-(index + HALF + landing) * sweep) % FULL_TURN + FULL_TURN) % FULL_TURN
}

/** Where a spin from [from] ends: always forwards, [SPIN_TURNS] whole turns and then on to [resting]. */
internal fun spinTarget(from: Float, resting: Float): Float {
    val ahead = ((resting - from) % FULL_TURN + FULL_TURN) % FULL_TURN
    return from + SPIN_TURNS * FULL_TURN + ahead
}

private const val FULL_TURN = 360f
private const val HALF = 0.5f
private const val TOP = -90f
private const val SPIN_TURNS = 5

/** Shares of the wheel's diameter. */
private const val RIM_SHARE = 0.035f
private const val POINTER_SHARE = 0.09f
private const val POINTER_LENGTH = 1.1f
private const val HUB_SHARE = 0.07f
private const val LABEL_PAD_SHARE = 0.02f

/** Shares of the rim's width. */
private const val DIVIDER_SHARE = 0.35f
private const val HUB_RING_SHARE = 0.6f
private const val PEG_SHARE = 0.32f

/** A label's font: a share of the wheel's radius, but never taller than its slice, and never tiny. */
private const val FONT_SHARE = 0.085f
private const val LINE_FILL = 0.62f
private val MinFont = 9.dp

/** A line of a label is about this many times its font size tall. */
private const val LINE_HEIGHT = 1.25f
private const val MIN_CHORD = 0.01f
