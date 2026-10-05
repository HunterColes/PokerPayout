package com.huntercoles.pokerpayout.tools.poker

import java.util.concurrent.Executors
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Engine API behaviour. Expected counts come from exhaustive enumeration in an independent
 * Python evaluator unless the comment says they are hand-derived.
 */
class OddsEngineTest {

    private val engine = OddsEngine()

    private fun request(vararg seats: String, board: String = "", dead: String = "") =
        OddsRequest(seats.map { Seat.of(it) }, Cards.parseAll(board), Cards.parseAll(dead))

    private fun result(request: OddsRequest, settings: OddsSettings = OddsSettings()) =
        runBlocking { engine.finalResult(request, settings) }

    // ------------------------------------------------------------------ exact mode

    @Test
    fun `heads-up preflop with known hands is enumerated exactly`() {
        val r = result(request("As Ah", "Ks Kd"))
        assertTrue(r.exact)
        assertTrue(r.complete)
        assertNull(r.seed)
        assertEquals(1_712_304L, r.deals)
        assertEquals(listOf(1_399_204L, 305_177L), r.players.map { it.wins })
        assertEquals(listOf(7_923L, 7_923L), r.players.map { it.ties })
        assertEquals(listOf(3_535_977_060L, 779_029_020L), r.players.map { it.potShares })
        assertEquals(listOf(0.0, 0.0), r.players.map { it.equityStdErr })
    }

    @Test
    fun `a random hand on the flop is enumerated exactly`() {
        // As Ks vs a random hand on Js Ts 2c: 1,081 villain hands x 990 runouts.
        val r = result(request("As Ks", "", board = "Js Ts 2c"))
        assertTrue(r.exact)
        assertEquals(1_070_190L, r.deals)
        assertEquals(listOf(805_388L, 254_892L), r.players.map { it.wins })
        assertEquals(listOf(9_910L, 9_910L), r.players.map { it.ties })
        assertEquals(listOf(2_042_064_360L, 654_814_440L), r.players.map { it.potShares })
        assertEquals(75.72, r.players[0].equityPct, 0.005)
    }

    @Test
    fun `three-way turn with a random hand is enumerated exactly`() {
        val r = result(request("Ah Kd", "Qc Qs", "", board = "Kc 7d 2s 9h"))
        assertTrue(r.exact)
        assertEquals(39_732L, r.deals)
        assertEquals(listOf(33_454L, 1_772L, 4_266L), r.players.map { it.wins })
        assertEquals(listOf(240L, 0L, 240L), r.players.map { it.ties })
        assertEquals(listOf(84_606_480L, 4_465_440L, 11_052_720L), r.players.map { it.potShares })
        assertEquals(100.0, r.players.sumOf { it.equityPct }, 1e-9)
    }

    @Test
    fun `river hand categories are reported per player`() {
        // 22 vs 98 on 9s 7d 5c 4h, 44 rivers. Hand-derivable: a deuce (2) gives 22 a set, a
        // 9/7/5/4 (11) gives it two pair, the other 31 leave one pair.
        val r = result(request("2h 2d", "9h 8c", board = "9s 7d 5c 4h"))
        assertEquals(44L, r.deals)
        assertEquals(
            pct(44, HandCategory.PAIR to 31, HandCategory.TWO_PAIR to 11, HandCategory.TRIPS to 2),
            r.players[0].handCategoryPct,
        )
        assertEquals(
            pct(44, HandCategory.PAIR to 26, HandCategory.TWO_PAIR to 12, HandCategory.TRIPS to 2, HandCategory.STRAIGHT to 4),
            r.players[1].handCategoryPct,
        )
    }

    @Test
    fun `dead cards are never dealt`() {
        // With the other two deuces dead, 22 has no outs at all.
        val r = result(request("2h 2d", "9h 8c", board = "9s 7d 5c 4h", dead = "2c 2s"))
        assertTrue(r.exact)
        assertEquals(42L, r.deals)
        assertEquals(listOf(0L, 42L), r.players.map { it.wins })
        assertEquals(0.0, r.players[0].handCategoryPct[HandCategory.TRIPS.ordinal])
    }

