package com.huntercoles.pokerpayout.tools.presentation

import androidx.lifecycle.ViewModel
import com.huntercoles.pokerpayout.core.tip.TipJar
import com.huntercoles.pokerpayout.tools.tip.TipCoin
import com.huntercoles.pokerpayout.tools.tip.TipLink
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** What the Tip the dealer page (S28) can be told. The route does the copying and the opening. */
sealed interface TipIntent {
    /** [coin]'s address is on the clipboard. */
    data class Copied(val coin: TipCoin) : TipIntent

    /** [link] was handed to the browser; [opened] is false when no app on the phone could open it. */
    data class Opened(val link: TipLink, val opened: Boolean) : TipIntent
}

/** The Tip the dealer page (S28). */
data class TipUiState(
    /** The address just copied: its button says so. */
    val copied: TipCoin? = null,
    /** A page no app on the phone could open: its address is shown instead. */
    val noBrowser: TipLink? = null,
)

/**
 * Tip the dealer (S28, PP-112): the donation page, the addresses with their QR codes, and the free
 * ways to help. Copying an address or opening the donation page means the host has found the page,
 * so the "Tip the dealer?" card on the Payouts tab never asks them again ([TipJar.stopAsking]).
 */
@HiltViewModel
class TipViewModel @Inject constructor(private val jar: TipJar) : ViewModel() {

    private val _uiState = MutableStateFlow(TipUiState())
    val uiState: StateFlow<TipUiState> = _uiState.asStateFlow()

    fun acceptIntent(intent: TipIntent) {
        when (intent) {
            is TipIntent.Copied -> {
                jar.stopAsking()
                _uiState.update { it.copy(copied = intent.coin, noBrowser = null) }
            }
            is TipIntent.Opened -> {
                if (intent.opened && intent.link == TipLink.DONATION_PAGE) jar.stopAsking()
                _uiState.update { it.copy(noBrowser = intent.link.takeUnless { intent.opened }) }
            }
        }
    }
}
