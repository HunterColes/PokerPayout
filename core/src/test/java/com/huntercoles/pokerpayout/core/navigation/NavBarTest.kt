package com.huntercoles.pokerpayout.core.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.huntercoles.pokerpayout.core.design.PokerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Which tab is selected, and what tab taps do to the back stack (B16): the real
 * [PokerNavigationShell] around a stand-in screen for every destination.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w360dp-h780dp-port-xhdpi")
class NavBarTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var nav: NavHostController

    // ---------------------------------------------------------------- the mapping itself

    @Test
    fun everyTabIsItsOwnTab() {
        NavTab.entries.forEach { tab -> assertEquals(tab, tab.destination.tab) }
    }

    @Test
    fun toolScreensBelongToTools() {
        listOf(
            NavigationDestination.OddsCalculator,
            NavigationDestination.HandRanks,
            NavigationDestination.ChipCalculator,
            NavigationDestination.SeatDraw,
            NavigationDestination.History,
            NavigationDestination.ShotClock,
            NavigationDestination.DealersChoice,
            NavigationDestination.EquityQuiz,
        ).forEach { assertEquals("$it", NavTab.Tools, it.tab) }
        assertNull(NavigationDestination.Back.tab)
    }

    @Test
    fun everyScreenIsLookedUpAndBelongsToATab() {
        val tabs = NavTab.entries.map { it.destination }
        assertTrue("a tab's first screen is missing from ScreenDestinations", ScreenDestinations.containsAll(tabs))
        ScreenDestinations.forEach { assertTrue("$it has no tab", it.tab != null) }
    }

    @Test
    fun tabsAreInBarOrder() {
        assertEquals(
            listOf(NavTab.Tournament, NavTab.Bank, NavTab.Payouts, NavTab.Tools),
            NavTab.entries.toList(),
        )
    }

    // ---------------------------------------------------------------- in the shell

    @Test
    fun startsOnTournament() {
        showApp()
        tab("Tournament").assertIsSelected()
        listOf("Bank", "Payouts", "Tools").forEach { tab(it).assertIsNotSelected() }
        compose.onNodeWithText(SCREEN + "Tournament").assertExists()
    }

    @Test
    fun eachTabSelectsItself() {
        showApp()
        listOf("Bank", "Payouts", "Tools", "Tournament").forEach { label ->
            tab(label).performClick()
            compose.waitForIdle()
            tab(label).assertIsSelected()
            NavTabLabels.minus(label).forEach { tab(it).assertIsNotSelected() }
        }
    }

    @Test
    fun toolScreensKeepToolsSelected() {
        showApp()
        tab("Tools").performClick()
        listOf(
            NavigationDestination.OddsCalculator,
            NavigationDestination.HandRanks,
            NavigationDestination.ChipCalculator,
            NavigationDestination.SeatDraw,
            NavigationDestination.History,
            NavigationDestination.ShotClock,
            NavigationDestination.DealersChoice,
            NavigationDestination.EquityQuiz,
        ).forEach { destination ->
            navigate(destination)
            compose.onNodeWithText(SCREEN + destination).assertExists()
            tab("Tools").assertIsSelected()
            listOf("Tournament", "Bank", "Payouts").forEach { tab(it).assertIsNotSelected() }
            back()
        }
    }

    @Test
    fun tabTapsDoNotPileUpOnTheBackStack() {
        showApp()
        listOf("Bank", "Tools", "Payouts", "Bank", "Bank").forEach { tab(it).performClick() }
        compose.waitForIdle()
        assertOn(NavigationDestination.Bank)

        back() // Bank -> Tournament, not back through Payouts and Tools
        assertOn(NavigationDestination.Tournament)
        tab("Tournament").assertIsSelected()
        assertNull("Tournament should be the only screen left", nav.previousBackStackEntry)
    }

    @Test
    fun tappingToolsInsideAToolReturnsToTheToolsList() {
        showApp()
        tab("Tools").performClick()
        navigate(NavigationDestination.OddsCalculator)
        tab("Tools").performClick()
        compose.waitForIdle()
        assertOn(NavigationDestination.Tools)
        back()
        assertOn(NavigationDestination.Tournament)
    }

    @Test
    fun leavingAToolForAnotherTabAndBackOpensTheToolsList() {
        showApp()
        tab("Tools").performClick()
        navigate(NavigationDestination.HandRanks)
        tab("Bank").performClick()
        tab("Tools").performClick()
        compose.waitForIdle()
        assertOn(NavigationDestination.Tools)
    }

    // ---------------------------------------------------------------- helpers

    private fun showApp() {
        compose.setContent {
            PokerTheme(reducedMotion = true) {
                nav = rememberNavController()
                PokerNavigationShell(navController = nav, factories = setOf(StandInScreens))
            }
        }
        compose.waitForIdle()
    }

    private fun tab(label: String) = compose.onNode(hasText(label) and isTab, useUnmergedTree = false)

    private fun navigate(destination: NavigationDestination) {
        compose.runOnUiThread { nav.navigate(destination) }
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun assertOn(destination: NavigationDestination) {
        val current = nav.currentBackStackEntry?.destination
        assertTrue("expected $destination, on ${current?.route}", current?.hasRoute(destination::class) == true)
    }

    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private companion object {
        const val SCREEN = "Screen: "
        val NavTabLabels = listOf("Tournament", "Bank", "Payouts", "Tools")

        /** A text naming each screen, for every destination the app has. */
        val StandInScreens = object : NavigationFactory {
            override fun create(builder: NavGraphBuilder) {
                builder.composable<NavigationDestination.Tournament> { Text(SCREEN + NavigationDestination.Tournament) }
                builder.composable<NavigationDestination.Bank> { Text(SCREEN + NavigationDestination.Bank) }
                builder.composable<NavigationDestination.Payouts> { Text(SCREEN + NavigationDestination.Payouts) }
                builder.composable<NavigationDestination.Tools> { Text(SCREEN + NavigationDestination.Tools) }
                builder.composable<NavigationDestination.OddsCalculator> { Text(SCREEN + NavigationDestination.OddsCalculator) }
                builder.composable<NavigationDestination.HandRanks> { Text(SCREEN + NavigationDestination.HandRanks) }
                builder.composable<NavigationDestination.ChipCalculator> { Text(SCREEN + NavigationDestination.ChipCalculator) }
                builder.composable<NavigationDestination.SeatDraw> { Text(SCREEN + NavigationDestination.SeatDraw) }
                builder.composable<NavigationDestination.History> { Text(SCREEN + NavigationDestination.History) }
                builder.composable<NavigationDestination.ShotClock> { Text(SCREEN + NavigationDestination.ShotClock) }
                builder.composable<NavigationDestination.DealersChoice> { Text(SCREEN + NavigationDestination.DealersChoice) }
                builder.composable<NavigationDestination.EquityQuiz> { Text(SCREEN + NavigationDestination.EquityQuiz) }
            }
        }
    }
}
