package com.huntercoles.pokerpayout.tools.presentation.composable

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.LocalWidthClass
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerIconButton
import com.huntercoles.pokerpayout.core.design.components.PokerTopBar
import com.huntercoles.pokerpayout.core.design.components.WidthClass
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.SeatDrawIntent
import com.huntercoles.pokerpayout.tools.presentation.SeatDrawText
import com.huntercoles.pokerpayout.tools.presentation.SeatDrawUiState
import com.huntercoles.pokerpayout.tools.presentation.SeatDrawViewModel
import com.huntercoles.pokerpayout.tools.seats.SeatDraw

/** The seat draw route ("Seat draw" in the Tools list): [SeatDrawContent], sharing the draw as text. */
@Composable
fun SeatDrawRoute(onBack: () -> Unit, viewModel: SeatDrawViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val text = rememberSeatDrawText()
    val title = stringResource(R.string.seat_draw_title)
    val chooser = stringResource(R.string.seat_draw_share)
    SeatDrawContent(
        state = state,
        onIntent = viewModel::acceptIntent,
        onBack = onBack,
        onShare = { draw -> shareAsText(context, text.share(draw), subject = title, chooserTitle = chooser) },
    )
}

/** Hands [text] to any app that takes plain text (ACTION_SEND), through the system's share sheet. */
private fun shareAsText(context: Context, text: String, subject: String, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(send, chooserTitle)) }
}

@Composable
internal fun rememberSeatDrawText(): SeatDrawText {
    val resources = LocalContext.current.resources
    val configuration = LocalConfiguration.current
    return remember(resources, configuration) { SeatDrawText(resources) }
}

/** Below the phone width, gutters narrow to 12 dp (design spec, section 4). */
private val SmallWidth = 360.dp
private val SmallGutter = 12.dp

/** From this font scale on, the tables stack in one column. */
private const val LARGE_TEXT = 1.5f

/** Windows at least this wide but shorter than [ShortHeight] (phones on their side) get two panes. */
private val TwoPaneWidth = 600.dp
private val ShortHeight = 480.dp

/** The controls' share of the width in two panes; the tables get the rest. */
private const val CONTROLS_SHARE = 0.44f

/**
 * Seat draw (S14, PP-036): who's playing (the Bank's players, or a list changed for the draw) and
 * how many sit at a table; then the draw, as a card per table listing seat and name; then the
 * high-card draw for the button, which marks the button and the blinds by seat. From 600 dp wide
 * the tables sit two to a row; on a phone on its side the controls and the tables are two panes.
 * Once there is a draw, who's playing folds to one line.
 *
 * @param playersOpen whether that folded card starts unfolded (screenshots of it).
 */
