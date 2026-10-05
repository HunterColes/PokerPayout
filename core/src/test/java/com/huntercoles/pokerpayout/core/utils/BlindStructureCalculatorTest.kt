package com.huntercoles.pokerpayout.core.utils

import com.huntercoles.pokerpayout.core.constants.BlindStructureConstants.MAX_BLIND_GROWTH_RATE
import com.huntercoles.pokerpayout.core.constants.BlindStructureConstants.MIN_BLIND_GROWTH_RATE
import org.junit.jupiter.api.Disabled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * End-to-end schedule generation, checked as properties over every realistic configuration:
 * durations 1-8 h, round lengths 10-60 min, smallest chips 1/5/25/50/100, starting stacks 1k-50k
 * (6,600 configurations). Every configuration is either accepted, and then must satisfy the
 * schedule invariants, or rejected with a reason the user can act on.
 */
class BlindStructureCalculatorTest {

    private sealed interface Outcome {
        val input: BlindStructureInput
    }

    private data class Accepted(override val input: BlindStructureInput, val levels: List<BlindLevel>) : Outcome
    private data class Rejected(override val input: BlindStructureInput, val error: Throwable) : Outcome

    companion object {
        private val hours = 1..8
        private val roundLengths = (10..60 step 5).toList()
        private val smallestChips = listOf(1, 5, 25, 50, 100)
        private val startingStacks = listOf(
            1_000, 1_500, 2_000, 2_500, 3_000, 4_000, 5_000, 7_500,
            10_000, 15_000, 20_000, 25_000, 30_000, 40_000, 50_000
        )

        private val sweep: List<Outcome> by lazy {
            hours.flatMap { h ->
                roundLengths.flatMap { rl ->
                    smallestChips.flatMap { chip ->
                        startingStacks.map { stack ->
                            val input = BlindStructureInput(
                                players = 9,
                                targetDurationMinutes = h * 60,
                                smallestChip = chip,
                                startingStack = stack,
                                roundLengthMinutes = rl
                            )
                            runCatching { BlindStructureCalculator.generateSchedule(input) }
                                .fold({ Accepted(input, it) }, { Rejected(input, it) })
                        }
                    }
                }
            }
        }

        private val accepted get() = sweep.filterIsInstance<Accepted>()
        private val rejected get() = sweep.filterIsInstance<Rejected>()

        private fun BlindStructureInput.describe() =
            "${targetDurationMinutes / 60}h/${roundLengthMinutes}min $smallestChip->$startingStack"
    }

    /** Fails listing up to 10 offending configurations when [check] returns a problem. */
    private fun assertEveryAccepted(property: String, check: (Accepted) -> String?) {
        val violations = accepted.mapNotNull { a -> check(a)?.let { "${a.input.describe()}: $it" } }
        if (violations.isNotEmpty()) {
            fail(
                "$property: ${violations.size} of ${accepted.size} accepted configurations violate it, e.g.\n" +
                    violations.take(10).joinToString("\n")
            )
        }
    }

    @Test
    fun `sweep exercises both outcomes`() {
        assertEquals(8 * 11 * 5 * 15, sweep.size)
        assertTrue(accepted.size > 1_000, "only ${accepted.size} configurations accepted")
        assertTrue(rejected.isNotEmpty())
    }

    @Test
    fun `every accepted schedule starts at the smallest chip and ends at the starting stack`() {
        assertEveryAccepted("first = smallest chip, last = starting stack") { (input, levels) ->
            when {
                levels.first().smallBlind != input.smallestChip -> "starts at ${levels.first().smallBlind}"
                levels.last().smallBlind != input.startingStack -> "ends at ${levels.last().smallBlind}"
                else -> null
            }
        }
    }

    @Test
    fun `every accepted schedule strictly increases in multiples of the smallest chip`() {
        assertEveryAccepted("strictly increasing multiples of the smallest chip") { (input, levels) ->
            val blinds = levels.map { it.smallBlind }
            when {
                blinds.any { it % input.smallestChip != 0 } -> "not all multiples of ${input.smallestChip}: $blinds"
                blinds.zipWithNext().any { (a, b) -> b <= a } -> "not strictly increasing: $blinds"
                else -> null
            }
        }
    }

    @Test
    fun `every accepted schedule numbers its levels and starts each on a round boundary`() {
        assertEveryAccepted("level i starts at (i - 1) x round length, BB = 2 x SB, no ante") { (input, levels) ->
            levels.withIndex().firstOrNull { (i, level) ->
                level.level != i + 1 ||
                    level.roundStartMinute != i * input.roundLengthMinutes ||
                    level.bigBlind != 2 * level.smallBlind ||
                    level.ante != 0
            }?.let { (i, level) -> "level index $i is $level" }
        }
    }

    @Test
    fun `regular levels exactly fill a duration that divides into whole rounds`() {
        assertEveryAccepted("levels x round length = duration") { (input, levels) ->
            val duration = input.targetDurationMinutes
            val round = input.roundLengthMinutes
            if (duration % round == 0 && levels.size * round != duration) {
                "${levels.size} levels of $round min for $duration min"
            } else {
                null
            }
        }
    }

