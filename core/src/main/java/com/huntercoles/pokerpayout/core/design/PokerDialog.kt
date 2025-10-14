package com.huntercoles.pokerpayout.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

import com.huntercoles.pokerpayout.core.design.PokerColors

@Composable
fun PokerDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    contentPadding: PaddingValues = PaddingValues(24.dp),
    cornerRadius: Dp = 24.dp,
    tonalElevation: Dp = 12.dp,
    properties: DialogProperties = DialogProperties(),
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(cornerRadius),
            color = PokerColors.DarkGreen,
            tonalElevation = tonalElevation,
            shadowElevation = tonalElevation,
            border = BorderStroke(1.dp, PokerColors.PokerGold)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                content = content
            )
        }
    }
}
