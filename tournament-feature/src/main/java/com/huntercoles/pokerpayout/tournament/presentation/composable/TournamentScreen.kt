package com.huntercoles.pokerpayout.tournament.presentation.composable

import android.Manifest
import android.content.res.Configuration
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.huntercoles.pokerpayout.core.design.LocalReducedMotion
import com.huntercoles.pokerpayout.core.design.components.ConfirmSheet
import com.huntercoles.pokerpayout.core.design.components.LocalWidthClass
import com.huntercoles.pokerpayout.core.design.components.RequestShellChrome
import com.huntercoles.pokerpayout.core.design.components.WidthClass
import com.huntercoles.pokerpayout.core.design.components.fillShellWidth
import com.huntercoles.pokerpayout.core.presentation.HideSystemBars
import com.huntercoles.pokerpayout.core.presentation.OnPhoneUpright
import com.huntercoles.pokerpayout.core.presentation.RequestOrientation
import com.huntercoles.pokerpayout.core.presentation.findActivity
import com.huntercoles.pokerpayout.core.utils.FormatUtils
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.live.NotificationsAsk
import com.huntercoles.pokerpayout.tournament.presentation.PurchaseKind
import com.huntercoles.pokerpayout.tournament.presentation.TimerIntent
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import com.huntercoles.pokerpayout.tournament.presentation.TimerViewModel
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigIntent
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigUiState
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigViewModel
import com.huntercoles.pokerpayout.tournament.presentation.TournamentMode
import com.huntercoles.pokerpayout.tournament.presentation.TournamentOrientation
import com.huntercoles.pokerpayout.tournament.presentation.TournamentUi
import com.huntercoles.pokerpayout.tournament.presentation.presets.PresetsViewModel
import com.huntercoles.pokerpayout.tournament.presentation.presets.shareSetup

/**
 * The Tournament tab (S1 v2, S2, S3, S4). The clock's ViewModel belongs to the activity, so the clock
 * is the same one on every tab and through every rotation; the tab's own state (which mood it's in,
 * the panel's lock) is saved with the screen, so a rotation or a process death puts it back.
 */
@Composable
fun TournamentScreen(
    onOpenBank: () -> Unit = {},
    onOpenPayouts: () -> Unit = {},
    onOpenSound: () -> Unit = {},
    calculatorViewModel: TournamentConfigViewModel = hiltViewModel(),
    timerViewModel: TimerViewModel = hiltViewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity),
) {
    // Saved setups (PP-032) belong to this tab alone, so their ViewModel isn't a parameter.
    val presetsViewModel: PresetsViewModel = hiltViewModel()
    val setup by calculatorViewModel.uiState.collectAsStateWithLifecycle()
    val timer by timerViewModel.uiState.collectAsStateWithLifecycle()
    val presets by presetsViewModel.uiState.collectAsStateWithLifecycle()
    var ui by rememberSaveable { mutableStateOf(TournamentUi.initial(timerViewModel.uiState.value.hasTimerStarted)) }
    val askForNotifications = rememberNotificationsAsk()
    val onPresetIntent = rememberPresetIntents(presetsViewModel, presets.sharing)
    val context = LocalContext.current
    val actions = remember(
        calculatorViewModel, timerViewModel, context, onOpenBank, onOpenPayouts, onOpenSound, askForNotifications, onPresetIntent,
    ) {
        TournamentActions(
            onSetupIntent = calculatorViewModel::acceptIntent,
            onTimerIntent = { intent ->
                // PP-081: the first Start (of a setup that can start) is when the live clock first matters
                val before = timerViewModel.uiState.value
                if (intent == TimerIntent.ToggleTimer && !before.hasTimerStarted && before.setupProblem == null) {
                    askForNotifications()
                }
                // The blind fields are mirrored in the setup ViewModel, which Reset reads.
                intent.toConfigIntent()?.let(calculatorViewModel::acceptIntent)
                timerViewModel.acceptIntent(intent)
            },
            updateUi = { transform -> ui = transform(ui) },
            openBank = onOpenBank,
            openPayouts = onOpenPayouts,
            openSound = onOpenSound,
            onPresetIntent = onPresetIntent,
            shareText = { text -> shareSetup(context, text) },
        )
    }
    val focusManager = LocalFocusManager.current
    val activity = context.findActivity()
    DisposableEffect(Unit) {
        onDispose {
            // Leaving the tab commits what was typed, and ends a ⤢ table view (phones turn back upright).
            focusManager.clearFocus(force = true)
            if (activity?.isChangingConfigurations != true) timerViewModel.acceptIntent(TimerIntent.SetTableView(false))
        }
    }
    val flash = rememberCueFlashes(timerViewModel)
    Box(Modifier.fillMaxSize()) {
        TournamentContent(setup = setup, timer = timer, ui = ui, actions = actions)
        CueFlash(flash)
    }
    PresetsSheet(presets, setup, timer, actions)
}

