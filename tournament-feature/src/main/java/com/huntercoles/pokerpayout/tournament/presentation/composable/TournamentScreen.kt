package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigIntent
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigUiState
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigViewModel
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDialog
import com.huntercoles.pokerpayout.core.design.components.PokerConfirmationDialog
import com.huntercoles.pokerpayout.core.design.components.PokerIconButton
import com.huntercoles.pokerpayout.core.design.components.PokerTopBar
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.design.components.PayoutPreview
import com.huntercoles.pokerpayout.core.design.components.WeightsEditorDialog
import com.huntercoles.pokerpayout.core.domain.model.PayoutSettings
import com.huntercoles.pokerpayout.core.utils.FormatUtils
import com.huntercoles.pokerpayout.core.design.components.invertHorizontally
import com.huntercoles.pokerpayout.tournament.presentation.composable.TimerScreen
import com.huntercoles.pokerpayout.tournament.presentation.TimerIntent
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import com.huntercoles.pokerpayout.tournament.presentation.TimerViewModel
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.core.R as CoreR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentScreen(
    calculatorViewModel: TournamentConfigViewModel = hiltViewModel(),
    // Scope TimerViewModel to Activity to ensure single instance across navigation
    timerViewModel: TimerViewModel = hiltViewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity)
) {
    val calculatorUiState by calculatorViewModel.uiState.collectAsStateWithLifecycle()
    val timerUiState by timerViewModel.uiState.collectAsStateWithLifecycle()

    PlayContent(
        calculatorUiState = calculatorUiState,
        timerUiState = timerUiState,
        onCalculatorIntent = calculatorViewModel::acceptIntent,
        onTimerIntent = timerViewModel::acceptIntent
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayContent(
    calculatorUiState: TournamentConfigUiState,
    timerUiState: TimerUiState,
    onCalculatorIntent: (TournamentConfigIntent) -> Unit,
    onTimerIntent: (TimerIntent) -> Unit
) {
    val focusManager = LocalFocusManager.current
    // Blind setup lives in the clock; mirror it to the config ViewModel's copy (used by Reset).
    val timerIntent: (TimerIntent) -> Unit = { intent ->
        intent.toConfigIntent()?.let(onCalculatorIntent)
        onTimerIntent(intent)
    }

    // Clear focus immediately when this composable is disposed (tab switch)
    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus(force = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // The tab's name is the title, so renaming the tab (D3) renames it here too
        PokerTopBar(title = stringResource(CoreR.string.navigation_tournament)) {
            PokerIconButton(
                icon = PokerIcons.Restart,
                contentDescription = stringResource(R.string.tournament_reset),
                onClick = { onCalculatorIntent(TournamentConfigIntent.ShowResetDialog) },
                tint = PokerColors.PokerGold
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Reset Confirmation Dialog
            PokerConfirmationDialog(
                title = stringResource(R.string.tournament_reset_title),
                description = resetDescription(calculatorUiState),
                onDismiss = { onCalculatorIntent(TournamentConfigIntent.HideResetDialog) },
                onConfirm = {
                    onCalculatorIntent(TournamentConfigIntent.ConfirmReset)
                    onTimerIntent(TimerIntent.ResetTimer)
                },
                isVisible = calculatorUiState.showResetDialog
            )

            PayoutDialogs(uiState = calculatorUiState, onIntent = onCalculatorIntent)

            // Configuration Section (Collapsible)
            TournamentConfigurationCard(
                uiState = calculatorUiState,
                onIntent = onCalculatorIntent,
                blindsPanel = {
                    BlindsConfigPanel(
                        uiState = timerUiState,
                        onIntent = timerIntent,
                        isLocked = calculatorUiState.isTournamentLocked
                    )
                },
                isExpanded = calculatorUiState.isConfigExpanded,
                onExpandedChange = { onCalculatorIntent(TournamentConfigIntent.ToggleConfigExpanded(it)) }
            )

            // Timer Section (from Timer screen)
            TimerScreen(
                uiState = timerUiState,
                onIntent = timerIntent,
                isConfigExpanded = calculatorUiState.isConfigExpanded
            )
        }
    }
}


@Composable
fun PlayerCountSlider(
    playerCount: Int,
    onPlayerCountChange: (Int) -> Unit,
    isLocked: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.tournament_players),
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = PokerColors.CardWhite.copy(alpha = 0.7f)
        )
        
        Slider(
            value = playerCount.toFloat(),
            onValueChange = { if (!isLocked) onPlayerCountChange(it.toInt()) },
            valueRange = 3f..30f,
            steps = 26,
            enabled = !isLocked,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = if (isLocked) PokerColors.CardWhite.copy(alpha = 0.5f) else PokerColors.PokerGold,
                activeTrackColor = if (isLocked) PokerColors.CardWhite.copy(alpha = 0.5f) else PokerColors.AccentGreen,
                inactiveTrackColor = PokerColors.DarkGreen,
                disabledThumbColor = PokerColors.PokerGold,
                disabledActiveTrackColor = PokerColors.PokerGold,
                disabledInactiveTrackColor = PokerColors.DarkGreen
            )
        )
        
        Text(
            text = "$playerCount",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = PokerColors.PokerGold,
            modifier = Modifier.widthIn(min = 24.dp)
        )
    }
}

