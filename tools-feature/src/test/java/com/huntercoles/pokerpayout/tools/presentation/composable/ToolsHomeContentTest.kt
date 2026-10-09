package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.core.navigation.NavigationDestination
import com.huntercoles.pokerpayout.tools.presentation.MusicSummary
import com.huntercoles.pokerpayout.tools.presentation.ToolsHomeIntent
import com.huntercoles.pokerpayout.tools.presentation.ToolsHomeUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the Tools tab's rows and Sound controls do when tapped, and what TalkBack hears. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w360dp-h780dp-port-xhdpi")
class ToolsHomeContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val intents = mutableListOf<ToolsHomeIntent>()
    private val opened = mutableListOf<NavigationDestination>()

    private fun show(state: ToolsHomeUiState) {
        compose.setContent {
            PokerTheme(reducedMotion = true) {
                ToolsHomeContent(
                    state = state,
                    onIntent = { intents += it },
                    onOpenTool = { opened += it },
                    versionName = "1.3.0",
                )
            }
        }
    }

    @Test
    fun eachRowOpensItsTool() {
        show(ToolsHomeUiState())
        compose.onNodeWithText("Odds").performClick()
        compose.onNodeWithText("Chip set").performClick()
        compose.onNodeWithText("Hand ranks").performClick()
        compose.onNodeWithText("Seat draw").performClick()
        compose.onNodeWithText("Outs & pot odds").performScrollTo().performClick()
        compose.onNodeWithText("Side pots").performScrollTo().performClick()
        compose.onNodeWithText("Deal maker").performScrollTo().performClick()
        compose.onNodeWithText("Shot clock").performScrollTo().performClick()
        compose.onNodeWithText("Dealer's choice").performScrollTo().performClick()
        compose.onNodeWithText("Equity quiz").performScrollTo().performClick()
        compose.onNodeWithText("History").performScrollTo().performClick()
        compose.onNodeWithText("Backup").performScrollTo().performClick()
        compose.onNodeWithText("Currency").performScrollTo().performClick()
        // PP-112: quietly at the foot of the list, after Sound, next to the app's promise
        compose.onNodeWithText("Tip the dealer").performScrollTo().performClick()
        assertEquals(
            listOf(
                NavigationDestination.OddsCalculator,
                NavigationDestination.ChipCalculator,
                NavigationDestination.HandRanks,
                NavigationDestination.SeatDraw,
                NavigationDestination.Outs,
                NavigationDestination.SidePots,
                NavigationDestination.DealMaker,
                NavigationDestination.ShotClock,
                NavigationDestination.DealersChoice,
                NavigationDestination.EquityQuiz,
                NavigationDestination.History,
                NavigationDestination.Backup,
                NavigationDestination.Currency,
                NavigationDestination.TipDealer,
            ),
            opened,
        )
    }

    @Test
    fun theSoundRowIsOneSwitch() {
        show(ToolsHomeUiState(soundOn = true))
        val soundSwitch = compose.onNode(hasText("Sound") and isSwitch).performScrollTo()
        soundSwitch.assertIsOn()
        soundSwitch.performScrollTo().performClick()
        assertEquals(listOf<ToolsHomeIntent>(ToolsHomeIntent.SetSoundOn(false)), intents)
    }

    @Test
    fun withTheSoundOffTheVolumeAndTestChimeRest() {
        show(ToolsHomeUiState(soundOn = false, volume = 0.5f))
        compose.onNode(hasText("Sound") and isSwitch).assertIsOff()
        compose.onNode(hasContentDescription("Chime volume")).assertIsNotEnabled()
        compose.onNodeWithText("Test chime").assertIsNotEnabled()
        compose.onNodeWithText("50%").assertExists()
    }

    @Test
    fun testChimeAsksForTheChime() {
        show(ToolsHomeUiState(soundOn = true))
        compose.onNode(hasContentDescription("Chime volume")).assertIsEnabled()
        compose.onNodeWithText("Test chime").performScrollTo().performClick()
        assertEquals(listOf<ToolsHomeIntent>(ToolsHomeIntent.TestChime), intents)
    }

    @Test
    fun theFooterKeepsWhatTheOldAboutCardSaid() {
        show(ToolsHomeUiState())
        compose.onNodeWithText("Free and open source", substring = true).assertExists()
        compose.onNodeWithText("No ads, no accounts, no tracking", substring = true).assertExists()
        compose.onNodeWithText("v1.3.0", substring = true).assertExists()
    }

    @Test
    fun theVibrateAndFlashRowsAreEachOneSwitch() {
        show(ToolsHomeUiState(vibrate = true, flash = true))
        compose.onNode(hasText("Vibrate") and isSwitch).performScrollTo().assertIsOn().performClick()
        compose.onNode(hasText("Flash the clock") and isSwitch).performScrollTo().assertIsOn().performClick()
        assertEquals(listOf(ToolsHomeIntent.SetVibrate(false), ToolsHomeIntent.SetFlash(false)), intents)
    }

    @Test
    fun theQuietCuesStayLiveWithTheSoundOff() {
        // For a muted phone or a quiet room: they don't rest with the chime
        show(ToolsHomeUiState(soundOn = false, vibrate = true, flash = false))
        compose.onNode(hasText("Vibrate") and isSwitch).performScrollTo().assertIsOn().assertIsEnabled()
        compose.onNode(hasText("Flash the clock") and isSwitch).performScrollTo().assertIsOff().assertIsEnabled()
    }

    @Test
    fun aDeviceThatCannotVibrateHasNoVibrateRow() {
        show(ToolsHomeUiState(canVibrate = false))
        compose.onNode(hasText("Vibrate") and isSwitch).assertDoesNotExist()
        compose.onNode(hasText("Flash the clock") and isSwitch).assertExists()
    }

    @Test
    fun withNotificationsOffTheSectionOffersToTurnThemOn() {
        var allowed = 0
        compose.setContent {
            PokerTheme(reducedMotion = true) {
                ToolsHomeContent(
                    state = ToolsHomeUiState(notificationsOff = true),
                    onIntent = { intents += it },
                    onOpenTool = { opened += it },
                    versionName = "1.3.0",
                    onAllowNotifications = { allowed++ },
                )
            }
        }
        compose.onNodeWithText("Clock on the lock screen").performScrollTo().assertExists()
        compose.onNodeWithText("Turn on notifications").performScrollTo().performClick()
        assertEquals(1, allowed)
        assertEquals(emptyList<ToolsHomeIntent>(), intents)
    }

    @Test
    fun withNotificationsOnThereIsNothingToTurnOn() {
        show(ToolsHomeUiState(notificationsOff = false))
        compose.onNodeWithText("Turn on notifications").assertDoesNotExist()
        compose.onNodeWithText("Clock on the lock screen").assertDoesNotExist()
    }

    @Test
    fun theCueSoundsRowNamesThePickedPackAndOpensThePacks() {
        show(ToolsHomeUiState())
        compose.onNodeWithText("Cue sounds").performScrollTo().assertExists()
        compose.onNodeWithText("Classic").assertExists()
        compose.onNodeWithText("Cue sounds").performClick()
        assertEquals(listOf<NavigationDestination>(NavigationDestination.CueSounds), opened)
    }

    @Test
    fun withNoSongsTheMusicRowSaysSoAndOpensTheMusic() {
        show(ToolsHomeUiState())
        compose.onNodeWithText("No songs yet").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Play music").assertDoesNotExist()
        compose.onNodeWithText("Music").performScrollTo().performClick()
        assertEquals(listOf<NavigationDestination>(NavigationDestination.Music), opened)
    }

    @Test
    fun withSongsTheMusicRowSaysWhatPlaysAndPlaysOrPauses() {
        show(ToolsHomeUiState(music = MusicSummary(songs = 3, playing = true, current = "Night Owl")))
        compose.onNodeWithText("Playing: Night Owl").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Pause music").performScrollTo().performClick()
        assertEquals(listOf<ToolsHomeIntent>(ToolsHomeIntent.ToggleMusic), intents)
    }

    @Test
    fun pausedTheMusicRowCountsTheSongs() {
        show(ToolsHomeUiState(music = MusicSummary(songs = 3, playing = false, current = "Night Owl")))
        compose.onNodeWithText("3 songs").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Play music").assertExists()
    }

    private val isSwitch = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
}
