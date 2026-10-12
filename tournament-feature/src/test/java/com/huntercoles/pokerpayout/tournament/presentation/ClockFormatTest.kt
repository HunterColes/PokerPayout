package com.huntercoles.pokerpayout.tournament.presentation

import org.junit.jupiter.api.Test
import java.util.Locale
import kotlin.test.assertEquals

/**
 * The clock's time, as the hero, the table view and the live clock notification show it. The digits
 * are Barlow Condensed's and are fitted by their width, so they are Western digits whatever the
 * phone's language: a Persian or Arabic phone used to get its own digits here, drawn in another font.
 */
class ClockFormatTest {

    @Test
    fun `minutes and seconds under an hour, hours from an hour`() {
        assertEquals("0:00", ClockFormat.clock(0))
        assertEquals("0:59", ClockFormat.clock(59))
        assertEquals("12:41", ClockFormat.clock(12 * 60 + 41))
        assertEquals("59:59", ClockFormat.clock(3_599))
        assertEquals("1:00:00", ClockFormat.clock(3_600))
        assertEquals("1:02:03", ClockFormat.clock(3_723))
    }

    @Test
    fun `time past the end shows as none left`() {
        assertEquals("0:00", ClockFormat.clock(-5))
    }

    @Test
    fun `Western digits on a phone set to a language with its own digits`() {
        val before = Locale.getDefault()
        try {
            listOf(Locale.forLanguageTag("fa-IR"), Locale.forLanguageTag("ar-EG"), Locale.forLanguageTag("hi-IN-u-nu-deva"))
                .forEach { phone ->
                    Locale.setDefault(phone)
                    assertEquals("12:41", ClockFormat.clock(12 * 60 + 41), phone.toLanguageTag())
                    assertEquals("1:02:03", ClockFormat.clock(3_723), phone.toLanguageTag())
                }
        } finally {
            Locale.setDefault(before)
        }
    }
}
