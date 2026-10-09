package com.huntercoles.pokerpayout.tools.presentation.composable

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerSegmentedControl
import com.huntercoles.pokerpayout.core.design.components.PokerTopBar
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.domain.history.NightCsv
import com.huntercoles.pokerpayout.core.domain.history.SavedNight
import com.huntercoles.pokerpayout.core.domain.history.Standing
import com.huntercoles.pokerpayout.core.domain.model.ordinalOf
import com.huntercoles.pokerpayout.core.domain.players.PlayerNames
import com.huntercoles.pokerpayout.core.utils.FormatUtils.formatMoney
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.HistoryIntent
import com.huntercoles.pokerpayout.tools.presentation.HistoryText
import com.huntercoles.pokerpayout.tools.presentation.HistoryUiState
import com.huntercoles.pokerpayout.tools.presentation.HistoryViewModel
import java.time.LocalDate

/**
 * The History route ("History" in the Tools list, PP-037): [HistoryContent], sharing a night as text
 * and every night as CSV, through the system's share sheet as text or saved as a file where the
 * player picks (the file picker's CreateDocument). Back closes an open night first.
 */
@Composable
fun HistoryRoute(onBack: () -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val text = rememberHistoryText()
    val shareTitle = stringResource(R.string.history_share)
    val exportTitle = stringResource(R.string.history_export)
    val exportSubject = stringResource(R.string.history_export_subject)
    val csvFileName = stringResource(R.string.history_csv_file_name, LocalDate.now().toString())
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV_TYPE)) { uri ->
        uri?.let { viewModel.acceptIntent(HistoryIntent.SaveCsv(it)) }
    }
    BackHandler(enabled = state.openNight != null) { viewModel.acceptIntent(HistoryIntent.Close) }
    HistoryContent(
        state = state,
        onIntent = viewModel::acceptIntent,
        onBack = onBack,
        onShare = { night -> send(context, "text/plain", text.share(night), text.date(night.date), shareTitle) },
        onExport = { how ->
            when (how) {
                CsvExport.Share -> send(context, CSV_TYPE, NightCsv.of(state.nights), exportSubject, exportTitle)
                CsvExport.Save -> runCatching { saveCsv.launch(csvFileName) }
            }
        },
    )
}

/** The CSV as text through the share sheet, or as a file saved where the player picks. */
enum class CsvExport { Share, Save }

private const val CSV_TYPE = "text/csv"

/** Hands [text] to any app that takes [type] (ACTION_SEND), through the system's share sheet; no permission needed. */
private fun send(context: Context, type: String, text: String, subject: String, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        this.type = type
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(send, chooserTitle)) }
}

@Composable
internal fun rememberHistoryText(): HistoryText {
    val resources = LocalContext.current.resources
    val configuration = LocalConfiguration.current
    return remember(resources, configuration) { HistoryText(resources) }
}

/**
 * History (S16, PP-037), stateless: the season's points standings with a year picker and its player
 * of the year, then the saved nights, the latest first, and the CSV (a file, or shared). A night opens in full
 * ([NightContent]). With nothing saved yet it says how to save a night. A player tapped in the
 * standings opens in a sheet, to merge two names of one person or separate them ([PlayerSheet], PP-110).
 */
@Suppress("LongParameterList") // a screen: its state, its intents, its three ways out, and a modifier
@Composable
fun HistoryContent(
    state: HistoryUiState,
    onIntent: (HistoryIntent) -> Unit,
    onBack: () -> Unit,
    onShare: (SavedNight) -> Unit,
    onExport: (CsvExport) -> Unit,
    modifier: Modifier = Modifier,
) {
    val night = state.openNight
    if (night != null) {
        NightContent(
            night = night,
            onClose = { onIntent(HistoryIntent.Close) },
            onShare = { onShare(night) },
            onDelete = { onIntent(HistoryIntent.Delete(night.id)) },
            modifier = modifier,
        )
    } else {
        NightsList(state, onIntent, onBack, onExport, modifier)
        // A player from the standings: their names, to merge or separate (S25b, PP-110)
        state.player?.let { PlayerSheet(it, onIntent) }
    }
}

