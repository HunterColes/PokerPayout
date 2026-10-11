package com.huntercoles.pokerpayout.core.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.backup.BackupCatalog
import com.huntercoles.pokerpayout.core.backup.BackupModule
import com.huntercoles.pokerpayout.core.backup.Backups
import com.huntercoles.pokerpayout.core.time.TimeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PP-113: the welcome on the setup page shows on a new install only, and once dismissed never again.
 * [FirstRun] tells a new install (nothing saved anywhere) from an update (anything saved by a version
 * before it, in any of the app's files) at the app's first start, once; every start after leaves the
 * answer alone, and no reset or restore brings a dismissed welcome back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirstRunTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun prefs(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    /** Every file the app keeps: the backed-up ones, and the one about this phone alone (where the welcome is). */
    private val allFiles = BackupCatalog.FILES + BackupCatalog.PHONE_FILES

    @Before
    fun wipe() {
        allFiles.forEach { prefs(it).edit().clear().commit() }
    }

    /** The app starting, as MainApplication does it: settled before anything else runs. */
    private fun start(): TimerPreferences = TimerPreferences(context).also { FirstRun(context, it).settle() }

    @Test
    fun `a new install shows the welcome`() {
        assertTrue(start().getShowWelcome())
    }

    @Test
    fun `an update from a version before it doesn't`() {
        // 1.4.6 left a game set up: ten players at $40
        prefs("tournament_prefs").edit().putInt("player_count", 10).putLong("buy_in_cents", 4_000L).commit()
        assertFalse(start().getShowWelcome())
    }

    @Test
    fun `anything in any of the app's files is an update`() {
        val showed = (allFiles - CurrencyPreferences.FILE).filter { file ->
            wipe()
            prefs(file).edit().putString("from_before", "x").commit()
            start().getShowWelcome()
        }
        assertEquals("the welcome showed with data from before in these files", emptyList<String>(), showed)
    }

    /** PP-114: the currency settles its own first start just before, so its file is no sign of an update. */
    @Test
    fun `a new install's own currency pick leaves the welcome up`() {
        prefs(CurrencyPreferences.FILE).edit().putString("currency", "euro").commit()
        assertTrue(start().getShowWelcome())
    }

    @Test
    fun `only the first start decides it, so what the first night saves leaves the welcome up`() {
        assertTrue(start().getShowWelcome())
        TournamentPreferences(context).setBuyInCents(4_000L)
        prefs("night_history").edit().putString("night_1", "{}").commit()
        assertTrue("still a new install's welcome", start().getShowWelcome())
    }

    @Test
    fun `an update stays an update, even after its data is cleared`() {
        prefs("bank_prefs").edit().putString("player_name_1", "Dana").commit()
        assertFalse(start().getShowWelcome())
        BackupCatalog.FILES.forEach { prefs(it).edit().clear().commit() }
        TimerPreferences(context).resetAllTimerData()
        assertFalse(start().getShowWelcome())
    }

    @Test
    fun `dismissed, it never comes back, not on a start, a reset or a restore`() {
        val timer = start()
        TournamentPreferences(context).setPlayerCount(8) // the first night, set up
        val backupWhileShowing = backups().export()
        timer.dismissWelcome()
        assertFalse(timer.getShowWelcome())

        assertFalse("after a restart", start().getShowWelcome())

        // New tournament… and every reset the app has
        TimerPreferences(context).resetTimer()
        TimerPreferences(context).resetAllTimerData()
        TournamentPreferences(context).resetAllTournamentData()
        assertFalse("after a reset", start().getShowWelcome())

        // A backup made while it still showed, restored in place of everything (it restarts the app)
        backups().replace(backups().open(backupWhileShowing))
        assertFalse("after a restore", start().getShowWelcome())
    }

    @Test
    fun `the welcome is about this phone, never in a backup and kept as it is by a restore`() {
        start().dismissWelcome()
        TournamentPreferences(context).setPlayerCount(8)
        val text = backups().export()
        assertFalse(text, "\"welcome\"" in text)

        // Another phone, new to the app, restoring this one's backup: its welcome is its own
        wipe()
        assertTrue("about this phone alone", BackupCatalog.PHONE_FILES.contains(PhonePrefs.FILE))
        assertTrue(start().getShowWelcome())
        backups().replace(backups().open(text))
        assertTrue("a restore leaves the phone's own welcome as it was", TimerPreferences(context).getShowWelcome())
    }

    /** The settings sections of a backup, as Hilt builds them. */
    private fun backups(): Backups = Backups(BackupModule.settingsSections(context, Clock), Clock, context)

    private object Clock : TimeSource {
        override fun elapsedRealtimeMillis(): Long = 50_000L

        override fun wallClockMillis(): Long = 1_791_460_800_000L

        override fun bootCount(): Int = 7
    }
}