@Composable
fun TournamentConfigurationCard(
    uiState: TournamentConfigUiState,
    onIntent: (TournamentConfigIntent) -> Unit,
    blindsPanel: @Composable () -> Unit,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PokerColors.SurfacePrimary),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with collapse arrow
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!isExpanded) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.tournament_config_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PokerColors.PokerGold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Lock icon when tournament is locked
                    if (uiState.isTournamentLocked) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = stringResource(R.string.tournament_locked),
                            tint = PokerColors.PokerGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            onExpandedChange(!isExpanded)
                        }
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(
                                if (isExpanded) R.string.tournament_collapse else R.string.tournament_expand
                            ),
                            tint = PokerColors.PokerGold
                        )
                    }
                }
            }

            // Collapsible content
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Player slider, then the Player / Blinds / Payouts panels
                    PoolConfigurationSection(
                        uiState = uiState,
                        onIntent = onIntent,
                        blindsPanel = blindsPanel
                    )
                }
            }
        }
    }
}

/** The reset dialog says what a reset takes with it, including purchases recorded in the Bank. */
@Composable
private fun resetDescription(uiState: TournamentConfigUiState): String {
    val rebuys = uiState.rebuyPurchases
    val addOns = uiState.addOnPurchases
    val purchases = listOfNotNull(
        rebuys.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.tournament_rebuys, it, it) },
        addOns.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.tournament_add_ons, it, it) }
    )
    val base = stringResource(R.string.tournament_reset_description)
    return when (purchases.size) {
        0 -> base
        1 -> stringResource(R.string.tournament_reset_purchases, base, purchases[0])
        else -> stringResource(
            R.string.tournament_reset_purchases,
            base,
            stringResource(R.string.tournament_and, purchases[0], purchases[1])
        )
    }
}

/** The payout editor and the "clear recorded purchases?" question (PP-014). The Payouts tab shows them too. */
@Composable
internal fun PayoutDialogs(uiState: TournamentConfigUiState, onIntent: (TournamentConfigIntent) -> Unit) {
    if (uiState.showWeightsEditor) {
        WeightsEditorDialog(
            current = PayoutSettings(
                weights = uiState.config.payoutWeights,
                preset = uiState.payoutPreset,
                rounding = uiState.config.payoutRounding
            ),
            preview = PayoutPreview(prizePoolCents = uiState.pool.prizePoolCents, playerCount = uiState.playerCount),
            onSave = { onIntent(TournamentConfigIntent.UpdatePayoutSettings(it)) },
            onDismiss = { onIntent(TournamentConfigIntent.HideWeightsEditor) },
            isLocked = uiState.isTournamentLocked
        )
    }

    uiState.purchaseClearPrompt?.let { prompt ->
        val noun = if (prompt.count == 1) prompt.kind.singular else prompt.kind.plural
        PokerConfirmationDialog(
            title = stringResource(R.string.tournament_turn_off_title, prompt.kind.plural),
            description = stringResource(
                R.string.tournament_turn_off_description,
                prompt.kind.singular,
                prompt.count,
                noun,
                FormatUtils.formatCents(prompt.keptAmountCents)
            ),
            onDismiss = { onIntent(TournamentConfigIntent.DismissClearPurchases) },
            onConfirm = { onIntent(TournamentConfigIntent.ConfirmClearPurchases) },
            cancelText = stringResource(R.string.tournament_keep),
            confirmText = stringResource(R.string.tournament_clear, noun)
        )
    }
}
