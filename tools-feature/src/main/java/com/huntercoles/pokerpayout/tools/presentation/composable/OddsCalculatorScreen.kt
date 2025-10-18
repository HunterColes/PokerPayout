package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerDialog
import com.huntercoles.pokerpayout.core.design.components.invertHorizontally
import com.huntercoles.pokerpayout.core.design.components.PlayingCard
import com.huntercoles.pokerpayout.core.design.components.PlayingCardView as CorePlayingCardView
import com.huntercoles.pokerpayout.tools.presentation.OddsCalculatorViewModel
import com.huntercoles.pokerpayout.tools.presentation.OddsCalculatorIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.huntercoles.pokerpayout.tools.poker.*
import kotlin.math.max
import kotlin.math.min

// Data classes for poker functionality
// Note: This UI-layer PlayingCard (from core) uses String for display flexibility.
// The poker engine uses a separate Card(Char, Char) for performance in calculations.
// This separation keeps UI concerns separate from poker calculation logic.

data class Player(
    val id: Int,
    val name: String,
    val cards: List<PlayingCard> = emptyList(),
    val winPercentage: Double = 0.0,
    val tiePercentage: Double = 0.0
)

enum class CardType {
    PLAYER_CARD, COMMUNITY_CARD
}

data class PokerGameState(
    val players: List<Player> = emptyList(),
    val communityCards: List<PlayingCard> = emptyList(),
    val showCardPicker: Boolean = false,
    val selectedPlayerForCard: Int? = null,
    val selectedCardType: CardType? = null,
    val isSimulating: Boolean = false
)

// All 52 cards in a deck - sorted by rank (A-K), then suit (C-H-S-D)
val allCards = listOf(
    // Aces
    PlayingCard("A", "c"), PlayingCard("A", "h"), PlayingCard("A", "s"), PlayingCard("A", "d"),
    // Kings
    PlayingCard("K", "c"), PlayingCard("K", "h"), PlayingCard("K", "s"), PlayingCard("K", "d"),
    // Queens
    PlayingCard("Q", "c"), PlayingCard("Q", "h"), PlayingCard("Q", "s"), PlayingCard("Q", "d"),
    // Jacks
    PlayingCard("J", "c"), PlayingCard("J", "h"), PlayingCard("J", "s"), PlayingCard("J", "d"),
    // Tens
    PlayingCard("T", "c"), PlayingCard("T", "h"), PlayingCard("T", "s"), PlayingCard("T", "d"),
    // Nines
    PlayingCard("9", "c"), PlayingCard("9", "h"), PlayingCard("9", "s"), PlayingCard("9", "d"),
    // Eights
    PlayingCard("8", "c"), PlayingCard("8", "h"), PlayingCard("8", "s"), PlayingCard("8", "d"),
    // Sevens
    PlayingCard("7", "c"), PlayingCard("7", "h"), PlayingCard("7", "s"), PlayingCard("7", "d"),
    // Sixes
    PlayingCard("6", "c"), PlayingCard("6", "h"), PlayingCard("6", "s"), PlayingCard("6", "d"),
    // Fives
    PlayingCard("5", "c"), PlayingCard("5", "h"), PlayingCard("5", "s"), PlayingCard("5", "d"),
    // Fours
    PlayingCard("4", "c"), PlayingCard("4", "h"), PlayingCard("4", "s"), PlayingCard("4", "d"),
    // Threes
    PlayingCard("3", "c"), PlayingCard("3", "h"), PlayingCard("3", "s"), PlayingCard("3", "d"),
    // Twos
    PlayingCard("2", "c"), PlayingCard("2", "h"), PlayingCard("2", "s"), PlayingCard("2", "d")
)

