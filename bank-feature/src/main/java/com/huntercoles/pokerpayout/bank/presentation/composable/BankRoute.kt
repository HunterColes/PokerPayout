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
import com.huntercoles.pokerpayout.bank.presentation.SettleUpShareText

/** The Bank tab: [BankContent] over [BankViewModel], with the settle-up shared as text. */
@Composable
fun BankRoute(viewModel: BankViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
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

/** Sends [text] to any app that takes plain text (the group chat); needs no permission. */
private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.bank_settle_share_chooser)))
}
