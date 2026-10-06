package com.huntercoles.pokerpayout.tournament.domain.clock

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.audio.SoundManager
import com.huntercoles.pokerpayout.core.domain.model.ClockStatus
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.time.TimeSource
import com.huntercoles.pokerpayout.tournament.presentation.FakeChipSets
import com.huntercoles.pokerpayout.tournament.presentation.TimerIntent
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import com.huntercoles.pokerpayout.tournament.presentation.TimerViewModel
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The Bank reads the clock's level and breaks through [SavedClockStatusProvider], from what the clock
 * saves. It must agree with the clock itself ([TimerViewModel]) everywhere: levels, breaks, overtime,
 * paused or running, after a jump, and after time simply passes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SavedClockStatusProviderTest {

    private val dispatcher = StandardTestDispatcher()
    private val time = StepTime()
    private val store = ViewModelStore()
    private lateinit var timerPreferences: TimerPreferences
    private lateinit var tournamentPreferences: TournamentPreferences
    private lateinit var bankPreferences: BankPreferences

    /** A clock that only moves when told to. */
    private class StepTime : TimeSource {
        var now = 5_000_000L
        override fun elapsedRealtimeMillis() = now
        override fun wallClockMillis() = 1_760_000_000_000L + now
        override fun bootCount() = 3
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context: Context = ApplicationProvider.getApplicationContext()
        listOf("tournament_prefs", "timer_prefs", "bank_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        tournamentPreferences = TournamentPreferences(context).apply { setPlayerCount(9) }
        timerPreferences = TimerPreferences(context)
        bankPreferences = BankPreferences(context)
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun clock(): TimerViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TimerViewModel(
                    timerPreferences,
                    tournamentPreferences,
                    bankPreferences,
                    mockk<SoundManager>(relaxed = true),
                    time,
                    AudioPreferences(ApplicationProvider.getApplicationContext()),
                    FakeChipSets(),
                ) as T
        }
        return ViewModelProvider(store, factory)[TimerViewModel::class.java].also { dispatcher.scheduler.runCurrent() }
    }

    private fun provider() = SavedClockStatusProvider(timerPreferences, tournamentPreferences, time)

    /** The status the clock's own state implies. */
    private fun TimerUiState.expected() = ClockStatus(
        started = hasTimerStarted,
        level = if (hasTimerStarted) currentBlindLevelIndex + 1 else 1,
        onBreak = hasTimerStarted && isOnBreak,
        breakAfterLevels = timeline.segments.filterIsInstance<BreakSegment>()
            .map { it.afterLevel }
            .takeIf { hasTimerStarted }
            .orEmpty(),
        finished = isFinished
    )

    private fun TimerViewModel.step(intent: TimerIntent) {
        acceptIntent(intent)
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun beforeTheStartTheClockIsNotStarted() {
        clock()
        assertEquals(ClockStatus.NOT_STARTED, provider().current())
    }

    @Test
    fun everyLevelAndBreakMatchesTheClockWhenJumpingThroughTheNight() {
        val viewModel = clock()
        viewModel.step(TimerIntent.UpdateBreakEvery(4))
        viewModel.step(TimerIntent.ToggleTimer)
        val provider = provider()
        var checked = 0
        while (viewModel.uiState.value.canGoForward) {
            assertEquals(
                "segment ${viewModel.uiState.value.currentSegmentIndex}",
                viewModel.uiState.value.expected(),
                provider.current()
            )
            viewModel.step(TimerIntent.NextBlindLevel)
            checked++
        }
        assertEquals(viewModel.uiState.value.expected(), provider.current())
        // 9 levels, 2 breaks, then the overtime levels
        assertTrue("checked $checked segments", checked >= 11)
        assertEquals(listOf(4, 8), provider.current().breakAfterLevels)
    }

    @Test
    fun aRunningClockMovesOnWithTimeAlone() {
        val viewModel = clock()
        viewModel.step(TimerIntent.UpdateBreakEvery(4))
        viewModel.step(TimerIntent.ToggleTimer)
        val provider = provider()
        assertEquals(1, provider.current().level)

        // Four 20-minute levels later the break after level 4 is on
        time.now += 4 * 20 * 60_000L + 30_000L
        with(provider.current()) {
            assertEquals(4, level)
            assertTrue(onBreak)
        }
        // Then level 5
        time.now += 10 * 60_000L
        with(provider.current()) {
            assertEquals(5, level)
            assertFalse(onBreak)
        }
    }

    @Test
    fun aPausedClockStaysPut() {
        val viewModel = clock()
        viewModel.step(TimerIntent.ToggleTimer)
        viewModel.step(TimerIntent.NextBlindLevel)
        viewModel.step(TimerIntent.ToggleTimer) // pause on level 2
        time.now += 3 * 60 * 60_000L
        assertEquals(2, provider().current().level)
    }

    @Test
    fun theFlowEmitsTheCurrentStatus() = runBlocking {
        val viewModel = clock()
        viewModel.step(TimerIntent.ToggleTimer)
        viewModel.step(TimerIntent.NextBlindLevel)
        assertEquals(2, provider().status.first().level)
    }

    @Test
    fun aResetClockIsNotStartedAgain() {
        val viewModel = clock()
        viewModel.step(TimerIntent.ToggleTimer)
        viewModel.step(TimerIntent.NextBlindLevel)
        viewModel.step(TimerIntent.ResetTimer)
        assertEquals(ClockStatus.NOT_STARTED, provider().current())
    }
}
