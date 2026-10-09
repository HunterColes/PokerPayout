package com.huntercoles.pokerpayout.tools.presentation

import android.content.Context
import android.content.SharedPreferences
import com.huntercoles.pokerpayout.tools.poker.OddsEngine
import com.huntercoles.pokerpayout.tools.poker.OddsRequest
import com.huntercoles.pokerpayout.tools.poker.Seat
import com.huntercoles.pokerpayout.tools.quiz.EquityQuizStore
import com.huntercoles.pokerpayout.tools.quiz.EquityRange
import com.huntercoles.pokerpayout.tools.quiz.QuizAnswer
import com.huntercoles.pokerpayout.tools.quiz.QuizJudge
import com.huntercoles.pokerpayout.tools.quiz.QuizQuestion
import com.huntercoles.pokerpayout.tools.quiz.QuizScore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.coroutines.CoroutineContext

/**
 * The equity quiz over the real odds engine (exact, on a dispatcher of its own that tests can see),
 * seeded deals and a store over in-memory preferences: a deal, a guess, the real odds, the streak.
 */
class EquityQuizViewModelTest {

    private val main = StandardTestDispatcher()
    private val engineDispatcher = RecordingDispatcher(StandardTestDispatcher(main.scheduler))
    private val stores = mutableMapOf<String, SharedPreferences>()
    private lateinit var context: Context
    private var nextSeed = 1_000L
    private val seeds = QuizSeeds { nextSeed++ }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(main)
        context = mockk {
            every { getSharedPreferences(any(), any()) } answers { stores.getOrPut(firstArg()) { InMemoryPreferences() } }
        }
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = EquityQuizViewModel(EquityQuizStore(context), OddsEngine(engineDispatcher), seeds)

    private fun runVmTest(block: suspend TestScope.() -> Unit) = runTest(main) { block() }

    private val EquityQuizViewModel.state get() = uiState.value

    /** Deals until the spot isn't too close to call, so a wrong answer exists. */
    private fun TestScope.dealClearSpot(vm: EquityQuizViewModel) {
        advanceUntilIdle()
        while (vm.state.closeCall) {
            vm.acceptIntent(EquityQuizIntent.NextDeal)
            advanceUntilIdle()
        }
    }

    @Test
    fun `it opens on a heads-up deal, and the engine works out the odds on its own dispatcher`() = runVmTest {
        val vm = viewModel()
        val deal = checkNotNull(vm.state.deal)
        assertEquals(2, deal.hands.size)
        assertEquals(QuizQuestion.Leader, vm.state.question)
        assertNull(vm.state.equity, "worked out in the background, not on the spot")
        advanceUntilIdle()
        val equity = checkNotNull(vm.state.equity)
        assertEquals(100.0, equity.sum(), 1e-9)
        assertTrue(engineDispatcher.dispatches > 0, "the calculation ran on the engine's dispatcher, not the main one")
        assertFalse(vm.state.revealed, "nothing shows until the guess is in")
        val exact = OddsEngine(engineDispatcher).finalResult(OddsRequest(deal.hands.map { Seat(it) }, deal.board))
        assertEquals(exact.players.map { it.equityPct }, equity)
    }

    @Test
    fun `a right guess grows the streak and is saved, and a wrong one ends it and keeps the best`() = runVmTest {
        val vm = viewModel()
        dealClearSpot(vm)
        val leader = vm.state.leaders.single()
        vm.acceptIntent(EquityQuizIntent.Answer(QuizAnswer.Hand(leader)))
        assertTrue(vm.state.revealed)
        assertEquals(true, vm.state.right)
        assertEquals(QuizScore(streak = 1, best = 1, right = 1, answered = 1), vm.state.score)

        vm.acceptIntent(EquityQuizIntent.NextDeal)
        dealClearSpot(vm)
        val wrong = (0 until vm.state.hands).first { it !in vm.state.leaders }
        vm.acceptIntent(EquityQuizIntent.Answer(QuizAnswer.Hand(wrong)))
        assertEquals(false, vm.state.right)
        assertEquals(QuizScore(streak = 0, best = 1, right = 1, answered = 2), vm.state.score)
        assertEquals(vm.state.score, viewModel().state.score, "saved")
    }

