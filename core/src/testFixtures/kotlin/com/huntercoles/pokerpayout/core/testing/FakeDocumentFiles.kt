package com.huntercoles.pokerpayout.core.testing

import android.net.Uri
import com.huntercoles.pokerpayout.core.backup.BackupException
import com.huntercoles.pokerpayout.core.backup.BackupProblem
import com.huntercoles.pokerpayout.core.backup.DocumentFiles

/**
 * The files the system's file picker would hand over, in memory: [texts] by URI. A file that isn't
 * there can't be read; with [failWrites] every write fails as a full or read-only folder would.
 */
class FakeDocumentFiles : DocumentFiles {
    val texts = mutableMapOf<String, String>()
    var failWrites = false

    override fun read(uri: Uri, maxBytes: Int): String {
        val text = texts[uri.toString()] ?: throw BackupException(BackupProblem.CantRead)
        if (text.toByteArray(Charsets.UTF_8).size > maxBytes) throw BackupException(BackupProblem.TooBig)
        return text
    }

    override fun write(uri: Uri, text: String) {
        if (failWrites) throw BackupException(BackupProblem.CantWrite)
        texts[uri.toString()] = text
    }

    override fun name(uri: Uri): String? = uri.lastPathSegment
}
