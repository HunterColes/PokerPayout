package com.huntercoles.pokerpayout.core.presentation

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.huntercoles.pokerpayout.core.backup.safeFileName
import java.io.File

/** What a share of plain text says it is. */
const val PLAIN_TEXT = "text/plain"

/**
 * Hands [text] to any app that takes [mimeType] (the group chat, a spreadsheet), through the
 * system's share sheet titled [chooserTitle]: payouts, the settle-up, a setup, a seat draw, a night
 * in History. [subject] goes along for apps that use one (mail). Needs no permission; a phone that
 * can't show the sheet shows nothing rather than crash.
 */
fun shareText(context: Context, text: String, chooserTitle: String, subject: String? = null, mimeType: String = PLAIN_TEXT) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(send, chooserTitle)) }
}

/**
 * Hands a small file the app wrote ([name], [text]) to any app that takes [mimeType], through the
 * system's share sheet, as an attachment (a preset to a friend in the group chat). The file is
 * written to the app's cache and shared through its FileProvider, read-only, to the app picked.
 */
fun shareFile(context: Context, name: String, mimeType: String, text: String, chooserTitle: String) {
    val dir = File(context.cacheDir, SHARED_DIR).apply { mkdirs() }
    // Only this file is offered; earlier ones go
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, safeFileName(name))
    runCatching { file.writeText(text, Charsets.UTF_8) }.onFailure { return }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, name)
        clipData = ClipData.newRawUri(name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(send, chooserTitle)) }
}

/** In the app's cache: res/xml/shared_files.xml offers this folder alone. */
private const val SHARED_DIR = "shared"

/** The FileProvider in core's manifest is "<package>.files". */
private const val AUTHORITY_SUFFIX = ".files"
