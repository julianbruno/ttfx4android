package com.ttfx.effects

import com.ttfx.core.*
import kotlin.math.floor
import kotlin.math.pow

class ThunderstormEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    data class Configuration(
        val lightningColor: Color = Color("68A3E8"),
        val glowingTextColor: Color = Color("EF5411"),
        val textGlowTime: Int = 6,
        val raindropSymbols: List<String> = listOf("\\", ".", ","),
        val sparkSymbols: List<String> = listOf("*", ".", "'"),
        val sparkGlowColor: Color = Color("ff4d00"),
        val sparkGlowTime: Int = 18,
        val stormTime: Int = 12,
        val finalGradientStops: List<Color> = listOf(Color("8A008A"), Color("00D1FF"), Color("FFFFFF")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientFrames: Int = 3,
        val finalGradientDirection: GradientDirection = GradientDirection.Vertical
    ) {
        init {
            require(textGlowTime > 0) { "text glow time must be positive" }
            require(raindropSymbols.isNotEmpty()) { "raindrop symbols must not be empty" }
            require(sparkSymbols.isNotEmpty()) { "spark symbols must not be empty" }
            require(sparkGlowTime > 0) { "spark glow time must be positive" }
            require(stormTime > 0) { "storm time must be positive" }
            require(finalGradientStops.isNotEmpty()) { "final gradient stops must not be empty" }
            require(finalGradientFrames > 0) { "final gradient frames must be positive" }
        }
    }

    private companion object {
        private const val EXPLICIT_BLACK_FOREGROUND_SENTINEL: UInt = 0xFFFF_FFFEu

        private fun gradient(from: Color, to: Color, steps: Int, duration: Int, loop: Boolean = false): List<UInt> {
            val grad = Gradient(stops = listOf(from, to), steps = steps, loop = loop)
            return grad.spectrum.flatMap { col -> List(duration) { col.asUInt } }
        }

        private fun ttfxCubicBezier(x: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
            fun sample(t: Double, a: Double, b: Double): Double =
                3.0 * a * (1.0 - t).pow(2) * t + 3.0 * b * (1.0 - t) * t * t + t * t * t
            var lower = 0.0
            var upper = 1.0
            for (i in 0 until 30) {
                val t = (lower + upper) / 2.0
                if (sample(t, x1, x2) < x) lower = t else upper = t
            }
            return sample((lower + upper) / 2.0, y1, y2)
        }
    }

    private enum class Phase { PreStorm, Waiting, Storm, Complete }
    private enum class Kind { Text, Rain, Spark, Strike }

    private class Scene(
        var colors: List<UInt>,
        var age: Int = 0,
        var easing: ((Double) -> Double)? = null
    )

    private class Motion(
        val origin: Coordinate,
        val target: Coordinate,
        val control: Coordinate? = null,
        speed: Double,
        val easing: Easing = Easing.Linear,
        var hold: Int = 0
    ) {
        val distance: Double = if (control != null) {
            Geometry.bezierLength(from = origin, controls = listOf(control), to = target)
        } else {
            Geometry.lineLength(from = origin, to = target)
        }
        val steps: Int = PyCompat.roundHalfEven(distance / speed)
        var step: Int = 0

        fun advance(): Pair<Coordinate, Boolean> {
            var coordinate = target
            if (steps > 0 && step < steps && distance > 0.0) {
                step += 1
                val t = (easing.value(step.toDouble() / steps.toDouble()) * distance) / distance
                coordinate = if (control != null) {
                    Geometry.coordinateOnBezier(from = origin, controls = listOf(control), to = target, t = t)
                } else {
                    Geometry.coordinateOnLine(from = origin, to = target, t = t)
                }
            }
            if (step == steps) {
                if (hold != 0) {
                    hold -= 1
                    return coordinate to false
                }
                return coordinate to true
            }
            return coordinate to false
        }
    }

    private class Glyph(
        val kind: Kind,
        val symbol: Int,
        var displaySymbol: Int,
        var coordinate: Coordinate,
        var foreground: UInt,
        var layer: Int,
        var visible: Boolean = false,
        var active: Boolean = false,
        var motion: Motion? = null,
        val scenes: HashMap<String, Scene> = HashMap(),
        var scene: String? = null,
        var lastStrike: Boolean = false
    )

    private val frameDuration: Double = 1.0 / configuration.effectiveFrameRate.toDouble()
    private var rng: Xoshiro256PlusPlus = configuration.makeRNG(seed)
    private var glyphs = ArrayList<Glyph>()
    private var inputCount = 0
    private var rainPool = ArrayList<Int>()
    private var sparkPool = ArrayList<Int>()
    private var strikePool = ArrayList<Int>()
    private var sparkCount = 0
    private var pendingStrikes = ArrayList<Int>()
    private var revealedStrikes = ArrayList<Int>()
    private var pendingGlow = ArrayList<Int>()
    private var strikeInProgress = false
    private var branchChance = 0.05
    private var strikeDelay = 0
    private var rainDelay = 0
    private var phase = Phase.PreStorm
    private var elapsed = 0.0
    private var stormStart = 0.0
    private var sparkColors = ArrayList<UInt>()

    init {
        build(input)
    }

    private fun build(input: InputText) {
        if (input.scalars.isEmpty()) {
            phase = Phase.Complete
            return
        }
        val coordinates = input.positions.map { Coordinate(column = it.column, row = it.row) }
        inputCount = input.scalars.size
        glyphs = ArrayList(coordinates.indices.map {
            Glyph(
                kind = Kind.Text,
                symbol = input.scalars[it],
                displaySymbol = input.scalars[it],
                coordinate = coordinates[it],
                foreground = 0u,
                layer = 0,
                visible = true
            )
        })

        for (i in 0 until 50) {
            rainPool.add(makeParticle(Kind.Rain))
        }
        sparkColors = ArrayList(gradient(options.sparkGlowColor, Color("000000"), steps = 7, duration = options.sparkGlowTime))
        for (i in 0 until 200) {
            sparkPool.add(makeParticle(Kind.Spark))
        }
        for (i in 0 until 200) {
            strikePool.add(makeParticle(Kind.Strike))
        }

        val gradient = Gradient(stops = options.finalGradientStops, steps = options.finalGradientSteps)
        val mapping = gradient.coordinateColorMapping(
            minRow = coordinates.minOf { it.row },
            maxRow = coordinates.maxOf { it.row },
            minColumn = coordinates.minOf { it.column },
            maxColumn = coordinates.maxOf { it.column },
            direction = options.finalGradientDirection
        ).entries.associate { it.coordinate to it.color }

        for (id in 0 until inputCount) {
            val visible = mapping[coordinates[id]] ?: options.finalGradientStops.last()
            val storm = visible.adjustBrightness(0.5)
            val fade = gradient(visible, storm, steps = 7, duration = 12)
            glyphs[id].scenes["fade"] = Scene(colors = fade)
            glyphs[id].scenes["unfade"] = Scene(colors = fade.reversed())
            glyphs[id].scenes["glow"] = Scene(colors = gradient(options.glowingTextColor, storm, steps = 7, duration = options.textGlowTime))
            glyphs[id].scenes["flash"] = Scene(colors = gradient(storm, visible.adjustBrightness(1.7), steps = 7, duration = 6, loop = true))
        }
    }

    private fun makeParticle(kind: Kind): Int {
        val symbols: List<String> = when (kind) {
            Kind.Rain -> options.raindropSymbols
            Kind.Spark -> options.sparkSymbols
            Kind.Strike, Kind.Text -> listOf("|")
        }
        val layer = when (kind) {
            Kind.Rain -> 1
            Kind.Spark -> 2
            Kind.Strike, Kind.Text -> 0
        }
        val symbol = if (kind == Kind.Strike) 124 else {
            val str = symbols[rng.integer(0 until symbols.size)]
            if (str.isNotEmpty()) str.codePointAt(0) else 32
        }
        val id = glyphs.size
        val glyph = Glyph(
            kind = kind,
            symbol = symbol,
            displaySymbol = symbol,
            coordinate = Coordinate(0, 0),
            foreground = if (kind == Kind.Rain) 0xaaaaffu else 0u,
            layer = layer
        )
        if (kind == Kind.Spark) {
            glyph.scenes["glow"] = Scene(colors = sparkColors, easing = { Easing.InCirc.value(it) })
            sparkCount += 1
        }
        glyphs.add(glyph)
        return id
    }

    private fun activateScene(id: Int, name: String) {
        glyphs[id].scene = name
        val scene = glyphs[id].scenes[name] ?: return
        glyphs[id].foreground = scene.colors[if (scene.easing == null) scene.age else 0]
    }

    private fun rain() {
        if (rainDelay != 0) {
            rainDelay -= 1
            return
        }
        val count = rng.integer(1..6)
        for (i in 0 until count) {
            val column = rng.integer((1 - canvas.rows)..canvas.columns)
            val origin = Coordinate(column = column - 1, row = canvas.rows + 1)
            val id = if (rainPool.isNotEmpty()) rainPool.removeAt(rainPool.size - 1) else makeParticle(Kind.Rain)
            val speed = rng.uniform(0.5, 1.5)
            glyphs[id].coordinate = origin
            glyphs[id].visible = true
            glyphs[id].active = true
            glyphs[id].motion = Motion(origin = origin, target = Coordinate(column = origin.column + canvas.rows + 1, row = 0), speed = speed)
            glyphs[id].scene = null
        }
        rainDelay = rng.integer(1..7)
    }

    private fun setupStrike(neighbor: Int? = null) {
        var n = neighbor
        var column = n?.let { glyphs[it].coordinate.column } ?: rng.integer(1..canvas.columns)
        var row = n?.let { glyphs[it].coordinate.row } ?: canvas.rows
        while (row >= 1) {
            val symbol: Int
            if (n != null) {
                val delta = if (rng.integer(0..1) == 0) -1 else 1
                column += delta
                symbol = if (delta == 1) 92 else 47
            } else {
                symbol = listOf(92, 47, 124)[rng.integer(0..2)]
            }
            if (strikePool.isEmpty()) {
                for (i in 0 until 20) {
                    strikePool.add(makeParticle(Kind.Strike))
                }
            }
            val id = strikePool.removeAt(strikePool.size - 1)
            glyphs[id].scenes.clear()
            glyphs[id].scene = null
            glyphs[id].lastStrike = false
            glyphs[id].coordinate = Coordinate(column = column, row = row)
            glyphs[id].displaySymbol = symbol
            glyphs[id].foreground = options.lightningColor.asUInt
            row -= 1
            if (symbol == 92) column += 1 else if (symbol == 47) column -= 1
            pendingStrikes.add(id)
            if (rng.random() < branchChance && n == null) {
                branchChance -= 0.01
                setupStrike(neighbor = id)
            }
            n = null
        }
        branchChance = 0.05
    }

    private fun lightning() {
        setupStrike()
        val flash = gradient(options.lightningColor, options.lightningColor.adjustBrightness(1.7), steps = 7, duration = 6, loop = true)
        val fade = gradient(options.lightningColor, Color("000000"), steps = 6, duration = 2)
        val y2 = rng.uniform(-0.6, 0.4)
        val easingFn: (Double) -> Double = { x -> ttfxCubicBezier(x, 0.0, 1.6, 1.0, y2) }
        for (id in pendingStrikes) {
            glyphs[id].scenes["flash"] = Scene(colors = flash, easing = easingFn)
            glyphs[id].scenes["fade"] = Scene(colors = fade)
            glyphs[id].layer = 1
        }
        for (id in 0 until inputCount) {
            glyphs[id].scenes["flash"]?.easing = easingFn
        }
    }

    private fun emitSparks(origin: Coordinate) {
        val count = rng.integer(12..18)
        for (i in 0 until count) {
            if (sparkPool.isEmpty() && sparkCount >= 2000) continue
            val id = if (sparkPool.isNotEmpty()) sparkPool.removeAt(sparkPool.size - 1) else makeParticle(Kind.Spark)
            val speed = rng.uniform(0.1, 0.25)
            val sign = if (rng.integer(0..1) == 0) 1 else -1
            val offset = rng.integer(4..20) * sign
            val target = Coordinate(column = origin.column + offset, row = 1)
            val control = Coordinate(
                column = origin.column - floor((origin.column - target.column).toDouble() / 2.0).toInt(),
                row = rng.integer(1..canvas.rows)
            )
            glyphs[id].coordinate = origin
            glyphs[id].motion = Motion(origin = origin, target = target, control = control, speed = speed, easing = Easing.OutQuint, hold = 30)
            activateScene(id, "glow")
            glyphs[id].visible = true
            glyphs[id].active = true
        }
    }

    private fun stepStrike() {
        if (strikeDelay != 0) {
            strikeDelay -= 1
            return
        }
        if (pendingStrikes.isNotEmpty()) {
            val count = rng.integer(1..3)
            for (i in 0 until count) {
                if (pendingStrikes.isEmpty()) break
                val id = pendingStrikes.removeAt(0)
                revealedStrikes.add(id)
                glyphs[id].visible = true
                strikeDelay = 1
                if (pendingStrikes.isEmpty()) {
                    emitSparks(glyphs[id].coordinate)
                    glyphs[id].lastStrike = true
                    for (strike in revealedStrikes) {
                        activateScene(strike, "flash")
                        glyphs[strike].active = true
                    }
                    revealedStrikes.clear()
                    for (text in 0 until inputCount) {
                        activateScene(text, "flash")
                        glyphs[text].active = true
                    }
                }
            }
        }
    }

    private fun sceneComplete(id: Int, name: String) {
        when (glyphs[id].kind) {
            Kind.Text -> {
                if (id == 0 && name == "fade") {
                    phase = Phase.Storm
                    stormStart = elapsed
                }
            }
            Kind.Rain -> {}
            Kind.Spark -> {
                glyphs[id].visible = false
                glyphs[id].motion = null
                glyphs[id].scene = null
                glyphs[id].active = false
                sparkPool.add(id)
            }
            Kind.Strike -> {
                if (name == "flash") {
                    activateScene(id, "fade")
                } else if (name == "fade") {
                    glyphs[id].visible = false
                    val textId = (0 until inputCount).firstOrNull { glyphs[it].coordinate == glyphs[id].coordinate }
                    if (textId != null) {
                        activateScene(textId, "glow")
                        pendingGlow.add(textId)
                    }
                    strikePool.add(id)
                    if (glyphs[id].lastStrike) {
                        strikeInProgress = false
                    }
                }
            }
        }
    }

    override fun tick(frame: Frame): TickStatus {
        for (i in frame.cells.indices) {
            frame.cells[i] = Cell.BLANK
        }

        if (phase == Phase.Complete && glyphs.none { it.active }) return TickStatus.Complete

        when (phase) {
            Phase.PreStorm -> {
                for (id in 0 until inputCount) {
                    activateScene(id, "fade")
                    glyphs[id].active = true
                }
                phase = Phase.Waiting
            }
            Phase.Storm -> {
                rain()
                if (!strikeInProgress && rng.random() < 0.008) {
                    strikeInProgress = true
                    lightning()
                }
                if (strikeInProgress) {
                    stepStrike()
                }
                for (id in pendingGlow) {
                    glyphs[id].active = true
                }
                pendingGlow.clear()
                if (elapsed - stormStart >= options.stormTime.toDouble() && !strikeInProgress) {
                    for (id in 0 until inputCount) {
                        activateScene(id, "unfade")
                        glyphs[id].active = true
                    }
                    phase = Phase.Complete
                }
            }
            Phase.Waiting, Phase.Complete -> {}
        }

        val active = glyphs.indices.filter { glyphs[it].active }
        for (id in active) {
            val motion = glyphs[id].motion
            if (motion != null) {
                val (coordinate, complete) = motion.advance()
                glyphs[id].coordinate = coordinate
                glyphs[id].motion = if (complete) null else motion
                if (complete && glyphs[id].kind == Kind.Rain) {
                    glyphs[id].visible = false
                    glyphs[id].active = false
                    rainPool.add(id)
                }
            }

            val name = glyphs[id].scene
            if (name != null) {
                val scene = glyphs[id].scenes[name]
                if (scene != null) {
                    val index: Int = if (scene.easing != null) {
                        val progress = scene.age.toDouble() / scene.colors.size.toDouble()
                        val easedVal = scene.easing!!.invoke(progress)
                        minOf(scene.colors.size - 1, maxOf(0, PyCompat.roundHalfEven(easedVal * (scene.colors.size - 1).toDouble())))
                    } else {
                        scene.age
                    }
                    glyphs[id].foreground = scene.colors[index]
                    scene.age += 1
                    val isDone = scene.age == scene.colors.size
                    if (isDone) {
                        scene.age = 0
                        glyphs[id].scene = null
                    }
                    if (isDone) {
                        sceneComplete(id, name)
                    }
                }
            }
            glyphs[id].active = glyphs[id].motion != null || glyphs[id].scene != null
        }

        val ordered = glyphs.indices.filter { glyphs[it].visible }.sortedWith { i1, i2 ->
            if (glyphs[i1].layer == glyphs[i2].layer) i1.compareTo(i2) else glyphs[i1].layer.compareTo(glyphs[i2].layer)
        }
        for (id in ordered) {
            val glyph = glyphs[id]
            if (glyph.coordinate.column in 1..canvas.columns && glyph.coordinate.row in 1..canvas.rows) {
                frame[glyph.coordinate.column, glyph.coordinate.row] = Cell(
                    codepoint = glyph.displaySymbol,
                    foreground = glyph.foreground,
                    background = if (glyph.foreground == 0u) EXPLICIT_BLACK_FOREGROUND_SENTINEL else 0u
                )
            }
        }

        elapsed += frameDuration
        return if (phase == Phase.Complete && glyphs.none { it.active }) TickStatus.Complete else TickStatus.Running
    }
}