@Suppress("LongParameterList") // a screen: its state, its three ways out, a modifier, and one test hook
@Composable
fun SeatDrawContent(
    state: SeatDrawUiState,
    onIntent: (SeatDrawIntent) -> Unit,
    onBack: () -> Unit,
    onShare: (SeatDraw) -> Unit,
    modifier: Modifier = Modifier,
    playersOpen: Boolean = false,
) {
    var showPlayers by rememberSaveable { mutableStateOf(playersOpen) }
    val text = rememberSeatDrawText()
    val draw = state.draw
    // Two tables a row from 600 dp, except at the largest text, where every screen reflows in one
    // column (design spec, section 6) and a half-width card would cut its pills.
    val twoUp = LocalWidthClass.current >= WidthClass.Medium && LocalDensity.current.fontScale < LARGE_TEXT
    val columns = if (twoUp) 2 else 1
    Column(modifier.fillMaxSize().background(PokerColors.PokerBlack)) {
        val subtitle = stringResource(
            R.string.seat_draw_subtitle,
            text.players(state.seatNames.size),
            text.tables(state.tableSizes.size),
        )
        PokerTopBar(title = stringResource(R.string.seat_draw_title), subtitle = subtitle, onBack = onBack) {
            if (draw != null) {
                PokerIconButton(
                    icon = PokerIcons.Share,
                    contentDescription = stringResource(R.string.seat_draw_share),
                    onClick = { onShare(draw) },
                    tint = PokerColors.PokerGold,
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val gutter = if (maxWidth < SmallWidth) SmallGutter else PokerDimens.Gutter
            val controls: @Composable ColumnScope.() -> Unit = {
                PlayersCard(state, text, onIntent, open = showPlayers, onOpen = { showPlayers = it })
                DrawActions(state, onIntent)
            }
            val tables: @Composable ColumnScope.(Int) -> Unit = { tableColumns ->
                if (draw != null) {
                    if (state.drawIsStale) {
                        NoteBox(ok = false) { NoteText(stringResource(R.string.seat_draw_stale)) }
                    }
                    TablesGrid(draw = draw, columns = tableColumns, dealToAnimate = state.dealToAnimate, text = text)
                }
            }
            // A phone on its side is wide but short: the controls and the tables scroll side by side,
            // so the tables are in view. Elsewhere one column, with the tables after the controls.
            if (draw != null && maxWidth >= TwoPaneWidth && maxHeight < ShortHeight) {
                Row(Modifier.fillMaxSize().padding(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Pane(Modifier.weight(CONTROLS_SHARE)) { controls() }
                    Pane(Modifier.weight(1f - CONTROLS_SHARE)) { tables(1) }
                }
            } else {
                Pane(Modifier.fillMaxSize().padding(horizontal = gutter)) {
                    controls()
                    tables(columns)
                }
            }
        }
    }
}

/** One scrolling column of the screen. */
@Composable
private fun Pane(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(top = 4.dp, bottom = PokerDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/**
 * The next step is the one gold button: draw the seats, then deal for the button. Redrawing and
 * dealing again are secondary, and both offer Undo. The rule for the button is always on screen
 * once there are seats.
 */
@Composable
private fun DrawActions(state: SeatDrawUiState, onIntent: (SeatDrawIntent) -> Unit) {
    val draw = state.draw
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (draw == null) {
            PokerButton(
                text = stringResource(R.string.seat_draw_draw_seats),
                onClick = { onIntent(SeatDrawIntent.DrawSeats) },
                icon = PokerIcons.Seat,
                modifier = Modifier.fillMaxWidth(),
            )
            NoteLine(icon = PokerIcons.Info, text = stringResource(R.string.seat_draw_how))
        } else {
            DrawnActions(draw, onIntent)
        }
    }
}

/** Once seats are drawn: deal for the button (gold until it's dealt), redraw, deal again, and the rule. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DrawnActions(draw: SeatDraw, onIntent: (SeatDrawIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!draw.buttonDealt) {
            PokerButton(
                text = stringResource(R.string.seat_draw_deal_button),
                onClick = { onIntent(SeatDrawIntent.DealButton) },
                icon = PokerIcons.Cards,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryAction(R.string.seat_draw_redraw_seats, PokerIcons.Restart) { onIntent(SeatDrawIntent.DrawSeats) }
            if (draw.buttonDealt) {
                SecondaryAction(R.string.seat_draw_deal_again, PokerIcons.Cards) { onIntent(SeatDrawIntent.DealButton) }
            }
        }
        val spoken = stringResource(R.string.seat_draw_rule_spoken)
        NoteLine(
            icon = PokerIcons.Info,
            text = stringResource(R.string.seat_draw_rule),
            modifier = Modifier.semantics { contentDescription = spoken },
        )
    }
}

@Composable
private fun SecondaryAction(label: Int, icon: ImageVector, onClick: () -> Unit) {
    PokerButton(
        text = stringResource(label),
        onClick = onClick,
        variant = PokerButtonVariant.Secondary,
        size = PokerButtonSize.Small,
        icon = icon,
    )
}

/** A Chalk line with an icon: how the draw works, the rule for the button. */
@Composable
private fun NoteLine(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = PokerColors.Chalk, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun NoteText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = PokerColors.CardWhite)
}
