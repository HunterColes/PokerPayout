package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerIconButton
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tournament.R

/**
 * PP-113: the one welcome a new install gets, at the top of the setup page, instead of a tutorial.
 * One line that says what to do and that nothing leaves the phone, a way to the starter nights
 * ([onSeeStarters] opens the presets sheet, where they are listed), and ✕, which hides it for good.
 */
@Composable
internal fun WelcomeCard(onSeeStarters: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(PokerDimens.CornerCard)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, PokerColors.FeltEdge), shape)
            .background(PokerColors.FeltGreen, shape)
            .padding(start = 16.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.welcome_line),
                style = MaterialTheme.typography.bodyMedium,
                color = PokerColors.CardWhite,
            )
            PokerButton(
                text = stringResource(R.string.welcome_see_starters),
                onClick = onSeeStarters,
                variant = PokerButtonVariant.Text,
                size = PokerButtonSize.Small,
                icon = PokerIcons.List,
                // A text button's label sits in from its edge: line it up with the line above
                modifier = Modifier.offset(x = -TEXT_BUTTON_INSET),
            )
        }
        PokerIconButton(
            icon = PokerIcons.Close,
            contentDescription = stringResource(R.string.welcome_dismiss),
            onClick = onDismiss,
        )
    }
}

/** How far a text button's icon sits in from its edge ([PokerButtonVariant.Text]'s padding). */
private val TEXT_BUTTON_INSET = 12.dp
