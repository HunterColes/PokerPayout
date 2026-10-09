package com.huntercoles.pokerpayout.tools.presentation.composable

import android.view.View
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.PokerTopBar
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.ShotClockIntent
import com.huntercoles.pokerpayout.tools.presentation.ShotClockUiState
import com.huntercoles.pokerpayout.tools.presentation.ShotClockViewModel
import com.huntercoles.pokerpayout.tools.shotclock.ShotClockPhase

/** The shot clock route ("Shot clock" in the Tools list). The screen stays on while a decision counts. */
@Composable
fun ShotClockRoute(onBack: () -> Unit, viewModel: ShotClockViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.phase == ShotClockPhase.Running) KeepScreenOn()
    ShotClockContent(state = state, onIntent = viewModel::acceptIntent, onBack = onBack)
}

/**
 * Keeps the screen on while it is composed, through a view of its own: the tournament clock keeps
 * the screen on through the app's root view, and this never turns that off.
 */
@Composable
private fun KeepScreenOn() {
    AndroidView(factory = { context -> View(context).apply { keepScreenOn = true } }, modifier = Modifier.size(0.dp))
}

/** Below the phone width, gutters narrow to 12 dp (design spec, section 4). */
private val SmallWidth = 360.dp
private val SmallGutter = 12.dp

/** The face's largest size, and its share of a tall window's height. */
private val FaceMax = 400.dp
private const val FACE_HEIGHT_SHARE = 0.5f

/** Windows this much wider than tall (a phone on its side) put the face beside the controls. */
private const val WIDE_RATIO = 1.25f

/** In a wide window, the face pane's largest share of the width. */
private const val FACE_PANE_SHARE = 0.5f

/**
 * The shot clock: one huge face to tap for each decision, counting down the time to act, with a
 * warning at ten seconds and at zero. Under it, Pause and Reset, the time to act (30, 45 or 60 s)
 * and the time bank: cards each player can play for 30 s more. On a phone held upright the face
 * stays put and the rest scrolls under it; on its side, the face and the rest are side by side.
 */
@Composable
fun ShotClockContent(
    state: ShotClockUiState,
    onIntent: (ShotClockIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(PokerColors.PokerBlack)) {
        val cards = if (state.cardsEach == 0) {
            stringResource(R.string.shot_clock_no_time_bank)
        } else {
            pluralStringResource(R.plurals.shot_clock_cards_each, state.cardsEach, state.cardsEach)
        }
        PokerTopBar(
            title = stringResource(R.string.shot_clock_title),
            subtitle = stringResource(R.string.shot_clock_subtitle, state.seconds, cards),
            onBack = onBack,
        )
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val gutter = if (maxWidth < SmallWidth) SmallGutter else PokerDimens.Gutter
            val controls: @Composable ColumnScope.() -> Unit = {
                ShotClockActions(state, onIntent)
                TimeToActSection(state, onIntent)
                TimeBankSection(state, onIntent)
                ShotClockNote()
            }
            if (maxWidth >= maxHeight * WIDE_RATIO) {
                val face = minOf(maxHeight - FaceStatusRoom, maxWidth * FACE_PANE_SHARE - gutter * 2, FaceMax)
                Row(Modifier.fillMaxSize().padding(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(gutter)) {
                    FacePane(state, onIntent, face, Modifier.fillMaxHeight().verticalScroll(rememberScrollState()))
                    ControlsPane(Modifier.weight(1f), controls)
                }
            } else {
                val face = minOf(maxWidth - gutter * 2, maxHeight * FACE_HEIGHT_SHARE, FaceMax)
                Column(Modifier.fillMaxSize().padding(horizontal = gutter)) {
                    FacePane(state, onIntent, face, Modifier.fillMaxWidth())
                    ControlsPane(Modifier.weight(1f), controls)
                }
            }
        }
    }
}

/** Room left under the face for its status line, in the side-by-side layout. */
private val FaceStatusRoom = 72.dp

/** The face and the line under it. Upright they never scroll away; on its side the pane scrolls if it must. */
@Composable
private fun FacePane(state: ShotClockUiState, onIntent: (ShotClockIntent) -> Unit, face: Dp, modifier: Modifier) {
    Column(
        modifier = modifier.padding(top = 4.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ShotClockFace(state = state, size = face, onTap = { onIntent(ShotClockIntent.NextDecision) })
        ShotClockStatus(state = state, modifier = Modifier.width(face))
    }
}

/** The controls under (or beside) the face: one scrolling column. */
@Composable
private fun ControlsPane(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = 4.dp, bottom = PokerDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}
