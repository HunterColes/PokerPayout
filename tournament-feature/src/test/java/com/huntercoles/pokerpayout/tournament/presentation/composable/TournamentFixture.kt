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
import com.huntercoles.pokerpayout.core.preferences.ChipCalculatorPreferences
import com.huntercoles.pokerpayout.core.preferences.SavedChipSetProvider
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.time.TimeSource
import com.huntercoles.pokerpayout.core.utils.ChipColour
import com.huntercoles.pokerpayout.core.utils.ChipInventory
import com.huntercoles.pokerpayout.core.utils.InventoryChip
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSegment
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockCues
import com.huntercoles.pokerpayout.tournament.domain.clock.CueVibrator
import com.huntercoles.pokerpayout.tournament.presentation.NineSeats
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
 * 12:41 left is the one every mockup shows. Nothing reads the real clock. The chip set is the real
 * one over the same preferences: not set up, so the color-ups use a common home set's chips, until
 * [withChipSet] sets one up.
 */
internal class TournamentFixture(private val store: ViewModelStore) {
    private val context: Context = ApplicationProvider.getApplicationContext()
    val tournamentPreferences: TournamentPreferences
    val timerPreferences: TimerPreferences
    val bankPreferences: BankPreferences
    val audioPreferences: AudioPreferences
    private val chipPreferences: ChipCalculatorPreferences

    init {
        listOf("tournament_prefs", "timer_prefs", "bank_prefs", "audio_prefs", "chip_calculator_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        tournamentPreferences = TournamentPreferences(context)
        timerPreferences = TimerPreferences(context)
        bankPreferences = BankPreferences(context)
        audioPreferences = AudioPreferences(context)
        chipPreferences = ChipCalculatorPreferences(context, tournamentPreferences)
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
        val cues = ClockCues(mockk<SoundManager>(relaxed = true), audioPreferences, CueVibrator { }, StillClock)
        return TimerViewModel(
            timerPreferences,
            tournamentPreferences,
            bankPreferences,
            cues,
            StillClock,
            audioPreferences,
            SavedChipSetProvider(chipPreferences),
            NineSeats,
        )
    }

    /**
     * The ready ticket with [inventory] set up in Tools → Chip set (PP-091 #9): its color-ups use those
     * chips. The chip set stays set up for the rest of this fixture.
     */
    fun withChipSet(inventory: ChipInventory = OWN_SET): TimerUiState {
        chipPreferences.setInventory(inventory)
        return viewModel(key = "chip set") { newTimer() }.uiState.value
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

    /** The clock at [elapsedSeconds] of play, started, [running] or paused; from [base], the ready ticket. */
    fun at(elapsedSeconds: Int, running: Boolean = true, base: TimerUiState = ready): TimerUiState {
        val endsAt = (base.timeline.regularEndSeconds - elapsedSeconds).takeIf { it > 0 }?.let { WALL_NOW + it * MILLIS }
        return base.copy(elapsedSeconds = elapsedSeconds, hasTimerStarted = true, isRunning = running, endsAtWallClock = endsAt)
    }

    /** Level [number] (1-based) with [secondsLeft] to go. */
    fun level(number: Int, secondsLeft: Int, running: Boolean = true): TimerUiState {
        val segment = ready.timeline.levels[number - 1]
        return at(segment.endSeconds - secondsLeft, running)
    }

    /** Level 6 with 12:41 left: the mockups' moment. */
    val running: TimerUiState get() = level(6, LEVEL_SIX_LEFT)

    val breaks: List<BreakSegment> get() = ready.timeline.segments.filterIsInstance<BreakSegment>()

    /** On [segment] with [secondsLeft] to go, from [base]. */
    fun onBreak(segment: BreakSegment, secondsLeft: Int = BREAK_LEFT, base: TimerUiState = ready): TimerUiState =
        at(segment.endSeconds - secondsLeft, base = base)

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

        /**
         * A set whose colours aren't the standard ones (PP-091 #9): white 25s, red 100s, green 500s and
         * black 1,000s, so a break drawn from it can't be mistaken for a common home set's.
         */
        val OWN_SET: ChipInventory = ChipInventory.of(
            listOf(
                InventoryChip(ChipColour.White, 25, 200),
                InventoryChip(ChipColour.Red, 100, 200),
                InventoryChip(ChipColour.Green, 500, 100),
                InventoryChip(ChipColour.Black, 1_000, 100),
            )
        )
    }
}
