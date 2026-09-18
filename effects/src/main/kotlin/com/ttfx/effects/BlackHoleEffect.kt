package com.ttfx.effects

import com.ttfx.core.*

typealias BlackholeEffect = BlackHoleEffect

class BlackHoleEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    data class Configuration(
        val blackholeColor: Color = Color("ffffff"),
        val starColors: List<Color> = listOf(
            Color("ffcc0d"), Color("ff7326"), Color("ff194d"),
            Color("bf2669"), Color("702a8c"), Color("049dbf")
        ),
        val finalGradientStops: List<Color> = listOf(Color("8A008A"), Color("00D1FF"), Color("ffffff")),
        val finalGradientSteps: List<Int> = listOf(9),
        val finalGradientDirection: GradientDirection = GradientDirection.Diagonal
    ) {
        init {
            require(starColors.isNotEmpty()) { "star colors must not be empty" }
        }
    }

    private enum class Phase {
        Forming, Consuming, Collapsing, Exploding, Complete
    }

    private enum class Kind {
        Formation, Rotation, Consume, Expand, Collapse, Nearby, Home
    }

    private data class Motion(
        val kind: Kind,
        val points: List<Coordinate>,
        val distances: List<Double>,
        val total: Double,
        val steps: Int,
        val easing: Easing,
        var step: Int = 0,
        var reached: Double = 0.0
    ) {
        constructor(kind: Kind, origin: Coordinate, targets: List<Coordinate>, speed: Double, easing: Easing = Easing.Linear) : this(
            kind = kind,
            points = listOf(origin) + targets,
            distances = (listOf(origin) + targets).let { pts ->
                (0 until pts.size - 1).map { Geometry.lineLength(pts[it], pts[it + 1]) }
            },
            total = (listOf(origin) + targets).let { pts ->
                (0 until pts.size - 1).sumOf { Geometry.lineLength(pts[it], pts[it + 1]) }
            },
            steps = PyCompat.roundHalfEven((listOf(origin) + targets).let { pts ->
                (0 until pts.size - 1).sumOf { Geometry.lineLength(pts[it], pts[it + 1]) }
            } / speed),
            easing = easing
        )

        fun advance(): Coordinate {
            if (steps <= 0 || total <= 0.0) return points.last()
            step++
            reached = easing.value(step.toDouble() / steps.toDouble()) * total
            var remaining = reached
            for (index in distances.indices) {
                if (remaining <= distances[index]) {
                    val t = if (distances[index] == 0.0) 0.0 else remaining / distances[index]
                    return Geometry.coordinateOnLine(points[index], points[index + 1], t)
                }
                remaining -= distances[index]
            }
            return points.last()
        }

        val complete: Boolean
            get() = step >= steps || total == 0.0
    }

    private data class Glyph(
        val source: Coordinate,
        val symbol: Int,
        val final: Color,
        var coordinate: Coordinate,
        var visual: Cell,
        var layer: Int = 0,
        var motion: Motion? = null,
        var scene: List<Cell> = emptyList(),
        var age: Int = 0,
        var consumed: List<Cell> = emptyList(),
        var cooling: List<Cell> = emptyList(),
        var speed: Double = 0.0,
        var homeSpeed: Double = 0.0,
        var rotation: List<Coordinate> = emptyList()
    ) {
        val active: Boolean
            get() = motion != null || age < scene.size
    }

    private var rng: Xoshiro256PlusPlus = configuration.makeRNG(seed)
    private var glyphs: MutableList<Glyph> = ArrayList()
    private var ring: MutableList<Int> = ArrayList()
    private var pendingRing: MutableList<Int> = ArrayList()
    private var pendingConsume: MutableList<Int> = ArrayList()
    private var phase = Phase.Forming
    private var radius = 3
    private var formationDelay = 0
    private var delay = 0
    private var pointScene: MutableList<Cell> = ArrayList()

    private val center: Coordinate
        get() = Coordinate(
            column = maxOf(1, (canvas.columns + 1) / 2),
            row = maxOf(1, (canvas.rows + 1) / 2)
        )

    init {
        build(input)
    }

    private fun build(input: InputText) {
        if (input.scalars.isEmpty()) {
            phase = Phase.Complete
            return
        }
        radius = maxOf(
            minOf(
                PyCompat.roundHalfEven(canvas.columns.toDouble() * 0.3),
                PyCompat.roundHalfEven(canvas.rows.toDouble() * 0.2)
            ),
            3
        )
        val coordinates = input.positions.map { Coordinate(it.column, it.row) }
        val gradient = Gradient(options.finalGradientStops, options.finalGradientSteps)
        val mapping = gradient.coordinateColorMapping(
            minRow = coordinates.minOf { it.row },
            maxRow = coordinates.maxOf { it.row },
            minColumn = coordinates.minOf { it.column },
            maxColumn = coordinates.maxOf { it.column },
            direction = options.finalGradientDirection
        ).entries.associate { it.coordinate to it.color }

        glyphs = coordinates.indices.map { i ->
            Glyph(
                source = coordinates[i],
                symbol = input.scalars[i],
                final = mapping[coordinates[i]]!!,
                coordinate = coordinates[i],
                visual = Cell.BLANK
            )
        }.toMutableList()

        val available = glyphs.indices.toMutableList()
        while (ring.size < radius * 3 && available.isNotEmpty()) {
            ring.add(available.removeAt(rng.integer(0 until available.size)))
        }

        val positions = Geometry.coordinatesOnCircle(origin = center, radius = radius, limit = ring.size)
        for ((index, id) in ring.withIndex()) {
            glyphs[id].rotation = positions.subList(index, positions.size) + positions.subList(0, index)
        }

        val symbols = "*'`¤•°·".codePoints().toArray().toList()
        val colors = Gradient(listOf(Color("4a4a4d"), Color("ffffff")), 6).spectrum
        for (id in glyphs.indices) {
            val symbol = symbols[rng.integer(0 until symbols.size)]
            val color = colors[rng.integer(0 until colors.size)]
            glyphs[id].visual = cell(symbol, color)
            if (!ring.contains(id)) {
                glyphs[id].coordinate = Coordinate(
                    column = rng.integer(1..canvas.columns),
                    row = rng.integer(1..canvas.rows)
                )
                glyphs[id].speed = rng.uniform(0.17, 0.30)
                glyphs[id].consumed = Gradient(listOf(color, Color("000000")), 10).spectrum.map {
                    cell(symbol, it)
                } + listOf(Cell.BLANK)
                pendingConsume.add(id)
            }
        }
        rng.shuffle(pendingConsume)
        pendingRing = ring.toMutableList()
        formationDelay = maxOf(100 / ring.size, 6)
        delay = formationDelay
    }

    private fun move(id: Int, kind: Kind, targets: List<Coordinate>, speed: Double, easing: Easing = Easing.Linear) {
        glyphs[id].motion = Motion(kind = kind, origin = glyphs[id].coordinate, targets = targets, speed = speed, easing = easing)
    }

    private fun scene(id: Int, frames: List<Cell>) {
        glyphs[id].scene = frames
        glyphs[id].age = 0
        if (frames.isNotEmpty()) {
            glyphs[id].visual = frames.first()
        }
    }

    override fun tick(frame: Frame): TickStatus {
        if (phase == Phase.Complete && glyphs.none { it.active }) return TickStatus.Complete

        when (phase) {
            Phase.Forming -> {
                if (pendingRing.isNotEmpty()) {
                    if (delay == 0) {
                        val id = pendingRing.removeAt(0)
                        move(id, Kind.Formation, listOf(glyphs[id].rotation[0]), 0.7, Easing.InOutSine)
                        glyphs[id].layer = 1
                        scene(id, listOf(cell('*'.code, options.blackholeColor)))
                        delay = formationDelay
                    } else {
                        delay--
                    }
                } else if (glyphs.none { it.active }) {
                    for (id in ring) {
                        move(id, Kind.Rotation, glyphs[id].rotation, 0.45)
                    }
                    phase = Phase.Consuming
                }
            }
            Phase.Consuming -> {
                if (pendingConsume.isNotEmpty()) {
                    for (id in pendingConsume) {
                        move(id, Kind.Consume, listOf(center), glyphs[id].speed, Easing.InExpo)
                        glyphs[id].layer = 2
                    }
                    pendingConsume.clear()
                } else if (glyphs.indices.all { !glyphs[it].active || ring.contains(it) }) {
                    phase = Phase.Collapsing
                }
            }
            Phase.Collapsing -> {
                val positions = Geometry.coordinatesOnCircle(origin = center, radius = radius + 3, limit = ring.size)
                val pointSymbols = "◦◎◉●◉◎◦".codePoints().toArray().toList()
                for ((index, id) in ring.withIndex()) {
                    if (index == 0) {
                        for (rep in 0 until 3) {
                            for (symbol in pointSymbols) {
                                val color = options.starColors[rng.integer(0 until options.starColors.size)]
                                pointScene.addAll(List(3) { cell(symbol, color) })
                            }
                        }
                    }
                    move(id, Kind.Expand, listOf(positions[index]), 0.2, Easing.InExpo)
                }
                phase = Phase.Exploding
            }
            Phase.Exploding -> {
                if (ring.all { !glyphs[it].active }) {
                    val colors = listOf("ffcc0d", "ff7326", "ff194d", "bf2669", "702a8c", "049dbf").map { Color(it) }
                    for (id in glyphs.indices) {
                        val nearbyPoints = Geometry.coordinatesOnCircle(origin = glyphs[id].source, radius = 3, limit = 5)
                        val nearby = nearbyPoints[rng.integer(0..4)]
                        val speed = rng.integer(3..4).toDouble() / 10.0
                        glyphs[id].homeSpeed = rng.integer(4..6).toDouble() / 100.0
                        val color = colors[rng.integer(0 until colors.size)]
                        glyphs[id].cooling = Gradient(listOf(color, glyphs[id].final), 10).spectrum.flatMap {
                            List(20) { _ -> cell(glyphs[id].symbol, it) }
                        }
                        scene(id, listOf(cell(glyphs[id].symbol, color)))
                        move(id, Kind.Nearby, listOf(nearby), speed, Easing.OutExpo)
                    }
                    phase = Phase.Complete
                }
            }
            Phase.Complete -> {}
        }

        for (id in glyphs.indices) {
            val motion = glyphs[id].motion
            if (motion != null) {
                glyphs[id].coordinate = motion.advance()
                glyphs[id].motion = if (motion.complete) null else motion
                if (motion.kind == Kind.Consume) {
                    if (motion.complete) {
                        glyphs[id].visual = Cell.BLANK
                    } else {
                        val progress = maxOf(maxOf(motion.total, 1.0) - maxOf(motion.total - motion.reached, 1.0), 1.0) / maxOf(motion.total, 1.0)
                        val index = minOf(glyphs[id].consumed.size - 1, maxOf(0, PyCompat.roundHalfEven((glyphs[id].consumed.size - 1).toDouble() * progress)))
                        glyphs[id].visual = glyphs[id].consumed[index]
                    }
                }
                if (motion.complete) {
                    when (motion.kind) {
                        Kind.Rotation -> move(id, Kind.Rotation, glyphs[id].rotation, 0.45)
                        Kind.Expand -> move(id, Kind.Collapse, listOf(center), 0.3, Easing.InExpo)
                        Kind.Collapse -> {
                            if (id == ring.first()) {
                                scene(id, pointScene)
                                glyphs[id].layer = 3
                            }
                        }
                        Kind.Nearby -> {
                            move(id, Kind.Home, listOf(glyphs[id].source), glyphs[id].homeSpeed, Easing.InCubic)
                            scene(id, glyphs[id].cooling)
                        }
                        else -> {}
                    }
                }
            }
            if (glyphs[id].age < glyphs[id].scene.size) {
                glyphs[id].visual = glyphs[id].scene[glyphs[id].age]
                glyphs[id].age++
            }
        }

        val ordered = glyphs.indices.sortedWith { i1, i2 ->
            if (glyphs[i1].layer == glyphs[i2].layer) i1.compareTo(i2) else glyphs[i1].layer.compareTo(glyphs[i2].layer)
        }
        for (id in ordered) {
            val glyph = glyphs[id]
            if (glyph.coordinate.column in 1..canvas.columns && glyph.coordinate.row in 1..canvas.rows) {
                frame[glyph.coordinate.column, glyph.coordinate.row] = glyph.visual
            }
        }

        return if (phase == Phase.Complete && glyphs.none { it.active }) TickStatus.Complete else TickStatus.Running
    }

    private fun cell(symbol: Int, color: Color): Cell {
        val rgb = color.asUInt
        return Cell(codepoint = symbol, foreground = rgb, background = if (rgb == 0u) 0xFFFF_FFFEu else 0u)
    }
}
