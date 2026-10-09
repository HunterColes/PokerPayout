package com.huntercoles.pokerpayout.tools.quiz

import com.huntercoles.pokerpayout.tools.poker.Cards
import kotlin.random.Random

/** What the quiz asks: which hand is ahead, or how often the first hand wins (a range of equity). */
enum class QuizQuestion(val key: String) {
    Leader("leader"),
    Range("range"),
    ;

    companion object {
        fun of(key: String?): QuizQuestion = entries.firstOrNull { it.key == key } ?: Leader
    }
}

/** A guess at the first hand's equity: a fifth of the way from 0 to 100%, in order. */
enum class EquityRange {
    Under20,
    From20,
    From40,
    From60,
    Over80,
    ;

    /** Where the range starts, in percent: 0, 20, 40, 60 or 80. */
    val from: Int get() = ordinal * WIDTH

    /** Where it ends, in percent. */
    val to: Int get() = from + WIDTH

    private companion object {
        const val WIDTH = 20
    }
}

/** A guess: the hand that's ahead, or the range the first hand's equity is in. */
sealed interface QuizAnswer {
    data class Hand(val index: Int) : QuizAnswer
    data class InRange(val range: EquityRange) : QuizAnswer
}

/** A spot to guess: two or three hands, face up, and a board of none, three or four cards. */
data class QuizDeal(val hands: List<List<Int>>, val board: List<Int>)

/** Deals the quiz's spots from a shuffled deck. Same [Random], same spots. */
object QuizDealer {
    const val MIN_HANDS = 2
    const val MAX_HANDS = 3

    /** The streets a spot is dealt on, as board sizes: before the flop, on the flop, on the turn. */
    val BOARDS = listOf(0, 3, 4)

    fun deal(hands: Int, random: Random): QuizDeal {
        require(hands in MIN_HANDS..MAX_HANDS) { "The quiz deals $MIN_HANDS or $MAX_HANDS hands" }
        val deck = (0 until Cards.COUNT).shuffled(random)
        val dealt = List(hands) { seat -> deck.subList(seat * 2, seat * 2 + 2).sortedDescending() }
        val boardSize = BOARDS[random.nextInt(BOARDS.size)]
        val board = deck.subList(hands * 2, hands * 2 + boardSize).toList()
        return QuizDeal(dealt, board)
    }
}

/** Marks a guess against the real equities (in percent, one per hand). */
object QuizJudge {
    /**
     * Within this many points two hands are too close to call, and a range's edge too close to
     * argue over: either answer counts.
     */
    const val CLOSE_CALL = 1.0

    /** Every hand within [CLOSE_CALL] of the best: any of them is "ahead". */
    fun leaders(equity: List<Double>): Set<Int> {
        val best = equity.max()
        return equity.indices.filter { best - equity[it] <= CLOSE_CALL }.toSet()
    }

    /** The range [equity] is in, and the next one too when it's within [CLOSE_CALL] of the edge. */
    fun ranges(equity: Double): Set<EquityRange> =
        EquityRange.entries.filter { equity >= it.from - CLOSE_CALL && equity <= it.to + CLOSE_CALL }.toSet()

    fun isRight(answer: QuizAnswer, equity: List<Double>): Boolean = when (answer) {
        is QuizAnswer.Hand -> answer.index in leaders(equity)
        is QuizAnswer.InRange -> answer.range in ranges(equity.first())
    }

    /** Whether the spot was close enough that more than one answer counts. */
    fun isCloseCall(question: QuizQuestion, equity: List<Double>): Boolean = when (question) {
        QuizQuestion.Leader -> leaders(equity).size > 1
        QuizQuestion.Range -> ranges(equity.first()).size > 1
    }
}

/**
 * The player's score: right answers in a row now ([streak]) and at best, and right of all answered.
 */
data class QuizScore(val streak: Int = 0, val best: Int = 0, val right: Int = 0, val answered: Int = 0) {
    /** The score after one more answer. */
    fun after(right: Boolean): QuizScore {
        val nextStreak = if (right) streak + 1 else 0
        return QuizScore(
            streak = nextStreak,
            best = maxOf(best, nextStreak),
            right = this.right + if (right) 1 else 0,
            answered = answered + 1,
        )
    }
}
