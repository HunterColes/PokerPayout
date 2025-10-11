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
import androidx.compose.foundation.layout.width
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.with
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.delay
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.fatline.core.design.PokerDialog
import com.huntercoles.fatline.portfoliofeature.presentation.BankIntent
import com.huntercoles.fatline.portfoliofeature.presentation.MAX_PURCHASE_COUNT
import com.huntercoles.fatline.portfoliofeature.presentation.BankUiState
import com.huntercoles.fatline.portfoliofeature.presentation.BankViewModel
import com.huntercoles.fatline.portfoliofeature.presentation.PendingPlayerAction
import com.huntercoles.fatline.portfoliofeature.presentation.PlayerActionType
import com.huntercoles.fatline.portfoliofeature.presentation.PlayerData
import com.huntercoles.fatline.portfoliofeature.presentation.buildPlayerDisplayModels
import com.huntercoles.fatline.portfoliofeature.R
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
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Edit weights button
                Card(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = PokerColors.DarkGreen)
                ) {
                    IconButton(
                        onClick = { focusManager.clearFocus(); onIntent(BankIntent.ShowWeightsDialog) },
                        enabled = !uiState.isTimerRunning,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_balance_scale),
                            contentDescription = "Edit payout weights",
                            tint = if (uiState.isTimerRunning) PokerColors.CardWhite.copy(alpha = 0.5f) else PokerColors.PokerGold,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
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
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // Reset Confirmation Dialog
        if (uiState.showResetDialog) {
            PokerDialog(
                onDismissRequest = {
                    focusManager.clearFocus()
                    onIntent(BankIntent.HideResetDialog)
                }
            ) {
                Text(
                    text = "Reset bank data?",
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
                        text = "This will reset all player names and payment statuses to defaults.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PokerColors.CardWhite,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = {
                            focusManager.clearFocus()
                            onIntent(BankIntent.HideResetDialog)
                        }
                    ) {
                        Text(
                            text = "Cancel",
                            color = PokerColors.CardWhite
                        )
                    }

                    TextButton(
                        onClick = {
                            focusManager.clearFocus()
                            onIntent(BankIntent.ConfirmReset)
                        }
                    ) {
                        Text(
                            text = "Reset",
                            color = PokerColors.PokerGold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Weights Editor Dialog
        if (uiState.showWeightsDialog) {
            WeightsEditorDialog(
                currentWeights = uiState.payoutWeights,
                onWeightsChanged = { newWeights ->
                    onIntent(BankIntent.UpdateWeights(newWeights))
                },
                onDismiss = { onIntent(BankIntent.HideWeightsDialog) }
            )
        }

        val pendingAction = uiState.pendingAction
        val playerDisplayModels = remember(uiState.players, uiState.eliminationOrder) {
            buildPlayerDisplayModels(uiState.players, uiState.eliminationOrder)
        }

        pendingAction?.let { action ->
            uiState.players.firstOrNull { it.id == action.playerId }?.let { player ->
                PlayerActionDialog(
                    player = player,
                    pendingAction = action,
                    allPlayers = uiState.players,
                    uiState = uiState,
                    onConfirm = { selectedCount, selectedPlayerId ->
                        when (action.actionType) {
                            PlayerActionType.OUT -> {
                                if (action.apply) {
                                    knockingOutPlayerId = player.id
                                    onIntent(
                                        BankIntent.ConfirmPlayerActionWithCount(
                                            selectedPlayerId = selectedPlayerId
                                        )
                                    )
                                } else {
                                    onIntent(BankIntent.ConfirmPlayerAction)
                                }
                            }
                            PlayerActionType.REBUY, PlayerActionType.ADDON -> {
                                val countToApply = selectedCount ?: action.baseCount
                                onIntent(
                                    BankIntent.ConfirmPlayerActionWithCount(
                                        count = countToApply
                                    )
                                )
                            }
                            else -> onIntent(BankIntent.ConfirmPlayerAction)
                        }
                    },
                    onCancel = { onIntent(BankIntent.CancelPlayerAction) }
                )
            }
        }

        val activePlayerIds = uiState.players.filter { !it.out }.map { it.id }
        val championPlayerId = activePlayerIds.singleOrNull()

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Pool Summary
            item {
                PoolSummaryCard(uiState = uiState)
            }

            // Player rows
            items(playerDisplayModels, key = { it.player.id }) { model ->
                val player = model.player
                val knockoutCount = uiState.knockoutCounts[player.id] ?: 0
                PlayerRow(
                        player = player,
                        placementNumber = model.placement ?: if (championPlayerId == player.id) 1 else null,
                        isKnockingOut = knockingOutPlayerId == player.id,
                        isChampionHighlight = championPlayerId == player.id,
                        outEnabled = championPlayerId == null || championPlayerId != player.id,
                        rebuyEnabled = uiState.rebuyAmount > 0.0,
                        addonEnabled = uiState.addonAmount > 0.0,
                        knockoutCount = knockoutCount,
                        showKnockoutIndicator = knockoutCount > 0,
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
                targetAmount = uiState.prizePool + uiState.bountyPool,
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
    val isComplete = safeTarget > 0.0 && (currentAmount + 0.01) >= safeTarget
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
    placementNumber: Int?,
    isKnockingOut: Boolean,
    isChampionHighlight: Boolean,
    outEnabled: Boolean,
    rebuyEnabled: Boolean,
    addonEnabled: Boolean,
    knockoutCount: Int,
    showKnockoutIndicator: Boolean,
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
            isChampionHighlight -> PokerColors.PokerGold
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                placementNumber?.let { placement ->
                    val orbitronFont = FontFamily(Font(R.font.orbitron_variablefont_wght))
                    if (isChampionHighlight) {
                        Text(
                            text = "👑",
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 55.sp),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .graphicsLayer { rotationZ = -4f },
                            color = Color.Unspecified,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = placement.toString(),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontFamily = orbitronFont,
                            fontWeight = FontWeight.Black,
                            fontSize = 48.sp,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.35f),
                                offset = Offset(2f, 4f),
                                blurRadius = 12f
                            )
                        ),
                        color = if (isChampionHighlight) Color.Black.copy(alpha = 0.78f) else PokerColors.PokerGold.copy(alpha = 0.22f),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                            .graphicsLayer { rotationZ = if (isChampionHighlight) -3f else -8f }
                    )
                }

                OutlinedTextField(
                    value = nameTextFieldValue,
                    onValueChange = { new -> nameTextFieldValue = new },
                    interactionSource = interactionSource,
                    modifier = Modifier
                        .fillMaxWidth()
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
                    colors = if (isChampionHighlight) {
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Black,
                            unfocusedBorderColor = Color.Black,
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            cursorColor = Color.Black,
                            selectionColors = TextSelectionColors(
                                handleColor = Color.Black,
                                backgroundColor = Color.Black.copy(alpha = 0.35f)
                            )
                        )
                    } else {
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PokerColors.AccentGreen,
                            unfocusedBorderColor = PokerColors.CardWhite,
                            focusedTextColor = PokerColors.CardWhite,
                            unfocusedTextColor = PokerColors.CardWhite,
                            cursorColor = PokerColors.PokerGold,
                            selectionColors = TextSelectionColors(
                                handleColor = PokerColors.PokerGold,
                                backgroundColor = PokerColors.PokerGold.copy(alpha = 0.4f)
                            )
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitAndClear(nameTextFieldValue.text) })
                )
            }

            // Right-justified group: out chip + two vertical columns (rebuy/addon) and (buy-in/payout)
            Row(
                modifier = Modifier.wrapContentWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerStatusChip(
                    emoji = if (player.out) "❌" else "⚪",
                    isActive = player.out,
                    activeColor = PokerColors.ErrorRed,
                    contentDescription = if (player.out) "Knocked out" else "Still in",
                    onClick = { onActionRequested(PlayerActionType.OUT) },
                    enabled = outEnabled,
                    borderOverride = if (isChampionHighlight) Color.Black else null
                )

                Box(
                    modifier = Modifier.width(38.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    PlayerStatusChip(
                        emoji = "♻️",
                        isActive = player.rebuys > 0,
                        activeColor = PokerColors.AccentGreen,
                        contentDescription = if (player.rebuys > 0) "Rebuy active" else "Rebuy available",
                        onClick = { onActionRequested(PlayerActionType.REBUY) },
                        enabled = rebuyEnabled,
                        borderOverride = if (isChampionHighlight) Color.Black else null
                    )

                    if (player.rebuys > 0) {
                        CountBadge(
                            count = player.rebuys,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-18).dp),
                            emoji = ""
                        )
                    }
                }

                Box(
                    modifier = Modifier.width(38.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    PlayerStatusChip(
                        emoji = "➕",
                        isActive = player.addons > 0,
                        activeColor = PokerColors.AccentGreen,
                        contentDescription = if (player.addons > 0) "Add-on active" else "Add-on available",
                        onClick = { onActionRequested(PlayerActionType.ADDON) },
                        enabled = addonEnabled,
                        borderOverride = if (isChampionHighlight) Color.Black else null
                    )

                    if (player.addons > 0) {
                        CountBadge(
                            count = player.addons,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-18).dp),
                            emoji = ""
                        )
                    }
                }

                PlayerStatusChip(
                    emoji = "💵",
                    isActive = player.buyIn,
                    activeColor = PokerColors.PokerGold,
                    contentDescription = if (player.buyIn) "Buy-in completed" else "Buy-in pending",
                    onClick = { onActionRequested(PlayerActionType.BUY_IN) },
                    borderOverride = if (isChampionHighlight) Color.Black else null
                )

                Box(
                    modifier = Modifier.width(38.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    PlayerStatusChip(
                        emoji = "💸",
                        isActive = player.payedOut,
                        activeColor = PokerColors.PokerGold,
                        contentDescription = if (player.payedOut) "Payout complete" else "Payout pending",
                        onClick = { onActionRequested(PlayerActionType.PAYED_OUT) },
                        borderOverride = if (isChampionHighlight) Color.Black else null
                    )

                    if (showKnockoutIndicator && knockoutCount > 0) {
                        CountBadge(
                            count = knockoutCount,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-18).dp)
                        )
                    }
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
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    borderOverride: Color? = null,
    badgeCount: Int = 0
) {
    val baseBackground = when {
        isActive -> PokerColors.DarkGreen
        !enabled -> PokerColors.SurfaceSecondary.copy(alpha = 0.4f)
        else -> PokerColors.SurfaceSecondary
    }
    val borderColor = borderOverride ?: when {
        isActive -> activeColor
        !enabled -> PokerColors.CardWhite.copy(alpha = 0.4f)
        else -> PokerColors.CardWhite
    }
    val emojiColor = when {
        isActive -> activeColor
        !enabled -> PokerColors.CardWhite.copy(alpha = 0.5f)
        else -> PokerColors.CardWhite.copy(alpha = 0.8f)
    }

    Surface(
        modifier = modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                enabled = enabled,
                onClick = onClick
            )
            .semantics {
                val badgeSuffix = if (badgeCount > 0) " ($badgeCount)" else ""
                this.contentDescription = contentDescription + badgeSuffix
                if (!enabled) this.disabled()
            },
        tonalElevation = if (isActive) 4.dp else 0.dp,
        shadowElevation = if (isActive) 2.dp else 0.dp,
        color = baseBackground,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = emoji,
                style = MaterialTheme.typography.titleMedium,
                color = emojiColor
            )

            if (badgeCount > 0) {
                Text(
                    text = "x$badgeCount",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = PokerColors.CardWhite,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 2.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun PlayerActionDialog(
    player: PlayerData,
    pendingAction: PendingPlayerAction,
    allPlayers: List<PlayerData>,
    uiState: BankUiState,
    onConfirm: (Int?, Int?) -> Unit,
    onCancel: () -> Unit
) {
    val (emoji, title) = dialogTitle(pendingAction.actionType)
    val message = dialogMessage(player, pendingAction)
    val isCountAction = pendingAction.actionType == PlayerActionType.REBUY || pendingAction.actionType == PlayerActionType.ADDON
    val isKnockoutSelection = pendingAction.actionType == PlayerActionType.OUT && pendingAction.apply
    val isPayoutAction = pendingAction.actionType == PlayerActionType.PAYED_OUT && pendingAction.apply
    val isBuyInAction = pendingAction.actionType == PlayerActionType.BUY_IN && pendingAction.apply
    val baseCount = pendingAction.baseCount.coerceAtLeast(0)
    var purchaseCount by remember(pendingAction.playerId, pendingAction.actionType, baseCount) {
        mutableStateOf(baseCount)
    }

    val clampedTarget = pendingAction.targetCount.coerceIn(0, MAX_PURCHASE_COUNT)

    var countAnimationDirection by remember(pendingAction.playerId) { mutableStateOf(1) }
    var currentCountIndex by remember(pendingAction.playerId, baseCount) { mutableStateOf(baseCount) }

    LaunchedEffect(pendingAction.playerId, pendingAction.actionType, baseCount, clampedTarget) {
        purchaseCount = baseCount
        currentCountIndex = baseCount
        if (isCountAction && clampedTarget != baseCount) {
            purchaseCount = clampedTarget
            currentCountIndex = clampedTarget
        }
    }

    LaunchedEffect(currentCountIndex) {
        purchaseCount = currentCountIndex
    }

    val knockoutOptions = remember(
        pendingAction.playerId,
        pendingAction.selectablePlayerIds,
        allPlayers,
        pendingAction.allowUnassignedSelection
    ) {
        val idToPlayer = allPlayers.associateBy { it.id }
        val playerEntries = pendingAction.selectablePlayerIds.mapNotNull { candidateId ->
            idToPlayer[candidateId]?.let { candidate ->
                KnockoutOption(
                    id = candidate.id,
                    label = candidate.name.ifBlank { "Player ${candidate.id}" }
                )
            }
        }

        val withFallback = when {
            pendingAction.allowUnassignedSelection && playerEntries.isNotEmpty() ->
                playerEntries + KnockoutOption(null, "Nobody")
            pendingAction.allowUnassignedSelection ->
                listOf(KnockoutOption(null, "Nobody"))
            else -> playerEntries
        }

        withFallback
    }

    var animationDirection by remember(pendingAction.playerId) { mutableStateOf(1) }
    var currentOptionIndex by remember(pendingAction.playerId) { mutableStateOf(0) }

    LaunchedEffect(knockoutOptions, pendingAction.selectedPlayerId) {
        if (isKnockoutSelection) {
            val selectedIndex = knockoutOptions.indexOfFirst { it.id == pendingAction.selectedPlayerId }
            currentOptionIndex = when {
                selectedIndex >= 0 -> selectedIndex
                knockoutOptions.isNotEmpty() ->
                    knockoutOptions.indexOfFirst { it.id != null }.takeIf { it >= 0 } ?: 0
                else -> 0
            }
            animationDirection = 1
        }
    }

    val canCycleOptions = knockoutOptions.size > 1
    val selectedKnockoutId = if (isKnockoutSelection) knockoutOptions.getOrNull(currentOptionIndex)?.id else null

    PokerDialog(onDismissRequest = onCancel) {
        Text(
            text = "$emoji $title",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = PokerColors.PokerGold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))

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

        if (isKnockoutSelection) {
            Spacer(modifier = Modifier.height(18.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = PokerColors.LightGreen.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, PokerColors.PokerGold.copy(alpha = 0.55f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (knockoutOptions.isNotEmpty()) {
                                animationDirection = -1
                                val size = knockoutOptions.size
                                currentOptionIndex = (currentOptionIndex - 1 + size) % size
                            }
                        },
                        enabled = canCycleOptions
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous player",
                            tint = PokerColors.CardWhite
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = currentOptionIndex,
                            transitionSpec = {
                                if (animationDirection >= 0) {
                                    (slideInHorizontally { fullWidth -> fullWidth } + fadeIn()) togetherWith
                                        (slideOutHorizontally { fullWidth -> -fullWidth } + fadeOut())
                                } else {
                                    (slideInHorizontally { fullWidth -> -fullWidth } + fadeIn()) togetherWith
                                        (slideOutHorizontally { fullWidth -> fullWidth } + fadeOut())
                                }.using(SizeTransform(clip = false))
                            },
                            label = "knockout_selector"
                        ) { targetIndex ->
                            val optionLabel = knockoutOptions.getOrNull(targetIndex)?.label ?: "No eligible players"
                            Text(
                                text = optionLabel,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = PokerColors.PokerGold,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (knockoutOptions.isNotEmpty()) {
                                animationDirection = 1
                                val size = knockoutOptions.size
                                currentOptionIndex = (currentOptionIndex + 1) % size
                            }
                        },
                        enabled = canCycleOptions
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next player",
                            tint = PokerColors.CardWhite
                        )
                    }
                }
            }
        }

        if (isCountAction) {
            Spacer(modifier = Modifier.height(18.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = PokerColors.LightGreen.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, PokerColors.PokerGold.copy(alpha = 0.55f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            countAnimationDirection = -1
                            currentCountIndex = (currentCountIndex - 1).coerceAtLeast(0)
                        },
                        enabled = currentCountIndex > 0
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Decrease count",
                            tint = PokerColors.CardWhite
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = currentCountIndex,
                            transitionSpec = {
                                if (countAnimationDirection >= 0) {
                                    (slideInHorizontally { fullWidth -> fullWidth } + fadeIn()) togetherWith
                                        (slideOutHorizontally { fullWidth -> -fullWidth } + fadeOut())
                                } else {
                                    (slideInHorizontally { fullWidth -> -fullWidth } + fadeIn()) togetherWith
                                        (slideOutHorizontally { fullWidth -> fullWidth } + fadeOut())
                                }.using(SizeTransform(clip = false))
                            },
                            label = "count_selector"
                        ) { targetCount ->
                            Text(
                                text = targetCount.toString(),
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = PokerColors.PokerGold,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.semantics { contentDescription = "Purchase count $targetCount" }
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            countAnimationDirection = 1
                            currentCountIndex = (currentCountIndex + 1).coerceAtMost(MAX_PURCHASE_COUNT)
                        },
                        enabled = currentCountIndex < MAX_PURCHASE_COUNT
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Increase count",
                            tint = PokerColors.CardWhite
                        )
                    }
                }
            }
        }

        if (isBuyInAction) {
            Spacer(modifier = Modifier.height(18.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = PokerColors.LightGreen.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, PokerColors.PokerGold.copy(alpha = 0.55f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$${String.format("%.2f", pendingAction.buyInCost)}",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = PokerColors.PokerGold
                    )
                }
            }
        }

        if (isPayoutAction) {
            Spacer(modifier = Modifier.height(18.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = PokerColors.LightGreen.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, PokerColors.PokerGold.copy(alpha = 0.55f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Payout Breakdown",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = PokerColors.PokerGold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Buy-in cost (negative, red)
                    if (pendingAction.buyInCost > 0.0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Buy-In",
                                style = MaterialTheme.typography.bodyLarge,
                                color = PokerColors.ErrorRed
                            )
                            Text(
                                text = "-$${String.format("%.2f", pendingAction.buyInCost)}",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = PokerColors.ErrorRed
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Payout (leaderboard payout)
                    if (pendingAction.buyInPayout > 0.0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Pay-Out",
                                style = MaterialTheme.typography.bodyLarge,
                                color = PokerColors.CardWhite
                            )
                            Text(
                                text = "$${String.format("%.2f", pendingAction.buyInPayout)}",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = PokerColors.PokerGold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Knockout bonus
                    val knockoutCount = pendingAction.knockoutCount
                    if (knockoutCount > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${knockoutCount}x 💀",
                                style = MaterialTheme.typography.bodyLarge,
                                color = PokerColors.CardWhite
                            )
                            Text(
                                text = "$${String.format("%.2f", pendingAction.knockoutBonus)}",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = PokerColors.PokerGold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // King's bounty
                    if (pendingAction.kingsBounty > 0.0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "👑 King's Bounty",
                                style = MaterialTheme.typography.bodyLarge,
                                color = PokerColors.CardWhite
                            )
                            Text(
                                text = "$${String.format("%.2f", pendingAction.kingsBounty)}",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = PokerColors.PokerGold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    HorizontalDivider(color = PokerColors.PokerGold.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Net Pay (was Total)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Net Pay:",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = PokerColors.CardWhite
                        )
                        val netPayColor = if (pendingAction.payoutAmount >= 0) PokerColors.PokerGold else PokerColors.ErrorRed
                        val netPayText = if (pendingAction.payoutAmount >= 0) 
                            "$${String.format("%.2f", pendingAction.payoutAmount)}" 
                        else 
                            "-$${String.format("%.2f", -pendingAction.payoutAmount)}"
                        Text(
                            text = netPayText,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = netPayColor
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = {
                onConfirm(
                    if (isCountAction) purchaseCount else null,
                    if (isKnockoutSelection) selectedKnockoutId else null
                )
            }) {
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

private fun dialogMessage(
    player: PlayerData,
    pendingAction: PendingPlayerAction
): String {
    val name = player.name.ifBlank { "Player ${player.id}" }
    return when (pendingAction.actionType) {
        PlayerActionType.OUT -> if (pendingAction.apply) {
            "$name has been knocked out by:"
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
    PlayerActionType.OUT -> "❌" to "Knocked-Out"
    PlayerActionType.BUY_IN -> "💵" to "Buy-In"
    PlayerActionType.PAYED_OUT -> "💸" to "Pay-Out"
    PlayerActionType.REBUY -> "♻️" to "Rebuy"
    PlayerActionType.ADDON -> "➕" to "Add-on"
}

@Composable
private fun SummaryInfoRow(
    label: String,
    amount: Double,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = PokerColors.CardWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${'$'}${String.format("%.2f", amount)}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = PokerColors.CardWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CountBadge(
    count: Int,
    modifier: Modifier = Modifier,
    emoji: String = "💀"
) {
    val label = if (emoji.isNotEmpty()) "${count}x$emoji" else "${count}x"
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = PokerColors.DarkGreen.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, PokerColors.PokerGold.copy(alpha = 0.85f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            ),
            color = PokerColors.PokerGold,
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

private data class KnockoutOption(
    val id: Int?,
    val label: String
)