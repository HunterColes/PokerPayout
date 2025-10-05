package com.huntercoles.fatline.basicfeature.presentation.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.huntercoles.fatline.core.design.PokerColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

// Data classes for poker functionality
data class Card(
    val rank: String,
    val suit: String
) {
    override fun toString() = "$rank$suit"
    
    fun getSuitSymbol(): String = when(suit) {
        "h" -> "♥"
        "d" -> "♦"
        "c" -> "♣"
        "s" -> "♠"
        else -> suit
    }
    
    fun getSuitColor(): Color = when(suit) {
        "h", "d" -> Color.Red
        "c", "s" -> Color.Black
        else -> PokerColors.CardWhite
    }
}

data class Player(
    val id: Int,
    val name: String,
    val cards: List<Card> = emptyList(),
    val winPercentage: Double = 0.0,
    val tiePercentage: Double = 0.0
)

data class PokerGameState(
    val players: List<Player> = emptyList(),
    val communityCards: List<Card> = emptyList(),
    val isSimulating: Boolean = false,
    val showCardPicker: Boolean = false,
    val selectedPlayerForCard: Int? = null,
    val selectedCardType: CardType? = null
)

enum class CardType {
    PLAYER_CARD, COMMUNITY_CARD
}

@Composable
fun TempScreen() {
    PokerOddsCalculatorScreen()
}

@Composable
fun PokerOddsCalculatorScreen() {
    var gameState by remember { mutableStateOf(PokerGameState()) }
    val scope = rememberCoroutineScope()
    val ranks = listOf("A", "K", "Q", "J", "T", "9", "8", "7", "6", "5", "4", "3", "2")
    val suits = listOf("h", "d", "c", "s")
    val allCards = ranks.flatMap { rank -> suits.map { suit -> Card(rank, suit) } }
    
    // Initialize with 2 players if empty
    LaunchedEffect(Unit) {
        if (gameState.players.isEmpty()) {
            gameState = gameState.copy(
                players = listOf(
                    Player(1, "Player 1"),
                    Player(2, "Player 2")
                )
            )
        }
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
                        tint = PokerColors.PokerGold
                    )
                }
            }
        }
        
        // Player Management
        PlayerManagementCard(
            players = gameState.players,
            onAddPlayer = {
                gameState = gameState.copy(
                    players = gameState.players + Player(
                        id = gameState.players.size + 1,
                        name = "Player ${gameState.players.size + 1}"
                    )
                )
            },
            onRemovePlayer = { playerId ->
                gameState = gameState.copy(
                    players = gameState.players.filter { it.id != playerId }
                )
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
                            // Simulate poker odds with proper Texas Hold'em logic on background thread
                            val simulatedResults = withContext(Dispatchers.Default) {
                                simulateTexasHoldemOdds(gameState.players, gameState.communityCards)
                            }
                            
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
    players: List<Player>,
    onAddPlayer: () -> Unit,
    onRemovePlayer: (Int) -> Unit
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Players (${players.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PokerColors.PokerGold
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onAddPlayer,
                        enabled = players.size < 10,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PokerColors.AccentGreen,
                            contentColor = PokerColors.CardWhite
                        ),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Player",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", fontSize = 12.sp)
                    }
                }
            }
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(players) { player ->
                    PlayerChip(
                        player = player,
                        onRemove = { onRemovePlayer(player.id) },
                        canRemove = players.size > 2
                    )
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
                text = "Player Cards",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PokerColors.PokerGold
            )
            
            players.forEach { player ->
                PlayerCardsRow(
                    player = player,
                    onCardClick = { onPlayerCardClick(player.id) },
                    onRemoveCard = { cardIndex -> onRemovePlayerCard(player.id, cardIndex) }
                )
            }
        }
    }
}

@Composable
fun PlayerCardsRow(
    player: Player,
    onCardClick: () -> Unit,
    onRemoveCard: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = player.name,
            color = PokerColors.CardWhite,
            modifier = Modifier.width(80.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(player.cards.size) { index ->
                PlayingCardView(
                    card = player.cards[index],
                    onRemove = { onRemoveCard(index) }
                )
            }
            
            if (player.cards.size < 2) {
                item {
                    AddCardSlot(onClick = onCardClick)
                }
            }
        }
        
        if (player.winPercentage > 0 || player.tiePercentage > 0) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.width(90.dp)
            ) {
                Text(
                    text = "Win: ${"%.2f".format(player.winPercentage)}%",
                    color = PokerColors.SuccessGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End
                )
                Text(
                    text = "Tie: ${"%.2f".format(player.tiePercentage)}%",
                    color = PokerColors.PokerGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
fun CommunityCardsSection(
    communityCards: List<Card>,
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Community Cards (${communityCards.size}/5)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PokerColors.PokerGold
            )
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(communityCards.size) { index ->
                    PlayingCardView(
                        card = communityCards[index],
                        onRemove = { onRemoveCommunityCard(index) }
                    )
                }
                
                if (communityCards.size < 5) {
                    item {
                        AddCardSlot(onClick = onCommunityCardClick)
                    }
                }
            }
        }
    }
}

