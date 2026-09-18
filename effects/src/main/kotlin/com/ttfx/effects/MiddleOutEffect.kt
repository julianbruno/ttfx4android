package com.ttfx.effects

import com.ttfx.core.*

class MiddleOutEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    enum class ExpandDirection {
        Vertical, Horizontal
    }

    data class Configuration(
        val startingColor: Color = Color("ffffff"),
        val expandDirection: ExpandDirection = ExpandDirection.Vertical,
        val centerMovementSpeed: Double = 0.6,
        val fullMovementSpeed: Double = 0.6,
        val centerEasing: Easing = Easing.InOutSine,
        val fullEasing: Easing = Easing.InOutSine,
        val finalGradientStops: List<Color> = listOf(Color("8A008A"), Color("00D1FF"), Color("FFFFFF")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientDirection: GradientDirection = GradientDirection.Vertical
    ) {
        init {
            require(centerMovementSpeed > 0) { "center movement speed must be positive" }
            require(fullMovementSpeed > 0) { "full movement speed must be positive" }
        }
    }

    private enum class Phase { Center, Full, Complete }

    private class Glyph(
        val symbol: Int,
        val target: Coordinate,
        val centerTarget: Coordinate,
        val finalColors: List<UInt>,
        var coordinate: Coordinate,
        var centerStep: Int = 0,
        var fullStep: Int = 0,
        var fullAge: Int = 0,
        val centerSteps: Int,
        val fullSteps: Int,
        val centerDistance: Double,
        val fullDistance: Double
    )

    private var glyphs = ArrayList<Glyph>()
    private var phase: Phase = Phase.Center

    init {
        build(input)
    }

    override fun tick(frame: Frame): TickStatus {
        if (phase == Phase.Complete) return TickStatus.Complete

        if (phase == Phase.Center && centerComplete) {
            phase = Phase.Full
        }

        return when (phase) {
            Phase.Center -> {
                advanceCenter()
                render(frame)
                if (centerComplete && glyphs.isEmpty()) {
                    phase = Phase.Complete
                    TickStatus.Complete
                } else {
                    TickStatus.Running
                }
            }

            Phase.Full -> {
                advanceFull()
                render(frame)
                if (fullComplete) {
                    phase = Phase.Complete
                    TickStatus.Complete
                } else {
                    TickStatus.Running
                }
            }

            Phase.Complete -> TickStatus.Complete
        }
    }

    private val centerComplete: Boolean
        get() = glyphs.all { it.centerStep >= it.centerSteps }

    private val fullComplete: Boolean
        get() = glyphs.all { it.fullStep >= it.fullSteps && it.fullAge >= 66 }

    private fun build(input: InputText) {
        val coordinates = input.positions.map { Coordinate(it.column, it.row) }
        if (coordinates.isEmpty()) {
            phase = Phase.Complete
            return
        }

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
        ).entries.associate { it.coordinate to it.color.asUInt }

        val center = canvasCenter()
        glyphs = ArrayList(input.scalars.indices.map { index ->
            val symbol = input.scalars[index]
            val target = coordinates[index]
            val centerTarget = when (options.expandDirection) {
                ExpandDirection.Vertical -> Coordinate(column = target.column, row = center.row)
                ExpandDirection.Horizontal -> Coordinate(column = center.column, row = target.row)
            }
            val finalColor = finalMapping[target] ?: (options.finalGradientStops.lastOrNull() ?: options.startingColor).asUInt
            val finalColorObj = Color(
                red = ((finalColor shr 16) and 0xFFu).toInt(),
                green = ((finalColor shr 8) and 0xFFu).toInt(),
                blue = (finalColor and 0xFFu).toInt()
            )
            val colors = Gradient(stops = listOf(options.startingColor, finalColorObj), steps = 10).spectrum.map { it.asUInt } + listOf(finalColor)
            val centerDistance = Geometry.lineLength(center, centerTarget)
            val fullDistance = Geometry.lineLength(centerTarget, target)
            Glyph(
                symbol = symbol,
                target = target,
                centerTarget = centerTarget,
                finalColors = colors,
                coordinate = center,
                centerSteps = PyCompat.roundHalfEven(centerDistance / options.centerMovementSpeed),
                fullSteps = PyCompat.roundHalfEven(fullDistance / options.fullMovementSpeed),
                centerDistance = centerDistance,
                fullDistance = fullDistance
            )
        })
    }

    private fun advanceCenter() {
        val center = canvasCenter()
        for (glyph in glyphs) {
            if (glyph.centerStep < glyph.centerSteps) {
                glyph.centerStep += 1
                val progress = glyph.centerStep.toDouble() / maxOf(glyph.centerSteps, 1).toDouble()
                val eased = options.centerEasing.value(progress)
                glyph.coordinate = Geometry.coordinateOnLine(from = center, to = glyph.centerTarget, t = eased)
            } else if (glyph.centerSteps == 0) {
                glyph.coordinate = glyph.centerTarget
            }
        }
    }

    private fun advanceFull() {
        for (glyph in glyphs) {
            if (glyph.fullStep < glyph.fullSteps) {
                glyph.fullStep += 1
                val progress = glyph.fullStep.toDouble() / maxOf(glyph.fullSteps, 1).toDouble()
                val eased = options.fullEasing.value(progress)
                glyph.coordinate = Geometry.coordinateOnLine(from = glyph.centerTarget, to = glyph.target, t = eased)
            } else if (glyph.fullSteps == 0) {
                glyph.coordinate = glyph.target
            }
            if (glyph.fullAge < 66) {
                glyph.fullAge += 1
            }
        }
    }

    private fun render(frame: Frame) {
        for (index in frame.cells.indices) {
            frame.cells[index] = Cell.BLANK
        }
        val winners = HashMap<Int, Glyph>()
        for (glyph in glyphs) {
            if (glyph.coordinate.column in 1..canvas.columns && glyph.coordinate.row in 1..canvas.rows) {
                val cellIndex = (canvas.rows - glyph.coordinate.row) * canvas.columns + glyph.coordinate.column - 1
                winners[cellIndex] = glyph
            }
        }
        for ((cellIndex, glyph) in winners) {
            frame.cells[cellIndex] = Cell(codepoint = glyph.symbol, foreground = foreground(glyph), background = 0u)
        }
    }

    private fun foreground(glyph: Glyph): UInt {
        if (phase != Phase.Full) return options.startingColor.asUInt
        val colorIndex = ((maxOf(glyph.fullAge, 1) - 1) / 6).coerceIn(0, glyph.finalColors.size - 1)
        return glyph.finalColors[colorIndex]
    }

    private fun canvasCenter(): Coordinate {
        return Coordinate(column = centered(canvas.columns), row = centered(canvas.rows))
    }

    private fun centered(size: Int): Int {
        var center = maxOf(PyCompat.floorDivide(size, 2), 1)
        if (size % 2 != 0 && size > 1) {
            center += 1
        }
        return center
    }
}