@Composable
fun OddsCalculatorScreen(
    viewModel: OddsCalculatorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🃏 Poker Odds Calculator",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = PokerColors.PokerGold,
                modifier = Modifier.weight(1f)
            )
            
            // Reset button
            Card(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = PokerColors.DarkGreen)
            ) {
                IconButton(
                    onClick = { viewModel.acceptIntent(OddsCalculatorIntent.ShowResetDialog) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset",
                        tint = PokerColors.PokerGold,
                        modifier = Modifier
                            .size(24.dp)
                            .invertHorizontally()
                    )
                }
            }
        }
        
        // Reset Confirmation Dialog
        if (uiState.showResetDialog) {
            PokerDialog(
                onDismissRequest = { viewModel.acceptIntent(OddsCalculatorIntent.HideResetDialog) }
            ) {
                Text(
                    text = "Reset odds calculator?",
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
                        text = "This will reset all player cards and community cards.",
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
                        onClick = { viewModel.acceptIntent(OddsCalculatorIntent.HideResetDialog) }
                    ) {
                        Text(
                            text = "Cancel",
                            color = PokerColors.CardWhite
                        )
                    }

                    TextButton(
                        onClick = { viewModel.acceptIntent(OddsCalculatorIntent.ConfirmReset) }
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
        
        // Player Management
        PlayerManagementCard(
            playerCount = uiState.playerCount,
            onPlayerCountChange = { newCount ->
                viewModel.acceptIntent(OddsCalculatorIntent.PlayerCountChanged(newCount))
            },
            isSimulating = uiState.isSimulating,
            canCalculate = uiState.players.all { it.cards.size >= 2 },
            validCommunityCards = uiState.communityCards.size in listOf(0, 3, 4, 5),
            onCalculate = {
                if (uiState.players.all { it.cards.size >= 2 }) {
                    viewModel.acceptIntent(OddsCalculatorIntent.StartSimulation(uiState.players, uiState.communityCards))
                    
                    scope.launch {
                        try {
                            // Simulate poker odds with proper Texas Hold'em logic
                            val simulatedResults = simulateTexasHoldemOdds(uiState.players, uiState.communityCards)
                            
                            viewModel.acceptIntent(OddsCalculatorIntent.SimulationComplete(simulatedResults))
                        } catch (e: Exception) {
                            // Handle any errors gracefully
                            viewModel.acceptIntent(OddsCalculatorIntent.SimulationComplete(uiState.players))
                        }
                    }
                }
            }
        )
        
        // Players Cards
        PlayersCardsSection(
            players = uiState.players,
            onPlayerCardClick = { playerId ->
                viewModel.acceptIntent(OddsCalculatorIntent.ShowCardPickerForPlayer(playerId))
            },
            onRemovePlayerCard = { playerId, cardIndex ->
                viewModel.acceptIntent(OddsCalculatorIntent.PlayerCardRemoved(playerId, cardIndex))
            }
        )
        
        // Community Cards
        CommunityCardsSection(
            communityCards = uiState.communityCards,
            onCommunityCardClick = {
                viewModel.acceptIntent(OddsCalculatorIntent.ShowCardPickerForCommunity)
            },
            onRemoveCommunityCard = { cardIndex ->
                viewModel.acceptIntent(OddsCalculatorIntent.CommunityCardRemoved(cardIndex))
            }
        )
    }
    
    // Card Picker Dialog
    if (uiState.showCardPicker) {
        val usedCards = uiState.players.flatMap { it.cards } + uiState.communityCards
        CardPickerDialog(
            allCards = allCards,
            usedCards = usedCards,
            onCardSelected = { selectedCard ->
                val cardString = "${selectedCard.rank}${selectedCard.suit}"
                viewModel.acceptIntent(OddsCalculatorIntent.CardSelected(cardString))
            },
            onDismiss = {
                viewModel.acceptIntent(OddsCalculatorIntent.HideCardPicker)
            }
        )
    }
}

@Composable
fun PlayerManagementCard(
    playerCount: Int,
    onPlayerCountChange: (Int) -> Unit,
    isSimulating: Boolean,
    canCalculate: Boolean,
    validCommunityCards: Boolean,
    onCalculate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PokerColors.SurfacePrimary),
        border = BorderStroke(1.dp, PokerColors.AccentGreen)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Player Count Slider
            Column {
                Text(
                    text = "Number of Players: $playerCount",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = PokerColors.CardWhite
                )

                Spacer(modifier = Modifier.height(8.dp))

                Slider(
                    value = playerCount.toFloat(),
                    onValueChange = { onPlayerCountChange(it.toInt()) },
                    valueRange = 2f..10f,
                    steps = 7,
                    colors = SliderDefaults.colors(
                        thumbColor = PokerColors.PokerGold,
                        activeTrackColor = PokerColors.AccentGreen,
                        inactiveTrackColor = PokerColors.DarkGreen
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Calculate Odds Button
            Button(
                onClick = onCalculate,
                enabled = canCalculate && validCommunityCards && !isSimulating,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PokerColors.AccentGreen,
                    contentColor = PokerColors.CardWhite
                )
            ) {
                if (isSimulating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = PokerColors.CardWhite,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Calculating...")
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Calculate Odds")
                }
            }
        }
    }
}

@Composable
fun PlayerChip(
    player: Player,
    onRemove: () -> Unit,
    canRemove: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PokerColors.SurfaceSecondary),
        border = BorderStroke(1.dp, PokerColors.PokerGold)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = player.name,
                color = PokerColors.CardWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            
            if (canRemove) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Remove ${player.name}",
                    tint = PokerColors.ErrorRed,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onRemove() }
                )
            }
        }
    }
}

