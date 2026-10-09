package com.huntercoles.pokerpayout.core.audio.music

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Where the songs' files are: bundled ones in the app, picked ones behind the system's file picker. */
interface MusicLibrary {
    /** Points [player] at song [ref]; false when its file can't be found or read. */
    fun open(player: MediaPlayer, ref: String): Boolean

    /** Whether song [ref] can still be read. Reads storage: off the main thread. */
    fun exists(ref: String): Boolean

    /**
     * Keeps the files the host picked ([uris], from the system's file picker) readable after a
     * restart, and names each one. Reads storage: off the main thread.
     */
    fun keep(uris: List<String>): List<MusicTrack>

    /** Lets go of a picked file that has left the playlist. */
    fun forget(ref: String)
}

/**
 * The [MusicLibrary] on the phone. Picked songs come from the system's file picker
 * (ACTION_OPEN_DOCUMENT), which lends the app each file it was given; taking the persistable grant
 * keeps that loan across restarts. No storage permission is needed, and none is asked for.
 */
class AndroidMusicLibrary @Inject constructor(@ApplicationContext private val context: Context) : MusicLibrary {

    override fun open(player: MediaPlayer, ref: String): Boolean = runCatching {
        val bundled = BundledTracks.byRef(ref)
        when {
            bundled != null -> context.resources.openRawResourceFd(bundled.res)?.use {
                player.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                true
            } ?: false
            // A bundled song a later version no longer has
            BundledTracks.isBundled(ref) -> false
            else -> {
                player.setDataSource(context, Uri.parse(ref))
                true
            }
        }
    }.getOrDefault(false)

    override fun exists(ref: String): Boolean = when {
        BundledTracks.isBundled(ref) -> BundledTracks.byRef(ref) != null
        else -> runCatching {
            context.contentResolver.openAssetFileDescriptor(Uri.parse(ref), "r")?.use { true } ?: false
        }.getOrDefault(false)
    }

    override fun keep(uris: List<String>): List<MusicTrack> = uris.map { text ->
        val uri = Uri.parse(text)
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        MusicTrack(ref = text, title = titleOf(uri))
    }

    override fun forget(ref: String) {
        if (BundledTracks.isBundled(ref)) return
        runCatching {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(ref), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** The file's name without its extension ("Night_Owl.mp3" is "Night Owl"); the URI's last part if it has none. */
    private fun titleOf(uri: Uri): String {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()
        return TrackTitles.fromFileName(name).ifEmpty { uri.toString() }
    }
}

/** Song titles from file names. */
object TrackTitles {
    /** "Night_Owl.mp3" is "Night Owl": no folders, no extension, underscores as spaces. */
    fun fromFileName(name: String): String {
        val base = name.substringAfterLast('/')
        val bare = if (base.lastIndexOf('.') > 0) base.substringBeforeLast('.') else base
        return bare.replace('_', ' ').trim()
    }
}
