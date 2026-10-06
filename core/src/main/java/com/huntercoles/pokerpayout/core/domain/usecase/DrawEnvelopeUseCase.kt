package com.huntercoles.pokerpayout.core.domain.usecase

import com.huntercoles.pokerpayout.core.domain.model.MysteryBounty
import java.security.SecureRandom
import javax.inject.Inject
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * The mystery-bounty draw (PP-035): one envelope, each as likely as the next, from those left. The
 * app draws with [SecureRandom], so nobody can work out the next envelope from the last ones; tests
 * pass a seeded [Random].
 */
class DrawEnvelopeUseCase(private val random: Random) {

    @Inject
    constructor() : this(SecureRandom().asKotlinRandom())

    /** One of [envelopesLeft], or null if there are none. */
    operator fun invoke(envelopesLeft: List<Long>): Long? = MysteryBounty.draw(envelopesLeft, random)
}
