package com.ttfx.effects

import com.ttfx.core.*
import kotlin.math.abs

class HighlightEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    enum class Direction {
        DiagonalTopLeftToBottomRight,
        DiagonalBottomRightToTopLeft,
        DiagonalBottomLeftToTopRight,
        DiagonalTopRightToBottomLeft,
        RowBottomToTop,
        RowTopToBottom,
        ColumnLeftToRight,
        ColumnRightToLeft,
        CenterToOutside,
        OutsideToCenter
    }

    data class Configuration(
        val highlightBrightness: Double = 1.75,
        val highlightDirection: Direction = Direction.DiagonalBottomLeftToTopRight,
        val highlightWidth: Int = 8,
        val finalGradientStops: List<Color> = listOf(Color("8A008A"), Color("00D1FF"), Color("FFFFFF")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientDirection: GradientDirection = GradientDirection.Vertical
    ) {
        init {
            require(highlightBrightness > 0) { "highlight brightness must be positive" }
            require(highlightWidth >= 1) { "highlight width must be at least one" }
        }
    }

    private class Glyph(
        val coordinate: Coordinate,
        val symbol: Int,
        val colors: List<UInt>,
        var visible: Boolean = true,
        var sceneIndex: Int = 0,
        var sceneTicksRemaining: Int = 2,
        var sceneActive: Boolean = false
    ) {
        val foreground: UInt
            get() = colors[sceneIndex]
    }

    private companion object {
        private const val EASING_STEPS = 100
    }

    private var glyphs = ArrayList<Glyph>()
    private var groups = ArrayList<List<Int>>()
    private var easingStep = 0
    private var previousLength = 0
    private var isComplete = false

    init {
        build(input)
    }

    private fun build(input: InputText) {
        val coordinates = input.positions.map { Coordinate(column = it.column, row = it.row) }
        if (coordinates.isEmpty()) {
            isComplete = true
            return
        }
        val bottom = coordinates.minOf { it.row }
        val top = coordinates.maxOf { it.row }
        val left = coordinates.minOf { it.column }
        val right = coordinates.maxOf { it.column }

        val finalGradient = Gradient(options.finalGradientStops, options.finalGradientSteps)
        val mapping = finalGradient.coordinateColorMapping(
            minRow = bottom,
            maxRow = top,
            minColumn = left,
            maxColumn = right,
            direction = options.finalGradientDirection
        ).entries.associate { it.coordinate to it.color }

        for ((pos, symbol) in input.positions.zip(input.scalars)) {
            val coordinate = Coordinate(column = pos.column, row = pos.row)
            val base = mapping[coordinate] ?: options.finalGradientStops.last()
            val bright = base.adjustBrightness(options.highlightBrightness)
            val gradient = Gradient(
                stops = listOf(base, bright, bright, base),
                steps = listOf(3, options.highlightWidth, 3)
            )
            glyphs.add(
                Glyph(
                    coordinate = coordinate,
                    symbol = symbol,
                    colors = gradient.spectrum.map { it.asUInt }
                )
            )
        }
        groups = groupedGlyphs()
    }

    override fun tick(frame: Frame): TickStatus {
        if (isComplete) return TickStatus.Complete

        advanceEaser()
        render(frame)
        advanceScenes()

        if (easingStep >= EASING_STEPS && glyphs.none { it.sceneActive }) {
            isComplete = true
            return TickStatus.Complete
        }
        return TickStatus.Running
    }

    private fun advanceEaser() {
        if (easingStep >= EASING_STEPS) return
        easingStep += 1
        val progress = easingStep.toDouble() / EASING_STEPS.toDouble()
        val eased = Easing.InOutCirc.value(progress).coerceIn(0.0, 1.0)
        val length = (eased * groups.size.toDouble()).toInt()
        if (length > previousLength) {
            for (groupIndex in previousLength until length) {
                for (index in groups[groupIndex]) {
                    activate(index)
                }
            }
        }
        previousLength = length
    }

    private fun activate(index: Int) {
        glyphs[index].visible = true
        glyphs[index].sceneIndex = 0
        glyphs[index].sceneTicksRemaining = 2
        glyphs[index].sceneActive = true
    }

    private fun advanceScenes() {
        for (index in glyphs.indices) {
            if (!glyphs[index].sceneActive) continue
            glyphs[index].sceneTicksRemaining -= 1
            if (glyphs[index].sceneTicksRemaining != 0) continue
            if (glyphs[index].sceneIndex + 1 < glyphs[index].colors.size) {
                glyphs[index].sceneIndex += 1
                glyphs[index].sceneTicksRemaining = 2
            } else {
                glyphs[index].sceneActive = false
            }
        }
    }

    private fun groupedGlyphs(): ArrayList<List<Int>> {
        val ordered = glyphs.indices.sortedWith { i1, i2 ->
            val lhs = glyphs[i1].coordinate
            val rhs = glyphs[i2].coordinate
            if (lhs.row != rhs.row) lhs.row.compareTo(rhs.row) else lhs.column.compareTo(rhs.column)
        }

        val key: (Glyph) -> Int
        val reverse: Boolean
        when (options.highlightDirection) {
            Direction.DiagonalTopLeftToBottomRight -> {
                key = { it.coordinate.column - it.coordinate.row }
                reverse = false
            }
            Direction.DiagonalBottomRightToTopLeft -> {
                key = { it.coordinate.column - it.coordinate.row }
                reverse = true
            }
            Direction.DiagonalBottomLeftToTopRight -> {
                key = { it.coordinate.column + it.coordinate.row }
                reverse = false
            }
            Direction.DiagonalTopRightToBottomLeft -> {
                key = { it.coordinate.column + it.coordinate.row }
                reverse = true
            }
            Direction.RowBottomToTop -> {
                key = { it.coordinate.row }
                reverse = false
            }
            Direction.RowTopToBottom -> {
                key = { it.coordinate.row }
                reverse = true
            }
            Direction.ColumnLeftToRight -> {
                key = { it.coordinate.column }
                reverse = false
            }
            Direction.ColumnRightToLeft -> {
                key = { it.coordinate.column }
                reverse = true
            }
            Direction.CenterToOutside, Direction.OutsideToCenter -> {
                val columns = glyphs.map { it.coordinate.column }
                val rows = glyphs.map { it.coordinate.row }
                val center = Coordinate(
                    column = columns.minOf { it } + (columns.maxOf { it } - columns.minOf { it }) / 2,
                    row = rows.minOf { it } + (rows.maxOf { it } - rows.minOf { it }) / 2
                )
                key = { abs(it.coordinate.column - center.column) + abs(it.coordinate.row - center.row) }
                reverse = options.highlightDirection == Direction.OutsideToCenter
            }
        }

        val buckets = LinkedHashMap<Int, MutableList<Int>>()
        for (index in ordered) {
            buckets.getOrPut(key(glyphs[index])) { ArrayList() }.add(index)
        }
        val result = ArrayList<List<Int>>(buckets.keys.sorted().map { buckets[it]!! })
        if (reverse) {
            result.reverse()
        }
        return result
    }

    private fun render(frame: Frame) {
        for (index in frame.cells.indices) {
            frame.cells[index] = Cell.BLANK
        }
        for (glyph in glyphs) {
            if (!glyph.visible) continue
            if (glyph.coordinate.column in 1..canvas.columns && glyph.coordinate.row in 1..canvas.rows) {
                val cellIndex = (canvas.rows - glyph.coordinate.row) * canvas.columns + glyph.coordinate.column - 1
                frame.cells[cellIndex] = Cell(codepoint = glyph.symbol, foreground = glyph.foreground, background = 0u)
            }
        }
    }
}
