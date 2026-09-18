package com.ttfx.core

class Xoshiro256PlusPlus(seed: ULong) {
    private var s0: ULong
    private var s1: ULong
    private var s2: ULong
    private var s3: ULong

    init {
        var splitMix = seed
        fun nextSplitMix(): ULong {
            splitMix += 0x9E3779B97F4A7C15UL
            var z = splitMix
            z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9UL
            z = (z xor (z shr 27)) * 0x94D049BB133111EBUL
            return z xor (z shr 31)
        }
        s0 = nextSplitMix()
        s1 = nextSplitMix()
        s2 = nextSplitMix()
        s3 = nextSplitMix()
    }

    private fun rotateLeft(x: ULong, k: Int): ULong {
        return (x shl k) or (x shr (64 - k))
    }

    fun nextULong(): ULong {
        val result = rotateLeft(s0 + s3, 23) + s0
        val t = s1 shl 17

        s2 = s2 xor s0
        s3 = s3 xor s1
        s1 = s1 xor s2
        s0 = s0 xor s3

        s2 = s2 xor t
        s3 = rotateLeft(s3, 45)

        return result
    }

    fun random(): Double {
        return (nextULong() shr 11).toDouble() * (1.0 / (1L shl 53).toDouble())
    }

    fun integer(range: IntRange): Int {
        require(range.first <= range.last) { "empty integer range" }
        val count = (range.last - range.first + 1).toULong()
        return range.first + randomBelow(count).toInt()
    }

    fun uniform(lower: Double, upper: Double): Double {
        return lower + (upper - lower) * random()
    }

    fun <T> shuffle(list: MutableList<T>) {
        if (list.size <= 1) return
        for (i in list.size - 1 downTo 1) {
            val j = randomBelow((i + 1).toULong()).toInt()
            val tmp = list[i]
            list[i] = list[j]
            list[j] = tmp
        }
    }

    private fun randomBelow(upperBound: ULong): ULong {
        require(upperBound > 0u) { "randomBelow(0)" }
        val bitCount = 64 - (upperBound - 1u).countLeadingZeroBits()
        while (true) {
            val shift = 64 - maxOf(bitCount, 1)
            val value = nextULong() shr shift
            if (value < upperBound) return value
        }
    }
}