@Composable
fun PlayingCardView(
    card: Card,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .size(width = 50.dp, height = 70.dp)
            .clickable { onRemove() },
        colors = CardDefaults.cardColors(containerColor = PokerColors.CardWhite),
        border = BorderStroke(1.dp, Color.Gray)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = card.rank,
                color = card.getSuitColor(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = card.getSuitSymbol(),
                color = card.getSuitColor(),
                fontSize = 20.sp
            )
        }
    }
}

@Composable
fun AddCardSlot(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .size(width = 50.dp, height = 70.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = PokerColors.SurfaceSecondary),
        border = BorderStroke(1.dp, PokerColors.PokerGold)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Card",
                tint = PokerColors.PokerGold,
                modifier = Modifier.size(24.dp)
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
    availableCards: List<Card>,
    onCardSelected: (Card) -> Unit,
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

// Poker calculation logic based on cookpete/poker-odds reference
// Card values for hand evaluation
val CARD_VALUES = listOf("2", "3", "4", "5", "6", "7", "8", "9", "T", "J", "Q", "K", "A")
val CARD_SUITS = listOf("s", "c", "h", "d")

data class HandResult(
    val hand: List<Card>,
    val count: Int = 0,
    val wins: Int = 0,
    val ties: Int = 0
)

// Main calculation function inspired by cookpete/poker-odds
fun calculateEquity(hands: List<List<Card>>, board: List<Card> = emptyList(), iterations: Int = 10000): List<HandResult> {
    var results = hands.map { hand ->
        HandResult(
            hand = hand,
            count = 0,
            wins = 0,
            ties = 0
        )
    }
    
    when {
        board.size == 5 -> {
            // All community cards present - evaluate final hands
            results = analyzeHands(results, board)
        }
        board.size >= 3 -> {
            // Flop or turn - enumerate remaining possibilities
            val deck = createDeck(board + hands.flatten())
            when (board.size) {
                3 -> {
                    // Need 2 more cards
                    for (i in deck.indices) {
                        for (j in i + 1 until deck.size) {
                            results = analyzeHands(results, board + listOf(deck[i], deck[j]))
                        }
                    }
                }
                4 -> {
                    // Need 1 more card
                    for (card in deck) {
                        results = analyzeHands(results, board + card)
                    }
                }
            }
        }
        else -> {
            // Preflop or minimal board - use Monte Carlo simulation
            repeat(iterations) {
                val remainingCards = 5 - board.size
                val randomCards = dealRandomCards(hands.flatten() + board, remainingCards)
                results = analyzeHands(results, board + randomCards)
            }
        }
    }
    
    // Mark favorites
    val maxWins = results.maxOfOrNull { it.wins } ?: 0
    return results.map { result ->
        result.copy()
    }
}

fun createDeck(withoutCards: List<Card> = emptyList()): List<Card> {
    val deck = mutableListOf<Card>()
    for (rank in CARD_VALUES) {
        for (suit in CARD_SUITS) {
            val card = Card(rank, suit)
            if (!withoutCards.contains(card)) {
                deck.add(card)
            }
        }
    }
    return deck
}

fun dealRandomCards(withoutCards: List<Card>, count: Int): List<Card> {
    val availableDeck = createDeck(withoutCards)
    return availableDeck.shuffled().take(count)
}

fun analyzeHands(results: List<HandResult>, board: List<Card>): List<HandResult> {
    val handRanks = results.map { result ->
        rankHand(result.hand + board)
    }
    
    val bestRank = handRanks.maxOrNull()
    val bestCount = handRanks.count { it == bestRank }
    val isTie = bestCount > 1
    
    return results.mapIndexed { index, result ->
        val newResult = result.copy(count = result.count + 1)
        
        when {
            handRanks[index] == bestRank && isTie -> {
                newResult.copy(ties = newResult.ties + 1)
            }
            handRanks[index] == bestRank -> {
                newResult.copy(wins = newResult.wins + 1)
            }
            else -> newResult
        }
    }
}

// Hand ranking system based on poker-odds reference
fun rankHand(cards: List<Card>): String {
    if (cards.size < 5) return "0" // Invalid hand
    
    // Get best 5-card hand from available cards
    val bestHand = getBestHand(cards)
    val ranks = bestHand.map { numericalValue(it) }.sorted().reversed()
    val suits = bestHand.map { it.suit }
    
    val isFlush = suits.distinct().size == 1
    val straight = getStraight(ranks)
    val rankCounts = ranks.groupingBy { it }.eachCount()
    val counts = rankCounts.values.sorted().reversed()
    
    return when {
        isFlush && straight != null -> {
            if (straight == 14) "9${convertToHex(listOf(14, 13, 12, 11, 10))}" // Royal flush
            else "8${convertToHex(getStraightCards(straight))}" // Straight flush
        }
        counts == listOf(4, 1) -> {
            val fourKind = rankCounts.filter { it.value == 4 }.keys.first()
            val kicker = rankCounts.filter { it.value == 1 }.keys.first()
            "7${convertToHex(listOf(fourKind))}${convertToHex(listOf(kicker))}"
        }
        counts == listOf(3, 2) -> {
            val threeKind = rankCounts.filter { it.value == 3 }.keys.first()
            val pair = rankCounts.filter { it.value == 2 }.keys.first()
            "6${convertToHex(listOf(threeKind, pair))}"
        }
        isFlush -> {
            "5${convertToHex(ranks.take(5))}"
        }
        straight != null -> {
            "4${convertToHex(getStraightCards(straight))}"
        }
        counts == listOf(3, 1, 1) -> {
            val threeKind = rankCounts.filter { it.value == 3 }.keys.first()
            val kickers = rankCounts.filter { it.value == 1 }.keys.sorted().reversed().take(2)
            "3${convertToHex(listOf(threeKind))}${convertToHex(kickers)}"
        }
        counts == listOf(2, 2, 1) -> {
            val pairs = rankCounts.filter { it.value == 2 }.keys.sorted().reversed()
            val kicker = rankCounts.filter { it.value == 1 }.keys.first()
            "2${convertToHex(pairs)}${convertToHex(listOf(kicker))}"
        }
        counts == listOf(2, 1, 1, 1) -> {
            val pair = rankCounts.filter { it.value == 2 }.keys.first()
            val kickers = rankCounts.filter { it.value == 1 }.keys.sorted().reversed().take(3)
            "1${convertToHex(listOf(pair))}${convertToHex(kickers)}"
        }
        else -> {
            "0${convertToHex(ranks.take(5))}"
        }
    }
}

fun getBestHand(cards: List<Card>): List<Card> {
    if (cards.size <= 5) return cards

    // For more than 5 cards, find the best 5-card combination
    // Generate all possible 5-card combinations and pick the best one
    val combinations = generateCombinations(cards, 5)
    var bestHand = combinations[0]
    var bestRank = rankHand(bestHand)

    for (combination in combinations.drop(1)) {
        val currentRank = rankHand(combination)
        if (currentRank > bestRank) {
            bestRank = currentRank
            bestHand = combination
        }
    }

    return bestHand
}

fun numericalValue(card: Card): Int = when(card.rank) {
    "A" -> 14
    "K" -> 13
    "Q" -> 12
    "J" -> 11
    "T" -> 10
    else -> card.rank.toIntOrNull() ?: 0
}

fun getStraight(ranks: List<Int>): Int? {
    val uniqueRanks = ranks.distinct().sorted()
    if (uniqueRanks.size < 5) return null
    
    // Check for standard straights
    for (i in 0..uniqueRanks.size - 5) {
        val fiveCards = uniqueRanks.subList(i, i + 5)
        if (fiveCards.last() - fiveCards.first() == 4) {
            return fiveCards.last()
        }
    }
    
    // Check for A-2-3-4-5 straight (wheel)
    if (uniqueRanks.contains(14) && uniqueRanks.contains(2) && 
        uniqueRanks.contains(3) && uniqueRanks.contains(4) && uniqueRanks.contains(5)) {
        return 5 // 5-high straight
    }
    
    return null
}

fun getStraightCards(high: Int): List<Int> {
    return when (high) {
        5 -> listOf(5, 4, 3, 2, 1) // A-2-3-4-5 (ace low)
        else -> listOf(high, high-1, high-2, high-3, high-4)
    }
}

fun convertToHex(values: List<Int>): String {
    return values.joinToString("") { 
        when (it) {
            10 -> "a"
            11 -> "b" 
            12 -> "c"
            13 -> "d"
            14 -> "e"
            1 -> "1" // Ace low in wheel straight
            else -> it.toString(16)
        }
    }
}

fun generateCombinations(cards: List<Card>, k: Int): List<List<Card>> {
    val result = mutableListOf<List<Card>>()

    fun combine(start: Int, current: MutableList<Card>) {
        if (current.size == k) {
            result.add(current.toList())
            return
        }

        for (i in start until cards.size) {
            current.add(cards[i])
            combine(i + 1, current)
            current.removeAt(current.size - 1)
        }
    }

    combine(0, mutableListOf())
    return result
}

// Updated simulation function to work with UI
fun simulateTexasHoldemOdds(players: List<Player>, communityCards: List<Card>): List<Player> {
    if (players.size < 2) return players
    
    val hands = players.map { it.cards }
    val results = calculateEquity(hands, communityCards, 10000)
    
    return players.mapIndexed { index, player ->
        val result = results[index]
        val winPercentage = if (result.count > 0) (result.wins.toDouble() / result.count) * 100 else 0.0
        val tiePercentage = if (result.count > 0) (result.ties.toDouble() / result.count) * 100 else 0.0
        
        player.copy(
            winPercentage = winPercentage,
            tiePercentage = tiePercentage
        )
    }
}