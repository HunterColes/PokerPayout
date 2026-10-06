package com.huntercoles.pokerpayout.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.components.CardFace
import com.huntercoles.pokerpayout.core.design.components.CardFacePreview
import com.huntercoles.pokerpayout.core.design.components.CardFaceSize
import com.huntercoles.pokerpayout.core.design.components.EquityBar
import com.huntercoles.pokerpayout.core.design.components.EquityBarPreview
import com.huntercoles.pokerpayout.core.design.components.PlayingCard
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonPreview
import com.huntercoles.pokerpayout.core.design.components.PokerChipPreview
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerFieldPreview
import com.huntercoles.pokerpayout.core.design.components.PokerIconButton
import com.huntercoles.pokerpayout.core.design.components.PokerNavBar
import com.huntercoles.pokerpayout.core.design.components.PokerNavBarPreview
import com.huntercoles.pokerpayout.core.design.components.PokerPill
import com.huntercoles.pokerpayout.core.design.components.PokerPillPreview
import com.huntercoles.pokerpayout.core.design.components.PokerPillTone
import com.huntercoles.pokerpayout.core.design.components.PokerSegmentedControlPreview
import com.huntercoles.pokerpayout.core.design.components.PokerSheetPreview
import com.huntercoles.pokerpayout.core.design.components.PokerStepperPreview
import com.huntercoles.pokerpayout.core.design.components.PokerTopBar
import com.huntercoles.pokerpayout.core.design.components.PokerTopBarPreview
import com.huntercoles.pokerpayout.core.design.components.ToggleChipPreview
import com.huntercoles.pokerpayout.core.design.components.UndoSnackbar
import com.huntercoles.pokerpayout.core.design.components.UndoSnackbarPreview
import com.huntercoles.pokerpayout.core.design.components.pokerNavItems
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import java.util.Locale

/** Every component's `@Preview` gallery, by golden name. Goldens and layout tests both render these. */
internal val ComponentGallery: List<Pair<String, @Composable () -> Unit>> = listOf(
    "PokerTopBar" to { PokerTopBarPreview() },
    "PokerButton" to { PokerButtonPreview() },
    "PokerPill" to { PokerPillPreview() },
    "ToggleChip" to { ToggleChipPreview() },
    "PokerSegmentedControl" to { PokerSegmentedControlPreview() },
    "PokerField" to { PokerFieldPreview() },
    "PokerStepper" to { PokerStepperPreview() },
    "CardFace" to { CardFacePreview() },
    "PokerChip" to { PokerChipPreview() },
    "EquityBar" to { EquityBarPreview() },
    "PokerSheet" to { PokerSheetPreview() },
    "UndoSnackbar" to { UndoSnackbarPreview() },
    "PokerNavBar" to { PokerNavBarPreview() },
)

/**
 * A whole screen built only from the new components: the top bar, a scrolling body, the Undo
 * snackbar and the nav bar. It shows how the parts sit together when the screen stretches and
 * rotates (it is not a real screen; those arrive in M2+).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShellSample() {
    PokerTheme(reducedMotion = true) {
        Column(Modifier.fillMaxSize().background(PokerColors.PokerBlack)) {
            PokerTopBar(title = "Odds", subtitle = "Flop · exact · 990 runouts", onBack = {}) {
                PokerIconButton(PokerIcons.Restart, "New hand", onClick = {})
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = PokerDimens.Gutter),
                verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
            ) {
                ShellSeat("Player 1", listOf(PlayingCard("A", "s"), PlayingCard("K", "s")), 0.561f, lead = true)
                ShellSeat("Player 2", listOf(PlayingCard("Q", "h"), PlayingCard("Q", "d")), 0.439f, lead = false)
                PokerEyebrow("Board")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("J" to "s", "T" to "s", "2" to "c").forEach { (rank, suit) ->
                        CardFace(PlayingCard(rank, suit))
                    }
                }
                PokerButton("Deal the turn", onClick = {}, icon = PokerIcons.Cards, modifier = Modifier.fillMaxWidth())
            }
            UndoSnackbar(message = "New hand dealt", onUndo = {}, modifier = Modifier.padding(12.dp))
            PokerNavBar(items = pokerNavItems(), selectedIndex = 3, onSelect = {})
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShellSeat(name: String, cards: List<PlayingCard>, equity: Float, lead: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PokerColors.FeltGreen, RoundedCornerShape(PokerDimens.CornerCard))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            cards.forEach { CardFace(it, size = CardFaceSize.Small) }
            Spacer(Modifier.weight(1f))
            Text(
                text = "%.1f%%".format(Locale.US, equity * 100),
                style = PokerType.NumberL,
                color = PokerColors.CardWhite,
            )
        }
        // The name and its pill wrap onto two lines at large font sizes instead of squeezing.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = PokerColors.CardWhite)
            if (lead) PokerPill("Favourite", tone = PokerPillTone.Gold, modifier = Modifier.align(Alignment.CenterVertically))
        }
        EquityBar(win = equity, tie = 0f)
    }
}
