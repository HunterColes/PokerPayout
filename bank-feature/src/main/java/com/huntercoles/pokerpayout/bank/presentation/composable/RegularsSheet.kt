package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.bank.R
import com.huntercoles.pokerpayout.bank.presentation.RegularRow
import com.huntercoles.pokerpayout.bank.presentation.RegularsModel
import com.huntercoles.pokerpayout.core.constants.TournamentConstants
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerField
import com.huntercoles.pokerpayout.core.design.components.PokerSheet
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.domain.players.PlayerNames
import com.huntercoles.pokerpayout.core.domain.players.Regular
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The tonight's players sheet as a modal bottom sheet. */
@Composable
internal fun RegularsSheet(
    model: RegularsModel,
    onToggle: (String) -> Unit,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    PokerSheet(onDismissRequest = onDismiss) {
        RegularsSheetContent(model, onToggle, onAdd, onDismiss)
    }
}

/**
 * Tonight's players (S26, PP-110): how many seats have a name, a field to add a name (it also
 * narrows the list as it is typed), then every regular, most nights lately first, each ticked when at
 * the table tonight. A tap seats a regular or frees their seat; the list keeps its order meanwhile.
 * With nobody yet, it says where regulars come from.
 */
@Composable
internal fun RegularsSheetContent(
    model: RegularsModel,
    onToggle: (String) -> Unit,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
    initialQuery: String = "",
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
    ) {
        SheetHeading(title = stringResource(R.string.bank_regulars)) {}
        Text(text = seatsLine(model), style = MaterialTheme.typography.bodyMedium, color = PokerColors.Chalk)
        AddName(
            query = query,
            canAdd = model.canAdd(query),
            onQuery = { query = it.take(PlayerNames.MAX_LENGTH) },
            onAdd = {
                onAdd(query)
                query = ""
            },
        )
        val rows = model.matching(query)
        when {
            model.rows.isEmpty() -> Note(stringResource(R.string.bank_regulars_empty))
            rows.isEmpty() -> Note(stringResource(R.string.bank_regulars_no_match, query.trim()))
            else -> RegularsList(rows, canSeat = model.canSeat, onToggle = onToggle)
        }
        PokerButton(
            text = stringResource(R.string.bank_regulars_done),
            onClick = onDismiss,
            variant = PokerButtonVariant.Secondary,
            size = PokerButtonSize.Small,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** "6 of 9 seats named. Tap a name…", or, every seat named, what picking another does. */
@Composable
private fun seatsLine(model: RegularsModel): String = when {
    model.open > 0 -> pluralStringResource(R.plurals.bank_regulars_seats, model.seats, model.named, model.seats)
    model.canAddSeat -> pluralStringResource(R.plurals.bank_regulars_full, model.seats, model.seats)
    else -> stringResource(R.string.bank_regulars_max, TournamentConstants.MAX_PLAYERS)
}

/** The name field and Add: Done on the keyboard adds too. */
@Composable
private fun AddName(query: String, canAdd: Boolean, onQuery: (String) -> Unit, onAdd: () -> Unit) {
    val label = stringResource(R.string.bank_regulars_add_label)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        PokerField(
            value = query,
            onValueChange = onQuery,
            label = label,
            keyboardType = KeyboardType.Text,
            capitalization = KeyboardCapitalization.Words,
            fieldModifier = Modifier.semantics { contentDescription = label },
            keyboardActions = KeyboardActions(onDone = { if (canAdd) onAdd() }),
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        PokerButton(
            text = stringResource(R.string.bank_regulars_add),
            onClick = onAdd,
            variant = PokerButtonVariant.Secondary,
            size = PokerButtonSize.Small,
            icon = PokerIcons.PersonAdd,
            enabled = canAdd,
        )
    }
}

/** "REGULARS · most nights lately first", then a line per regular. */
@Composable
private fun RegularsList(rows: List<RegularRow>, canSeat: Boolean, onToggle: (String) -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PokerEyebrow(
                text = stringResource(R.string.bank_regulars_heading),
                color = PokerColors.PokerGold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.bank_regulars_order),
                style = MaterialTheme.typography.bodySmall,
                color = PokerColors.Chalk,
                modifier = Modifier.weight(1f),
            )
        }
        val dates = rememberShortDates()
        rows.forEachIndexed { index, row ->
            RegularLine(
                row = row,
                detail = detail(row.regular, dates),
                rule = index > 0,
                enabled = row.seated || canSeat,
                onToggle = { onToggle(row.regular.name) },
            )
        }
    }
}

/** "Dana / 4 nights · last Oct 2, 2026   [✓]": the whole line toggles; TalkBack reads it as one checkbox. */
@Composable
private fun RegularLine(row: RegularRow, detail: String, rule: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (rule) Modifier.topRule() else Modifier)
            .toggleable(value = row.seated, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .heightIn(min = 52.dp)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = row.regular.name, style = MaterialTheme.typography.titleMedium, color = PokerColors.CardWhite)
            Text(text = detail, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
        }
        Box(Modifier.size(PokerDimens.MinTouch), contentAlignment = Alignment.Center) { TickBox(row.seated) }
    }
}

/** "4 nights · last Oct 2, 2026", or "No saved night yet" for a name only the Bank has used. */
@Composable
private fun detail(regular: Regular, dates: DateTimeFormatter): String {
    val last = regular.lastPlayed ?: return stringResource(R.string.bank_regulars_new)
    return pluralStringResource(R.plurals.bank_regulars_played, regular.nights, regular.nights, dates.format(last))
}

@Composable
private fun rememberShortDates(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
}

@Composable
private fun Note(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = PokerColors.Chalk)
}
