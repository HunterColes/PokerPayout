package com.huntercoles.fatline.basicfeature.domain.usecase

import com.huntercoles.fatline.basicfeature.domain.model.PayoutPosition
import com.huntercoles.fatline.basicfeature.domain.model.TournamentConfig
import kotlin.math.max
import kotlin.math.min

/**
 * Use case for calculating tournament payouts based on weighted distribution
 */
class CalculatePayoutsUseCase {
    
    operator fun invoke(config: TournamentConfig): List<PayoutPosition> {
        // Calculate number of paying positions using the same logic as Python: max(1, num_players // 3)
        val maxPayingPositions = kotlin.math.max(1, config.numPlayers / 3)
        
        // Only show positions that are actually paying out (not all weights)
        val actualPayingPositions = kotlin.math.min(maxPayingPositions, config.payoutWeights.size)
        
        // Get paying weights for calculation
        val payingWeights = config.payoutWeights.take(actualPayingPositions)
        val totalWeight = payingWeights.sum()
        
        if (totalWeight == 0) {
            return emptyList()
        }
        
        // Create only the positions that are actually paying out
        return payingWeights.mapIndexed { index, weight ->
            val position = index + 1
            val payout = (weight.toDouble() / totalWeight) * config.prizePool
            val percentage = (weight.toDouble() / totalWeight) * 100
            
            PayoutPosition(
                position = position,
                payout = payout,
                weight = weight,
                percentage = percentage
            )
        }
    }
}