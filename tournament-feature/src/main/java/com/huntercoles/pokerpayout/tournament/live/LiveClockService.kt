package com.huntercoles.pokerpayout.tournament.live

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.time.TimeSource
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockCues
import com.huntercoles.pokerpayout.tournament.domain.clock.SavedClock
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * The live clock notification's foreground service (PP-081). It runs only while the clock runs and
 * the app is out of sight ([LiveClockController] starts and stops it), and does three things:
 *
 * - shows the notification, re-posted only when it changes ([LiveClockDriver]);
 * - keeps the process in the foreground, so Android doesn't freeze or kill it, and the clock's cues
 *   (chime, vibration) keep sounding on time, from here or from the clock's own screen; the music
 *   plays on too ([TournamentMusicLink]);
 * - holds a partial wake lock while the clock runs, renewed at each look, so the CPU is awake when
 *   a cue is due even with the screen off.
 *
 * The clock itself stays in the saved anchor: this only reads it. A special-use foreground service,
 * since none of the standard types fits a game clock; nothing here needs the network.
 */
@AndroidEntryPoint
class LiveClockService : Service() {
    @Inject lateinit var savedClock: SavedClock

    @Inject lateinit var timeSource: TimeSource

    @Inject lateinit var cues: ClockCues

    @Inject lateinit var timerPreferences: TimerPreferences

    @Inject lateinit var controller: LiveClockController

    @Inject lateinit var musicLink: TournamentMusicLink

    private val scope = MainScope()
    private var driver: LiveClockDriver? = null
    private var driving: Job? = null
    private var observing: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        controller.serviceRunning = true
        // A process the system started for this service alone has no screen yet to start the music's link
        musicLink.start()
        LiveClockNotification.createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        if (intent?.action == ACTION_HIDE) {
            stop()
            START_NOT_STICKY
        } else {
            show()
            START_STICKY
        }

    /** Foreground at once (a promise made by startForegroundService), then the driver takes over. */
    private fun show() {
        if (driving?.isActive == true) {
            driver?.poke()
            return
        }
        val first = savedClock.read()?.let { clock ->
            LiveClockCard.of(clock, clock.anchor.elapsedAt(timeSource), timeSource.elapsedRealtimeMillis())
        }
        val notification = first?.let { LiveClockNotification.build(this, it, timeSource) }
            ?: LiveClockNotification.placeholder(this)
        val foreground = runCatching { startForeground(LiveClockNotification.ID, notification) }
            .onFailure { Timber.w(it, "Live clock: not allowed in the foreground") }
            .isSuccess
        if (!foreground || first == null || controller.appVisible) {
            stop()
        } else {
            drive()
        }
    }

    private fun drive() {
        val manager = getSystemService(NotificationManager::class.java)
        val newDriver = LiveClockDriver(
            savedClock = savedClock::read,
            time = timeSource,
            cues = cues,
            post = { card -> manager?.notify(LiveClockNotification.ID, LiveClockNotification.build(this, card, timeSource)) },
            keepAwake = ::keepAwake,
        )
        driver = newDriver
        // Pause or Resume from the notification, the game finished or reset: look again at once
        observing = timerPreferences.observeChanges().onEach { newDriver.poke() }.launchIn(scope)
        driving = scope.launch {
            newDriver.run()
            stop()
        }
    }

    /** Holds the CPU awake for [millis], or lets it sleep (null). */
    private fun keepAwake(millis: Long?) {
        val lock = wakeLock ?: getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            ?.apply { setReferenceCounted(false) }
            ?.also { wakeLock = it }
        when {
            lock == null -> Unit
            millis != null -> lock.acquire(millis)
            lock.isHeld -> lock.release()
        }
    }

    private fun stop() {
        observing?.cancel()
        observing = null
        driving?.cancel()
        driving = null
        keepAwake(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        keepAwake(null)
        controller.serviceRunning = false
        musicLink.liveClockGone()
        super.onDestroy()
    }

    companion object {
        const val ACTION_SHOW = "com.huntercoles.pokerpayout.action.SHOW_LIVE_CLOCK"
        const val ACTION_HIDE = "com.huntercoles.pokerpayout.action.HIDE_LIVE_CLOCK"
        private const val WAKE_LOCK_TAG = "PokerPayout:LiveClock"

        fun intent(context: Context, action: String): Intent =
            Intent(context, LiveClockService::class.java).setAction(action)
    }
}
