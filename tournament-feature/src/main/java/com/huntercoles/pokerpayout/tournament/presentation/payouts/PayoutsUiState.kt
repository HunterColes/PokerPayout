package com.huntercoles.pokerpayout.tournament.presentation.payouts

import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.PayoutSettings
import com.huntercoles.pokerpayout.core.domain.model.PayoutTable
import com.huntercoles.pokerpayout.core.domain.model.PoolBreakdown

/** The Payouts tab (S6): the pool, the structure, the table, the bubble and the bounties. */
data class PayoutsUiState(
    val playerCount: Int = 0,
    val money: MoneySettings = MoneySettings.DEFAULT,
    /** With every rebuy and add-on at the price it was bought at (PP-085). */
    val pool: PoolBreakdown = PoolBreakdown.EMPTY,
    val rebuyCount: Int = 0,
    val addOnCount: Int = 0,
    val settings: PayoutSettings = PayoutSettings(emptyList(), PayoutPreset.DEFAULT, PayoutRounding.DEFAULT),
    val table: PayoutTable = PayoutTable.EMPTY,
    /** What 1st would get under each preset, at today's places and rounding. */
    val firstPlaceByPreset: Map<PayoutPreset, Long> = emptyMap(),
    val recommendedPlaces: Int = 1,
    val maxPlaces: Int = 1,
    val rows: List<PayoutRowModel> = emptyList(),
    val bubble: BubbleModel = BubbleModel(),
    val bounties: BountiesModel = BountiesModel(),
    /** The structure can't change while the clock runs. */
    val isLocked: Boolean = false,
    val showStructureSheet: Boolean = false,
    /** Saving the finished night to History (PP-037). */
    val night: NightSave = NightSave.NotOver
) {
    val places: Int get() = table.places.size

    /** The table's rows add up to the prize pool, to the cent (always, by construction; shown as a check). */
    val addsUp: Boolean get() = table.places.isNotEmpty() && table.totalCents == table.prizePoolCents

    val preset: PayoutPreset? get() = settings.preset

    val canPlaceMore: Boolean get() = !isLocked && places < maxPlaces
}

/** Whether tonight can go into History (PP-037). */
enum class NightSave {
    /** No champion yet, or someone is still owed money. */
    NotOver,

    /** Over and everyone paid: "Save this night" is offered. */
    Offered,

    /** History holds it. */
    Saved
}

/** One paid place. [sharePercent] is the rounded amount's share of the pool, not the raw weight. */
data class PayoutRowModel(
    val place: Int,
    val amountCents: Long,
    val sharePercent: Double,
    /** Who finished there, once decided. */
    val holderName: String?
)

/** One finishing place on the bubble strip, worst to best. */
enum class SeatState {
    /** Decided, outside the money. */
    Out,

    /** Decided, in the money. */
    Cashed,

    /** Still to be decided, outside the money. */
    Bubble,

    /** Still to be decided, in the money. */
    Money
}

data class SeatModel(val place: Int, val state: SeatState)

/** How far the money is: [moreOutToMoney] players still to go out before everyone left is paid. */
data class BubbleModel(
    val seats: List<SeatModel> = emptyList(),
    val moreOutToMoney: Int = 0,
    /** The place the next player out finishes in; null once there is a champion. */
    val nextOutPlace: Int? = null
)

/** Bounties claimed so far: who knocked out whom. */
data class BountyClaim(val name: String, val victims: List<String>, val cents: Long)

data class BountiesModel(
    val perHeadCents: Long = 0L,
    val claims: List<BountyClaim> = emptyList(),
    /** Bounties not claimed yet; the champion's share once the night is over. */
    val stillOutCents: Long = 0L,
    /**
     * Once there is a champion: their own bounty plus the unclaimed ones (PP-055); progressive, their
     * grown bounty; mystery, the envelopes left.
     */
    val championName: String? = null,
    val championCents: Long = 0L,
    val foodCents: Long = 0L,
    /** How knockouts pay (PP-035); a claim's [BountyClaim.cents] is what it paid in this mode. */
    val mode: BountyMode = BountyMode.STANDARD,
    /** Mystery bounties: how many envelopes the pool started with (one per player). */
    val envelopes: Int = 0
)
