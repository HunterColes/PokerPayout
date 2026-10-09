package com.huntercoles.pokerpayout.tools.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.core.design.components.SnackbarController
import com.huntercoles.pokerpayout.core.preferences.PlayerNamesProvider
import com.huntercoles.pokerpayout.core.time.TimeSource
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.shotclock.Countdown
import com.huntercoles.pokerpayout.tools.shotclock.ShotClockAlerts
import com.huntercoles.pokerpayout.tools.shotclock.ShotClockPhase
import com.huntercoles.pokerpayout.tools.shotclock.ShotClockStore
import com.huntercoles.pokerpayout.tools.shotclock.ShotClockTiming
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MILLIS = 1_000L

/** A player's time bank: the cards they have left of [ShotClockUiState.cardsEach]. */
data class TimeBankPlayer(val name: String, val cardsLeft: Int)

/**
 * The shot clock screen's state.
 *
 * @property seconds the time to act for a new decision (30, 45 or 60).
 * @property countdown the decision on the clock, on the monotonic clock.
 * @property leftMillis the time left at the last look (a look each time the seconds shown change).
 * @property phase where the decision stands at that look.
 * @property flashes grows with each warning that flashes the face (Flash the clock in Tools).
 */
data class ShotClockUiState(
    val seconds: Int = ShotClockStore.DEFAULT_SECONDS,
    val countdown: Countdown = Countdown.ready(seconds * MILLIS),
    val leftMillis: Long = countdown.totalMillis,
    val phase: ShotClockPhase = ShotClockPhase.Ready,
    val cardsEach: Int = ShotClockStore.DEFAULT_CARDS_EACH,
    val players: List<TimeBankPlayer> = emptyList(),
    val flashes: Int = 0,
) {
    /** The big number: whole seconds left, rounded up. */
    val shownSeconds: Int get() = ShotClockTiming.shownSeconds(leftMillis)

    /** How much of the decision's time is left, 0 to 1, for the ring. */
    val progress: Float
        get() = if (countdown.totalMillis <= 0) 0f else (leftMillis.toFloat() / countdown.totalMillis).coerceIn(0f, 1f)

    /** The last ten seconds of a running or paused decision. */
    val lowOnTime: Boolean get() = phase != ShotClockPhase.Ready && leftMillis <= ShotClockTiming.WARNING_MILLIS

    /** A card can be played once a decision is on the clock, even just after time ran out. */
    val canPlayCards: Boolean get() = countdown.started

    /** Whether anyone has played a card, so there is something to give back. */
    val anyCardsPlayed: Boolean get() = players.any { it.cardsLeft < cardsEach }
}

sealed interface ShotClockIntent {
    /** The big tap target: a new decision, at the full time, counting. Starts the first one too. */
    data object NextDecision : ShotClockIntent
    data object Pause : ShotClockIntent
    data object Resume : ShotClockIntent

    /** Back to full and waiting. */
    data object Reset : ShotClockIntent
    data class SetSeconds(val seconds: Int) : ShotClockIntent
    data class SetCardsEach(val cards: Int) : ShotClockIntent

    /** [player] plays a time-bank card: 30 more seconds on this decision. */
    data class PlayCard(val player: Int) : ShotClockIntent

    /** Everyone gets their cards back (a new level, a new game), with Undo. */
    data object GiveCardsBack : ShotClockIntent
}

/**
 * The shot clock: a countdown for each decision, a warning at ten seconds and at zero, and
 * time-bank cards for the players who need longer.
 *
 * Like the tournament clock it never counts ticks: the decision is a [Countdown] on the monotonic
 * [TimeSource], and each look (once a second, when the number changes) works the time left out
 * from it. It touches nothing of the tournament clock: its own settings ([ShotClockStore]), its own
 * beeper ([ShotClockAlerts]), and it stops when the screen is closed.
 */
