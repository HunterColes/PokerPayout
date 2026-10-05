package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDialog
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockSegment
import com.huntercoles.pokerpayout.tournament.presentation.ClockFormat
import com.huntercoles.pokerpayout.tournament.presentation.TimerIntent
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState

private const val NO_SETUP_TEXT = "The blind setup can't be played. Check Tournament Configuration → Blinds."

/**
 * The Tournament tab's clock (PP-025): the current level's countdown and blinds first, then what's
 * next, then the tournament as a whole; the schedule below it.
 */
@Composable
fun TimerScreen(
    uiState: TimerUiState,
    onIntent: (TimerIntent) -> Unit,
    isConfigExpanded: Boolean = false
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ClockCard(uiState = uiState, onIntent = onIntent)
        ScheduleCard(uiState = uiState, isConfigExpanded = isConfigExpanded)
    }

    if (uiState.showInvalidConfigDialog) {
        InvalidConfigDialog(uiState = uiState, onIntent = onIntent)
    }
    if (uiState.isTableView) {
        TableViewDialog(uiState = uiState, onIntent = onIntent)
    }
}

@Composable
private fun ClockCard(uiState: TimerUiState, onIntent: (TimerIntent) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = PokerColors.FeltGreen)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ClockHeader(uiState, onIntent)
            val segment = uiState.currentSegment
            if (segment == null) {
                SetupProblemInClock(uiState, onIntent)
            } else {
                ClockBody(uiState, segment, onIntent)
            }
        }
    }
}

@Composable
private fun ClockHeader(uiState: TimerUiState, onIntent: (TimerIntent) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val overtime = uiState.currentLevelSegment?.isOvertime == true && !uiState.isOnBreak
        Text(
            text = headline(uiState),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = if (overtime) PokerColors.ErrorRed else PokerColors.PokerGold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        statusText(uiState)?.let { StatusPill(it) }
        if (!uiState.timeline.isEmpty) {
            IconButton(onClick = { onIntent(TimerIntent.SetTableView(true)) }) {
                Icon(Icons.Default.Fullscreen, contentDescription = "Table view", tint = PokerColors.PokerGold)
            }
        }
    }
}

/** Level time left (the hero), the blinds, what's next, the controls, then the whole tournament. */
@Composable
private fun ClockBody(uiState: TimerUiState, segment: ClockSegment, onIntent: (TimerIntent) -> Unit) {
    val formatter = rememberChipFormatter()
    Text(
        text = if (uiState.isOnBreak) "BREAK TIME LEFT" else "LEVEL TIME LEFT",
        style = Caption,
        color = Dim
    )
    Text(
        text = ClockFormat.clock(uiState.segmentRemainingSeconds),
        fontSize = 84.sp,
        lineHeight = 88.sp,
        fontWeight = FontWeight.Bold,
        color = heroColor(uiState),
        modifier = Modifier.clickable(enabled = !uiState.isFinished) { onIntent(TimerIntent.ToggleTimer) }
    )
    ToneProgress(uiState)
    Spacer(Modifier.height(10.dp))

    CurrentBlinds(segment, formatter, bigSize = 40.sp)
    Spacer(Modifier.height(6.dp))
    NextLevelLine(uiState, formatter, fontSize = 18.sp)

    Spacer(Modifier.height(8.dp))
    ClockControls(uiState, onIntent, playSize = 64.dp)

    HorizontalDivider(color = PokerColors.CardWhite.copy(alpha = 0.12f), modifier = Modifier.padding(vertical = 8.dp))
    TournamentLine(uiState)
    Spacer(Modifier.height(4.dp))
    TableStatsLine(uiState, formatter)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupProblemInClock(uiState: TimerUiState, onIntent: (TimerIntent) -> Unit) {
    val problem = uiState.setupProblem
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = problem?.explanation ?: NO_SETUP_TEXT, color = PokerColors.CardWhite, textAlign = TextAlign.Center)
        if (problem != null && problem.fixes.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                problem.fixes.forEach { fix -> FixButton(fix) { onIntent(TimerIntent.ApplyFix(it)) } }
            }
        }
        ClockControls(uiState, onIntent, playSize = 56.dp)
    }
}

/** PP-020: why the blinds can't be played, and the nearest setups that can, one tap each. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InvalidConfigDialog(uiState: TimerUiState, onIntent: (TimerIntent) -> Unit) {
    val problem = uiState.setupProblem
    PokerDialog(onDismissRequest = { onIntent(TimerIntent.HideInvalidConfigDialog) }) {
        Text(
            text = "Can't start: blinds don't fit",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = PokerColors.PokerGold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = PokerColors.FeltGreen,
            border = BorderStroke(1.dp, PokerColors.PokerGold.copy(alpha = 0.6f))
        ) {
            Text(
                text = problem?.explanation ?: NO_SETUP_TEXT,
                style = MaterialTheme.typography.bodyMedium,
                color = PokerColors.CardWhite,
                modifier = Modifier.padding(16.dp)
            )
        }
        if (problem != null && problem.fixes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Nearest setups that work:", color = Dim, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                problem.fixes.forEach { fix -> FixButton(fix) { onIntent(TimerIntent.ApplyFix(it)) } }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onIntent(TimerIntent.HideInvalidConfigDialog) }) {
                Text(text = "OK", color = PokerColors.PokerGold, fontWeight = FontWeight.Bold)
            }
        }
    }
}
