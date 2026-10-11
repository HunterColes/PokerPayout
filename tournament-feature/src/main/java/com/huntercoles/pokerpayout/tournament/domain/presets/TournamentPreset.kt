package com.huntercoles.pokerpayout.tournament.domain.presets

import com.huntercoles.pokerpayout.core.constants.TournamentDefaults
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.model.PayoutPlaces
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.PayoutSettings
import com.huntercoles.pokerpayout.core.preferences.ChipSetSettings
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.utils.SmallestChipChoices

/**
 * A saved setup with its name (PP-032), so last month's game is one tap away.
 *
 * @property id stable while the preset exists; names can change.
 * @property lastUsedMillis when it was last saved or loaded (wall clock); the list puts the latest first.
 */
data class TournamentPreset(
    val id: Long,
    val name: String,
    val lastUsedMillis: Long,
    val setup: PresetSetup,
) {
    companion object {
        const val MAX_NAME_LENGTH = 40
        private val SPACES = Regex("\\s+")

        /** A name as it is saved: trimmed, runs of spaces made one, at most [MAX_NAME_LENGTH] letters. */
        fun cleanName(name: String): String = name.trim().replace(SPACES, " ").take(MAX_NAME_LENGTH).trim()
    }
}

/**
 * What a preset holds: the money, the blinds, the payout structure and, when chosen, the chip set from
 * Tools. Never the players (their number or names), the Bank's purchases or the clock's progress.
 *
 * @property rebuyUntilLevel the last level rebuys are allowed at; 0 = no cutoff.
 * @property chipSet the chip set, if the preset includes it.
 * @property lateEntryUntilLevel the last level a player can join late or re-enter at (PP-116); 0 = no
 *   cutoff, as presets saved before it load.
 */
data class PresetSetup(
    val money: MoneySettings,
    val rebuyUntilLevel: Int,
    val blinds: PresetBlinds,
    val payouts: PresetPayouts,
    val chipSet: ChipSetSettings? = null,
    val lateEntryUntilLevel: Int = 0,
) {
    /**
     * This setup as loading it leaves it tonight: the payouts for [players] players
     * ([PresetPayouts.settingsFor]), the smallest chip on a real chip, and a rebuy or add-on the preset
     * turns off kept at its current amount while the Bank holds purchases of it ([keepRebuyCents] and
     * [keepAddOnCents], null when it holds none), so no recorded purchase is left worth nothing. With
     * mystery envelopes already drawn, the bounty that dealt them stays as it is ([keepBountyCents],
     * PP-035; null when none are drawn).
     */
    fun resolved(players: Int, keepRebuyCents: Long?, keepAddOnCents: Long?, keepBountyCents: Long? = null): PresetSetup = copy(
        money = money.copy(
            bountyCents = keepBountyCents ?: money.bountyCents,
            rebuyCents = money.rebuyCents.takeIf { it > 0L } ?: keepRebuyCents ?: 0L,
            addOnCents = money.addOnCents.takeIf { it > 0L } ?: keepAddOnCents ?: 0L,
        ),
        blinds = blinds.copy(smallestChip = SmallestChipChoices.normalize(blinds.smallestChip)),
        payouts = PresetPayouts.of(payouts.settingsFor(players), players),
    )

    companion object {
        /** A new or reset tournament's setup for [players] players, with [chipSet] (or none). */
        fun defaults(players: Int, chipSet: ChipSetSettings?): PresetSetup = PresetSetup(
            money = MoneySettings.DEFAULT,
            rebuyUntilLevel = 0,
            blinds = PresetBlinds.DEFAULT,
            payouts = PresetPayouts.of(
                PayoutSettings(
                    weights = PayoutPreset.DEFAULT.weightsFor(PayoutPlaces.recommended(players)),
                    preset = PayoutPreset.DEFAULT,
                    rounding = PayoutRounding.DEFAULT,
                ),
                players,
            ),
            chipSet = chipSet,
        )
    }
}

/**
 * The blind setup: game length, level length, smallest chip, starting stack, breaks and their note,
 * and the big-blind ante.
 *
 * @property breakEveryLevels 0 = no breaks.
 * @property anteFromLevel the first level with a big-blind ante; 0 = no ante.
 */
data class PresetBlinds(
    val durationMinutes: Int,
    val roundLengthMinutes: Int,
    val smallestChip: Int,
    val startingChips: Int,
    val breakEveryLevels: Int,
    val breakLengthMinutes: Int,
    val breakNote: String,
    val anteFromLevel: Int,
) {
    companion object {
        val DEFAULT = PresetBlinds(
            durationMinutes = TimerPreferences.DEFAULT_DURATION_MINUTES,
            roundLengthMinutes = TournamentDefaults.ROUND_LENGTH_MINUTES,
            smallestChip = TournamentDefaults.SMALLEST_CHIP,
            startingChips = TournamentDefaults.STARTING_CHIPS,
            breakEveryLevels = TimerPreferences.DEFAULT_BREAK_EVERY_LEVELS,
            breakLengthMinutes = TimerPreferences.DEFAULT_BREAK_LENGTH_MINUTES,
            breakNote = "",
            anteFromLevel = 0,
        )
    }
}

/**
 * The payout structure: the weights (one per place paid), the preset they come from (null when edited
 * by hand) and the rounding.
 *
 * @property followsPlayers the preset at the places recommended for the players it was saved with.
 *   Like the setup itself, it then follows the player count: loaded for 12 players it pays the
 *   places recommended for 12.
 */
data class PresetPayouts(
    val weights: List<Int>,
    val preset: PayoutPreset?,
    val rounding: PayoutRounding,
    val followsPlayers: Boolean,
) {
    /** The structure for [players] players: following the count, or as saved, never more places than players. */
    fun settingsFor(players: Int): PayoutSettings {
        val saved = PayoutSettings(weights, preset, rounding)
        val places = if (followsPlayers && preset != null) {
            PayoutPlaces.recommended(players)
        } else {
            weights.size.coerceAtMost(PayoutPlaces.maxFor(players))
        }
        return if (places == weights.size) saved else saved.withPlaces(places)
    }

    companion object {
        /** [settings] as a preset saves them, for [players] players. */
        fun of(settings: PayoutSettings, players: Int): PresetPayouts = PresetPayouts(
            weights = settings.weights,
            preset = settings.preset,
            rounding = settings.rounding,
            followsPlayers = settings.preset?.weightsFor(PayoutPlaces.recommended(players)) == settings.weights,
        )
    }
}
