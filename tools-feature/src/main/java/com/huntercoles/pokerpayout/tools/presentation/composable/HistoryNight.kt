package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.LocalWidthClass
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerIconButton
import com.huntercoles.pokerpayout.core.design.components.PokerTopBar
import com.huntercoles.pokerpayout.core.design.components.Stat
import com.huntercoles.pokerpayout.core.design.components.StatStrip
import com.huntercoles.pokerpayout.core.design.components.WidthClass
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.domain.history.NightPlayer
import com.huntercoles.pokerpayout.core.domain.history.SavedNight
import com.huntercoles.pokerpayout.core.domain.model.ordinalOf
import com.huntercoles.pokerpayout.core.utils.FormatUtils.formatMoney
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.HistoryText

/**
 * One saved night in full, read-only (S16, PP-037): the day and the setup's name in the top bar, the
 * prize pool and the number of players, then every player in finishing order with what they won and
 * what they paid in. Share sends it as text; Delete applies at once, with Undo on the snackbar.
 */
@Composable
internal fun NightContent(
    night: SavedNight,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = rememberHistoryText()
    Column(modifier.fillMaxSize().background(PokerColors.PokerBlack)) {
        PokerTopBar(title = text.date(night.date), subtitle = night.structureName, onBack = onClose) {
            PokerIconButton(
                icon = PokerIcons.Share,
                contentDescription = stringResource(R.string.history_share),
                onClick = onShare,
                tint = PokerColors.PokerGold,
            )
        }
        HistoryPane {
            StatStrip(
                listOf(
                    Stat(
                        label = stringResource(R.string.history_prize_pool),
                        value = formatMoney(night.prizePoolCents),
                        valueColor = PokerColors.PokerGold,
                    ),
                    Stat(label = stringResource(R.string.history_players_stat), value = night.players.size.toString()),
                ),
            )
            HistoryCard {
                night.players.forEachIndexed { index, player ->
                    if (index > 0) HorizontalDivider(color = PokerColors.FeltLine)
                    PlayerRow(player, text)
                }
            }
            PokerButton(
                text = stringResource(R.string.history_delete),
                onClick = onDelete,
                variant = PokerButtonVariant.DestructiveOutline,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** "1st  Dana / Paid in $50 · 2 knockouts · bounties $10  $235": one TalkBack stop. */
@Composable
private fun PlayerRow(player: NightPlayer, text: HistoryText) {
    val first = player.place == 1
    val color = if (first) PokerColors.PokerGold else PokerColors.CardWhite
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(ordinalOf(player.place), style = PokerType.NumberM, color = color, modifier = Modifier.widthIn(min = RankWidth))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(player.name, style = MaterialTheme.typography.titleMedium, color = PokerColors.CardWhite)
            Text(text.details(player), style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
        }
        if (player.wonCents > 0L) Text(formatMoney(player.wonCents), style = PokerType.NumberM, color = color)
    }
}

/** History's one scrolling column, with the app's gutters (12 dp on small phones). */
@Composable
internal fun HistoryPane(content: @Composable ColumnScope.() -> Unit) {
    val gutter = if (LocalWidthClass.current == WidthClass.Small) SmallGutter else PokerDimens.Gutter
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = gutter, end = gutter, top = 4.dp, bottom = PokerDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
        content = content,
    )
}

/** A felt card, as on the other tools. */
@Composable
internal fun HistoryCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PokerDimens.CornerCard))
            .background(PokerColors.FeltGreen)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/** Below the phone width, gutters narrow to 12 dp (design spec, section 4). */
private val SmallGutter = 12.dp
