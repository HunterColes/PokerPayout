package com.huntercoles.pokerpayout.core.audio.music

import androidx.annotation.RawRes

/**
 * A song that comes with the app: an audio file in `core/src/main/res/raw`, its title, and who made
 * it under which licence (shown under the title, as the licence asks).
 */
data class BundledTrack(
    /** Saved in playlists ("bundled:<id>"): never change it once the track has shipped. */
    val id: String,
    val title: String,
    @RawRes val res: Int,
    /** Who made it and its licence, as shown: "Kai Engel, CC BY 4.0". */
    val credit: String,
) {
    val ref: String get() = BundledTracks.ref(id)

    fun toTrack(): MusicTrack = MusicTrack(ref, title)
}

/**
 * The songs that come with the app, offered in Tools > Sound > Music beside the host's own. None
 * yet: the list says so plainly until there are some.
 *
 * To add one (docs/SOUNDS.md has the whole checklist): put the file in `core/src/main/res/raw`
 * (lowercase, `music_` first: `music_night_owl.ogg`), check its licence is one F-Droid accepts
 * (CC0, CC BY, CC BY-SA; never "non-commercial" or "no derivatives"), and add one line here:
 *
 * ```
 * BundledTrack("night_owl", "Night Owl", R.raw.music_night_owl, "Jane Doe, CC BY 4.0"),
 * ```
 */
object BundledTracks {
    private const val PREFIX = "bundled:"

    val all: List<BundledTrack> = listOf(
        // BundledTrack("night_owl", "Night Owl", R.raw.music_night_owl, "Jane Doe, CC BY 4.0"),
    )

    fun ref(id: String): String = PREFIX + id

    /** The bundled track [ref] names, or null for a picked file or a track no longer bundled. */
    fun byRef(ref: String): BundledTrack? =
        ref.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.let { id -> all.firstOrNull { it.id == id } }

    fun isBundled(ref: String): Boolean = ref.startsWith(PREFIX)
}