/** PP-083: the clock's flashes while the tab is on screen; one that comes while it isn't is dropped. */
@Composable
private fun rememberCueFlashes(timerViewModel: TimerViewModel): FlashRequest? {
    var flash by remember { mutableStateOf<FlashRequest?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(timerViewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            timerViewModel.flashes.collect { cue -> flash = FlashRequest(cue, (flash?.id ?: 0) + 1) }
        }
    }
    return flash
}

/**
 * PP-081: on Android 13 and up, asks once on this phone for permission to post notifications, so
 * the live clock can show in the shade and on the lock screen. Never again once answered: everything
 * else works without it, and Tools, Sound offers the way back to it. Android keeps the answer, not
 * the app, so a phone restored from a backup asks too (PP-137, [NotificationsAsk]).
 */
@Composable
private fun rememberNotificationsAsk(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Allowed or not, the clock starts as asked; the notification shows only if allowed
    }
    return remember(context, launcher) {
        {
            val ask = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.findActivity()?.let(NotificationsAsk::shouldAsk) == true
            if (ask) runCatching { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }
}

/**
 * The tab, stateless: setup before the start, the fold on Start, then the clock with its strip and
 * panel; or the full-screen table view when a phone is turned sideways (or ⤢ is on). ✕ in a table
 * view the phone was turned into holds the clock upright until the phone is held upright again
 * (PP-094 #2): turned sideways after that, it shows the table view again.
 */
@Composable
fun TournamentContent(
    setup: TournamentConfigUiState,
    timer: TimerUiState,
    ui: TournamentUi,
    actions: TournamentActions,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val started = timer.hasTimerStarted
    val settled = ui.settledFor(started, reduced)
    if (settled != ui) SideEffect { actions.updateUi { it.settledFor(started, reduced) } }
    val configuration = LocalConfiguration.current
    val smallestWidth = configuration.smallestScreenWidthDp
    val clockExists = timer.hasTimerStarted
    RequestOrientation(TournamentOrientation.requested(smallestWidth, clockExists, timer.isTableView, settled.rotationPaused))
    OnPhoneUpright(enabled = settled.rotationPaused) { actions.updateUi { it.pauseRotation(false) } }
    val tableView = TournamentOrientation.showsTableView(
        smallestScreenWidthDp = smallestWidth,
        landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
        clock = TournamentOrientation.TableViewInputs(clockExists, timer.isTableView, settled.rotationPaused),
    )
    if (tableView) {
        // ✕ (or Back) shows the clock upright for this turn, whether ⤢ or a turn opened the table
        // view (PP-094 #2). Closing a ⤢ view must hold too: with rotation locked, Android 14 keeps
        // the landscape it was asked for as the user's rotation, so following it would bring the
        // table view straight back.
        val exit = {
            if (timer.isTableView) actions.onTimerIntent(TimerIntent.SetTableView(false))
            actions.updateUi { it.pauseRotation(true) }
        }
        RequestShellChrome(immersive = true)
        HideSystemBars()
        BackHandler(onBack = exit)
        TableViewContent(timer, actions.onTimerIntent, onExit = exit, modifier = modifier)
    } else {
        TabBody(setup, timer, settled, actions, modifier)
    }
    TournamentDialogs(setup, actions)
}

@Composable
private fun TabBody(
    setup: TournamentConfigUiState,
    timer: TimerUiState,
    ui: TournamentUi,
    actions: TournamentActions,
    modifier: Modifier,
) {
    val widthClass = LocalWidthClass.current
    val small = widthClass == WidthClass.Small
    val gutter = if (small) 12.dp else 16.dp
    val layout = when (widthClass) {
        WidthClass.Small -> ClockLayout.Small
        WidthClass.Expanded -> ClockLayout.TwoPane
        else -> ClockLayout.Phone
    }
    val wide = layout == ClockLayout.TwoPane && ui.mode != TournamentMode.Setup
    Column(modifier.fillMaxSize().then(if (wide) Modifier.fillShellWidth() else Modifier)) {
        TournamentTopBar(timer, ui.mode, actions, small = small, wide = layout == ClockLayout.TwoPane)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (ui.mode) {
                TournamentMode.Setup -> SetupContent(setup, timer, actions, gutter)
                TournamentMode.Folding -> SetupFold(setup, timer, gutter) { actions.updateUi { it.foldFinished() } }
                TournamentMode.Running -> ClockContent(setup, timer, actions, layout, gutter)
                TournamentMode.PanelOpen -> {
                    // The clock runs on under the panel; TalkBack and taps go to the panel.
                    ClockBackdrop(timer, layout, gutter)
                    Scrim { actions.updateUi { it.closePanel() } }
                    Box(Modifier.align(Alignment.TopCenter)) { SetupPanel(setup, timer, ui, actions, gutter) }
                }
            }
        }
    }
}

