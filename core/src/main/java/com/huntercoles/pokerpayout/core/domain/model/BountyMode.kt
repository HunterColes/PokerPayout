package com.huntercoles.pokerpayout.core.domain.model

/**
 * How knockouts pay (PP-035). Every game saved before PP-035 has no mode stored and plays
 * [STANDARD], as it always did.
 */
enum class BountyMode(
    /** What the Tournament settings store; never rename one. */
    val key: String
) {
    /**
     * The knocked-out player's whole bounty goes to whoever knocked them out. The champion keeps
     * their own and collects the ones nobody was credited with (PP-055).
     */
    STANDARD("standard"),

    /**
     * Progressive knockout (PKO): every bounty starts at the bounty amount. A knockout pays half the
     * knocked-out player's bounty in cash and adds the other half to the eliminator's own bounty
     * ([ProgressiveBounty]). The champion takes their final bounty and, as in [STANDARD], the
     * bounties nobody was credited with.
     */
    PROGRESSIVE("progressive"),

    /**
     * Mystery bounty: the bounties make one pool of envelopes ([MysteryBounty]); each knockout draws
     * one at random. The champion takes the envelopes left.
     */
    MYSTERY("mystery");

    companion object {
        /** The mode stored under [key]; nothing stored, or anything unknown, is [STANDARD]. */
        fun fromKey(key: String?): BountyMode = entries.firstOrNull { it.key == key } ?: STANDARD
    }
}
