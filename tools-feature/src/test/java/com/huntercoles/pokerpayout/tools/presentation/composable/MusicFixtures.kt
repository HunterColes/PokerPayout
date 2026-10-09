package com.huntercoles.pokerpayout.tools.presentation.composable

import com.huntercoles.pokerpayout.core.audio.music.BreakMusic
import com.huntercoles.pokerpayout.core.audio.music.BundledTrack
import com.huntercoles.pokerpayout.core.audio.music.MusicTrack
import com.huntercoles.pokerpayout.core.audio.music.RepeatMode
import com.huntercoles.pokerpayout.core.audio.packs.SoundPacks
import com.huntercoles.pokerpayout.tools.presentation.CueSoundsUiState
import com.huntercoles.pokerpayout.tools.presentation.MusicUiState

/** The Music (S18) and Cue sounds (S18) screens' states for the tests and goldens. */
internal object MusicFixtures {
    val songs = listOf(
        MusicTrack("content://music/1", "Shuffle Up and Deal"),
        MusicTrack("content://music/2", "Midnight Card Room"),
        MusicTrack("content://music/3", "River Card Blues"),
        MusicTrack("content://music/4", "Felt and Smoke"),
        MusicTrack("content://music/5", "All In at Dawn"),
    )

    /** A fresh install: no songs, the link off, nothing built in. */
    val empty = MusicUiState()

    /** Five songs, the second playing, the fourth's file gone; shuffle and repeat on; with the clock, quieter on breaks. */
    val playing = MusicUiState(
        tracks = songs,
        currentRef = songs[1].ref,
        position = 2,
        playing = true,
        shuffle = true,
        repeat = RepeatMode.ALL,
        volume = 0.6f,
        missing = setOf(songs[3].ref),
        autoPlay = true,
        breakMusic = BreakMusic.QUIET,
    )

    /** The same list, paused, with Edit on: move and remove buttons. */
    val editing = playing.copy(playing = false, editing = true, repeat = RepeatMode.ONE, shuffle = false)

    /** Every file gone. */
    val nothingPlayable = playing.copy(playing = false, missing = songs.map { it.ref }.toSet())

    /** Songs that come with the app, one already in the list (none ship yet: layout only). */
    val withBuiltIn = playing.copy(
        bundled = listOf(
            BundledTrack("dealer", "The Dealer's Waltz", res = 1, credit = "Jane Doe, CC BY 4.0"),
            BundledTrack("chips", "Stacks of Chips", res = 2, credit = "Sam Roe, CC0"),
        ),
        tracks = songs + MusicTrack("bundled:chips", "Stacks of Chips"),
    )

    val cueSounds = CueSoundsUiState(packs = SoundPacks.all, selected = SoundPacks.CLASSIC_ID, soundOn = true)

    val cueSoundsOff = cueSounds.copy(soundOn = false)
}
