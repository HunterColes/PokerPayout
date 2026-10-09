package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.pokerpayout.bank.presentation.BankIntent
import com.huntercoles.pokerpayout.bank.presentation.BankSheet
import com.huntercoles.pokerpayout.bank.presentation.BankViewModel
import com.huntercoles.pokerpayout.core.presentation.TableKnockouts
import com.huntercoles.pokerpayout.core.presentation.findActivity
import javax.inject.Inject

/** PP-135: the Bank's knockout, offered to the full-screen clock. */
class BankTableKnockouts @Inject constructor() : TableKnockouts {
    @Composable
    override fun Panel(onClose: () -> Unit) {
        QuickKnockoutRoute(onClose = onClose)
    }
}

/**
 * The knockout from the full-screen clock (PP-135), in two taps: who is out, then who knocked them
 * out (or nobody). It is the Bank's own knockout, sent to a Bank ViewModel as the Bank's knockout
 * sheet sends it ([BankIntent.OpenKnockout], then [BankIntent.KnockOut]), so the place, the bounty
 * or envelope, the players left and the payouts come out exactly as from the Bank, and the snackbar
 * offers Undo. There is no confirm: Undo is the way back. A mystery knockout shows its envelope
 * before the clock comes back; once it is recorded (and any envelope seen, or put back by Undo),
 * [onClose].
 *
 * [viewModel] is the Bank tab's own ([bankViewModel]): a knockout from the clock is in the Bank's
 * Undo history, and its top bar's Undo can take it back too.
 */
@Composable
fun QuickKnockoutRoute(onClose: () -> Unit, viewModel: BankViewModel = bankViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Put away, the Bank's sheet goes too, so the next knockout starts at "Who's out?" (a rotation keeps it)
    val context = LocalContext.current
    DisposableEffect(viewModel) {
        onDispose {
            if (context.findActivity()?.isChangingConfigurations != true) viewModel.acceptIntent(BankIntent.DismissSheet)
        }
    }
    var revealing by rememberSaveable { mutableStateOf(false) }
    val close by rememberUpdatedState(onClose)
    LaunchedEffect(viewModel) {
        // The envelope seen (Close), or put back in the pool by Undo: the clock again
        viewModel.uiState.collect { if (revealing && it.sheet !is BankSheet.Envelope) close() }
    }
    QuickKnockoutOverlay(
        state = state,
        onIntent = { intent ->
            viewModel.acceptIntent(intent)
            if (intent is BankIntent.KnockOut) {
                // Recorded: the clock again, unless a mystery envelope was drawn to show first
                if (viewModel.uiState.value.sheet is BankSheet.Envelope) revealing = true else onClose()
            }
        },
        onClose = onClose,
    )
}
