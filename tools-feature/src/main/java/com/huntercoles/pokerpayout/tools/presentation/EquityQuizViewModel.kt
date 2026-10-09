package com.huntercoles.pokerpayout.tools.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.tools.poker.OddsEngine
import com.huntercoles.pokerpayout.tools.poker.OddsRequest
import com.huntercoles.pokerpayout.tools.poker.Seat
import com.huntercoles.pokerpayout.tools.quiz.EquityQuizStore
import com.huntercoles.pokerpayout.tools.quiz.QuizAnswer
import com.huntercoles.pokerpayout.tools.quiz.QuizDealer
import com.huntercoles.pokerpayout.tools.quiz.QuizDeal
import com.huntercoles.pokerpayout.tools.quiz.QuizJudge
import com.huntercoles.pokerpayout.tools.quiz.QuizQuestion
import com.huntercoles.pokerpayout.tools.quiz.QuizScore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

/** Where the quiz's deals come from. Tests fix them; the app shuffles afresh each time. */
fun interface QuizSeeds {
    fun next(): Long
}

/**
 * The equity quiz's state.
 *
 * @property deal the spot on the table, or null before the first deal.
 * @property equity each hand's real equity (percent), once the odds engine has worked it out.
 * @property wins how often each hand wins outright (percent); the rest of its equity is split pots.
 * @property answer the player's guess, or null while they think.
 * @property scored whether this deal's answer is in [score] already.
 * @property deals grows with each deal.
 */
data class EquityQuizUiState(
    val hands: Int = QuizDealer.MIN_HANDS,
    val question: QuizQuestion = QuizQuestion.Leader,
    val deal: QuizDeal? = null,
    val equity: List<Double>? = null,
    val wins: List<Double>? = null,
    val answer: QuizAnswer? = null,
    val score: QuizScore = QuizScore(),
    val scored: Boolean = false,
    val deals: Int = 0,
) {
    /** The guess is in and the odds are known: show who was ahead, and by how much. */
    val revealed: Boolean get() = answer != null && equity != null

    /** The guess is in but the engine is still working (a big preflop spot on a slow phone). */
    val working: Boolean get() = answer != null && equity == null

    /** Whether the guess was right, once [revealed]. */
    val right: Boolean? get() {
        val guess = answer ?: return null
        return equity?.let { QuizJudge.isRight(guess, it) }
    }

    /** The hands that count as ahead (more than one when it's too close to call). */
    val leaders: Set<Int> get() = equity?.let(QuizJudge::leaders).orEmpty()

    val closeCall: Boolean get() = equity?.let { QuizJudge.isCloseCall(question, it) } ?: false
}

sealed interface EquityQuizIntent {
    data class Answer(val answer: QuizAnswer) : EquityQuizIntent
    data object NextDeal : EquityQuizIntent
    data class SetHands(val hands: Int) : EquityQuizIntent
    data class SetQuestion(val question: QuizQuestion) : EquityQuizIntent
}

/**
 * The equity quiz: deals two or three hands face up (seeded from [QuizSeeds]), on a board of none,
 * three or four cards; the player guesses who's ahead, or how often the first hand wins; then the
 * real equities show, worked out exactly by the odds screen's [OddsEngine] (on its own background
 * dispatcher, never the main thread) while the player thinks. Right answers build a streak; the
 * best streak and the totals are saved in [EquityQuizStore].
 */
@HiltViewModel
class EquityQuizViewModel @Inject constructor(
    private val store: EquityQuizStore,
    private val engine: OddsEngine,
    private val seeds: QuizSeeds,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        EquityQuizUiState(hands = store.hands(), question = store.question(), score = store.score()),
    )
    val uiState: StateFlow<EquityQuizUiState> = _uiState.asStateFlow()

    private var work: Job? = null

    init {
        deal()
    }

    fun acceptIntent(intent: EquityQuizIntent) {
        when (intent) {
            is EquityQuizIntent.Answer -> answer(intent.answer)
            EquityQuizIntent.NextDeal -> deal()
            is EquityQuizIntent.SetHands -> {
                val hands = intent.hands.coerceIn(QuizDealer.MIN_HANDS, QuizDealer.MAX_HANDS)
                if (hands == _uiState.value.hands) return
                store.setHands(hands)
                _uiState.update { it.copy(hands = hands) }
                deal()
            }
            is EquityQuizIntent.SetQuestion -> {
                if (intent.question == _uiState.value.question) return
                store.setQuestion(intent.question)
                _uiState.update { it.copy(question = intent.question) }
                deal()
            }
        }
    }

    /** A new spot, and its odds worked out in the background. A guess on the last one that wasn't marked doesn't count. */
    private fun deal() {
        work?.cancel()
        val deal = QuizDealer.deal(_uiState.value.hands, Random(seeds.next()))
        _uiState.update { it.copy(deal = deal, equity = null, wins = null, answer = null, scored = false, deals = it.deals + 1) }
        work = viewModelScope.launch {
            val result = engine.finalResult(OddsRequest(seats = deal.hands.map { Seat(it) }, board = deal.board))
            val equity = result.players.map { it.equityPct }
            val wins = result.players.map { it.winPct }
            _uiState.update { if (it.deal == deal) it.copy(equity = equity, wins = wins) else it }
            mark()
        }
    }

    private fun answer(answer: QuizAnswer) {
        val state = _uiState.value
        if (state.deal == null || state.answer != null) return
        _uiState.update { it.copy(answer = answer) }
        mark()
    }

    /** Once both the guess and the odds are in, counts the answer: once a deal. */
    private fun mark() {
        val state = _uiState.value
        val right = state.right ?: return
        if (state.scored) return
        val score = state.score.after(right)
        store.setScore(score)
        _uiState.update { it.copy(score = score, scored = true) }
    }
}
