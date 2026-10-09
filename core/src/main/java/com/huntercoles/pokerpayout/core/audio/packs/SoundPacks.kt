package com.huntercoles.pokerpayout.core.audio.packs

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.constants.AudioConstants

/** The moments the clock can play a sound for: one slot each in a [SoundPack]. */
enum class CueEvent {
    /** A new blind level starts. */
    LEVEL_UP,

    /** One minute left in a level or a break. */
    ONE_MINUTE,

    /** A break starts. */
    BREAK_START,

    /** A break ends and play starts again. */
    BREAK_END,

    /** The last level ends: the clock has finished. */
    GAME_OVER,
}

/**
 * A set of cue sounds, one slot per [CueEvent]. A slot with no sound is silent; the vibration and the
 * flash still come. The sounds for a change (every slot but [CueEvent.ONE_MINUTE]) start
 * [AudioConstants.LEVEL_CHANGE_SOUND_LEAD_SECONDS] before the change, so they should peak about that
 * far in, as the chime does. The one-minute sound plays on the minute.
 *
 * Adding a pack or a sound: docs/SOUNDS.md.
 */
data class SoundPack(
    /** Saved as the host's choice (`sound_pack` in audio_prefs): never change it once the pack has shipped. */
    val id: String,
    @StringRes val name: Int,
    @StringRes val description: Int,
    private val sounds: Map<CueEvent, Int>,
) {
    /** The sound for [event], or null when the slot is empty. */
    @RawRes
    fun soundFor(event: CueEvent): Int? = sounds[event]
}

/** Every sound pack the app has. The host picks one in Tools > Sound > Cue sounds. */
object SoundPacks {
    const val CLASSIC_ID = "classic"

    /** The sounds the clock has always played: the chime at every change, nothing with a minute left. */
    val Classic = SoundPack(
        id = CLASSIC_ID,
        name = R.string.sound_pack_classic,
        description = R.string.sound_pack_classic_description,
        sounds = mapOf(
            CueEvent.LEVEL_UP to R.raw.blind_level_up,
            CueEvent.BREAK_START to R.raw.blind_level_up,
            CueEvent.BREAK_END to R.raw.blind_level_up,
            CueEvent.GAME_OVER to R.raw.blind_level_up,
        ),
    )

    /** The default pack: a fresh install, or a saved pack that is no longer here. */
    val default: SoundPack = Classic

    /** Every pack, the default first. A new pack is one more entry here. */
    val all: List<SoundPack> = listOf(Classic)

    /** The pack saved as [id], or the default when there is none by that id. */
    fun byId(id: String?): SoundPack = all.firstOrNull { it.id == id } ?: default
}
