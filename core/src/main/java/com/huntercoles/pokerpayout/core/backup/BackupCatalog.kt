package com.huntercoles.pokerpayout.core.backup

import androidx.annotation.StringRes
import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.preferences.CurrencyPreferences
import com.huntercoles.pokerpayout.core.preferences.MusicPreferences
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences

/**
 * Every SharedPreferences file the app keeps, and what a backup does with it. `BackupCoverageTest`
 * finds every file the sources open and fails until it is listed here, so new data can't be left
 * out of backups by accident. Android's own Auto Backup (res/xml/data_extraction_rules.xml) takes
 * every one of them as well.
 */
object BackupCatalog {

    /** Settings files, saved whole (every key, with its type) by their group's section. */
    val SETTINGS: List<SettingsFile> = listOf(
        // The tournament setup and tonight's game: the Bank (players, purchases, the settle-up) and the clock
        SettingsFile("tournament_prefs", SettingsGroup.GAME),
        SettingsFile("timer_prefs", SettingsGroup.GAME, phoneOnly = TimerPreferences.PHONE_ONLY_KEYS),
        SettingsFile("bank_prefs", SettingsGroup.GAME),
        // The currency amounts show in (PP-114): a game's amounts read as they were meant
        SettingsFile(CurrencyPreferences.FILE, SettingsGroup.GAME),
        SettingsFile("chip_calculator_prefs", SettingsGroup.CHIP_SET),
        SettingsFile("audio_prefs", SettingsGroup.SOUND),
        // The music's settings; its playlist names this phone's files, so it stays with the phone
        SettingsFile(MusicPreferences.FILE, SettingsGroup.SOUND, phoneOnly = MusicPreferences.PHONE_ONLY_KEYS),
        SettingsFile("odds_calculator_prefs", SettingsGroup.TOOLS),
        SettingsFile("seat_draw_prefs", SettingsGroup.TOOLS),
        // The shot clock (its times and the time-bank cards played), dealer's choice (the wheel,
        // house games, the last pick) and the equity quiz (its settings and the score)
        SettingsFile("shot_clock_prefs", SettingsGroup.TOOLS),
        SettingsFile("dealers_choice_prefs", SettingsGroup.TOOLS),
        SettingsFile("equity_quiz_prefs", SettingsGroup.TOOLS),
    )

    /** Files saved item by item by a section of their own, so a backup can merge them: file to section key. */
    val COLLECTIONS: Map<String, String> = mapOf(
        "tournament_presets" to "presets",
        "night_history" to HistoryBackup.KEY,
    )

    /** Every file a backup saves. */
    val FILES: Set<String> get() = SETTINGS.map { it.name }.toSet() + COLLECTIONS.keys

    fun filesOf(group: SettingsGroup): List<SettingsFile> = SETTINGS.filter { it.group == group }
}

/**
 * A settings file in a backup.
 *
 * @property phoneOnly keys about this phone rather than the game (a monotonic clock reading, a
 *   permission already asked for): never written to a backup, and kept as they are on a restore.
 */
data class SettingsFile(val name: String, val group: SettingsGroup, val phoneOnly: Set<String> = emptySet())

/** The settings sections of a backup, each a few files restored together. Never rename a [key]. */
enum class SettingsGroup(val key: String, val order: Int, @StringRes val line: Int) {
    CHIP_SET(key = "chipSet", order = 20, line = R.string.backup_line_chip_set),
    GAME(key = "game", order = 30, line = R.string.backup_line_game),
    SOUND(key = "sound", order = 40, line = R.string.backup_line_sound),
    TOOLS(key = "tools", order = 50, line = R.string.backup_line_tools),
}
