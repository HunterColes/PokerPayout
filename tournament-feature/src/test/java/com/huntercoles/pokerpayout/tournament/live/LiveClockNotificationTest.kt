package com.huntercoles.pokerpayout.tournament.live

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.time.TimeSource
import com.huntercoles.pokerpayout.core.utils.BlindLevel
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The live clock notification as Android gets it (PP-081): the words, the countdown the system runs
 * by itself, the buttons and where they go, and that it is public on the lock screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveClockNotificationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private object Now : TimeSource {
        override fun elapsedRealtimeMillis() = 5_000_000L
        override fun wallClockMillis() = 1_760_000_000_000L
        override fun bootCount() = 2
    }

    private val level4 = BlindLevel(level = 4, smallBlind = 300, bigBlind = 600, ante = 0, roundStartMinute = 60)
    private val level5 = BlindLevel(level = 5, smallBlind = 500, bigBlind = 1_000, ante = 1_000, roundStartMinute = 80)

    /** Level 4 running with 12:41 left, level 5 next. */
    private val running = LiveClockCard(
        showing = LiveClockCard.Showing.Level(level4, overtime = false),
        next = LiveClockCard.Next.Level(level5, overtime = false),
        endsAtRealtime = 5_000_000L + 761_000L,
        pausedLeftSeconds = null,
    )

    private val paused = running.copy(endsAtRealtime = null, pausedLeftSeconds = 761)

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    private fun Notification.actionTitles() = actions.map { it.title.toString() }

    @Test
    fun runningLevelSaysTheLevelTheBlindsAndWhatIsNext() {
        val notification = LiveClockNotification.build(context, running, Now)

        assertEquals("Level 4", notification.title())
        assertEquals("Blinds 300 / 600 · Next 500 / 1,000, ante 1,000", notification.text())
        assertEquals(LiveClockNotification.CHANNEL_ID, notification.channelId)
    }

    @Test
    fun theSystemCountsDownToTheEndOfTheLevel() {
        val notification = LiveClockNotification.build(context, running, Now)

        assertTrue(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        // 12:41 from now on the wall clock
        assertEquals(1_760_000_000_000L + 761_000L, notification.`when`)
    }

    @Test
    fun itIsOngoingPublicOnTheLockScreenAndQuiet() {
        val notification = LiveClockNotification.build(context, running, Now)

        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(Notification.VISIBILITY_PUBLIC, notification.visibility)
        assertEquals(Notification.CATEGORY_STOPWATCH, notification.category)
        assertNotNull(notification.contentIntent)
    }

    @Test
    fun runningItOffersPauseAndOpen() {
        val notification = LiveClockNotification.build(context, running, Now)

        assertEquals(listOf("Pause", "Open"), notification.actionTitles())
        val pause = shadowOf(notification.actions[0].actionIntent)
        assertTrue(pause.isBroadcast)
        assertEquals(ClockActionReceiver.ACTION_PAUSE, pause.savedIntent.action)
        assertEquals(ClockActionReceiver::class.java.name, pause.savedIntent.component?.className)
        assertTrue(shadowOf(notification.actions[1].actionIntent).isActivity)
    }

    @Test
    fun pausedItSaysSoWithTheTimeLeftAndOffersResume() {
        val notification = LiveClockNotification.build(context, paused, Now)

        assertEquals("Level 4 · paused", notification.title())
        assertEquals("12:41 left · Blinds 300 / 600 · Next 500 / 1,000, ante 1,000", notification.text())
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertEquals(listOf("Resume", "Open"), notification.actionTitles())
        val resume = shadowOf(notification.actions[0].actionIntent)
        assertEquals(ClockActionReceiver.ACTION_RESUME, resume.savedIntent.action)
    }

    @Test
    fun aBreakSaysWherePlayResumesAndItsNote() {
        val card = running.copy(
            showing = LiveClockCard.Showing.Break(number = 1, note = "Last rebuy"),
            next = LiveClockCard.Next.Level(level5, overtime = false),
        )
        val notification = LiveClockNotification.build(context, card, Now)

        assertEquals("Break 1", notification.title())
        assertEquals("Back at Level 5: 500 / 1,000, ante 1,000", notification.text())
        val big = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        assertEquals("Back at Level 5: 500 / 1,000, ante 1,000\nLast rebuy", big)
    }

    @Test
    fun aBreakNextAndTheFinalLevelAreNamed() {
        val beforeBreak = running.copy(next = LiveClockCard.Next.Break(minutes = 10))
        assertEquals("Blinds 300 / 600 · Next: 10-min break", LiveClockNotification.text(context, beforeBreak))

        val last = running.copy(
            showing = LiveClockCard.Showing.Level(level4.copy(level = 12), overtime = true),
            next = LiveClockCard.Next.None,
        )
        assertEquals("Level 12 · overtime", LiveClockNotification.title(context, last))
        assertEquals("Blinds 300 / 600 · Final level", LiveClockNotification.text(context, last))
    }

    @Test
    fun theChannelIsPublicAndMakesNoSoundOfItsOwn() {
        LiveClockNotification.createChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(LiveClockNotification.CHANNEL_ID)

        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertEquals(Notification.VISIBILITY_PUBLIC, channel.lockscreenVisibility)
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
    }

    @Test
    fun theButtonsBroadcastsMapBackToTheirCommands() {
        ClockCommand.entries.forEach { command ->
            val intent = ClockActionReceiver.intent(context, command)
            assertEquals(command, ClockActionReceiver.commandOf(intent.action))
        }
        assertNull(ClockActionReceiver.commandOf("android.intent.action.BOOT_COMPLETED"))
        assertNull(ClockActionReceiver.commandOf(null))
    }
}