    @Test
    fun `a folded seat's cards are dead and the seat has no odds`() {
        val withFold = result(
            OddsRequest(
                seats = listOf(Seat.of("As Ks"), Seat.of("Jd Jc", folded = true), Seat.of("Qh Qd")),
                board = Cards.parseAll("Js Ts 2c"),
            ),
        )
        val withDead = result(request("As Ks", "Qh Qd", board = "Js Ts 2c", dead = "Jd Jc"))
        assertEquals(3, withFold.players.size)
        assertTrue(withFold.players[1].folded)
        assertEquals(0.0, withFold.players[1].equityPct)
        assertEquals(emptyList<Double>(), withFold.players[1].handCategoryPct)
        assertEquals(withDead.deals, withFold.deals)
        assertEquals(withDead.players[0], withFold.players[0])
        assertEquals(withDead.players[1], withFold.players[2])
    }

    @Test
    fun `a folded random hand changes nothing`() {
        val base = result(request("As Ks", "Qh Qd", board = "Js Ts 2c"))
        val withFold = result(
            OddsRequest(listOf(Seat.of("As Ks"), Seat(folded = true), Seat.of("Qh Qd")), Cards.parseAll("Js Ts 2c")),
        )
        assertEquals(base.players[0], withFold.players[0])
        assertEquals(base.players[1], withFold.players[2])
    }

    // ------------------------------------------------------------------ Monte Carlo

    @Test
    fun `AA vs a random hand preflop falls back to Monte Carlo and lands on the known 85_2 percent`() {
        // 1,225 villain hands x 1.7M boards is far past the exact budget. Published: 85.20%;
        // independent Python Monte Carlo (1.2M deals): 85.21%.
        val r = result(request("As Ah", ""), OddsSettings(maxSamples = 200_000, seed = 11))
        assertFalse(r.exact)
        assertTrue(r.complete)
        assertEquals(200_000L, r.deals)
        assertEquals(11L, r.seed)
        val aa = r.players[0]
        assertTrue(aa.equityStdErr in 0.05..0.12, "standard error ${aa.equityStdErr}")
        assertEquals(85.2, aa.equityPct, 4 * aa.equityStdErr)
        assertEquals(100.0, r.players.sumOf { it.equityPct }, 1e-9)
    }

    @Test
    fun `Monte Carlo agrees with exact enumeration within four standard errors`() {
        val tenHanded = request("As Ah", "Ks Kh", "Qs Qh", "Js Jh", "Ts Th", "9s 9h", "8s 8h", "7s 7h", "6s 6h", "5s 5h")
        val exact = result(tenHanded)
        assertTrue(exact.exact)
        assertEquals(201_376L, exact.deals) // C(32, 5)
        val mc = result(tenHanded, OddsSettings(maxSamples = 100_000, exactBudget = 0, seed = 5))
        assertFalse(mc.exact)
        mc.players.zip(exact.players).forEachIndexed { i, (m, e) ->
            assertTrue(
                abs(m.equityPct - e.equityPct) <= 4 * m.equityStdErr,
                "seat ${i + 1}: MC ${m.equityPct} +/- ${m.equityStdErr} vs exact ${e.equityPct}",
            )
        }
    }

    @Test
    fun `same seed gives identical snapshots on any thread count`() {
        val req = request("Ah Kd", "", "", board = "Qs 7h 2c")
        val settings = OddsSettings(maxSamples = 40_000, exactBudget = 0, seed = 1234)
        val parallel = runBlocking { OddsEngine(Dispatchers.Default).calculate(req, settings).toList() }
        val single = Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { one ->
            runBlocking { OddsEngine(one).calculate(req, settings).toList() }
        }
        assertEquals(parallel, single)
        val other = runBlocking { engine.calculate(req, settings.copy(seed = 4321)).toList() }
        assertTrue(other.last() != parallel.last(), "a different seed should give different samples")
    }

