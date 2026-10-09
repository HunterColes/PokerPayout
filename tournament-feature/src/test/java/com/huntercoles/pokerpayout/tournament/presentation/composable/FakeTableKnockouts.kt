package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.presentation.TableKnockouts

/**
 * A stand-in for the Bank's knockout panel (PP-135; bank-feature tests the real one): it says it is
 * open, and Done closes it.
 */
internal object FakeTableKnockouts : TableKnockouts {
    const val OPEN = "Knockout panel"
    const val DONE = "Done"

    @Composable
    override fun Panel(onClose: () -> Unit) {
        Box(Modifier.fillMaxSize().background(PokerColors.FeltGreen)) {
            Column(Modifier.align(Alignment.Center)) {
                Text(OPEN, color = PokerColors.CardWhite)
                PokerButton(text = DONE, onClick = onClose)
            }
        }
    }
}
