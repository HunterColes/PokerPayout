package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.LocalReducedMotion
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.components.CardEmphasis
import com.huntercoles.pokerpayout.core.design.components.CardFace
import com.huntercoles.pokerpayout.core.design.components.CardFaceSize
import com.huntercoles.pokerpayout.core.design.components.CardSlot
import com.huntercoles.pokerpayout.core.design.components.CardSlotState
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.RunOutState
import com.huntercoles.pokerpayout.tools.presentation.Street

/**
 * Run it out's board: dealt cards face up (the street just dealt with a gold edge, flipping in),
 * the rest face down, captioned FLOP / TURN / RIVER as on the odds board. Large cards when they fit, medium on
 * narrow phones.
 */
@Composable
internal fun RunBoard(runOut: RunOutState, fourColour: Boolean) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        val size = if (maxWidth >= LARGE_BOARD_WIDTH) CardFaceSize.Large else CardFaceSize.Medium
        val board = runOut.board
        val fresh = if (runOut.dealt == 0) IntRange.EMPTY else previousBoardSize(runOut.street) until board.size
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
            BOARD_GROUPS.forEachIndexed { group, slots ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        slots.forEach { i -> DealtCard(board.getOrNull(i), i in fresh, size, fourColour) }
                    }
                    val street = Street.entries[group + 1]
                    val justDealt = runOut.dealt > 0 && runOut.street == street
                    // The odds board's captions ("FLOP", "RIVER"); "JUST DEALT" goes on its own line.
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val colour = if (justDealt) PokerColors.PokerGold else PokerColors.Chalk
                        StreetCaption(streetName(street.boardCards), colour)
                        if (justDealt) StreetCaption(stringResource(R.string.odds_runout_just_dealt), colour)
                    }
                }
            }
        }
    }
}

/** A dealt card (flipping in when it's new, unless motion is reduced) or a face-down card. */
@Composable
private fun DealtCard(card: Int?, fresh: Boolean, size: CardFaceSize, fourColour: Boolean) {
    if (card == null) {
        CardSlot(CardSlotState.Random, size = size)
        return
    }
    val reduced = LocalReducedMotion.current
    val flip = remember(card) { Animatable(if (fresh && !reduced) 0f else 1f) }
    LaunchedEffect(card) { if (flip.value < 1f) flip.animateTo(1f, tween(FLIP_MS)) }
    CardFace(
        card = card.toPlayingCard(),
        size = size,
        emphasis = if (fresh) CardEmphasis.New else CardEmphasis.Normal,
        fourColour = fourColour,
        modifier = Modifier.graphicsLayer { scaleX = flip.value },
    )
}

/** How many board cards were out before [street] was dealt. */
private fun previousBoardSize(street: Street): Int = when (street) {
    Street.PREFLOP, Street.FLOP -> 0
    Street.TURN -> Street.FLOP.boardCards
    Street.RIVER -> Street.TURN.boardCards
}

private const val FLIP_MS = 250

/** Five large cards and their gaps: 5 x 58 + 4 x 6, plus the group gaps. */
private val LARGE_BOARD_WIDTH = 340.dp
