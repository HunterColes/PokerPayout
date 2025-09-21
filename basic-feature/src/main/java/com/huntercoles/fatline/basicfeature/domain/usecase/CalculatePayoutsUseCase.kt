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
        // Calculate number of paying positions (max 1/3 of players or length of weights)
        val maxPayingPositions = min(
            max(1, config.numPlayers / 3),
            config.payoutWeights.size
        )
        
        // Get paying weights
        val payingWeights = config.payoutWeights.take(maxPayingPositions)
        val totalWeight = payingWeights.sum()
        
        if (totalWeight == 0) {
            return emptyList()
        }
        
        // Calculate payouts for each position
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