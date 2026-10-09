package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.tools.dealers.BuiltInGame
import com.huntercoles.pokerpayout.tools.dealers.GameChoice
import com.huntercoles.pokerpayout.tools.presentation.DealersChoiceIntent
import com.huntercoles.pokerpayout.tools.presentation.DealersChoiceUiState
import com.huntercoles.pokerpayout.tools.presentation.EquityQuizIntent
import com.huntercoles.pokerpayout.tools.presentation.EquityQuizUiState
import com.huntercoles.pokerpayout.tools.presentation.ShotClockIntent
import com.huntercoles.pokerpayout.tools.presentation.ShotClockUiState
import com.huntercoles.pokerpayout.tools.quiz.EquityRange
import com.huntercoles.pokerpayout.tools.quiz.QuizAnswer
import com.huntercoles.pokerpayout.tools.quiz.QuizQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the shot clock's, dealer's choice's and the quiz's controls send, and what TalkBack hears. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w360dp-h780dp-port-xhdpi")
class FunToolsContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val shotClockIntents = mutableListOf<ShotClockIntent>()
    private val dealersIntents = mutableListOf<DealersChoiceIntent>()
    private val quizIntents = mutableListOf<EquityQuizIntent>()

    private fun showShotClock(state: ShotClockUiState) = compose.setContent {
        PokerTheme(reducedMotion = true) { ShotClockContent(state, onIntent = { shotClockIntents += it }, onBack = {}) }
    }

    private fun showDealers(state: DealersChoiceUiState) = compose.setContent {
        PokerTheme(reducedMotion = true) { DealersChoiceContent(state, onIntent = { dealersIntents += it }, onBack = {}) }
    }

    private fun showQuiz(state: EquityQuizUiState) = compose.setContent {
        PokerTheme(reducedMotion = true) { EquityQuizContent(state, onIntent = { quizIntents += it }, onBack = {}) }
    }

    // Shot clock

    @Test
    fun theFaceIsOneBigButtonThatStartsTheNextDecision() {
        showShotClock(ShotClockFixtures.ready)
        compose.onNodeWithContentDescription("Shot clock, 30 seconds left").performClick()
        compose.onNodeWithText("Tap the clock to start").assertExists()
        assertEquals(listOf<ShotClockIntent>(ShotClockIntent.NextDecision), shotClockIntents)
    }

    @Test
    fun beforeTheFirstTapThereIsNothingToPauseAndNoCardToPlay() {
        showShotClock(ShotClockFixtures.ready)
        compose.onNodeWithText("Pause").assertDoesNotExist()
        compose.onNodeWithText("Reset").assertDoesNotExist()
        compose.onNodeWithContentDescription("Play a card for Dana, 30 more seconds").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun lowOnTimeTheLineSaysSoAndCardsCanBePlayed() {
        showShotClock(ShotClockFixtures.low)
        compose.onNodeWithContentDescription("Shot clock, 8 seconds left").assertExists()
        compose.onNodeWithText("Last ten seconds").assertExists()
        compose.onNodeWithText("Pause").performClick()
        compose.onNodeWithText("Reset").performClick()
        compose.onNodeWithContentDescription("Play a card for Marcus, 30 more seconds").performScrollTo()
            .assertIsEnabled()
            .performClick()
        compose.onNodeWithContentDescription("Play a card for Theo, 30 more seconds").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("1 of 2 cards left").assertExists()
        compose.onNodeWithText("Give everyone their cards back").performScrollTo().performClick()
        assertEquals(
            listOf(ShotClockIntent.Pause, ShotClockIntent.Reset, ShotClockIntent.PlayCard(1), ShotClockIntent.GiveCardsBack),
            shotClockIntents,
        )
    }

    @Test
    fun pausedItOffersResumeAndTimeUpSaysSo() {
        showShotClock(ShotClockFixtures.paused)
        compose.onNodeWithContentDescription("Shot clock, 21 seconds left, paused").assertExists()
        compose.onNodeWithText("Resume").performClick()
        assertEquals(listOf<ShotClockIntent>(ShotClockIntent.Resume), shotClockIntents)
    }

    @Test
    fun timeUpTheFaceSaysTime() {
        showShotClock(ShotClockFixtures.timeUp)
        compose.onNodeWithContentDescription("Shot clock, time is up").assertExists()
        compose.onNodeWithText("Time's up. Tap the clock for the next decision.").assertExists()
    }

    @Test
    fun theTimeToActAndTheCardsEachAreSettings() {
        showShotClock(ShotClockFixtures.ready)
        compose.onNodeWithText("45 s").performClick()
        compose.onNodeWithContentDescription("Increase Cards each").performScrollTo().performClick()
        assertEquals(listOf(ShotClockIntent.SetSeconds(45), ShotClockIntent.SetCardsEach(3)), shotClockIntents)
    }

    // Dealer's choice

    @Test
    fun theWheelAndTheButtonBothSpin() {
        showDealers(DealersFixtures.fresh)
        compose.onNodeWithContentDescription("Game wheel, 9 games").performClick()
        compose.onNodeWithText("Spin the wheel").performClick()
        compose.onNodeWithText("Spin the wheel to pick the next game.").assertExists()
        assertEquals(listOf<DealersChoiceIntent>(DealersChoiceIntent.Spin, DealersChoiceIntent.Spin), dealersIntents)
    }

    @Test
    fun thePickShowsWithItsRules() {
        showDealers(DealersFixtures.picked)
        compose.onNodeWithContentDescription("Next game: Badugi").performScrollTo().assertExists()
        compose.onNodeWithText("Any badugi beats any three-card hand. The best is A-2-3-4 in four suits.").performScrollTo()
        compose.onNodeWithText("DRAW").assertExists()
    }

    @Test
    fun aSpinTurnsTheWheelAndRevealsThePick() {
        var state by mutableStateOf(DealersFixtures.fresh)
        compose.setContent {
            PokerTheme(reducedMotion = true) { DealersChoiceContent(state, onIntent = {}, onBack = {}) }
        }
        state = state.copy(pickId = BuiltInGame.Razz.id, spins = 1)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Next game: Razz").performScrollTo().assertExists()
    }

    @Test
    fun gamesGoOnAndOffTheWheelAndHouseGamesComeAndGo() {
        showDealers(DealersFixtures.picked)
        compose.onNode(hasText("Irish") and hasContentDescription("Irish")).performScrollTo().assertIsOff().performClick()
        compose.onNode(hasContentDescription("Badugi")).performScrollTo().assertIsOn().performClick()
        compose.onNodeWithContentDescription("Remove Kings and Little Ones").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Add a house game").performScrollTo().performTextInput("Guts")
        compose.onNodeWithText("Add").performClick()
        assertEquals(
            listOf(
                DealersChoiceIntent.SetOnWheel(BuiltInGame.Irish.id, true),
                DealersChoiceIntent.SetOnWheel(BuiltInGame.Badugi.id, false),
                DealersChoiceIntent.RemoveHouseGame(GameChoice.houseId("Kings and Little Ones")),
                DealersChoiceIntent.AddHouseGame("Guts"),
            ),
            dealersIntents,
        )
    }

    @Test
    fun withTooFewGamesTheWheelWontSpin() {
        showDealers(DealersFixtures.tooFew)
        compose.onNodeWithText("Spin the wheel").assertIsNotEnabled()
        compose.onNodeWithText("Put at least two games on the wheel to spin it.").assertExists()
    }

    // Equity quiz

    @Test
    fun askedWhosAheadEachHandIsTheButton() {
        showQuiz(QuizFixtures.ask)
        compose.onNodeWithText("Who's ahead?").assertExists()
        compose.onNodeWithContentDescription("Hand B: 9 of hearts, 9 of diamonds").performClick()
        assertEquals(listOf<EquityQuizIntent>(EquityQuizIntent.Answer(QuizAnswer.Hand(1))), quizIntents)
    }

    @Test
    fun askedHowOftenTheRangesAreTheButtons() {
        showQuiz(QuizFixtures.rangeAsk)
        compose.onNodeWithText("How often does Hand A win?").performScrollTo()
        compose.onNodeWithText("40–60%").performScrollTo().performClick()
        assertEquals(listOf<EquityQuizIntent>(EquityQuizIntent.Answer(QuizAnswer.InRange(EquityRange.From40))), quizIntents)
    }

    @Test
    fun answeredTheOddsShowWithTheVerdictAndDealAgain() {
        showQuiz(QuizFixtures.right)
        compose.onNodeWithText("Right!").performScrollTo()
        val leader = QuizFixtures.right.leaders.first()
        val equity = OddsFormat.oneDecimal(checkNotNull(QuizFixtures.right.equity)[leader])
        compose.onNode(hasContentDescription("$equity%", substring = true) and hasContentDescription("Ahead", substring = true))
            .assertExists()
        compose.onNodeWithText("Deal again").performScrollTo().performClick()
        assertEquals(listOf<EquityQuizIntent>(EquityQuizIntent.NextDeal), quizIntents)
    }

    @Test
    fun aWrongRangeSaysSoAndNamesThePick() {
        showQuiz(QuizFixtures.rangeWrong)
        compose.onNodeWithText("Not this time").performScrollTo()
        compose.onNodeWithText("Your pick:", substring = true).performScrollTo()
        compose.onNodeWithText("Hand A wins", substring = true).assertExists()
    }

    @Test
    fun whileTheEngineWorksTheGuessWaits() {
        showQuiz(QuizFixtures.working)
        compose.onNodeWithText("Working out the odds…").performScrollTo()
        compose.onNodeWithContentDescription("Hand B: 9 of hearts, 9 of diamonds, Your pick").assertExists()
        assertTrue(quizIntents.isEmpty())
    }

    @Test
    fun theSettingsDealAfresh() {
        showQuiz(QuizFixtures.ask)
        compose.onNodeWithText("Three hands").performScrollTo().performClick()
        compose.onNodeWithText("How often").performScrollTo().performClick()
        assertEquals(
            listOf(EquityQuizIntent.SetHands(3), EquityQuizIntent.SetQuestion(QuizQuestion.Range)),
            quizIntents,
        )
    }
}
