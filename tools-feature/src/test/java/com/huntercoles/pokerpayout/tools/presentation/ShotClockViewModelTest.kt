package com.huntercoles.pokerpayout.tools.presentation

import android.content.Context
import android.content.SharedPreferences
import com.huntercoles.pokerpayout.core.design.components.SnackbarController
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import com.huntercoles.pokerpayout.core.preferences.PlayerNamesProvider
import com.huntercoles.pokerpayout.core.time.TimeSource
import com.huntercoles.pokerpayout.tools.shotclock.ShotClockAlerts
import com.huntercoles.pokerpayout.tools.shotclock.ShotClockCue
import com.huntercoles.pokerpayout.tools.shotclock.ShotClockPhase
import com.huntercoles.pokerpayout.tools.shotclock.ShotClockSignals
import com.huntercoles.pokerpayout.tools.shotclock.ShotClockStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The shot clock on virtual time with a fake monotonic clock: it counts from a monotonic anchor
 * (so a phone asleep between looks can't make it drift), warns at ten seconds and at zero as the
 * Sound section's switches say, pauses, and plays time-bank cards. Settings and the cards played
 * are saved; a new ViewModel on the same preferences is a process death.
 */
class ShotClockViewModelTest {

    private val main = StandardTestDispatcher()
    private val stores = mutableMapOf<String, SharedPreferences>()
    private val snackbars = SnackbarController()
    private lateinit var context: Context
    private lateinit var audio: AudioPreferences
    private val signals = RecordingSignals()
    private val time = FakeTime()
    private var bank = listOf("Dana", "Marcus", "Priya")
    private val messages = mockk<ShotClockMessages> {
        every { cardsBack } returns "Cards given back"
        every { undo } returns "Undo"
        every { defaultName(any()) } answers { "Player ${firstArg<Int>()}" }
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(main)
        context = mockk {
            every { getSharedPreferences(any(), any()) } answers { stores.getOrPut(firstArg()) { InMemoryPreferences() } }
        }
        audio = AudioPreferences(context)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ShotClockViewModel(
        store = ShotClockStore(context),
        bankNames = PlayerNamesProvider { bank },
        time = time,
        alerts = ShotClockAlerts(audio, signals),
        snackbars = snackbars,
        messages = messages,
    )

    private fun runVmTest(block: suspend TestScope.() -> Unit) = runTest(main) {
        time.scheduler = { testScheduler.currentTime }
        block()
    }

    private val ShotClockViewModel.state get() = uiState.value

    /** Moves virtual time on by [millis], running every look due by then. */
    private fun TestScope.wait(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `it opens full and waiting, 30 s to act and 2 cards each for the Bank's players`() {
        val vm = viewModel()
        assertEquals(ShotClockPhase.Ready, vm.state.phase)
        assertEquals(30, vm.state.shownSeconds)
        assertEquals(1f, vm.state.progress)
        assertEquals(listOf(TimeBankPlayer("Dana", 2), TimeBankPlayer("Marcus", 2), TimeBankPlayer("Priya", 2)), vm.state.players)
        assertFalse(vm.state.canPlayCards, "no decision on the clock yet")
    }

    @Test
    fun `with nobody in the Bank the time bank lists Player 1 to Player 9`() {
        bank = emptyList()
        val players = viewModel().state.players
        assertEquals(ShotClockViewModel.DEFAULT_PLAYERS, players.size)
        assertEquals("Player 1", players.first().name)
    }

    @Test
    fun `a decision counts down, warns once at ten seconds and once at zero, then shows time up`() = runVmTest {
        val vm = viewModel()
        vm.acceptIntent(ShotClockIntent.NextDecision)
        runCurrent()
        assertEquals(ShotClockPhase.Running, vm.state.phase)
        wait(5_000)
        assertEquals(25, vm.state.shownSeconds)
        wait(14_999)
        assertTrue(signals.calls.isEmpty(), "nothing before ten seconds left")
        assertFalse(vm.state.lowOnTime)
        wait(1)
        assertEquals(10, vm.state.shownSeconds)
        assertTrue(vm.state.lowOnTime)
        assertEquals(listOf("beep TenSeconds 1.0", "buzz TenSeconds"), signals.calls)
        wait(10_000)
        assertEquals(ShotClockPhase.TimeUp, vm.state.phase)
        assertEquals(0, vm.state.shownSeconds)
        assertEquals(listOf("beep TenSeconds 1.0", "buzz TenSeconds", "beep TimeUp 1.0", "buzz TimeUp"), signals.calls)
        assertEquals(2, vm.state.flashes, "Flash the clock is on: one flash a warning")
        wait(60_000)
        assertEquals(4, signals.calls.size, "time up is given once")
    }

    @Test
    fun `a phone asleep between looks can't make it drift, and a warning long past isn't given late`() = runVmTest {
        val vm = viewModel()
        vm.acceptIntent(ShotClockIntent.NextDecision)
        runCurrent()
        wait(5_000)
        time.asleep += 20_000 // 20 s pass on the monotonic clock while nothing runs
        wait(1_000)
        assertEquals(4, vm.state.shownSeconds, "30 s less 26 s: the clock, not the looks")
        assertTrue(signals.calls.isEmpty(), "the ten-second warning was 6 s ago: skipped")
        wait(4_000)
        assertEquals(ShotClockPhase.TimeUp, vm.state.phase)
        assertEquals(listOf("beep TimeUp 1.0", "buzz TimeUp"), signals.calls)
    }

    @Test
    fun `the warnings follow the Sound section's switches and volume`() = runVmTest {
        audio.setMuted(true)
        audio.setFlashCues(false)
        val vm = viewModel()
        vm.acceptIntent(ShotClockIntent.NextDecision)
        runCurrent()
        wait(30_000)
        assertEquals(listOf("buzz TenSeconds", "buzz TimeUp"), signals.calls, "sound off: buzzes only")
        assertEquals(0, vm.state.flashes)

        signals.calls.clear()
        audio.setMuted(false)
        audio.setVolume(0.4f)
        audio.setVibrateCues(false)
        vm.acceptIntent(ShotClockIntent.NextDecision)
        runCurrent()
        wait(30_000)
        assertEquals(listOf("beep TenSeconds 0.4", "beep TimeUp 0.4"), signals.calls, "vibrate off: beeps only")
    }

    @Test
    fun `a pause holds the time left, a resume carries on, and a tap on the face starts afresh`() = runVmTest {
        val vm = viewModel()
        vm.acceptIntent(ShotClockIntent.NextDecision)
        runCurrent()
        wait(18_000)
        vm.acceptIntent(ShotClockIntent.Pause)
        runCurrent()
        assertEquals(ShotClockPhase.Paused, vm.state.phase)
        wait(120_000)
        assertEquals(12, vm.state.shownSeconds)
        assertTrue(signals.calls.isEmpty())

        vm.acceptIntent(ShotClockIntent.Resume)
        runCurrent()
        wait(2_000)
        assertEquals(10, vm.state.shownSeconds)
        assertEquals(listOf("beep TenSeconds 1.0", "buzz TenSeconds"), signals.calls, "warned after the pause")

        vm.acceptIntent(ShotClockIntent.NextDecision)
        runCurrent()
        assertEquals(30, vm.state.shownSeconds)
        assertFalse(vm.state.lowOnTime)
        vm.acceptIntent(ShotClockIntent.Reset)
        runCurrent()
        assertEquals(ShotClockPhase.Ready, vm.state.phase)
        wait(60_000)
        assertEquals(30, vm.state.shownSeconds, "reset waits, full")
    }

    @Test
    fun `a time-bank card adds 30 s, warns again at ten, and is saved for that player`() = runVmTest {
        val vm = viewModel()
        vm.acceptIntent(ShotClockIntent.PlayCard(0))
        assertEquals(2, vm.state.players[0].cardsLeft, "no card before a decision is on the clock")

        vm.acceptIntent(ShotClockIntent.NextDecision)
        runCurrent()
        wait(22_000)
        vm.acceptIntent(ShotClockIntent.PlayCard(1))
        runCurrent()
        assertEquals(38, vm.state.shownSeconds)
        assertEquals(TimeBankPlayer("Marcus", 1), vm.state.players[1])
        assertFalse(vm.state.lowOnTime)
        wait(28_000)
        assertEquals(10, vm.state.shownSeconds)
        assertEquals(2, signals.calls.count { it.startsWith("beep TenSeconds") }, "a warning for each pass through ten")

        assertEquals(TimeBankPlayer("Marcus", 1), viewModel().state.players[1], "saved")
    }

    @Test
    fun `a card played just after time ran out starts the clock again with its 30 s`() = runVmTest {
        val vm = viewModel()
        vm.acceptIntent(ShotClockIntent.NextDecision)
        runCurrent()
        wait(31_000)
        assertEquals(ShotClockPhase.TimeUp, vm.state.phase)
        vm.acceptIntent(ShotClockIntent.PlayCard(2))
        runCurrent()
        assertEquals(ShotClockPhase.Running, vm.state.phase)
        assertEquals(30, vm.state.shownSeconds)
        wait(30_000)
        assertEquals(ShotClockPhase.TimeUp, vm.state.phase)
    }

    @Test
    fun `a player with no cards left can't play one`() = runVmTest {
        val vm = viewModel()
        vm.acceptIntent(ShotClockIntent.NextDecision)
        runCurrent()
        repeat(3) { vm.acceptIntent(ShotClockIntent.PlayCard(0)) }
        runCurrent()
        assertEquals(0, vm.state.players[0].cardsLeft)
        assertEquals(90, vm.state.shownSeconds, "two cards' worth, not three")
    }

    @Test
    fun `everyone gets their cards back at once, with Undo`() = runVmTest {
        val vm = viewModel()
        vm.acceptIntent(ShotClockIntent.NextDecision)
        vm.acceptIntent(ShotClockIntent.PlayCard(0))
        vm.acceptIntent(ShotClockIntent.PlayCard(2))
        runCurrent()
        assertTrue(vm.state.anyCardsPlayed)

        vm.acceptIntent(ShotClockIntent.GiveCardsBack)
        runCurrent()
        assertTrue(vm.state.players.all { it.cardsLeft == 2 })
        assertTrue(viewModel().state.players.all { it.cardsLeft == 2 }, "saved")
        val snackbar = checkNotNull(snackbars.hostState.currentSnackbarData) { "no Undo offered" }
        assertEquals("Cards given back", snackbar.visuals.message)
        snackbar.performAction()
        runCurrent()
        assertEquals(listOf(1, 2, 1), vm.state.players.map { it.cardsLeft })
        assertEquals(listOf(1, 2, 1), viewModel().state.players.map { it.cardsLeft })
        vm.acceptIntent(ShotClockIntent.Reset)
    }

    @Test
    fun `the time to act and the cards each are saved, and a new time waits for the next decision`() = runVmTest {
        val vm = viewModel()
        vm.acceptIntent(ShotClockIntent.SetSeconds(45))
        runCurrent()
        assertEquals(45, vm.state.shownSeconds, "nothing on the clock: the face shows the new time at once")
        vm.acceptIntent(ShotClockIntent.NextDecision)
        runCurrent()
        wait(5_000)
        vm.acceptIntent(ShotClockIntent.SetSeconds(60))
        runCurrent()
        assertEquals(40, vm.state.shownSeconds, "the decision under way keeps its time")
        vm.acceptIntent(ShotClockIntent.SetSeconds(17))
        assertEquals(60, vm.state.seconds, "only the presets")
        vm.acceptIntent(ShotClockIntent.SetCardsEach(9))
        assertEquals(ShotClockStore.MAX_CARDS_EACH, vm.state.cardsEach)
        vm.acceptIntent(ShotClockIntent.Reset)

        val reborn = viewModel()
        assertEquals(60, reborn.state.seconds)
        assertEquals(60, reborn.state.shownSeconds)
        assertEquals(ShotClockStore.MAX_CARDS_EACH, reborn.state.players[0].cardsLeft)
    }

    @Test
    fun `no time bank with cards each at zero`() {
        val vm = viewModel()
        vm.acceptIntent(ShotClockIntent.SetCardsEach(0))
        assertTrue(vm.state.players.all { it.cardsLeft == 0 })
        assertFalse(vm.state.anyCardsPlayed)
    }

    /** The monotonic clock: virtual time, plus time that passed while nothing ran (deep sleep). */
    private class FakeTime : TimeSource {
        var scheduler: () -> Long = { 0L }
        var asleep = 0L

        override fun elapsedRealtimeMillis(): Long = BOOT + scheduler() + asleep

        override fun wallClockMillis(): Long = WALL + scheduler() + asleep

        override fun bootCount(): Int = 1

        private companion object {
            const val BOOT = 7_000_000L
            const val WALL = 1_800_000_000_000L
        }
    }

    /** Counts the beeps and buzzes, as "beep TenSeconds 1.0" and "buzz TimeUp". */
    private class RecordingSignals : ShotClockSignals {
        val calls = mutableListOf<String>()

        override fun beep(cue: ShotClockCue, volume: Float) {
            calls += "beep $cue $volume"
        }

        override fun buzz(cue: ShotClockCue) {
            calls += "buzz $cue"
        }
    }
}
