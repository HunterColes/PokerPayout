package com.huntercoles.pokerpayout.core.tip

import com.huntercoles.pokerpayout.core.testing.expect
import com.huntercoles.pokerpayout.core.testing.forAll
import io.kotest.property.Arb
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.list
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The "Tip the dealer?" card's rules (PP-112): the third night saved, at most two cards ever, three
 * saves apart; never on the first run or while the clock runs (the save waits for the next one);
 * "Don't ask again" and "Leave a tip" end it for good with one tap; "Not now" puts one card away.
 * Then a property over random evenings: whatever the host does, those rules hold.
 */
class TipAskTest {

    /** [count] saves on a later run with the clock stopped, nights numbered on from [firstId]. */
    private fun TipAsk.saves(count: Int, firstId: Long = nightsSaved + 1L): TipAsk =
        (0 until count).fold(this) { ask, i -> ask.afterSave(firstId + i, clockRunning = false, firstRun = false) }

    @Test
    fun theCardComesWithTheThirdNightSavedAndNotBefore() {
        val two = TipAsk().saves(2)
        assertNull(two.cardNight)
        assertEquals(0, two.asksShown)
        val three = two.saves(1)
        assertEquals(3L, three.cardNight)
        assertEquals(1, three.asksShown)
    }

    @Test
    fun atMostTwoCardsEverThreeSavesApart() {
        var ask = TipAsk().saves(3).notNow()
        ask = ask.saves(2)
        assertNull(ask.cardNight, "the 4th and 5th saves are too soon")
        ask = ask.saves(1)
        assertEquals(6L, ask.cardNight, "the second card comes with the 6th save")
        ask = ask.notNow().saves(50)
        assertNull(ask.cardNight)
        assertEquals(TipAsk.MAX_ASKS, ask.asksShown)
    }

    @Test
    fun ignoredCardsCountToo() {
        // A card nobody answered goes with its night, and counts as one of the two
        val ask = TipAsk().saves(3).saves(3).saves(30)
        assertEquals(2, ask.asksShown)
        assertNull(ask.cardNight)
    }

    @Test
    fun neverWhileTheClockRunsTheSaveWaitsForTheNextOne() {
        val running = TipAsk().saves(2).afterSave(3L, clockRunning = true, firstRun = false)
        assertNull(running.cardNight)
        assertEquals(0, running.asksShown, "an ask the clock held back isn't counted")
        assertEquals(4L, running.saves(1).cardNight)
    }

    @Test
    fun neverOnTheFirstRun() {
        val firstRun = (1L..5L).fold(TipAsk()) { ask, id -> ask.afterSave(id, clockRunning = false, firstRun = true) }
        assertNull(firstRun.cardNight)
        assertEquals(0, firstRun.asksShown)
        assertEquals(5, firstRun.nightsSaved, "the nights still count")
        assertEquals(6L, firstRun.saves(1).cardNight, "the next run's first save brings it")
    }

    @Test
    fun dontAskAgainEndsItForGoodInOneTap() {
        val stopped = TipAsk().saves(3).stop()
        assertNull(stopped.cardNight)
        assertTrue(stopped.stopped)
        assertNull(stopped.saves(100).cardNight)
        assertFalse(stopped.saves(100).due)
    }

    @Test
    fun aStopBeforeAnyCardMeansNoCardAtAll() {
        // "Leave a tip" or a copied address on the Tip the dealer page, before the third night
        assertNull(TipAsk().saves(1).stop().saves(10).cardNight)
    }

    @Test
    fun anotherNightSavedTakesAnOldCardAway() {
        // The card belongs to the night it came with; a later save without one clears it
        val ask = TipAsk().saves(3).saves(1)
        assertNull(ask.cardNight)
        assertEquals(1, ask.asksShown)
    }

    /** Something the host does, in a random evening. */
    private sealed interface Event {
        data class Save(val clockRunning: Boolean) : Event
        data object Restart : Event
        data object NotNow : Event
        data object Stop : Event
    }

    private val events = Arb.list(
        Arb.choice(
            Arb.constant(Event.Save(clockRunning = false)),
            Arb.constant(Event.Save(clockRunning = false)),
            Arb.constant(Event.Save(clockRunning = true)),
            Arb.constant(Event.Restart),
            Arb.constant(Event.NotNow),
            Arb.constant(Event.Stop),
        ),
        0..60,
    )

    @Test
    fun `whatever the host does, the card keeps its promises`() =
        forAll(seed = 2026_1009_112L, iterations = 2_000, gen = events) { evening ->
            var ask = TipAsk()
            var firstRun = true
            var stoppedAt: Int? = null
            val shownAt = mutableListOf<Int>() // save counts that brought a card
            evening.forEachIndexed { step, event ->
                when (event) {
                    is Event.Save -> {
                        val next = ask.afterSave(ask.nightsSaved + 1L, event.clockRunning, firstRun)
                        if (next.asksShown > ask.asksShown) {
                            expect(!event.clockRunning) { "a card while the clock ran, at step $step" }
                            expect(!firstRun) { "a card on the first run, at step $step" }
                            expect(stoppedAt == null) { "a card after the host said no, at step $step" }
                            expect(next.cardNight == next.nightsSaved.toLong()) { "the card isn't under tonight's night" }
                            shownAt += next.nightsSaved
                        } else {
                            expect(next.cardNight == null) { "an old card stayed after another save, at step $step" }
                        }
                        ask = next
                    }
                    Event.Restart -> firstRun = false
                    Event.NotNow -> ask = ask.notNow()
                    Event.Stop -> {
                        ask = ask.stop()
                        if (stoppedAt == null) stoppedAt = step
                    }
                }
            }
            expect(shownAt.size <= TipAsk.MAX_ASKS) { "${shownAt.size} cards: $shownAt" }
            expect(shownAt.firstOrNull()?.let { it >= TipAsk.FIRST_ASK_AT } ?: true) { "a card before the third save: $shownAt" }
            expect(shownAt.zipWithNext().all { (a, b) -> b - a >= TipAsk.NIGHTS_BETWEEN_ASKS }) { "cards too close: $shownAt" }
            expect(ask.asksShown == shownAt.size) { "counted ${ask.asksShown}, shown ${shownAt.size}" }
        }
}
