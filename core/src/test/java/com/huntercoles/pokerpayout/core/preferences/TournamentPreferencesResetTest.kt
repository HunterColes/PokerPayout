package com.huntercoles.pokerpayout.core.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Tournament's Reset is offered while anything it resets differs from a new tournament
 * ([TournamentPreferences.isInDefaultState]), the cutoffs included: a host who only set "rebuys
 * until level 4" can still reset it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TournamentPreferencesResetTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clear() {
        context.getSharedPreferences("tournament_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `a rebuy cutoff alone is a change Reset takes back`() {
        val prefs = TournamentPreferences(context)
        assertTrue(prefs.isInDefaultState())
        prefs.setRebuyUntilLevel(4)
        assertFalse(prefs.isInDefaultState())
        prefs.resetAllTournamentData()
        assertEquals(0, prefs.getRebuyUntilLevel())
        assertTrue(prefs.isInDefaultState())
    }

    @Test
    fun `a late entry cutoff alone is a change Reset takes back`() {
        val prefs = TournamentPreferences(context)
        prefs.setLateEntryUntilLevel(6)
        assertFalse(prefs.isInDefaultState())
        prefs.resetAllTournamentData()
        assertEquals(0, prefs.getLateEntryUntilLevel())
        assertTrue(prefs.isInDefaultState())
    }

    @Test
    fun `a bounty type alone is a change Reset takes back`() {
        val prefs = TournamentPreferences(context)
        prefs.setBountyMode(BountyMode.PROGRESSIVE)
        assertFalse(prefs.isInDefaultState())
        prefs.resetAllTournamentData()
        assertEquals(BountyMode.STANDARD, prefs.getBountyMode())
        assertTrue(prefs.isInDefaultState())
    }
}
