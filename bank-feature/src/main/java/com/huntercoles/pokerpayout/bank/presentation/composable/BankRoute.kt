package com.huntercoles.pokerpayout.bank.presentation.composable

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.pokerpayout.bank.R
import com.huntercoles.pokerpayout.bank.presentation.BankIntent
import com.huntercoles.pokerpayout.bank.presentation.BankViewModel
import com.huntercoles.pokerpayout.bank.presentation.SettleUpShareText
import com.huntercoles.pokerpayout.core.presentation.findActivity

/**
 * The Bank tab: [BankContent] over the Bank's one [BankViewModel] ([bankViewModel]), with the
 * settle-up shared as text. Leaving the tab puts any open sheet away, as before the ViewModel was
 * shared (a rotation keeps it).
 */
@Composable
fun BankRoute(viewModel: BankViewModel = bankViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    DisposableEffect(viewModel) {
        onDispose {
            if (context.findActivity()?.isChangingConfigurations != true) viewModel.acceptIntent(BankIntent.DismissSheet)
        }
    }
    BankContent(
        state = uiState,
        onIntent = { intent ->
            // Any other action takes the focus out of a name being typed, which saves it.
            if (intent !is BankIntent.PlayerNameChanged) focusManager.clearFocus()
            viewModel.acceptIntent(intent)
        },
        onShareSettleUp = { SettleUpShareText.build(context.resources, uiState)?.let { shareText(context, it) } },
    )
}

/**
 * The Bank's one ViewModel, the activity's (PP-135): the Bank tab and the full-screen clock's
 * knockout share it, so their actions are one Undo history and one snackbar, and a knockout from
 * the clock can be taken back from the Bank's top bar.
 */
@Composable
fun bankViewModel(): BankViewModel {
    val activity = LocalContext.current.findActivity() as? ComponentActivity
    return if (activity != null) hiltViewModel(viewModelStoreOwner = activity) else hiltViewModel()
}

/** Sends [text] to any app that takes plain text (the group chat); needs no permission. */
private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.bank_settle_share_chooser)))
}
