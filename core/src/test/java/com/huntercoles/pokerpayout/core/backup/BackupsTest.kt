package com.huntercoles.pokerpayout.core.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.audio.music.BreakMusic
import com.huntercoles.pokerpayout.core.audio.music.MusicTrack
import com.huntercoles.pokerpayout.core.audio.music.Playlist
import com.huntercoles.pokerpayout.core.domain.history.NightStore
import com.huntercoles.pokerpayout.core.domain.history.Nights.night
import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.settle.Transfer
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import com.huntercoles.pokerpayout.core.preferences.MusicPreferences
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.ChipCalculatorPreferences
import com.huntercoles.pokerpayout.core.preferences.CurrencyPreferences
import com.huntercoles.pokerpayout.core.preferences.OddsCalculatorPreferences
import com.huntercoles.pokerpayout.core.preferences.PhonePrefs
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.time.ClockAnchor
import com.huntercoles.pokerpayout.core.time.TimeSource
import com.huntercoles.pokerpayout.core.utils.ChipColour
import com.huntercoles.pokerpayout.core.utils.ChipDistributionCurve
import com.huntercoles.pokerpayout.core.utils.ChipInventory
import com.huntercoles.pokerpayout.core.utils.InventoryChip
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * Backups of everything the core saves ([Backups] with the core's sections, as the app builds them in
 * [BackupModule]): every setting the app writes comes back exactly over a wiped phone; a running
 * clock is saved paused; what's about this phone stays on it; History merges without duplicates, with
 * Undo; and a file only partly readable restores what it can.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val time = Clock()
    private lateinit var nights: NightStore

    private val friday =
        night("2026-10-02", listOf("Dana", "Marcus", "Priya"), prizes = listOf(9_000L, 3_000L), structure = "Friday")
    private val saturday = night("2026-10-03", listOf("Priya", "Dana"), prizes = listOf(8_000L))
    private val holiday = night("2025-12-19", listOf("Theo", "Jo"), prizes = listOf(6_000L), structure = "Holiday")

    private fun prefs(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    /** The app's core sections, as Hilt builds them. */
    private fun backups(): Backups {
        val sections = BackupModule.settingsSections(context, time) + BackupModule.historySection(HistoryBackup(nights))
        return Backups(sections, time, context)
    }

    @Before
    fun wipe() {
        (BackupCatalog.FILES + BackupCatalog.PHONE_FILES).forEach { prefs(it).edit().clear().commit() }
        nights = NightStore(context)
    }

    /** A night in progress and a phone set up every way the core saves: every setter, off its default. */
    private fun fillEverything() {
        val tournament = fillTournament()
        fillBank()
        fillTools(tournament)
        // The currency (PP-114), as CurrencyPreferences saves it
        prefs(CurrencyPreferences.FILE).edit().putString("currency", "euro").commit()
        listOf(holiday, friday, saturday).forEach { nights.add(it) }
    }

    /** The setup and the clock. */
    private fun fillTournament(): TournamentPreferences {
        val tournament = TournamentPreferences(context)
        with(tournament) {
            setPlayerCount(6)
            setBuyInCents(4_000L)
            setFoodCents(500L)
            setBountyCents(1_000L)
            setRebuyCents(2_000L)
            setAddOnCents(1_500L)
            setBountyMode(BountyMode.PROGRESSIVE)
            setPayoutPreset(PayoutPreset.TOP_HEAVY, 2)
            setPayoutRounding(PayoutRounding.entries.last())
            setRebuyUntilLevel(4)
            setIsConfigExpanded(false)
            setGameDurationHours(4)
            setRoundLengthMinutes(15)
            setSmallestChip(25)
            setStartingChips(10_000)
            setSelectedPanel("blinds")
            setTournamentLocked(true)
        }
        with(TimerPreferences(context)) {
            saveClock(ClockAnchor.stopped(1_234_567L))
            setGameDurationMinutes(240)
            setHasTimerStarted(true)
            setIsFinished(false)
            setSmallestChipAtStart(25)
            setStartingChipsAtStart(10_000)
            setRoundLengthAtStart(15)
            setBreakEveryLevels(4)
            setBreakLengthMinutes(15)
            setBreakMessage("Last rebuy, then color up")
            setBigBlindAnteFromLevel(5)
            setColorUpDone(4, true)
        }
        return tournament
    }

    /** Tonight's players, their purchases and knockouts, and the settle-up's ticked payments. */
    private fun fillBank() {
        with(BankPreferences(context)) {
            savePlayerName(1, "Dana")
            savePlayerName(2, "Zoë, \"Ace\"")
            savePlayerBuyInStatus(1, true)
            savePlayerBuyInStatus(2, true)
            savePlayerOutStatus(2, true)
            savePlayerPayedOutStatus(1, true)
            savePlayerEliminatedBy(2, 1)
            savePlayerBountyDraw(2, 750L)
            savePlayerRebuyPrices(1, listOf(2_000L, 2_500L))
            savePlayerAddonPrices(1, listOf(1_500L))
            savePlayerOutLevel(2, 3)
            saveEliminationOrder(listOf(2))
            saveSettlePaid(setOf(Transfer(2, 0, 1_500L), Transfer(0, 1, 9_000L)))
        }
    }

    /** The chip set, sound, odds and the seat draw. */
    private fun fillTools(tournament: TournamentPreferences) {
        with(ChipCalculatorPreferences(context, tournament)) {
            setInventory(
                ChipInventory.of(listOf(InventoryChip(ChipColour.White, 25, 200), InventoryChip(ChipColour.Red, 100, 120))),
            )
            setStackOverride(8_000)
            setShape(ChipDistributionCurve.BellCurve)
            setMaxColours(4)
            setReserveOverride(2)
        }
        with(AudioPreferences(context)) {
            setVolume(0.35f)
            setMuted(true)
            setVibrateCues(false)
            setFlashCues(false)
            setSoundPack("classic")
        }
        with(MusicPreferences(context)) {
            setVolume(0.6f)
            setAutoPlay(true)
            setBreakMusic(BreakMusic.QUIET)
        }
        with(OddsCalculatorPreferences(context)) {
            setPlayerCount(4)
            setPlayerCards(1, "AsKs")
            setCommunityCards("Js10s2c")
            setPlayerFolded(3, true)
            fourColourDeck = true
        }
        // The seat draw's file (tools-feature), as it saves it
        prefs("seat_draw_prefs").edit()
            .putInt("seats_per_table", 6)
            .putString("players", "Dana,Zo%C3%AB")
            .putString("draw", "seats:1\nDana,Zo%C3%AB|As,Kd")
            .commit()
        // The shot clock's, dealer's choice's and the equity quiz's files (tools-feature), as they save them
        prefs("shot_clock_prefs").edit()
            .putInt("seconds", 45)
            .putInt("cards_each", 3)
            .putString("used_cards", "Dana=1,Zo%C3%AB=2")
            .commit()
        prefs("dealers_choice_prefs").edit()
            .putString("on_wheel", "holdem,badugi,house%3Aguts")
            .putString("house_games", "Guts")
            .putString("last_pick", "badugi")
            .commit()
        prefs("equity_quiz_prefs").edit()
            .putInt("hands", 3)
            .putString("question", "range")
            .putInt("streak", 2)
            .putInt("best_streak", 7)
            .putInt("right", 13)
            .putInt("answered", 17)
            .commit()
    }

    /** Every settings file as it stands, without the keys about this phone. */
    private fun settings(): Map<String, Map<String, Any?>> = BackupCatalog.SETTINGS.associate { file ->
        file.name to prefs(file.name).all.filterKeys { it !in file.phoneOnly }
    }

    @Test
    fun `every setting comes back exactly over a wiped phone, and History with it`() {
        fillEverything()
        val before = settings()
        val savedNights = nights.nights.value
        assertTrue("the fixture fills every settings file", before.values.all { it.isNotEmpty() })
        val text = backups().export()

        wipe()
        assertTrue(backups().replace(backups().open(text)))

        assertEquals(before, settings())
        assertEquals(savedNights, NightStore(context).nights.value)
    }

    @Test
    fun `after the restart the app reads the restored game as it was`() {
        fillEverything()
        val text = backups().export()
        wipe()
        backups().replace(backups().open(text))

        // Fresh instances, as after the app starts again
        val tournament = TournamentPreferences(context)
        assertEquals(6, tournament.getPlayerCount())
        assertEquals(4_000L, tournament.getMoneySettings().buyInCents)
        assertEquals(BountyMode.PROGRESSIVE, tournament.getBountyMode())
        assertEquals(PayoutPreset.TOP_HEAVY, tournament.getPayoutPreset())
        val timer = TimerPreferences(context)
        assertEquals(1_234_567L, timer.getClock()?.elapsedMillis)
        assertEquals("Last rebuy, then color up", timer.getBreakMessage())
        assertEquals(setOf(4), timer.getColorUpDoneAfterLevels())
        val bank = BankPreferences(context)
        assertEquals("Zoë, \"Ace\"", bank.getPlayerName(2))
        assertEquals(listOf(2_000L, 2_500L), bank.getPlayerRebuyPrices(1))
        assertEquals(750L, bank.getPlayerBountyDraw(2))
        assertEquals(setOf(Transfer(2, 0, 1_500L), Transfer(0, 1, 9_000L)), bank.getSettlePaid())
        val chips = ChipCalculatorPreferences(context, tournament).current()
        assertEquals(ChipDistributionCurve.BellCurve, chips.shape)
        assertEquals(2, chips.reserveOverride)
        assertEquals(0.35f, AudioPreferences(context).getVolume())
        assertTrue(OddsCalculatorPreferences(context).fourColourDeck)
    }

    @Test
    fun `the preview says what the backup holds, in order`() {
        fillEverything()
        val opened = backups().open(backups().export())
        assertEquals(
            listOf(
                BackupLine.Counted(R.plurals.backup_line_nights, 3),
                BackupLine.Named(R.string.backup_line_chip_set),
                BackupLine.Named(R.string.backup_line_game),
                BackupLine.Named(R.string.backup_line_sound),
                BackupLine.Named(R.string.backup_line_tools),
            ),
            opened.lines,
        )
        assertTrue(opened.canMerge)
        assertFalse(opened.partial)
        assertEquals("2026-10-08T12:00:00Z", opened.meta.created.toString())
    }

    @Test
    fun `a running clock is saved paused where it stands`() {
        time.realtime = 100_000L
        TimerPreferences(context).saveClock(ClockAnchor.runningFrom(600_000L, time))
        time.realtime = 160_000L // a minute later
        val text = backups().export()

        val timer = Json.parseToJsonElement(text).jsonObject.getValue("sections").jsonObject.getValue("game").jsonObject
            .getValue("files").jsonObject.getValue("timer_prefs").jsonObject
        assertEquals("""{"long":660000}""", timer.getValue("clock_elapsed_ms").toString())
        assertEquals("""{"boolean":false}""", timer.getValue("timer_running").toString())
        assertFalse(timer.keys.any { it.startsWith("clock_anchor_") })

        wipe()
        backups().replace(backups().open(text))
        val clock = TimerPreferences(context).getClock()
        assertEquals(
            ClockAnchor(elapsedMillis = 660_000L, running = false),
            clock?.copy(realtimeMillis = 0L, wallMillis = 0L, bootCount = -1),
        )
        assertFalse(TimerPreferences(context).getTimerRunning())
    }

    @Test
    fun `what's about this phone never leaves it, and a restore leaves it alone`() {
        fillEverything()
        val playlist = Playlist().add(listOf(MusicTrack(PICKED_SONG, "Night Owl")), Random(0))
        with(MusicPreferences(context)) {
            setPlaylist(playlist)
            setPosition(PICKED_SONG, 61_000)
        }
        // The flag 1.4.6 kept, as an install not yet started since the update still has it
        prefs("timer_prefs").edit().putBoolean("notifications_permission_asked", true).commit()
        val text = backups().export()
        listOf("notifications_permission_asked", PICKED_SONG, PhonePrefs.FILE, "position_ms").forEach {
            assertFalse(it, it in text)
        }

        // Another phone: no playlist (its songs are this phone's files)
        wipe()
        backups().replace(backups().open(text))
        assertTrue(MusicPreferences(context).getPlaylist().isEmpty)

        // This phone again: a restore leaves its playlist and the paused song's place alone
        with(MusicPreferences(context)) {
            setPlaylist(playlist)
            setPosition(PICKED_SONG, 61_000)
        }
        backups().replace(backups().open(text))
        assertEquals(playlist, MusicPreferences(context).getPlaylist())
        assertEquals(61_000, MusicPreferences(context).getPosition(PICKED_SONG))
    }

    @Test
    fun `merging adds the nights the phone doesn't have, once, and Undo takes them out`() {
        listOf(holiday, friday, saturday).forEach { nights.add(it) }
        val text = backups().export()
        wipe()
        val mine = nights.add(friday) // the same night, under another number
        val later = nights.add(night("2026-10-09", listOf("Sam", "Jo"), prizes = listOf(4_000L)))

        val result = backups().merge(backups().open(text))
        assertEquals(mapOf(HistoryBackup.KEY to 2), result.added)
        assertEquals(4, nights.nights.value.size)
        assertTrue(mine in nights.nights.value)
        assertEquals(0, backups().merge(backups().open(text)).total)

        result.undo()
        assertEquals(listOf(later, mine), NightStore(context).nights.value)
    }

    @Test
    fun `merging leaves the settings and the game as they are`() {
        fillEverything()
        val text = backups().export()
        wipe()
        AudioPreferences(context).setVolume(0.9f)
        val before = settings()

        backups().merge(backups().open(text))
        assertEquals(before, settings())
    }

    @Test
    fun `replacing puts the backup's nights in place of these`() {
        nights.add(holiday)
        val text = backups().export()
        nights.add(friday)

        backups().replace(backups().open(text))
        assertEquals(listOf(holiday.copy(id = 1L)), NightStore(context).nights.value)
        assertEquals(2L, NightStore(context).add(saturday).id)
    }

    @Test
    fun `a settings file the backup doesn't have is left as it is`() {
        fillEverything()
        val text = backups().export().replace("\"seat_draw_prefs\"", "\"seat_draw_prefs_from_later\"")
        wipe()
        prefs("seat_draw_prefs").edit().putInt("seats_per_table", 9).commit()

        backups().replace(backups().open(text))
        assertEquals(9, prefs("seat_draw_prefs").getInt("seats_per_table", 0))
        assertEquals(4, prefs("odds_calculator_prefs").getInt("player_count", 0))
    }

    @Test
    fun `a section from a later version is left out and the rest restores`() {
        fillEverything()
        val text = backups().export().replace("\"history\": {\n      \"version\": 1", "\"history\": {\n      \"version\": 2")
        wipe()
        nights.add(holiday)

        val opened = backups().open(text)
        assertTrue(opened.partial)
        assertFalse(BackupLine.Counted(R.plurals.backup_line_nights, 3) in opened.lines)
        backups().replace(opened)
        assertEquals(listOf("2025-12-19"), NightStore(context).nights.value.map { it.date.toString() })
        assertEquals(6, TournamentPreferences(context).getPlayerCount())
    }

    @Test
    fun `a night this version can't read is left out and counted`() {
        nights.add(friday)
        AudioPreferences(context).setVolume(0.5f)
        val text = backups().export().replace("\"format\": 1,", "\"format\": 2,")
        val opened = backups().open(text)
        assertEquals(1, opened.unread)
        assertNull(opened.lines.firstOrNull { it is BackupLine.Counted })
    }

    @Test
    fun `a file with nothing this version knows is empty`() {
        val text = BackupJson.write(BackupMeta("9.0.0", null), mapOf("music" to (1 to Json.parseToJsonElement("{}").jsonObject)))
        val problem = assertThrows(BackupException::class.java) { backups().open(text) }.problem
        assertEquals(BackupProblem.Empty, problem)
    }

    @Test
    fun `a damaged setting refuses the whole file, and nothing changes`() {
        fillEverything()
        val text = backups().export()
            .replace(Regex(""""player_count": \{\s*"int": 6\s*}"""), """"player_count": {"int": "six"}""")
        assertTrue("the fixture's player count is in the file", text.contains("\"six\""))
        val before = settings()
        val problem = assertThrows(BackupException::class.java) { backups().open(text) }.problem
        assertEquals(BackupProblem.Damaged, problem)
        assertEquals(before, settings())
    }

    private companion object {
        /** A song the host picked: a loan from this phone's file picker. */
        const val PICKED_SONG = "content://com.android.providers.media.documents/document/audio%3A12"
    }

    /** Stands still unless a test moves it: 12:00 UTC on 8 October 2026, one boot. */
    private class Clock : TimeSource {
        var realtime = 50_000L

        override fun elapsedRealtimeMillis(): Long = realtime

        override fun wallClockMillis(): Long = 1_791_460_800_000L

        override fun bootCount(): Int = 7
    }
}
