package com.huntercoles.pokerpayout.core.testing

import io.kotest.common.ExperimentalKotest
import io.kotest.property.Gen
import io.kotest.property.PropTestConfig
import io.kotest.property.PropertyContext
import io.kotest.property.PropertyTesting
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking

/**
 * Property-based tests (kotest-property, run from plain JUnit 5 `@Test` methods).
 *
 * [forAll] draws [iterations] cases from [gen] with a fixed [seed], so every run checks the same
 * cases and a failure reproduces. On a failure kotest shrinks the case to a smallest one that still
 * fails, then reports both, the original and the shrunk, with the seed ("Repeat this test by using
 * seed ..."). Change a seed only on purpose, to explore new cases.
 *
 * Generators mix in edge cases (0, 1, the ends of every range) about one case in fifty.
 */
@OptIn(ExperimentalKotest::class) // PropTestConfig.iterations
fun <A> forAll(seed: Long, iterations: Int, gen: Gen<A>, property: suspend PropertyContext.(A) -> Unit) {
    // A failed seed must not be written to ~/.kotest and replayed on the next run: same cases, every run.
    PropertyTesting.writeFailedSeed = false
    runBlocking {
        checkAll(PropTestConfig(seed = seed, iterations = iterations), gen, property)
    }
}

/** Fails with [message] (and the case kotest prints with it) unless [condition] holds. */
fun expect(condition: Boolean, message: () -> String) {
    if (!condition) throw AssertionError(message())
}
