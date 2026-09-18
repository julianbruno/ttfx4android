package com.ttfx.core

enum class TickStatus {
    Running, Complete
}

data class EffectConfiguration(
    val text: String = "",
    val seed: ULong = 0u,
    val frameRate: Int = 60,
    val initialRNG: Xoshiro256PlusPlus? = null
) {
    init {
        require(frameRate >= 0) { "frame rate must not be negative" }
    }

    val effectiveFrameRate: Int
        get() = if (frameRate == 0) 60 else frameRate

    fun makeRNG(customSeed: ULong = seed): Xoshiro256PlusPlus {
        return initialRNG ?: Xoshiro256PlusPlus(customSeed)
    }
}

typealias EffectConfig = EffectConfiguration

interface Effect {
    fun tick(frame: Frame): TickStatus
}
