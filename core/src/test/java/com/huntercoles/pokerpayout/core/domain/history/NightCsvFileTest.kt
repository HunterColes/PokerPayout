package com.huntercoles.pokerpayout.core.domain.history

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.backup.BackupException
import com.huntercoles.pokerpayout.core.backup.BackupProblem
import com.huntercoles.pokerpayout.core.backup.ResolverDocumentFiles
import com.huntercoles.pokerpayout.core.domain.history.Nights.night
import com.huntercoles.pokerpayout.core.domain.history.Nights.player
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * History's CSV as a real file (PP-037): RFC 4180 that any CSV reader gets back field for field (names
 * with commas, quotes, line breaks and letters beyond ASCII included), written as UTF-8 with no byte
 * order mark, lines ending in CRLF, through the same file writer the backup uses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NightCsvFileTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val awkward = SavedNight(
        id = 1L,
        date = LocalDate.of(2026, 10, 5),
        structureName = "Deep, \"slow\"",
        prizePoolCents = 10_000L,
        players = listOf(
            player("Zoë Ångström", 1, prize = 7_000L),
            player("Smith, \"Ace\" Jr", 2, prize = 3_000L),
            player("Two\r\nlines", 3),
            player("=1+1", 4),
            player("李雷 🂡", 5),
        ),
    )

    @Test
    fun `every field reads back as it was written, the header first`() {
        val rows = parse(NightCsv.of(listOf(awkward, night("2026-09-12", listOf("Jo", "Sam")))))
        assertEquals(NightCsv.HEADER, rows.first())
        assertTrue(rows.all { it.size == NightCsv.HEADER.size })
        assertEquals(1 + awkward.players.size + 2, rows.size)
        val names = rows.drop(1).map { it[NightCsv.HEADER.indexOf("player")] }
        assertEquals(listOf("Jo", "Sam", "Zoë Ångström", "Smith, \"Ace\" Jr", "Two\r\nlines", "'=1+1", "李雷 🂡"), names)
        assertEquals("Deep, \"slow\"", rows.last()[NightCsv.HEADER.indexOf("structure")])
        assertEquals("70.00", rows[3][NightCsv.HEADER.indexOf("prize")])
    }

    @Test
    fun `the file is UTF-8 without a byte order mark, every line ending in CRLF`() {
        val file = folder.newFile("poker-nights.csv")
        val csv = NightCsv.of(listOf(awkward))
        ResolverDocumentFiles(context).write(Uri.fromFile(file), csv)

        val bytes = file.readBytes()
        assertArrayEquals(csv.toByteArray(Charsets.UTF_8), bytes)
        assertEquals('d'.code.toByte(), bytes.first()) // "date", no byte order mark
        assertTrue(String(bytes, Charsets.UTF_8).contains("Zoë Ångström"))
        assertTrue(String(bytes, Charsets.UTF_8).endsWith("\r\n"))
        assertEquals(csv, ResolverDocumentFiles(context).read(Uri.fromFile(file)))
    }

    @Test
    fun `a file written again holds the new text alone`() {
        val file = folder.newFile("poker-nights.csv")
        file.writeText("x".repeat(10_000))
        ResolverDocumentFiles(context).write(Uri.fromFile(file), "date\r\n")
        assertEquals("date\r\n", file.readText())
    }

    @Test
    fun `a file too big to be a backup isn't read whole`() {
        val file = folder.newFile("big.json")
        file.writeText("{" + " ".repeat(2_000) + "}")
        val problem = assertThrows(BackupException::class.java) { ResolverDocumentFiles(context).read(Uri.fromFile(file), maxBytes = 1_000) }
        assertEquals(BackupProblem.TooBig, problem.problem)
    }

    /** A strict RFC 4180 reader: fields split on commas, records on CRLF, quoted fields with doubled quotes. */
    private fun parse(csv: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < csv.length) {
            val c = csv[i]
            when {
                quoted && c == '"' && csv.getOrNull(i + 1) == '"' -> field.append('"').also { i++ }
                quoted && c == '"' -> quoted = false
                quoted -> field.append(c)
                c == '"' -> quoted = true
                c == ',' -> row.add(field.toString()).also { field.clear() }
                c == '\r' && csv.getOrNull(i + 1) == '\n' -> {
                    row.add(field.toString())
                    field.clear()
                    rows.add(row)
                    row = mutableListOf()
                    i++
                }
                else -> field.append(c)
            }
            i++
        }
        assertTrue("the last record ends in CRLF", row.isEmpty() && field.isEmpty())
        return rows
    }
}
