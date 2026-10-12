package com.huntercoles.pokerpayout.tournament.domain.clock

import com.huntercoles.pokerpayout.core.constants.TournamentDefaults
import com.huntercoles.pokerpayout.core.utils.BlindSetupProblemKind
import com.huntercoles.pokerpayout.core.utils.BlindStructureInput
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The one way the clock's regular levels are built from a blind setup, shared by the clock's screen,
 * the saved clock (the live notification, the Bank's cutoffs) and the schedule other tabs read.
 */
class ClockLevelsTest {

    private fun setup(
        durationMinutes: Int = TournamentDefaults.GAME_DURATION_HOURS * 60,
        roundLengthMinutes: Int = TournamentDefaults.ROUND_LENGTH_MINUTES,
        smallestChip: Int = TournamentDefaults.SMALLEST_CHIP,
        startingChips: Int = TournamentDefaults.STARTING_CHIPS,
        anteFromLevel: Int = 0,
    ) = BlindStructureInput(
        players = TournamentDefaults.PLAYER_COUNT,
        targetDurationMinutes = durationMinutes,
        smallestChip = smallestChip,
        startingStack = startingChips,
        roundLengthMinutes = roundLengthMinutes,
        bigBlindAnteFromLevel = anteFromLevel,
    )

    @Test
    fun `a playable setup climbs from the smallest chip to the stack, one level per round`() {
        val built = ClockLevels.of(setup())

        assertNull(built.problem)
        assertEquals(9, built.levels.size, "3 hours of 20-minute levels")
        assertEquals(TournamentDefaults.SMALLEST_CHIP, built.levels.first().smallBlind)
        assertEquals(TournamentDefaults.STARTING_CHIPS, built.levels.last().smallBlind)
        assertEquals((1..9).toList(), built.levels.map { it.level })
    }

    @Test
    fun `the big-blind ante starts at its level`() {
        val built = ClockLevels.of(setup(anteFromLevel = 4))

        assertTrue(built.levels.take(3).all { it.ante == 0 })
        assertTrue(built.levels.drop(3).all { it.ante == it.bigBlind })
    }

    @Test
    fun `a setup that can't be played says why and has no levels`() {
        val uneven = ClockLevels.of(setup(roundLengthMinutes = 25))
        assertEquals(BlindSetupProblemKind.UNEVEN_ROUNDS, uneven.problem?.kind)
        assertTrue(uneven.levels.isEmpty())

        val tooSmall = ClockLevels.of(setup(smallestChip = 100, startingChips = 50))
        assertEquals(BlindSetupProblemKind.STACK_TOO_SMALL, tooSmall.problem?.kind)
        assertTrue(tooSmall.levels.isEmpty())
    }
}
