package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.invertHorizontally
import com.huntercoles.pokerpayout.core.design.components.PlayingCard
import com.huntercoles.pokerpayout.core.design.components.PlayingCardView as CorePlayingCardView
import com.huntercoles.pokerpayout.tools.presentation.SettingsViewModel
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

// All 52 cards in a deck
val allCards = listOf(
    // Hearts
    PlayingCard("A", "h"), PlayingCard("K", "h"), PlayingCard("Q", "h"), PlayingCard("J", "h"), PlayingCard("T", "h"),
    PlayingCard("9", "h"), PlayingCard("8", "h"), PlayingCard("7", "h"), PlayingCard("6", "h"), PlayingCard("5", "h"),
    PlayingCard("4", "h"), PlayingCard("3", "h"), PlayingCard("2", "h"),
    // Diamonds
    PlayingCard("A", "d"), PlayingCard("K", "d"), PlayingCard("Q", "d"), PlayingCard("J", "d"), PlayingCard("T", "d"),
    PlayingCard("9", "d"), PlayingCard("8", "d"), PlayingCard("7", "d"), PlayingCard("6", "d"), PlayingCard("5", "d"),
    PlayingCard("4", "d"), PlayingCard("3", "d"), PlayingCard("2", "d"),
    // Clubs
    PlayingCard("A", "c"), PlayingCard("K", "c"), PlayingCard("Q", "c"), PlayingCard("J", "c"), PlayingCard("T", "c"),
    PlayingCard("9", "c"), PlayingCard("8", "c"), PlayingCard("7", "c"), PlayingCard("6", "c"), PlayingCard("5", "c"),
    PlayingCard("4", "c"), PlayingCard("3", "c"), PlayingCard("2", "c"),
    // Spades
    PlayingCard("A", "s"), PlayingCard("K", "s"), PlayingCard("Q", "s"), PlayingCard("J", "s"), PlayingCard("T", "s"),
    PlayingCard("9", "s"), PlayingCard("8", "s"), PlayingCard("7", "s"), PlayingCard("6", "s"), PlayingCard("5", "s"),
    PlayingCard("4", "s"), PlayingCard("3", "s"), PlayingCard("2", "s")
)

@Composable
fun OddsCalculatorScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var gameState by remember { mutableStateOf(PokerGameState()) }
    val scope = rememberCoroutineScope()
    val showRulesPopup by viewModel.showRulesPopup.collectAsState()

    if (gameState.players.isEmpty()) {
        gameState = gameState.copy(
            players = listOf(
                Player(1, "Player 1"),
                Player(2, "Player 2")
            )
        )
    }
    
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
                    onClick = {
                        gameState = PokerGameState(
                            players = listOf(
                                Player(1, "Player 1"),
                                Player(2, "Player 2")
                            )
                        )
                    },
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
        
        // Rules Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.showRulesPopup() },
            colors = CardDefaults.cardColors(containerColor = PokerColors.DarkGreen),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Rules",
                    tint = PokerColors.PokerGold
                )
                Text(
                    text = "Rules",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PokerColors.PokerGold
                )
            }
        }

        // Rules Popup
        if (showRulesPopup) {
            RulesPopup(onDismiss = { viewModel.hideRulesPopup() })
        }
        
        // Player Management
        PlayerManagementCard(
            playerCount = gameState.players.size,
            onPlayerCountChange = { newCount ->
                val currentCount = gameState.players.size
                when {
                    newCount > currentCount -> {
                        // Add players
                        val newPlayers = (currentCount + 1..newCount).map { id ->
                            Player(id, "Player $id")
                        }
                        gameState = gameState.copy(
                            players = gameState.players + newPlayers
                        )
                    }
                    newCount < currentCount -> {
                        // Remove players (keep at least 2)
                        val keepCount = max(2, newCount)
                        gameState = gameState.copy(
                            players = gameState.players.take(keepCount)
                        )
                    }
                }
            }
        )
        
        // Players Cards
        PlayersCardsSection(
            players = gameState.players,
            onPlayerCardClick = { playerId ->
                gameState = gameState.copy(
                    showCardPicker = true,
                    selectedPlayerForCard = playerId,
                    selectedCardType = CardType.PLAYER_CARD
                )
            },
            onRemovePlayerCard = { playerId, cardIndex ->
                gameState = gameState.copy(
                    players = gameState.players.map { player ->
                        if (player.id == playerId) {
                            player.copy(cards = player.cards.filterIndexed { index, _ -> index != cardIndex })
                        } else player
                    }
                )
            }
        )
        
        // Community Cards
        CommunityCardsSection(
            communityCards = gameState.communityCards,
            onCommunityCardClick = {
                gameState = gameState.copy(
                    showCardPicker = true,
                    selectedCardType = CardType.COMMUNITY_CARD
                )
            },
            onRemoveCommunityCard = { cardIndex ->
                gameState = gameState.copy(
                    communityCards = gameState.communityCards.filterIndexed { index, _ -> index != cardIndex }
                )
            }
        )
        
        // Calculate Button and Results
        CalculationSection(
            gameState = gameState,
            onCalculate = {
                if (gameState.players.all { it.cards.size >= 2 }) {
                    gameState = gameState.copy(isSimulating = true)
                    
                    scope.launch {
                        try {
                            // Simulate poker odds with proper Texas Hold'em logic
                            val simulatedResults = simulateTexasHoldemOdds(gameState.players, gameState.communityCards)
                            
                            gameState = gameState.copy(
                                players = simulatedResults,
                                isSimulating = false
                            )
                        } catch (e: Exception) {
                            // Handle any errors gracefully
                            gameState = gameState.copy(isSimulating = false)
                        }
                    }
                }
            }
        )
    }
    
    // Card Picker Dialog
    if (gameState.showCardPicker) {
        CardPickerDialog(
            availableCards = allCards.filter { card ->
                val usedCards = gameState.players.flatMap { it.cards } + gameState.communityCards
                !usedCards.contains(card)
            },
            onCardSelected = { selectedCard ->
                when (gameState.selectedCardType) {
                    CardType.PLAYER_CARD -> {
                        gameState.selectedPlayerForCard?.let { playerId ->
                            gameState = gameState.copy(
                                players = gameState.players.map { player ->
                                    if (player.id == playerId && player.cards.size < 2) {
                                        player.copy(cards = player.cards + selectedCard)
                                    } else player
                                }
                            )
                        }
                    }
                    CardType.COMMUNITY_CARD -> {
                        if (gameState.communityCards.size < 5) {
                            gameState = gameState.copy(
                                communityCards = gameState.communityCards + selectedCard
                            )
                        }
                    }
                    null -> {}
                }
                gameState = gameState.copy(
                    showCardPicker = false,
                    selectedPlayerForCard = null,
                    selectedCardType = null
                )
            },
            onDismiss = {
                gameState = gameState.copy(
                    showCardPicker = false,
                    selectedPlayerForCard = null,
                    selectedCardType = null
                )
            }
        )
    }
}

