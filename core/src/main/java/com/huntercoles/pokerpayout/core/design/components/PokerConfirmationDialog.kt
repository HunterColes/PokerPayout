package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDialog

/**
 * Standardized confirmation dialog used for destructive actions like reset.
 * Follows consistent design pattern across all features.
 * 
 * @param title Dialog title (e.g., "Reset bank data?")
 * @param description Explanation text shown in the info surface
 * @param onDismiss Callback when dialog should be dismissed (cancel or outside click)
 * @param onConfirm Callback when confirm action is selected
 * @param cancelText Text for cancel button (default: "Cancel")
 * @param confirmText Text for confirm button (default: "Reset")
 * @param isVisible Whether dialog should be shown
 */
@Composable
fun PokerConfirmationDialog(
    title: String,
    description: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    cancelText: String = "Cancel",
    confirmText: String = "Reset",
    isVisible: Boolean = true
) {
    if (isVisible) {
        PokerDialog(onDismissRequest = onDismiss) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = PokerColors.PokerGold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = PokerColors.FeltGreen,
                border = BorderStroke(1.dp, PokerColors.PokerGold.copy(alpha = 0.6f))
            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PokerColors.CardWhite,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            PokerDialogButtons(
                onCancel = onDismiss,
                onConfirm = onConfirm,
                cancelText = cancelText,
                confirmText = confirmText,
                isConfirmDestructive = true
            )
        }
    }
}