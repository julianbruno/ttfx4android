package com.ttfx.effects

import com.ttfx.core.*
import kotlin.math.floor

class SpotlightsEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    data class Configuration(
        val beamWidthRatio: Double = 2.0,
        val beamFalloff: Double = 0.3,
        val searchDuration: Int = 550,
        val searchSpeedRange: ClosedFloatingPointRange<Double> = 0.35..0.75,
        val spotlightCount: Int = 3,
        val finalGradientStops: List<Color> = listOf(Color("ab48ff"), Color("e7b2b2"), Color("fffebd")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientDirection: GradientDirection = GradientDirection.Vertical
    ) {
        init {
            require(beamWidthRatio > 0.0) { "beam width ratio must be positive" }
            require(beamFalloff >= 0.0) { "beam falloff must be non-negative" }
            require(searchDuration > 0) { "search duration must be positive" }
            require(searchSpeedRange.start > 0.0 && searchSpeedRange.endInclusive > 0.0) { "search speed must be positive" }
            require(spotlightCount > 0) { "spotlight count must be positive" }
            require(finalGradientStops.isNotEmpty()) { "final gradient stops must not be empty" }
        }
    }

    private companion object {
        private const val EXPLICIT_BLACK_FOREGROUND_SENTINEL: UInt = 0xFFFF_FFFEu
    }

    private class Glyph(
        val symbol: Int,
        val coordinate: Coordinate,
        val bright: Color,
        val dark: Color
    )

    private class SearchPath(
        val target: Coordinate,
        val control: Coordinate,
        val speed: Double
    )

    private class Spotlight(
        val paths: List<SearchPath>,
        var coordinate: Coordinate,
        var origin: Coordinate,
        var pathIndex: Int = 0,
        var step: Int = 0,
        var maxSteps: Int = 0,
        var returning: Boolean = false,
        var active: Boolean = true
    )

    private var rng: Xoshiro256PlusPlus = configuration.makeRNG(seed)
    private var glyphs = ArrayList<Glyph>()
    private var spotlights = ArrayList<Spotlight>()
    private var range = 1
    private var remaining = options.searchDuration
    private var searching = true
    private var complete = false

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
            complete = true
            return
        }

        for (i in 0 until options.spotlightCount) {
            val spawn = randomOutside()
            val targets = ArrayList<Coordinate>()
            targets.add(randomInside())
            for (j in 0 until 10) {
                var target = randomInside()
                while (Geometry.lineLength(from = targets.last(), to = target, doubleRowDifference = false) < (canvas.columns / 4).toDouble()) {
                    target = randomInside()
                }
                targets.add(target)
            }
            val paths = ArrayList<SearchPath>()
            for (target in targets) {
                val speed = rng.uniform(options.searchSpeedRange.start, options.searchSpeedRange.endInclusive)
                paths.add(SearchPath(target = target, control = randomOutside(), speed = speed))
            }
            val steps = PyCompat.roundHalfEven(
                Geometry.bezierLength(from = spawn, controls = listOf(paths[0].control), to = paths[0].target) / paths[0].speed
            )
            spotlights.add(Spotlight(paths = paths, coordinate = spawn, origin = spawn, maxSteps = steps))
        }

        val coordinates = input.positions.map { Coordinate(column = it.column, row = it.row) }
        val gradient = Gradient(stops = options.finalGradientStops, steps = options.finalGradientSteps)
        val mapping = gradient.coordinateColorMapping(
            minRow = coordinates.minOf { it.row },
            maxRow = coordinates.maxOf { it.row },
            minColumn = coordinates.minOf { it.column },
            maxColumn = coordinates.maxOf { it.column },
            direction = options.finalGradientDirection
        ).entries.associate { it.coordinate to it.color }

        glyphs = ArrayList(coordinates.indices.map {
            val bright = mapping[coordinates[it]] ?: options.finalGradientStops.last()
            Glyph(
                symbol = input.scalars[it],
                coordinate = coordinates[it],
                bright = bright,
                dark = bright.adjustBrightness(0.2)
            )
        })

        val smallest = minOf(canvas.columns, canvas.rows)
        range = maxOf(1, minOf(floor(smallest.toDouble() / options.beamWidthRatio).toInt(), smallest))
    }

    override fun tick(frame: Frame): TickStatus {
        if (complete) return TickStatus.Complete

        for (i in frame.cells.indices) {
            frame.cells[i] = Cell.BLANK
        }

        val lit = HashSet<Coordinate>()
        for (light in spotlights) {
            lit.addAll(Geometry.coordinatesInEllipse(center = light.coordinate, diameter = range))
        }

        for (glyph in glyphs) {
            var color = glyph.dark
            if (glyph.coordinate in lit) {
                val distance = spotlights.minOfOrNull {
                    Geometry.lineLength(from = it.coordinate, to = glyph.coordinate)
                } ?: Double.POSITIVE_INFINITY
                val threshold = range.toDouble() * (1.0 - options.beamFalloff)
                color = if (distance > threshold) {
                    val factor = maxOf(0.2, 1.0 - (distance - threshold) / (range.toDouble() * options.beamFalloff))
                    glyph.bright.adjustBrightness(factor)
                } else {
                    glyph.bright
                }
            }
            val word = color.asUInt
            frame[glyph.coordinate.column, glyph.coordinate.row] = Cell(
                codepoint = glyph.symbol,
                foreground = word,
                background = if (word == 0u) EXPLICIT_BLACK_FOREGROUND_SENTINEL else 0u
            )
        }

        if (searching) {
            remaining -= 1
            if (remaining == 0) {
                searching = false
                for (index in spotlights.indices) {
                    spotlights[index].returning = true
                    spotlights[index].origin = spotlights[index].coordinate
                    spotlights[index].step = 0
                    spotlights[index].maxSteps = PyCompat.roundHalfEven(
                        Geometry.lineLength(from = spotlights[index].coordinate, to = center) / 0.5
                    )
                }
            }
        }

        if (spotlights.none { it.active }) {
            if (spotlights.size > 1) {
                spotlights = ArrayList(spotlights.subList(0, 1))
            }
            range += 1
            if (range.toDouble() > floor(maxOf(canvas.columns, canvas.rows).toDouble() / 1.5)) {
                complete = true
            }
        }

        for (index in spotlights.indices) {
            if (!spotlights[index].active) continue
            spotlights[index].step += 1
            val light = spotlights[index]
            val ratio = if (light.maxSteps == 0) 1.0 else minOf(1.0, light.step.toDouble() / light.maxSteps.toDouble())
            if (light.returning) {
                spotlights[index].coordinate = Geometry.coordinateOnLine(
                    from = light.origin,
                    to = center,
                    t = Easing.InOutSine.value(ratio)
                )
                if (ratio == 1.0) {
                    spotlights[index].active = false
                }
            } else {
                val path = light.paths[light.pathIndex]
                spotlights[index].coordinate = Geometry.coordinateOnBezier(
                    from = light.origin,
                    controls = listOf(path.control),
                    to = path.target,
                    t = Easing.InOutQuad.value(ratio)
                )
                if (ratio == 1.0) {
                    val next = (light.pathIndex + 1) % light.paths.size
                    val nextPath = light.paths[next]
                    spotlights[index].pathIndex = next
                    spotlights[index].origin = spotlights[index].coordinate
                    spotlights[index].step = 0
                    spotlights[index].maxSteps = PyCompat.roundHalfEven(
                        Geometry.bezierLength(from = spotlights[index].coordinate, controls = listOf(nextPath.control), to = nextPath.target) / nextPath.speed
                    )
                }
            }
        }

        return if (complete) TickStatus.Complete else TickStatus.Running
    }

    private fun randomInside(): Coordinate {
        return Coordinate(column = rng.integer(1..canvas.columns), row = rng.integer(1..canvas.rows))
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
