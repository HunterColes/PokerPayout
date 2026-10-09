package com.huntercoles.pokerpayout.tools.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.tip.TipJar
import com.huntercoles.pokerpayout.tools.tip.TipCoin
import com.huntercoles.pokerpayout.tools.tip.TipLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tip the dealer (S25, PP-112): a copied address says so, a page no app could open says why, and
 * using a way to tip (an address copied, the donation page opened) means the Payouts tab's card never
 * asks again, saved at once so it holds after process death. A star or an idea doesn't count.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TipViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun wipe() {
        listOf(TipJar.FILE, "timer_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun jar() = TipJar(context, TimerPreferences(context))

    @Test
    fun aCopiedAddressSaysSoAndTheCardNeverAsksAgain() {
        val viewModel = TipViewModel(jar())
        viewModel.acceptIntent(TipIntent.Copied(TipCoin.XMR))
        assertEquals(TipCoin.XMR, viewModel.uiState.value.copied)
        assertTrue("after process death", jar().ask.value.stopped)
    }

    @Test
    fun theDonationPageOpenedEndsTheAsksToo() {
        val viewModel = TipViewModel(jar())
        viewModel.acceptIntent(TipIntent.Opened(TipLink.DONATION_PAGE, opened = true))
        assertNull(viewModel.uiState.value.noBrowser)
        assertTrue(jar().ask.value.stopped)
    }

    @Test
    fun aStarOrAnIdeaIsNotATip() {
        val viewModel = TipViewModel(jar())
        viewModel.acceptIntent(TipIntent.Opened(TipLink.REPOSITORY, opened = true))
        viewModel.acceptIntent(TipIntent.Opened(TipLink.IDEAS, opened = true))
        assertFalse(jar().ask.value.stopped)
    }

    @Test
    fun aPageNoAppCouldOpenSaysWhyAndCountsForNothing() {
        val viewModel = TipViewModel(jar())
        viewModel.acceptIntent(TipIntent.Opened(TipLink.DONATION_PAGE, opened = false))
        assertEquals(TipLink.DONATION_PAGE, viewModel.uiState.value.noBrowser)
        assertFalse(jar().ask.value.stopped)
        // Copying an address instead clears the note
        viewModel.acceptIntent(TipIntent.Copied(TipCoin.ETH))
        assertNull(viewModel.uiState.value.noBrowser)
    }
}
