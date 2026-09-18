package com.ttfx.effects

import com.ttfx.core.*
import kotlin.math.abs

class WipeEffect(
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
        val direction: Direction = Direction.DiagonalTopLeftToBottomRight,
        val delay: Int = 0,
        val easing: Easing = Easing.InOutCirc,
        val finalGradientStops: List<Color> = listOf(Color("833ab4"), Color("fd1d1d"), Color("fcb045")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientFrames: Int = 3,
        val finalGradientDirection: GradientDirection = GradientDirection.Vertical
    ) {
        init {
            require(delay >= 0) { "wipe delay must not be negative" }
            require(finalGradientFrames > 0) { "gradient frames must be positive" }
        }
    }

    private companion object {
        private const val EASING_STEPS = 100
    }

    private class Glyph(
        val coordinate: Coordinate,
        val symbol: Int,
        val colors: List<UInt>,
        var visible: Boolean = false,
        var sceneIndex: Int = 0,
        var sceneTicksRemaining: Int,
        var sceneActive: Boolean = false
    ) {
        val foreground: UInt
            get() = colors[sceneIndex]
    }

    private var glyphs = ArrayList<Glyph>()
    private var groups = ArrayList<List<Int>>()
    private var easingStep = 0
    private var previousLength = 0
    private var delayRemaining = options.delay
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

        val finalGradient = Gradient(stops = options.finalGradientStops, steps = options.finalGradientSteps)
        val mapping = finalGradient.coordinateColorMapping(
            minRow = bottom,
            maxRow = top,
            minColumn = left,
            maxColumn = right,
            direction = options.finalGradientDirection
        ).entries.associate { it.coordinate to it.color }
        val startColor = finalGradient.spectrum[0]

        for ((pos, symbol) in input.positions.zip(input.scalars)) {
            val coordinate = Coordinate(column = pos.column, row = pos.row)
            val scene = Gradient(
                stops = listOf(startColor, mapping[coordinate] ?: startColor),
                steps = options.finalGradientSteps
            )
            glyphs.add(
                Glyph(
                    coordinate = coordinate,
                    symbol = symbol,
                    colors = scene.spectrum.map { it.asUInt },
                    sceneTicksRemaining = options.finalGradientFrames
                )
            )
        }
        groups = groupedGlyphs()
    }

    override fun tick(frame: Frame): TickStatus {
        if (isComplete) return TickStatus.Complete

        if (delayRemaining == 0) {
            advanceEaser()
            delayRemaining = options.delay
        } else {
            delayRemaining -= 1
        }
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
        val eased = options.easing.value(progress).coerceIn(0.0, 1.0)
        val length = (eased * groups.size.toDouble()).toInt()

        if (length > previousLength) {
            for (groupIndex in previousLength until length) {
                for (index in groups[groupIndex]) {
                    activate(index)
                }
            }
        } else if (length < previousLength) {
            for (groupIndex in length until previousLength) {
                for (index in groups[groupIndex]) {
                    removeAndReset(index)
                }
            }
        }
        previousLength = length
    }

    private fun activate(index: Int) {
        glyphs[index].visible = true
        glyphs[index].sceneIndex = 0
        glyphs[index].sceneTicksRemaining = options.finalGradientFrames
        glyphs[index].sceneActive = true
    }

    private fun removeAndReset(index: Int) {
        glyphs[index].sceneActive = false
        glyphs[index].sceneIndex = 0
        glyphs[index].sceneTicksRemaining = options.finalGradientFrames
        glyphs[index].visible = false
    }

    private fun advanceScenes() {
        for (index in glyphs.indices) {
            if (!glyphs[index].sceneActive) continue
            glyphs[index].sceneTicksRemaining -= 1
            if (glyphs[index].sceneTicksRemaining != 0) continue
            if (glyphs[index].sceneIndex + 1 < glyphs[index].colors.size) {
                glyphs[index].sceneIndex += 1
                glyphs[index].sceneTicksRemaining = options.finalGradientFrames
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
        when (options.direction) {
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
                reverse = options.direction == Direction.OutsideToCenter
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
