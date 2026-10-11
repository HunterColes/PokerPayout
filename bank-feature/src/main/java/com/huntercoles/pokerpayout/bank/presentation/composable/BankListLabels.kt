package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.bank.R
import com.huntercoles.pokerpayout.bank.presentation.BankSection
import com.huntercoles.pokerpayout.bank.presentation.BankUiState
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.domain.model.PurchaseWindow
import java.util.Locale

// The player list's labels: the rule between rows, each section's label, and under the list Late
// entry (PP-116) and the cutoff note.

internal fun Modifier.topRule(): Modifier = drawBehind {
    drawLine(PokerColors.FeltLine, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
}

@Composable
internal fun SectionLabel(section: BankSection, count: Int, hint: String?, first: Boolean) {
    val label = when (section) {
        BankSection.CHAMPION -> stringResource(R.string.bank_section_champion)
        BankSection.PLAYING -> stringResource(R.string.bank_section_playing, count)
        BankSection.OUT -> stringResource(R.string.bank_section_out, count)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PokerColors.FeltGreen)
            .then(if (first) Modifier else Modifier.topRule())
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label.uppercase(Locale.ROOT),
            style = PokerType.Eyebrow.copy(fontSize = 12.sp, letterSpacing = 1.3.sp),
            color = PokerColors.Chalk,
            modifier = Modifier.semantics { heading() },
        )
        if (hint != null) {
            Text(
                text = hint,
                style = HintStyle,
                color = PokerColors.Chalk,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private val HintStyle = TextStyle(fontSize = 11.5.sp, lineHeight = 14.sp)

/**
 * Under the list once the clock is running (PP-116): Late entry, while entries are open, and where
 * the late entry cutoff stands ("Late entry until the end of level 4."). Nothing before the start
 * (the Tournament's player count sets the field) or once there is a champion.
 */
@Composable
internal fun LateEntryFooter(state: BankUiState, onLateEntry: () -> Unit) {
    if (!state.clock.started || state.championId != null) return
    val note = when (val window = state.lateEntryWindow) {
        is PurchaseWindow.OpenUntilLevel -> stringResource(R.string.bank_note_late_open_level, window.level)
        is PurchaseWindow.ClosedAfterLevel -> stringResource(R.string.bank_note_late_closed, window.level)
        else -> null
    }
    if (!state.canTakeLateEntry && note == null) return
    Column(
        modifier = Modifier.padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (state.canTakeLateEntry) {
            PokerButton(
                text = stringResource(R.string.bank_late_button),
                onClick = onLateEntry,
                variant = PokerButtonVariant.Secondary,
                icon = PokerIcons.Plus,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = PokerColors.Chalk,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

/** Under the list: where the cutoffs stand ("Rebuys closed after level 4 · add-ons closed after break 1."). */
@Composable
internal fun CutoffNote(state: BankUiState) {
    if (!state.isRebuyEnabled || state.rebuyWindow == PurchaseWindow.NoCutoff) return
    val rebuys = rebuyNote(state.rebuyWindow).orEmpty()
    val addOns = if (state.isAddOnEnabled) addOnNote(state.addOnWindow) else null
    val windows = if (addOns != null) {
        stringResource(R.string.bank_note_join, rebuys, addOns)
    } else {
        stringResource(R.string.bank_note_rebuys_only, rebuys)
    }
    val anyClosed = !state.rebuyWindow.isOpen || (state.isAddOnEnabled && !state.addOnWindow.isOpen)
    val taken = stringResource(R.string.bank_note_taken)
    Text(
        text = if (anyClosed) stringResource(R.string.bank_note_then, windows, taken) else windows,
        style = MaterialTheme.typography.bodySmall,
        color = PokerColors.Chalk,
        modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 10.dp),
    )
}

@Composable
private fun rebuyNote(window: PurchaseWindow): String? = when (window) {
    is PurchaseWindow.ClosedAfterLevel -> stringResource(R.string.bank_note_rebuys_closed, window.level)
    is PurchaseWindow.OpenUntilLevel -> stringResource(R.string.bank_note_rebuys_open, window.level)
    else -> null
}

@Composable
private fun addOnNote(window: PurchaseWindow): String? = when (window) {
    is PurchaseWindow.OpenUntilBreak -> stringResource(R.string.bank_note_add_ons_open_break, window.number)
    is PurchaseWindow.OpenUntilLevel -> stringResource(R.string.bank_note_add_ons_open_level, window.level)
    is PurchaseWindow.ClosedAfterBreak -> stringResource(R.string.bank_note_add_ons_closed_break, window.number)
    is PurchaseWindow.ClosedAfterLevel -> stringResource(R.string.bank_note_add_ons_closed_level, window.level)
    PurchaseWindow.NoCutoff -> null
}
