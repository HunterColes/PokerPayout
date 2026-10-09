package com.huntercoles.pokerpayout.core.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.domain.history.NightStore
import com.huntercoles.pokerpayout.core.domain.history.Nights.night
import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.ChipCalculatorPreferences
import com.huntercoles.pokerpayout.core.preferences.OddsCalculatorPreferences
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.time.TimeSource
import com.huntercoles.pokerpayout.core.utils.ChipColour
import com.huntercoles.pokerpayout.core.utils.ChipDistributionCurve
import com.huntercoles.pokerpayout.core.utils.ChipInventory
import com.huntercoles.pokerpayout.core.utils.InventoryChip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A backup file in the first format (schema 1, every section at version 1), as version 1.4.0 writes
 * it: `src/test/resources/backup/backup-v1.json`. Every later version must restore it, so this file
 * never changes; a new format gets a file and a test of its own beside it.
 *
 * Its game holds keys older versions wrote (v1.1.x money as Float dollars, rebuys counted without
 * their prices): a backup puts keys back as they were, and the app's own migrations bring them up to
 * date when it starts again, exactly as after an update.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupFileV1Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val text = requireNotNull(javaClass.getResource("/backup/backup-v1.json")).readText()

    private val time = object : TimeSource {
        override fun elapsedRealtimeMillis(): Long = 0L

        override fun wallClockMillis(): Long = 0L

        override fun bootCount(): Int = -1
    }

    private fun backups() = Backups(
        BackupModule.settingsSections(context, time) + BackupModule.historySection(HistoryBackup(NightStore(context))),
        time,
        context,
    )

    @Before
    fun wipe() {
        BackupCatalog.FILES.forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
    }

    @Test
    fun `its preview lists what it holds, the presets left to the Tournament's section`() {
        val opened = backups().open(text)
        assertEquals("1.4.0", opened.meta.appVersion)
        assertEquals(LocalDate.of(2026, 10, 8), opened.meta.created?.atZone(ZoneOffset.UTC)?.toLocalDate())
        assertEquals(
            listOf(
                BackupLine.Counted(R.plurals.backup_line_nights, 1),
                BackupLine.Named(R.string.backup_line_chip_set),
                BackupLine.Named(R.string.backup_line_game),
                BackupLine.Named(R.string.backup_line_sound),
                BackupLine.Named(R.string.backup_line_tools),
            ),
            opened.lines,
        )
        // The core alone has no presets section: in the app, tournament-feature's reads them
        assertEquals(1, opened.unread)
    }

    @Test
    fun `it restores, and the app brings its old keys up to date as it starts`() {
        assertTrue("settings restored: the app starts again", backups().replace(backups().open(text)))

        val tournament = TournamentPreferences(context)
        assertEquals(9, tournament.getPlayerCount())
        assertEquals(1_250L, tournament.getMoneySettings().buyInCents) // v1.1.x's 12.5f
        assertEquals(1_000L, tournament.getMoneySettings().rebuyCents)
        assertEquals(BountyMode.MYSTERY, tournament.getBountyMode())
        assertEquals(20, tournament.getRoundLengthMinutes())

        val bank = BankPreferences(context)
        assertEquals("Dana", bank.getPlayerName(1))
        assertTrue(bank.getPlayerBuyInStatus(1))
        assertEquals(listOf(1_000L, 1_000L), bank.getPlayerRebuyPrices(1)) // priced once, as after an update

        val timer = TimerPreferences(context)
        assertEquals(2_400_000L, timer.getClock()?.elapsedMillis)
        assertTrue(timer.getHasTimerStarted())
        assertEquals("Last rebuy", timer.getBreakMessage())

        val chips = ChipCalculatorPreferences(context, tournament).current()
        assertEquals(
            ChipInventory.of(
                listOf(InventoryChip(ChipColour.White, 25, 200), InventoryChip(ChipColour.Red, 100, 150), InventoryChip(ChipColour.Green, 500, 50)),
            ),
            chips.inventory,
        )
        assertTrue(chips.inventoryReviewed)
        assertEquals(ChipDistributionCurve.BellCurve, chips.shape)
        assertEquals(4, chips.maxColours)

        assertEquals(0.6f, AudioPreferences(context).getVolume())
        assertEquals(false, AudioPreferences(context).getVibrateCues())
        assertTrue(OddsCalculatorPreferences(context).fourColourDeck)
        assertEquals(6, context.getSharedPreferences("seat_draw_prefs", Context.MODE_PRIVATE).getInt("seats_per_table", 0))

        val night = NightStore(context).nights.value.single()
        assertEquals(4L, night.id)
        assertEquals(listOf("Dana", "Zoë, \"Ace\""), night.players.map { it.name })
        assertEquals(12_000L, night.prizePoolCents)
    }

    @Test
    fun `its nights merge into a phone that has others`() {
        val store = NightStore(context)
        store.add(night("2026-09-01", listOf("Jo", "Sam")))
        val result = backups().merge(backups().open(text))
        assertEquals(1, result.total)
        assertEquals(2, NightStore(context).nights.value.size)
    }
}
