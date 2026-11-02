package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons

/**
 * An empty list's note: a gold [icon], a short [title] and one [body] line saying where things come
 * from ("No nights saved yet" / "Settle up saves each night here."). The icon is decorative; the
 * title is a heading for TalkBack. Both lines wrap, never ellipsize.
 */
@Composable
fun PokerEmptyNote(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium)) {
        Icon(icon, contentDescription = null, tint = PokerColors.PokerGold, modifier = Modifier.size(EmptyNoteIconSize))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingSmall)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = PokerColors.CardWhite,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = PokerColors.Chalk)
        }
    }
}

private val EmptyNoteIconSize = 24.dp

@Preview(name = "PokerEmptyNote", widthDp = 360, showBackground = true, backgroundColor = 0xFF0B0B0B)
@Composable
internal fun PokerEmptyNotePreview() {
    PokerPreviewPage {
        PokerStage {
            PokerEmptyNote(
                icon = PokerIcons.People,
                title = "No regulars yet",
                body = "Names you type in the Bank and the players of saved nights show up here.",
            )
            PokerEmptyNote(
                icon = PokerIcons.MusicNote,
                title = "Noch keine Lieder in der Wiedergabeliste für den Abend",
                body = "Füge Lieder von diesem Telefon hinzu; sie spielen während der Partie und pausieren in den Pausen.",
            )
        }
    }
}
