package com.huntercoles.pokerpayout.tournament.presentation.payouts

import android.content.Context
import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.ordinalOf
import com.huntercoles.pokerpayout.core.utils.FormatUtils.formatMoney
import com.huntercoles.pokerpayout.tournament.R

/**
 * The payouts as plain text, for the group chat (Share, `ACTION_SEND` text/plain; no permission):
 * the pool and where it came from, every paid place with its winner once known, the structure, and
 * the bounties claimed.
 */
object PayoutsShareText {

    fun build(context: Context, state: PayoutsUiState): String = buildString {
        appendLine(context.getString(R.string.payouts_share_title))
        val pool = formatMoney(state.pool.prizePoolCents)
        appendLine(context.getString(R.string.payouts_share_pool, pool, poolSources(context, state)))
        state.rows.forEach { row ->
            val line = context.getString(R.string.payouts_share_row, ordinalOf(row.place), formatMoney(row.amountCents))
            appendLine(row.holderName?.let { context.getString(R.string.payouts_share_row_winner, line, it) } ?: line)
        }
        val structure = state.preset?.let { presetLabel(context, it) } ?: context.getString(R.string.payouts_custom)
        appendLine(context.getString(R.string.payouts_share_structure, structure, state.settings.rounding.label))
        bounties(context, state)?.let { appendLine(it) }
        if (state.bounties.foodCents > 0L) {
            appendLine(context.getString(R.string.payouts_food, formatMoney(state.bounties.foodCents)))
        }
    }.trimEnd()

    /** "9 buy-ins $360 · 1 rebuy $40 · 5 add-ons $50". */
    fun poolSources(context: Context, state: PayoutsUiState): String {
        val res = context.resources
        fun part(plural: Int, count: Int, cents: Long) = res.getQuantityString(plural, count, count, formatMoney(cents))
        val parts = buildList {
            add(part(R.plurals.payouts_buy_ins, state.playerCount, state.pool.buyInCents))
            if (state.rebuyCount > 0) add(part(R.plurals.payouts_rebuys, state.rebuyCount, state.pool.rebuyCents))
            if (state.addOnCount > 0) add(part(R.plurals.payouts_add_ons, state.addOnCount, state.pool.addOnCents))
        }
        return parts.joinToString(context.getString(R.string.payouts_separator))
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
