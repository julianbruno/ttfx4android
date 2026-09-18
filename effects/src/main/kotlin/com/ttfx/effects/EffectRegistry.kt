package com.ttfx.effects

import com.ttfx.core.Canvas
import com.ttfx.core.Effect
import com.ttfx.core.EffectConfiguration
import com.ttfx.core.InputText

typealias EffectFactory = (EffectConfiguration, Canvas, InputText, ULong) -> Effect

object EffectRegistry {
    val allEffects: List<String> = listOf(
        "beams", "binarypath", "blackhole", "bouncyballs", "bubbles", "burn",
        "colorshift", "crumble", "decrypt", "errorcorrect", "expand", "fireworks",
        "highlight", "laseretch", "matrix", "middleout", "orbittingvolley",
        "overflow", "pour", "print", "rain", "randomsequence", "rings", "scattered",
        "slice", "slide", "smoke", "spotlights", "spray", "swarm", "sweep",
        "synthgrid", "thunderstorm", "unstable", "vhstape", "waves", "wipe"
    )

    val names: List<String>
        get() = allEffects

    private val factories: Map<String, EffectFactory> = mapOf(
        "beams" to { config, canvas, input, seed -> BeamsEffect(config, canvas, input, seed) },
        "binarypath" to { config, canvas, input, seed -> BinaryPathEffect(config, canvas, input, seed) },
        "blackhole" to { config, canvas, input, seed -> BlackHoleEffect(config, canvas, input, seed) },
        "bouncyballs" to { config, canvas, input, seed -> BouncyBallsEffect(config, canvas, input, seed) },
        "bubbles" to { config, canvas, input, seed -> BubblesEffect(config, canvas, input, seed) },
        "burn" to { config, canvas, input, seed -> BurnEffect(config, canvas, input, seed) },
        "colorshift" to { config, canvas, input, seed -> ColorShiftEffect(config, canvas, input, seed) },
        "crumble" to { config, canvas, input, seed -> CrumbleEffect(config, canvas, input, seed) },
        "decrypt" to { config, canvas, input, seed -> DecryptEffect(config, canvas, input, seed) },
        "errorcorrect" to { config, canvas, input, seed -> ErrorCorrectEffect(config, canvas, input, seed) },
        "expand" to { config, canvas, input, seed -> ExpandEffect(config, canvas, input, seed) },
        "fireworks" to { config, canvas, input, seed -> FireworksEffect(config, canvas, input, seed) },
        "highlight" to { config, canvas, input, seed -> HighlightEffect(config, canvas, input, seed) },
        "laseretch" to { config, canvas, input, seed -> LaserEtchEffect(config, canvas, input, seed) },
        "matrix" to { config, canvas, input, seed -> MatrixEffect(config, canvas, input, seed) },
        "middleout" to { config, canvas, input, seed -> MiddleOutEffect(config, canvas, input, seed) },
        "orbittingvolley" to { config, canvas, input, seed -> OrbittingVolleyEffect(config, canvas, input, seed) },
        "overflow" to { config, canvas, input, seed -> OverflowEffect(config, canvas, input, seed) },
        "pour" to { config, canvas, input, seed -> PourEffect(config, canvas, input, seed) },
        "print" to { config, canvas, input, seed -> PrintEffect(config, canvas, input, seed) },
        "rain" to { config, canvas, input, seed -> RainEffect(config, canvas, input, seed) },
        "randomsequence" to { config, canvas, input, seed -> RandomSequenceEffect(config, canvas, input, seed) },
        "rings" to { config, canvas, input, seed -> RingsEffect(config, canvas, input, seed) },
        "scattered" to { config, canvas, input, seed -> ScatteredEffect(config, canvas, input, seed) },
        "slice" to { config, canvas, input, seed -> SliceEffect(config, canvas, input, seed) },
        "slide" to { config, canvas, input, seed -> SlideEffect(config, canvas, input, seed) },
        "smoke" to { config, canvas, input, seed -> SmokeEffect(config, canvas, input, seed) },
        "spotlights" to { config, canvas, input, seed -> SpotlightsEffect(config, canvas, input, seed) },
        "spray" to { config, canvas, input, seed -> SprayEffect(config, canvas, input, seed) },
        "swarm" to { config, canvas, input, seed -> SwarmEffect(config, canvas, input, seed) },
        "sweep" to { config, canvas, input, seed -> SweepEffect(config, canvas, input, seed) },
        "synthgrid" to { config, canvas, input, seed -> SynthGridEffect(config, canvas, input, seed) },
        "thunderstorm" to { config, canvas, input, seed -> ThunderstormEffect(config, canvas, input, seed) },
        "unstable" to { config, canvas, input, seed -> UnstableEffect(config, canvas, input, seed) },
        "vhstape" to { config, canvas, input, seed -> VHSTapeEffect(config, canvas, input, seed) },
        "waves" to { config, canvas, input, seed -> WavesEffect(config, canvas, input, seed) },
        "wipe" to { config, canvas, input, seed -> WipeEffect(config, canvas, input, seed) }
    )

    fun contains(name: String): Boolean {
        return factories.containsKey(name.lowercase())
    }

    fun makeEffect(
        name: String,
        configuration: EffectConfiguration,
        canvas: Canvas,
        input: InputText,
        seed: ULong
    ): Effect? {
        return factories[name.lowercase()]?.invoke(configuration, canvas, input, seed)
    }

    fun create(
        name: String,
        configuration: EffectConfiguration,
        canvas: Canvas,
        input: InputText,
        seed: ULong
    ): Effect? {
        return makeEffect(name, configuration, canvas, input, seed)
    }
}