    @Test
    fun `a guess made while the engine works shows once the odds are in, and counts once`() = runVmTest {
        val vm = viewModel()
        vm.acceptIntent(EquityQuizIntent.Answer(QuizAnswer.Hand(0)))
        assertTrue(vm.state.working)
        assertFalse(vm.state.revealed)
        assertEquals(0, vm.state.score.answered)
        advanceUntilIdle()
        assertTrue(vm.state.revealed)
        assertEquals(1, vm.state.score.answered)
        vm.acceptIntent(EquityQuizIntent.Answer(QuizAnswer.Hand(1)))
        assertEquals(QuizAnswer.Hand(0), vm.state.answer, "one guess a deal")
        assertEquals(1, vm.state.score.answered)
    }

    @Test
    fun `a new deal clears the guess, and odds for an old deal never land on a new one`() = runVmTest {
        val vm = viewModel()
        val first = vm.state.deal
        vm.acceptIntent(EquityQuizIntent.NextDeal)
        val second = checkNotNull(vm.state.deal)
        assertNotEquals(first, second)
        assertEquals(2, vm.state.deals)
        advanceUntilIdle()
        val exact = OddsEngine(engineDispatcher).finalResult(OddsRequest(second.hands.map { Seat(it) }, second.board))
        assertEquals(exact.players.map { it.equityPct }, vm.state.equity)
        assertNull(vm.state.answer)
    }

    @Test
    fun `the range question marks the first hand's equity, with both ranges counting on the line`() = runVmTest {
        val vm = viewModel()
        vm.acceptIntent(EquityQuizIntent.SetQuestion(QuizQuestion.Range))
        advanceUntilIdle()
        val first = checkNotNull(vm.state.equity).first()
        val right = QuizJudge.ranges(first).first()
        vm.acceptIntent(EquityQuizIntent.Answer(QuizAnswer.InRange(right)))
        assertEquals(true, vm.state.right)

        vm.acceptIntent(EquityQuizIntent.NextDeal)
        advanceUntilIdle()
        val miss = EquityRange.entries.first { it !in QuizJudge.ranges(checkNotNull(vm.state.equity).first()) }
        vm.acceptIntent(EquityQuizIntent.Answer(QuizAnswer.InRange(miss)))
        assertEquals(false, vm.state.right)
    }

    @Test
    fun `three hands and the question are saved, and changing either deals afresh`() = runVmTest {
        val vm = viewModel()
        val before = vm.state.deal
        vm.acceptIntent(EquityQuizIntent.SetHands(3))
        assertEquals(3, checkNotNull(vm.state.deal).hands.size)
        assertNotEquals(before, vm.state.deal)
        vm.acceptIntent(EquityQuizIntent.SetHands(7))
        assertEquals(3, vm.state.hands, "two or three")
        vm.acceptIntent(EquityQuizIntent.SetQuestion(QuizQuestion.Range))
        advanceUntilIdle()
        assertEquals(3, checkNotNull(vm.state.equity).size)

        val reborn = viewModel()
        assertEquals(3, reborn.state.hands)
        assertEquals(QuizQuestion.Range, reborn.state.question)
    }

    @Test
    fun `the same seeds deal the same spots`() = runVmTest {
        val first = viewModel().state.deal
        nextSeed = 1_000L
        assertEquals(first, viewModel().state.deal)
    }

    /** Hands work to [inner] and counts how often: proof the engine's own dispatcher ran it. */
    private class RecordingDispatcher(private val inner: CoroutineDispatcher) : CoroutineDispatcher() {
        var dispatches = 0

        override fun isDispatchNeeded(context: CoroutineContext): Boolean = inner.isDispatchNeeded(context)

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatches++
            inner.dispatch(context, block)
        }
    }
}
