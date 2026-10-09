package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/** Routine actions apply at once and can be undone for this long. */
const val UNDO_WINDOW_MS = 8_000L

/**
 * Shows "[message] · UNDO" for [UNDO_WINDOW_MS] and returns true if the player pressed Undo.
 * The snackbar goes away when the window ends or the caller is cancelled. On its own it queues
 * behind a snackbar already showing; features go through [SnackbarController.showUndo], which
 * replaces that one instead.
 */
suspend fun SnackbarHostState.showUndo(message: String, actionLabel: String): Boolean {
    val result = withTimeoutOrNull(UNDO_WINDOW_MS) {
        showSnackbar(message = message, actionLabel = actionLabel, duration = SnackbarDuration.Indefinite)
    }
    return result == SnackbarResult.ActionPerformed
}

/**
 * The app's one snackbar. Features call [showUndo] from a ViewModel after applying a routine
 * action ("Rita is out in 8th · bounty to Marcus"), and undo it when it returns true:
 * ```
 * viewModelScope.launch { if (snackbars.showUndo(message, undoLabel)) undoLastAction() }
 * ```
 * `MainActivity` passes [hostState] to the app shell, which hosts it once with [PokerSnackbarHost] (M2).
 */
@Singleton
class SnackbarController @Inject constructor() {
    val hostState = SnackbarHostState()

    /** The snackbar showing, or about to; the next [showUndo] takes its place. */
    private val latest = AtomicReference<Job?>(null)

    /**
     * Shows "[message] · UNDO" for the Undo window and returns true if Undo was pressed. The app's
     * rule (PP-097): a new snackbar replaces the one showing at once, from any screen, instead of
     * queueing behind it. The one replaced goes with its Undo, and returns false.
     */
    suspend fun showUndo(message: String, actionLabel: String): Boolean = coroutineScope {
        val shown = async(start = CoroutineStart.UNDISPATCHED) { hostState.showUndo(message, actionLabel) }
        latest.getAndSet(shown)?.cancel()
        try {
            shown.await()
        } catch (expected: CancellationException) {
            ensureActive() // the caller was cancelled, not replaced: pass that on
            false
        } finally {
            latest.compareAndSet(shown, null)
        }
    }
}

/** Hosts the app's snackbars, each drawn as an [UndoSnackbar]. */
@Composable
fun PokerSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        UndoSnackbar(
            message = data.visuals.message,
            onUndo = data::performAction,
            actionLabel = data.visuals.actionLabel ?: stringResource(R.string.design_undo),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/**
 * The snackbar itself: white text on FeltHigh, a gold "UNDO" (48 dp touch target), and the only
 * shadow in the app apart from sheets. The message wraps rather than truncates. TalkBack announces
 * it politely when it appears.
 */
@Composable
fun UndoSnackbar(
    message: String,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String = stringResource(R.string.design_undo),
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(PokerDimens.CornerControl),
        color = PokerColors.FeltHigh,
        contentColor = PokerColors.CardWhite,
        shadowElevation = PokerDimens.ElevationOverlay,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 52.dp)
                .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(PokerDimens.SpacingSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(text = message, style = MaterialTheme.typography.bodyMedium, color = PokerColors.CardWhite)
            }
            PokerButton(
                text = actionLabel.uppercase(Locale.ROOT),
                onClick = onUndo,
                variant = PokerButtonVariant.Text,
                size = PokerButtonSize.Small,
            )
        }
    }
}

@Preview(name = "UndoSnackbar", widthDp = 360, showBackground = true, backgroundColor = 0xFF0B0B0B)
@Composable
internal fun UndoSnackbarPreview() {
    PokerPreviewPage {
        UndoSnackbar(message = "Rita is out in 8th · bounty to Marcus", onUndo = {})
        UndoSnackbar(message = "New hand", onUndo = {})
    }
}
