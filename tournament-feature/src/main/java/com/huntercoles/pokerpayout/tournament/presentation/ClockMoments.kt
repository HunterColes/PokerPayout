package com.huntercoles.pokerpayout.tournament.presentation

import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockCues
import com.huntercoles.pokerpayout.tournament.domain.moments.BigMoment
import com.huntercoles.pokerpayout.tournament.domain.moments.Field
import com.huntercoles.pokerpayout.tournament.domain.moments.MomentTracker

/**
 * PP-111: the clock's side of the night's big moments ([MomentTracker] decides which are new). The
 * biggest new one shows on the clock with its cue: a banner, or for the champion their screen. Once
 * a clock exists, that is; before its first start a moment is only noted. A moment the field no
 * longer has (an Undo, a player brought back) goes.
 */
internal class ClockMoments(
    preferences: TimerPreferences,
    private val cues: ClockCues,
    /** A player's name, as the Bank has it. */
    private val nameOf: (Int) -> String,
) {
    private val tracker = MomentTracker(preferences)

    /** Moments shown so far in this process; each banner's id. */
    private var shown = 0

    /**
     * [state] once the clock has looked at [field]: [stillIn] are the players still in, and
     * [lowestPrizeCents] the last paid place's prize (what everyone in the money wins at least).
     */
    fun after(state: TimerUiState, field: Field, stillIn: List<Int>, lowestPrizeCents: Long): TimerUiState {
        val look = tracker.look(field)
        val headline = look.headline?.takeIf { state.hasTimerStarted }
        headline?.let { cues.playMoment(it.cue) }
        val banner = headline?.takeIf { it != BigMoment.CHAMPION }?.let { moment ->
            MomentBanner(
                moment = moment,
                id = ++shown,
                playersLeft = stillIn.size,
                names = if (moment == BigMoment.HEADS_UP) stillIn.map(nameOf) else emptyList(),
                lowestPrizeCents = lowestPrizeCents,
            )
        }
        val champion = BigMoment.CHAMPION in look.reached
        return state.copy(
            moment = banner ?: state.moment?.takeIf { headline == null && it.moment in look.reached },
            winnerOpen = champion && (state.winnerOpen || headline == BigMoment.CHAMPION),
        )
    }
}
