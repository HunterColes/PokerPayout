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
import com.huntercoles.pokerpayout.core.time.ClockAnchor
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockCommand
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockCommands
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockCues
import com.huntercoles.pokerpayout.tournament.domain.clock.CueVibrator
import com.huntercoles.pokerpayout.tournament.domain.clock.SavedClock
import com.huntercoles.pokerpayout.tournament.domain.clock.SilentCue
import com.huntercoles.pokerpayout.tournament.live.LiveClockController
import com.huntercoles.pokerpayout.tournament.presentation.TimerViewModelTest.FakeTimeSource
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
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
 * The live clock notification's Pause and Resume (PP-081) against the real clock, on the virtual time
 * and fake clocks of [TimerViewModelTest]. The saved anchor is the one clock: a command from the
 * notification must leave exactly what the clock's own button leaves, the clock on screen must follow
 * it at once, and a command made while the app's process was gone must be there when it comes back.
 * Also the quiet cues from the clock itself (PP-083), and that the service hears every change.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LiveClockSyncTest {

    private val testDispatcher = StandardTestDispatcher()
    private val clock = FakeTimeSource(testDispatcher.scheduler)
    private lateinit var context: Context
    private lateinit var timerPreferences: TimerPreferences
    private lateinit var tournamentPreferences: TournamentPreferences
    private lateinit var bankPreferences: BankPreferences
    private lateinit var audioPreferences: AudioPreferences
    private val soundManager: SoundManager = mockk(relaxed = true)
    private val buzzes = mutableListOf<SilentCue>()
    private var store = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        listOf("tournament_prefs", "timer_prefs", "bank_prefs", "audio_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        newPreferences()
        tournamentPreferences.setPlayerCount(10)
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun newPreferences() {
        tournamentPreferences = TournamentPreferences(context)
        timerPreferences = TimerPreferences(context)
        bankPreferences = BankPreferences(context)
        audioPreferences = AudioPreferences(context)
    }

    private fun newViewModel(): TimerViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val cues = ClockCues(soundManager, audioPreferences, CueVibrator { buzzes += it }, clock)
                val viewModel = TimerViewModel(
                    timerPreferences,
                    tournamentPreferences,
                    bankPreferences,
                    cues,
                    clock,
                    audioPreferences,
                    FakeChipSets(),
                )
                return viewModel as T
            }
        }
        return ViewModelProvider(store, factory)[TimerViewModel::class.java]
            .also { testDispatcher.scheduler.runCurrent() }
    }

    /** The notification's button, as its broadcast receiver carries it out. */
    private fun notificationButton(command: ClockCommand): Boolean =
        ClockCommands(timerPreferences, tournamentPreferences, clock, SavedClock(timerPreferences, tournamentPreferences))
            .perform(command)
            .also { testDispatcher.scheduler.runCurrent() }

    private fun TimerViewModel.send(intent: TimerIntent) {
        acceptIntent(intent)
        testDispatcher.scheduler.runCurrent()
    }

    private fun advanceSeconds(seconds: Int) {
        testDispatcher.scheduler.advanceTimeBy(seconds * 1000L)
        testDispatcher.scheduler.runCurrent()
    }

    private val TimerViewModel.state get() = uiState.value

    /** What a pause leaves behind: the saved clock and the tournament's lock. */
    private data class Saved(val anchor: ClockAnchor?, val locked: Boolean, val running: Boolean)

    private fun saved() = Saved(
        anchor = timerPreferences.getClock(),
        locked = tournamentPreferences.getTournamentLocked(),
        running = timerPreferences.getTimerRunning(),
    )

    @Test
    fun `pause from the notification leaves exactly what the clock button leaves`() {
        val byButton = newViewModel()
        byButton.send(TimerIntent.ToggleTimer)
        advanceSeconds(300)
        byButton.send(TimerIntent.ToggleTimer)
        val afterButton = saved()
        val screenAfterButton = byButton.state

        // The same game again, paused from the notification instead
        store.clear()
        store = ViewModelStore()
        timerPreferences.resetTimer()
        val byNotification = newViewModel()
        byNotification.send(TimerIntent.ToggleTimer)
        advanceSeconds(300)
        assertTrue(notificationButton(ClockCommand.PAUSE))

        assertEquals(afterButton.copy(anchor = ClockAnchor.stopped(300_000)), afterButton)
        assertEquals(afterButton, saved())
        assertFalse(byNotification.state.isRunning)
        assertEquals(screenAfterButton.elapsedSeconds, byNotification.state.elapsedSeconds)
        assertEquals(screenAfterButton.isRunning, byNotification.state.isRunning)
        assertEquals("15:00", ClockFormat.clock(byNotification.state.segmentRemainingSeconds))
    }

    @Test
    fun `the clock on screen stays paused, and resume from the notification starts it again`() {
        val viewModel = newViewModel()
        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(60)
        notificationButton(ClockCommand.PAUSE)

        advanceSeconds(600)
        assertEquals(60, viewModel.state.elapsedSeconds)
        assertTrue(viewModel.state.hasTimerStarted)

        assertTrue(notificationButton(ClockCommand.RESUME))
        assertTrue(viewModel.state.isRunning)
        assertTrue(tournamentPreferences.getTournamentLocked())
        advanceSeconds(30)
        assertEquals(90, viewModel.state.elapsedSeconds)
        // And the clock's own button still works on top of it
        viewModel.send(TimerIntent.ToggleTimer)
        assertFalse(viewModel.state.isRunning)
        assertFalse(timerPreferences.getTimerRunning())
    }

    @Test
    fun `a stale button does nothing`() {
        val viewModel = newViewModel()
        assertFalse("nothing to pause before the start", notificationButton(ClockCommand.PAUSE))
        assertFalse("nor to resume", notificationButton(ClockCommand.RESUME))
        assertFalse(viewModel.state.hasTimerStarted)

        viewModel.send(TimerIntent.ToggleTimer)
        assertFalse("Resume on a running clock", notificationButton(ClockCommand.RESUME))
        viewModel.send(TimerIntent.ToggleTimer)
        val before = saved()
        assertFalse("Pause on a paused clock", notificationButton(ClockCommand.PAUSE))
        assertEquals(before, saved())
    }

    @Test
    fun `a pause made while the app was gone is there when it comes back`() {
        val viewModel = newViewModel()
        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(300)

        // The process dies (swiped away); 10 minutes later Pause comes from the notification in a new one
        store.clear()
        store = ViewModelStore()
        clock.sleep(600_000)
        newPreferences()
        assertTrue(notificationButton(ClockCommand.PAUSE))

        // An hour later the app is opened again: paused where the button stopped it
        clock.sleep(3_600_000)
        newPreferences()
        val restored = newViewModel()
        assertFalse(restored.state.isRunning)
        assertEquals(900, restored.state.elapsedSeconds)
        assertEquals("5:00", ClockFormat.clock(restored.state.segmentRemainingSeconds))
    }

    @Test
    fun `a resume made while the app was gone keeps counting until it comes back`() {
        val viewModel = newViewModel()
        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(120)
        viewModel.send(TimerIntent.ToggleTimer) // paused at 2:00 of play

        store.clear()
        store = ViewModelStore()
        newPreferences()
        assertTrue(notificationButton(ClockCommand.RESUME))
        clock.sleep(180_000)

        newPreferences()
        val restored = newViewModel()
        assertTrue(restored.state.isRunning)
        assertEquals(300, restored.state.elapsedSeconds)
    }

    @Test
    fun `the clock shows the notification only while it runs`() {
        assertFalse(LiveClockController.shows(timerPreferences))
        val viewModel = newViewModel()
        viewModel.send(TimerIntent.ToggleTimer)
        assertTrue(LiveClockController.shows(timerPreferences))
        viewModel.send(TimerIntent.ToggleTimer)
        assertFalse(LiveClockController.shows(timerPreferences))
        viewModel.send(TimerIntent.ToggleTimer)
        viewModel.send(TimerIntent.ResetTimer)
        assertFalse(LiveClockController.shows(timerPreferences))
    }

    @Test
    fun `the service hears every change to the clock, and nothing while it simply runs`() {
        val viewModel = newViewModel()
        var changes = 0
        val scope = TestScope(testDispatcher)
        val watching = timerPreferences.observeChanges().onEach { changes++ }.launchIn(scope)
        testDispatcher.scheduler.runCurrent()

        viewModel.send(TimerIntent.ToggleTimer)
        val afterStart = changes
        assertTrue("starting is heard", afterStart > 0)
        advanceSeconds(30 * 60)
        assertEquals("nothing while it runs, across a level change", afterStart, changes)
        viewModel.send(TimerIntent.ToggleTimer)
        assertTrue("pausing is heard", changes > afterStart)
        watching.cancel()
    }

    @Test
    fun `the clock itself vibrates with a minute left and at the change`() {
        val viewModel = newViewModel()
        viewModel.send(TimerIntent.ToggleTimer)

        advanceSeconds(19 * 60 - 1)
        assertTrue(buzzes.isEmpty())
        advanceSeconds(1)
        assertEquals(listOf(SilentCue.ONE_MINUTE), buzzes)
        advanceSeconds(60)
        assertEquals(listOf(SilentCue.ONE_MINUTE, SilentCue.LEVEL_CHANGE), buzzes)
        // Paused, nothing more
        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(40 * 60)
        assertEquals(2, buzzes.size)
    }

    @Test
    fun `with vibration off the clock does not vibrate, and the chime is untouched`() {
        audioPreferences.setVibrateCues(false)
        val viewModel = newViewModel()
        viewModel.send(TimerIntent.ToggleTimer)

        advanceSeconds(20 * 60)
        assertTrue(buzzes.isEmpty())
        verify(exactly = 1) { soundManager.playSound(any()) }
    }
}
