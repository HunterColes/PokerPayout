package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.pokerpayout.core.design.LocalReducedMotion
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerSheet
import com.huntercoles.pokerpayout.core.design.components.PokerTopBar
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.DealersChoiceIntent
import com.huntercoles.pokerpayout.tools.presentation.DealersChoiceUiState
import com.huntercoles.pokerpayout.tools.presentation.DealersChoiceViewModel

/** The dealer's choice route ("Dealer's choice" in the Tools list). */
@Composable
fun DealersChoiceRoute(onBack: () -> Unit, viewModel: DealersChoiceViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DealersChoiceContent(state = state, onIntent = viewModel::acceptIntent, onBack = onBack)
}

/** Below the phone width, gutters narrow to 12 dp (design spec, section 4). */
private val SmallWidth = 360.dp
private val SmallGutter = 12.dp

/** The wheel's largest size, and its share of a tall window's height. */
private val WheelMax = 420.dp
private const val WHEEL_HEIGHT_SHARE = 0.55f

/** Windows this much wider than tall (a phone on its side) put the wheel beside the rest. */
private const val WIDE_RATIO = 1.25f
private const val WHEEL_PANE_SHARE = 0.5f

/** A spin: a quick start and a long slow finish, about four seconds. */
private const val SPIN_MILLIS = 4_000
private val SpinEasing = CubicBezierEasing(0.15f, 0.85f, 0.25f, 1f)

/**
 * Dealer's choice: the wheel of games and its Spin button, the game it picked with that game's
 * rules, then the games on the wheel (the app's to switch on and off, house games to add and
 * remove) and every game's rules in a sheet. Upright, one scrolling column; on its side, the wheel
 * beside the rest. Under Reduce motion the wheel doesn't turn: the pick shows at once.
 *
 * @param rulesOpen whether the rules sheet starts open (screenshots of it).
 */
@Composable
fun DealersChoiceContent(
    state: DealersChoiceUiState,
    onIntent: (DealersChoiceIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    rulesOpen: Boolean = false,
) {
    var showRules by rememberSaveable { mutableStateOf(rulesOpen) }
    val turn = rememberWheelTurn(state)
    val spin = { onIntent(DealersChoiceIntent.Spin) }
    Column(modifier.fillMaxSize().background(PokerColors.PokerBlack)) {
        PokerTopBar(
            title = stringResource(R.string.dealers_title),
            subtitle = pluralStringResource(R.plurals.dealers_subtitle, state.wheel.size, state.wheel.size),
            onBack = onBack,
        )
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val gutter = if (maxWidth < SmallWidth) SmallGutter else PokerDimens.Gutter
            val rest: @Composable ColumnScope.() -> Unit = {
                GameResultCard(state, revealed = turn.revealed)
                GamesSection(state, onIntent, onShowRules = { showRules = true })
            }
            if (maxWidth >= maxHeight * WIDE_RATIO) {
                val size = minOf(maxHeight - WheelRoom, maxWidth * WHEEL_PANE_SHARE - gutter * 2, WheelMax)
                val pane = maxOf(size, minOf(SpinMinWidth, maxWidth * WHEEL_PANE_SHARE - gutter * 2))
                Row(Modifier.fillMaxSize().padding(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(gutter)) {
                    Pane(Modifier.width(pane)) { WheelAndSpin(state, turn, size, spin) }
                    Pane(Modifier.weight(1f), content = rest)
                }
            } else {
                val size = minOf(maxWidth - gutter * 2, maxHeight * WHEEL_HEIGHT_SHARE, WheelMax)
                Pane(Modifier.fillMaxWidth().padding(horizontal = gutter)) {
                    WheelAndSpin(state, turn, size, spin)
                    rest()
                }
            }
        }
    }
    if (showRules) {
        PokerSheet(onDismissRequest = { showRules = false }, title = stringResource(R.string.dealers_all_rules)) {
            AllGameRules()
        }
    }
}

/** On its side, the wheel takes the pane's height less this; the Spin button scrolls under it. */
private val WheelRoom = 12.dp

/** The Spin button is at least this wide, wider than a small wheel, so its words fit. */
private val SpinMinWidth = 200.dp

/** One scrolling column. */
@Composable
private fun Pane(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(top = 4.dp, bottom = PokerDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** The wheel and its gold Spin button; while it turns, neither takes a tap. */
@Composable
private fun WheelAndSpin(state: DealersChoiceUiState, turn: WheelTurn, size: Dp, spin: () -> Unit) {
    val canSpin = state.canSpin && turn.revealed
    GameWheelDial(
        wheel = state.wheel,
        rotation = turn.rotation,
        highlight = if (turn.revealed) state.pickIndex else -1,
        size = size,
        onSpin = if (canSpin) spin else null,
    )
    PokerButton(
        text = stringResource(R.string.dealers_spin),
        onClick = spin,
        icon = PokerIcons.Wheel,
        enabled = canSpin,
        modifier = Modifier.fillMaxWidth(),
    )
    if (!state.canSpin) NoteBox(ok = false) { NoteLineText(stringResource(R.string.dealers_too_few)) }
}

/** Where the wheel stands, and whether the last spin has finished (so its pick can show). */
private class WheelTurn(val rotation: Float, val revealed: Boolean)

/**
 * Turns the wheel once for each new spin, forwards, several whole turns and on to the pick, and
 * says when it has stopped. Under Reduce motion it jumps straight there. A spin already shown
 * (after the screen turns, say) isn't played again.
 */
@Composable
private fun rememberWheelTurn(state: DealersChoiceUiState): WheelTurn {
    val reduced = LocalReducedMotion.current
    val resting = restingAngle(state.pickIndex, state.wheel.size, state.landing)
    val angle = remember { Animatable(resting) }
    var shown by rememberSaveable { mutableIntStateOf(state.spins) }
    val wheel = state.wheel.map { it.id }
    LaunchedEffect(state.spins, wheel) {
        if (state.spins > shown) {
            val target = spinTarget(angle.value, resting)
            if (reduced) angle.snapTo(target) else angle.animateTo(target, tween(SPIN_MILLIS, easing = SpinEasing))
        } else if (state.pickIndex >= 0) {
            angle.snapTo(resting)
        }
        shown = state.spins
    }
    return WheelTurn(angle.value, revealed = shown == state.spins)
}
