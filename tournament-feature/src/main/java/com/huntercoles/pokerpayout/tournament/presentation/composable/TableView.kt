package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.LocalReducedMotion
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.LocalShellSnackbars
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerPill
import com.huntercoles.pokerpayout.core.design.components.PokerPillTone
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.presentation.LocalTableKnockouts
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.presentation.TimerIntent
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import kotlinx.coroutines.delay

/**
 * S3: the clock for a phone propped against the chip tray (PP-025). Black, no tabs, no system bars,
 * the screen kept on. Time left fills the left 55%, fitted to the width (Barlow Condensed is about
 * 2.2 em for "12:41", so 780 x 360 dp gives about 164 sp); blinds and ante on the right, NEXT
 * labelled and dimmer; the numbers players ask about along the foot. Pause and exit fade to 40% after
 * 3 s and come back on any touch.
 *
 * It is a full-screen state of the Tournament tab (no `Dialog` window), shown when a phone is turned
 * sideways with a clock running, or with ⤢; ✕ leaves it.
 *
 * PP-135: Knock out, beside pause, opens the Bank's knockout over the clock ([LocalTableKnockouts]):
 * who is out, then who knocked them out. While it is open the clock is only to look at (TalkBack
 * skips it) and Back puts the knockout away. The snackbar's Undo shows bottom left; the players left
 * say when it is the bubble.
 *
 * PP-111: a big moment of the night shows across the top ([MomentSlot]) and the digits make room
 * for it. The last knockout opens the champion's screen ([winner]) in place of the clock (Back or ✕
 * closes it); a mystery envelope drawn by that knockout still shows over it first.
 */
@Composable
internal fun TableViewContent(
    uiState: TimerUiState,
    onIntent: (TimerIntent) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    winner: WinnerPane? = null,
) {
    val knockouts = LocalTableKnockouts.current
    var knockoutAsked by rememberSaveable { mutableStateOf(false) }
    val canKnockOut = knockouts != null && uiState.table.playersLeft > 1
    // Open until it closes itself: the last knockout's envelope still shows once one player is left
    val knockoutOpen = knockoutAsked && knockouts != null
    val champion = winner?.takeIf { uiState.winnerOpen }
    BackHandler(enabled = champion != null) { onIntent(TimerIntent.CloseWinner) }
    BackHandler(enabled = knockoutOpen) { knockoutAsked = false }
    BoxWithConstraints(modifier.fillMaxSize().background(PokerColors.PokerBlack)) {
        if (champion != null) {
            // Under the knockout the screen is only to look at (TalkBack skips it)
            WinnerContent(champion.model, champion.onAction, if (knockoutOpen) Modifier.clearAndSetSemantics {} else Modifier)
        } else {
            val openKnockout = { knockoutAsked = true }
            TableClock(uiState, onIntent, onExit, onKnockOut = openKnockout.takeIf { canKnockOut }, quiet = knockoutOpen)
        }
        if (knockoutOpen) knockouts?.Panel(onClose = { knockoutAsked = false })
        TableSnackbars()
    }
}

/**
 * The clock itself: a big moment across the top, the time and the blinds, the numbers and the
 * controls along the foot. [quiet] while the knockout is over it: only to look at.
 */
@Composable
private fun TableClock(
    uiState: TimerUiState,
    onIntent: (TimerIntent) -> Unit,
    onExit: () -> Unit,
    onKnockOut: (() -> Unit)?,
    quiet: Boolean,
) {
    var touches by remember { mutableIntStateOf(0) }
    var dimmed by remember { mutableStateOf(false) }
    LaunchedEffect(touches) {
        dimmed = false
        delay(CONTROLS_VISIBLE_MILLIS)
        dimmed = true
    }
    val reduced = LocalReducedMotion.current
    val controlsAlpha by animateFloatAsState(
        targetValue = if (dimmed) DIMMED_ALPHA else 1f,
        animationSpec = if (reduced) snap() else tween(FADE_MILLIS),
        label = "tableControls",
    )
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        touches++
                    }
                }
            }
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .then(if (quiet) Modifier.clearAndSetSemantics {} else Modifier),
    ) {
        val width = maxWidth
        val height = maxHeight
        // PP-111: a short window at large text keeps a moment to its title, so the digits keep room
        val brief = height < BRIEF_BELOW * LocalDensity.current.fontScale
        Column(Modifier.fillMaxSize()) {
            MomentSlot(uiState.momentSlot, onIntent, style = if (brief) MomentStyle.Brief else MomentStyle.Strip)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (width > height) {
                    LandscapeBody(uiState, heroCap = height * HERO_MAX_HEIGHT_LANDSCAPE, width = width)
                } else {
                    PortraitBody(uiState, heroCap = height * HERO_MAX_HEIGHT_PORTRAIT, width = width)
                }
            }
            TableFooter(uiState, controlsAlpha, onIntent, onExit, onKnockOut)
        }
    }
}

