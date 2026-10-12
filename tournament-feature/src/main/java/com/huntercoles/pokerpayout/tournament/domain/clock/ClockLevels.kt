package com.huntercoles.pokerpayout.tournament.domain.clock

import com.huntercoles.pokerpayout.core.utils.BlindLevel
import com.huntercoles.pokerpayout.core.utils.BlindSetupAdvisor
import com.huntercoles.pokerpayout.core.utils.BlindSetupProblem
import com.huntercoles.pokerpayout.core.utils.BlindStructureCalculator
import com.huntercoles.pokerpayout.core.utils.BlindStructureInput

/** A blind setup's regular levels, or why it has none ([problem]): then [levels] is empty. */
data class ClockLevels(val problem: BlindSetupProblem?, val levels: List<BlindLevel>) {
    companion object {
        /**
         * The regular levels [setup] plays before overtime, built one way for everyone who lays out
         * the clock: the clock's screen, the saved clock (the live clock notification, the Bank's
         * cutoffs) and the schedule other tabs read (the chip set's color-ups). A setup
         * [BlindSetupAdvisor] rejects says why and has none; one it passes always has a ladder, and
         * an unexpected failure to build one leaves none rather than a crash.
         */
        fun of(setup: BlindStructureInput): ClockLevels {
            val problem = BlindSetupAdvisor.check(
                durationMinutes = setup.targetDurationMinutes,
                roundLengthMinutes = setup.roundLengthMinutes,
                smallestChip = setup.smallestChip,
                startingChips = setup.startingStack,
            )
            val levels = if (problem == null) {
                runCatching { BlindStructureCalculator.generateSchedule(setup) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            return ClockLevels(problem, levels)
        }
    }
}
