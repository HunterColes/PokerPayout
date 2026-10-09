package com.huntercoles.pokerpayout.tools.presentation.composable

import com.huntercoles.pokerpayout.tools.dealers.BuiltInGame
import com.huntercoles.pokerpayout.tools.dealers.GameChoice
import com.huntercoles.pokerpayout.tools.poker.Cards
import com.huntercoles.pokerpayout.tools.poker.OddsEngine
import com.huntercoles.pokerpayout.tools.poker.OddsRequest
import com.huntercoles.pokerpayout.tools.poker.Seat
import com.huntercoles.pokerpayout.tools.presentation.DealersChoiceUiState
import com.huntercoles.pokerpayout.tools.presentation.EquityQuizUiState
import com.huntercoles.pokerpayout.tools.presentation.ShotClockUiState
import com.huntercoles.pokerpayout.tools.presentation.TimeBankPlayer
import com.huntercoles.pokerpayout.tools.quiz.EquityRange
import com.huntercoles.pokerpayout.tools.quiz.QuizAnswer
import com.huntercoles.pokerpayout.tools.quiz.QuizDeal
import com.huntercoles.pokerpayout.tools.quiz.QuizJudge
import com.huntercoles.pokerpayout.tools.quiz.QuizQuestion
import com.huntercoles.pokerpayout.tools.quiz.QuizScore
import com.huntercoles.pokerpayout.tools.shotclock.Countdown
import com.huntercoles.pokerpayout.tools.shotclock.ShotClockPhase
import kotlinx.coroutines.runBlocking
import kotlin.math.abs

/** The mockups' nine players, for the shot clock's time bank. */
private val Players = listOf("Dana", "Marcus", "Priya", "Theo", "Jo", "Sam", "Alex", "Rita", "Ben")

/** The shot clock's screens: before the first tap, mid-decision, low on time, paused, out of time. */
internal object ShotClockFixtures {
    val ready = ShotClockUiState(seconds = 30, cardsEach = 2, players = Players.map { TimeBankPlayer(it, 2) })

    /** Eight seconds left of 30; Marcus has played a card, Theo both of his. */
    val low: ShotClockUiState = ready.copy(
        countdown = Countdown.startedAt(30_000, now = 0),
        leftMillis = 8_000,
        phase = ShotClockPhase.Running,
        players = Players.map { name ->
            TimeBankPlayer(name, cardsLeft = mapOf("Marcus" to 1, "Theo" to 0)[name] ?: 2)
        },
    )

    val paused: ShotClockUiState = low.copy(
        countdown = Countdown.startedAt(45_000, now = 0).pausedAt(24_000),
        seconds = 45,
        leftMillis = 21_000,
        phase = ShotClockPhase.Paused,
    )

    val timeUp: ShotClockUiState = low.copy(leftMillis = 0, phase = ShotClockPhase.TimeUp)

    val noTimeBank = ShotClockUiState(seconds = 60, cardsEach = 0, players = Players.map { TimeBankPlayer(it, 0) })
}

/** Eight house games: as many as a wheel holds. */
private val HouseGames = listOf(
    "Guts",
    "Anaconda",
    "Baseball",
    "Night Baseball",
    "Chicago Low",
    "Screw Your Neighbour",
    "Acey Deucey",
    "Midnight",
)

/** Dealer's choice: the first wheel before a spin, Badugi picked with a house game, and the edge cases. */
internal object DealersFixtures {
    private val firstWheel = BuiltInGame.entries.map { GameChoice.builtIn(it, it.onWheelAtFirst) }

    val fresh = DealersChoiceUiState(games = firstWheel)

    val picked = DealersChoiceUiState(
        games = firstWheel + GameChoice.house("Kings and Little Ones", onWheel = true),
        pickId = BuiltInGame.Badugi.id,
        landing = 0.18f,
        spins = 0,
    )

    val housePicked = picked.copy(pickId = GameChoice.houseId("Kings and Little Ones"))

    val tooFew = DealersChoiceUiState(games = firstWheel.map { it.copy(onWheel = it.game == BuiltInGame.HoldEm) })

    val houseFull = DealersChoiceUiState(
        games = firstWheel + HouseGames.map { GameChoice.house(it, onWheel = true) },
        pickId = GameChoice.houseId("Night Baseball"),
    )
}

/**
 * The equity quiz: a heads-up flop to guess, the same answered right, and three hands answered
 * wrong. The odds are the engine's own, worked out exactly, so the goldens show real numbers.
 */
internal object QuizFixtures {
    private fun cards(text: String) = Cards.parseAll(text)

    /** The engine's exact equities and outright wins for [deal], in percent. */
    private fun odds(deal: QuizDeal): Pair<List<Double>, List<Double>> = runBlocking {
        val result = OddsEngine().finalResult(OddsRequest(deal.hands.map { Seat(it) }, deal.board))
        result.players.map { it.equityPct } to result.players.map { it.winPct }
    }

    /** A♠K♠ against 9♥9♦ on a J♠10♦2♠ flop: a pair against a big draw. */
    private val flop = QuizDeal(hands = listOf(cards("As Ks"), cards("9h 9d")), board = cards("Js Td 2s"))
    private val flopOdds = odds(flop)

    val ask = EquityQuizUiState(
        hands = 2,
        question = QuizQuestion.Leader,
        deal = flop,
        score = QuizScore(streak = 3, best = 7, right = 12, answered = 15),
        deals = 16,
    )

    val right: EquityQuizUiState = ask.copy(
        equity = flopOdds.first,
        wins = flopOdds.second,
        answer = QuizAnswer.Hand(QuizJudge.leaders(flopOdds.first).first()),
        score = QuizScore(streak = 4, best = 7, right = 13, answered = 16),
        scored = true,
    )

    val working: EquityQuizUiState = ask.copy(answer = QuizAnswer.Hand(1))

    /** Three hands before the flop, asked how often the first wins. */
    private val preflop = QuizDeal(hands = listOf(cards("Qh Qc"), cards("Ad Kc"), cards("7s 6s")), board = emptyList())
    private val preflopOdds = odds(preflop)

    val rangeAsk = EquityQuizUiState(
        hands = 3,
        question = QuizQuestion.Range,
        deal = preflop,
        score = QuizScore(streak = 4, best = 7, right = 13, answered = 16),
    )

    /** The same, answered one range off. */
    val rangeWrong: EquityQuizUiState = rangeAsk.copy(
        equity = preflopOdds.first,
        wins = preflopOdds.second,
        answer = QuizAnswer.InRange(nearMiss(preflopOdds.first.first())),
        score = QuizScore(streak = 0, best = 7, right = 13, answered = 17),
        scored = true,
    )

    /** The wrong range nearest [equity]: the answer someone nearly got right. */
    private fun nearMiss(equity: Double): EquityRange {
        val counted = QuizJudge.ranges(equity)
        return EquityRange.entries.filter { it !in counted }.minBy { abs((it.from + it.to) / 2.0 - equity) }
    }
}