@Composable
private fun LandscapeBody(uiState: TimerUiState, heroCap: Dp, width: Dp) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        // The digits take whatever height the eyebrow and the bar leave, so large text never squeezes them.
        Column(
            modifier = Modifier
                .weight(HERO_COLUMN_SHARE)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            TableEyebrow(uiState)
            BoxWithConstraints(Modifier.weight(1f, fill = false), contentAlignment = Alignment.Center) {
                TableDigits(uiState, width * HERO_COLUMN_SHARE - 24.dp, heroCap, height = maxHeight)
            }
            ClockProgress(uiState, labelled = false)
        }
        Box(
            Modifier
                .padding(horizontal = 20.dp)
                .width(1.dp)
                .fillMaxHeight(DIVIDER_HEIGHT)
                .background(PokerColors.FeltLine),
        )
        Column(
            modifier = Modifier
                .weight(1f - HERO_COLUMN_SHARE)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TableSide(uiState, blindsCap = heroCap * BLINDS_TO_HERO)
        }
    }
}

@Composable
private fun PortraitBody(uiState: TimerUiState, heroCap: Dp, width: Dp) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TableHero(uiState, width, heroCap)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TableSide(uiState, blindsCap = heroCap * BLINDS_TO_HERO)
        }
    }
}

@Composable
private fun TableHero(uiState: TimerUiState, width: Dp, cap: Dp) {
    TableEyebrow(uiState)
    TableDigits(uiState, width, cap)
    ClockProgress(uiState, labelled = false)
}

/** "LEVEL 6 · TIME LEFT" (or the break's pill) and any state pills. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TableEyebrow(uiState: TimerUiState) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        if (uiState.isOnBreak) {
            PokerPill(clockEyebrow(uiState), tone = PokerPillTone.Gold, icon = PokerIcons.Coffee)
        } else {
            PokerEyebrow(
                clockEyebrow(uiState),
                color = eyebrowColor(uiState),
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
        clockPills(uiState).forEach { PokerPill(it.text, tone = it.tone) }
    }
}

@Composable
private fun TableDigits(uiState: TimerUiState, width: Dp, cap: Dp, height: Dp = Dp.Unspecified) {
    val time = clockText(uiState.segmentRemainingSeconds)
    val size = rememberFittedSize(time, PokerType.Clock, width, cap, height = height, lineHeightRatio = HERO_LINE_HEIGHT)
    Text(
        text = time,
        style = PokerType.Clock.copy(fontSize = size, lineHeight = size * HERO_LINE_HEIGHT),
        color = heroColor(uiState),
        maxLines = 1,
        softWrap = false,
    )
}

/** Blinds and ante, then NEXT; on a break, what to do and the level it leads into. */
@Composable
private fun TableSide(uiState: TimerUiState, blindsCap: Dp) {
    val formatter = rememberChipFormatter()
    val level = if (uiState.isOnBreak) null else uiState.currentLevelSegment?.level
    if (level != null) {
        PokerEyebrow(stringResource(R.string.clock_blinds))
        WithWidth { width -> FittedNumber(blindsText(level, formatter), width, blindsCap, PokerColors.CardWhite) }
        if (level.ante > 0) {
            Text(
                text = stringResource(R.string.clock_bb_ante_amount, formatter.format(level.ante)),
                style = PokerType.Title.copy(fontSize = PokerType.DisplayM.fontSize * ANTE_SCALE),
                color = PokerColors.PokerGold,
            )
        }
        HorizontalDivider(color = PokerColors.FeltLine)
        NextBlinds(uiState.blindsUp)
    } else {
        uiState.currentBreak?.let { segment ->
            breakDetails(segment, formatter)?.let {
                Text(it, style = PokerType.Title, color = PokerColors.PokerGold)
            }
        }
        uiState.nextLevelSegment?.let { next ->
            PokerEyebrow(stringResource(R.string.clock_then_level, next.level.level))
            WithWidth { width -> FittedNumber(blindsText(next.level, formatter), width, blindsCap, PokerColors.CardWhite) }
        }
    }
}

