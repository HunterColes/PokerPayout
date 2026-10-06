package com.huntercoles.pokerpayout.tournament.presentation.composable

import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.preferences.ChipSetSettings
import com.huntercoles.pokerpayout.tournament.domain.presets.PresetBlinds
import com.huntercoles.pokerpayout.tournament.domain.presets.PresetPayouts
import com.huntercoles.pokerpayout.tournament.domain.presets.PresetSetup
import com.huntercoles.pokerpayout.tournament.domain.presets.TournamentPreset
import com.huntercoles.pokerpayout.tournament.presentation.presets.ChipSetSummary
import com.huntercoles.pokerpayout.tournament.presentation.presets.PresetSheet
import com.huntercoles.pokerpayout.tournament.presentation.presets.PresetsUiState

/**
 * Three saved nights for the presets sheet (PP-032), the last used first: the mockups' Friday game,
 * a deep stack with the chip set, and a turbo.
 */
internal object PresetsFixture {
    val friday = TournamentPreset(
        id = 1L,
        name = "Friday",
        lastUsedMillis = 1_791_054_000_000L, // 3 Oct 2026
        setup = PresetSetup(
            money = MoneySettings(4_000L, foodCents = 500L, bountyCents = 500L, rebuyCents = 4_000L, addOnCents = 1_000L),
            rebuyUntilLevel = 4,
            blinds = PresetBlinds(180, 20, 25, 5_000, 4, 10, "Last rebuy", 5),
            payouts = PresetPayouts(PayoutPreset.STANDARD.weightsFor(3), PayoutPreset.STANDARD, PayoutRounding.ONE_DOLLAR, true),
        ),
    )

    val deepStack = TournamentPreset(
        id = 2L,
        name = "Deep stack",
        lastUsedMillis = 1_789_844_400_000L, // 19 Sep 2026
        setup = PresetSetup(
            money = MoneySettings(buyInCents = 6_000L, foodCents = 0L, bountyCents = 0L, rebuyCents = 0L, addOnCents = 0L),
            rebuyUntilLevel = 0,
            blinds = PresetBlinds(240, 30, 25, 10_000, 0, 10, "", 0),
            payouts = PresetPayouts(
                PayoutPreset.TOP_HEAVY.weightsFor(4),
                PayoutPreset.TOP_HEAVY,
                PayoutRounding.FIVE_DOLLARS,
                followsPlayers = false,
            ),
            chipSet = ChipSetSettings(inventoryReviewed = true),
        ),
    )

    val turbo = TournamentPreset(
        id = 3L,
        name = "Turbo",
        lastUsedMillis = 1_788_030_000_000L, // 29 Aug 2026
        setup = PresetSetup(
            money = MoneySettings(buyInCents = 2_000L, foodCents = 0L, bountyCents = 0L, rebuyCents = 2_000L, addOnCents = 0L),
            rebuyUntilLevel = 0,
            blinds = PresetBlinds(120, 10, 25, 3_000, 0, 10, "", 0),
            payouts = PresetPayouts(listOf(65, 35), preset = null, rounding = PayoutRounding.ONE_DOLLAR, followsPlayers = false),
        ),
    )

    val all = listOf(friday, deepStack, turbo)

    /** The home set's 4 colours and 500 chips, checked in Tools. */
    val chipSet = ChipSetSummary(colours = 4, chips = 500, ready = true)

    /** The list before the start, with [all] saved. */
    val list = PresetsUiState(presets = all, sheet = PresetSheet.List, canLoad = true, chipSet = chipSet)

    /** The suggested name for the mockups' night ($40 buy-in, 20-minute levels). */
    const val SUGGESTED_NAME = "$40 · 20-min levels"
}
