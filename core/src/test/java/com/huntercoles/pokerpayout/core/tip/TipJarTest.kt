package com.huntercoles.pokerpayout.core.tip

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.backup.BackupCatalog
import com.huntercoles.pokerpayout.core.backup.BackupModule
import com.huntercoles.pokerpayout.core.backup.Backups
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.time.TimeSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Where the "Tip the dealer?" card stands, on disk (PP-112): it survives process death, a running
 * clock hides it, the first run is told apart from later ones, and the in-app backup leaves the file
 * out, so a restore can never bring back a card the host said no to.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TipJarTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var timer: TimerPreferences

    private fun prefs() = context.getSharedPreferences(TipJar.FILE, Context.MODE_PRIVATE)

    /** A new process: everything read again from what was saved. */
    private fun restart(): TipJar {
        timer = TimerPreferences(context)
        return TipJar(context, timer)
    }

    @Before
    fun wipe() {
        (BackupCatalog.FILES + TipJar.FILE).forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    /** A later run of the app, the first one having been and gone. */
    private fun laterRun(): TipJar {
        restart()
        return restart()
    }

    @Test
    fun theFirstRunIsToldApartFromLaterOnes() {
        assertTrue(restart().firstRun)
        assertFalse(restart().firstRun)
        assertFalse(restart().firstRun)
    }

    @Test
    fun aCardOnTheFirstRunWaitsForALaterOne() {
        val first = restart()
        (1L..3L).forEach(first::nightSaved)
        assertNull(first.cardNightNow())
        val later = restart()
        later.nightSaved(4L)
        assertEquals(4L, later.cardNightNow())
    }

    @Test
    fun theCardAndItsCountersSurviveProcessDeath() {
        val jar = laterRun()
        (11L..13L).forEach(jar::nightSaved)
        assertEquals(13L, jar.cardNightNow())

        val after = restart()
        assertEquals(13L, after.cardNightNow())
        assertEquals(TipAsk(nightsSaved = 3, asksShown = 1, nextAskAt = 6, cardNight = 13L), after.ask.value)

        after.notNow()
        assertEquals(TipAsk(nightsSaved = 3, asksShown = 1, nextAskAt = 6), restart().ask.value)
    }

    @Test
    fun dontAskAgainHoldsAfterProcessDeath() {
        val jar = laterRun()
        (1L..3L).forEach(jar::nightSaved)
        jar.stopAsking()
        val after = restart()
        assertTrue(after.ask.value.stopped)
        (4L..20L).forEach(after::nightSaved)
        assertNull(after.cardNightNow())
        assertEquals(1, after.ask.value.asksShown)
    }

    @Test
    fun aRunningClockHidesTheCardAndHoldsANewOneBack() = runBlocking {
        val jar = laterRun()
        timer.setTimerRunning(true)
        (1L..3L).forEach(jar::nightSaved)
        assertNull(jar.cardNightNow())
        assertEquals(0, jar.ask.value.asksShown)

        timer.setTimerRunning(false)
        jar.nightSaved(4L)
        assertEquals(4L, jar.cardNight.first())
        timer.setTimerRunning(true) // the host starts the clock again with the card up
        assertNull(jar.cardNight.first())
        assertNull(jar.cardNightNow())
        timer.setTimerRunning(false)
        assertEquals(4L, jar.cardNightNow())
    }

    @Test
    fun everyKeyItWritesIsAKnownKey() {
        val jar = laterRun()
        (1L..3L).forEach(jar::nightSaved)
        assertEquals(TipJar.KEYS, prefs().all.keys)
        jar.stopAsking()
        assertTrue(TipJar.KEYS.containsAll(prefs().all.keys))
    }

    @Test
    fun aBackupLeavesTheCardOutAndARestoreLeavesItAlone() {
        val jar = laterRun()
        (1L..3L).forEach(jar::nightSaved)
        timer.setGameDurationMinutes(180) // something for the backup to hold
        val text = backups().export()
        assertFalse("the backup names ${TipJar.FILE}", TipJar.FILE in text)

        jar.stopAsking() // "Don't ask again" after the backup was made
        val restored = backups().open(text)
        backups().replace(restored)
        assertTrue("the restore brought the card back", restart().ask.value.stopped)
    }

    private fun backups() = Backups(BackupModule.settingsSections(context, Clock), Clock, context)

    private object Clock : TimeSource {
        override fun elapsedRealtimeMillis(): Long = 50_000L

        override fun wallClockMillis(): Long = 1_791_460_800_000L

        override fun bootCount(): Int = 7
    }
}
