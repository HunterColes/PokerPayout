package com.huntercoles.pokerpayout.tools.quiz

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The equity quiz, saved on the phone: how many hands and which question, and the score (the
 * streak, the best streak, and right of all answered). Written once per answer, never while dealing.
 */
@Singleton
class EquityQuizStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hands(): Int = prefs.getInt(KEY_HANDS, QuizDealer.MIN_HANDS).coerceIn(QuizDealer.MIN_HANDS, QuizDealer.MAX_HANDS)

    fun setHands(hands: Int) {
        prefs.edit().putInt(KEY_HANDS, hands).apply()
    }

    fun question(): QuizQuestion = QuizQuestion.of(prefs.getString(KEY_QUESTION, null))

    fun setQuestion(question: QuizQuestion) {
        prefs.edit().putString(KEY_QUESTION, question.key).apply()
    }

    fun score(): QuizScore = QuizScore(
        streak = prefs.getInt(KEY_STREAK, 0).coerceAtLeast(0),
        best = prefs.getInt(KEY_BEST, 0).coerceAtLeast(0),
        right = prefs.getInt(KEY_RIGHT, 0).coerceAtLeast(0),
        answered = prefs.getInt(KEY_ANSWERED, 0).coerceAtLeast(0),
    )

    fun setScore(score: QuizScore) {
        prefs.edit()
            .putInt(KEY_STREAK, score.streak)
            .putInt(KEY_BEST, score.best)
            .putInt(KEY_RIGHT, score.right)
            .putInt(KEY_ANSWERED, score.answered)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "equity_quiz_prefs"
        const val KEY_HANDS = "hands"
        const val KEY_QUESTION = "question"
        const val KEY_STREAK = "streak"
        const val KEY_BEST = "best_streak"
        const val KEY_RIGHT = "right"
        const val KEY_ANSWERED = "answered"
    }
}
