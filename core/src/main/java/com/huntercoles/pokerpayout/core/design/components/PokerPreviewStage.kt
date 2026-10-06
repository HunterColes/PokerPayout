package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerTheme

/**
 * The page behind a component's preview and its gallery golden: [PokerTheme] with motion off, on
 * PokerBlack, with the 16 dp gutter (bars that span the screen pass `gutter = false`). [PokerStage]
 * groups related states, as in the mockups.
 */
@Composable
internal fun PokerPreviewPage(gutter: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    PokerTheme(reducedMotion = true) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PokerColors.PokerBlack)
                .padding(vertical = PokerDimens.Gutter, horizontal = if (gutter) PokerDimens.Gutter else 0.dp),
            verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
            content = content,
        )
    }
}

/** One group of states: a black or felt well, like the `.stage` boxes in the mockups. */
@Composable
internal fun PokerStage(
    modifier: Modifier = Modifier,
    felt: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (felt) {
                    Modifier
                        .clip(RoundedCornerShape(PokerDimens.CornerControl))
                        .background(PokerColors.FeltGreen)
                        .padding(PokerDimens.SpacingMedium)
                } else {
                    Modifier
                },
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}
