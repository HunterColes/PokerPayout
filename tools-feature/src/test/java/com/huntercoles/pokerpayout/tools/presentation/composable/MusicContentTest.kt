package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
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
import com.huntercoles.pokerpayout.core.audio.music.BreakMusic
import com.huntercoles.pokerpayout.core.audio.packs.CueEvent
import com.huntercoles.pokerpayout.core.audio.packs.SoundPacks
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.tools.presentation.CueSoundsIntent
import com.huntercoles.pokerpayout.tools.presentation.CueSoundsUiState
import com.huntercoles.pokerpayout.tools.presentation.MusicIntent
import com.huntercoles.pokerpayout.tools.presentation.MusicUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the Music and Cue sounds screens' controls do when tapped, and what TalkBack hears. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w360dp-h780dp-port-xhdpi")
class MusicContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val intents = mutableListOf<MusicIntent>()
    private var adds = 0
    private val songs = MusicFixtures.songs

    private fun show(state: MusicUiState) {
        compose.setContent {
            PokerTheme(reducedMotion = true) {
                MusicContent(state, onIntent = { intents += it }, onBack = {}, onAddSongs = { adds++ })
            }
        }
    }

    @Test
    fun aFreshInstallSaysThereAreNoSongsAndOffersToAddSome() {
        show(MusicFixtures.empty)
        compose.onNodeWithText("Add songs below to play music here.").assertExists()
        compose.onNodeWithContentDescription("Play music").assertIsNotEnabled()
        compose.onNodeWithText("No songs yet. Add music files from your phone", substring = true).performScrollTo()
        compose.onNodeWithText("Add songs").performScrollTo().performClick()
        assertEquals(1, adds)
        compose.onNodeWithText("None yet. Songs that come with the app will show here.").performScrollTo().assertExists()
        compose.onNodeWithText("Edit").assertDoesNotExist()
    }

    @Test
    fun thePlayerButtonsAskForWhatTheySay() {
        show(MusicFixtures.playing)
        compose.onNodeWithContentDescription("Pause music").performClick()
        compose.onNodeWithContentDescription("Next song").performClick()
        compose.onNodeWithContentDescription("Previous song").performClick()
        compose.onNode(hasContentDescription("Shuffle") and isSwitch).assertIsOn().performClick()
        compose.onNodeWithContentDescription("Repeat: all songs").performClick()
        assertEquals(
            listOf(
                MusicIntent.TogglePlay,
                MusicIntent.Next,
                MusicIntent.Previous,
                MusicIntent.SetShuffle(false),
                MusicIntent.CycleRepeat,
            ),
            intents,
        )
    }

    @Test
    fun theSongPlayingAndWhereItIsInTheList() {
        show(MusicFixtures.playing)
        compose.onNodeWithText("Now playing", ignoreCase = true).assertExists()
        compose.onNodeWithText("Song 2 of 5").assertExists()
        // Its title is in the card and in the list
        assertEquals(2, compose.onAllNodes(hasText(songs[1].title)).fetchSemanticsNodes().size)
    }

    @Test
    fun aTapOnASongPlaysIt() {
        show(MusicFixtures.playing)
        compose.onNodeWithText(songs[4].title).performScrollTo().performClick()
        assertEquals(listOf<MusicIntent>(MusicIntent.PlayTrack(songs[4].ref)), intents)
    }

    @Test
    fun aSongWhoseFileHasGoneSaysSo() {
        show(MusicFixtures.playing)
        compose.onNodeWithText("File not found").performScrollTo().assertExists()
    }

    @Test
    fun withEveryFileGoneTheListSaysWhy() {
        show(MusicFixtures.nothingPlayable)
        compose.onNodeWithText("None of these songs can be found", substring = true).performScrollTo().assertExists()
    }

    @Test
    fun editShowsMoveAndRemoveForEachSong() {
        show(MusicFixtures.playing)
        compose.onNodeWithText("Edit").performScrollTo().performClick()
        assertEquals(listOf<MusicIntent>(MusicIntent.SetEditing(true)), intents)
    }

    @Test
    fun whileEditingTheButtonsMoveAndRemoveSongs() {
        show(MusicFixtures.editing)
        compose.onNodeWithContentDescription("Move ${songs[0].title} up").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Move ${songs[4].title} down").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Move ${songs[1].title} up").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Move ${songs[1].title} down").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Remove ${songs[2].title}").performScrollTo().performClick()
        compose.onNodeWithText("Done").performScrollTo().performClick()
        assertEquals(
            listOf(
                MusicIntent.Move(1, 0),
                MusicIntent.Move(1, 2),
                MusicIntent.Remove(songs[2].ref),
                MusicIntent.SetEditing(false),
            ),
            intents,
        )
    }

    @Test
    fun playingWithTheClockIsOneSwitchAndBreaksAreAChoice() {
        show(MusicFixtures.playing)
        compose.onNode(hasText("Play with the clock") and isSwitch).performScrollTo().assertIsOn().performClick()
        compose.onNodeWithText("Pause").performScrollTo().performClick()
        assertEquals(listOf(MusicIntent.SetAutoPlay(false), MusicIntent.SetBreakMusic(BreakMusic.PAUSE)), intents)
    }

    @Test
    fun withTheLinkOffTheBreakChoiceRests() {
        show(MusicFixtures.playing.copy(autoPlay = false))
        compose.onNode(hasText("Play with the clock") and isSwitch).performScrollTo().assertIsOff()
        compose.onNodeWithText("Keep playing").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun aBuiltInSongIsAddedWithOneTap() {
        show(MusicFixtures.withBuiltIn)
        compose.onNodeWithContentDescription("Add The Dealer's Waltz").performScrollTo().performClick()
        compose.onNodeWithText("In your list").performScrollTo().assertExists()
        assertEquals(listOf<MusicIntent>(MusicIntent.AddBundled("dealer")), intents)
    }

    // ------------------------------------------------------------------ cue sounds

    private val cueIntents = mutableListOf<CueSoundsIntent>()

    private fun showCues(state: CueSoundsUiState) {
        compose.setContent {
            PokerTheme(reducedMotion = true) {
                CueSoundsContent(state, onIntent = { cueIntents += it }, onBack = {})
            }
        }
    }

    @Test
    fun eachSlotPlaysItsSoundAndAnEmptyOneSaysSo() {
        showCues(MusicFixtures.cueSounds)
        compose.onNodeWithContentDescription("Play New level, Classic").performClick()
        compose.onNodeWithContentDescription("Play Game over, Classic").performScrollTo().performClick()
        compose.onNodeWithText("No sound").assertExists()
        compose.onNodeWithContentDescription("Play 1 minute left, Classic").assertDoesNotExist()
        assertEquals(
            listOf(
                CueSoundsIntent.Preview(SoundPacks.CLASSIC_ID, CueEvent.LEVEL_UP),
                CueSoundsIntent.Preview(SoundPacks.CLASSIC_ID, CueEvent.GAME_OVER),
            ),
            cueIntents,
        )
    }

    @Test
    fun aPackIsPickedWithItsRadio() {
        showCues(MusicFixtures.cueSounds)
        compose.onNode(hasText("Classic") and isRadio).performClick()
        assertEquals(listOf<CueSoundsIntent>(CueSoundsIntent.Pick(SoundPacks.CLASSIC_ID)), cueIntents)
    }

    @Test
    fun withTheSoundOffTheScreenSaysTheClockPlaysNone() {
        showCues(MusicFixtures.cueSoundsOff)
        compose.onNodeWithText("Sound is off", substring = true).assertExists()
    }

    @Test
    fun withTheSoundOnThereIsNothingToSay() {
        showCues(MusicFixtures.cueSounds)
        compose.onNodeWithText("Sound is off", substring = true).assertDoesNotExist()
    }

    private val isSwitch = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
    private val isRadio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
}