/**
 * "7 of 9 left | Avg 10,714 · 18 BB | Pool $450 | Break in 52:41", with the bubble beside the
 * players left; Knock out (while two or more are left), pause and exit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TableFooter(
    uiState: TimerUiState,
    controlsAlpha: Float,
    onIntent: (TimerIntent) -> Unit,
    onExit: () -> Unit,
    onKnockOut: (() -> Unit)?,
) {
    val formatter = rememberChipFormatter()
    val table = uiState.table
    val bigBlind = uiState.statsBigBlind
    val facts = listOfNotNull(
        stringResource(R.string.table_left, table.playersLeft, table.playerCount),
        if (table.averageStack > 0 && bigBlind > 0) {
            stringResource(
                R.string.table_avg,
                formatter.format(table.averageStack),
                bigBlinds(table.averageStack, bigBlind, formatter),
            )
        } else {
            null
        },
        stringResource(R.string.table_pool, money(table.prizePoolCents)),
        uiState.nextBreakInSeconds?.let { stringResource(R.string.table_break_in, clockText(it)) },
    )
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // A snackbar (Undo after a knockout) sits over the numbers: they make way, not peek out beside it
        val snackbarUp = LocalShellSnackbars.current?.currentSnackbarData != null
        FlowRow(
            modifier = Modifier
                .weight(1f)
                .alpha(if (snackbarUp) 0f else 1f),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val factStyle = PokerType.NumberS.copy(fontSize = PokerType.NumberM.fontSize)
            facts.forEachIndexed { index, fact ->
                Text(fact, style = factStyle, color = PokerColors.Chalk, modifier = Modifier.align(Alignment.CenterVertically))
                if (index == 0) MoneyStagePill(table.moneyStage, Modifier.align(Alignment.CenterVertically))
            }
        }
        Row(
            modifier = Modifier.alpha(controlsAlpha),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onKnockOut?.let { KnockOutButton(it) }
            PlayPauseButton(uiState.buttons, TableControl) { onIntent(TimerIntent.ToggleTimer) }
            ExitButton(onExit)
        }
    }
}

@Composable
private fun ExitButton(onExit: () -> Unit) {
    val description = stringResource(R.string.clock_exit_table_view)
    Box(
        modifier = Modifier
            .size(TableControl)
            .clip(CircleShape)
            .background(PokerColors.FeltDeep)
            .clickable(role = Role.Button, onClick = onExit)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(PokerIcons.Close, contentDescription = null, tint = PokerColors.CardWhite, modifier = Modifier.size(22.dp))
    }
}

/** In landscape the hero takes this share of the width; the blinds the rest. */
private const val HERO_COLUMN_SHARE = 0.55f

/** The hero's font is capped at this share of the screen height. */
private const val HERO_MAX_HEIGHT_LANDSCAPE = 0.42f
private const val HERO_MAX_HEIGHT_PORTRAIT = 0.22f

/** Blinds text relative to the hero. */
private const val BLINDS_TO_HERO = 0.42f
private const val ANTE_SCALE = 0.8f
private const val DIVIDER_HEIGHT = 0.7f
private const val DIMMED_ALPHA = 0.4f
private const val CONTROLS_VISIBLE_MILLIS = 3_000L

/** Below this height (per unit of font scale) a big moment on the table view is its title alone. */
private val BRIEF_BELOW = 240.dp
private const val FADE_MILLIS = 300
private val TableControl = 48.dp
