package com.ttfx.effects

import com.ttfx.core.*

class ColorShiftEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    data class Configuration(
        val gradientStops: List<Color> = listOf(
            Color("e81416"),
            Color("ffa500"),
            Color("faeb36"),
            Color("79c314"),
            Color("487de7"),
            Color("4b369d"),
            Color("70369d")
        ),
        val gradientSteps: List<Int> = listOf(12),
        val gradientFrames: Int = 2,
        val noTravel: Boolean = false,
        val travelDirection: GradientDirection = GradientDirection.Radial,
        val reverseTravelDirection: Boolean = false,
        val noLoop: Boolean = false,
        val cycles: Int = 3,
        val skipFinalGradient: Boolean = false,
        val finalGradientStops: List<Color> = listOf(
            Color("e81416"),
            Color("ffa500"),
            Color("faeb36"),
            Color("79c314"),
            Color("487de7"),
            Color("4b369d"),
            Color("70369d")
        ),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientDirection: GradientDirection = GradientDirection.Vertical
    ) {
        init {
            require(gradientStops.isNotEmpty()) { "gradient stops must not be empty" }
            require(finalGradientStops.isNotEmpty()) { "final gradient stops must not be empty" }
            require(gradientSteps.isNotEmpty() && gradientSteps.all { it > 0 }) { "gradient steps must be positive" }
            require(finalGradientSteps.isNotEmpty() && finalGradientSteps.all { it > 0 }) { "final gradient steps must be positive" }
            require(gradientFrames > 0) { "gradient frames must be positive" }
            require(cycles >= 0) { "cycles must not be negative" }
        }
    }

    private enum class Phase {
        Gradient, Final, Done
    }

    private class Glyph(
        val coordinate: Coordinate,
        val symbol: Int,
        val gradientColors: List<UInt>,
        val finalColors: List<UInt>,
        var phase: Phase = Phase.Gradient,
        var frameIndex: Int = 0,
        var ticksRemaining: Int = 0,
        var completedGradientLoops: Int = 0,
        var displayedForeground: UInt? = null
    ) {
        val active: Boolean
            get() = phase != Phase.Done

        val foreground: UInt
            get() = when (phase) {
                Phase.Gradient -> gradientColors[frameIndex]
                Phase.Final -> finalColors[frameIndex]
                Phase.Done -> finalColors.lastOrNull() ?: gradientColors.lastOrNull() ?: 0u
            }
    }

    private var glyphs = ArrayList<Glyph>()
    private var complete = false

    init {
        build(input)
    }

    private fun build(input: InputText) {
        val sources = input.scalars.zip(input.positions).map { (symbol, pos) ->
            symbol to Coordinate(column = pos.column, row = pos.row)
        }
        if (sources.isEmpty()) {
            complete = true
            return
        }

        val coordinates = sources.map { it.second }
        val bottom = coordinates.minOf { it.row }
        val top = coordinates.maxOf { it.row }
        val left = coordinates.minOf { it.column }
        val right = coordinates.maxOf { it.column }

        val finalGradient = Gradient(options.finalGradientStops, options.finalGradientSteps)
        val finalMapping = finalGradient.coordinateColorMapping(
            minRow = bottom,
            maxRow = top,
            minColumn = left,
            maxColumn = right,
            direction = options.finalGradientDirection
        ).entries.associate { it.coordinate to it.color }

        val gradient = Gradient(stops = options.gradientStops, steps = options.gradientSteps, loop = !options.noLoop)
        glyphs = ArrayList(sources.map { (symbol, coordinate) ->
            val colors = shiftedColors(coordinate, gradient.spectrum, bottom, top, left, right)
            val last = colors.last()
            val target = finalMapping[coordinate] ?: options.finalGradientStops.last()
            val finalScene = Gradient(listOf(last, target), steps = 8)
            Glyph(
                coordinate = coordinate,
                symbol = symbol,
                gradientColors = colors.map { it.asUInt },
                finalColors = finalScene.spectrum.map { it.asUInt },
                ticksRemaining = options.gradientFrames
            )
        })
    }

    private fun shiftedColors(
        coordinate: Coordinate,
        spectrum: List<Color>,
        bottom: Int,
        top: Int,
        left: Int,
        right: Int
    ): List<Color> {
        if (options.noTravel || spectrum.isEmpty()) return spectrum
        val directionIndex: Double = when (options.travelDirection) {
            GradientDirection.Horizontal -> coordinate.column.toDouble() / canvas.columns.toDouble()
            GradientDirection.Vertical -> coordinate.row.toDouble() / canvas.rows.toDouble()
            GradientDirection.Diagonal -> (coordinate.row + coordinate.column).toDouble() / (canvas.columns + canvas.rows).toDouble()
            GradientDirection.Radial -> {
                try {
                    Geometry.normalizedDistanceFromCenter(
                        bottom = bottom,
                        top = top,
                        left = left,
                        right = right,
                        coordinate = coordinate
                    )
                } catch (e: Exception) {
                    0.0
                }
            }
        }

        var shiftDistance = (spectrum.size.toDouble() * directionIndex).toInt()
        if (options.reverseTravelDirection) shiftDistance *= -1
        val count = spectrum.size
        val split = if (shiftDistance < 0) {
            maxOf(count + shiftDistance, 0)
        } else {
            minOf(shiftDistance, count)
        }
        return spectrum.subList(split, count) + spectrum.subList(0, split)
    }

    override fun tick(frame: Frame): TickStatus {
        if (complete) return TickStatus.Complete
        if (glyphs.none { it.active }) {
            complete = true
            return TickStatus.Complete
        }

        advanceScenes()
        render(frame)

        if (glyphs.none { it.active }) {
            complete = true
            return TickStatus.Complete
        }
        return TickStatus.Running
    }

    private fun advanceScenes() {
        for (index in glyphs.indices) {
            if (!glyphs[index].active) continue
            glyphs[index].displayedForeground = glyphs[index].foreground
            glyphs[index].ticksRemaining -= 1
            if (glyphs[index].ticksRemaining != 0) continue

            when (glyphs[index].phase) {
                Phase.Gradient -> {
                    if (glyphs[index].frameIndex + 1 < glyphs[index].gradientColors.size) {
                        glyphs[index].frameIndex += 1
                        glyphs[index].ticksRemaining = options.gradientFrames
                    } else {
                        glyphs[index].completedGradientLoops += 1
                        if (options.cycles == 0 || glyphs[index].completedGradientLoops < options.cycles) {
                            glyphs[index].frameIndex = 0
                            glyphs[index].ticksRemaining = options.gradientFrames
                            glyphs[index].displayedForeground = glyphs[index].foreground
                        } else if (options.skipFinalGradient) {
                            glyphs[index].phase = Phase.Done
                        } else {
                            glyphs[index].phase = Phase.Final
                            glyphs[index].frameIndex = 0
                            glyphs[index].ticksRemaining = options.gradientFrames
                            glyphs[index].displayedForeground = glyphs[index].foreground
                        }
                    }
                }
                Phase.Final -> {
                    if (glyphs[index].frameIndex + 1 < glyphs[index].finalColors.size) {
                        glyphs[index].frameIndex += 1
                        glyphs[index].ticksRemaining = options.gradientFrames
                    } else {
                        glyphs[index].phase = Phase.Done
                    }
                }
                Phase.Done -> {}
            }
        }
    }

    private fun render(frame: Frame) {
        for (index in frame.cells.indices) {
            frame.cells[index] = Cell.BLANK
        }
        for (glyph in glyphs) {
            if (glyph.coordinate.column in 1..canvas.columns && glyph.coordinate.row in 1..canvas.rows) {
                frame[glyph.coordinate.column, glyph.coordinate.row] = Cell(
                    codepoint = glyph.symbol,
                    foreground = glyph.displayedForeground ?: glyph.foreground,
                    background = 0u
                )
            }
        }
    }
}
