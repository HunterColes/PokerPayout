package com.huntercoles.pokerpayout.core.audio.custom

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** The cue sound file the host picked, behind the system's file picker. Reads storage: off the main thread. */
interface CueSoundFiles {
    /**
     * Keeps the file at [uri] (from the system's file picker) readable after a restart and measures
     * it; null when it can't be read or has no length, and then nothing is kept.
     */
    fun keep(uri: String): CustomCueSound?

    /** Whether the file at [uri] can still be read (false once its grant is gone or it was deleted). */
    fun exists(uri: String): Boolean

    /** Lets go of a file the host no longer uses. */
    fun forget(uri: String)
}

/**
 * The [CueSoundFiles] on the phone, like the music's picked songs (AndroidMusicLibrary): the file
 * picker (ACTION_OPEN_DOCUMENT) lends the app the file, and taking the persistable grant keeps that
 * loan across restarts. No storage permission is needed, and none is asked for.
 */
class AndroidCueSoundFiles @Inject constructor(@ApplicationContext private val context: Context) : CueSoundFiles {

    override fun keep(uri: String): CustomCueSound? {
        val parsed = Uri.parse(uri)
        runCatching { context.contentResolver.takePersistableUriPermission(parsed, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val length = lengthOf(parsed)
        if (length == null || length <= 0) {
            forget(uri)
            return null
        }
        return CustomCueSound(uri = uri, lengthMillis = length)
    }

    override fun exists(uri: String): Boolean = runCatching {
        context.contentResolver.openAssetFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false
    }.getOrDefault(false)

    override fun forget(uri: String) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** The file's length in milliseconds, or null when it can't be read. */
    private fun lengthOf(uri: Uri): Long? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } finally {
            runCatching { retriever.release() }
        }
    }.getOrNull()
}

/** The picked cue sound file, as the phone keeps it. */
@Module
@InstallIn(SingletonComponent::class)
abstract class CueSoundFilesModule {
    @Binds
    abstract fun bindCueSoundFiles(files: AndroidCueSoundFiles): CueSoundFiles
}
