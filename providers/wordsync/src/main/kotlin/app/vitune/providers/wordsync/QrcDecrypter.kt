package app.vitune.providers.wordsync

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * Opens QQ Music's QRC (word-timed) lyric files: a non-standard Triple-DES followed by zlib.
 *
 * Ported from the GPL-3.0 QQMusicDecoder (github.com/WXRIW/QQMusicDecoder) via
 * qqmusic-api-python; the S-boxes and key schedule deliberately keep QQ's quirks.
 */
internal object QrcDecrypter {
    private val KEY = "!@#)(*$%123ZXC!@!@#)(NHL".toByteArray(Charsets.US_ASCII)
    private const val ENCRYPT = 1
    private const val DECRYPT = 0

    private val schedule by lazy {
        arrayOf(
            keySchedule(KEY.copyOfRange(16, 24), DECRYPT),
            keySchedule(KEY.copyOfRange(8, 16), ENCRYPT),
            keySchedule(KEY.copyOfRange(0, 8), DECRYPT)
        )
    }

    fun decrypt(hex: String): String {
        val data = hexToBytes(hex.trim())
        val out = ByteArray(data.size - data.size % 8)
        var i = 0
        while (i + 8 <= data.size) {
            var block = data.copyOfRange(i, i + 8)
            for (k in schedule) block = crypt(block, k)
            block.copyInto(out, i)
            i += 8
        }
        return inflate(out).toString(Charsets.UTF_8)
    }

