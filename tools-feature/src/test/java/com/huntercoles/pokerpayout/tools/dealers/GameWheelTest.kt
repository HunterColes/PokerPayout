package com.huntercoles.pokerpayout.tools.dealers

import com.huntercoles.pokerpayout.tools.presentation.composable.restingAngle
import com.huntercoles.pokerpayout.tools.presentation.composable.spinTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.floor
import kotlin.random.Random

/** The wheel's draw (seeded), and the geometry that turns it to the slice it picked. */
class GameWheelTest {

    private fun wheel(count: Int) = BuiltInGame.entries.take(count).map { GameChoice.builtIn(it, onWheel = true) }

    @Test
    fun `every game is as likely, and the same seed picks the same game`() {
        val games = wheel(9)
        val random = Random(2026)
        val counts = IntArray(games.size)
        repeat(SPINS) { counts[GameWheel.spin(games, last = null, random).index]++ }
        val expected = SPINS / games.size
        counts.forEach { assertTrue(it in expected * 9 / 10..expected * 11 / 10, "${counts.toList()}") }
        assertEquals(GameWheel.spin(games, null, Random(7)), GameWheel.spin(games, null, Random(7)))
    }

    @Test
    fun `it never picks the game just played twice running`() {
        val games = wheel(3)
        val random = Random(11)
        var last: String? = null
        repeat(2_000) {
            val picked = games[GameWheel.spin(games, last, random).index].id
            assertNotEquals(last, picked)
            last = picked
        }
        val two = wheel(2)
        repeat(50) { assertEquals(1, GameWheel.spin(two, two[0].id, random).index, "with two games it alternates") }
    }

    @Test
    fun `it stops well inside a slice, never on a line`() {
        val random = Random(3)
        repeat(1_000) {
            val landing = GameWheel.spin(wheel(5), null, random).landing
            assertTrue(landing in -0.35f..0.35f, "$landing")
        }
    }

    @Test
    fun `the wheel can't spin with fewer than two games`() {
        val thrown = runCatching { GameWheel.spin(wheel(1), null, Random(1)) }.exceptionOrNull()
        assertTrue(thrown is IllegalArgumentException)
    }

    @Test
    fun `at rest, the slice picked is the one under the pointer`() {
        val random = Random(5)
        listOf(2, 3, 9, 17, 25).forEach { slices ->
            repeat(200) {
                val index = random.nextInt(slices)
                val landing = (random.nextFloat() * 2 - 1) * 0.35f
                val angle = restingAngle(index, slices, landing)
                assertTrue(angle in 0f..360f)
                assertEquals(index, sliceUnderPointer(angle, slices), "slice $index of $slices, landing $landing")
            }
        }
    }

    @Test
    fun `a spin always turns forwards, at least five times round, and stops at rest on the pick`() {
        val random = Random(9)
        repeat(200) {
            val from = random.nextFloat() * 5_000f
            val resting = restingAngle(random.nextInt(9), 9, 0.1f)
            val target = spinTarget(from, resting)
            assertTrue(target - from >= 5 * 360f && target - from < 6 * 360f)
            assertEquals(resting, ((target % 360f) + 360f) % 360f, 0.01f)
        }
    }

    @Test
    fun `house games are known by their name whatever its capitals`() {
        assertEquals(GameChoice.houseId("Kings Wild"), GameChoice.houseId("  kings WILD "))
        assertNotEquals(GameChoice.houseId("Kings Wild"), BuiltInGame.HoldEm.id)
    }

    @Test
    fun `the app's games keep their saved ids`() {
        // Saved with the wheel: renaming one would drop it from every saved wheel.
        assertEquals(
            listOf(
                "holdem", "omaha", "omaha_hilo", "big_o", "stud", "stud_hilo", "razz", "triple_draw", "badugi", "pineapple",
                "crazy_pineapple", "five_card_draw", "short_deck", "irish", "courchevel", "follow_the_queen", "high_chicago",
            ),
            BuiltInGame.entries.map { it.id },
        )
    }

    /** Which slice sits under the pointer (at the top) with the wheel turned [angle] degrees clockwise. */
    private fun sliceUnderPointer(angle: Float, slices: Int): Int {
        val sweep = 360.0 / slices
        val underPointer = ((-angle % 360.0) + 360.0) % 360.0
        return floor(underPointer / sweep).toInt() % slices
    }

    private companion object {
        const val SPINS = 18_000
    }
}
