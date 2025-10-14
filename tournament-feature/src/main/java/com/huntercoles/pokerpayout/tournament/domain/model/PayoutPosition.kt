package com.huntercoles.pokerpayout.tournament.domain.model

import com.huntercoles.pokerpayout.core.utils.FormatUtils

/**
 * Represents a payout for a specific position in the tournament
 */
data class PayoutPosition(
    val position: Int,
    val payout: Double,
    val weight: Int,
    val percentage: Double
) {
    val positionSuffix: String
        get() = when {
            position % 100 in 10..20 -> "th"
            position % 10 == 1 -> "st"
            position % 10 == 2 -> "nd"
            position % 10 == 3 -> "rd"
            else -> "th"
        }
        
    val formattedPosition: String
        get() = "$position$positionSuffix"
        
    val isPaying: Boolean
        get() = payout > 0.0
        
    val formattedPayout: String
        get() = if (isPaying) FormatUtils.formatCurrency(payout) else "-----"
        
    val formattedPercentage: String
        get() = if (isPaying) FormatUtils.formatPercent(percentage) else "-----"
}