@Composable
fun PlayerManagementCard(
    playerCount: Int,
    onPlayerCountChange: (Int) -> Unit
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
            Text(
                text = "Players ($playerCount)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PokerColors.PokerGold
            )

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
                    )
                )
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
    Card(
        modifier = Modifier.fillMaxWidth().height(400.dp),
        colors = CardDefaults.cardColors(containerColor = PokerColors.SurfacePrimary),
        border = BorderStroke(1.dp, PokerColors.AccentGreen)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Text(
                text = "Player Cards",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PokerColors.PokerGold,
                modifier = Modifier.padding(16.dp)
            )
            
            // Grid of players
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(players) { player ->
                    PlayerGridItem(
                        player = player,
                        onCardClick = { onPlayerCardClick(player.id) },
                        onRemoveCard = { cardIndex -> onRemovePlayerCard(player.id, cardIndex) }
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerGridItem(
    player: Player,
    onCardClick: () -> Unit,
    onRemoveCard: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        colors = CardDefaults.cardColors(containerColor = PokerColors.SurfaceSecondary.copy(alpha = 0.8f)),
        border = BorderStroke(1.dp, PokerColors.PokerGold.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = player.name,
                color = PokerColors.CardWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
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
            
            if (player.winPercentage > 0 || player.tiePercentage > 0) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Win: ${"%.1f".format(player.winPercentage)}%",
                        color = Color.Green,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tie: ${"%.1f".format(player.tiePercentage)}%",
                        color = Color.Yellow,
                        fontSize = 12.sp,
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
                        PlayingCardView(
                            card = cards[position],
                            onRemove = { onRemoveCard(startIndex + position) },
                            modifier = slotModifier
                        )
                    } else {
                        AddCardSlot(onClick = onCardClick, modifier = slotModifier)
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
fun CalculationSection(
    gameState: PokerGameState,
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
            Text(
                text = "Calculate Odds",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PokerColors.PokerGold
            )
            
            val canCalculate = gameState.players.all { it.cards.size >= 2 }
            
            Button(
                onClick = onCalculate,
                enabled = canCalculate && !gameState.isSimulating,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PokerColors.AccentGreen,
                    contentColor = PokerColors.CardWhite
                )
            ) {
                if (gameState.isSimulating) {
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
            
            if (!canCalculate) {
                Text(
                    text = "Each player needs 2 cards to calculate odds",
                    color = PokerColors.TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun CardPickerDialog(
    availableCards: List<PlayingCard>,
    onCardSelected: (PlayingCard) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select a Card",
                color = PokerColors.CardWhite
            )
        },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(400.dp)
            ) {
                items(availableCards) { card ->
                    PlayingCardView(
                        card = card,
                        onRemove = { onCardSelected(card) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = PokerColors.PokerGold)
            }
        },
        containerColor = PokerColors.SurfacePrimary
    )
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