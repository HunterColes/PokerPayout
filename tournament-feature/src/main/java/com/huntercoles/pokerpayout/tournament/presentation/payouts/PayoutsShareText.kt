package com.huntercoles.pokerpayout.tournament.presentation.payouts

import android.content.Context
import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.PoolBreakdown
import com.huntercoles.pokerpayout.core.domain.model.ordinalOf
import com.huntercoles.pokerpayout.core.utils.FormatUtils.formatMoney
import com.huntercoles.pokerpayout.tournament.R

/**
 * The payouts as plain text, for the group chat (Share, `ACTION_SEND` text/plain; no permission):
 * the pool and where it came from, every paid place with its winner once known, the structure, and
 * the bounties claimed. The setup shared as text (PP-032) uses the same lines for its payouts.
 */
object PayoutsShareText {

    fun build(context: Context, state: PayoutsUiState): String = buildString {
        appendLine(context.getString(R.string.payouts_share_title))
        appendLine(poolLine(context, state.pool, state.playerCount, state.rebuyCount, state.addOnCount))
        state.rows.forEach { row -> appendLine(placeLine(context, row.place, row.amountCents, row.holderName)) }
        appendLine(structureLine(context, state.preset, state.settings.rounding))
        bounties(context, state)?.let { appendLine(it) }
        if (state.bounties.foodCents > 0L) {
            appendLine(context.getString(R.string.payouts_food, formatMoney(state.bounties.foodCents)))
        }
    }.trimEnd()

    /** "Prize pool $410 (9 buy-ins $360 · 1 rebuy $40 · 1 add-on $10)". */
    fun poolLine(context: Context, pool: PoolBreakdown, players: Int, rebuys: Int, addOns: Int): String =
        context.getString(
            R.string.payouts_share_pool,
            formatMoney(pool.prizePoolCents),
            poolSources(context, pool, players, rebuys, addOns),
        )

    /** "9 buy-ins $360 · 1 rebuy $40 · 5 add-ons $50". */
    fun poolSources(context: Context, state: PayoutsUiState): String =
        poolSources(context, state.pool, state.playerCount, state.rebuyCount, state.addOnCount)

    /** "9 buy-ins $360 · 1 rebuy $40 · 5 add-ons $50", for [players] buy-ins and the purchases counted. */
    fun poolSources(context: Context, pool: PoolBreakdown, players: Int, rebuys: Int, addOns: Int): String {
        val res = context.resources
        fun part(plural: Int, count: Int, cents: Long) = res.getQuantityString(plural, count, count, formatMoney(cents))
        val parts = buildList {
            add(part(R.plurals.payouts_buy_ins, players, pool.buyInCents))
            if (rebuys > 0) add(part(R.plurals.payouts_rebuys, rebuys, pool.rebuyCents))
            if (addOns > 0) add(part(R.plurals.payouts_add_ons, addOns, pool.addOnCents))
        }
        return parts.joinToString(context.getString(R.string.payouts_separator))
    }

    /** "1st: $180", and "1st: $180 · Alice" once [winner] has finished there. */
    fun placeLine(context: Context, place: Int, amountCents: Long, winner: String?): String {
        val line = context.getString(R.string.payouts_share_row, ordinalOf(place), formatMoney(amountCents))
        return winner?.let { context.getString(R.string.payouts_share_row_winner, line, it) } ?: line
    }

    /** "Standard, rounded to $5"; "Custom, rounded to $5" for weights edited by hand. */
    fun structureLine(context: Context, preset: PayoutPreset?, rounding: PayoutRounding): String {
        val structure = preset?.let { presetLabel(context, it) } ?: context.getString(R.string.payouts_custom)
        return context.getString(R.string.payouts_share_structure, structure, rounding.label)
    }

    /**
     * "Bounties, $5 a head: Dana $10 (Ben, Rita) · …"; progressive and mystery bounties (PP-035) say
     * so, and each amount is what the knockouts paid.
     */
    private fun bounties(context: Context, state: PayoutsUiState): String? {
        val bounties = state.bounties
        if (bounties.perHeadCents <= 0L || (bounties.claims.isEmpty() && bounties.championName == null)) return null
        val claims = bounties.claims.map { claim ->
            context.getString(
                R.string.payouts_share_claim,
                claim.name,
                formatMoney(claim.cents),
                claim.victims.joinToString(", ")
            )
        }
        val championLine = if (bounties.mode == BountyMode.MYSTERY) {
            R.string.payouts_share_champion_mystery
        } else {
            R.string.payouts_share_champion_bounty
        }
        val champion = bounties.championName?.takeIf { bounties.championCents > 0L }?.let {
            context.getString(championLine, it, formatMoney(bounties.championCents))
        }
        val lines = (claims + listOfNotNull(champion)).joinToString(context.getString(R.string.payouts_separator))
        val perHead = formatMoney(bounties.perHeadCents)
        return when (bounties.mode) {
            BountyMode.STANDARD -> context.getString(R.string.payouts_share_bounties, perHead, lines)
            BountyMode.PROGRESSIVE -> context.getString(R.string.payouts_share_bounties_pko, perHead, lines)
            BountyMode.MYSTERY -> context.resources.getQuantityString(
                R.plurals.payouts_share_bounties_mystery,
                bounties.envelopes,
                bounties.envelopes,
                lines
            )
        }
    }

    fun presetLabel(context: Context, preset: PayoutPreset): String = context.getString(
        when (preset) {
            PayoutPreset.TOP_HEAVY -> R.string.payouts_preset_top_heavy
            PayoutPreset.STANDARD -> R.string.payouts_preset_standard
            PayoutPreset.FLAT -> R.string.payouts_preset_flat
        }
    )
}
