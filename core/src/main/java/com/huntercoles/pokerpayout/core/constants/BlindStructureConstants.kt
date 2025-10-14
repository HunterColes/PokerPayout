package com.huntercoles.pokerpayout.core.constants

/**
 * Reference data for generating tournament blind structures.
 *
 * These constants capture the common chip denominations and blind multipliers
 * used across live and home poker tournaments. The multipliers are intended to
 * deliver roughly 25-35% increases between consecutive blind levels while
 * keeping values aligned with familiar chip denominations.
 */
object BlindStructureConstants {

    /**
     * Standard chip denominations typically available in a tournament set.
     * These are expressed in base chip values (e.g., $25 chip, $100 chip, etc.).
     *
     * For a given smallest chip, valid chip denominations can be inferred by
     * multiplying these base units by the ratio between the smallest chip and
     * the canonical $25 starting chip.
     */
    val STANDARD_CHIP_DENOMINATIONS: List<Int> = listOf(
        1,      // Micro / add-on chips
        5,      // Common lowest cash chip
        10,
        25,     // Typical tournament starting chip
        50,
        100,
        200,
        500,
        1_000,
        2_000,
        5_000,
        10_000,
        25_000,
        50_000,
        100_000
    )

    /**
     * Canonical blind values expressed in $5 units. Multiply each entry by
     * (smallestChip / 5) to obtain the recommended small blind ladder for a
     * tournament that begins at the specified smallest chip value.
     */
    val STANDARD_SMALL_BLIND_BASES: List<Int> = listOf(
        5,
        10,
        15,
        20,
        25,
        30,
        40,
        50,
        60,
        75,
        80,
        100,
        120,
        150,
        200,
        250,
        300,
        400,
        500,
        600,
        800,
        1_000,
        1_200,
        1_500,
        2_000,
        2_500,
        3_000,
        4_000,
        5_000,
        6_000,
        8_000,
        10_000,
        12_000,
        15_000,
        20_000,
        25_000,
        30_000,
        40_000,
        50_000
    )
}