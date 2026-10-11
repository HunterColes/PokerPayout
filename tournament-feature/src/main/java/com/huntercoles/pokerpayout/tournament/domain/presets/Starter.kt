package com.huntercoles.pokerpayout.tournament.domain.presets

import androidx.annotation.StringRes
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.model.PayoutPlaces
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.PayoutSettings
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.tournament.R

/**
 * The starter nights that come with the app (PP-113), so a first-time host has a sensible night set
 * up in one tap. Read-only: they are never in the [PresetStore], so they can't be renamed or deleted,
 * and a copy saved among the host's own presets is what gets changed.
 *
 * A starter holds its stack as a number of smallest chips ([StarterNight.stackInChips]), so it fits
 * the chips the host plays with ([setupFor]): over a 25 it starts at 25 / 50 with 2,500 chips, over a
 * 100 at 100 / 200 with 10,000. Whether the blinds can climb from the smallest chip to the stack
 * depends only on that number and the number of levels, so every starter makes a valid ladder with
 * every chip (`StarterTest` builds each one as the clock does).
 *
 * @property title the name the presets sheet shows, and a copy is saved under.
 */
enum class Starter(@StringRes val title: Int, val night: StarterNight) {
    /** Two hours of 10-minute levels: 12 levels, the blinds about 1.5x a level. */
    TURBO(R.string.starter_turbo, StarterNight(hours = 2, levelMinutes = 10, stackInChips = 100, buyInCents = 1_000L)),

    /** Three hours of 20-minute levels with two breaks: the app's own default night, named. */
    CLASSIC(
        R.string.starter_classic,
        StarterNight(hours = 3, levelMinutes = 20, stackInChips = 100, buyInCents = 2_000L, breakEveryLevels = 4),
    ),

    /** Four hours, twice the stack over 12 slower levels, and a big-blind ante from level 7. */
    DEEP_STACK(
        R.string.starter_deep_stack,
        StarterNight(
            hours = 4,
            levelMinutes = 20,
            stackInChips = 200,
            buyInCents = 3_000L,
            breakEveryLevels = 4,
            anteFromLevel = 7,
        ),
    ),

    /** The classic night with a bounty on every player. */
    BOUNTY_NIGHT(
        R.string.starter_bounty_night,
        StarterNight(
            hours = 3,
            levelMinutes = 20,
            stackInChips = 100,
            buyInCents = 2_000L,
            bountyCents = 500L,
            breakEveryLevels = 4,
        ),
    ),
    ;

    /** The blind levels this night plays before overtime. */
    val levels: Int get() = night.hours * MINUTES_PER_HOUR / night.levelMinutes

    /**
     * This night for a table that plays [smallestChip] as its smallest chip, charges [foodCents] for
     * food (the house's call, which a starter leaves alone) and seats [players]: the stack is
     * [StarterNight.stackInChips] of those chips, and the payouts are the standard ones for that many
     * players, following the player count as it changes.
     */
    fun setupFor(smallestChip: Int, foodCents: Long, players: Int): PresetSetup = PresetSetup(
        money = MoneySettings(
            buyInCents = night.buyInCents,
            foodCents = foodCents,
            bountyCents = night.bountyCents,
            rebuyCents = 0L,
            addOnCents = 0L,
        ),
        rebuyUntilLevel = 0,
        blinds = PresetBlinds(
            durationMinutes = night.hours * MINUTES_PER_HOUR,
            roundLengthMinutes = night.levelMinutes,
            smallestChip = smallestChip,
            startingChips = night.stackInChips * smallestChip,
            breakEveryLevels = night.breakEveryLevels,
            breakLengthMinutes = TimerPreferences.DEFAULT_BREAK_LENGTH_MINUTES,
            breakNote = "",
            anteFromLevel = night.anteFromLevel,
        ),
        payouts = PresetPayouts.of(
            PayoutSettings(
                weights = PayoutPreset.STANDARD.weightsFor(PayoutPlaces.recommended(players)),
                preset = PayoutPreset.STANDARD,
                rounding = PayoutRounding.DEFAULT,
            ),
            players,
        ),
    )

    private companion object {
        const val MINUTES_PER_HOUR = 60
    }
}

/**
 * What a [Starter] is made of, whatever the chips.
 *
 * @property stackInChips the starting stack, in smallest chips.
 * @property breakEveryLevels 0 = no breaks.
 * @property anteFromLevel the first level with a big-blind ante; 0 = no ante.
 */
data class StarterNight(
    val hours: Int,
    val levelMinutes: Int,
    val stackInChips: Int,
    val buyInCents: Long,
    val bountyCents: Long = 0L,
    val breakEveryLevels: Int = 0,
    val anteFromLevel: Int = 0,
)

/** A [starter] as it would load tonight ([Starter.setupFor]): what the presets sheet lists. */
data class StarterSetup(val starter: Starter, val setup: PresetSetup)
