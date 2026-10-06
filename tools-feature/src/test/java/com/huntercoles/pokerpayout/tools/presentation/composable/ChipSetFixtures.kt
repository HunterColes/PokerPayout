package com.huntercoles.pokerpayout.tools.presentation.composable

import com.huntercoles.pokerpayout.core.preferences.ChipSetSettings
import com.huntercoles.pokerpayout.core.utils.BlindLevel
import com.huntercoles.pokerpayout.core.utils.BlindSchedule
import com.huntercoles.pokerpayout.core.utils.ChipColour
import com.huntercoles.pokerpayout.core.utils.ChipInventory
import com.huntercoles.pokerpayout.core.utils.PlanStacksUseCase
import com.huntercoles.pokerpayout.core.utils.ScheduledBreak
import com.huntercoles.pokerpayout.core.utils.StackPlanRequest
import com.huntercoles.pokerpayout.tools.presentation.ChipSetUiState

/**
 * The S11 mockup's game for the chip set screenshots: 9 players, 5,000 a stack, blinds from 25/50
 * with breaks after levels 4 and 8 (the makeover's ladder), and the 500-chip home set. Every plan
 * comes from the real planner, so a golden shows what the app would.
 */
internal object ChipSetFixtures {
    const val PLAYERS = 9
    const val STACK = 5_000
    const val SMALL_BLIND = 25

    /** 25/50 to 1,000/2,000 in nine levels, then the clock's three doubling overtime levels. */
    val schedule = BlindSchedule(
        levels = listOf(25, 50, 75, 100, 200, 300, 400, 600, 1_000, 2_000, 4_000, 8_000)
            .mapIndexed { i, sb -> BlindLevel(i + 1, sb, 2 * sb, 0, i * 20) },
        regularLevelCount = 9,
        breaks = listOf(ScheduledBreak(1, 4), ScheduledBreak(2, 8)),
        smallestChip = SMALL_BLIND,
        startingChips = STACK,
    )

    fun state(
        inventory: ChipInventory,
        reviewed: Boolean = true,
        reserve: Int = 0,
        players: Int = PLAYERS,
        stackOverride: Int? = null,
    ): ChipSetUiState {
        val settings = ChipSetSettings(inventory, reviewed, stackOverride, reserveStacks = reserve)
        val request = StackPlanRequest(
            inventory = inventory,
            startingStack = stackOverride ?: STACK,
            players = players,
            smallBlind = SMALL_BLIND,
            reserveStacks = reserve,
            maxColours = settings.maxColours,
            curve = settings.shape,
            schedule = schedule,
        )
        return ChipSetUiState(
            settings = settings,
            players = players,
            tournamentStack = STACK,
            smallBlind = SMALL_BLIND,
            plan = PlanStacksUseCase()(request),
            hasSchedule = true,
        )
    }

    /** S11: the home set covers 9 players with room for rebuys. */
    val ok: ChipSetUiState get() = state(ChipInventory.HOME_SET)

    /** S11, not enough: the same set with only 30 blacks and 60 greens (short 6 blacks; 7 stacks now). */
    val short: ChipSetUiState
        get() = state(ChipInventory.HOME_SET.withCount(ChipColour.Black, 30).withCount(ChipColour.Green, 60))
}
