package com.huntercoles.pokerpayout.tournament.presentation

import com.huntercoles.pokerpayout.core.constants.TournamentConstants
import com.huntercoles.pokerpayout.core.constants.TournamentDefaults
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.max
import kotlin.test.assertEquals

/**
 * Test to verify that payout weights are properly reset when resetAllTournamentData is called
 */
@RunWith(RobolectricTestRunner::class)
class PayoutWeightsResetTest {

    private lateinit var tournamentPreferences: TournamentPreferences
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        tournamentPreferences = TournamentPreferences(context)
    }

    @After
    fun teardown() {
        // Clean up preferences
        tournamentPreferences.resetAllTournamentData()
    }

    @Test
    fun `payout weights should reset to defaults when resetAllTournamentData is called`() {
        // Given: custom payout weights are set
        val customWeights = listOf(50, 30, 20) // Different from defaults
        tournamentPreferences.setPayoutWeights(customWeights)
        
        // Verify custom weights are set
        assertEquals(customWeights, tournamentPreferences.getPayoutWeights())
        
        // When: resetting all tournament data
        tournamentPreferences.resetAllTournamentData()
        
        // Then: payout weights should be back to defaults for current player count
        val expectedDefaults = expectedDefaultWeights()
        assertEquals(expectedDefaults, tournamentPreferences.getPayoutWeights())
    }

    @Test
    fun `payout weights should reset to defaults with correct count`() {
        // Given: custom weights with different count are set (6 positions instead of default 9)
        val customWeights = listOf(40, 25, 20, 10, 3, 2) // 6 positions
        tournamentPreferences.setPayoutWeights(customWeights)
        
        // Verify custom weights are set
        assertEquals(6, tournamentPreferences.getPayoutWeights().size)
        assertEquals(customWeights, tournamentPreferences.getPayoutWeights())
        
        // When: resetting all tournament data
        tournamentPreferences.resetAllTournamentData()
        
        // Then: should be back to default number of positions based on player count
        val resetWeights = tournamentPreferences.getPayoutWeights()
        val expectedDefaults = expectedDefaultWeights()
        assertEquals(expectedDefaults.size, resetWeights.size)
        assertEquals(expectedDefaults, resetWeights)
    }

    @Test
    fun `reset should restore default player count and matching weights`() {
        // Given: a non-default player count with custom weights
        tournamentPreferences.setPlayerCount(18)
        val customWeights = listOf(50, 25, 15, 5, 3, 2)
        tournamentPreferences.setPayoutWeights(customWeights)

        // Sanity check the custom state
        assertEquals(18, tournamentPreferences.getPlayerCount())
        assertEquals(customWeights, tournamentPreferences.getPayoutWeights())

        // When: resetting tournament data
        tournamentPreferences.resetAllTournamentData()

        // Then: player count and weights should return to defaults
        assertEquals(TournamentDefaults.PLAYER_COUNT, tournamentPreferences.getPlayerCount())
        assertEquals(expectedDefaultWeights(TournamentDefaults.PLAYER_COUNT), tournamentPreferences.getPayoutWeights())
    }

    @Test
    fun `default weights constant should be correct`() {
        // Verify the default weights are as expected
        assertEquals(listOf(35, 20, 15, 10, 8, 6, 3, 2, 1), TournamentConstants.DEFAULT_PAYOUT_WEIGHTS)
        assertEquals("35,20,15,10,8,6,3,2,1", TournamentConstants.DEFAULT_PAYOUT_WEIGHTS_STRING)
    }

    private fun expectedDefaultWeights(playerCount: Int = tournamentPreferences.getPlayerCount()): List<Int> {
        val defaultCount = max(1, playerCount / 3)
        return TournamentConstants.DEFAULT_PAYOUT_WEIGHTS.take(defaultCount)
    }
}