@Composable
fun PlayersCardsSection(
    players: List<Player>,
    onPlayerCardClick: (Int) -> Unit,
    onRemovePlayerCard: (Int, Int) -> Unit
) {
    val scrollState = rememberScrollState()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp), // Reduced from 280dp for tighter layout
        colors = CardDefaults.cardColors(containerColor = PokerColors.SurfacePrimary),
        border = BorderStroke(1.dp, PokerColors.AccentGreen)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Horizontal scrolling player cards
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(20.dp), // Increased from 16dp
                horizontalArrangement = Arrangement.spacedBy(16.dp), // Increased from 12dp
                verticalAlignment = Alignment.CenterVertically
            ) {
                players.forEach { player ->
                    PlayerCardItem(
                        player = player,
                        onCardClick = { onPlayerCardClick(player.id) },
                        onRemoveCard = { cardIndex -> onRemovePlayerCard(player.id, cardIndex) }
                    )
                }
            }
            
            // Scroll indicator (poker gold slider)
            if (players.size > 1) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp) // Increased vertical padding
                ) {
                    val trackWidth = maxWidth
                    
                    // Calculate if scrolling is needed
                    val maxScroll = scrollState.maxValue.toFloat()
                    val needsScrolling = maxScroll > 0
                    
                    // Variable slider width - full when no scrolling, proportional when scrolling
                    val indicatorWidthFraction = if (!needsScrolling) {
                        1f // Full width when no scrolling
                    } else {
                        // Calculate visible fraction of content
                        0.3f.coerceAtLeast(1f / players.size)
                    }
                    val indicatorWidth = trackWidth * indicatorWidthFraction
                    
                    // Background track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(
                                color = PokerColors.DarkGreen,
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                    
                    // Scrollable indicator
                    val scrollProgress = if (needsScrolling) {
                        (scrollState.value.toFloat() / maxScroll).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    
                    val maxOffset = trackWidth - indicatorWidth
                    
                    Box(
                        modifier = Modifier
                            .width(indicatorWidth)
                            .height(4.dp)
                            .offset(x = maxOffset * scrollProgress)
                            .background(
                                color = PokerColors.PokerGold,
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerCardItem(
    player: Player,
    onCardClick: () -> Unit,
    onRemoveCard: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .wrapContentWidth() // Variable width to shrink-wrap content
            .fillMaxHeight()
            .padding(6.dp),
        colors = CardDefaults.cardColors(containerColor = PokerColors.SurfaceSecondary.copy(alpha = 0.8f)),
        border = BorderStroke(1.dp, PokerColors.PokerGold.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp), // Reduced from 16dp
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp) // Reduced from 12dp
        ) {
            // Player name
            Text(
                text = player.name,
                color = PokerColors.CardWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            // Player cards
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(2) { index ->
                    if (index < player.cards.size) {
                        PlayingCardView(
                            card = player.cards[index],
                            onRemove = { onRemoveCard(index) }
                        )
                    } else {
                        AddCardSlot(onClick = onCardClick)
                    }
                }
            }
            
            // Stats below cards (vertical stack) - with spacer to push to bottom if needed
            Spacer(modifier = Modifier.weight(1f, fill = false))
            
            if (player.winPercentage > 0 || player.tiePercentage > 0) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp) // Reduced from 6dp
                ) {
                    Text(
                        text = "${"%.2f".format(player.winPercentage)}%",
                        color = Color.Green,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${"%.2f".format(player.tiePercentage)}%",
                        color = Color.Yellow,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun CommunityCardsSection(
    communityCards: List<PlayingCard>,
    onCommunityCardClick: () -> Unit,
    onRemoveCommunityCard: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PokerColors.SurfacePrimary),
        border = BorderStroke(1.dp, PokerColors.AccentGreen)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Community Cards (${communityCards.size}/5)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PokerColors.PokerGold
            )

            // Community cards in a single row of 5 boxes
            CommunityCardBox(
                title = "Community Cards",
                cards = communityCards,
                maxCards = 5,
                onCardClick = onCommunityCardClick,
                onRemoveCard = onRemoveCommunityCard,
                startIndex = 0,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun CommunityCardBox(
    title: String,
    cards: List<PlayingCard>,
    maxCards: Int,
    onCardClick: () -> Unit,
    onRemoveCard: (Int) -> Unit,
    startIndex: Int,
    modifier: Modifier = Modifier
) {
    // Use standard card slot size, slightly larger to match player cards
    val slotModifier = Modifier.size(width = PokerDimens.CardWidth, height = PokerDimens.CardHeight)
    if (maxCards > 1) {
        // Multiple cards: equally spaced slots across the width
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            repeat(maxCards) { position ->
                val label = when (position) {
                    1 -> "Flop"
                    3 -> "Turn"
                    4 -> "River"
                    else -> ""
                }
                // Can only click/add to the next available slot (left to right)
                val canAddHere = position == cards.size
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (label.isNotEmpty()) {
                        Text(
                            text = label,
                            color = PokerColors.CardWhite,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "",
                            color = PokerColors.CardWhite,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (position < cards.size) {
                        // Show existing card
                        PlayingCardView(
                            card = cards[position],
                            onRemove = { 
                                // Only allow removing the rightmost card
                                if (position == cards.size - 1) {
                                    onRemoveCard(startIndex + position)
                                }
                            },
                            modifier = slotModifier
                        )
                    } else if (canAddHere) {
                        // Only show clickable slot for the next position
                        AddCardSlot(onClick = onCardClick, modifier = slotModifier)
                    } else {
                        // Show disabled/greyed out slot
                        DisabledCardSlot(modifier = slotModifier)
                    }
                }
            }
        }
    } else {
        // Single card: show card and add slot if needed
        cards.forEachIndexed { index, card ->
            PlayingCardView(
                card = card,
                onRemove = { onRemoveCard(startIndex + index) },
                modifier = slotModifier
            )
        }
        if (cards.size < maxCards) {
            AddCardSlot(
                onClick = onCardClick,
                modifier = slotModifier
            )
        }
    }
}

@Composable
fun PlayingCardView(
    card: PlayingCard,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier.size(width = PokerDimens.CardWidth, height = PokerDimens.CardHeight)
) {
    CorePlayingCardView(
        card = card,
        onClick = onRemove,
        modifier = modifier
    )
}

@Composable
fun AddCardSlot(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(width = PokerDimens.CardWidth, height = PokerDimens.CardHeight)
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = PokerColors.SurfaceSecondary),
        border = BorderStroke(2.dp, PokerColors.PokerGold),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Card",
                tint = PokerColors.PokerGold,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun DisabledCardSlot(
    modifier: Modifier = Modifier.size(width = PokerDimens.CardWidth, height = PokerDimens.CardHeight)
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = PokerColors.SurfaceSecondary.copy(alpha = 0.3f)),
        border = BorderStroke(2.dp, PokerColors.PokerGold.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Empty disabled slot
        }
    }
}

@Composable
fun CardPickerDialog(
    allCards: List<PlayingCard>,
    usedCards: List<PlayingCard>,
    onCardSelected: (PlayingCard) -> Unit,
    onDismiss: () -> Unit
) {
    PokerDialog(onDismissRequest = onDismiss) {
        Text(
            text = "Select a Card",
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .height(400.dp)
                    .padding(16.dp)
            ) {
                items(allCards) { card ->
                    val isUsed = usedCards.contains(card)
                    if (isUsed) {
                        // Show empty/disabled slot for used cards - same size as actual cards
                        Box(
                            modifier = Modifier
                                .size(width = PokerDimens.CardWidth, height = PokerDimens.CardHeight)
                                .background(
                                    color = PokerColors.DarkGreen.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = PokerColors.CardWhite.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                        )
                    } else {
                        PlayingCardView(
                            card = card,
                            onRemove = { onCardSelected(card) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = PokerColors.CardWhite
                )
            }
        }
    }
}

// Updated simulation function to use TexasHoldemOdds.simulateEquity
suspend fun simulateTexasHoldemOdds(players: List<Player>, communityCards: List<PlayingCard>): List<Player> {
    if (players.size < 2) return players
    
    // Convert UI cards to TexasHoldemOdds cards with validation
    val holes = players.map { player ->
        player.cards
            .filter { it.rank.isNotEmpty() && it.suit.isNotEmpty() }
            .map { uiCard -> com.huntercoles.pokerpayout.tools.poker.Card(uiCard.rank[0], uiCard.suit[0]) }
    }
    val board = communityCards
        .filter { it.rank.isNotEmpty() && it.suit.isNotEmpty() }
        .map { uiCard -> com.huntercoles.pokerpayout.tools.poker.Card(uiCard.rank[0], uiCard.suit[0]) }
    
    val results = simulateEquity(holes, board)
    
    return players.mapIndexed { index, player ->
        val result = results[index]
        player.copy(
            winPercentage = result.winPct,
            tiePercentage = result.tiePct
        )
    }
}