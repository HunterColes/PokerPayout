package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import com.huntercoles.pokerpayout.core.design.PokerColors

/**
 * Provides consistent theming for all number input text fields in the app.
 * This ensures a uniform appearance across tournament config, blinds config, and all calculators.
 * 
 * Visual Style:
 * - Focused: AccentGreen border (#4CAF50)
 * - Unfocused: CardWhite border
 * - Cursor: PokerGold
 * - Selection handles: PokerGold with semi-transparent background
 * - Text: CardWhite
 * - Disabled: Semi-transparent borders and PokerGold text
 */
object PokerTextFieldDefaults {
    
    /**
     * Standard colors for poker-themed number input fields.
     * Use this for all numeric text fields to ensure consistency.
     */
    @Composable
    fun colors(
        isLocked: Boolean = false
    ): TextFieldColors {
        return OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (isLocked) PokerColors.CardWhite.copy(alpha = 0.5f) else PokerColors.AccentGreen,
            unfocusedBorderColor = if (isLocked) PokerColors.CardWhite.copy(alpha = 0.5f) else PokerColors.CardWhite,
            focusedTextColor = if (isLocked) PokerColors.PokerGold else PokerColors.CardWhite,
            unfocusedTextColor = if (isLocked) PokerColors.PokerGold else PokerColors.CardWhite,
            disabledBorderColor = PokerColors.CardWhite.copy(alpha = 0.5f),
            disabledTextColor = PokerColors.PokerGold,
            focusedLabelColor = if (isLocked) PokerColors.PokerGold else PokerColors.AccentGreen,
            unfocusedLabelColor = if (isLocked) PokerColors.PokerGold else PokerColors.CardWhite,
            disabledLabelColor = PokerColors.PokerGold.copy(alpha = 0.7f),
            cursorColor = PokerColors.PokerGold,
            selectionColors = TextSelectionColors(
                handleColor = PokerColors.PokerGold,
                backgroundColor = PokerColors.PokerGold.copy(alpha = 0.4f)
            )
        )
    }
}