@HiltViewModel
class ShotClockViewModel @Inject constructor(
    private val store: ShotClockStore,
    private val bankNames: PlayerNamesProvider,
    private val time: TimeSource,
    private val alerts: ShotClockAlerts,
    private val snackbars: SnackbarController,
    private val messages: ShotClockMessages,
) : ViewModel() {

    private val names: List<String> = bankNames.currentNames().ifEmpty { List(DEFAULT_PLAYERS) { messages.defaultName(it + 1) } }
    private var used: Map<String, Int> = store.used()

    private val _uiState = MutableStateFlow(
        store.seconds().let { seconds ->
            ShotClockUiState(seconds = seconds, cardsEach = store.cardsEach()).withPlayers()
        },
    )
    val uiState: StateFlow<ShotClockUiState> = _uiState.asStateFlow()

    private var ticker: Job? = null
    private var undo: Job? = null

    /** Signed milliseconds left at the last look, to tell which warnings the next look passes. */
    private var lastLook: Long = _uiState.value.leftMillis

    fun acceptIntent(intent: ShotClockIntent) {
        when (intent) {
            ShotClockIntent.NextDecision -> setCountdown(Countdown.startedAt(_uiState.value.seconds * MILLIS, now()))
            ShotClockIntent.Pause -> setCountdown(_uiState.value.countdown.pausedAt(now()))
            ShotClockIntent.Resume -> setCountdown(_uiState.value.countdown.resumedAt(now()))
            ShotClockIntent.Reset -> setCountdown(Countdown.ready(_uiState.value.seconds * MILLIS))
            is ShotClockIntent.SetSeconds -> setSeconds(intent.seconds)
            is ShotClockIntent.SetCardsEach -> setCardsEach(intent.cards)
            is ShotClockIntent.PlayCard -> playCard(intent.player)
            ShotClockIntent.GiveCardsBack -> giveCardsBack()
        }
    }

    private fun now(): Long = time.elapsedRealtimeMillis()

    /** Puts [countdown] on the clock, takes a fresh look, and counts while it runs. */
    private fun setCountdown(countdown: Countdown) {
        ticker?.cancel()
        _uiState.update { it.copy(countdown = countdown) }
        lastLook = look()
        if (countdown.running && lastLook > 0) ticker = viewModelScope.launch { count() }
    }

    /** One look each time the seconds shown change, giving any warning passed, until time is up or it stops. */
    private suspend fun count() {
        while (lastLook > 0) {
            delay(ShotClockTiming.untilNextSecond(lastLook))
            val signed = look()
            ShotClockTiming.crossed(lastLook, signed)?.let { cue ->
                if (alerts.alert(cue)) _uiState.update { it.copy(flashes = it.flashes + 1) }
            }
            lastLook = signed
        }
    }

    /** Shows the time left now and where the decision stands; returns the signed time left. */
    private fun look(): Long {
        val now = now()
        val countdown = _uiState.value.countdown
        val signed = countdown.signedLeftAt(now)
        _uiState.update { it.copy(leftMillis = signed.coerceAtLeast(0), phase = countdown.phaseAt(now)) }
        return signed
    }

    private fun setSeconds(seconds: Int) {
        if (seconds !in ShotClockStore.PRESETS) return
        store.setSeconds(seconds)
        _uiState.update { it.copy(seconds = seconds) }
        // A new time shows at once while nothing is on the clock; a decision under way keeps its own.
        if (_uiState.value.phase == ShotClockPhase.Ready) setCountdown(Countdown.ready(seconds * MILLIS))
    }

    private fun setCardsEach(cards: Int) {
        val wanted = cards.coerceIn(0, ShotClockStore.MAX_CARDS_EACH)
        store.setCardsEach(wanted)
        _uiState.update { it.copy(cardsEach = wanted).withPlayers() }
    }

    private fun playCard(player: Int) {
        val state = _uiState.value
        val target = state.players.getOrNull(player) ?: return
        if (!state.canPlayCards || target.cardsLeft <= 0) return
        setUsed(used + (target.name to (used[target.name] ?: 0) + 1))
        setCountdown(state.countdown.extendedAt(now(), ShotClockStore.CARD_SECONDS * MILLIS))
    }

    private fun giveCardsBack() {
        val before = used
        if (!_uiState.value.anyCardsPlayed) return
        setUsed(emptyMap())
        undo?.cancel()
        undo = viewModelScope.launch {
            if (snackbars.showUndo(messages.cardsBack, messages.undo) && used.isEmpty()) setUsed(before)
        }
    }

    private fun setUsed(next: Map<String, Int>) {
        used = next
        store.setUsed(next)
        _uiState.update { it.withPlayers() }
    }

    private fun ShotClockUiState.withPlayers(): ShotClockUiState =
        copy(players = names.map { name -> TimeBankPlayer(name, (cardsEach - (used[name] ?: 0)).coerceAtLeast(0)) })

    override fun onCleared() {
        alerts.release()
    }

    companion object {
        /** A full table, when the Bank has nobody. */
        const val DEFAULT_PLAYERS = 9
    }
}

/** The strings the shot clock's ViewModel needs itself: its snackbar and default names. */
class ShotClockMessages @Inject constructor(@ApplicationContext private val context: Context) {
    val cardsBack: String get() = context.getString(R.string.shot_clock_snackbar_cards_back)
    val undo: String get() = context.getString(R.string.shot_clock_undo)

    /** "Player 3", for a table nobody has named in the Bank. */
    fun defaultName(number: Int): String = context.getString(R.string.shot_clock_default_player, number)
}
