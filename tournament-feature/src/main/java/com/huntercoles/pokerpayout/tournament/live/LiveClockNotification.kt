package com.huntercoles.pokerpayout.tournament.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.time.TimeSource
import com.huntercoles.pokerpayout.core.utils.BlindLevel
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockCommand
import com.huntercoles.pokerpayout.tournament.presentation.ClockFormat
import java.text.NumberFormat
import java.util.Locale

/**
 * The live clock notification (PP-081): its channel, and how a [LiveClockCard] reads in the shade and
 * on the lock screen. "Level 4", the time left counting down by itself (the system animates the
 * chronometer, so the app posts nothing per second), "Blinds 300 / 600 · Next 500 / 1,000", and
 * Pause (or Resume) and Open. Public on the lock screen: blinds and a countdown, nothing private.
 */
object LiveClockNotification {
    const val ID = 81
    const val CHANNEL_ID = "live_clock"

    private const val PAUSE_REQUEST = 1
    private const val RESUME_REQUEST = 2
    private const val OPEN_REQUEST = 3

    /**
     * The channel: default importance, so it shows in the status bar and on the lock screen, but
     * without a sound or a buzz of its own (the clock's cues are the app's). Safe to call again.
     */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.live_clock_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.live_clock_channel_description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** The notification for [card], now as [time] tells it. */
    fun build(context: Context, card: LiveClockCard, time: TimeSource): Notification {
        val text = text(context, card)
        val note = (card.showing as? LiveClockCard.Showing.Break)?.note?.takeIf { it.isNotBlank() }
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_live_clock)
            .setContentTitle(title(context, card))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(listOfNotNull(text, note).joinToString("\n")))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setColor(PokerColors.PokerGold.toArgb())
            .setContentIntent(openIntent(context))
            .addAction(if (card.running) action(context, ClockCommand.PAUSE) else action(context, ClockCommand.RESUME))
            .addAction(
                Notification.Action.Builder(icon(context), context.getString(R.string.live_clock_open), openIntent(context))
                    .build(),
            )
        val endsAt = card.endsAtRealtime
        if (endsAt != null) {
            // The system counts down to the end of the level by itself: no update every second
            builder.setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setShowWhen(true)
                .setWhen(time.wallClockMillis() + (endsAt - time.elapsedRealtimeMillis()))
        } else {
            builder.setShowWhen(false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .setCategory(Notification.CATEGORY_STOPWATCH)
        }
        return builder.build()
    }

    /** Shown for a moment when the service must go foreground with no clock to show (it then stops). */
    fun placeholder(context: Context): Notification =
        Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_live_clock)
            .setContentTitle(context.getString(R.string.live_clock_channel))
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .build()

    /** "Level 4", "Level 10 · overtime", "Break 1"; "Level 4 · paused". */
    fun title(context: Context, card: LiveClockCard): String {
        val what = when (val showing = card.showing) {
            is LiveClockCard.Showing.Level -> context.getString(
                if (showing.overtime) R.string.live_clock_level_overtime else R.string.live_clock_level,
                showing.blinds.level,
            )
            is LiveClockCard.Showing.Break -> context.getString(R.string.live_clock_break, showing.number)
        }
        return if (card.running) what else context.getString(R.string.live_clock_paused, what)
    }

    /**
     * "Blinds 300 / 600 · Next 500 / 1,000"; "Back at Level 5: 300 / 600" on a break; paused, it
     * starts with what's left: "12:41 left · Blinds 300 / 600 · Next 500 / 1,000".
     */
    fun text(context: Context, card: LiveClockCard): String {
        val chips = NumberFormat.getIntegerInstance(Locale.getDefault())
        val left = card.pausedLeftSeconds?.let { context.getString(R.string.live_clock_left, ClockFormat.clock(it)) }
        val parts = when (val showing = card.showing) {
            is LiveClockCard.Showing.Level -> listOf(
                context.getString(R.string.live_clock_blinds, blinds(context, showing.blinds, chips)),
                nextText(context, card.next, chips),
            )
            is LiveClockCard.Showing.Break -> listOf(
                (card.next as? LiveClockCard.Next.Level)?.let {
                    context.getString(R.string.live_clock_back_at, it.blinds.level, blinds(context, it.blinds, chips))
                } ?: context.getString(R.string.live_clock_final),
            )
        }
        return (listOfNotNull(left) + parts).joinToString(context.getString(R.string.strip_separator))
    }

    private fun nextText(context: Context, next: LiveClockCard.Next, chips: NumberFormat): String = when (next) {
        is LiveClockCard.Next.Level -> context.getString(R.string.live_clock_next, blinds(context, next.blinds, chips))
        is LiveClockCard.Next.Break -> context.getString(R.string.live_clock_next_break, next.minutes)
        LiveClockCard.Next.None -> context.getString(R.string.live_clock_final)
    }

    /** "300 / 600", or "300 / 600, ante 600". */
    private fun blinds(context: Context, level: BlindLevel, chips: NumberFormat): String {
        val pair = "${chips.format(level.smallBlind)} / ${chips.format(level.bigBlind)}"
        return if (level.ante > 0) context.getString(R.string.live_clock_with_ante, pair, chips.format(level.ante)) else pair
    }

    private fun action(context: Context, command: ClockCommand): Notification.Action {
        val (label, request) = when (command) {
            ClockCommand.PAUSE -> R.string.live_clock_pause to PAUSE_REQUEST
            ClockCommand.RESUME -> R.string.live_clock_resume to RESUME_REQUEST
        }
        val intent = PendingIntent.getBroadcast(
            context,
            request,
            ClockActionReceiver.intent(context, command),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(icon(context), context.getString(label), intent).build()
    }

    /** Brings the app's task to the front, as the launcher icon does. */
    private fun openIntent(context: Context): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return PendingIntent.getActivity(
            context,
            OPEN_REQUEST,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun icon(context: Context): Icon = Icon.createWithResource(context, R.drawable.ic_live_clock)
}
