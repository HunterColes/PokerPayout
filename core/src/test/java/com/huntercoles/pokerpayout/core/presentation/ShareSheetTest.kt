package com.huntercoles.pokerpayout.core.presentation

import android.app.Activity
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The one share of text (payouts, the settle-up, a setup, a seat draw, a night): the system's share
 * sheet with the text, its type and, when there is one, a subject.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareSheetTest {

    /** The screen sharing, as a composable's LocalContext is. */
    private val screen: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    @Test
    fun `text goes to the share sheet as plain text, under the sheet's title`() {
        shareText(screen, "Poker night payouts", chooserTitle = "Share payouts")

        val chooser = shadowOf(screen).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals("Share payouts", chooser.getCharSequenceExtra(Intent.EXTRA_TITLE))
        val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals(PLAIN_TEXT, send.type)
        assertEquals("Poker night payouts", send.getStringExtra(Intent.EXTRA_TEXT))
        assertFalse(send.hasExtra(Intent.EXTRA_SUBJECT))
    }

    @Test
    fun `a subject and another type go along when given`() {
        shareText(screen, "date,structure\r\n", chooserTitle = "Export", subject = "Poker nights", mimeType = "text/csv")

        val send = shadowOf(screen).nextStartedActivity.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals("text/csv", send.type)
        assertEquals("Poker nights", send.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("date,structure\r\n", send.getStringExtra(Intent.EXTRA_TEXT))
    }
}
