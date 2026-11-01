package com.huntercoles.pokerpayout.core.audio.custom

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import org.robolectric.shadows.util.DataSource

/** A picked cue sound file is measured when kept; one that can't be read is turned away without a crash. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidCueSoundFilesTest {

    private val app: Context = ApplicationProvider.getApplicationContext()
    private val files = AndroidCueSoundFiles(app)

    @Test
    fun `a kept file is measured, with its peak at the start and no end`() {
        ShadowMediaMetadataRetriever.addMetadata(
            DataSource.toDataSource(app, Uri.parse(URI)),
            MediaMetadataRetriever.METADATA_KEY_DURATION,
            "12345",
        )

        assertEquals(CustomCueSound(uri = URI, lengthMillis = 12_345), files.keep(URI))
    }

    @Test
    fun `a file with no length is not kept`() {
        assertNull(files.keep("content://picked/not-audio.txt"))
    }

    @Test
    fun `a file whose grant was taken back doesn't exist, and forgetting it is safe`() {
        assertFalse(files.exists("content://picked/revoked.mp3"))
        assertFalse(files.exists("not a uri at all"))
        files.forget("content://picked/revoked.mp3")
    }

    private companion object {
        const val URI = "content://picked/cue.mp3"
    }
}
