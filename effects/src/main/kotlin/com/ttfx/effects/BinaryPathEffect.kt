package com.ttfx.effects

import com.ttfx.core.*
import kotlin.math.abs
import kotlin.math.pow

class BinaryPathEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    private val options: Configuration = Configuration()
) : Effect {

    data class Configuration(
        val finalGradientStops: List<Color> = listOf(Color("00d500"), Color("007500")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientDirection: GradientDirection = GradientDirection.Radial,
        val binaryColors: List<Color> = listOf(Color("044E29"), Color("157e38"), Color("45bf55"), Color("95ed87")),
        val movementSpeed: Double = 1.0,
        val activeBinaryGroups: Double = 0.08
    ) {
        init {
            require(finalGradientStops.isNotEmpty()) { "final gradient stops must not be empty" }
            require(binaryColors.isNotEmpty()) { "binary colors must not be empty" }
            require(movementSpeed > 0.0) { "movement speed must be positive" }
            require(activeBinaryGroups >= 0.0) { "active binary groups must be non-negative" }
        }
    }

    private data class Bit(
        val symbol: Int,
        val color: UInt,
        val points: List<Coordinate>,
        val distances: List<Double>,
        val total: Double,
        val maxSteps: Int,
        var coordinate: Coordinate,
        var step: Int = 0,
        var released: Boolean = false,
        var visible: Boolean = false,
        var moving: Boolean = true
    ) {
        fun tick() {
            if (!released || !moving) return
            if (maxSteps == 0 || total == 0.0) {
                coordinate = points.last()
                moving = false
                return
            }
            step++
            var distance = (step.toDouble() / maxSteps.toDouble()) * total
            for (index in distances.indices) {
                if (distance <= distances[index]) {
                    coordinate = if (distances[index] == 0.0) points[index] else Geometry.coordinateOnLine(
                        points[index], points[index + 1], distance / distances[index]
                    )
                    break
                }
                distance -= distances[index]
                if (index == distances.size - 1) {
                    coordinate = points.last()
                }
            }
            if (step >= maxSteps) moving = false
        }
    }

    private data class Representation(
        val coordinate: Coordinate,
        val symbol: Int,
        val collapse: List<UInt>,
        val brighten: List<UInt>,
        val bits: List<Bit>,
        var released: Int = 0,
        var visible: Boolean = false,
        var scene: List<UInt> = emptyList(),
        var sceneStep: Int = 0,
        var eased: Boolean = false,
        var foreground: UInt = 0u
    ) {
        val active: Boolean
            get() = sceneStep < scene.size || bits.any { it.released && it.moving }

        fun activate(brightening: Boolean) {
            visible = true
            scene = if (brightening) brighten else collapse
            eased = !brightening
            sceneStep = 0
            foreground = scene[0]
        }

        fun tick() {
            for (bit in bits) bit.tick()
            if (sceneStep < scene.size) {
                val index = if (eased) {
                    PyCompat.roundHalfEven((sceneStep.toDouble() / scene.size.toDouble()).pow(2) * (scene.size - 1).toDouble())
                } else {
                    sceneStep
                }
                foreground = scene[index]
                sceneStep++
            }
        }
    }

    private var rng: Xoshiro256PlusPlus = configuration.makeRNG(seed)
    private val representations = ArrayList<Representation>()
    private val pending = ArrayList<Int>()
    private val active = ArrayList<Int>()
    private val wipeGroups = ArrayList<List<Int>>()
    private var wiping = false
    private var wipeFinished = false
    private var complete = false
    private var maxActive = 1

    init {
        if (input.scalars.isEmpty()) {
            complete = true
        } else {
            val coordinates = input.positions.map { Coordinate(it.column, it.row) }
            val gradient = Gradient(options.finalGradientStops, options.finalGradientSteps)
            val mapping = gradient.coordinateColorMapping(
                minRow = coordinates.minOf { it.row },
                maxRow = coordinates.maxOf { it.row },
                minColumn = coordinates.minOf { it.column },
                maxColumn = coordinates.maxOf { it.column },
                direction = options.finalGradientDirection
            ).entries.associate { it.coordinate to it.color }

            for (index in input.scalars.indices) {
                val target = coordinates[index]
                val start = randomOutside()
                val points = ArrayList<Coordinate>()
                points.add(start)
                var columnOrientation = rng.integer(0..1) == 0

                while (points.last() != target) {
                    val last = points.last()
                    val columnDistance = abs(last.column - target.column)
                    val rowDistance = abs(last.row - target.row)
                    val next: Coordinate = if (columnOrientation && rowDistance > 0) {
                        val step = rng.integer(1..minOf(rowDistance, maxOf(10, (canvas.columns * 0.2).toInt())))
                        Coordinate(last.column, last.row + (if (last.row > target.row) -step else step)).also {
                            columnOrientation = false
                        }
                    } else if (!columnOrientation && columnDistance > 0) {
                        val step = rng.integer(1..minOf(columnDistance, 4))
                        Coordinate(last.column + (if (last.column > target.column) -step else step), last.row).also {
                            columnOrientation = true
                        }
                    } else {
                        target
                    }
                    points.add(next)
                }
                points.add(target)
                points.add(target)

                val distances = (0 until points.size - 1).map { i ->
                    Geometry.lineLength(points[i], points[i + 1])
                }
                val total = distances.sum()
                val binStr = input.scalars[index].toString(2).padStart(8, '0')
                val bits = binStr.map { ch ->
                    val color = options.binaryColors[rng.integer(0 until options.binaryColors.size)].asUInt
                    Bit(
                        symbol = ch.code,
                        color = color,
                        points = points,
                        distances = distances,
                        total = total,
                        maxSteps = PyCompat.roundHalfEven(total / options.movementSpeed),
                        coordinate = start
                    )
                }

                val final = mapping[target] ?: options.finalGradientStops[0]
                val dim = final.adjustBrightness(0.5)
                val collapse = Gradient(listOf(Color("ffffff"), dim), 7).spectrum.flatMap { c ->
                    List(3) { c.asUInt }
                }
                val brighten = Gradient(listOf(dim, final), 10).spectrum.flatMap { c ->
                    List(2) { c.asUInt }
                }
                representations.add(
                    Representation(
                        coordinate = target,
                        symbol = input.scalars[index],
                        collapse = collapse,
                        brighten = brighten,
                        bits = bits
                    )
                )
            }

            pending.addAll(representations.indices)
            maxActive = maxOf(1, (pending.size * options.activeBinaryGroups).toInt())
            val grouped = representations.indices.groupBy {
                representations[it].coordinate.column + representations[it].coordinate.row
            }
            for (key in grouped.keys.sortedDescending()) {
                wipeGroups.add(grouped[key]!!)
            }
        }
    }

    override fun tick(frame: Frame): TickStatus {
        if (complete) return TickStatus.Complete

        if (wipeFinished && representations.none { it.active }) {
            render(frame)
            complete = true
            return TickStatus.Complete
        }

        if (!wiping) {
            while (active.size < maxActive && pending.isNotEmpty()) {
                active.add(pending.removeAt(rng.integer(0 until pending.size)))
            }
            val finished = HashSet<Int>()
            for (index in active) {
                val rep = representations[index]
                if (rep.released < rep.bits.size) {
                    val bit = rep.bits[rep.released]
                    bit.released = true
                    bit.visible = true
                    rep.released++
                } else if (rep.bits.all { it.coordinate == rep.coordinate }) {
                    for (bit in rep.bits) bit.visible = false
                    rep.activate(brightening = false)
                    finished.add(index)
                }
            }
            active.removeAll(finished)
            if (representations.none { it.active }) wiping = true
        }

        if (wiping) {
            for (w in 0 until 2) {
                if (wipeGroups.isEmpty()) {
                    wipeFinished = true
                } else {
                    for (idx in wipeGroups.removeAt(0)) {
                        representations[idx].activate(brightening = true)
                    }
                }
            }
        }

        for (rep in representations) rep.tick()
        render(frame)
        return TickStatus.Running
    }

    private fun randomOutside(): Coordinate {
        val candidates = listOf(
            Coordinate(rng.integer(1..canvas.columns), canvas.rows + 1),
            Coordinate(rng.integer(1..canvas.columns), 0),
            Coordinate(0, rng.integer(1..canvas.rows)),
            Coordinate(canvas.columns + 1, rng.integer(1..canvas.rows))
        )
        return candidates[rng.integer(0 until candidates.size)]
    }

    private fun render(frame: Frame) {
        fun put(symbol: Int, color: UInt, coordinate: Coordinate) {
            if (coordinate.column in 1..canvas.columns && coordinate.row in 1..canvas.rows) {
                frame[coordinate.column, coordinate.row] = Cell(
                    codepoint = symbol,
                    foreground = color,
                    background = if (color == 0u) 0xFFFF_FFFEu else 0u
                )
            }
        }

        for (source in representations) {
            if (source.visible) put(source.symbol, source.foreground, source.coordinate)
        }
        for (source in representations) {
            for (bit in source.bits) {
                if (bit.visible) put(bit.symbol, bit.color, bit.coordinate)
            }
        }
    }
}