/** The season and the nights, or how to save a first night. */
@Composable
private fun NightsList(
    state: HistoryUiState,
    onIntent: (HistoryIntent) -> Unit,
    onBack: () -> Unit,
    onExport: (CsvExport) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = rememberHistoryText()
    Column(modifier.fillMaxSize().background(PokerColors.PokerBlack)) {
        val count = state.nights.size
        PokerTopBar(
            title = stringResource(R.string.history_title),
            subtitle = if (count > 0) pluralStringResource(R.plurals.history_nights_count, count, count) else null,
            onBack = onBack,
        )
        HistoryPane {
            if (state.nights.isEmpty()) {
                EmptyNote()
            } else {
                SeasonSection(state, text, onIntent)
                PokerEyebrow(
                    stringResource(R.string.history_nights_heading),
                    modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                )
                state.nights.forEach { saved -> NightRow(saved, text) { onIntent(HistoryIntent.Open(saved.id)) } }
                PokerButton(
                    text = stringResource(R.string.history_save_csv),
                    onClick = { onExport(CsvExport.Save) },
                    variant = PokerButtonVariant.Secondary,
                    icon = PokerIcons.Save,
                    modifier = Modifier.fillMaxWidth(),
                )
                PokerButton(
                    text = stringResource(R.string.history_export),
                    onClick = { onExport(CsvExport.Share) },
                    variant = PokerButtonVariant.Secondary,
                    icon = PokerIcons.Share,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Nothing saved yet: where nights come from. */
@Composable
private fun EmptyNote() {
    HistoryCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(PokerIcons.Trophy, contentDescription = null, tint = PokerColors.PokerGold, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.history_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = PokerColors.CardWhite,
                )
                Text(
                    text = stringResource(R.string.history_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PokerColors.Chalk,
                )
            }
        }
    }
}

/**
 * The season: all time or a year, its player of the year, the standings (rank, name, nights and wins,
 * points) and the points rule in one line.
 */
@Composable
private fun SeasonSection(state: HistoryUiState, text: HistoryText, onIntent: (HistoryIntent) -> Unit) {
    PokerEyebrow(stringResource(R.string.history_season), modifier = Modifier.semantics { heading() })
    val allTime = stringResource(R.string.history_all_time)
    PokerSegmentedControl(
        options = listOf<Int?>(null) + state.years,
        selected = state.year,
        onSelect = { onIntent(HistoryIntent.SelectYear(it)) },
        label = { year -> year?.toString() ?: allTime },
    )
    text.leaders(state.year, state.leaders)?.let { leaders ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                PokerIcons.Crown,
                contentDescription = null,
                tint = PokerColors.PokerGold,
                modifier = Modifier.padding(top = 2.dp).size(20.dp),
            )
            Text(
                text = leaders,
                style = MaterialTheme.typography.titleMedium,
                color = PokerColors.CardWhite,
                modifier = Modifier.weight(1f),
            )
        }
    }
    HistoryCard {
        state.standings.forEachIndexed { index, standing ->
            if (index > 0) HorizontalDivider(color = PokerColors.FeltLine)
            StandingRow(standing, text) { onIntent(HistoryIntent.OpenPlayer(standing.name)) }
        }
    }
    Text(stringResource(R.string.history_points_rule), style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
    Text(stringResource(R.string.history_merge_hint), style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
}

/**
 * "1st  Dana / 3 nights · 2 wins  18"; TalkBack reads it as one: "1st, Dana, 18 points, 3 nights · 2
 * wins". A tap opens the player, to merge two of their names (PP-110); a seat nobody named ("Player
 * 3") is nobody's, so it doesn't open.
 */
@Composable
private fun StandingRow(standing: Standing, text: HistoryText, onOpen: () -> Unit) {
    val top = standing.rank == 1
    val spoken = listOf(ordinalOf(standing.rank), standing.name, text.points(standing.points), text.record(standing))
        .joinToString(", ")
    val open = stringResource(R.string.history_open_player, standing.name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PokerDimens.MinTouch)
            .then(
                if (PlayerNames.isPlaceholder(standing.name)) {
                    Modifier
                } else {
                    Modifier.clickable(onClickLabel = open, role = Role.Button, onClick = onOpen)
                },
            )
            .padding(vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = ordinalOf(standing.rank),
            style = PokerType.NumberM,
            color = if (top) PokerColors.PokerGold else PokerColors.CardWhite,
            modifier = Modifier.widthIn(min = RankWidth),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(standing.name, style = MaterialTheme.typography.titleMedium, color = PokerColors.CardWhite)
            Text(text.record(standing), style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
        }
        Text(
            text = standing.points.toString(),
            style = PokerType.NumberL,
            color = if (top) PokerColors.PokerGold else PokerColors.CardWhite,
        )
    }
}

/** One saved night: the day, how many played and who won, the pool. A tap opens it. */
@Composable
private fun NightRow(night: SavedNight, text: HistoryText, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PokerDimens.RowMinHeight)
            .clip(RoundedCornerShape(PokerDimens.CornerCard))
            .background(PokerColors.FeltGreen)
            .clickable(onClickLabel = stringResource(R.string.history_open), role = Role.Button, onClick = onOpen)
            .padding(start = 16.dp, top = 10.dp, end = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text.date(night.date), style = MaterialTheme.typography.titleMedium, color = PokerColors.CardWhite)
            Text(text.nightLine(night), style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
        }
        Text(formatMoney(night.prizePoolCents), style = PokerType.NumberM, color = PokerColors.PokerGold)
        Icon(PokerIcons.ChevronRight, contentDescription = null, tint = PokerColors.Chalk)
    }
}

/** Room for "10th" in the rank column. */
internal val RankWidth = 44.dp
