package com.huntercoles.pokerpayout.tools.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.core.preferences.OddsCalculatorPreferences
import com.huntercoles.pokerpayout.core.design.components.PlayingCard
import com.huntercoles.pokerpayout.tools.presentation.composable.Player
import com.huntercoles.pokerpayout.tools.presentation.composable.CardType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OddsCalculatorUiState(
    val playerCount: Int = 2,
    val players: List<Player> = emptyList(),
    val communityCards: List<PlayingCard> = emptyList(),
    val showCardPicker: Boolean = false,
    val selectedPlayerForCard: Int? = null,
    val selectedCardType: CardType? = null,
    val isSimulating: Boolean = false,
    val showResetDialog: Boolean = false
)

@HiltViewModel
class OddsCalculatorViewModel @Inject constructor(
    private val oddsPreferences: OddsCalculatorPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(OddsCalculatorUiState())
    val uiState: StateFlow<OddsCalculatorUiState> = _uiState.asStateFlow()

    init {
        loadSavedState()
    }

    fun acceptIntent(intent: OddsCalculatorIntent) {
        when (intent) {
            is OddsCalculatorIntent.PlayerCountChanged -> updatePlayerCount(intent.count)
            is OddsCalculatorIntent.ShowCardPickerForPlayer -> showCardPickerForPlayer(intent.playerId)
            is OddsCalculatorIntent.ShowCardPickerForCommunity -> showCardPickerForCommunity()
            is OddsCalculatorIntent.CardSelected -> handleCardSelected(intent.cardString)
            is OddsCalculatorIntent.PlayerCardRemoved -> removePlayerCard(intent.playerId, intent.cardIndex)
            is OddsCalculatorIntent.CommunityCardRemoved -> removeCommunityCard(intent.cardIndex)
            is OddsCalculatorIntent.HideCardPicker -> hideCardPicker()
            is OddsCalculatorIntent.StartSimulation -> startSimulation(intent.players, intent.communityCards)
            is OddsCalculatorIntent.SimulationComplete -> updateSimulationResults(intent.players)
            is OddsCalculatorIntent.ShowResetDialog -> {
                if (!isInDefaultState()) {
                    showResetDialog()
                }
            }
            is OddsCalculatorIntent.HideResetDialog -> hideResetDialog()
            is OddsCalculatorIntent.ConfirmReset -> {
                resetAllData()
                hideResetDialog()
            }
        }
    }

    private fun loadSavedState() {
        val playerCount = oddsPreferences.getPlayerCount()
        val players = (1..playerCount).map { id ->
            val cardsString = oddsPreferences.getPlayerCards(id)
            val cards = parseCardsFromString(cardsString)
            Player(id = id, name = "Player $id", cards = cards)
        }
        val communityCards = parseCardsFromString(oddsPreferences.getCommunityCards())

        _uiState.value = OddsCalculatorUiState(
            playerCount = playerCount,
            players = players,
            communityCards = communityCards
        )
    }

    private fun updatePlayerCount(count: Int) {
        oddsPreferences.setPlayerCount(count)
        val currentPlayers = _uiState.value.players
        
        val players = if (count > currentPlayers.size) {
            // Add new players
            currentPlayers + ((currentPlayers.size + 1)..count).map { id ->
                Player(id = id, name = "Player $id", cards = emptyList())
            }
        } else {
            // Remove excess players and clear their saved cards
            (count + 1..currentPlayers.size).forEach { id ->
                oddsPreferences.setPlayerCards(id, "")
            }
            currentPlayers.take(count)
        }
        
        _uiState.update { it.copy(playerCount = count, players = players) }
    }

    private fun addPlayerCard(playerId: Int, cardString: String) {
        val card = parseCardFromString(cardString) ?: return
        val players = _uiState.value.players.map { player ->
            if (player.id == playerId && player.cards.size < 2) {
                val updatedCards = player.cards + card
                oddsPreferences.setPlayerCards(playerId, formatCardsToString(updatedCards))
                player.copy(cards = updatedCards)
            } else player
        }
        _uiState.update { it.copy(players = players, showCardPicker = false) }
    }

    private fun removePlayerCard(playerId: Int, cardIndex: Int) {
        val players = _uiState.value.players.map { player ->
            if (player.id == playerId) {
                val updatedCards = player.cards.filterIndexed { index, _ -> index != cardIndex }
                oddsPreferences.setPlayerCards(playerId, formatCardsToString(updatedCards))
                player.copy(cards = updatedCards, winPercentage = 0.0, tiePercentage = 0.0)
            } else player
        }
        _uiState.update { it.copy(players = players) }
    }

    private fun addCommunityCard(cardString: String) {
        val card = parseCardFromString(cardString) ?: return
        if (_uiState.value.communityCards.size < 5) {
            val updatedCards = _uiState.value.communityCards + card
            oddsPreferences.setCommunityCards(formatCardsToString(updatedCards))
            _uiState.update { it.copy(communityCards = updatedCards, showCardPicker = false) }
        }
    }

    private fun removeCommunityCard(cardIndex: Int) {
        val updatedCards = _uiState.value.communityCards.filterIndexed { index, _ -> index != cardIndex }
        oddsPreferences.setCommunityCards(formatCardsToString(updatedCards))
        // Clear all player percentages when community cards change
        val players = _uiState.value.players.map { it.copy(winPercentage = 0.0, tiePercentage = 0.0) }
        _uiState.update { it.copy(communityCards = updatedCards, players = players) }
    }

    private fun showCardPickerForPlayer(playerId: Int) {
        _uiState.update { it.copy(showCardPicker = true, selectedPlayerForCard = playerId, selectedCardType = CardType.PLAYER_CARD) }
    }

    private fun showCardPickerForCommunity() {
        _uiState.update { it.copy(showCardPicker = true, selectedCardType = CardType.COMMUNITY_CARD) }
    }

    private fun handleCardSelected(cardString: String) {
        val card = parseCardFromString(cardString) ?: return
        
        when (_uiState.value.selectedCardType) {
            CardType.PLAYER_CARD -> {
                _uiState.value.selectedPlayerForCard?.let { playerId ->
                    addPlayerCard(playerId, cardString)
                }
            }
            CardType.COMMUNITY_CARD -> {
                addCommunityCard(cardString)
            }
            null -> {}
        }
    }

    private fun hideCardPicker() {
        _uiState.update { it.copy(showCardPicker = false, selectedPlayerForCard = null, selectedCardType = null) }
    }

    private fun startSimulation(players: List<Player>, communityCards: List<PlayingCard>) {
        _uiState.update { it.copy(isSimulating = true) }
    }

    private fun updateSimulationResults(players: List<Player>) {
        _uiState.update { it.copy(players = players, isSimulating = false) }
    }

    private fun showResetDialog() {
        _uiState.update { it.copy(showResetDialog = true) }
    }

    private fun hideResetDialog() {
        _uiState.update { it.copy(showResetDialog = false) }
    }

    private fun resetAllData() {
        oddsPreferences.resetAllData()
        loadSavedState()
    }

    private fun isInDefaultState(): Boolean {
        return oddsPreferences.isInDefaultState()
    }

    // Helper functions to serialize/deserialize cards
    private fun formatCardsToString(cards: List<PlayingCard>): String {
        return cards.joinToString(",") { "${it.rank}${it.suit}" }
    }

    private fun parseCardsFromString(cardsString: String): List<PlayingCard> {
        if (cardsString.isEmpty()) return emptyList()
        return cardsString.split(",").mapNotNull { parseCardFromString(it) }
    }

    private fun parseCardFromString(cardString: String): PlayingCard? {
        if (cardString.length != 2) return null
        return PlayingCard(rank = cardString[0].toString(), suit = cardString[1].toString())
    }
}
