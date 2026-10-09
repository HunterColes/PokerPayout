package com.huntercoles.pokerpayout.tournament.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.audio.SoundManager
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.testing.expect
import com.huntercoles.pokerpayout.core.testing.forAll
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockCues
import com.huntercoles.pokerpayout.tournament.domain.clock.CueVibrator
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The tournament clock survives process death (PP-015), as a property over random sessions: start,
 * pause, levels skipped forward and back, minutes nudged, breaks ended early, colour-ups marked,
 * play time with ticks and the phone asleep without them, and the process killed at random points,
 * sometimes for a long while.
 *
 * Each session is played twice from the same start. In one, the process dies at the marked steps
 * and the app is opened again after the gap; in the other the phone just sleeps for the same gap
 * with the app alive. A process death must be invisible: a second after each such step, and at the
 * end, the two clocks agree on the second played, the level or break, running or paused, finished,
 * and the colour-ups done. Virtual time and a fake clock only (TimerViewModelTest.FakeTimeSource).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ClockRestoreTest {

    private val testDispatcher = StandardTestDispatcher()
    private val clock = TimerViewModelTest.FakeTimeSource(testDispatcher.scheduler)
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val soundManager: SoundManager = mockk(relaxed = true)
    private var store = ViewModelStore()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    /** One thing that happens to the clock; [kind] [DIE] and [DIE_NOW] are process deaths. */
    private data class Step(val kind: Int, val amount: Int)

    private data class Session(val breaks: Boolean, val steps: List<Step>)

    /** What the host sees, a second after a step. */
    private data class Seen(
        val step: Int,
        val elapsedSeconds: Int,
        val segment: Int,
        val running: Boolean,
        val finished: Boolean,
        val started: Boolean,
        val colourUpsDone: Set<Int>,
    )

    @Test
    fun `a process death is invisible to the clock, however long the app was gone`() =
        forAll(seed = 2026_1008_81L, iterations = 40, gen = sessions) { session ->
            val withDeaths = play(session, deaths = true)
            val alive = play(session, deaths = false)
            val first = withDeaths.indices.firstOrNull { withDeaths[it] != alive[it] }
            expect(first == null) {
                "after step ${first?.let { withDeaths[it].step }}: with a process death the clock shows\n" +
                    "${first?.let { withDeaths[it] }}\nbut alive it shows\n${first?.let { alive[it] }}"
            }
        }

    /** Plays [session] from a fresh install and returns what was on the clock after each step. */
    private fun play(session: Session, deaths: Boolean): List<Seen> {
        listOf("tournament_prefs", "timer_prefs", "bank_prefs", "audio_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        var viewModel = launch()
        if (session.breaks) {
            viewModel.send(TimerIntent.UpdateBreakEvery(BREAK_EVERY), TimerIntent.UpdateBreakLength(BREAK_MINUTES))
        }
        val seen = session.steps.mapIndexed { index, step ->
            when (step.kind) {
                DIE, DIE_NOW -> {
                    val gapMillis = if (step.kind == DIE_NOW) 0L else step.amount * GAP_UNIT_MILLIS
                    if (deaths) {
                        store.clear()
                        clock.sleep(gapMillis)
                        viewModel = launch()
                    } else {
                        clock.sleep(gapMillis)
                    }
                }
                else -> viewModel.perform(step)
            }
            advanceSeconds(1)
            viewModel.uiState.value.let { state ->
                Seen(
                    step = index,
                    elapsedSeconds = state.elapsedSeconds,
                    segment = state.currentSegmentIndex,
                    running = state.isRunning,
                    finished = state.isFinished,
                    started = state.hasTimerStarted,
                    colourUpsDone = state.colorUpDoneAfterLevels,
                )
            }
        }
        store.clear()
        return seen
    }

    private fun TimerViewModel.perform(step: Step) {
        when (step.kind) {
            0, 1 -> send(TimerIntent.ToggleTimer)
            2 -> send(TimerIntent.NextBlindLevel)
            3 -> send(TimerIntent.PreviousBlindLevel)
            4 -> send(TimerIntent.NudgeMinutes(if (step.amount % 2 == 0) 1 else -1))
            5 -> send(TimerIntent.EndBreakNow)
            6 -> send(TimerIntent.MarkColorUpDone(step.amount % 2 == 0))
            7, 8 -> advanceSeconds(step.amount.coerceAtMost(MAX_PLAY_SECONDS))
            else -> {
                // The phone sleeps with the app alive: time passes, no tick runs
                clock.sleep(step.amount * GAP_UNIT_MILLIS)
            }
        }
    }

    /** The app opened: new preference objects read from what is saved, and a new ViewModel. */
    private fun launch(): TimerViewModel {
        store = ViewModelStore()
        val audio = AudioPreferences(context)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TimerViewModel(
                    TimerPreferences(context),
                    TournamentPreferences(context),
                    BankPreferences(context),
                    ClockCues(soundManager, audio, CueVibrator { }, clock),
                    clock,
                    audio,
                    FakeChipSets(),
                    NineSeats,
                ) as T
        }
        // runCurrent, not advanceUntilIdle: a restored running clock ticks forever
        return ViewModelProvider(store, factory)[TimerViewModel::class.java].also { testDispatcher.scheduler.runCurrent() }
    }

    private fun TimerViewModel.send(vararg intents: TimerIntent) {
        intents.forEach { acceptIntent(it) }
        testDispatcher.scheduler.runCurrent()
    }

    private fun advanceSeconds(seconds: Int) {
        testDispatcher.scheduler.advanceTimeBy(seconds * MILLIS)
        testDispatcher.scheduler.runCurrent()
    }

    private companion object {
        const val DIE = 10
        const val DIE_NOW = 11
        const val BREAK_EVERY = 3
        const val BREAK_MINUTES = 10
        const val MAX_PLAY_SECONDS = 180
        const val GAP_UNIT_MILLIS = 30_000L
        const val MILLIS = 1_000L

        val steps = Arb.bind(Arb.int(0..DIE_NOW), Arb.int(0..240), ::Step)
        val sessions = Arb.bind(Arb.boolean(), Arb.list(steps, 1..25), ::Session)
    }
}
