package com.ttfx.effects

import com.ttfx.core.*

class ErrorCorrectEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    private val options: Configuration = Configuration()
) : Effect {

    data class Configuration(
        val errorPairs: Double = 0.1,
        val swapDelay: Int = 6,
        val errorColor: Color = Color("e74c3c"),
        val correctColor: Color = Color("45bf55"),
        val movementSpeed: Double = 0.9,
        val finalGradientStops: List<Color> = listOf(Color("8A008A"), Color("00D1FF"), Color("FFFFFF")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientDirection: GradientDirection = GradientDirection.Vertical
    ) {
        init {
            require(errorPairs > 0.0) { "error pairs must be positive" }
            require(swapDelay >= 1) { "swap delay must be positive" }
            require(movementSpeed > 0.0) { "movement speed must be positive" }
        }
    }

    private enum class Stage { Idle, Error, WipeStart, Correcting, WipeEnd, Final, Done }
    private data class Visual(val symbol: Int, val color: UInt)
    private data class Glyph(
        val input: Coordinate,
        val symbol: Int,
        val finalColor: UInt,
        val correction: List<UInt>,
        val final: List<Visual>,
        var coordinate: Coordinate,
        var origin: Coordinate,
        var visual: Visual,
        var stage: Stage = Stage.Idle,
        var timeline: List<Visual> = emptyList(),
        var age: Int = 0,
        var motionStep: Int = 0,
        var motionSteps: Int = 0,
        var totalDistance: Double = 0.0,
        var layer: Int = 0
    ) {
        val active: Boolean
            get() = stage != Stage.Idle && stage != Stage.Done
    }

    private val glyphs = ArrayList<Glyph>()
    private val pairs = ArrayList<Pair<Int, Int>>()
    private var delay = 0
    private var complete = false

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

            val correction = Gradient(listOf(options.errorColor, options.correctColor), 10).spectrum.map { it.asUInt }
            for (index in coordinates.indices) {
                val color = mapping[coordinates[index]] ?: options.errorColor
                val final = Gradient(listOf(options.correctColor, color), 10).spectrum.flatMap { c ->
                    List(3) { Visual(input.scalars[index], c.asUInt) }
                }
                glyphs.add(
                    Glyph(
                        input = coordinates[index],
                        symbol = input.scalars[index],
                        finalColor = color.asUInt,
                        correction = correction,
                        final = final,
                        coordinate = coordinates[index],
                        origin = coordinates[index],
                        visual = Visual(input.scalars[index], color.asUInt)
                    )
                )
            }

            val rng = configuration.makeRNG(seed)
            val remaining = ArrayList(glyphs.indices.toList())
            val pairCount = minOf((options.errorPairs * remaining.size).toInt(), remaining.size / 2)
            for (p in 0 until pairCount) {
                val first = remaining.removeAt(rng.integer(0 until remaining.size))
                val second = remaining.removeAt(rng.integer(0 until remaining.size))
                val firstInput = glyphs[first].input
                val secondInput = glyphs[second].input
                glyphs[first].coordinate = secondInput
                glyphs[second].coordinate = firstInput
                val errColor = options.errorColor.asUInt
                glyphs[first].visual = Visual(glyphs[first].symbol, errColor)
                glyphs[second].visual = Visual(glyphs[second].symbol, errColor)
                pairs.add(first to second)
            }
        }
    }

    private fun activate(index: Int, stage: Stage) {
        val g = glyphs[index]
        g.stage = stage
        g.age = 0
        val error = options.errorColor.asUInt
        val correct = options.correctColor.asUInt
        when (stage) {
            Stage.Error -> {
                val pair = List(3) { Visual(0x2593, error) } + List(3) { Visual(g.symbol, 0xFFFFFFu) }
                g.timeline = (0 until 10).flatMap { pair }
            }
            Stage.WipeStart -> {
                g.timeline = "▁▂▃▄▅▆▇█".codePoints().toArray().flatMap { sym ->
                    List(3) { Visual(sym, error) }
                }
            }
            Stage.Correcting -> {
                g.origin = g.coordinate
                g.totalDistance = Geometry.lineLength(g.origin, g.input)
                g.motionSteps = PyCompat.roundHalfEven(g.totalDistance / options.movementSpeed)
                g.motionStep = 0
                g.layer = 1
                g.timeline = listOf(Visual(0x2588, error))
            }
            Stage.WipeEnd -> {
                g.layer = 0
                g.timeline = "▇▆▅▄▃▂▁".codePoints().toArray().flatMap { sym ->
                    List(3) { Visual(sym, correct) }
                }
            }
            Stage.Final -> g.timeline = g.final
            Stage.Idle, Stage.Done -> return
        }
        g.visual = g.timeline[0]
    }

    override fun tick(frame: Frame): TickStatus {
        if (complete) return TickStatus.Complete

        if (pairs.isNotEmpty() && delay == 0) {
            val pair = pairs.removeAt(0)
            activate(pair.first, Stage.Error)
            activate(pair.second, Stage.Error)
            delay = options.swapDelay
        } else {
            delay--
        }

        for (index in glyphs.indices) {
            val g = glyphs[index]
            if (!g.active) continue
            if (g.stage == Stage.Correcting) {
                g.motionStep++
                val ratio = if (g.motionSteps == 0) 1.0 else minOf(1.0, g.motionStep.toDouble() / g.motionSteps.toDouble())
                g.coordinate = Geometry.coordinateOnLine(g.origin, g.input, ratio)
                if (ratio == 1.0) {
                    activate(index, Stage.WipeEnd)
                } else {
                    val total = maxOf(g.totalDistance, 1.0)
                    val progress = maxOf(total - maxOf(g.totalDistance * (1.0 - ratio), 1.0), 1.0) / total
                    val colorIdx = minOf(10, maxOf(0, PyCompat.roundHalfEven(progress * 10.0)))
                    val color = g.correction[colorIdx]
                    g.visual = Visual(0x2588, color)
                }
            } else {
                g.visual = g.timeline[g.age]
                g.age++
                if (g.age == g.timeline.size) {
                    when (g.stage) {
                        Stage.Error -> activate(index, Stage.WipeStart)
                        Stage.WipeStart -> activate(index, Stage.Correcting)
                        Stage.WipeEnd -> activate(index, Stage.Final)
                        Stage.Final -> g.stage = Stage.Done
                        else -> {}
                    }
                }
            }
        }

        val sortedIndices = glyphs.indices.sortedWith { a, b ->
            val cmp = glyphs[a].layer.compareTo(glyphs[b].layer)
            if (cmp != 0) cmp else a.compareTo(b)
        }

        for (index in sortedIndices) {
            val g = glyphs[index]
            if (g.coordinate.column in 1..canvas.columns && g.coordinate.row in 1..canvas.rows) {
                frame[g.coordinate.column, g.coordinate.row] = Cell(
                    codepoint = g.visual.symbol,
                    foreground = g.visual.color,
                    background = if (g.visual.color == 0u) 0xFFFF_FFFEu else 0u
                )
            }
        }

        complete = pairs.isEmpty() && glyphs.none { it.active }
        return if (complete) TickStatus.Complete else TickStatus.Running
    }
}
