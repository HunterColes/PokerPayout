package com.huntercoles.pokerpayout.core.backup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/**
 * Files the player picks with the system's file picker (CreateDocument and OpenDocument): their own
 * Drive, Downloads or SD card. The picker grants access to that one file, so the app needs no storage
 * permission (and has no internet permission at all). Text is UTF-8.
 */
interface DocumentFiles {
    /**
     * The text in [uri]; throws [BackupException] ([BackupProblem.CantRead], or [BackupProblem.TooBig]
     * above [maxBytes]) and nothing else, for a file that can't be reached any more too.
     */
    fun read(uri: Uri, maxBytes: Int = BackupJson.MAX_BYTES): String

    /** Writes [text] to [uri] in place of what it held; throws [BackupException] ([BackupProblem.CantWrite]) and nothing else. */
    fun write(uri: Uri, text: String)

    /** The file's name as the picker shows it ("poker-payout-2026-10-08.json"), or null. */
    fun name(uri: Uri): String?
}

class ResolverDocumentFiles @Inject constructor(@ApplicationContext private val context: Context) : DocumentFiles {

    override fun read(uri: Uri, maxBytes: Int): String {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readAtMost(maxBytes + 1) }
        } catch (expected: IOException) {
            fail(BackupProblem.CantRead, expected)
        } catch (expected: SecurityException) {
            fail(BackupProblem.CantRead, expected)
        } catch (expected: IllegalArgumentException) {
            // A file the picker's provider no longer knows (access revoked, or its app gone)
            fail(BackupProblem.CantRead, expected)
        } ?: fail(BackupProblem.CantRead)
        if (bytes.size > maxBytes) fail(BackupProblem.TooBig)
        return bytes.toString(Charsets.UTF_8)
    }

    override fun write(uri: Uri, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val written = try {
            open(uri)?.use { it.write(bytes) } != null
        } catch (expected: IOException) {
            fail(BackupProblem.CantWrite, expected)
        } catch (expected: SecurityException) {
            fail(BackupProblem.CantWrite, expected)
        } catch (expected: IllegalArgumentException) {
            // A file the picker's provider no longer knows, in either mode open() tries
            fail(BackupProblem.CantWrite, expected)
        }
        if (!written) fail(BackupProblem.CantWrite)
    }

    /** "wt" truncates what was there; a provider that doesn't know it gets "w" (a new file is empty anyway). */
    private fun open(uri: Uri): OutputStream? = try {
        context.contentResolver.openOutputStream(uri, "wt")
    } catch (ignored: IllegalArgumentException) {
        context.contentResolver.openOutputStream(uri, "w")
    }

    override fun name(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (ignored: SecurityException) {
        null
    } catch (ignored: IllegalArgumentException) {
        null
    } ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun InputStream.readAtMost(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun fail(problem: BackupProblem, cause: Throwable? = null): Nothing = throw BackupException(problem, cause)

    private companion object {
        const val BUFFER_BYTES = 8 * 1024
    }
}

/** [name] with the characters file systems refuse replaced, and never empty. */
fun safeFileName(name: String): String =
    name.replace(UNSAFE_FILE_CHARACTERS, "_").trim().trim('.').ifEmpty { "poker-payout" }.take(MAX_FILE_NAME)

private val UNSAFE_FILE_CHARACTERS = Regex("""[\\/:*?"<>|\p{Cntrl}]""")
private const val MAX_FILE_NAME = 120
