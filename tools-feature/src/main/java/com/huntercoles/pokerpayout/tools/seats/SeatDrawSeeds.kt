package com.huntercoles.pokerpayout.tools.seats

import java.security.SecureRandom

/**
 * Where each draw's seed comes from. Every draw is `Random(seed)`, so a test that fixes the seeds
 * gets the same seats and cards every time; the app seeds from [SecureRandom], so nobody at the
 * table can predict or steer a draw.
 */
fun interface SeatDrawSeeds {
    fun next(): Long
}

/** The app's seeds: a fresh one from [SecureRandom] for every draw. */
class SecureSeatDrawSeeds : SeatDrawSeeds {
    private val random = SecureRandom()

    override fun next(): Long = random.nextLong()
}
