package com.ttfx.effects

import com.ttfx.core.*

class RingsEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    data class Configuration(
        val ringColors: List<Color> = listOf(Color("ab48ff"), Color("e7b2b2"), Color("fffebd")),
        val ringGap: Double = 0.1,
        val spinDuration: Int = 200,
        val spinSpeed: ClosedRange<Double> = 0.25..1.0,
        val disperseDuration: Int = 200,
        val spinDisperseCycles: Int = 3,
        val finalGradientStops: List<Color> = listOf(Color("ab48ff"), Color("e7b2b2"), Color("fffebd")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientDirection: GradientDirection = GradientDirection.Vertical
    ) {
        init {
            require(ringGap > 0.0) { "ring gap must be positive" }
            require(spinDuration > 0) { "spin duration must be positive" }
            require(spinSpeed.start > 0.0) { "spin speed range must be positive" }
            require(disperseDuration > 0) { "disperse duration must be positive" }
            require(spinDisperseCycles > 0) { "spin disperse cycles must be positive" }
        }
    }

    private enum class Phase { Start, Disperse, Spin, Final, Complete }

    private sealed interface MotionKind {
        data class Ring(val index: Int) : MotionKind
        data object InitialDisperse : MotionKind
        data object Disperse : MotionKind
        data object Condense : MotionKind
        data object Home : MotionKind
        data object External : MotionKind
    }

    private class Motion(
        val kind: MotionKind,
        origin: Coordinate,
        targets: List<Coordinate>,
        val speed: Double,
        val easing: Easing = Easing.Linear
    ) {
        val points = listOf(origin) + targets
        val distances = (0 until points.size - 1).map { Geometry.lineLength(points[it], points[it + 1]) }
        val total = distances.sum()
        val steps = PyCompat.roundHalfEven(total / speed)
        val easingFn = easing
        var step = 0

        fun advance(): Coordinate {
            if (steps <= 0 || total <= 0.0) return points.last()
            step += 1
            var distance = easingFn.value(step.toDouble() / steps.toDouble()) * total
            for (index in distances.indices) {
                if (distance <= distances[index]) {
                    val fraction = if (distances[index] == 0.0) 0.0 else distance / distances[index]
                    return Geometry.coordinateOnLine(points[index], points[index + 1], fraction)
                }
                distance -= distances[index]
            }
            return points.last()
        }

        val complete: Boolean
            get() = step >= steps || total == 0.0
    }

    private class Glyph(
        val symbol: Int,
        val input: Coordinate,
        val final: Color,
        var coordinate: Coordinate,
        var foreground: UInt,
        var visible: Boolean = true,
        var rotation: List<Coordinate> = emptyList(),
        var speed: Double = 0.0,
        var lastRingPath: Int = 0,
        var disperseTargets: List<Coordinate> = emptyList(),
        var spinScene: List<UInt> = emptyList(),
        var disperseScene: List<UInt> = emptyList(),
        var scene: List<UInt> = emptyList(),
        var age: Int = 0,
        var spinAge: Int = 0,
        var disperseAge: Int = 0,
        var sceneIsSpin: Boolean? = null,
        var motion: Motion? = null,
        var external: Coordinate? = null
    ) {
        val active: Boolean
            get() = motion != null || age < scene.size
    }

    private var rng: Xoshiro256PlusPlus = configuration.makeRNG(seed)
    private var glyphs = ArrayList<Glyph>()
    private var rings = ArrayList<List<Int>>()
    private var phase = Phase.Start
    private var initialRemaining = 100
    private var firstDisperse = true
    private var spinRemaining: Int = options.spinDuration
    private var disperseRemaining: Int = options.disperseDuration
    private var cyclesRemaining: Int = options.spinDisperseCycles
    private var gap = 1

    init {
        build(input)
    }

    private fun build(input: InputText) {
        if (input.scalars.isEmpty()) {
            phase = Phase.Complete
            return
        }
        gap = maxOf(1, PyCompat.roundHalfEven(minOf(canvas.columns, canvas.rows).toDouble() * options.ringGap))
        val coordinates = input.positions.map { Coordinate(it.column, it.row) }
        val gradient = Gradient(options.finalGradientStops, options.finalGradientSteps)
        val mapping = gradient.coordinateColorMapping(
            minRow = coordinates.minOf { it.row },
            maxRow = coordinates.maxOf { it.row },
            minColumn = coordinates.minOf { it.column },
            maxColumn = coordinates.maxOf { it.column },
            direction = options.finalGradientDirection
        ).entries.associate { it.coordinate to it.color }

        glyphs = ArrayList(coordinates.indices.map {
            Glyph(
                symbol = input.scalars[it],
                input = coordinates[it],
                final = mapping[coordinates[it]]!!,
                coordinate = coordinates[it],
                foreground = mapping[coordinates[it]]!!.asUInt
            )
        })

        val pending = glyphs.indices.toMutableList()
        rng.shuffle(pending)
        val center = Coordinate(column = maxOf(1, (canvas.columns + 1) / 2), row = maxOf(1, (canvas.rows + 1) / 2))

        data class RingGeometry(val points: List<Coordinate>, val speed: Double, val color: Color)
        val geometry = ArrayList<RingGeometry>()
        val maxDim = maxOf(canvas.columns, canvas.rows)
        var radius = 1
        while (radius < maxDim) {
            val points = Geometry.coordinatesOnCircle(origin = center, radius = radius, limit = 7 * radius)
            val inside = points.count { it.column in 1..canvas.columns && it.row in 1..canvas.rows }
            if (points.isNotEmpty() && inside.toDouble() / points.size.toDouble() < 0.25) break
            val speed = rng.uniform(options.spinSpeed.start, options.spinSpeed.endInclusive)
            geometry.add(RingGeometry(points, speed, options.ringColors[geometry.size % options.ringColors.size]))
            radius += gap
        }

        for ((ringIndex, value) in geometry.withIndex()) {
            val (points, speed, color) = value
            val direction = if (ringIndex % 2 == 0) points else points.reversed()
            val assigned = ArrayList<Int>()
            for (offset in direction.indices) {
                if (pending.isEmpty()) break
                val id = pending.removeAt(0)
                glyphs[id].rotation = direction.subList(offset, direction.size) + direction.subList(0, offset)
                glyphs[id].speed = speed
                glyphs[id].spinScene = Gradient(stops = listOf(glyphs[id].final, color), steps = 8).spectrum.flatMap { c -> List(3) { c.asUInt } }
                glyphs[id].disperseScene = Gradient(stops = listOf(color, glyphs[id].final), steps = 8).spectrum.flatMap { c -> List(10) { c.asUInt } }
                assigned.add(id)
            }
            rings.add(assigned)
        }

        for (id in glyphs.indices) {
            if (glyphs[id].rotation.isEmpty()) {
                glyphs[id].external = randomOutside()
            }
        }
    }

    private fun move(id: Int, kind: MotionKind, targets: List<Coordinate>, speed: Double, easing: Easing = Easing.Linear) {
        glyphs[id].motion = Motion(kind = kind, origin = glyphs[id].coordinate, targets = targets, speed = speed, easing = easing)
    }

    private fun scene(id: Int, spin: Boolean) {
        val previous = glyphs[id].sceneIsSpin
        if (previous != null) {
            val cursor = if (glyphs[id].age < glyphs[id].scene.size) glyphs[id].age else 0
            if (previous) glyphs[id].spinAge = cursor else glyphs[id].disperseAge = cursor
        }
        glyphs[id].scene = if (spin) glyphs[id].spinScene else glyphs[id].disperseScene
        glyphs[id].age = if (spin) glyphs[id].spinAge else glyphs[id].disperseAge
        glyphs[id].sceneIsSpin = spin
        if (glyphs[id].scene.isNotEmpty()) {
            glyphs[id].foreground = glyphs[id].scene[glyphs[id].age]
        }
    }

    private fun disperse(id: Int, initial: Boolean) {
        val origin = if (initial) glyphs[id].rotation[0] else glyphs[id].coordinate
        val choices = Geometry.coordinatesInRectangle(center = origin, distance = gap)
        glyphs[id].disperseTargets = (0 until 5).map { choices[rng.integer(0 until choices.size)] }
        if (initial) {
            move(id, MotionKind.InitialDisperse, listOf(glyphs[id].disperseTargets[0]), speed = 0.3, easing = Easing.OutCubic)
        } else {
            val motionKind = glyphs[id].motion?.kind
            glyphs[id].lastRingPath = if (motionKind is MotionKind.Ring) motionKind.index else 0
            move(id, MotionKind.Disperse, glyphs[id].disperseTargets, speed = 0.14)
        }
        scene(id, spin = false)
    }

    override fun tick(frame: Frame): TickStatus {
        if (phase == Phase.Complete) return TickStatus.Complete

        when (phase) {
            Phase.Start -> {
                if (initialRemaining == 0) phase = Phase.Disperse else initialRemaining -= 1
            }
            Phase.Disperse -> {
                if (firstDisperse) {
                    firstDisperse = false
                    for (group in rings) {
                        for (id in group) disperse(id, initial = true)
                    }
                    for (id in glyphs.indices) {
                        val ext = glyphs[id].external
                        if (ext != null) move(id, MotionKind.External, listOf(ext), speed = 0.8, easing = Easing.OutSine)
                    }
                } else if (disperseRemaining == 0) {
                    phase = Phase.Spin
                    cyclesRemaining -= 1
                    spinRemaining = options.spinDuration
                    for (group in rings) {
                        for (id in group) {
                            move(id, MotionKind.Condense, listOf(glyphs[id].rotation[glyphs[id].lastRingPath]), speed = 0.1)
                            scene(id, spin = true)
                        }
                    }
                } else {
                    disperseRemaining -= 1
                }
            }
            Phase.Spin -> {
                if (spinRemaining == 0) {
                    if (cyclesRemaining == 0) {
                        phase = Phase.Final
                        for (id in glyphs.indices) {
                            glyphs[id].visible = true
                            move(id, MotionKind.Home, listOf(glyphs[id].input), speed = 0.8, easing = Easing.OutQuad)
                            if (glyphs[id].external == null) scene(id, spin = false)
                        }
                    } else {
                        disperseRemaining = options.disperseDuration
                        for (group in rings) {
                            for (id in group) disperse(id, initial = false)
                        }
                        phase = Phase.Disperse
                    }
                } else {
                    spinRemaining -= 1
                }
            }
            Phase.Final -> {
                if (glyphs.none { it.active }) phase = Phase.Complete
            }
            Phase.Complete -> Unit
        }

        for (id in glyphs.indices) {
            val motion = glyphs[id].motion
            if (motion != null) {
                glyphs[id].coordinate = motion.advance()
                if (motion.complete) {
                    glyphs[id].motion = null
                    when (val kind = motion.kind) {
                        is MotionKind.External -> glyphs[id].visible = false
                        is MotionKind.Home -> Unit
                        is MotionKind.InitialDisperse, is MotionKind.Disperse -> {
                            move(id, MotionKind.Disperse, glyphs[id].disperseTargets, speed = 0.14)
                        }
                        is MotionKind.Condense -> {
                            val index = glyphs[id].lastRingPath
                            move(id, MotionKind.Ring(index), listOf(glyphs[id].rotation[index]), speed = glyphs[id].speed)
                        }
                        is MotionKind.Ring -> {
                            val next = (kind.index + 1) % glyphs[id].rotation.size
                            move(id, MotionKind.Ring(next), listOf(glyphs[id].rotation[next]), speed = glyphs[id].speed)
                        }
                    }
                }
            }
            if (glyphs[id].age < glyphs[id].scene.size) {
                glyphs[id].foreground = glyphs[id].scene[glyphs[id].age]
                glyphs[id].age += 1
            }
        }

        for (glyph in glyphs) {
            if (glyph.visible && glyph.coordinate.column in 1..canvas.columns && glyph.coordinate.row in 1..canvas.rows) {
                frame[glyph.coordinate.column, glyph.coordinate.row] = Cell(
                    codepoint = glyph.symbol,
                    foreground = glyph.foreground,
                    background = if (glyph.foreground == 0u) 0xFFFF_FFFEu else 0u
                )
            }
        }

        return if (phase == Phase.Complete) TickStatus.Complete else TickStatus.Running
    }

    private fun randomOutside(): Coordinate {
        val candidates = listOf(
            Coordinate(column = rng.integer(1..canvas.columns), row = canvas.rows + 1),
            Coordinate(column = rng.integer(1..canvas.columns), row = 0),
            Coordinate(column = 0, row = rng.integer(1..canvas.rows)),
            Coordinate(column = canvas.columns + 1, row = rng.integer(1..canvas.rows))
        )
        return candidates[rng.integer(0 until candidates.size)]
    }
}
