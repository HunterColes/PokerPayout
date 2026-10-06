package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType

private val SheetShape = RoundedCornerShape(topStart = PokerDimens.CornerSheet, topEnd = PokerDimens.CornerSheet)
private const val SCRIM_ALPHA = 0.62f

/**
 * One bottom sheet per decision, in place of dialog chains (`PokerDialog`, the confirmation
 * dialogs, the weights dialog): a Material modal sheet in FeltGreen with 28 dp top corners and a
 * DarkGold handle, an optional [title] (a heading for TalkBack) and the content below it.
 *
 * Screenshot tests render [PokerSheetContent] directly; a modal window doesn't capture under
 * Robolectric.
 */
@Composable
fun PokerSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = PokerColors.FeltGreen,
        contentColor = PokerColors.CardWhite,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = SCRIM_ALPHA),
        dragHandle = { SheetHandle() },
    ) {
        PokerSheetBody(title = title, content = content)
    }
}

/**
 * The sheet as it looks open, without the modal window: the surface, handle, title and content.
 * Use it for screenshots, and wherever a sheet is docked rather than modal.
 */
@Composable
fun PokerSheetContent(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(PokerDimens.ElevationOverlay, SheetShape)
            .background(PokerColors.FeltGreen, SheetShape),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SheetHandle()
        Box(Modifier.navigationBarsPadding()) { PokerSheetBody(title = title, content = content) }
    }
}

@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .padding(top = 10.dp, bottom = 4.dp)
            .size(width = 36.dp, height = 4.dp)
            .background(PokerColors.DarkGold, RoundedCornerShape(2.dp)),
    )
}

@Composable
private fun PokerSheetBody(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PokerDimens.Gutter, end = PokerDimens.Gutter, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = SheetTitle,
                color = PokerColors.CardWhite,
                modifier = Modifier.semantics { heading() },
            )
        }
        content()
    }
}

private val SheetTitle = PokerType.Title.copy(fontSize = 24.sp, lineHeight = 28.sp)

/**
 * The body of a confirmation: what will happen, and two buttons in one row. Dismiss is a text
 * button on the left, confirm a filled button on the right. Destructive confirms are red and name
 * the effect ("Clear 9 players"), never "OK".
 */
@Suppress("LongParameterList") // a component API: one parameter per visual option
@Composable
fun ConfirmSheetContent(
    body: String,
    dismissLabel: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    destructive: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium)) {
        Text(text = body, style = MaterialTheme.typography.bodyMedium, color = PokerColors.Chalk)
        ConfirmButtons(dismissLabel, confirmLabel, onDismiss, onConfirm, destructive)
    }
}

@Composable
private fun ConfirmButtons(
    dismissLabel: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    destructive: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(PokerDimens.SpacingSmall), verticalAlignment = Alignment.CenterVertically) {
        PokerButton(
            text = dismissLabel,
            onClick = onDismiss,
            variant = PokerButtonVariant.Text,
            size = PokerButtonSize.Small,
            modifier = Modifier.weight(1f),
        )
        PokerButton(
            text = confirmLabel,
            onClick = onConfirm,
            variant = if (destructive) PokerButtonVariant.Destructive else PokerButtonVariant.Primary,
            size = PokerButtonSize.Small,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A modal [PokerSheet] that asks once before a real reset. Routine actions use Undo instead. */
@Suppress("LongParameterList") // a component API: one parameter per visual option
@Composable
fun ConfirmSheet(
    title: String,
    body: String,
    dismissLabel: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    destructive: Boolean = false,
) {
    PokerSheet(onDismissRequest = onDismiss, title = title) {
        ConfirmSheetContent(body, dismissLabel, confirmLabel, onDismiss, onConfirm, destructive)
    }
}

@Preview(name = "PokerSheet", widthDp = 360, showBackground = true, backgroundColor = 0xFF0B0B0B)
@Composable
internal fun PokerSheetPreview() {
    PokerPreviewPage(gutter = false) {
        PokerSheetContent(title = "Reset the bank?") {
            ConfirmSheetContent(
                body = "Clears names, buy-ins, rebuys, add-ons and knockouts for 9 players. The clock is not touched.",
                dismissLabel = "Keep",
                confirmLabel = "Clear 9 players",
                onDismiss = {},
                onConfirm = {},
                destructive = true,
            )
        }
    }
}
