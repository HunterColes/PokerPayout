package com.huntercoles.pokerpayout.bank.presentation.composable

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.pokerpayout.bank.R
import com.huntercoles.pokerpayout.bank.presentation.BankIntent
import com.huntercoles.pokerpayout.bank.presentation.BankViewModel
import com.huntercoles.pokerpayout.bank.presentation.cash.CashIntent
import com.huntercoles.pokerpayout.bank.presentation.cash.CashShareText
import com.huntercoles.pokerpayout.bank.presentation.cash.CashViewModel
import com.huntercoles.pokerpayout.core.domain.cash.BankMode

/**
 * The Bank tab: the tournament's [BankContent] over [BankViewModel], or the cash game's
 * [CashLedgerContent] over [CashViewModel] (D4), with the Tournament / Cash game switch at the top
 * of either. Both ViewModels live as long as the tab, so switching keeps both games as they were.
 */
@Composable
fun BankRoute(
    viewModel: BankViewModel = hiltViewModel(),
    cashViewModel: CashViewModel = hiltViewModel(),
) {
    val cash by cashViewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val modeSwitch = @Composable {
        BankModeSwitch(
            mode = cash.mode,
            onSwitch = { mode ->
                focusManager.clearFocus()
                cashViewModel.acceptIntent(CashIntent.SwitchMode(mode))
            },
        )
    }
    when (cash.mode) {
        BankMode.TOURNAMENT -> {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            BankContent(
                state = uiState,
                onIntent = { intent ->
                    // Any other action takes the focus out of a name being typed, which saves it.
                    if (intent !is BankIntent.PlayerNameChanged) focusManager.clearFocus()
                    viewModel.acceptIntent(intent)
                },
                modeSwitch = modeSwitch,
            )
        }
        BankMode.CASH -> CashLedgerContent(
            state = cash,
            onIntent = cashViewModel::acceptIntent,
            onShare = { CashShareText.build(context.resources, cash)?.let { shareText(context, it) } },
            modeSwitch = modeSwitch,
        )
    }
}

/** Sends [text] to any app that takes plain text (the group chat); needs no permission. */
private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.cash_share_chooser)))
}
