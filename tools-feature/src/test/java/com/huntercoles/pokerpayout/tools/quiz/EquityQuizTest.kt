package com.huntercoles.pokerpayout.tools.quiz

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** The quiz's seeded deals, how a guess is marked, and the score. */
class EquityQuizTest {

    @Test
    fun `a deal is two or three hands of two cards and a board of none, three or four, never a card twice`() {
        val random = Random(1)
        val boards = mutableSetOf<Int>()
        repeat(3_000) { n ->
            val hands = if (n % 2 == 0) 2 else 3
            val deal = QuizDealer.deal(hands, random)
            assertEquals(hands, deal.hands.size)
            assertTrue(deal.hands.all { it.size == 2 })
            assertTrue(deal.board.size in QuizDealer.BOARDS)
            boards += deal.board.size
            val cards = deal.hands.flatten() + deal.board
            assertEquals(cards.size, cards.toSet().size)
            assertTrue(cards.all { it in 0 until 52 })
        }
        assertEquals(setOf(0, 3, 4), boards, "every street comes up")
    }

    @Test
    fun `the same seed deals the same spot`() {
        assertEquals(QuizDealer.deal(3, Random(99)), QuizDealer.deal(3, Random(99)))
    }

    @Test
    fun `who's ahead is the best equity, and both hands count when it's too close to call`() {
        assertEquals(setOf(1), QuizJudge.leaders(listOf(36.4, 63.6)))
        assertEquals(setOf(0, 1), QuizJudge.leaders(listOf(50.4, 49.6)), "0.8 points apart")
        assertEquals(setOf(2), QuizJudge.leaders(listOf(20.0, 30.0, 50.0)))
        assertTrue(QuizJudge.isRight(QuizAnswer.Hand(0), listOf(50.4, 49.6)))
        assertTrue(QuizJudge.isRight(QuizAnswer.Hand(1), listOf(50.4, 49.6)))
        assertFalse(QuizJudge.isRight(QuizAnswer.Hand(0), listOf(36.4, 63.6)))
        assertTrue(QuizJudge.isCloseCall(QuizQuestion.Leader, listOf(50.4, 49.6)))
        assertFalse(QuizJudge.isCloseCall(QuizQuestion.Leader, listOf(36.4, 63.6)))
    }

    @Test
    fun `the first hand's equity is in one range, or two when it's on the line`() {
        assertEquals(setOf(EquityRange.From60), QuizJudge.ranges(63.6))
        assertEquals(setOf(EquityRange.Under20), QuizJudge.ranges(0.0))
        assertEquals(setOf(EquityRange.Over80), QuizJudge.ranges(100.0))
        assertEquals(setOf(EquityRange.From40, EquityRange.From60), QuizJudge.ranges(60.5))
        assertEquals(setOf(EquityRange.Under20, EquityRange.From20), QuizJudge.ranges(19.2))
        assertTrue(QuizJudge.isRight(QuizAnswer.InRange(EquityRange.From40), listOf(60.5, 39.5)))
        assertFalse(QuizJudge.isRight(QuizAnswer.InRange(EquityRange.Over80), listOf(60.5, 39.5)))
        assertTrue(QuizJudge.isCloseCall(QuizQuestion.Range, listOf(60.5, 39.5)))
        // Every equity from 0 to 100 is in at least one range
        (0..1_000).forEach { assertTrue(QuizJudge.ranges(it / 10.0).isNotEmpty()) }
    }

    @Test
    fun `a right answer grows the streak and the best, a wrong one ends the streak but keeps the best`() {
        val score = QuizScore().after(true).after(true).after(true).after(false).after(true)
        assertEquals(QuizScore(streak = 1, best = 3, right = 4, answered = 5), score)
    }

    @Test
    fun `the question is saved by a key that never changes`() {
        assertEquals(listOf("leader", "range"), QuizQuestion.entries.map { it.key })
        assertEquals(QuizQuestion.Range, QuizQuestion.of("range"))
        assertEquals(QuizQuestion.Leader, QuizQuestion.of("something else"))
        assertEquals(QuizQuestion.Leader, QuizQuestion.of(null))
    }
}
