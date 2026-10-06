package com.huntercoles.pokerpayout.tournament.presentation.composable

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.audio.SoundManager
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.time.TimeSource
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSegment
import com.huntercoles.pokerpayout.tournament.presentation.TimerIntent
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import com.huntercoles.pokerpayout.tournament.presentation.TimerViewModel
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigUiState
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigViewModel
import io.mockk.mockk

/**
 * The mockups' game night, built through the real ViewModels over Robolectric's in-memory
 * preferences: 9 players, $40 buy-in, $5 food, $5 bounty, $40 rebuy until the end of level 4, $10
 * add-on; 3 hours of 20-minute levels from a 25 chip to 5,000, a 10-minute break every 4 levels
 * with a note, a big-blind ante from level 5. Two players are out, one rebuy and three add-ons are
 * in the Bank.
 *
 * Clock states are the ViewModel's own state moved to a moment of the night ([at]): level 6 with
 * 12:41 left is the one every mockup shows. Nothing reads the real clock.
 */
internal class TournamentFixture(private val store: ViewModelStore) {
    private val context: Context = ApplicationProvider.getApplicationContext()
    val tournamentPreferences: TournamentPreferences
    val timerPreferences: TimerPreferences
    val bankPreferences: BankPreferences
    val audioPreferences: AudioPreferences

    init {
        listOf("tournament_prefs", "timer_prefs", "bank_prefs", "audio_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        tournamentPreferences = TournamentPreferences(context)
        timerPreferences = TimerPreferences(context)
        bankPreferences = BankPreferences(context)
        audioPreferences = AudioPreferences(context)
        tournamentPreferences.setPlayerCount(PLAYERS)
        tournamentPreferences.setBuyIn(40.0)
        tournamentPreferences.setFoodPerPlayer(5.0)
        tournamentPreferences.setBountyPerPlayer(5.0)
        tournamentPreferences.setRebuyAmount(40.0)
        tournamentPreferences.setAddOnAmount(10.0)
        tournamentPreferences.setRebuyUntilLevel(4)
        tournamentPreferences.setSmallestChip(25)
        timerPreferences.setBreakEveryLevels(4)
        timerPreferences.setBreakLengthMinutes(10)
        timerPreferences.setBreakMessage("Last orders at the bar")
        timerPreferences.setBigBlindAnteFromLevel(5)
        bankPreferences.saveEliminationOrder(listOf(9, 8))
        bankPreferences.savePlayerRebuys(2, 1)
        listOf(1, 2, 3).forEach { bankPreferences.savePlayerAddons(it, 1) }
    }

    fun setupState(): TournamentConfigUiState = viewModel {
        TournamentConfigViewModel(CalculatePayoutsUseCase(), tournamentPreferences, timerPreferences, bankPreferences)
    }.uiState.value

    fun timerViewModel(): TimerViewModel = viewModel { newTimer() }

    private fun newTimer(): TimerViewModel {
        val sound = mockk<SoundManager>(relaxed = true)
        return TimerViewModel(timerPreferences, tournamentPreferences, bankPreferences, sound, StillClock, audioPreferences)
    }

    /** Before the start: the ready ticket's game. */
    val ready: TimerUiState by lazy { timerViewModel().uiState.value }

    /** The same setup with 25-minute levels, which 3 hours can't be cut into. */
    fun invalid(): TimerUiState {
        val viewModel = viewModel(key = "invalid") { newTimer() }
        viewModel.acceptIntent(TimerIntent.UpdateRoundLength(25))
        val state = viewModel.uiState.value
        viewModel.acceptIntent(TimerIntent.UpdateRoundLength(20))
        return state
    }

    /** The clock at [elapsedSeconds] of play, started, [running] or paused. */
    fun at(elapsedSeconds: Int, running: Boolean = true): TimerUiState {
        val endsAt = (ready.timeline.regularEndSeconds - elapsedSeconds).takeIf { it > 0 }?.let { WALL_NOW + it * MILLIS }
        return ready.copy(elapsedSeconds = elapsedSeconds, hasTimerStarted = true, isRunning = running, endsAtWallClock = endsAt)
    }

    /** Level [number] (1-based) with [secondsLeft] to go. */
    fun level(number: Int, secondsLeft: Int, running: Boolean = true): TimerUiState {
        val segment = ready.timeline.levels[number - 1]
        return at(segment.endSeconds - secondsLeft, running)
    }

    /** Level 6 with 12:41 left: the mockups' moment. */
    val running: TimerUiState get() = level(6, LEVEL_SIX_LEFT)

    val breaks: List<BreakSegment> get() = ready.timeline.segments.filterIsInstance<BreakSegment>()

    /** On [segment] with [secondsLeft] to go. */
    fun onBreak(segment: BreakSegment, secondsLeft: Int = BREAK_LEFT): TimerUiState = at(segment.endSeconds - secondsLeft)

    val overtime: TimerUiState get() = at(ready.timeline.regularEndSeconds + OVERTIME_INTO)

    val finished: TimerUiState
        get() = ready.copy(
            elapsedSeconds = ready.timeline.endSeconds,
            hasTimerStarted = true,
            isRunning = false,
            isFinished = true,
            endsAtWallClock = null,
        )

    /** A ViewModel in [store], so clearing the store cancels its coroutines. */
    private inline fun <reified T : ViewModel> viewModel(key: String = T::class.java.name, crossinline build: () -> T): T {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <V : ViewModel> create(modelClass: Class<V>): V = build() as V
        }
        return ViewModelProvider(store, factory)[key, T::class.java]
    }

    /** A clock that never moves. */
    private object StillClock : TimeSource {
        override fun elapsedRealtimeMillis() = 1_000_000L
        override fun wallClockMillis() = WALL_NOW
        override fun bootCount() = 1
    }

    companion object {
        const val PLAYERS = 9
        const val LEVEL_SIX_LEFT = 12 * 60 + 41
        const val BREAK_LEFT = 7 * 60 + 32
        const val OVERTIME_INTO = 5 * 60
        const val MILLIS = 1_000L

        /** 21:47 UTC, 5 October 2026: the night of the mockups. */
        const val WALL_NOW = 1_791_236_820_000L
    }
}
