package com.ttfx.effects

import com.ttfx.core.*

class ExpandEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    data class Configuration(
        val movementEasing: Easing = Easing.InOutQuart,
        val movementSpeed: Double = 0.35,
        val finalGradientStops: List<Color> = listOf(Color("8A008A"), Color("00D1FF"), Color("FFFFFF")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientDirection: GradientDirection = GradientDirection.Vertical
    ) {
        init {
            require(movementSpeed > 0) { "movement speed must be positive" }
        }
    }

    private class Glyph(
        val target: Coordinate,
        val symbol: Int,
        val colors: List<UInt>,
        val totalDistance: Double,
        val maxSteps: Int,
        var coordinate: Coordinate,
        var currentStep: Int = 0,
        var lastDistance: Double = 0.0,
        var pathActive: Boolean = true
    ) {
        val layer: Int
            get() = if (pathActive) 1 else 0

        val foreground: UInt
            get() {
                if (!pathActive) return colors.last()
                val total = maxOf(totalDistance, 1.0)
                val remaining = maxOf(totalDistance - lastDistance, 1.0)
                val reached = maxOf(total - remaining, 1.0)
                val progress = reached / total
                val index = PyCompat.roundHalfEven((colors.size - 1).toDouble() * progress).coerceIn(0, colors.size - 1)
                return colors[index]
            }
    }

    private var glyphs = ArrayList<Glyph>()
    private var isComplete = false

    init {
        build(input)
    }

    override fun tick(frame: Frame): TickStatus {
        if (isComplete) return TickStatus.Complete

        advancePaths()
        render(frame)
        if (glyphs.none { it.pathActive }) {
            isComplete = true
            return TickStatus.Complete
        }
        return TickStatus.Running
    }

    private fun build(input: InputText) {
        val coordinates = input.positions.map { Coordinate(it.column, it.row) }
        if (coordinates.isEmpty()) {
            isComplete = true
            return
        }

        val bottom = coordinates.minOf { it.row }
        val top = coordinates.maxOf { it.row }
        val left = coordinates.minOf { it.column }
        val right = coordinates.maxOf { it.column }

        val finalGradient = Gradient(options.finalGradientStops, options.finalGradientSteps)
        val finalColors = finalGradient.coordinateColorMapping(
            minRow = bottom,
            maxRow = top,
            minColumn = left,
            maxColumn = right,
            direction = options.finalGradientDirection
        )
        val colorByCoordinate = finalColors.entries.associate { it.coordinate to it.color }
        val center = canvasCenter()
        val startColor = finalGradient.spectrum[0]

        glyphs = ArrayList(input.scalars.indices.map { index ->
            val symbol = input.scalars[index]
            val target = coordinates[index]
            val colors = Gradient(
                stops = listOf(startColor, colorByCoordinate[target]!!),
                steps = 10
            ).spectrum.map { it.asUInt }
            val distance = Geometry.lineLength(center, target)
            Glyph(
                target = target,
                symbol = symbol,
                colors = colors,
                totalDistance = distance,
                maxSteps = PyCompat.roundHalfEven(distance / options.movementSpeed),
                coordinate = center
            )
        })
    }

    private fun advancePaths() {
        val center = canvasCenter()
        for (glyph in glyphs) {
            if (!glyph.pathActive) continue
            if (glyph.maxSteps <= 0) {
                glyph.coordinate = glyph.target
                glyph.pathActive = false
                continue
            }

            glyph.currentStep += 1
            val ratio = glyph.currentStep.toDouble() / glyph.maxSteps.toDouble()
            val distance = options.movementEasing.value(ratio) * glyph.totalDistance
            glyph.lastDistance = distance
            glyph.coordinate = Geometry.coordinateOnLine(
                from = center,
                to = glyph.target,
                t = distance / glyph.totalDistance
            )
            if (glyph.currentStep == glyph.maxSteps) {
                glyph.pathActive = false
            }
        }
    }

    private fun render(frame: Frame) {
        for (index in frame.cells.indices) {
            frame.cells[index] = Cell.BLANK
        }
        data class Winner(val layer: Int, val glyph: Glyph)
        val winners = HashMap<Int, Winner>()
        for (glyph in glyphs) {
            if (glyph.coordinate.column in 1..canvas.columns && glyph.coordinate.row in 1..canvas.rows) {
                val cellIndex = (canvas.rows - glyph.coordinate.row) * canvas.columns + glyph.coordinate.column - 1
                val winner = winners[cellIndex]
                if (winner != null && winner.layer > glyph.layer) continue
                winners[cellIndex] = Winner(glyph.layer, glyph)
            }
        }
        for ((cellIndex, winner) in winners) {
            frame.cells[cellIndex] = Cell(codepoint = winner.glyph.symbol, foreground = winner.glyph.foreground, background = 0u)
        }
    }

    private fun canvasCenter(): Coordinate {
        return Coordinate(
            column = centered(canvas.columns),
            row = centered(canvas.rows)
        )
    }

    private fun centered(size: Int): Int {
        var center = maxOf(PyCompat.floorDivide(size, 2), 1)
        if (size % 2 != 0 && size > 1) {
            center += 1
        }
        return center
    }
}
