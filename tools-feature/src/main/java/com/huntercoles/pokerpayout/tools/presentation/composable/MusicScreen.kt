package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.pokerpayout.core.audio.music.BreakMusic
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerSegmentedControl
import com.huntercoles.pokerpayout.core.design.components.PokerTopBar
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.MusicIntent
import com.huntercoles.pokerpayout.tools.presentation.MusicUiState
import com.huntercoles.pokerpayout.tools.presentation.MusicViewModel

/**
 * The Music route (Tools > Sound > Music): [MusicContent], and the system's file picker for adding
 * songs. The picker lends the app each file the host picks, so no storage permission is needed.
 */
@Composable
fun MusicRoute(onBack: () -> Unit, viewModel: MusicViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.acceptIntent(MusicIntent.AddPicked(uris.map { it.toString() }))
    }
    MusicContent(
        state = state,
        onIntent = viewModel::acceptIntent,
        onBack = onBack,
        // A phone with no file picker at all (rare) just does nothing
        onAddSongs = { runCatching { picker.launch(arrayOf(AUDIO_FILES)) } },
    )
}

private const val AUDIO_FILES = "audio/*"

/**
 * Music (S18), stateless: what plays and the buttons to play it, the music's volume, playing with
 * the clock (and what it does on breaks), the songs (tap one to play it; Edit to move or remove),
 * and the songs that come with the app, none yet.
 */
@Composable
fun MusicContent(
    state: MusicUiState,
    onIntent: (MusicIntent) -> Unit,
    onBack: () -> Unit,
    onAddSongs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val songs = state.tracks.size
    val count = pluralStringResource(R.plurals.tools_music_songs, songs, songs)
    val subtitle = when {
        songs == 0 -> stringResource(R.string.tools_music_none)
        state.playing -> stringResource(R.string.music_subtitle_playing, count)
        else -> count
    }
    Column(modifier.fillMaxSize().background(PokerColors.PokerBlack)) {
        PokerTopBar(title = stringResource(R.string.music_title), subtitle = subtitle, onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = PokerDimens.Gutter, end = PokerDimens.Gutter, top = 4.dp, bottom = PokerDimens.Gutter),
            verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
        ) {
            MusicCard { NowPlaying(state, onIntent) }
            MusicCard { WithTheClock(state, onIntent) }
            MusicCard { SongList(state, onIntent, onAddSongs) }
            MusicCard { BuiltInSongs(state, onIntent) }
        }
    }
}

/** A felt card, as the Sound section's. */
@Composable
internal fun MusicCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PokerDimens.CornerCard))
            .background(PokerColors.FeltGreen)
            .padding(PokerDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
        content = content,
    )
}

/** "Play with the clock" and, under it, what the music does on breaks. */
@Composable
private fun WithTheClock(state: MusicUiState, onIntent: (MusicIntent) -> Unit) {
    SwitchRow(
        title = stringResource(R.string.music_with_clock_title),
        description = stringResource(R.string.music_with_clock_description),
        checked = state.autoPlay,
        onChange = { onIntent(MusicIntent.SetAutoPlay(it)) },
    )
    // Always Chalk: the heading is read with the clock off too (ChalkDim was 3.3:1, under AA); the
    // choices under it dim to show they're off
    PokerEyebrow(stringResource(R.string.music_on_breaks), color = PokerColors.Chalk)
    val labels = mapOf(
        BreakMusic.KEEP to stringResource(R.string.music_break_keep),
        BreakMusic.PAUSE to stringResource(R.string.music_break_pause),
        BreakMusic.QUIET to stringResource(R.string.music_break_quiet),
    )
    PokerSegmentedControl(
        options = BreakMusic.entries,
        selected = state.breakMusic,
        onSelect = { onIntent(MusicIntent.SetBreakMusic(it)) },
        label = { labels.getValue(it) },
        enabled = state.autoPlay,
    )
    Text(
        text = stringResource(R.string.music_background),
        style = MaterialTheme.typography.bodySmall,
        color = PokerColors.Chalk,
    )
}

/** The songs that come with the app, each with Add; none yet, and it says so. */
@Composable
internal fun BuiltInSongs(state: MusicUiState, onIntent: (MusicIntent) -> Unit) {
    PokerEyebrow(stringResource(R.string.music_built_in))
    if (state.bundled.isEmpty()) {
        Text(
            text = stringResource(R.string.music_built_in_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = PokerColors.Chalk,
        )
    }
    state.bundled.forEach { track ->
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = PokerDimens.MinTouch),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = track.title, style = MaterialTheme.typography.bodyLarge, color = PokerColors.CardWhite)
                Text(text = track.credit, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
            }
            if (state.inList(track)) {
                Text(
                    text = stringResource(R.string.music_in_list),
                    style = MaterialTheme.typography.bodySmall,
                    color = PokerColors.Chalk,
                )
            } else {
                val label = stringResource(R.string.music_add_named, track.title)
                PokerButton(
                    text = stringResource(R.string.music_add),
                    onClick = { onIntent(MusicIntent.AddBundled(track.id)) },
                    variant = PokerButtonVariant.Secondary,
                    size = PokerButtonSize.Small,
                    icon = PokerIcons.Plus,
                    modifier = Modifier.semantics { contentDescription = label },
                )
            }
        }
    }
}
