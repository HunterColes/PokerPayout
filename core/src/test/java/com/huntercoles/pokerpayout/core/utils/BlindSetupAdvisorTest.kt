package com.huntercoles.pokerpayout.core.utils

import com.huntercoles.pokerpayout.core.utils.BlindSetupFix.UseRoundLength
import com.huntercoles.pokerpayout.core.utils.BlindSetupFix.UseStartingChips
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** PP-020: an invalid blind setup is explained in plain words, with fixes that really work. */
class BlindSetupAdvisorTest {

    private fun check(hours: Int, round: Int, chip: Int, stack: Int) =
        BlindSetupAdvisor.check(hours * 60, round, chip, stack)

    @Test
    fun `the default setup is valid`() {
        assertNull(check(3, 20, 50, 5_000))
    }

    @Test
    fun `a duration that isn't whole rounds names the leftover and the nearest round length`() {
        // B15's repro: default chips with 25-minute rounds
        val problem = assertNotNull(check(3, 25, 50, 5_000))

        assertEquals(BlindSetupProblemKind.UNEVEN_ROUNDS, problem.kind)
        assertEquals(
            "3 hours doesn't divide into 25-minute rounds: that's 7 rounds with 5 minutes left over.",
            problem.explanation
        )
        // 30-minute rounds divide 3 hours but give 6 levels, too few to climb 50 -> 5,000 in band
        assertEquals(listOf(UseRoundLength(20, 9)), problem.fixes)
        assertEquals("Use 20-min rounds (9 levels)", problem.fixes.single().label)
    }

    @Test
    fun `too steep a climb offers more, shorter rounds and the nearest smaller stack`() {
        val problem = assertNotNull(check(3, 20, 50, 50_000))

        assertEquals(BlindSetupProblemKind.TOO_STEEP, problem.kind)
        assertTrue("more than doubling" in problem.explanation, problem.explanation)
        // 12 levels of 15 minutes reach 50,000; 9 levels top out at 50 x 2^8 = 12,800
        assertEquals(listOf(UseRoundLength(15, 12), UseStartingChips(12_000)), problem.fixes)
    }

    @Test
    fun `too flat a climb offers fewer, longer rounds and the nearest bigger stack`() {
        val problem = assertNotNull(check(8, 10, 100, 1_000))

        assertEquals(BlindSetupProblemKind.TOO_FLAT, problem.kind)
        assertTrue("less than 30%" in problem.explanation, problem.explanation)
        // 100 -> 1,000 fits in 5 or 6 levels; of the round lengths that divide 8 hours, 80 minutes
        // gives 6.
        assertEquals(UseRoundLength(80, 6), problem.fixes.first())
        assertTrue(problem.fixes.last() is UseStartingChips)
    }

    @Test
    fun `a stack that can't be made from the smallest chip suggests the nearest that can`() {
        val problem = assertNotNull(check(3, 20, 25, 1_010))

        assertEquals(BlindSetupProblemKind.STACK_NOT_MULTIPLE_OF_CHIP, problem.kind)
        assertEquals("Starting chips (1,010) can't be made from 25 chips. Use a multiple of 25.", problem.explanation)
        assertEquals(listOf(UseStartingChips(1_000)), problem.fixes)
    }

    @Test
    fun `a stack no bigger than the smallest chip is explained`() {
        val problem = assertNotNull(check(3, 20, 100, 50))

        assertEquals(BlindSetupProblemKind.STACK_TOO_SMALL, problem.kind)
        assertEquals("Starting chips (50) must be more than the smallest chip (100).", problem.explanation)
        assertEquals(listOf(UseStartingChips(2_000)), problem.fixes)
    }

    /**
     * Found by BlindPropertiesTest: 68 levels from a 10 chip need at least 1,123,447,780 chips, and the
     * fix offered "Use 1,200,000,000 starting chips", whose ladder's big blinds overflowed to negative
     * numbers. No stack whose big blind can't be counted is offered or built now; a longer level
     * length still fixes it.
     */
    @Test
    fun `a suggested stack never makes a big blind too big to count`() {
        val problem = assertNotNull(check(17, 15, 10, 20_000))
        assertTrue(problem.fixes.isNotEmpty())
        problem.fixes.filterIsInstance<UseStartingChips>().forEach { fix ->
            assertTrue(fix.chips <= BlindFittingAlgorithm.MAX_STARTING_CHIPS, "suggested ${fix.chips}")
        }

        // A stack saved from the old suggestion is refused, not built with negative blinds
        assertNotNull(check(17, 15, 10, 1_200_000_000))
        assertFailsWith<BlindLadderException> {
            BlindStructureCalculator.generateSchedule(BlindStructureInput(9, 17 * 60, 10, 1_200_000_000, 15))
        }
    }

    @Test
    fun `rounds longer than half the game leave too few levels`() {
        val problem = assertNotNull(check(1, 45, 50, 5_000))

        assertEquals(BlindSetupProblemKind.TOO_FEW_ROUNDS, problem.kind)
        assertEquals(listOf(UseRoundLength(5, 12)), problem.fixes)
    }

    private data class Setup(val hours: Int, val round: Int, val chip: Int, val stack: Int)

    private fun sweep(rounds: List<Int>, stacks: List<Int>) = (1..8).flatMap { hours ->
        rounds.flatMap { round ->
            listOf(1, 5, 25, 50, 100).flatMap { chip -> stacks.map { stack -> Setup(hours, round, chip, stack) } }
        }
    }

    /** Every fix the advisor offers, across the batch-A sweep and a few odd stacks, really works. */
    @Test
    fun `every offered fix gives a valid setup`() {
        val setups = sweep(
            rounds = listOf(10, 15, 20, 25, 30, 40, 45, 60),
            stacks = listOf(500, 1_000, 1_010, 2_500, 5_000, 7_500, 20_000, 50_000, 100_000)
        )
        val problems = setups.mapNotNull { setup ->
            check(setup.hours, setup.round, setup.chip, setup.stack)?.let { setup to it }
        }
        problems.forEach { (setup, problem) ->
            problem.fixes.forEach { fix ->
                val fixed = when (fix) {
                    is UseRoundLength -> setup.copy(round = fix.minutes)
                    is UseStartingChips -> setup.copy(stack = fix.chips)
                }
                val after = check(fixed.hours, fixed.round, fixed.chip, fixed.stack)
                if (after != null) fail("$setup: $fix still fails: ${after.explanation}")
            }
        }
        assertTrue(problems.size > 100, "the sweep should hit plenty of invalid setups, got ${problems.size}")
    }

    @Test
    fun `an accepted setup always builds a schedule`() {
        sweep(rounds = listOf(10, 15, 20, 30, 60), stacks = listOf(1_000, 5_000, 20_000))
            .filter { check(it.hours, it.round, it.chip, it.stack) == null }
            .forEach { setup ->
                val input = BlindStructureInput(9, setup.hours * 60, setup.chip, setup.stack, setup.round)
                val levels = BlindStructureCalculator.generateSchedule(input)
                assertEquals(setup.hours * 60 / setup.round, levels.size)
            }
    }
}
