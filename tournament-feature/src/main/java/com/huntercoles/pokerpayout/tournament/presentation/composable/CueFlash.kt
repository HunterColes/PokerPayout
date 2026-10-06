package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.LocalReducedMotion
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.tournament.domain.clock.SilentCue
import kotlinx.coroutines.delay

/** One flash to show; [id] grows with each, so the same cue twice flashes twice. */
@Immutable
data class FlashRequest(val cue: SilentCue, val id: Int)

/**
 * PP-083: a brief gold flash over the clock when the level changes (two pulses) or one minute is left
 * (one), for a quiet room. Under Reduce motion nothing blinks: a steady gold frame shows for a moment
 * instead. Between cues it draws nothing; it never takes a touch, and TalkBack hears nothing from it
 * (the clock's own level line is a live region).
 */
@Composable
internal fun CueFlash(flash: FlashRequest?, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val glow = remember { Animatable(0f) }
    var framed by remember { mutableStateOf(false) }
    LaunchedEffect(flash) {
        val cue = flash?.cue ?: return@LaunchedEffect
        when (FlashTiming.style(reduced)) {
            FlashStyle.FRAME -> {
                framed = true
                delay(FlashTiming.STEADY_MILLIS)
                framed = false
            }
            FlashStyle.PULSE -> repeat(FlashTiming.pulses(cue)) {
                glow.animateTo(FlashTiming.PEAK_ALPHA, tween(FlashTiming.RISE_MILLIS))
                glow.animateTo(0f, tween(FlashTiming.FALL_MILLIS))
            }
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .then(if (framed) Modifier.border(FRAME_WIDTH, PokerColors.PokerGold) else Modifier)
            .drawBehind {
                val alpha = glow.value
                if (alpha > 0f) drawRect(PokerColors.PokerGold.copy(alpha = alpha))
            },
    )
}

/** How a flash shows: gold pulses, or (Reduce motion) a steady gold frame that doesn't blink. */
enum class FlashStyle { PULSE, FRAME }

/**
 * How the flash moves. At most two pulses of 0.6 s, well under the three flashes a second that
 * photosensitivity guidance (WCAG 2.3.1) allows, and a soft gold, not a white strobe.
 */
internal object FlashTiming {
    const val PEAK_ALPHA = 0.35f
    const val RISE_MILLIS = 150
    const val FALL_MILLIS = 450

    /** Under Reduce motion: how long the steady frame stays. */
    const val STEADY_MILLIS = 1_500L

    fun style(reducedMotion: Boolean): FlashStyle = if (reducedMotion) FlashStyle.FRAME else FlashStyle.PULSE

    fun pulses(cue: SilentCue): Int = when (cue) {
        SilentCue.LEVEL_CHANGE -> 2
        SilentCue.ONE_MINUTE -> 1
    }
}

private val FRAME_WIDTH = 6.dp
