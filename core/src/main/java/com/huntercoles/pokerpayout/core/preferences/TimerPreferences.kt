package com.huntercoles.pokerpayout.core.preferences

import android.content.Context
import android.content.SharedPreferences
import com.huntercoles.pokerpayout.core.constants.TournamentDefaults
import com.huntercoles.pokerpayout.core.time.ClockAnchor
import com.huntercoles.pokerpayout.core.time.TimeSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tournament clock state and the clock-only settings (breaks, big-blind ante).
 *
 * The clock is stored as a [ClockAnchor], written only when it starts, pauses, jumps, finishes or
 * resets; a running clock's time is derived from the anchor, so nothing is written per tick.
 */
@Singleton
class TimerPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("timer_prefs", Context.MODE_PRIVATE)

    /** PP-113: the welcome is about this phone, so its state lives in [PhonePrefs.FILE], which no backup takes. */
    private val phone: SharedPreferences = PhonePrefs.open(context)

    private val _timerRunning = MutableStateFlow(getTimerRunning())
    val timerRunning: Flow<Boolean> = _timerRunning.asStateFlow()

    init {
        // PP-137: whether to ask for notifications is the system's answer now (it is per phone and
        // never restored from another one), so the flag older versions kept here goes, once.
        if (prefs.contains(RETIRED_NOTIFICATIONS_ASKED_KEY)) {
            prefs.edit().remove(RETIRED_NOTIFICATIONS_ASKED_KEY).apply()
        }
    }

    // ------------------------------------------------------------------ clock

    /** Mirrors the clock's running flag for observers (Bank locks while the clock runs). */
    fun setTimerRunning(running: Boolean) {
        prefs.edit().putBoolean(TIMER_RUNNING_KEY, running).apply()
        _timerRunning.value = running
    }

    fun getTimerRunning(): Boolean = prefs.getBoolean(TIMER_RUNNING_KEY, false)

    fun saveClock(anchor: ClockAnchor) {
        prefs.edit()
            .putLong(CLOCK_ELAPSED_MS_KEY, anchor.elapsedMillis)
            .putBoolean(TIMER_RUNNING_KEY, anchor.running)
            .putLong(CLOCK_REALTIME_MS_KEY, anchor.realtimeMillis)
            .putLong(CLOCK_WALL_MS_KEY, anchor.wallMillis)
            .putInt(CLOCK_BOOT_COUNT_KEY, anchor.bootCount)
            .removeLegacyClock()
            .apply()
        _timerRunning.value = anchor.running
    }

    /** The saved clock, or null if none was saved in the current format. */
    fun getClock(): ClockAnchor? {
        if (!prefs.contains(CLOCK_ELAPSED_MS_KEY)) return null
        return ClockAnchor(
            elapsedMillis = prefs.getLong(CLOCK_ELAPSED_MS_KEY, 0L),
            running = getTimerRunning(),
            realtimeMillis = prefs.getLong(CLOCK_REALTIME_MS_KEY, 0L),
            wallMillis = prefs.getLong(CLOCK_WALL_MS_KEY, 0L),
            bootCount = prefs.getInt(CLOCK_BOOT_COUNT_KEY, -1)
        )
    }

    /**
     * A clock saved by v1.1.x (remaining or overtime seconds plus a wall-clock timestamp), or null.
     * [LegacyClock.elapsedSeconds] is play time at [LegacyClock.savedAtWallMillis].
     */
    fun getLegacyClock(): LegacyClock? {
        if (prefs.contains(CLOCK_ELAPSED_MS_KEY) || !prefs.contains(LEGACY_CURRENT_TIME_SECONDS_KEY)) return null
        val durationSeconds = getGameDurationMinutes() * SECONDS_PER_MINUTE
        val seconds = prefs.getInt(LEGACY_CURRENT_TIME_SECONDS_KEY, durationSeconds)
        val elapsed = when (prefs.getString(LEGACY_TIMER_DIRECTION_KEY, "COUNTDOWN")) {
            "COUNTUP" -> durationSeconds + seconds
            else -> durationSeconds - seconds
        }
        return LegacyClock(
            elapsedSeconds = elapsed.coerceAtLeast(0),
            running = getTimerRunning() && !getIsFinished(),
            savedAtWallMillis = prefs.getLong(LEGACY_LAST_UPDATE_TIME_KEY, 0L)
        )
    }

    data class LegacyClock(val elapsedSeconds: Int, val running: Boolean, val savedAtWallMillis: Long)

    /**
     * Emits whenever anything here is saved: the clock starting, pausing, jumping, finishing or
     * resetting, or a clock setting changing (PP-081: the live clock notification follows the saved
     * clock). Never per tick, since a running clock writes nothing.
     */
    fun observeChanges(): Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // ------------------------------------------------- the welcome (PP-113)

    /**
     * PP-113: true while the setup page shows its one-line welcome. Only a new install shows it
     * ([settleWelcome]), until it is dismissed ([dismissWelcome]).
     */
    fun getShowWelcome(): Boolean = phone.getString(WELCOME_KEY, null) == WELCOME_NEW

    /** The welcome is done for good on this phone: no reset or restore brings it back. */
    fun dismissWelcome() {
        if (phone.getString(WELCOME_KEY, null) != WELCOME_DONE) phone.edit().putString(WELCOME_KEY, WELCOME_DONE).apply()
    }

    /**
     * Once ever, at the app's first start with the welcome (PP-113): [isNewInstall] says whether the
     * phone holds no data from before, and only then does the welcome show. Every later start leaves
     * the answer as it is, so data saved since never changes it.
     */
    fun settleWelcome(isNewInstall: () -> Boolean) {
        if (phone.contains(WELCOME_KEY)) return
        phone.edit().putString(WELCOME_KEY, if (isNewInstall()) WELCOME_NEW else WELCOME_DONE).apply()
    }

    fun setGameDurationMinutes(minutes: Int) {
        prefs.edit().putInt(GAME_DURATION_MINUTES_KEY, minutes).apply()
    }

    fun getGameDurationMinutes(): Int = prefs.getInt(GAME_DURATION_MINUTES_KEY, DEFAULT_DURATION_MINUTES)

    fun setIsFinished(finished: Boolean) {
        prefs.edit().putBoolean(IS_FINISHED_KEY, finished).apply()
    }

    fun getIsFinished(): Boolean = prefs.getBoolean(IS_FINISHED_KEY, false)

    fun setHasTimerStarted(hasStarted: Boolean) {
        prefs.edit().putBoolean(HAS_TIMER_STARTED_KEY, hasStarted).apply()
    }

    fun getHasTimerStarted(): Boolean = prefs.getBoolean(HAS_TIMER_STARTED_KEY, false)

    // ------------------------------------------------- blind setup frozen at start

    fun getSmallestChipAtStart(): Int = prefs.getInt(SMALLEST_CHIP_AT_START_KEY, TournamentDefaults.SMALLEST_CHIP)

    fun setSmallestChipAtStart(value: Int) {
        prefs.edit().putInt(SMALLEST_CHIP_AT_START_KEY, value).apply()
    }

    fun getStartingChipsAtStart(): Int = prefs.getInt(STARTING_CHIPS_AT_START_KEY, TournamentDefaults.STARTING_CHIPS)

    fun setStartingChipsAtStart(value: Int) {
        prefs.edit().putInt(STARTING_CHIPS_AT_START_KEY, value).apply()
    }

    fun getRoundLengthAtStart(): Int = prefs.getInt(ROUND_LENGTH_AT_START_KEY, TournamentDefaults.ROUND_LENGTH_MINUTES)

    fun setRoundLengthAtStart(minutes: Int) {
        prefs.edit().putInt(ROUND_LENGTH_AT_START_KEY, minutes).apply()
    }

    // ------------------------------------------------- breaks and antes (PP-026)

    /** Levels between breaks; 0 = no breaks. */
    fun getBreakEveryLevels(): Int = prefs.getInt(BREAK_EVERY_LEVELS_KEY, DEFAULT_BREAK_EVERY_LEVELS)

    fun setBreakEveryLevels(levels: Int) {
        prefs.edit().putInt(BREAK_EVERY_LEVELS_KEY, levels).apply()
    }

    fun getBreakLengthMinutes(): Int = prefs.getInt(BREAK_LENGTH_MINUTES_KEY, DEFAULT_BREAK_LENGTH_MINUTES)

    fun setBreakLengthMinutes(minutes: Int) {
        prefs.edit().putInt(BREAK_LENGTH_MINUTES_KEY, minutes).apply()
    }

    /** Shown on the clock during every break, e.g. "Last rebuy". */
    fun getBreakMessage(): String = prefs.getString(BREAK_MESSAGE_KEY, "") ?: ""

    fun setBreakMessage(message: String) {
        prefs.edit().putString(BREAK_MESSAGE_KEY, message).apply()
    }

    /** 1-based level from which the big blind antes one big blind; 0 = no ante. */
    fun getBigBlindAnteFromLevel(): Int = prefs.getInt(BIG_BLIND_ANTE_FROM_LEVEL_KEY, 0)

    fun setBigBlindAnteFromLevel(level: Int) {
        prefs.edit().putInt(BIG_BLIND_ANTE_FROM_LEVEL_KEY, level).apply()
    }

    // ------------------------------------------------- color-up done, per break (S4)

    /**
     * The breaks whose color-up the host has marked done, by the level each break follows (1-based).
     * Keyed by that level, not the break's number, so changing the break interval mid-game can't move
     * a tick onto a different break. Cleared with the clock.
     */
    fun getColorUpDoneAfterLevels(): Set<Int> =
        prefs.getString(COLOR_UP_DONE_KEY, null)
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.toSet()
            .orEmpty()

    fun setColorUpDone(afterLevel: Int, done: Boolean) {
        val current = getColorUpDoneAfterLevels()
        val updated = if (done) current + afterLevel else current - afterLevel
        if (updated == current) return
        prefs.edit().putString(COLOR_UP_DONE_KEY, updated.sorted().joinToString(",")).apply()
    }

    // ------------------------------------------------------------------ reset

    /** Back to a fresh clock; keeps the duration, breaks and ante settings. */
    fun resetTimer() {
        prefs.edit()
            .remove(COLOR_UP_DONE_KEY)
            .putLong(CLOCK_ELAPSED_MS_KEY, 0L)
            .putBoolean(TIMER_RUNNING_KEY, false)
            .putLong(CLOCK_REALTIME_MS_KEY, 0L)
            .putLong(CLOCK_WALL_MS_KEY, 0L)
            .putInt(CLOCK_BOOT_COUNT_KEY, -1)
            .putBoolean(IS_FINISHED_KEY, false)
            .putBoolean(HAS_TIMER_STARTED_KEY, false)
            .remove(SMALLEST_CHIP_AT_START_KEY)
            .remove(STARTING_CHIPS_AT_START_KEY)
            .remove(ROUND_LENGTH_AT_START_KEY)
            .removeLegacyClock()
            .apply()
        _timerRunning.value = false
    }

    /** Reset all timer data, including duration, breaks and ante, to default values. */
    fun resetAllTimerData() {
        resetTimer()
        prefs.edit()
            .putInt(GAME_DURATION_MINUTES_KEY, DEFAULT_DURATION_MINUTES)
            .putInt(BREAK_EVERY_LEVELS_KEY, DEFAULT_BREAK_EVERY_LEVELS)
            .putInt(BREAK_LENGTH_MINUTES_KEY, DEFAULT_BREAK_LENGTH_MINUTES)
            .putString(BREAK_MESSAGE_KEY, "")
            .putInt(BIG_BLIND_ANTE_FROM_LEVEL_KEY, 0)
            .apply()
    }

    /** Check if timer settings are in default state. */
    fun isInDefaultState(): Boolean {
        return !getTimerRunning() &&
            (getClock()?.elapsedMillis ?: 0L) == 0L &&
            getLegacyClock()?.elapsedSeconds.let { it == null || it == 0 } &&
            getGameDurationMinutes() == DEFAULT_DURATION_MINUTES &&
            !getIsFinished() &&
            !getHasTimerStarted() &&
            getBreakEveryLevels() == DEFAULT_BREAK_EVERY_LEVELS &&
            getBreakLengthMinutes() == DEFAULT_BREAK_LENGTH_MINUTES &&
            getBreakMessage().isEmpty() &&
            getBigBlindAnteFromLevel() == 0
    }

    private fun SharedPreferences.Editor.removeLegacyClock() = this
        .remove(LEGACY_CURRENT_TIME_SECONDS_KEY)
        .remove(LEGACY_TIMER_DIRECTION_KEY)
        .remove(LEGACY_LAST_UPDATE_TIME_KEY)
        .remove(LEGACY_OVERTIME_LEVELS_REVEALED_KEY)

    companion object {
        const val DEFAULT_DURATION_MINUTES = TournamentDefaults.GAME_DURATION_HOURS * 60
        const val DEFAULT_BREAK_EVERY_LEVELS = 0
        const val DEFAULT_BREAK_LENGTH_MINUTES = 10
        private const val SECONDS_PER_MINUTE = 60

        /**
         * Keys about this phone, not the game, that the in-app backup leaves out and a restore leaves
         * alone: the running clock's monotonic reading and boot (meaningless on another phone, or after
         * a restart), and the notifications flag older versions kept (retired, PP-137).
         *
         * Android's own backup takes this file whole, readings and all (it can only leave out whole
         * files, and the clock saves its anchor in one write). On another phone the boot differs, so a
         * clock restored running carries on by wall-clock time, as after a restart (ClockAnchor).
         */
        val PHONE_ONLY_KEYS: Set<String> =
            setOf(CLOCK_REALTIME_MS_KEY, CLOCK_WALL_MS_KEY, CLOCK_BOOT_COUNT_KEY, RETIRED_NOTIFICATIONS_ASKED_KEY)

        /**
         * This file's saved [values] as a backup keeps them: a running clock is saved paused where it
         * stands at [time], since what it runs from ([PHONE_ONLY_KEYS]) isn't saved. Restored, the
         * game waits on Start at the time it had.
         */
        fun pausedForBackup(values: Map<String, Any?>, time: TimeSource): Map<String, Any?> {
            val elapsed = values[CLOCK_ELAPSED_MS_KEY] as? Long
            if (values[TIMER_RUNNING_KEY] != true || elapsed == null) return values
            val anchor = ClockAnchor(
                elapsedMillis = elapsed,
                running = true,
                realtimeMillis = values[CLOCK_REALTIME_MS_KEY] as? Long ?: 0L,
                wallMillis = values[CLOCK_WALL_MS_KEY] as? Long ?: 0L,
                bootCount = values[CLOCK_BOOT_COUNT_KEY] as? Int ?: -1,
            )
            return values + mapOf(CLOCK_ELAPSED_MS_KEY to anchor.elapsedAt(time), TIMER_RUNNING_KEY to false)
        }

        private const val TIMER_RUNNING_KEY = "timer_running"
        private const val GAME_DURATION_MINUTES_KEY = "game_duration_minutes"
        private const val IS_FINISHED_KEY = "is_finished"
        private const val HAS_TIMER_STARTED_KEY = "has_timer_started"
        private const val CLOCK_ELAPSED_MS_KEY = "clock_elapsed_ms"
        private const val CLOCK_REALTIME_MS_KEY = "clock_anchor_realtime_ms"
        private const val CLOCK_WALL_MS_KEY = "clock_anchor_wall_ms"
        private const val CLOCK_BOOT_COUNT_KEY = "clock_anchor_boot_count"
        private const val SMALLEST_CHIP_AT_START_KEY = "smallest_chip_at_start"
        private const val STARTING_CHIPS_AT_START_KEY = "starting_chips_at_start"
        private const val ROUND_LENGTH_AT_START_KEY = "round_length_at_start"
        private const val BREAK_EVERY_LEVELS_KEY = "break_every_levels"
        private const val BREAK_LENGTH_MINUTES_KEY = "break_length_minutes"
        private const val BREAK_MESSAGE_KEY = "break_message"
        private const val BIG_BLIND_ANTE_FROM_LEVEL_KEY = "big_blind_ante_from_level"
        private const val COLOR_UP_DONE_KEY = "color_up_done_after_levels"

        // PP-081's "asked for notifications" flag, retired by PP-137: removed once, never read
        private const val RETIRED_NOTIFICATIONS_ASKED_KEY = "notifications_permission_asked"

        // PP-113, in PhonePrefs.FILE: absent until the first start settles it; no reset clears it
        private const val WELCOME_KEY = "welcome"
        private const val WELCOME_NEW = "new"
        private const val WELCOME_DONE = "done"

        // v1.1.x clock, read once and migrated to the anchor
        private const val LEGACY_CURRENT_TIME_SECONDS_KEY = "current_time_seconds"
        private const val LEGACY_TIMER_DIRECTION_KEY = "timer_direction"
        private const val LEGACY_LAST_UPDATE_TIME_KEY = "last_update_time"
        private const val LEGACY_OVERTIME_LEVELS_REVEALED_KEY = "overtime_levels_revealed"
    }
}
