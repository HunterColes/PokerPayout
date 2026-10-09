package com.huntercoles.pokerpayout.core.domain.history

import com.huntercoles.pokerpayout.core.domain.history.Nights.night
import com.huntercoles.pokerpayout.core.domain.history.Nights.player
import com.huntercoles.pokerpayout.core.testing.withCurrency
import com.huntercoles.pokerpayout.core.utils.AppCurrency
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * History's CSV (PP-037): a header, one row per player per night (the oldest night first, players in
 * finishing order), dollars with two decimals, and RFC 4180 quoting for names with commas, quotes or
 * line breaks.
 */
class NightCsvTest {

    private val header = "date,structure,prize_pool,players,place,player,points,buy_in,rebuys,rebuy_total," +
        "add_ons,add_on_total,paid_in,prize,knockouts,bounties,won,net,currency"

    @Test
    fun `one row per player per night, the oldest night first and its players in finishing order`() {
        val later = night("2026-10-05", listOf("Dana", "Marcus"), id = 2, prizes = listOf(7_000L, 3_000L), structure = "Friday")
        val earlier = night("2026-09-12", listOf("Priya", "Theo", "Jo"), id = 1, prizes = listOf(15_000L))
        val lines = NightCsv.of(listOf(later, earlier)).split("\r\n")
        assertEquals(
            listOf(
                header,
                "2026-09-12,,150.00,3,1,Priya,3,50.00,0,0.00,0,0.00,50.00,150.00,0,0.00,150.00,100.00,$",
                "2026-09-12,,150.00,3,2,Theo,2,50.00,0,0.00,0,0.00,50.00,0.00,0,0.00,0.00,-50.00,$",
                "2026-09-12,,150.00,3,3,Jo,1,50.00,0,0.00,0,0.00,50.00,0.00,0,0.00,0.00,-50.00,$",
                "2026-10-05,Friday,100.00,2,1,Dana,2,50.00,0,0.00,0,0.00,50.00,70.00,0,0.00,70.00,20.00,$",
                "2026-10-05,Friday,100.00,2,2,Marcus,1,50.00,0,0.00,0,0.00,50.00,30.00,0,0.00,30.00,-20.00,$",
                "",
            ),
            lines,
        )
    }

    @Test
    fun `rebuys, add-ons, knockouts and bounties each have their column`() {
        val dana = NightPlayer(
            name = "Dana", place = 1, entryCents = 5_000L, rebuys = 2, rebuyCents = 8_050L, addOns = 1, addOnCents = 1_000L,
            prizeCents = 22_500L, knockouts = 3, bountyCents = 1_500L,
        )
        val night = SavedNight(1L, LocalDate.of(2026, 1, 9), null, prizePoolCents = 22_500L, players = listOf(dana))
        assertEquals(
            "2026-01-09,,225.00,1,1,Dana,1,50.00,2,80.50,1,10.00,140.50,225.00,3,15.00,240.00,99.50,$",
            NightCsv.of(listOf(night)).split("\r\n")[1],
        )
    }

    @Test
    fun `a name or structure with a comma, a quote or a line break is quoted, its quotes doubled`() {
        val night = SavedNight(
            id = 1L,
            date = LocalDate.of(2026, 1, 9),
            structureName = "Deep, slow",
            prizePoolCents = 0L,
            players = listOf(player("Smith, \"Ace\" Jr", 1), player("Line\nbreak", 2), player("Plain O'Neil", 3)),
        )
        val rows = NightCsv.of(listOf(night)).split("\r\n").drop(1)
        assertEquals("2026-01-09,\"Deep, slow\",0.00,3,1,\"Smith, \"\"Ace\"\" Jr\",3,", rows[0].substringBefore("50.00"))
        assertEquals("2026-01-09,\"Deep, slow\",0.00,3,2,\"Line\nbreak\",2,", rows[1].substringBefore("50.00"))
        assertEquals("2026-01-09,\"Deep, slow\",0.00,3,3,Plain O'Neil,1,", rows[2].substringBefore("50.00"))
        assertEquals("\"a\"\"\"", NightCsv.field("a\""))
        assertEquals("\"'\r\"", NightCsv.field("\r")) // a carriage return could start a formula too
        assertEquals("", NightCsv.field(""))
    }

    @Test
    fun `a name a spreadsheet would run as a formula gets a leading apostrophe`() {
        assertEquals("'=1+1", NightCsv.field("=1+1"))
        assertEquals("'+Ben", NightCsv.field("+Ben"))
        assertEquals("'-Ann", NightCsv.field("-Ann"))
        assertEquals("'@Dev", NightCsv.field("@Dev"))
        assertEquals("\"'=SUM(A1,A2)\"", NightCsv.field("=SUM(A1,A2)"))
        assertEquals("Alice", NightCsv.field("Alice"))
        assertEquals("Ben-Jo", NightCsv.field("Ben-Jo"))
    }

    @Test
    fun `amounts are dollars with two decimals, a loss with its minus sign`() {
        assertEquals("0.00", NightCsv.amount(0L))
        assertEquals("0.05", NightCsv.amount(5L))
        assertEquals("12.50", NightCsv.amount(1_250L))
        assertEquals("1234.56", NightCsv.amount(123_456L))
        assertEquals("-0.50", NightCsv.amount(-50L))
        assertEquals("-40.00", NightCsv.amount(-4_000L))
    }

    @Test
    fun `the last column is the money symbol the app shows, the amounts plain numbers in every currency`() {
        val night = night("2026-10-05", listOf("Dana", "Marcus"), id = 1, prizes = listOf(7_000L, 3_000L))
        fun firstRow(currency: AppCurrency) = NightCsv.of(listOf(night), currency).split("\r\n")[1]
        assertEquals("2026-10-05,,100.00,2,1,Dana,2,50.00,0,0.00,0,0.00,50.00,70.00,0,0.00,70.00,20.00,$", firstRow(AppCurrency.DOLLAR))
        assertEquals("2026-10-05,,100.00,2,1,Dana,2,50.00,0,0.00,0,0.00,50.00,70.00,0,0.00,70.00,20.00,€", firstRow(AppCurrency.EURO))
        assertEquals("2026-10-05,,100.00,2,1,Dana,2,50.00,0,0.00,0,0.00,50.00,70.00,0,0.00,70.00,20.00,R$", firstRow(AppCurrency.REAL))
        // The yen keeps the exact amounts too: the CSV is data, not what the screen rounds
        assertEquals("2026-10-05,,100.00,2,1,Dana,2,50.00,0,0.00,0,0.00,50.00,70.00,0,0.00,70.00,20.00,¥", firstRow(AppCurrency.YEN))
        assertEquals("2026-10-05,,100.00,2,1,Dana,2,50.00,0,0.00,0,0.00,50.00,70.00,0,0.00,70.00,20.00,", firstRow(AppCurrency.NONE))
        assertEquals("currency", NightCsv.HEADER.last())
        withCurrency(AppCurrency.KRONA) { assertTrue(NightCsv.of(listOf(night)).split("\r\n")[1].endsWith(",kr")) }
    }

    @Test
    fun `no nights is the header alone`() {
        assertEquals("$header\r\n", NightCsv.of(emptyList()))
    }
}
