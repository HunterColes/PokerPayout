package com.huntercoles.fatline.portfoliofeature.presentation.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.delay
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.fatline.portfoliofeature.presentation.BankIntent
import com.huntercoles.fatline.portfoliofeature.presentation.BankUiState
import com.huntercoles.fatline.portfoliofeature.presentation.BankViewModel
import com.huntercoles.fatline.portfoliofeature.presentation.PendingPlayerAction
import com.huntercoles.fatline.portfoliofeature.presentation.PlayerActionType
import com.huntercoles.fatline.portfoliofeature.presentation.PlayerData
import com.huntercoles.fatline.core.design.PokerColors

@Composable
fun BankRoute(viewModel: BankViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BankScreen(
        uiState = uiState,
        onIntent = viewModel::acceptIntent,
    )
}

@Composable
internal fun BankScreen(
    uiState: BankUiState,
    onIntent: (BankIntent) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    
    // Track which players are being knocked out for animation
    var knockingOutPlayerId by remember { mutableStateOf<Int?>(null) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        // Title with Reset Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🏦 Bank Tracker",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = PokerColors.PokerGold,
                modifier = Modifier.weight(1f)
            )
            
            // Green circular background with yellow refresh button
            Card(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = PokerColors.DarkGreen)
            ) {
                IconButton(
                    onClick = { focusManager.clearFocus(); onIntent(BankIntent.ShowResetDialog) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Bank Data",
                        tint = PokerColors.PokerGold,
                        modifier = Modifier.size(24.dp).graphicsLayer(scaleX = -1f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // Reset Confirmation Dialog
        if (uiState.showResetDialog) {
            AlertDialog(
                onDismissRequest = { focusManager.clearFocus(); onIntent(BankIntent.HideResetDialog) },
                containerColor = PokerColors.DarkGreen,
                title = {
                    Text(
                        text = "Reset?",
                        color = PokerColors.PokerGold,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "This will reset all player names and payment statuses to defaults.",
                        color = PokerColors.CardWhite
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { focusManager.clearFocus(); onIntent(BankIntent.HideResetDialog) }
                    ) {
                        Text(
                            text = "No",
                            color = PokerColors.CardWhite
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { focusManager.clearFocus(); onIntent(BankIntent.ConfirmReset) }
                    ) {
                        Text(
                            text = "Yes",
                            color = PokerColors.PokerGold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }

        val pendingAction = uiState.pendingAction
        val sortedPlayers = uiState.players.sortedWith(compareBy<PlayerData> { it.out }.thenBy { it.id })

        pendingAction?.let { action ->
            uiState.players.firstOrNull { it.id == action.playerId }?.let { player ->
                PlayerActionDialog(
                    player = player,
                    pendingAction = action,
                    onConfirm = {
                        // If knocking out a player, trigger animation
                        if (action.actionType == PlayerActionType.OUT && action.apply) {
                            knockingOutPlayerId = player.id
                        }
                        onIntent(BankIntent.ConfirmPlayerAction)
                    },
                    onCancel = { onIntent(BankIntent.CancelPlayerAction) }
                )
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Pool Summary
            item {
                PoolSummaryCard(uiState = uiState)
            }

            // Player rows
            items(sortedPlayers, key = { it.id }) { player ->
                PlayerRow(
                    player = player,
                    isKnockingOut = knockingOutPlayerId == player.id,
                    onNameChange = { onIntent(BankIntent.PlayerNameChanged(player.id, it)) },
                    onActionRequested = { actionType ->
                        onIntent(BankIntent.ShowPlayerActionDialog(player.id, actionType))
                    },
                    onAnimationComplete = {
                        if (knockingOutPlayerId == player.id) {
                            knockingOutPlayerId = null
                        }
                    },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

@Composable
private fun PoolSummaryCard(uiState: BankUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = PokerColors.SurfacePrimary)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "💰 Pool Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PokerColors.PokerGold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total Pool:",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PokerColors.CardWhite
                )
                Text(
                    text = "${'$'}${String.format("%.2f", uiState.totalPool)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = PokerColors.CardWhite
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            SummaryProgressBar(
                label = "Total Payed In:",
                currentAmount = uiState.totalPaidIn,
                targetAmount = uiState.totalPool,
                baseColor = PokerColors.AccentGreen
            )

            Spacer(modifier = Modifier.height(16.dp))

            SummaryProgressBar(
                label = "Total Payed Out:",
                currentAmount = uiState.totalPayedOut,
                targetAmount = uiState.prizePool,
                baseColor = PokerColors.SuccessGreen
            )
        }
    }
}

@Composable
private fun SummaryProgressBar(
    label: String,
    currentAmount: Double,
    targetAmount: Double,
    baseColor: Color,
    modifier: Modifier = Modifier,
    fullColor: Color = PokerColors.PokerGold
) {
    val safeTarget = targetAmount.coerceAtLeast(0.0)
    val progress = if (safeTarget > 0.0) (currentAmount / safeTarget).coerceIn(0.0, 1.0) else 0.0
    val isComplete = safeTarget > 0.0 && currentAmount >= safeTarget
    val fillColor = if (isComplete) fullColor else baseColor

    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(PokerColors.SurfaceSecondary.copy(alpha = 0.35f))
            )

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.toFloat())
                    .clip(RoundedCornerShape(18.dp))
                    .background(fillColor)
            )

            val textColor = if (isComplete) Color.Black else PokerColors.CardWhite

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = textColor
                )
                Text(
                    text = "${'$'}${String.format("%.2f", currentAmount)}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun PlayerRow(
    player: PlayerData,
    isKnockingOut: Boolean,
    onNameChange: (String) -> Unit,
    onActionRequested: (PlayerActionType) -> Unit,
    onAnimationComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val commitAndClear: (String) -> Unit = { text ->
        onNameChange(text)
        focusManager.clearFocus()
    }
    
    // Animation state for the red flash effect
    var showRedFlash by remember(player.id) { mutableStateOf(false) }
    
    // Trigger red flash animation when player is being knocked out
    LaunchedEffect(isKnockingOut) {
        if (isKnockingOut && player.out) {
            showRedFlash = true
            delay(600) // Flash duration
            showRedFlash = false
            onAnimationComplete()
        }
    }
    
    // Animate the background color
    val backgroundColor by animateColorAsState(
        targetValue = when {
            showRedFlash -> PokerColors.ErrorRed
            player.out -> PokerColors.ErrorRed.copy(alpha = 0.55f)
            else -> PokerColors.SurfaceSecondary
        },
        animationSpec = tween(durationMillis = 300),
        label = "background_color_animation"
    )
    
    // Keep a local editable text state to handle IME Done commits and to avoid
    // losing typed input when recomposition happens. Also sync with external
    // updates (like reset) by observing player.name.
    var nameTextFieldValue by remember(player.id, player.name) {
        mutableStateOf(TextFieldValue(text = player.name))
    }
    
    // Track interaction source for focus detection
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    // Check if the current name is default (e.g., "Player 1", "Player 2")
    val isDefaultName = player.name.matches(Regex("^Player \\d+$"))
    
    // Auto-select all text when focused on default name
    LaunchedEffect(isFocused, isDefaultName) {
        if (isFocused && isDefaultName && nameTextFieldValue.selection.collapsed) {
            nameTextFieldValue = nameTextFieldValue.copy(
                selection = TextRange(0, nameTextFieldValue.text.length)
            )
        }
    }
    
    // If the player.name changes externally (reset), update local text state once
    LaunchedEffect(player.name) {
        if (nameTextFieldValue.text != player.name) {
            nameTextFieldValue = TextFieldValue(text = player.name)
        }
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = nameTextFieldValue,
                onValueChange = { new -> nameTextFieldValue = new },
                interactionSource = interactionSource,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
                    .onPreviewKeyEvent { keyEvent ->
                        val native = keyEvent.nativeKeyEvent ?: return@onPreviewKeyEvent false
                        if (native.keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                            when (native.action) {
                                android.view.KeyEvent.ACTION_DOWN -> true
                                android.view.KeyEvent.ACTION_UP -> { commitAndClear(nameTextFieldValue.text); true }
                                else -> false
                            }
                        } else false
                    },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PokerColors.AccentGreen,
                    unfocusedBorderColor = PokerColors.CardWhite,
                    focusedTextColor = PokerColors.CardWhite,
                    unfocusedTextColor = PokerColors.CardWhite,
                    cursorColor = PokerColors.PokerGold,
                    selectionColors = TextSelectionColors(
                        handleColor = PokerColors.PokerGold,
                        backgroundColor = PokerColors.PokerGold.copy(alpha = 0.4f)
                    )
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commitAndClear(nameTextFieldValue.text) })
            )

            // Right-justified group: out chip + two vertical columns (rebuy/addon) and (buy-in/payout)
            Row(
                modifier = Modifier.wrapContentWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Out chip (single)
                PlayerStatusChip(
                    emoji = if (player.out) "❌" else "⚪",
                    isActive = player.out,
                    activeColor = PokerColors.ErrorRed,
                    contentDescription = if (player.out) "Knocked out" else "Still in",
                    onClick = { onActionRequested(PlayerActionType.OUT) },
                    modifier = Modifier.padding(end = 8.dp)
                )

                // Rebuy / Add-on column (vertical)
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    PlayerStatusChip(
                        emoji = "♻️",
                        isActive = player.rebuys > 0,
                        activeColor = PokerColors.AccentGreen,
                        contentDescription = if (player.rebuys > 0) "Rebuy active" else "Rebuy available",
                        onClick = { onActionRequested(PlayerActionType.REBUY) }
                    )

                    PlayerStatusChip(
                        emoji = "➕",
                        isActive = player.addons > 0,
                        activeColor = PokerColors.AccentGreen,
                        contentDescription = if (player.addons > 0) "Add-on active" else "Add-on available",
                        onClick = { onActionRequested(PlayerActionType.ADDON) }
                    )
                }

                // Buy-in / Payout column (vertical)
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PlayerStatusChip(
                        emoji = "💰",
                        isActive = player.buyIn,
                        activeColor = PokerColors.PokerGold,
                        contentDescription = if (player.buyIn) "Buy-in completed" else "Buy-in pending",
                        onClick = { onActionRequested(PlayerActionType.BUY_IN) }
                    )

                    PlayerStatusChip(
                        emoji = "⭐",
                        isActive = player.payedOut,
                        activeColor = PokerColors.PokerGold,
                        contentDescription = if (player.payedOut) "Payout complete" else "Payout pending",
                        onClick = { onActionRequested(PlayerActionType.PAYED_OUT) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerStatusChip(
    emoji: String,
    isActive: Boolean,
    activeColor: Color,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        tonalElevation = if (isActive) 4.dp else 0.dp,
        shadowElevation = if (isActive) 2.dp else 0.dp,
        color = if (isActive) PokerColors.DarkGreen else PokerColors.SurfaceSecondary,
        border = BorderStroke(1.dp, if (isActive) activeColor else PokerColors.CardWhite)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = emoji,
                style = MaterialTheme.typography.titleMedium,
                color = if (isActive) activeColor else PokerColors.CardWhite.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun PlayerActionDialog(
    player: PlayerData,
    pendingAction: PendingPlayerAction,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val (emoji, title) = dialogTitle(pendingAction.actionType)
    val message = dialogMessage(player, pendingAction)

    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = PokerColors.DarkGreen,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, PokerColors.PokerGold)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "$emoji $title",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = PokerColors.PokerGold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = PokerColors.FeltGreen,
                    border = BorderStroke(1.dp, PokerColors.PokerGold.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PokerColors.CardWhite,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onConfirm) {
                        Text(
                            text = "Okay",
                            color = PokerColors.PokerGold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TextButton(onClick = onCancel) {
                        Text(
                            text = "Cancel",
                            color = PokerColors.CardWhite
                        )
                    }
                }
            }
        }
    }
}

private fun dialogMessage(
    player: PlayerData,
    pendingAction: PendingPlayerAction
): String {
    val name = player.name.ifBlank { "Player ${player.id}" }
    return when (pendingAction.actionType) {
        PlayerActionType.OUT -> if (pendingAction.apply) {
            "$name has been knocked out."
        } else {
            "$name is back in the game."
        }

        PlayerActionType.REBUY -> if (pendingAction.apply) {
            "$name has purchased a rebuy."
        } else {
            "Rebuy removed for $name."
        }

        PlayerActionType.ADDON -> if (pendingAction.apply) {
            "$name has purchased an add-on."
        } else {
            "Add-on removed for $name."
        }

        PlayerActionType.BUY_IN -> if (pendingAction.apply) {
            "$name has paid the buy-in."
        } else {
            "$name's buy-in has been cleared."
        }

        PlayerActionType.PAYED_OUT -> if (pendingAction.apply) {
            "$name has been paid out."
        } else {
            "$name's payout has been undone."
        }
    }
}

private fun dialogTitle(actionType: PlayerActionType): Pair<String, String> = when (actionType) {
    PlayerActionType.OUT -> "❌" to "Knocked Out"
    PlayerActionType.BUY_IN -> "💰" to "Buy-In"
    PlayerActionType.PAYED_OUT -> "⭐" to "Payout"
    PlayerActionType.REBUY -> "♻️" to "Rebuy"
    PlayerActionType.ADDON -> "➕" to "Add-on"
}