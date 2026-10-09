package com.huntercoles.pokerpayout.tournament.domain.presets

import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.ChipCalculatorPreferences
import com.huntercoles.pokerpayout.core.preferences.ChipSetSettings
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.utils.SmallestChipChoices
import javax.inject.Inject

/**
 * The Tournament setup as a preset sees it (PP-032): [capture] reads what a preset holds from the
 * saved settings, [apply] writes a preset back over them. Nothing else is touched: not the player
 * count, the Bank (names, buy-ins, purchases, knockouts) or the clock.
 *
 * Loading is allowed only before the clock first starts ([canLoad]): a preset would replace the
 * blinds of a game under way. A reset (New tournament…) makes it possible again.
 */
class CurrentSetup @Inject constructor(
    private val tournamentPreferences: TournamentPreferences,
    private val timerPreferences: TimerPreferences,
    private val chipPreferences: ChipCalculatorPreferences,
    private val bankPreferences: BankPreferences,
) {
    fun canLoad(): Boolean = !timerPreferences.getHasTimerStarted()

    /** Every [Starter], fitted to tonight ([starter]), in the order the presets sheet lists them. */
    fun starters(): List<StarterSetup> = Starter.entries.map { StarterSetup(it, starter(it)) }

    /** The chip set from Tools, as it is now. */
    fun chipSet(): ChipSetSettings = chipPreferences.current()

    /**
     * What a preset saved now would hold, with the chip set if [includeChipSet]. Once the clock has
     * started, the blinds are the ones it runs (frozen at the start, or changed through "Unlock to edit").
     */
    fun capture(includeChipSet: Boolean): PresetSetup {
        val tournament = tournamentPreferences
        val timer = timerPreferences
        val started = timer.getHasTimerStarted()
        return PresetSetup(
            money = tournament.getMoneySettings(),
            rebuyUntilLevel = tournament.getRebuyUntilLevel(),
            blinds = PresetBlinds(
                durationMinutes = timer.getGameDurationMinutes(),
                roundLengthMinutes = if (started) timer.getRoundLengthAtStart() else tournament.getRoundLengthMinutes(),
                smallestChip = SmallestChipChoices.normalize(
                    if (started) timer.getSmallestChipAtStart() else tournament.getSmallestChip()
                ),
                startingChips = if (started) timer.getStartingChipsAtStart() else tournament.getStartingChips(),
                breakEveryLevels = timer.getBreakEveryLevels(),
                breakLengthMinutes = timer.getBreakLengthMinutes(),
                breakNote = timer.getBreakMessage(),
                anteFromLevel = timer.getBigBlindAnteFromLevel(),
            ),
            payouts = PresetPayouts.of(tournament.getPayoutSettings(), tournament.getPlayerCount()),
            chipSet = if (includeChipSet) chipPreferences.current() else null,
        )
    }

    /**
     * [starter] fitted to tonight (PP-113): the smallest chip in setup now (the one the clock plays,
     * once it has started), the food the host charges, and tonight's players.
     */
    fun starter(starter: Starter): PresetSetup {
        val now = capture(includeChipSet = false)
        return starter.setupFor(now.blinds.smallestChip, now.money.foodCents, tournamentPreferences.getPlayerCount())
    }

    /**
     * True when loading [setup] would replace something the host chose: the setup now is neither a
     * new tournament's nor already what [setup] would make it. Loading then asks first.
     */
    fun wouldOverwrite(setup: PresetSetup): Boolean {
        val withChips = setup.chipSet != null
        val now = capture(includeChipSet = withChips)
        val fresh = PresetSetup.defaults(tournamentPreferences.getPlayerCount(), ChipSetSettings().takeIf { withChips })
        return now != resolve(setup) && now != fresh
    }

    /**
     * True when the setup now is what loading [setup] would make it, its chip set aside: tonight was
     * played with that preset (History names a saved night after it, PP-037).
     */
    fun matches(setup: PresetSetup): Boolean = capture(includeChipSet = false) == resolve(setup.copy(chipSet = null))

    /**
     * Writes [setup] over the Tournament setup (and the chip set, if it holds one), resolved for
     * tonight ([PresetSetup.resolved]), then tells the screens that keep their own copy to read it
     * again. Refused, with nothing written, once the clock has started; returns whether it was written.
     */
    fun apply(setup: PresetSetup): Boolean {
        if (!canLoad()) return false
        val target = resolve(setup)
        val money = target.money
        val blinds = target.blinds
        with(tournamentPreferences) {
            setBuyInCents(money.buyInCents)
            setFoodCents(money.foodCents)
            setBountyCents(money.bountyCents)
            setRebuyCents(money.rebuyCents)
            setAddOnCents(money.addOnCents)
            setRebuyUntilLevel(target.rebuyUntilLevel)
            setPayoutSettings(target.payouts.settingsFor(getPlayerCount()))
            setGameDurationHours(blinds.durationMinutes / MINUTES_PER_HOUR)
            setRoundLengthMinutes(blinds.roundLengthMinutes)
            setSmallestChip(blinds.smallestChip)
            setStartingChips(blinds.startingChips)
        }
        with(timerPreferences) {
            setGameDurationMinutes(blinds.durationMinutes)
            setBreakEveryLevels(blinds.breakEveryLevels)
            setBreakLengthMinutes(blinds.breakLengthMinutes)
            setBreakMessage(blinds.breakNote)
            setBigBlindAnteFromLevel(blinds.anteFromLevel)
        }
        target.chipSet?.let { chipPreferences.restore(it) }
        tournamentPreferences.setupReplaced()
        return true
    }

    /**
     * [setup] for tonight's players, keeping a rebuy or add-on amount the Bank has purchases at, and
     * a mystery bounty whose envelopes have started to be drawn (PP-035).
     */
    private fun resolve(setup: PresetSetup): PresetSetup {
        val now = tournamentPreferences.getMoneySettings()
        val players = tournamentPreferences.getPlayerCount()
        val envelopesDrawn = now.bountyMode == BountyMode.MYSTERY && bankPreferences.hasBountyDraws(players)
        return setup.resolved(
            players = players,
            keepRebuyCents = now.rebuyCents.takeIf { bankPreferences.getTotalRebuyCount() > 0 },
            keepAddOnCents = now.addOnCents.takeIf { bankPreferences.getTotalAddonCount() > 0 },
            keepBountyCents = now.bountyCents.takeIf { envelopesDrawn },
        )
    }

    private companion object {
        const val MINUTES_PER_HOUR = 60
    }
}
