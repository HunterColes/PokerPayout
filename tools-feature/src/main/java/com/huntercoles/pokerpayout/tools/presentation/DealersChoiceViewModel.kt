package com.huntercoles.pokerpayout.tools.presentation

import androidx.lifecycle.ViewModel
import com.huntercoles.pokerpayout.tools.dealers.BuiltInGame
import com.huntercoles.pokerpayout.tools.dealers.DealersChoiceStore
import com.huntercoles.pokerpayout.tools.dealers.GameChoice
import com.huntercoles.pokerpayout.tools.dealers.GameWheel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.random.Random

/** Where the wheel's random draws come from. Tests fix them; the app uses a fresh one each spin. */
fun interface WheelSeeds {
    fun next(): Long
}

/**
 * Dealer's choice's state.
 *
 * @property games every game the host can put on the wheel: the app's, then the house games.
 * @property pickId the last game picked, or null before the first spin.
 * @property landing where in its slice the wheel stops (-0.35 to 0.35 of a slice from its middle).
 * @property spins grows with each spin, so the screen turns the wheel once for each.
 */
data class DealersChoiceUiState(
    val games: List<GameChoice> = emptyList(),
    val pickId: String? = null,
    val landing: Float = 0f,
    val spins: Int = 0,
) {
    /** The games on the wheel, in the order they sit round it. */
    val wheel: List<GameChoice> get() = games.filter { it.onWheel }

    val pick: GameChoice? get() = games.firstOrNull { it.id == pickId }

    /** The pick's slice on the wheel, or -1 when it isn't on the wheel (taken off since). */
    val pickIndex: Int get() = wheel.indexOfFirst { it.id == pickId }

    val canSpin: Boolean get() = wheel.size >= GameWheel.MIN_GAMES

    val houseGames: List<GameChoice> get() = games.filter { it.game == null }

    val canAddHouseGame: Boolean get() = houseGames.size < DealersChoiceViewModel.MAX_HOUSE_GAMES
}

sealed interface DealersChoiceIntent {
    data object Spin : DealersChoiceIntent
    data class SetOnWheel(val id: String, val on: Boolean) : DealersChoiceIntent

    /** A game of the host's own, on the wheel at once. A name already there is ignored. */
    data class AddHouseGame(val name: String) : DealersChoiceIntent
    data class RemoveHouseGame(val id: String) : DealersChoiceIntent
}

/**
 * Dealer's choice: a wheel of games, spun to pick the next one. The host chooses which of the
 * app's games are on it and adds house games of their own; each spin is seeded from [WheelSeeds]
 * and never picks the game just played twice running. Everything is saved in [DealersChoiceStore].
 * The spin itself is the screen's: the pick is made here at once, and the wheel turns to it.
 */
@HiltViewModel
class DealersChoiceViewModel @Inject constructor(
    private val store: DealersChoiceStore,
    private val seeds: WheelSeeds,
) : ViewModel() {

    private val _uiState = MutableStateFlow(load())
    val uiState: StateFlow<DealersChoiceUiState> = _uiState.asStateFlow()

    private fun load(): DealersChoiceUiState {
        val saved = store.onWheel()
        val builtIns = BuiltInGame.entries.map { game ->
            GameChoice.builtIn(game, onWheel = saved?.contains(game.id) ?: game.onWheelAtFirst)
        }
        val house = store.houseGames().map { name ->
            GameChoice.house(name, onWheel = saved?.contains(GameChoice.houseId(name)) ?: true)
        }
        val games = builtIns + house
        return DealersChoiceUiState(games = games, pickId = store.lastPick()?.takeIf { id -> games.any { it.id == id } })
    }

    fun acceptIntent(intent: DealersChoiceIntent) {
        when (intent) {
            DealersChoiceIntent.Spin -> spin()
            is DealersChoiceIntent.SetOnWheel -> setGames(
                _uiState.value.games.map { if (it.id == intent.id) it.copy(onWheel = intent.on) else it },
            )
            is DealersChoiceIntent.AddHouseGame -> addHouseGame(intent.name)
            is DealersChoiceIntent.RemoveHouseGame -> removeHouseGame(intent.id)
        }
    }

    private fun spin() {
        val state = _uiState.value
        if (!state.canSpin) return
        val wheel = state.wheel
        val stop = GameWheel.spin(wheel, state.pickId, Random(seeds.next()))
        val picked = wheel[stop.index].id
        store.setLastPick(picked)
        _uiState.update { it.copy(pickId = picked, landing = stop.landing, spins = it.spins + 1) }
    }

    private fun addHouseGame(name: String) {
        val trimmed = name.trim().take(MAX_NAME_LENGTH)
        val state = _uiState.value
        if (trimmed.isEmpty() || !state.canAddHouseGame) return
        val id = GameChoice.houseId(trimmed)
        if (state.houseGames.any { it.id == id }) return
        setGames(state.games + GameChoice.house(trimmed, onWheel = true))
    }

    private fun removeHouseGame(id: String) {
        val games = _uiState.value.games
        if (games.none { it.id == id && it.game == null }) return
        setGames(games.filterNot { it.id == id })
        if (_uiState.value.pickId == id) {
            store.setLastPick(null)
            _uiState.update { it.copy(pickId = null) }
        }
    }

    /** Shows and saves [games]: the wheel and the house games. */
    private fun setGames(games: List<GameChoice>) {
        store.setOnWheel(games.filter { it.onWheel }.map { it.id }.toSet())
        store.setHouseGames(games.mapNotNull { it.houseName })
        _uiState.update { it.copy(games = games) }
    }

    companion object {
        /** House games a wheel can hold, on top of the app's. */
        const val MAX_HOUSE_GAMES = 8

        /** Long enough for "Kings and Little Ones"; short enough for a slice of the wheel. */
        const val MAX_NAME_LENGTH = 24
    }
}