    @Test
    fun `snapshots grow and only the last one is complete`() {
        val snapshots = runBlocking {
            engine.calculate(request("As Ah", ""), OddsSettings(maxSamples = 100_000, seed = 3)).toList()
        }
        assertEquals(listOf(2_048L, 6_144L, 14_336L, 30_720L, 63_488L, 96_256L, 100_000L), snapshots.map { it.deals })
        assertEquals(listOf(false, false, false, false, false, false, true), snapshots.map { it.complete })
        assertTrue(snapshots.first().maxStdErr > snapshots.last().maxStdErr)
    }

    @Test
    fun `Monte Carlo stops early once the target standard error is reached`() {
        val r = result(request("As Ah", ""), OddsSettings(maxSamples = 1_000_000, targetStdErr = 0.25, seed = 9))
        assertTrue(r.complete)
        assertTrue(r.maxStdErr <= 0.25)
        assertTrue(r.deals < 1_000_000L, "stopped at ${r.deals}")
    }

    @Test
    fun `cancelling the collector stops a long Monte Carlo run promptly`() = runBlocking {
        val first = CompletableResult()
        val job = launch(Dispatchers.Default) {
            engine.calculate(request("As Ah", "", "", ""), OddsSettings(maxSamples = Int.MAX_VALUE, seed = 1))
                .collect { first.offer(it) }
        }
        withTimeout(10_000) { first.await() }
        val started = System.nanoTime()
        withTimeout(2_000) { job.cancelAndJoin() }
        assertTrue((System.nanoTime() - started) / 1e6 < 2_000)
    }

    @Test
    fun `cancelling stops a long exact enumeration promptly`() = runBlocking {
        // Preflop vs a random hand, forced exact: ~2 billion deals, minutes of work.
        val job = launch(Dispatchers.Default) {
            engine.calculate(request("As Ah", ""), OddsSettings(exactBudget = Long.MAX_VALUE)).first()
        }
        kotlinx.coroutines.delay(200)
        assertTrue(job.isActive, "the enumeration should still be running")
        withTimeout(2_000) { job.cancelAndJoin() }
    }

    // ------------------------------------------------------------------ errors

    @Test
    fun `invalid requests are reported as OddsInputException`() {
        fun error(req: OddsRequest): String =
            assertThrows(OddsInputException::class.java) { result(req) }.message!!

        assertEquals("As is used twice.", error(request("As Kd", "As Qc")))
        assertEquals("Kc is used twice.", error(request("As Kd", "Qh Qc", board = "Kc 7d Kc")))
        assertEquals("Add at least two players.", error(request("As Kd")))
        assertEquals(
            "At least two players must stay in the hand.",
            error(OddsRequest(listOf(Seat.of("As Kd"), Seat.of("Qh Qc", folded = true)))),
        )
        assertEquals("Player 2 has more than two hole cards.", error(OddsRequest(listOf(Seat.of("As Kd"), Seat.of("Qh Qc Jd")))))
        assertEquals("The board has at most five cards.", error(request("As Kd", "Qh Qc", board = "2c 3c 4c 5c 6c 7c")))
        assertEquals("At most 10 players.", error(OddsRequest(List(11) { Seat.RANDOM })))
        assertEquals("Unknown card index 52.", error(OddsRequest(listOf(Seat(listOf(52)), Seat.RANDOM))))
    }

    private fun pct(total: Int, vararg counts: Pair<HandCategory, Int>): List<Double> {
        val byCat = counts.toMap()
        return HandCategory.entries.map { 100.0 * (byCat[it] ?: 0) / total }
    }

    /** Minimal one-shot latch for the first snapshot. */
    private class CompletableResult {
        private val deferred = kotlinx.coroutines.CompletableDeferred<OddsResult>()
        fun offer(r: OddsResult) { deferred.complete(r) }
        suspend fun await(): OddsResult = deferred.await()
    }
}
