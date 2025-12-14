package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.components.invertHorizontally

/**
 * Standardized reset button used across all features.
 * Provides consistent styling with green circular background and gold refresh icon.
 * 
 * @param onClick Callback when the reset button is clicked
 * @param contentDescription Accessibility description for the button
 * @param modifier Optional modifier for the button
 */
@Composable
fun PokerResetButton(
    onClick: () -> Unit,
    contentDescription: String = "Reset",
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.size(48.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = PokerColors.DarkGreen)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = contentDescription,
                tint = PokerColors.PokerGold,
                modifier = Modifier
                    .size(24.dp)
                    .invertHorizontally()
            )
        }
    }
}