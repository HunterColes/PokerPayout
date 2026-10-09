package com.huntercoles.pokerpayout.tools.tip

import android.graphics.Bitmap

/**
 * A small QR code reader for the tests (PP-112): enough to prove that the codes the app shows hold
 * the addresses beside them. It reads a clean, upright code (versions 1 to 6 with equal blocks, any
 * error correction level) by sampling each module's centre, then decodes numeric, alphanumeric and
 * byte segments. It corrects no errors: a code it reads is a code with none.
 */
object QrReader {

    /** The modules of the code in [image] (true is dark), sampled at each module's centre. */
    fun modules(image: Bitmap): List<List<Boolean>> {
        fun dark(x: Int, y: Int): Boolean {
            val rgb = image.getPixel(x, y)
            val alpha = rgb ushr ALPHA_SHIFT and BYTE
            val luma = ((rgb shr RED_SHIFT and BYTE) + (rgb shr GREEN_SHIFT and BYTE) + (rgb and BYTE)) / 3
            return alpha > HALF && luma < HALF
        }
        // The code's dark box, a few pixels in from the edge (a screenshot border isn't the code)
        val inner = (EDGE until image.height - EDGE).flatMap { y -> (EDGE until image.width - EDGE).map { x -> x to y } }
            .filter { (x, y) -> dark(x, y) }
        val left = inner.minOf { it.first }
        val top = inner.minOf { it.second }
        val right = inner.maxOf { it.first }
        // The finder pattern's top edge is 7 modules wide (its first row: a module may be one pixel)
        var run = 0
        while (dark(left + run, top)) run++
        val size = Math.round((right - left + 1) / (run / FINDER.toDouble())).toInt()
        val module = (right - left + 1) / size.toDouble()
        return List(size) { r ->
            List(size) { c -> dark((left + (c + CENTRE) * module).toInt(), (top + (r + CENTRE) * module).toInt()) }
        }
    }

    /** The text [modules] hold. */
    fun read(modules: List<List<Boolean>>): String {
        val n = modules.size
        val version = (n - BASE) / STEP
        require(version in 1..MAX_VERSION && n == BASE + STEP * version) { "a ${n}x$n code isn't one this reader knows" }
        val format = formatBits(modules)
        val level = Level.entries.single { it.bits == format shr MASK_BITS }
        val mask = format and MASK_MASK
        val stream = dataStream(modules, version, mask)
        val (blocks, perBlock) = level.blocks.getValue(version)
        // The data codewords are interleaved across equal blocks: take them back in order
        val codewords = stream.chunked(BYTE_BITS).map { bits -> bits.fold(0) { acc, bit -> acc shl 1 or bit } }
        val data = IntArray(blocks * perBlock)
        for (index in 0 until blocks * perBlock) data[(index % blocks) * perBlock + index / blocks] = codewords[index]
        return segments(data.joinToString("") { it.toString(2).padStart(BYTE_BITS, '0') })
    }

    /** The 5 format data bits (level and mask), from the copy around the top-left finder. */
    private fun formatBits(m: List<List<Boolean>>): Int {
        val cells = listOf(0, 1, 2, 3, 4, 5, 7, 8).map { 8 to it } + listOf(7, 5, 4, 3, 2, 1, 0).map { it to 8 }
        val read = cells.fold(0) { acc, (r, c) -> acc shl 1 or (if (m[r][c]) 1 else 0) }
        val best = (0 until FORMATS).minBy { Integer.bitCount(bch(it) xor read) }
        check(bch(best) == read) { "the format bits are damaged" }
        return best
    }

    private fun bch(data: Int): Int {
        var v = data shl BCH_SHIFT
        for (i in FORMAT_DATA_BITS - 1 downTo 0) {
            if (v and (1 shl (i + BCH_SHIFT)) != 0) v = v xor (BCH_GENERATOR shl i)
        }
        return (data shl BCH_SHIFT or v) xor FORMAT_MASK
    }

    /** Every data bit, unmasked, in the order the code is read: up and down two-module columns from the right. */
    private fun dataStream(m: List<List<Boolean>>, version: Int, mask: Int): List<Int> {
        val n = m.size
        val reserved = reserved(n, version)
        val bits = mutableListOf<Int>()
        var upward = true
        var col = n - 1
        while (col > 0) {
            if (col == TIMING) col--
            val rows: IntProgression = if (upward) n - 1 downTo 0 else 0 until n
            for (row in rows) {
                bits += intArrayOf(col, col - 1).filter { !reserved[row][it] }
                    .map { c -> (if (m[row][c]) 1 else 0) xor (if (masked(mask, row, c)) 1 else 0) }
            }
            upward = !upward
            col -= 2
        }
        return bits
    }

