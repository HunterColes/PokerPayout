package com.huntercoles.pokerpayout.tools.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.core.design.components.PlayingCard
import com.huntercoles.pokerpayout.core.preferences.OddsCalculatorPreferences
import com.huntercoles.pokerpayout.tools.poker.Cards
import com.huntercoles.pokerpayout.tools.poker.OddsEngine
import com.huntercoles.pokerpayout.tools.poker.OddsInputException
import com.huntercoles.pokerpayout.tools.poker.OddsRequest
import com.huntercoles.pokerpayout.tools.poker.OddsResult
import com.huntercoles.pokerpayout.tools.poker.OddsSettings
import com.huntercoles.pokerpayout.tools.poker.Seat
import com.huntercoles.pokerpayout.tools.presentation.composable.CardType
import com.huntercoles.pokerpayout.tools.presentation.composable.Player
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OddsCalculatorUiState(
    val playerCount: Int = 2,
    val players: List<Player> = emptyList(),
    val communityCards: List<PlayingCard> = emptyList(),
    val showCardPicker: Boolean = false,
    val selectedPlayerForCard: Int? = null,
    val selectedCardType: CardType? = null,
    /** A calculation for the current cards is running; [result] may hold a partial snapshot. */
    val isSimulating: Boolean = false,
    val showResetDialog: Boolean = false,
    /**
     * Latest odds for the current cards, one [OddsResult.players] entry per player in order.
     * Cleared the moment any input changes, so it never describes other cards.
     */
    val result: OddsResult? = null,
    /** Why the last calculation failed (e.g. a duplicated card), for display. */
    val error: String? = null,
) {
    /** Today's UI rule: every player has two cards and the board is empty, a flop, turn or river. */
    val canCalculate: Boolean
        get() = players.size >= 2 &&
            players.all { it.cards.size == 2 } &&
            communityCards.size in VALID_BOARD_SIZES &&
            !isSimulating

    private companion object {
        val VALID_BOARD_SIZES = setOf(0, 3, 4, 5)
    }
}

@HiltViewModel
class OddsCalculatorViewModel @Inject constructor(
    private val oddsPreferences: OddsCalculatorPreferences,
    private val engine: OddsEngine,
    private val settings: OddsSettings,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OddsCalculatorUiState())
    val uiState: StateFlow<OddsCalculatorUiState> = _uiState.asStateFlow()

    private var calculation: Job? = null

    /** Bumped on every input change and calculation start; late snapshots from older runs are dropped. */
    private var generation = 0

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
            is OddsCalculatorIntent.Calculate -> calculate()
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

    // ------------------------------------------------------------------ calculation

    private fun calculate() {
        val state = _uiState.value
        if (!state.canCalculate) return
        val request = try {
            OddsRequest(
                seats = state.players.map { player -> Seat(player.cards.map(::toEngineCard)) },
                board = state.communityCards.map(::toEngineCard),
            )
        } catch (e: IllegalArgumentException) {
            _uiState.update { it.copy(error = e.message ?: "Unreadable card.") }
            return
        }
        stopCalculation()
        val runId = generation
        _uiState.update { it.copy(isSimulating = true, result = null, error = null) }
        calculation = viewModelScope.launch {
            try {
                engine.calculate(request, settings).collect { snapshot ->
                    if (runId == generation) {
                        _uiState.update { it.copy(result = snapshot, isSimulating = !snapshot.complete) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OddsInputException) {
                fail(runId, e.message ?: "These cards can't be calculated.")
            } catch (e: Exception) {
                fail(runId, "Couldn't calculate odds: ${e.message ?: e::class.simpleName}")
            }
        }
    }

    private fun fail(runId: Int, message: String) {
        if (runId == generation) {
            _uiState.update { it.copy(isSimulating = false, result = null, error = message) }
        }
    }

    /** Cancels any running calculation; its snapshots will no longer reach the UI. */
    private fun stopCalculation() {
        generation++
        calculation?.cancel()
        calculation = null
    }

    /** Every input change goes through here: stop the old run and drop its numbers at once. */
    private fun onInputsChanged(transform: (OddsCalculatorUiState) -> OddsCalculatorUiState) {
        stopCalculation()
        _uiState.update { transform(it).copy(result = null, error = null, isSimulating = false) }
    }

    private fun toEngineCard(card: PlayingCard): Int = Cards.parse(card.rank + card.suit)

    // ------------------------------------------------------------------ inputs

    private fun loadSavedState() {
        val playerCount = oddsPreferences.getPlayerCount()
        val players = (1..playerCount).map { id ->
            val cardsString = oddsPreferences.getPlayerCards(id)
            val cards = parseCardsFromString(cardsString)
            Player(id = id, name = "Player $id", cards = cards)
        }
        val communityCards = parseCardsFromString(oddsPreferences.getCommunityCards())

        onInputsChanged {
            OddsCalculatorUiState(
                playerCount = playerCount,
                players = players,
                communityCards = communityCards,
            )
        }
    }

    private fun updatePlayerCount(count: Int) {
        if (count == _uiState.value.playerCount) return
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

        onInputsChanged { it.copy(playerCount = count, players = players) }
    }

    private fun addPlayerCard(playerId: Int, cardString: String) {
        val card = parseCardFromString(cardString) ?: return
        val players = _uiState.value.players.map { player ->
            if (player.id == playerId && player.cards.size < 2) {
                val updatedCards = player.cards + card
                oddsPreferences.setPlayerCards(playerId, formatCardsToString(updatedCards))
                player.copy(cards = updatedCards)
            } else {
                player
            }
        }
        onInputsChanged { it.copy(players = players, showCardPicker = false) }
    }

    private fun removePlayerCard(playerId: Int, cardIndex: Int) {
        val players = _uiState.value.players.map { player ->
            if (player.id == playerId) {
                val updatedCards = player.cards.filterIndexed { index, _ -> index != cardIndex }
                oddsPreferences.setPlayerCards(playerId, formatCardsToString(updatedCards))
                player.copy(cards = updatedCards)
            } else {
                player
            }
        }
        onInputsChanged { it.copy(players = players) }
    }

    private fun addCommunityCard(cardString: String) {
        val card = parseCardFromString(cardString) ?: return
        if (_uiState.value.communityCards.size < 5) {
            val updatedCards = _uiState.value.communityCards + card
            oddsPreferences.setCommunityCards(formatCardsToString(updatedCards))
            onInputsChanged { it.copy(communityCards = updatedCards, showCardPicker = false) }
        }
    }

    private fun removeCommunityCard(cardIndex: Int) {
        val updatedCards = _uiState.value.communityCards.filterIndexed { index, _ -> index != cardIndex }
        oddsPreferences.setCommunityCards(formatCardsToString(updatedCards))
        onInputsChanged { it.copy(communityCards = updatedCards) }
    }

    private fun showCardPickerForPlayer(playerId: Int) {
        _uiState.update { it.copy(showCardPicker = true, selectedPlayerForCard = playerId, selectedCardType = CardType.PLAYER_CARD) }
    }

    private fun showCardPickerForCommunity() {
        _uiState.update { it.copy(showCardPicker = true, selectedCardType = CardType.COMMUNITY_CARD) }
    }

    private fun handleCardSelected(cardString: String) {
        parseCardFromString(cardString) ?: return

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
