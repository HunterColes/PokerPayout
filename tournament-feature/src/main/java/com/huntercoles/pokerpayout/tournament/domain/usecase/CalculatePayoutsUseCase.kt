package com.huntercoles.pokerpayout.tournament.domain.usecase

import com.huntercoles.pokerpayout.tournament.domain.model.PayoutPosition
import com.huntercoles.pokerpayout.tournament.domain.model.TournamentConfig
import com.huntercoles.pokerpayout.core.constants.TournamentConstants
import kotlin.math.max
import kotlin.math.min

/**
 * Use case for calculating tournament payouts based on weighted distribution
 */
class CalculatePayoutsUseCase {
    
    operator fun invoke(config: TournamentConfig): List<PayoutPosition> {
        // If user has customized payout weights, use all of them
        // Otherwise, fall back to the standard max paying positions calculation
        val maxPayingPositions = max(1, config.numPlayers / 3)
        val defaultWeights = TournamentConstants.DEFAULT_PAYOUT_WEIGHTS.take(maxPayingPositions)
        val isUsingDefaultWeights = when {
            config.payoutWeights == defaultWeights -> true
            config.payoutWeights == TournamentConstants.DEFAULT_PAYOUT_WEIGHTS -> true
            else -> false
        }

        val actualPayingPositions = if (isUsingDefaultWeights) {
            min(maxPayingPositions, defaultWeights.size)
        } else {
            config.payoutWeights.size
        }

        // Get paying weights for calculation
        val payingWeights = if (isUsingDefaultWeights) {
            defaultWeights.take(actualPayingPositions)
        } else {
            config.payoutWeights.take(actualPayingPositions)
        }
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