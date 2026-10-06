package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.pokerpayout.bank.presentation.BankIntent
import com.huntercoles.pokerpayout.bank.presentation.BankViewModel

/** The Bank tab: [BankContent] over [BankViewModel]. */
@Composable
fun BankRoute(viewModel: BankViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    BankContent(
        state = uiState,
        onIntent = { intent ->
            // Any other action takes the focus out of a name being typed, which saves it.
            if (intent !is BankIntent.PlayerNameChanged) focusManager.clearFocus()
            viewModel.acceptIntent(intent)
        },
    )
}