    /** The finders, separators, format areas, timing lines and alignment patterns: no data there. */
    private fun reserved(n: Int, version: Int): Array<BooleanArray> {
        val f = Array(n) { BooleanArray(n) }
        fun box(r0: Int, c0: Int, r1: Int, c1: Int) {
            for (r in maxOf(r0, 0)..minOf(r1, n - 1)) for (c in maxOf(c0, 0)..minOf(c1, n - 1)) f[r][c] = true
        }
        box(0, 0, 8, 8)
        box(0, n - 8, 8, n - 1)
        box(n - 8, 0, n - 1, 8)
        for (i in 0 until n) {
            f[TIMING][i] = true
            f[i][TIMING] = true
        }
        val centres = ALIGNMENT.getValue(version)
        for (r in centres) for (c in centres) {
            val onFinder = (r < 9 && c < 9) || (r < 9 && c > n - 10) || (r > n - 10 && c < 9)
            if (!onFinder) box(r - 2, c - 2, r + 2, c + 2)
        }
        if (version >= VERSION_INFO_FROM) {
            box(0, n - 11, 5, n - 9)
            box(n - 11, 0, n - 9, 5)
        }
        return f
    }

    @Suppress("MagicNumber") // the eight mask patterns, as the standard writes them
    private fun masked(mask: Int, i: Int, j: Int): Boolean = when (mask) {
        0 -> (i + j) % 2 == 0
        1 -> i % 2 == 0
        2 -> j % 3 == 0
        3 -> (i + j) % 3 == 0
        4 -> (i / 2 + j / 3) % 2 == 0
        5 -> (i * j) % 2 + (i * j) % 3 == 0
        6 -> ((i * j) % 2 + (i * j) % 3) % 2 == 0
        else -> ((i + j) % 2 + (i * j) % 3) % 2 == 0
    }

    /** Reads a string of bits, so many at a time. */
    private class Bits(private val bits: String) {
        private var position = 0
        val left: Int get() = bits.length - position

        fun take(count: Int): Int = bits.substring(position, position + count).toInt(2).also { position += count }
    }

    /** Numeric, alphanumeric and byte segments, up to the terminator (count fields as for versions 1 to 9). */
    private fun segments(text: String): String = buildString {
        val bits = Bits(text)
        while (bits.left >= MODE_BITS) {
            when (bits.take(MODE_BITS)) {
                0 -> break
                1 -> append(numeric(bits, bits.take(10)))
                2 -> append(alphanumeric(bits, bits.take(9)))
                4 -> append(String(ByteArray(bits.take(8)) { bits.take(8).toByte() }, Charsets.UTF_8))
                else -> error("a segment mode this reader doesn't know")
            }
        }
    }

    /** [count] digits: three to 10 bits, then two to 7 or one to 4. */
    private fun numeric(bits: Bits, count: Int): String = buildString {
        repeat(count / 3) { append(bits.take(10).toString().padStart(3, '0')) }
        when (count % 3) {
            2 -> append(bits.take(7).toString().padStart(2, '0'))
            1 -> append(bits.take(4))
        }
    }

    /** [count] characters of the 45: two to 11 bits, then one to 6. */
    private fun alphanumeric(bits: Bits, count: Int): String = buildString {
        repeat(count / 2) {
            val pair = bits.take(11)
            append(ALPHANUMERIC[pair / 45]).append(ALPHANUMERIC[pair % 45])
        }
        if (count % 2 == 1) append(ALPHANUMERIC[bits.take(6)])
    }

    /** Error correction levels: their format bits and, per version, (blocks, data codewords per block). */
    private enum class Level(val bits: Int, val blocks: Map<Int, Pair<Int, Int>>) {
        L(1, mapOf(1 to (1 to 19), 2 to (1 to 34), 3 to (1 to 55), 4 to (1 to 80), 5 to (1 to 108), 6 to (2 to 68))),
        M(0, mapOf(1 to (1 to 16), 2 to (1 to 28), 3 to (1 to 44), 4 to (2 to 32), 5 to (2 to 43), 6 to (4 to 27))),
        Q(3, mapOf(1 to (1 to 13), 2 to (1 to 22), 3 to (2 to 17), 4 to (2 to 24), 6 to (4 to 19))),
        H(2, mapOf(1 to (1 to 9), 2 to (1 to 16), 3 to (2 to 13), 4 to (4 to 9), 6 to (4 to 15))),
    }

    private val ALIGNMENT = mapOf(
        1 to emptyList(), 2 to listOf(6, 18), 3 to listOf(6, 22), 4 to listOf(6, 26), 5 to listOf(6, 30), 6 to listOf(6, 34),
    )
    private const val ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"
    private const val BASE = 17
    private const val STEP = 4
    private const val MAX_VERSION = 6
    private const val VERSION_INFO_FROM = 7
    private const val TIMING = 6
    private const val FINDER = 7
    private const val CENTRE = 0.5
    private const val EDGE = 3
    private const val FORMATS = 32
    private const val MASK_BITS = 3
    private const val FORMAT_DATA_BITS = 5
    private const val MASK_MASK = 0b111
    private const val BCH_SHIFT = 10
    private const val BCH_GENERATOR = 0x537
    private const val FORMAT_MASK = 0x5412
    private const val MODE_BITS = 4
    private const val BYTE_BITS = 8
    private const val BYTE = 0xFF
    private const val HALF = 128
    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
}
