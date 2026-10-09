package com.huntercoles.pokerpayout.tournament.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.audio.SoundManager
import com.huntercoles.pokerpayout.core.audio.packs.CueEvent
import com.huntercoles.pokerpayout.core.audio.packs.SoundPack
import com.huntercoles.pokerpayout.core.audio.packs.SoundPacks
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockCues
import com.huntercoles.pokerpayout.tournament.domain.clock.CueVibrator
import com.huntercoles.pokerpayout.tournament.domain.clock.SilentCue
import com.huntercoles.pokerpayout.tournament.domain.moments.BigMoment
import com.huntercoles.pokerpayout.tournament.domain.moments.TableSeats
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * PP-111 on the clock: the knockouts the Bank records (from its tab, or from the table view's Knock
 * out: both write the Bank's records, as here) bring the night's big moments to the clock, each
 * once, with its cue from the picked sound pack. An Undo takes a moment back (and a knockout again
 * brings it again); a restart, process death included, never marks one twice.
 *
 * The mockups' night: nine players (Dana, Marcus, Priya, Theo, Jo, Sam, Alex, Rita, Ben) at $40,
 * Standard over three places, at the seat draw's nine seats a table. Virtual time; nothing reads a
 * real clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BigMomentsClockTest {

    private val testDispatcher = StandardTestDispatcher()
    private val clock = TimerViewModelTest.FakeTimeSource(testDispatcher.scheduler)
    private lateinit var context: Context
    private lateinit var timerPreferences: TimerPreferences
    private lateinit var tournamentPreferences: TournamentPreferences
    private lateinit var bankPreferences: BankPreferences
    private lateinit var audioPreferences: AudioPreferences
    private val soundManager: SoundManager = mockk(relaxed = true)
    private var store = ViewModelStore()
    private var seats = TableSeats { NINE }

    /** A pack with a sound for both kinds of moment (fake sound ids: the player is a mock). */
    private val fanfare = SoundPack(
        id = "fanfare",
        name = R.string.sound_pack_classic,
        description = R.string.sound_pack_classic_description,
        sounds = mapOf(CueEvent.BIG_MOMENT to MOMENT_SOUND, CueEvent.CHAMPION to CHAMPION_SOUND),
    )
    private val buzzes = mutableListOf<SilentCue>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        listOf("tournament_prefs", "timer_prefs", "bank_prefs", "audio_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        newPreferences()
        tournamentPreferences.setPlayerCount(NAMES.size)
        tournamentPreferences.setBuyIn(40.0)
        tournamentPreferences.setPayoutPreset(PayoutPreset.STANDARD, 3)
        NAMES.forEachIndexed { index, name -> bankPreferences.savePlayerName(index + 1, name) }
        audioPreferences.setSoundPack(fanfare.id)
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
                cues.packs = listOf(SoundPacks.Classic, fanfare)
                return TimerViewModel(
                    timerPreferences,
                    tournamentPreferences,
                    bankPreferences,
                    cues,
                    clock,
                    audioPreferences,
                    FakeChipSets(),
                    seats,
                ) as T
            }
        }
        // runCurrent, not advanceUntilIdle: a started clock looks at the wall clock forever
        return ViewModelProvider(store, factory)[TimerViewModel::class.java].also { testDispatcher.scheduler.runCurrent() }
    }

    /** The clock started, then paused: it exists, as the clock does once a night is under way. */
    private fun startedClock(): TimerViewModel = newViewModel().apply {
        send(TimerIntent.ToggleTimer, TimerIntent.ToggleTimer)
        assertTrue(state.hasTimerStarted)
    }

    /** The process dies and the app comes back: every ViewModel and preference object built again. */
    private fun restart(): TimerViewModel {
        store.clear()
        store = ViewModelStore()
        newPreferences()
        return newViewModel()
    }

    private fun TimerViewModel.send(vararg intents: TimerIntent) {
        intents.forEach { acceptIntent(it) }
        testDispatcher.scheduler.runCurrent()
    }

    private val TimerViewModel.state get() = uiState.value

    /** What the Bank records for a knockout (its sheet, or the table view's Knock out). */
    private fun knockOut(id: Int, by: Int? = null) {
        bankPreferences.savePlayerOutStatus(id, true)
        bankPreferences.savePlayerEliminatedBy(id, by)
        bankPreferences.saveEliminationOrder(bankPreferences.getEliminationOrder() + id)
        testDispatcher.scheduler.runCurrent()
    }

    /** What the Bank's Undo (or Bring back) puts back: [id] in again. */
    private fun backIn(id: Int) {
        bankPreferences.savePlayerOutStatus(id, false)
        bankPreferences.savePlayerEliminatedBy(id, null)
        bankPreferences.saveEliminationOrder(bankPreferences.getEliminationOrder() - id)
        testDispatcher.scheduler.runCurrent()
    }

    /** Ben, Rita, Alex, Sam and Jo out: four left of nine, three paid. */
    private fun toTheBubble() = listOf(BEN, RITA, ALEX, SAM, JO).forEach { knockOut(it, by = DANA) }

    @Test
    fun eachMomentShowsOnTheClockOnceAsTheKnockoutsBringItWithItsCue() {
        val viewModel = startedClock()
        listOf(BEN, RITA, ALEX, SAM).forEach {
            knockOut(it, by = DANA)
            assertNull("$it out: no moment yet", viewModel.state.moment)
        }

        knockOut(JO, by = MARCUS)
        val bubble = viewModel.state.moment!!
        assertEquals(BigMoment.BUBBLE, bubble.moment)
        assertEquals(4, bubble.playersLeft)

        knockOut(THEO)
        val money = viewModel.state.moment!!
        assertEquals(BigMoment.IN_THE_MONEY, money.moment)
        val pool = 9 * 4_000L
        val lowest = CalculatePayoutsUseCase()(pool, tournamentPreferences.getCurrentTournamentConfig().payoutWeights, 9)
            .places.minOf { it.amountCents }
        assertEquals("everyone left wins at least 3rd's prize", lowest, money.lowestPrizeCents)
        assertTrue(money.id > bubble.id)

        knockOut(PRIYA, by = DANA)
        val headsUp = viewModel.state.moment!!
        assertEquals(BigMoment.HEADS_UP, headsUp.moment)
        assertEquals(listOf("Dana", "Marcus"), headsUp.names)
        assertFalse(viewModel.state.winnerOpen)

        knockOut(MARCUS, by = DANA)
        assertNull("the champion has a screen, not a banner", viewModel.state.moment)
        assertTrue(viewModel.state.winnerOpen)
        assertEquals("Dana", viewModel.state.table.championName)

        verify(exactly = 3) { soundManager.playSound(MOMENT_SOUND) }
        verify(exactly = 1) { soundManager.playSound(CHAMPION_SOUND) }
        assertTrue("a moment neither vibrates nor flashes: it shows", buzzes.isEmpty())
    }

    @Test
    fun theClassicPackPlaysTheChimeForTheChampionAndNothingForTheOthers() {
        audioPreferences.setSoundPack(SoundPacks.CLASSIC_ID)
        startedClock()
        toTheBubble()
        listOf(THEO, PRIYA).forEach { knockOut(it) }
        verify(exactly = 0) { soundManager.playSound(any()) }
        knockOut(MARCUS)
        verify(exactly = 1) { soundManager.playSound(R.raw.blind_level_up) }
    }

    @Test
    fun aBannerGoesOnceSeenAndAnOlderOnesTimeCantTakeANewerOne() {
        val viewModel = startedClock()
        toTheBubble()
        val bubble = viewModel.state.moment!!
        viewModel.send(TimerIntent.MomentSeen(bubble.id))
        assertNull(viewModel.state.moment)
        assertEquals("the pill still says so", MoneyStage.BUBBLE, viewModel.state.table.moneyStage)

        knockOut(THEO)
        val money = viewModel.state.moment!!
        viewModel.send(TimerIntent.MomentSeen(bubble.id))
        assertEquals(money, viewModel.state.moment)
        viewModel.send(TimerIntent.MomentSeen(money.id))
        assertNull(viewModel.state.moment)
    }

    @Test
    fun undoTakesTheMomentBackAndTheKnockoutAgainBringsItAgain() {
        val viewModel = startedClock()
        toTheBubble()
        val first = viewModel.state.moment!!

        backIn(JO)
        assertNull("the Undo took the bubble back", viewModel.state.moment)
        assertEquals(setOf<String>(), timerPreferences.getBigMomentsReached())

        knockOut(JO, by = DANA)
        val again = viewModel.state.moment!!
        assertEquals(BigMoment.BUBBLE, again.moment)
        assertNotEquals("shown again, as a new banner", first.id, again.id)
        verify(exactly = 2) { soundManager.playSound(MOMENT_SOUND) }
    }

    @Test
    fun anUndoTakesBackOnlyWhatTheFieldNoLongerHas() {
        val viewModel = startedClock()
        toTheBubble()
        knockOut(THEO)
        val money = viewModel.state.moment!!
        // Ben back in: four left, on the bubble again, so only the money goes
        backIn(BEN)
        assertEquals(setOf(BigMoment.BUBBLE.name), timerPreferences.getBigMomentsReached())
        assertNull("in the money no more: its banner goes", viewModel.state.moment)
        knockOut(BEN)
        assertEquals(BigMoment.IN_THE_MONEY, viewModel.state.moment!!.moment)
        assertNotEquals(money.id, viewModel.state.moment!!.id)
    }

    @Test
    fun anUndoKeepsTheBannerOfAMomentTheFieldStillHas() {
        tournamentPreferences.setPlayerCount(12)
        val viewModel = startedClock()
        listOf(12, 11, 10).forEach { knockOut(it) }
        val finalTable = viewModel.state.moment!!
        knockOut(BEN)
        assertEquals("no new moment: the final table stays", finalTable, viewModel.state.moment)
        backIn(BEN)
        assertEquals("nine left: still the final table", finalTable, viewModel.state.moment)
    }

    @Test
    fun undoOfTheLastKnockoutClosesTheChampionsScreen() {
        val viewModel = startedClock()
        toTheBubble()
        listOf(THEO, PRIYA, MARCUS).forEach { knockOut(it, by = DANA) }
        assertTrue(viewModel.state.winnerOpen)

        viewModel.send(TimerIntent.CloseWinner)
        assertFalse(viewModel.state.winnerOpen)
        viewModel.send(TimerIntent.OpenWinner)
        assertTrue("the clock's champion card opens it again", viewModel.state.winnerOpen)

        backIn(MARCUS)
        assertFalse(viewModel.state.winnerOpen)
        assertNull(viewModel.state.table.championName)
        viewModel.send(TimerIntent.OpenWinner)
        assertFalse("no champion, no screen", viewModel.state.winnerOpen)

        knockOut(MARCUS, by = DANA)
        assertTrue("recorded again, the champion's screen opens again", viewModel.state.winnerOpen)
        verify(exactly = 2) { soundManager.playSound(CHAMPION_SOUND) }
    }

    @Test
    fun afterProcessDeathNothingIsMarkedTwice() {
        var viewModel = startedClock()
        toTheBubble()
        assertEquals(BigMoment.BUBBLE, viewModel.state.moment!!.moment)

        viewModel = restart()
        assertNull(viewModel.state.moment)
        knockOut(THEO)
        knockOut(PRIYA)
        knockOut(MARCUS)
        assertTrue(viewModel.state.winnerOpen)

        viewModel = restart()
        assertNull(viewModel.state.moment)
        assertFalse("the champion's screen doesn't open again by itself", viewModel.state.winnerOpen)
        assertEquals("Dana", viewModel.state.table.championName)
        viewModel.send(TimerIntent.OpenWinner)
        assertTrue(viewModel.state.winnerOpen)
        verify(exactly = 3) { soundManager.playSound(MOMENT_SOUND) } // bubble, money, heads-up: once each
        verify(exactly = 1) { soundManager.playSound(CHAMPION_SOUND) }
    }

    @Test
    fun aKnockoutRecordedWhileTheClockWasAwayShowsWhenItComesBack() {
        startedClock()
        listOf(BEN, RITA, ALEX, SAM).forEach { knockOut(it) }
        // The process dies; the app opens on the Bank and Jo goes out before the clock is back
        store.clear()
        store = ViewModelStore()
        newPreferences()
        knockOut(JO)
        val viewModel = newViewModel()
        assertEquals(BigMoment.BUBBLE, viewModel.state.moment!!.moment)
    }

    @Test
    fun beforeTheClockStartsAMomentIsOnlyNoted() {
        val viewModel = newViewModel()
        toTheBubble()
        assertNull(viewModel.state.moment)
        verify(exactly = 0) { soundManager.playSound(any()) }

        viewModel.send(TimerIntent.ToggleTimer)
        assertNull("starting the clock doesn't bring back an old moment", viewModel.state.moment)
        knockOut(THEO)
        assertEquals(BigMoment.IN_THE_MONEY, viewModel.state.moment!!.moment)
    }

    @Test
    fun anUpdateMidGameMarksNothingOld() {
        toTheBubble()
        timerPreferences.setHasTimerStarted(true)
        assertNull("nothing saved before this version", timerPreferences.getBigMomentsReached())
        val viewModel = newViewModel()
        assertNull(viewModel.state.moment)
        assertEquals(setOf(BigMoment.BUBBLE.name), timerPreferences.getBigMomentsReached())
        knockOut(THEO)
        assertEquals(BigMoment.IN_THE_MONEY, viewModel.state.moment!!.moment)
    }

    @Test
    fun aLateArrivalTakesTheBubbleBack() {
        val viewModel = startedClock()
        toTheBubble()
        tournamentPreferences.setPlayerCount(NAMES.size + 1)
        testDispatcher.scheduler.runCurrent()
        assertEquals(5, viewModel.state.table.playersLeft)
        // Ten players now: two tables' worth, five left. Nobody went out, so that's no final table.
        assertNull("the bubble's banner goes, and nothing comes in its place", viewModel.state.moment)
        knockOut(THEO)
        assertEquals("the bubble again, with the late arrival in", BigMoment.BUBBLE, viewModel.state.moment!!.moment)
    }

    @Test
    fun aNightOnTwoTablesReachesTheFinalTable() {
        tournamentPreferences.setPlayerCount(12)
        val viewModel = startedClock()
        knockOut(12)
        knockOut(11)
        assertNull(viewModel.state.moment)
        knockOut(10)
        val finalTable = viewModel.state.moment!!
        assertEquals(BigMoment.FINAL_TABLE, finalTable.moment)
        assertEquals(9, finalTable.playersLeft)
    }

    @Test
    fun theFinalTableFollowsTheSeatDrawsTables() {
        seats = TableSeats { 6 }
        val viewModel = startedClock()
        listOf(BEN, RITA).forEach { knockOut(it) }
        assertNull(viewModel.state.moment)
        knockOut(ALEX)
        assertEquals(BigMoment.FINAL_TABLE, viewModel.state.moment!!.moment)
    }

    private companion object {
        val NAMES = listOf("Dana", "Marcus", "Priya", "Theo", "Jo", "Sam", "Alex", "Rita", "Ben")
        const val DANA = 1
        const val MARCUS = 2
        const val PRIYA = 3
        const val THEO = 4
        const val JO = 5
        const val SAM = 6
        const val ALEX = 7
        const val RITA = 8
        const val BEN = 9
        const val NINE = 9
        const val MOMENT_SOUND = 201
        const val CHAMPION_SOUND = 202
    }
}
