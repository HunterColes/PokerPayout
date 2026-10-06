package com.huntercoles.pokerpayout.tools.presentation.composable

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerIconButton
import com.huntercoles.pokerpayout.core.design.components.PokerSheet
import com.huntercoles.pokerpayout.core.design.components.PokerTopBar
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.ChipSetIntent
import com.huntercoles.pokerpayout.tools.presentation.ChipSetUiState
import com.huntercoles.pokerpayout.tools.presentation.ChipSetViewModel

/** The chip set route ("Chip set" in the Tools list): [ChipSetContent], and the colour sheet when open. */
@Composable
fun ChipSetRoute(onBack: () -> Unit, viewModel: ChipSetViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ChipSetContent(state = state, onIntent = viewModel::acceptIntent, onBack = onBack)
    val editor = state.editor
    if (editor != null) {
        PokerSheet(
            onDismissRequest = { viewModel.acceptIntent(ChipSetIntent.CloseEditor) },
            title = colourSheetTitle(state, editor),
        ) {
            ColourEditorContent(state = state, editor = editor, onIntent = viewModel::acceptIntent)
        }
    }
}

/** From this width the set and the plan sit side by side, each scrolling on its own. */
private val TwoPaneWidth = 600.dp

/** Below the phone width, gutters narrow to 12 dp (design spec, section 4). */
private val SmallWidth = 360.dp
private val SmallGutter = 12.dp

/**
 * The chip set (S11, PP-033): the chips you own with a stepper each, one player's stack as a
 * picture of chip piles with the reserve check, the color-up plan from the blind schedule, and the
 * stack settings (the old calculator's advanced settings). The plan is live: there is no Generate.
 *
 * From 600 dp wide (tablets, phones on their side) the set and the plan are two panes.
 *
 * @param settingsOpen whether the stack settings start unfolded (screenshots of them).
 */
@Composable
fun ChipSetContent(
    state: ChipSetUiState,
    onIntent: (ChipSetIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    settingsOpen: Boolean = false,
) {
    var showSettings by rememberSaveable { mutableStateOf(settingsOpen) }
    Column(modifier.fillMaxSize().background(PokerColors.PokerBlack)) {
        PokerTopBar(
            title = stringResource(R.string.chip_set_title),
            subtitle = pluralStringResource(
                R.plurals.chip_set_subtitle,
                state.players,
                chipNumber(state.inventory.totalChips),
                state.players,
            ),
            onBack = onBack,
        ) {
            PokerIconButton(
                icon = PokerIcons.Restart,
                contentDescription = stringResource(R.string.chip_set_reset),
                onClick = { onIntent(ChipSetIntent.Reset) },
                tint = PokerColors.PokerGold,
            )
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val gutter = if (maxWidth < SmallWidth) SmallGutter else PokerDimens.Gutter
            val settings: @Composable () -> Unit = {
                SettingsCard(state, onIntent, expanded = showSettings, onExpand = { showSettings = it })
            }
            if (maxWidth >= TwoPaneWidth) {
                Row(Modifier.fillMaxSize().padding(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Pane(0.dp, Modifier.weight(1f)) {
                        OwnedCard(state, onIntent)
                        settings()
                    }
                    Pane(0.dp, Modifier.weight(1f)) {
                        StackCard(state)
                        ColorUpCard(state)
                    }
                }
            } else {
                Pane(gutter, Modifier.fillMaxSize()) {
                    OwnedCard(state, onIntent)
                    StackCard(state)
                    ColorUpCard(state)
                    settings()
                }
            }
        }
    }
}

/** One scrolling column of cards. */
@Composable
private fun Pane(gutter: Dp, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(start = gutter, end = gutter, top = 4.dp, bottom = PokerDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** A felt card: one part of the screen. */
@Composable
internal fun ChipSetSection(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PokerColors.FeltGreen, RoundedCornerShape(PokerDimens.CornerCard))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** An eyebrow on the left and a short Chalk note on the right, wrapping under it if it must. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SectionHeader(title: String, note: String?, gold: Boolean = true) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        PokerEyebrow(
            text = title,
            color = if (gold) PokerColors.PokerGold else PokerColors.Chalk,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = PokerColors.Chalk,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A dark well with a check (all good) or an alert, holding a few lines of text. */
@Composable
internal fun NoteBox(ok: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PokerColors.FeltDeep, RoundedCornerShape(PokerDimens.CornerControl))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = if (ok) PokerIcons.Check else PokerIcons.Info,
            contentDescription = null,
            tint = if (ok) PokerColors.Live else PokerColors.Danger,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}
