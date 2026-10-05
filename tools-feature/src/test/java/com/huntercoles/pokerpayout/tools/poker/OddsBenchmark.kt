package com.huntercoles.pokerpayout.tools.poker

import java.util.concurrent.Executors
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * JVM micro-benchmark for the odds engine. Skipped unless ODDS_BENCH=1:
 * ```
 * ODDS_BENCH=1 ./gradlew :tools-feature:testDebugUnitTest --tests '*OddsBenchmark*' -i
 * ```
 * Prints the median of several timed runs after warm-up, both on Dispatchers.Default and on
 * a single thread (the single-thread figure is the better guide for a phone core).
 */
@EnabledIfEnvironmentVariable(named = "ODDS_BENCH", matches = "1")
class OddsBenchmark {

    @Test
    fun benchmark() {
        println("cores=${Runtime.getRuntime().availableProcessors()} java=${System.getProperty("java.version")}")
        evaluatorThroughput()
        val single = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        try {
            val headsUp = OddsRequest(listOf(Seat.of("As Ah"), Seat.of("Ks Kd")))
            val fourWayFlop = OddsRequest(
                listOf(Seat.of("As Kd"), Seat.of("Qh Qc"), Seat.of("Jc Tc"), Seat.of("8s 7s")),
                Cards.parseAll("Ad 9c 2h"),
            )
            val tenHanded = OddsRequest(
                listOf("As Ah", "Ks Kh", "Qs Qh", "Js Jh", "Ts Th", "9s 9h", "8s 8h", "7s 7h", "6s 6h", "5s 5h").map { Seat.of(it) },
            )
            val mc50k = OddsSettings(maxSamples = 50_000, exactBudget = 0, seed = 1)
            for ((name, dispatcher) in listOf("all cores" to Dispatchers.Default, "1 thread" to single)) {
                time("heads-up preflop exact (1,712,304 boards), $name", dispatcher, headsUp, OddsSettings())
                time("4-way flop exact (820 runouts), $name", dispatcher, fourWayFlop, OddsSettings())
                time("10-player preflop Monte Carlo 50k, $name", dispatcher, tenHanded, mc50k)
                time("10-player preflop exact (201,376 boards), $name", dispatcher, tenHanded, OddsSettings())
                time("AA vs random preflop Monte Carlo 100k, $name", dispatcher,
                    OddsRequest(listOf(Seat.of("As Ah"), Seat.RANDOM)), OddsSettings(maxSamples = 100_000, seed = 2))
            }
        } finally {
            single.close()
        }
    }

    private fun evaluatorThroughput() {
        val rnd = Random(1)
        val hands = LongArray(1 shl 20) {
            val deck = (0 until 52).shuffled(rnd)
            Cards.mask(deck.subList(0, 7))
        }
        var sink = 0
        repeat(5) { for (h in hands) sink += HandEvaluator.evaluate(h) }
        val runs = (1..7).map {
            val t0 = System.nanoTime()
            for (h in hands) sink += HandEvaluator.evaluate(h)
            System.nanoTime() - t0
        }.sorted()
        val ns = runs[runs.size / 2].toDouble() / hands.size
        println("HandEvaluator.evaluate(7 cards): %.1f ns/eval = %.1fM evals/s single-thread (sink %d)".format(ns, 1e3 / ns, sink and 1))
    }

    private fun time(label: String, dispatcher: CoroutineDispatcher, request: OddsRequest, settings: OddsSettings) {
        val engine = OddsEngine(dispatcher)
        repeat(3) { runBlocking { engine.finalResult(request, settings) } }
        val runs = (1..5).map {
            val t0 = System.nanoTime()
            val r = runBlocking { engine.finalResult(request, settings) }
            (System.nanoTime() - t0) to r
        }.sortedBy { it.first }
        val (ns, r) = runs[runs.size / 2]
        println("%-62s %8.1f ms  (%s, %,d deals)".format(label, ns / 1e6, if (r.exact) "exact" else "MC", r.deals))
    }
}