    private fun hexToBytes(hex: String) = ByteArray(hex.length / 2) {
        ((Character.digit(hex[it * 2], 16) shl 4) or Character.digit(hex[it * 2 + 1], 16)).toByte()
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val out = ByteArrayOutputStream(data.size * 4)
        val buffer = ByteArray(8192)
        try {
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buffer, 0, n)
            }
        } finally {
            inflater.end()
        }
        return out.toByteArray()
    }

    private val SBOX = arrayOf(
        intArrayOf(14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7, 0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8, 4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0, 15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13),
        intArrayOf(15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10, 3, 13, 4, 7, 15, 2, 8, 15, 12, 0, 1, 10, 6, 9, 11, 5, 0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15, 13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9),
        intArrayOf(10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8, 13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1, 13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7, 1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12),
        intArrayOf(7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15, 13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9, 10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4, 3, 15, 0, 6, 10, 10, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14),
        intArrayOf(2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9, 14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6, 4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14, 11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3),
        intArrayOf(12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11, 10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8, 9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6, 4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13),
        intArrayOf(4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1, 13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6, 1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2, 6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12),
        intArrayOf(13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7, 1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2, 7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8, 2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11)
    )

    // Source bit for each output bit of the P permutation, from bit 31 down to bit 0.
    private val P = intArrayOf(
        16, 25, 12, 11, 3, 20, 4, 15, 31, 17, 9, 6, 27, 14, 1, 22,
        30, 24, 8, 18, 0, 5, 29, 23, 13, 19, 2, 26, 10, 21, 28, 7
    )

    private fun bit(v: Long, n: Int) = (v ushr n) and 1L

    private fun sboxBit(a: Int) = (a and 32) or ((a and 31) shr 1) or ((a and 1) shl 4)

    private fun initialPermutation(input: ByteArray): Pair<Long, Long> {
        fun u(i: Int) = (input[i].toLong() and 0xFF)
        val v0 = u(0) or (u(1) shl 8) or (u(2) shl 16) or (u(3) shl 24)
        val v1 = u(4) or (u(5) shl 8) or (u(6) shl 16) or (u(7) shl 24)

        fun half(bases: IntArray): Long {
            var out = 0L
            var pos = 31
            for (b in bases) for (v in longArrayOf(v1, v0)) for (k in intArrayOf(0, 8, 16, 24)) {
                out = out or (bit(v, b + k) shl pos)
                pos--
            }
            return out
        }
        return half(intArrayOf(6, 4, 2, 0)) to half(intArrayOf(7, 5, 3, 1))
    }

    private fun inversePermutation(s0: Long, s1: Long): ByteArray {
        val data = ByteArray(8)
        val order = intArrayOf(3, 2, 1, 0, 7, 6, 5, 4)
        for (i in 0 until 8) {
            var b = 0L
            var pos = 7
            for (k in intArrayOf(24, 16, 8, 0)) {
                b = b or (bit(s1, k + i) shl pos--)
                b = b or (bit(s0, k + i) shl pos--)
            }
            data[order[i]] = b.toByte()
        }
        return data
    }

    private fun f(state: Long, key: IntArray): Long {
        val t1 = ((state and 1L) shl 31) or
            ((state and 0xF8000000L) ushr 1) or
            ((state and 0x1F800000L) ushr 3) or
            ((state and 0x01F80000L) ushr 5) or
            ((state and 0x001F8000L) ushr 7)
        val t2 = ((state and 0x0001F800L) shl 15) or
            ((state and 0x00001F80L) shl 13) or
            ((state and 0x000001F8L) shl 11) or
            ((state and 0x0000001FL) shl 9) or
            ((state and 0x80000000L) ushr 23)

        val k0 = ((t1 ushr 24) and 0xFF).toInt() xor key[0]
        val k1 = ((t1 ushr 16) and 0xFF).toInt() xor key[1]
        val k2 = ((t1 ushr 8) and 0xFF).toInt() xor key[2]
        val k3 = ((t2 ushr 24) and 0xFF).toInt() xor key[3]
        val k4 = ((t2 ushr 16) and 0xFF).toInt() xor key[4]
        val k5 = ((t2 ushr 8) and 0xFF).toInt() xor key[5]

        val s = (SBOX[0][sboxBit(k0 shr 2)].toLong() shl 28) or
            (SBOX[1][sboxBit(((k0 and 0x03) shl 4) or (k1 shr 4))].toLong() shl 24) or
            (SBOX[2][sboxBit(((k1 and 0x0F) shl 2) or (k2 shr 6))].toLong() shl 20) or
            (SBOX[3][sboxBit(k2 and 0x3F)].toLong() shl 16) or
            (SBOX[4][sboxBit(k3 shr 2)].toLong() shl 12) or
            (SBOX[5][sboxBit(((k3 and 0x03) shl 4) or (k4 shr 4))].toLong() shl 8) or
            (SBOX[6][sboxBit(((k4 and 0x0F) shl 2) or (k5 shr 6))].toLong() shl 4) or
            SBOX[7][sboxBit(k5 and 0x3F)].toLong()

        var out = 0L
        for (i in P.indices) out = out or (bit(s, P[i]) shl (31 - i))
        return out
    }

    private fun crypt(input: ByteArray, key: Array<IntArray>): ByteArray {
        var (s0, s1) = initialPermutation(input)
        for (idx in 0 until 15) {
            val previous = s1
            s1 = f(s1, key[idx]) xor s0
            s0 = previous
        }
        s0 = f(s1, key[15]) xor s0
        return inversePermutation(s0, s1)
    }

    private val ROUND_SHIFT = intArrayOf(1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1)
    private val PERM_C = intArrayOf(
        56, 48, 40, 32, 24, 16, 8, 0, 57, 49, 41, 33, 25, 17, 9, 1,
        58, 50, 42, 34, 26, 18, 10, 2, 59, 51, 43, 35
    )
    private val PERM_D = intArrayOf(
        62, 54, 46, 38, 30, 22, 14, 6, 61, 53, 45, 37, 29, 21, 13, 5,
        60, 52, 44, 36, 28, 20, 12, 4, 27, 19, 11, 3
    )
    private val COMPRESSION = intArrayOf(
        13, 16, 10, 23, 0, 4, 2, 27, 14, 5, 20, 9, 22, 18, 11, 3,
        25, 7, 15, 6, 26, 19, 12, 1, 40, 51, 30, 36, 46, 54, 29, 39,
        50, 44, 32, 47, 43, 48, 38, 55, 33, 52, 45, 41, 49, 35, 28, 31
    )

    private fun keySchedule(key: ByteArray, mode: Int): Array<IntArray> {
        val schedule = Array(16) { IntArray(6) }
        fun u(i: Int) = (key[i].toLong() and 0xFF)
        val v0 = u(0) or (u(1) shl 8) or (u(2) shl 16) or (u(3) shl 24)
        val v1 = u(4) or (u(5) shl 8) or (u(6) shl 16) or (u(7) shl 24)

        fun pick(table: IntArray): Long {
            var out = 0L
            for ((i, b) in table.withIndex()) {
                val bit = if (b < 32) bit(v0, 31 - b) else bit(v1, 63 - b)
                out = out or (bit shl (31 - i))
            }
            return out
        }

        var c = pick(PERM_C)
        var d = pick(PERM_D)

        for (i in 0 until 16) {
            val shift = ROUND_SHIFT[i]
            c = ((c shl shift) or (c ushr (28 - shift))) and 0xFFFFFFF0L
            d = ((d shl shift) or (d ushr (28 - shift))) and 0xFFFFFFF0L

            val round = if (mode == DECRYPT) 15 - i else i
            val sub = schedule[round]
            sub.fill(0)
            for (j in 0 until 24) {
                val b = bit(c, 31 - COMPRESSION[j]).toInt()
                sub[j / 8] = sub[j / 8] or (b shl (7 - j % 8))
            }
            for (j in 24 until 48) {
                val b = bit(d, 31 - (COMPRESSION[j] - 27)).toInt()
                sub[j / 8] = sub[j / 8] or (b shl (7 - j % 8))
            }
        }
        return schedule
    }
}
