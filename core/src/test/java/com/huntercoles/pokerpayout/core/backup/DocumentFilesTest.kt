package com.huntercoles.pokerpayout.core.backup

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A file the picker handed over that can no longer be reached (access revoked, the file's app gone,
 * a URI its provider doesn't know): providers say so with an IllegalArgumentException, and the
 * screens get the same plain problem as for any file that can't be read or saved, never a crash.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocumentFilesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val files = ResolverDocumentFiles(context)
    private val gone = Uri.parse("content://$AUTHORITY/document/poker-payout-2026-10-08.json")

    @Before
    fun setUp() {
        Robolectric.setupContentProvider(RefusingProvider::class.java, AUTHORITY)
    }

    @Test
    fun `a file its provider refuses can't be read`() {
        val problem = assertThrows(BackupException::class.java) { files.read(gone) }
        assertEquals(BackupProblem.CantRead, problem.problem)
    }

    @Test
    fun `a file its provider refuses can't be saved`() {
        val problem = assertThrows(BackupException::class.java) { files.write(gone, "{}") }
        assertEquals(BackupProblem.CantWrite, problem.problem)
    }

    @Test
    fun `a file its provider refuses is named from its address`() {
        assertEquals("poker-payout-2026-10-08.json", files.name(gone))
    }

    /** A provider that knows no file: every question about one is an IllegalArgumentException. */
    class RefusingProvider : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = refuse(uri)

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor = refuse(uri)

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri = refuse(uri)

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = refuse(uri)

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
            refuse(uri)

        private fun refuse(uri: Uri): Nothing = throw IllegalArgumentException("Unknown URI $uri")
    }

    private companion object {
        const val AUTHORITY = "com.example.documents"
    }
}