/** The running clock behind the setup panel: its time, with nothing to tap. */
@Composable
private fun ClockBackdrop(timer: TimerUiState, layout: ClockLayout, gutter: Dp) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .wrapContentHeight(Alignment.Top, unbounded = true)
            .clearAndSetSemantics {}
            .padding(start = gutter, end = gutter, top = BACKDROP_TOP),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WithWidth(Modifier.fillMaxWidth()) { width ->
            ClockHero(timer, width, if (layout == ClockLayout.TwoPane) TABLET_HERO else PHONE_HERO, onIntent = null)
        }
        ClockProgress(timer)
    }
}

/** The dim behind the setup panel; a tap on it folds the panel away. */
@Composable
private fun Scrim(onTap: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM_ALPHA))
            .pointerInput(onTap) { detectTapGestures { onTap() } },
    )
}

/** The reset question, and the "turn rebuys off?" one a $0 rebuy or add-on asks. */
@Composable
private fun TournamentDialogs(setup: TournamentConfigUiState, actions: TournamentActions) {
    if (setup.showResetDialog) {
        ConfirmSheet(
            title = stringResource(R.string.tournament_reset_title),
            body = resetDescription(setup),
            dismissLabel = stringResource(R.string.clock_cancel),
            confirmLabel = stringResource(R.string.clock_reset_confirm),
            onDismiss = { actions.onSetupIntent(TournamentConfigIntent.HideResetDialog) },
            onConfirm = {
                actions.onSetupIntent(TournamentConfigIntent.ConfirmReset)
                actions.onTimerIntent(TimerIntent.ResetTimer)
            },
            destructive = true,
        )
    }
    PurchaseClearQuestion(uiState = setup, onIntent = actions.onSetupIntent)
}

/** The reset question says what a reset takes with it, including purchases recorded in the Bank. */
@Composable
private fun resetDescription(uiState: TournamentConfigUiState): String {
    val rebuys = uiState.rebuyPurchases
    val addOns = uiState.addOnPurchases
    val purchases = listOfNotNull(
        rebuys.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.tournament_rebuys, it, it) },
        addOns.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.tournament_add_ons, it, it) }
    )
    val base = stringResource(R.string.tournament_reset_description)
    return when (purchases.size) {
        0 -> base
        1 -> stringResource(R.string.tournament_reset_purchases, base, purchases[0])
        else -> stringResource(
            R.string.tournament_reset_purchases,
            base,
            stringResource(R.string.tournament_and, purchases[0], purchases[1])
        )
    }
}

/**
 * "Turn rebuys off?" when a rebuy or add-on amount is left at $0 while purchases are recorded
 * (PP-014): a sheet like every other confirmation, Keep on the left, the red "Clear 3 rebuys" on
 * the right (PP-049; it was the last old-style dialog).
 */
@Composable
private fun PurchaseClearQuestion(uiState: TournamentConfigUiState, onIntent: (TournamentConfigIntent) -> Unit) {
    uiState.purchaseClearPrompt?.let { prompt ->
        val count = prompt.count
        val kept = FormatUtils.formatCents(prompt.keptAmountCents)
        val rebuys = prompt.kind == PurchaseKind.REBUY
        ConfirmSheet(
            title = stringResource(if (rebuys) R.string.tournament_turn_off_rebuys else R.string.tournament_turn_off_add_ons),
            body = pluralStringResource(
                if (rebuys) R.plurals.tournament_turn_off_rebuys_body else R.plurals.tournament_turn_off_add_ons_body,
                count,
                count,
                kept,
            ),
            dismissLabel = stringResource(R.string.tournament_keep),
            confirmLabel = pluralStringResource(
                if (rebuys) R.plurals.tournament_clear_rebuys else R.plurals.tournament_clear_add_ons,
                count,
                count,
            ),
            onDismiss = { onIntent(TournamentConfigIntent.DismissClearPurchases) },
            onConfirm = { onIntent(TournamentConfigIntent.ConfirmClearPurchases) },
            destructive = true,
        )
    }
}

private const val SCRIM_ALPHA = 0.62f

/** Below the strip, where the clock starts on the page. */
private val BACKDROP_TOP = 64.dp
private val PHONE_HERO = 112.dp
private val TABLET_HERO = 230.dp
