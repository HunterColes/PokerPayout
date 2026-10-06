package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerStepper
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.SeatDrawIntent
import com.huntercoles.pokerpayout.tools.presentation.SeatDrawText
import com.huntercoles.pokerpayout.tools.presentation.SeatDrawUiState
import com.huntercoles.pokerpayout.tools.presentation.SeatDrawViewModel
import com.huntercoles.pokerpayout.tools.seats.SeatDrawer

/**
 * Who's playing: how many, how many sit at a table, and their names, which start as the Bank's and
 * can be changed for this draw (Edit names); "Use the Bank's names" goes back. The plan line says
 * how the next draw splits them ("2 tables of 5").
 *
 * Once there is a draw the card folds to one line ([open] says whether it is unfolded), so the
 * tables come first.
 */
@Composable
internal fun PlayersCard(
    state: SeatDrawUiState,
    text: SeatDrawText,
    onIntent: (SeatDrawIntent) -> Unit,
    open: Boolean,
    onOpen: (Boolean) -> Unit,
) {
    val foldable = state.draw != null
    ChipSetSection {
        if (foldable) {
            FoldHeader(state, text, open, onOpen)
        } else {
            SectionHeader(
                title = stringResource(R.string.seat_draw_whos_playing),
                note = stringResource(if (state.fromBank) R.string.seat_draw_from_bank else R.string.seat_draw_changed),
            )
        }
        if (!foldable || open) PlayersSettings(state, text, onIntent)
    }
}

/** The folded card's header: who's playing in one line, and a chevron. The whole row is the button. */
@Composable
private fun FoldHeader(state: SeatDrawUiState, text: SeatDrawText, open: Boolean, onOpen: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PokerDimens.MinTouch)
            .clip(RoundedCornerShape(PokerDimens.CornerControl))
            .clickable(
                onClickLabel = stringResource(if (open) R.string.seat_draw_players_hide else R.string.seat_draw_players_show),
                role = Role.Button,
                onClick = { onOpen(!open) },
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PokerEyebrow(stringResource(R.string.seat_draw_whos_playing), color = PokerColors.PokerGold)
            val source = if (state.fromBank) R.string.seat_draw_summary_from_bank else R.string.seat_draw_summary_changed
            Text(
                text = stringResource(
                    R.string.seat_draw_players_summary,
                    text.players(state.seatNames.size),
                    state.seatsPerTable,
                    stringResource(source),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = PokerColors.Chalk,
            )
        }
        Icon(
            imageVector = PokerIcons.ChevronDown,
            contentDescription = null,
            tint = PokerColors.PokerGold,
            modifier = Modifier.rotate(if (open) HALF_TURN else 0f),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayersSettings(state: SeatDrawUiState, text: SeatDrawText, onIntent: (SeatDrawIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingRow(
            label = stringResource(R.string.seat_draw_players),
            value = state.players.size,
            range = SeatDrawer.MIN_PLAYERS..SeatDrawer.MAX_PLAYERS,
            onChange = { onIntent(SeatDrawIntent.SetPlayerCount(it)) },
        )
        SettingRow(
            label = stringResource(R.string.seat_draw_seats_per_table),
            value = state.seatsPerTable,
            range = SeatDrawer.MIN_SEATS_PER_TABLE..SeatDrawer.MAX_SEATS_PER_TABLE,
            onChange = { onIntent(SeatDrawIntent.SetSeatsPerTable(it)) },
        )
        if (state.editingNames) {
            NameFields(state, onIntent)
        } else {
            Text(
                text = state.seatNames.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                color = PokerColors.CardWhite,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(PokerIcons.Seat, contentDescription = null, tint = PokerColors.Chalk, modifier = Modifier.size(18.dp))
            Text(text = text.plan(state.tableSizes), style = MaterialTheme.typography.bodyMedium, color = PokerColors.Chalk)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PokerButton(
                text = stringResource(if (state.editingNames) R.string.seat_draw_names_done else R.string.seat_draw_edit_names),
                onClick = { onIntent(SeatDrawIntent.EditNames(open = !state.editingNames)) },
                variant = PokerButtonVariant.Secondary,
                size = PokerButtonSize.Small,
                icon = if (state.editingNames) PokerIcons.Check else PokerIcons.Edit,
            )
            if (!state.fromBank) {
                PokerButton(
                    text = stringResource(R.string.seat_draw_use_bank),
                    onClick = { onIntent(SeatDrawIntent.UseBankNames) },
                    variant = PokerButtonVariant.Text,
                    size = PokerButtonSize.Small,
                )
            }
        }
    }
}

/** A label and its stepper, side by side when they fit, the stepper under the label when not. */
@Composable
private fun SettingRow(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    SideBySideOrStacked(
        modifier = Modifier.fillMaxWidth(),
        first = { Text(text = label, style = MaterialTheme.typography.titleSmall, color = PokerColors.CardWhite) },
        second = {
            PokerStepper(
                value = value,
                onValueChange = onChange,
                range = range,
                label = label,
                modifier = Modifier.widthIn(min = StepperWidth),
            )
        },
    )
}

/** One field per player, numbered; a blank one is drawn as its "Player N". */
@Composable
private fun NameFields(state: SeatDrawUiState, onIntent: (SeatDrawIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.players.forEachIndexed { index, name ->
            NameField(
                number = index + 1,
                value = name,
                placeholder = state.seatNames[index],
                last = index == state.players.lastIndex,
                onChange = { onIntent(SeatDrawIntent.SetName(index, it)) },
            )
        }
    }
}

/**
 * A name field in the app's field style: dark green, a FeltEdge outline that turns gold in focus.
 * It edits its own text, so fast typing never races the saved name coming back; the saved name
 * replaces it only while the field isn't being typed in ("Use the Bank's names").
 */
@Composable
private fun NameField(number: Int, value: String, placeholder: String, last: Boolean, onChange: (String) -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(value, focused) { if (!focused && value != text) text = value }
    val shape = RoundedCornerShape(PokerDimens.CornerControl)
    val label = stringResource(R.string.seat_draw_name_label, number)
    BasicTextField(
        value = text,
        onValueChange = { typed ->
            text = typed.take(SeatDrawViewModel.MAX_NAME_LENGTH)
            onChange(text)
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = PokerColors.CardWhite),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = if (last) ImeAction.Done else ImeAction.Next,
        ),
        interactionSource = interactions,
        cursorBrush = SolidColor(PokerColors.PokerGold),
        decorationBox = { field ->
            Row(
                modifier = Modifier
                    .heightIn(min = PokerDimens.ControlHeight)
                    .clip(shape)
                    .background(PokerColors.DarkGreen)
                    .border(if (focused) 2.dp else 1.dp, if (focused) PokerColors.PokerGold else PokerColors.FeltEdge, shape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = number.toString(),
                    style = PokerType.NumberM.copy(fontSize = 16.sp, lineHeight = 20.sp),
                    color = PokerColors.Chalk,
                    modifier = Modifier.widthIn(min = 20.dp),
                )
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) {
                        Text(text = placeholder, style = MaterialTheme.typography.bodyLarge, color = PokerColors.Chalk)
                    }
                    field()
                }
            }
        },
    )
}

/** Wide enough for two-digit counts without the number squeezing the buttons. */
private val StepperWidth = 148.dp
private const val HALF_TURN = 180f