    // The old version of this test accepted a schedule when 70% of its steps were in band, which
    // hid that most accepted schedules have a step outside it: early levels crawl up one chip at a
    // time (25, 50, 75, 100, 125 ... is 1.25x, 1.2x, 1.17x) and the 5% tolerance on the average
    // rate admits steps above 2x.
    @Disabled(
        "PP-020: 1,229 of 2,224 accepted configurations have a step outside the documented " +
            "1.3x-2.0x band (998 below 1.3x, 231 above 2.0x). Accept only in-band ladders, or " +
            "reject the configuration with a reason."
    )
    @Test
    fun `every step of an accepted schedule stays within the documented growth bounds`() {
        assertEveryAccepted("every step within ${MIN_BLIND_GROWTH_RATE}x-${MAX_BLIND_GROWTH_RATE}x") { (_, levels) ->
            val steps = levels.zipWithNext { a, b -> b.smallBlind.toDouble() / a.smallBlind }
            val outOfBand = steps.filter { it < MIN_BLIND_GROWTH_RATE - 1e-9 || it > MAX_BLIND_GROWTH_RATE + 1e-9 }
            if (outOfBand.isEmpty()) {
                null
            } else {
                "${levels.map { it.smallBlind }} has steps ${outOfBand.map { "%.2f".format(it) }}"
            }
        }
    }

    @Test
    fun `every rejected configuration says why and which way to adjust`() {
        val unexplained = rejected.filterNot { (_, error) ->
            val message = error.message.orEmpty()
            error is IllegalArgumentException && "growth rate" in message && (
                ("too high" in message && "Try more rounds or smaller starting chips" in message) ||
                    ("too low" in message && "Try fewer rounds or larger starting chips" in message)
                )
        }
        assertTrue(
            unexplained.isEmpty(),
            "${unexplained.size} rejections without an actionable reason, e.g. " +
                unexplained.take(5).joinToString { "${it.input.describe()}: ${it.error}" }
        )
    }

    @Test
    fun `rejection direction matches the configuration`() {
        // A 1 h game can't climb 1 -> 50,000 in 6 levels; 8 h of 10-minute rounds can't stay
        // above 1.3x per level from 100 to 1,000.
        val tooSteep = BlindStructureInput(9, 60, 1, 50_000, 10)
        val tooFlat = BlindStructureInput(9, 480, 100, 1_000, 10)

        val steep = assertFailsWith<IllegalArgumentException> { BlindStructureCalculator.generateSchedule(tooSteep) }
        val flat = assertFailsWith<IllegalArgumentException> { BlindStructureCalculator.generateSchedule(tooFlat) }

        assertTrue("too high" in steep.message.orEmpty(), steep.message)
        assertTrue("too low" in flat.message.orEmpty(), flat.message)
    }

    @Test
    fun `default tournament gives nine in-band levels from 50 to 5000`() {
        val schedule = BlindStructureCalculator.generateSchedule(
            BlindStructureInput(
                players = 10,
                targetDurationMinutes = 180,
                smallestChip = 50,
                startingStack = 5_000,
                roundLengthMinutes = 20
            )
        )

        assertEquals(9, schedule.size)
        assertEquals(50, schedule.first().smallBlind)
        assertEquals(5_000, schedule.last().smallBlind)
        assertEquals(160, schedule.last().roundStartMinute)
        val steps = schedule.zipWithNext { a, b -> b.smallBlind.toDouble() / a.smallBlind }
        assertTrue(steps.all { it in MIN_BLIND_GROWTH_RATE..MAX_BLIND_GROWTH_RATE }, "steps $steps")
    }

    @Test
    fun `antes start at level 5 at half the small blind rounded up to a chip`() {
        val schedule = BlindStructureCalculator.generateSchedule(
            BlindStructureInput(9, 180, 25, 6_400, 20, includeAnte = true)
        )

        assertTrue(schedule.take(4).all { it.ante == 0 }, "no ante before level 5: $schedule")
        schedule.drop(4).forEach { level ->
            val half = level.smallBlind / 2
            assertTrue(level.ante % 25 == 0 && level.ante >= half && level.ante < half + 25, "ante of $level")
        }
    }

    @Test
    fun `overtime levels double the last level and continue the clock`() {
        val schedule = BlindStructureCalculator.generateSchedule(
            BlindStructureInput(9, 180, 50, 5_000, 20)
        )

        val first = BlindStructureCalculator.generateNextOvertimeLevel(schedule, roundLengthMinutes = 20)
        assertEquals(BlindLevel(level = 10, smallBlind = 10_000, bigBlind = 20_000, ante = 0, roundStartMinute = 180), first)

        val second = BlindStructureCalculator.generateNextOvertimeLevel(schedule + first!!, roundLengthMinutes = 20)
        assertEquals(BlindLevel(level = 11, smallBlind = 20_000, bigBlind = 40_000, ante = 0, roundStartMinute = 200), second)

        assertNull(BlindStructureCalculator.generateNextOvertimeLevel(emptyList(), roundLengthMinutes = 20))
    }

    @Test
    fun `non-positive inputs are rejected`() {
        val valid = BlindStructureInput(9, 180, 50, 5_000, 20)
        listOf(
            valid.copy(players = 0),
            valid.copy(targetDurationMinutes = 0),
            valid.copy(smallestChip = 0),
            valid.copy(startingStack = -1),
            valid.copy(roundLengthMinutes = 0)
        ).forEach { input ->
            assertFailsWith<IllegalArgumentException>(input.toString()) {
                BlindStructureCalculator.generateSchedule(input)
            }
        }
    }
}
