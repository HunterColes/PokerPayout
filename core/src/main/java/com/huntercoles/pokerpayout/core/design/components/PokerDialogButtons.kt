package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors

/**
 * Standardized dialog button pair (Cancel/Confirm) used across confirmation dialogs.
 * Provides consistent styling and layout for dialog actions.
 * 
 * @param onCancel Callback when cancel button is clicked
 * @param onConfirm Callback when confirm button is clicked
 * @param cancelText Text for the cancel button (default: "Cancel")
 * @param confirmText Text for the confirm button (default: "Confirm")
 * @param isConfirmDestructive Whether confirm action is destructive (changes color to gold)
 * @param modifier Optional modifier for the button row
 */
@Composable
fun PokerDialogButtons(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    cancelText: String = "Cancel",
    confirmText: String = "Confirm",
    isConfirmDestructive: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
    ) {
        TextButton(onClick = onCancel) {
            Text(
                text = cancelText,
                color = PokerColors.CardWhite
            )
        }

        TextButton(onClick = onConfirm) {
            Text(
                text = confirmText,
                color = if (isConfirmDestructive) PokerColors.PokerGold else PokerColors.AccentGreen,
                fontWeight = FontWeight.Bold
            )
        }
    }
}