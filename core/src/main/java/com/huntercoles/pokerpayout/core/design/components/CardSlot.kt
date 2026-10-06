@file:Suppress("MatchingDeclarationName") // CardSlotState is the one class; CardSlot the component

package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.design.LocalReducedMotion
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons

/** An empty place for a card: still to fill, the next to fill (gold, pulsing), or random (a card back). */
enum class CardSlotState { Empty, Next, Random }

/**
 * A place for a card that isn't there yet. [CardSlotState.Next] pulses its gold outline (a static,
 * thicker outline under Reduce motion). [CardSlotState.Random] is a felt card back with "?".
 */
@Composable
fun CardSlot(
    state: CardSlotState,
    modifier: Modifier = Modifier,
    size: CardFaceSize = CardFaceSize.Medium,
    contentDescription: String? = null,
) {
    val description = contentDescription ?: stringResource(
        when (state) {
            CardSlotState.Empty -> R.string.card_slot_empty
            CardSlotState.Next -> R.string.card_slot_next
            CardSlotState.Random -> R.string.card_slot_random
        },
    )
    val reducedMotion = LocalReducedMotion.current
    val pulse = if (state == CardSlotState.Next && !reducedMotion) nextSlotAlpha() else 1f
    Box(
        modifier = modifier
            .size(size.size)
            .clearAndSetSemantics { this.contentDescription = description }
            .then(slotDecoration(state, size.corner, pulse, outlineWidth = if (reducedMotion) 3.dp else 2.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            CardSlotState.Empty -> Unit
            CardSlotState.Next -> Icon(
                imageVector = PokerIcons.Plus,
                contentDescription = null,
                tint = PokerColors.PokerGold,
                modifier = Modifier.size(20.dp),
            )
            CardSlotState.Random -> Text(
                text = "?",
                color = PokerColors.PokerGold,
                style = PokerType.CardRank.copy(fontSize = 20.dp.fixedSp(), lineHeight = 20.dp.fixedSp()),
            )
        }
    }
}

private fun slotDecoration(state: CardSlotState, corner: Dp, pulse: Float, outlineWidth: Dp): Modifier = when (state) {
    CardSlotState.Empty -> Modifier.drawBehind {
        drawSlotOutline(PokerColors.ChalkDim, 1.5.dp.toPx(), corner.toPx(), dashed = true)
    }
    CardSlotState.Next -> Modifier.drawBehind {
        drawHalo(corner.toPx())
        drawSlotOutline(PokerColors.PokerGold.copy(alpha = pulse), outlineWidth.toPx(), corner.toPx(), dashed = false)
    }
    CardSlotState.Random -> Modifier
        .clip(RoundedCornerShape(corner))
        .drawWithContent {
            drawFeltStripes()
            drawSlotOutline(PokerColors.DarkGold, 1.5.dp.toPx(), corner.toPx(), dashed = false)
            drawContent()
        }
}

/** The next slot's outline pulses from 0.4 to 1.0 alpha and back every 1.2 s (not under Reduce motion). */
@Composable
private fun nextSlotAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "next slot")
    val alpha by transition.animateFloat(
        initialValue = PULSE_MIN_ALPHA,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_HALF_PERIOD_MS, easing = LinearEasing), RepeatMode.Reverse),
        label = "next slot alpha",
    )
    return alpha
}

private fun DrawScope.drawSlotOutline(color: Color, width: Float, corner: Float, dashed: Boolean) {
    drawRoundRect(
        color = color,
        topLeft = Offset(width / 2, width / 2),
        size = Size(size.width - width, size.height - width),
        cornerRadius = CornerRadius(corner),
        style = Stroke(width, pathEffect = if (dashed) dashes() else null),
    )
}

private fun DrawScope.dashes(): PathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))

private fun DrawScope.drawHalo(corner: Float) {
    val spread = 4.dp.toPx()
    drawRoundRect(
        color = PokerColors.PokerGold.copy(alpha = HALO_ALPHA),
        topLeft = Offset(-spread, -spread),
        size = Size(size.width + spread * 2, size.height + spread * 2),
        cornerRadius = CornerRadius(corner + spread),
    )
}

/** A felt card back: 45-degree stripes of DarkGreen and FeltGreen, 4 dp each. */
private fun DrawScope.drawFeltStripes() {
    drawRect(PokerColors.FeltGreen)
    val band = 4.dp.toPx()
    val stroke = band / SQRT2
    clipRect {
        var x = -size.height
        while (x < size.width) {
            drawLine(PokerColors.DarkGreen, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = stroke * 2)
            x += band * 2 * SQRT2
        }
    }
}

private const val HALO_ALPHA = 0.16f
private const val PULSE_MIN_ALPHA = 0.4f
private const val PULSE_HALF_PERIOD_MS = 600
private const val SQRT2 = 1.4142135f
