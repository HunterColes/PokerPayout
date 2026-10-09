package com.huntercoles.pokerpayout.core.audio.music

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The playlist as text, for `playlist` in music_prefs: one JSON object, versioned by [FORMAT_KEY] as
 * saved nights are. Reading never throws: text that isn't a playlist of this format reads as an
 * empty playlist, a song without a ref is skipped, an unknown repeat mode reads as off, and unknown
 * fields are ignored, so a later version can add fields under the same format number.
 *
 * The keys at the foot of this file are the saved format: renaming one would lose every playlist.
 */
internal object PlaylistCodec {
    const val FORMAT_KEY = "format"

    /** The format written. A change that older readers can't read takes the next number. */
    const val FORMAT = 1

    fun encode(playlist: Playlist): String {
        val tracks = JSONArray()
        playlist.tracks.forEach { tracks.put(JSONObject().put(REF, it.ref).put(TITLE, it.title)) }
        val order = JSONArray()
        playlist.shuffleOrder.forEach { order.put(it) }
        val json = JSONObject()
            .put(FORMAT_KEY, FORMAT)
            .put(TRACKS, tracks)
            .put(SHUFFLE, playlist.shuffle)
            .put(ORDER, order)
            .put(REPEAT, playlist.repeat.name)
        playlist.currentRef?.let { json.put(CURRENT, it) }
        return json.toString()
    }

    fun decode(text: String?): Playlist {
        val json = text?.takeIf { it.isNotBlank() }?.let(::parse)
        return json?.takeIf { it.optInt(FORMAT_KEY) == FORMAT }?.let(::playlist) ?: Playlist()
    }

    private fun parse(text: String): JSONObject? = try {
        JSONObject(text)
    } catch (ignored: JSONException) {
        null
    }

    private fun playlist(json: JSONObject): Playlist {
        val tracks = json.optJSONArray(TRACKS)?.let { array ->
            (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let(::track) }
        }.orEmpty()
        val order = json.optJSONArray(ORDER)?.let { array ->
            (0 until array.length()).mapNotNull { i -> array.optString(i).ifEmpty { null } }
        }.orEmpty()
        return Playlist(
            tracks = tracks,
            currentRef = json.optString(CURRENT).ifEmpty { null },
            shuffle = json.optBoolean(SHUFFLE),
            shuffleOrder = order,
            repeat = RepeatMode.entries.firstOrNull { it.name == json.optString(REPEAT) } ?: RepeatMode.OFF,
        ).normalized()
    }

    private fun track(json: JSONObject): MusicTrack? {
        val ref = json.optString(REF)
        return if (ref.isEmpty()) null else MusicTrack(ref, json.optString(TITLE).ifEmpty { ref })
    }

    private const val TRACKS = "tracks"
    private const val REF = "ref"
    private const val TITLE = "title"
    private const val CURRENT = "current"
    private const val SHUFFLE = "shuffle"
    private const val ORDER = "order"
    private const val REPEAT = "repeat"
}
