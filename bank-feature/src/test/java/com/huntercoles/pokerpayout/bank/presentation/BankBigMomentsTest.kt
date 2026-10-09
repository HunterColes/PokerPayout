package com.huntercoles.pokerpayout.bank.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.audio.SoundManager
import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.testing.expect
import com.huntercoles.pokerpayout.core.testing.forAll
import com.huntercoles.pokerpayout.core.time.TimeSource
import com.huntercoles.pokerpayout.core.utils.ChipSetChips
import com.huntercoles.pokerpayout.core.utils.ChipSetProvider
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockCues
import com.huntercoles.pokerpayout.tournament.domain.clock.CueVibrator
import com.huntercoles.pokerpayout.tournament.domain.moments.BigMoment
import com.huntercoles.pokerpayout.tournament.domain.moments.BigMoments
import com.huntercoles.pokerpayout.tournament.domain.moments.Field
import com.huntercoles.pokerpayout.tournament.domain.moments.TableSeats
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import com.huntercoles.pokerpayout.tournament.presentation.TimerViewModel
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

/**
 * PP-111 from the Bank's side: the knockouts the Bank records, in every bounty type, from its own
 * sheet or from the clock's Knock out (the same Bank intents, PP-135), bring the clock's big
 * moments; the Bank's Undo (the snackbar's or the top bar's) and Bring back take them back; and a
 * restart never marks one twice. The real Bank and the real clock ViewModel, over the same
 * preferences, as in the app.
 *
 * The clock has run out here (started and finished), so it doesn't tick: the Bank's virtual time
 * (its 8 s Undo windows) runs to the end without the clock's own loops.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BankBigMomentsTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var kit: BankTestKit
    private val sound: SoundManager = mockk(relaxed = true)
    private var clockStore = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        kit = BankTestKit(dispatcher)
    }

    @After
    fun tearDown() {
        clockStore.clear()
        kit.clear()
        Dispatchers.resetMain()
    }

    /** Nine players ([BankScenes.NAMES]) at $40, a $5 bounty in [mode], three places paid. */
    private fun night(mode: BountyMode = BountyMode.STANDARD, players: Int = 9): BankViewModel = with(kit) {
        configure(players = players, buyIn = 40.0, bounty = 5.0, rebuy = 40.0, weights = listOf(50, 30, 20))
        tournamentPreferences.setBountyMode(mode)
        val bank = newViewModel()
        BankScenes.NAMES.take(players).forEachIndexed { index, name -> bank.send(BankIntent.PlayerNameChanged(index + 1, name)) }
        bank
    }

    /** The Tournament tab's clock over the kit's preferences, as the app builds it. */
    private fun clock(): TimerViewModel {
        kit.timerPreferences.setHasTimerStarted(true)
        kit.timerPreferences.setIsFinished(true)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TimerViewModel(
                kit.timerPreferences,
                kit.tournamentPreferences,
                kit.bankPreferences,
                ClockCues(sound, kit.audioPreferences, CueVibrator { }, StillTime),
                StillTime,
                kit.audioPreferences,
                NoChipSet,
                TableSeats { NINE_SEATS },
            ) as T
        }
        return ViewModelProvider(clockStore, factory)[TimerViewModel::class.java].also { kit.settle() }
    }

    /** Process death and a cold start: the Bank and the clock built again from what was saved. */
    private fun restart(): Pair<BankViewModel, TimerViewModel> {
        clockStore.clear()
        clockStore = ViewModelStore()
        val bank = kit.restartProcess()
        return bank to clock()
    }

    private val TimerViewModel.state: TimerUiState get() = uiState.value

    /** A knockout as the Bank records it; a mystery envelope's reveal is closed after. */
    private fun BankViewModel.out(id: Int, by: Int? = BankScenes.DANA) = with(kit) {
        knockOut(id, by)
        if (uiState.value.sheet != null) send(BankIntent.DismissSheet)
    }

    @Test
    fun everyBountyTypesKnockoutsBringTheClocksMoments() = BountyMode.entries.forEach { mode ->
        kit.clear()
        clockStore.clear()
        clockStore = ViewModelStore()
        kit = BankTestKit(dispatcher)
        val bank = night(mode)
        val clock = clock()
        listOf(9, 8, 7, 6).forEach {
            bank.out(it)
            assertNull("$mode: $it out, no moment yet", clock.state.moment)
        }
        bank.out(5, by = BankScenes.MARCUS)
        assertEquals("$mode: four left of nine, three paid", BigMoment.BUBBLE, clock.state.moment?.moment)
        bank.out(4, by = null)
        assertEquals(mode.name, BigMoment.IN_THE_MONEY, clock.state.moment?.moment)
        bank.out(3)
        assertEquals(mode.name, BigMoment.HEADS_UP, clock.state.moment?.moment)
        assertEquals(mode.name, listOf("Dana", "Marcus"), clock.state.moment?.names)
        bank.out(2)
        assertTrue("$mode: the champion's screen opens", clock.state.winnerOpen)
        assertEquals(mode.name, "Dana", clock.state.table.championName)
        assertEquals("$mode: the Bank's champion", BankScenes.DANA, bank.uiState.value.championId)
    }

    @Test
    fun theClocksKnockOutIsTheBanksKnockout() = with(kit) {
        val bank = night(BountyMode.MYSTERY)
        val clock = clock()
        listOf(9, 8, 7, 6).forEach { bank.out(it) }
        // The table view's Knock out sends what the Bank's sheet sends: who's out, then who did it
        bank.send(BankIntent.OpenKnockout(5))
        bank.send(BankIntent.KnockOut(5, BankScenes.DANA))
        assertTrue("the envelope shows first", bank.uiState.value.sheet is BankSheet.Envelope)
        assertEquals(BigMoment.BUBBLE, clock.state.moment?.moment)
    }

    @Test
    fun undoOnTheSnackbarOrTheTopBarTakesTheMomentBack() = with(kit) {
        val bank = night()
        val clock = clock()
        listOf(9, 8, 7, 6).forEach { bank.out(it) }
        bank.act(BankIntent.KnockOut(5, BankScenes.DANA))
        val bubble = clock.state.moment!!
        assertEquals(BigMoment.BUBBLE, bubble.moment)

        pressSnackbarUndo()
        assertNull("the snackbar's Undo took the bubble back", clock.state.moment)
        bank.out(5)
        assertEquals("recorded again, it shows again", BigMoment.BUBBLE, clock.state.moment?.moment)
        assertTrue(clock.state.moment!!.id > bubble.id)

        listOf(4, 3, 2).forEach { bank.out(it) }
        assertTrue(clock.state.winnerOpen)
        bank.send(BankIntent.Undo)
        assertFalse("the top bar's Undo closes the champion's screen", clock.state.winnerOpen)
        assertNull(clock.state.table.championName)
        verify(exactly = 1) { sound.playSound(R.raw.blind_level_up) } // the classic pack's chime, for the champion
    }

    @Test
    fun aPlayerBroughtBackWithARebuyTakesTheBubbleBack() = with(kit) {
        val bank = night()
        val clock = clock()
        listOf(9, 8, 7, 6, 5).forEach { bank.out(it) }
        assertEquals(BigMoment.BUBBLE, clock.state.moment?.moment)

        // Jo buys back in: back in the game, with a rebuy
        bank.send(BankIntent.BringBack(BankScenes.JO), BankIntent.AddPurchase(BankScenes.JO, Purchase.REBUY))
        assertEquals(5, clock.state.table.playersLeft)
        assertNull(clock.state.moment)
        bank.out(4)
        assertEquals("the bubble again, one knockout later", BigMoment.BUBBLE, clock.state.moment?.moment)
    }

    @Test
    fun aRestartMarksNothingTwice() = with(kit) {
        val bank = night(BountyMode.PROGRESSIVE)
        clock()
        (9 downTo 2).forEach { bank.out(it) }
        val (_, clock) = restart()
        assertNull(clock.state.moment)
        assertFalse(clock.state.winnerOpen)
        assertEquals("Dana", clock.state.table.championName)
        verify(exactly = 1) { sound.playSound(R.raw.blind_level_up) }
    }

    /** One thing done at the Bank or the Tournament tab ([kind]), or the process dying. */
    private data class Step(val kind: Int, val who: Int, val by: Int)

    private data class Night(val players: Int, val mode: BountyMode, val steps: List<Step>)

    /**
     * Random nights at the Bank in every bounty type, with knockouts (credited or not), Bring back,
     * Undo, rebuys, a late arrival and process death at random points. After every step the clock
     * shows a new moment only when a knockout brought one, and then the biggest it brought; what it
     * shows the field still has; and after a restart it shows nothing until the next knockout.
     */
    @Test
    fun aMomentComesOnlyWithTheKnockoutThatBringsIt() {
        var moments = 0
        forAll(seed = 2026_1009_1110L, iterations = 40, gen = nights) { night ->
            kit.clear()
            clockStore.clear()
            clockStore = ViewModelStore()
            kit = BankTestKit(dispatcher)
            kit.draws = Random(night.steps.size)
            var bank = night(night.mode, night.players)
            var clock = clock()
            night.steps.forEachIndexed { index, step ->
                val before = clock.state
                val reachedBefore = reached(before)
                val knockout = perform(bank, step)
                if (step.kind == DIE) {
                    restart().let { (newBank, newClock) -> bank = newBank; clock = newClock }
                    expect(clock.state.moment == null && !clock.state.winnerOpen) { "step $index: shown again after a restart" }
                    return@forEachIndexed
                }
                val after = clock.state
                val brought = reached(after) - reachedBefore
                val shown = after.moment?.takeIf { it.id != before.moment?.id }?.moment
                    ?: BigMoment.CHAMPION.takeIf { after.winnerOpen && !before.winnerOpen }
                // A knockout, or an Undo that puts one back (of a Bring back): someone more is out
                val wentOut = out(after) > out(before)
                expect(shown == null || wentOut) { "step $index ($step): $shown shown with nobody more out" }
                expect(!wentOut || BigMoments.headline(brought) == shown) {
                    "step $index ($step): $brought brought, $shown shown"
                }
                expect(!knockout || wentOut || shown == null) { "step $index: a knockout that changed nothing showed $shown" }
                val still = after.moment?.moment
                expect(still == null || still in reached(after)) { "step $index: $still isn't reached" }
                expect(!after.winnerOpen || after.table.championName != null) { "step $index: a champion's screen, no champion" }
                if (shown != null) moments++
            }
            kit.clear()
        }
        assertTrue("the random nights reached some moments: $moments", moments >= MOMENTS_AT_LEAST)
    }

    private fun out(state: TimerUiState): Int = state.table.playerCount - state.table.playersLeft

    private fun reached(state: TimerUiState): Set<BigMoment> = with(state.table) {
        BigMoments.reached(Field(playerCount, playersLeft, paidPlaces, NINE_SEATS))
    }

    /** Does [step] at the Bank; true when it was a knockout. */
    private fun perform(bank: BankViewModel, step: Step): Boolean = with(kit) {
        val ids = bank.uiState.value.players.map { it.id }
        val id = ids[step.who.mod(ids.size)]
        val by = ids[step.by.mod(ids.size)].takeIf { it != id && step.by % 3 != 0 }
        when (step.kind) {
            in 0..KNOCKOUTS -> bank.out(id, by)
            KNOCKOUTS + 1 -> bank.send(BankIntent.BringBack(id))
            KNOCKOUTS + 2 -> bank.send(BankIntent.Undo)
            KNOCKOUTS + 3 -> bank.send(BankIntent.AddPurchase(id, Purchase.REBUY))
            KNOCKOUTS + 4 -> {
                tournamentPreferences.setPlayerCount(ids.size + 1) // a late arrival
                settle()
            }
            else -> Unit // DIE: the caller restarts
        }
        step.kind in 0..KNOCKOUTS
    }

    /** A clock that never moves. */
    private object StillTime : TimeSource {
        override fun elapsedRealtimeMillis() = 1_000_000L
        override fun wallClockMillis() = 1_791_236_820_000L
        override fun bootCount() = 1
    }

    /** No chip set set up. */
    private object NoChipSet : ChipSetProvider {
        override fun current(): ChipSetChips? = null
        override val chipSet = MutableStateFlow<ChipSetChips?>(null)
    }

    private companion object {
        const val NINE_SEATS = 9
        const val KNOCKOUTS = 5
        const val DIE = KNOCKOUTS + 5
        const val MOMENTS_AT_LEAST = 20

        val steps = Arb.bind(Arb.int(0..DIE), Arb.int(0..30), Arb.int(0..30), ::Step)
        val nights = Arb.bind(Arb.int(3..12), Arb.element(BountyMode.entries), Arb.list(steps, 1..30), ::Night)
    }
}
