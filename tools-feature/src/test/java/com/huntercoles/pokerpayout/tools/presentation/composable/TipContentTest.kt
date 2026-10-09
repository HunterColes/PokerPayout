package com.huntercoles.pokerpayout.tools.presentation.composable

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.tip.TipJar
import com.huntercoles.pokerpayout.tools.presentation.TipUiState
import com.huntercoles.pokerpayout.tools.presentation.TipViewModel
import com.huntercoles.pokerpayout.tools.tip.TipCoin
import com.huntercoles.pokerpayout.tools.tip.TipLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * What Tip the dealer's controls do (S25, PP-112) and what TalkBack hears: each Copy names its coin
 * and says when it has copied, each page opens in the browser as a plain VIEW intent (the app has no
 * internet permission), and the route really puts the address on the clipboard.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w360dp-h780dp-port-xhdpi")
class TipContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val copied = mutableListOf<TipCoin>()
    private val opened = mutableListOf<TipLink>()

    @Before
    fun wipe() {
        listOf(TipJar.FILE, "timer_prefs").forEach {
            compose.activity.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun show(state: TipUiState = TipUiState()) {
        compose.setContent {
            PokerTheme(reducedMotion = true) {
                TipContent(state, onBack = {}, onCopy = { copied += it }, onOpen = { opened += it })
            }
        }
    }

    @Test
    fun eachControlSendsWhatItSays() {
        show()
        compose.onNodeWithText("Open the donation page").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Copy the Ethereum address").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Copy the Monero address").performScrollTo().performClick()
        compose.onNodeWithText("Star on GitHub").performScrollTo().performClick()
        compose.onNodeWithText("Suggest an idea").performScrollTo().performClick()
        assertEquals(listOf(TipCoin.ETH, TipCoin.XMR), copied)
        assertEquals(listOf(TipLink.DONATION_PAGE, TipLink.REPOSITORY, TipLink.IDEAS), opened)
    }

    @Test
    fun theAddressesAndTheirCodesAreThereToCheck() {
        show()
        compose.onNodeWithContentDescription("QR code for the Ethereum address").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("QR code for the Monero address").performScrollTo().assertExists()
        compose.onNodeWithText(TipCoin.ETH.grouped).performScrollTo().assertExists()
        compose.onNodeWithText(TipCoin.XMR.grouped).performScrollTo().assertExists()
    }

    @Test
    fun aCopiedAddressSaysSo() {
        show(TipUiState(copied = TipCoin.XMR))
        compose.onNodeWithContentDescription("Monero address copied").performScrollTo().assertExists()
        compose.onNodeWithText("Copied").assertExists()
        compose.onNodeWithContentDescription("Copy the Ethereum address").assertExists()
    }

    @Test
    fun noBrowserSaysTheAddressesStillWork() {
        show(TipUiState(noBrowser = TipLink.DONATION_PAGE))
        compose.onNodeWithText("No app on this phone can open web pages. The addresses below work without one.")
            .performScrollTo().assertExists()
    }

    @Test
    fun theRouteCopiesTheAddressAndOpensTheBrowser() {
        val jar = TipJar(compose.activity, TimerPreferences(compose.activity))
        val viewModel = TipViewModel(jar)
        compose.setContent { PokerTheme(reducedMotion = true) { TipRoute(onBack = {}, viewModel = viewModel) } }

        compose.onNodeWithContentDescription("Copy the Monero address").performScrollTo().performClick()
        compose.waitForIdle()
        val clip = compose.activity.getSystemService(ClipboardManager::class.java).primaryClip!!
        assertEquals(TipCoin.XMR.address, clip.getItemAt(0).text.toString())
        compose.onNodeWithContentDescription("Monero address copied").assertExists()

        compose.onNodeWithText("Star on GitHub").performScrollTo().performClick()
        compose.waitForIdle()
        val view = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals(TipLink.REPOSITORY.url, view.dataString)
        assertTrue(view.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertTrue("an address copied: the card never asks again", jar.ask.value.stopped)
    }

    @Test
    fun aStarAloneLeavesTheCardAsItWas() {
        val jar = TipJar(compose.activity, TimerPreferences(compose.activity))
        compose.setContent { PokerTheme(reducedMotion = true) { TipRoute(onBack = {}, viewModel = TipViewModel(jar)) } }
        compose.onNodeWithText("Star on GitHub").performScrollTo().performClick()
        compose.waitForIdle()
        assertFalse(jar.ask.value.stopped)
    }
}
