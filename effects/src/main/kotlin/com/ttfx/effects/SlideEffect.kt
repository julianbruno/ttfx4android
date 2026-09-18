package com.ttfx.effects

import com.ttfx.core.*
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class SlideEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    enum class Grouping {
        Row, Column, Diagonal
    }

    enum class MovementEasing {
        Linear, InOutQuad, OutExpo, OutCirc, InOutQuart, InOutExpo, OutSine, InOutSine
    }

    data class Configuration(
        val movementSpeed: Double = 0.8,
        val grouping: Grouping = Grouping.Row,
        val gap: Int = 2,
        val reverseDirection: Boolean = false,
        val merge: Boolean = false,
        val movementEasing: MovementEasing = MovementEasing.InOutQuad,
        val finalGradientStops: List<Color> = listOf(Color("833ab4"), Color("fd1d1d"), Color("fcb045")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientFrames: Int = 6,
        val finalGradientDirection: GradientDirection = GradientDirection.Vertical
    ) {
        init {
            require(movementSpeed > 0) { "movement speed must be positive" }
            require(gap >= 0) { "gap must not be negative" }
            require(finalGradientFrames > 0) { "gradient frames must be positive" }
        }
    }

    private class Glyph(
        val inputCoordinate: Coordinate,
        val symbol: Int,
        val colors: List<UInt>,
        var coordinate: Coordinate,
        var pathOrigin: Coordinate,
        var visible: Boolean = false,
        var pathStep: Int = 0,
        var pathSteps: Int = 0,
        var pathActive: Boolean = false,
        var sceneIndex: Int = 0,
        var sceneTicksRemaining: Int,
        var sceneActive: Boolean = false,
        var displayedForeground: UInt? = null
    ) {
        val foreground: UInt
            get() = displayedForeground ?: colors[sceneIndex]
    }

    private var glyphs = ArrayList<Glyph>()
    private var pendingGroups = ArrayList<MutableList<Int>>()
    private var activeGroups = ArrayList<MutableList<Int>>()
    private var currentGap = 0
    private var isComplete = false

    init {
        build(input)
    }

    override fun tick(frame: Frame): TickStatus {
        if (isComplete) return TickStatus.Complete

        releaseNextGroupCharacters()
        updateGlyphs()
        render(frame)

        if (!hasPendingWork) {
            isComplete = true
            return TickStatus.Complete
        }
        return TickStatus.Running
    }

    private fun build(input: InputText) {
        val positions = input.positions.map { Coordinate(column = it.column, row = it.row) }
        if (positions.isEmpty()) {
            isComplete = true
            return
        }

        val bottom = positions.minOf { it.row }
        val top = positions.maxOf { it.row }
        val left = positions.minOf { it.column }
        val right = positions.maxOf { it.column }
        val finalGradient = Gradient(options.finalGradientStops, options.finalGradientSteps)
        val finalColors = finalGradient.coordinateColorMapping(
            minRow = bottom,
            maxRow = top,
            minColumn = left,
            maxColumn = right,
            direction = options.finalGradientDirection
        )
        val colorByCoordinate = finalColors.entries.associate { it.coordinate to it.color.asUInt }

        glyphs = ArrayList(input.scalars.indices.map { index ->
            val scalar = input.scalars[index]
            val coordinate = positions[index]
            val targetColor = colorByCoordinate[coordinate]!!
            val targetColorObj = Color(
                red = ((targetColor shr 16) and 0xFFu).toInt(),
                green = ((targetColor shr 8) and 0xFFu).toInt(),
                blue = (targetColor and 0xFFu).toInt()
            )
            val colors = Gradient(
                stops = listOf(options.finalGradientStops[0], targetColorObj),
                steps = 10
            ).spectrum.map { it.asUInt }
            Glyph(
                inputCoordinate = coordinate,
                symbol = scalar,
                colors = colors,
                coordinate = coordinate,
                pathOrigin = coordinate,
                sceneTicksRemaining = options.finalGradientFrames,
                sceneActive = false
            )
        })

        val groups = groupedGlyphIndices().toMutableList()
        for (index in groups.indices) {
            val original = ArrayList(groups[index])
            val groupStart = initialCoordinate(groups[index], original, index)
            for (glyphIndex in groups[index]) {
                val glyphStart = startingCoordinate(glyphIndex, groupStart)
                glyphs[glyphIndex].coordinate = glyphStart
                glyphs[glyphIndex].pathOrigin = glyphStart
            }
        }
        pendingGroups = ArrayList(groups)
    }

    private fun groupedGlyphIndices(): List<MutableList<Int>> {
        val indices = glyphs.indices.sortedWith { i, j ->
            val left = glyphs[i].inputCoordinate
            val right = glyphs[j].inputCoordinate
            if (left.row != right.row) left.row.compareTo(right.row)
            else left.column.compareTo(right.column)
        }
        val keys: List<Int>
        val keyFn: (Glyph) -> Int
        when (options.grouping) {
            Grouping.Row -> {
                keys = glyphs.map { it.inputCoordinate.row }.toSet().sortedDescending()
                keyFn = { it.inputCoordinate.row }
            }
            Grouping.Column -> {
                keys = glyphs.map { it.inputCoordinate.column }.toSet().sorted()
                keyFn = { it.inputCoordinate.column }
            }
            Grouping.Diagonal -> {
                keys = glyphs.map { it.inputCoordinate.column - it.inputCoordinate.row }.toSet().sorted()
                keyFn = { it.inputCoordinate.column - it.inputCoordinate.row }
            }
        }
        return keys.map { value -> indices.filter { keyFn(glyphs[it]) == value }.toMutableList() }
    }

    private fun initialCoordinate(group: MutableList<Int>, original: List<Int>, index: Int): Coordinate {
        return when (options.grouping) {
            Grouping.Row -> {
                val startsFromRight = options.merge && (index % 2 == 0)
                if (!startsFromRight) group.reverse()
                if (options.reverseDirection && !options.merge) {
                    group.reverse()
                    Coordinate(column = canvas.columns + 1, row = glyphs[group[0]].inputCoordinate.row)
                } else {
                    Coordinate(
                        column = if (startsFromRight) canvas.columns + 1 else 0,
                        row = glyphs[group[0]].inputCoordinate.row
                    )
                }
            }
            Grouping.Column -> {
                val startsFromBottom = options.merge && (index % 2 == 0)
                if (!startsFromBottom) group.reverse()
                if (options.reverseDirection && !options.merge) {
                    group.reverse()
                    Coordinate(column = glyphs[group[0]].inputCoordinate.column, row = 0)
                } else {
                    Coordinate(
                        column = glyphs[group[0]].inputCoordinate.column,
                        row = if (startsFromBottom) 0 else canvas.rows + 1
                    )
                }
            }
            Grouping.Diagonal -> {
                val last = glyphs[original.last()].inputCoordinate
                val bottomDistance = last.row
                var start = Coordinate(column = last.column - bottomDistance, row = last.row - bottomDistance)
                if (options.merge && (index % 2 == 0)) {
                    group.reverse()
                    val first = glyphs[original[0]].inputCoordinate
                    val distance = canvas.rows + 1 - first.row
                    start = Coordinate(column = first.column + distance, row = first.row + distance)
                }
                if (options.reverseDirection && !options.merge) {
                    group.reverse()
                    val first = glyphs[original[0]].inputCoordinate
                    val distance = canvas.rows + 1 - first.row
                    start = Coordinate(column = first.column + distance, row = first.row + distance)
                }
                start
            }
        }
    }

    private fun startingCoordinate(glyphIndex: Int, from: Coordinate): Coordinate {
        return when (options.grouping) {
            Grouping.Row -> Coordinate(column = from.column, row = glyphs[glyphIndex].inputCoordinate.row)
            Grouping.Column -> Coordinate(column = glyphs[glyphIndex].inputCoordinate.column, row = from.row)
            Grouping.Diagonal -> from
        }
    }

    private fun releaseNextGroupCharacters() {
        if (currentGap == options.gap && pendingGroups.isNotEmpty()) {
            activeGroups.add(pendingGroups.removeAt(0))
            currentGap = 0
        } else if (pendingGroups.isNotEmpty()) {
            currentGap += 1
        }

        for (index in activeGroups.indices) {
            if (activeGroups[index].isNotEmpty()) {
                activate(activeGroups[index].removeAt(0))
            }
        }
        activeGroups.removeAll { it.isEmpty() }
    }

    private fun activate(index: Int) {
        val distance = Geometry.lineLength(glyphs[index].coordinate, glyphs[index].inputCoordinate)
        glyphs[index].pathSteps = PyCompat.roundHalfEven(distance / options.movementSpeed)
        glyphs[index].pathActive = true
        glyphs[index].visible = true
        glyphs[index].sceneActive = true
    }

    private fun updateGlyphs() {
        for (index in glyphs.indices) {
            if (glyphs[index].pathActive || glyphs[index].sceneActive) {
                updatePath(index)
                updateScene(index)
            }
        }
    }

    private fun updatePath(index: Int) {
        if (!glyphs[index].pathActive) return
        if (glyphs[index].pathSteps <= 0) {
            glyphs[index].coordinate = glyphs[index].inputCoordinate
            glyphs[index].pathActive = false
            return
        }
        glyphs[index].pathStep += 1
        val ratio = glyphs[index].pathStep.toDouble() / glyphs[index].pathSteps.toDouble()
        val eased = easingValue(ratio)
        glyphs[index].coordinate = Geometry.coordinateOnLine(
            from = glyphs[index].pathOrigin,
            to = glyphs[index].inputCoordinate,
            t = eased
        )
        if (glyphs[index].pathStep == glyphs[index].pathSteps) {
            glyphs[index].pathActive = false
        }
    }

    private fun updateScene(index: Int) {
        if (!glyphs[index].sceneActive) return
        glyphs[index].displayedForeground = glyphs[index].colors[glyphs[index].sceneIndex]
        glyphs[index].sceneTicksRemaining -= 1
        if (glyphs[index].sceneTicksRemaining == 0) {
            if (glyphs[index].sceneIndex + 1 < glyphs[index].colors.size) {
                glyphs[index].sceneIndex += 1
                glyphs[index].sceneTicksRemaining = options.finalGradientFrames
            } else {
                glyphs[index].sceneActive = false
            }
        }
    }

    private fun render(frame: Frame) {
        for (index in frame.cells.indices) {
            frame.cells[index] = Cell.BLANK
        }
        for (glyph in glyphs) {
            if (glyph.visible) {
                val coordinate = glyph.coordinate
                if (coordinate.column in 1..canvas.columns && coordinate.row in 1..canvas.rows) {
                    val cellIndex = (canvas.rows - coordinate.row) * canvas.columns + coordinate.column - 1
                    frame.cells[cellIndex] = Cell(codepoint = glyph.symbol, foreground = glyph.foreground, background = 0u)
                }
            }
        }
    }

    private val hasPendingWork: Boolean
        get() = pendingGroups.isNotEmpty() || activeGroups.isNotEmpty() || glyphs.any { it.pathActive || it.sceneActive }

    private fun easingValue(value: Double): Double {
        return when (options.movementEasing) {
            MovementEasing.Linear -> value
            MovementEasing.InOutQuad -> {
                if (value < 0.5) 2.0 * value * value
                else {
                    val inverse = -2.0 * value + 2.0
                    1.0 - inverse * inverse / 2.0
                }
            }
            MovementEasing.OutExpo -> if (value == 1.0) 1.0 else 1.0 - 2.0.pow(-10.0 * value)
            MovementEasing.OutCirc -> sqrt(1.0 - (value - 1.0).pow(2))
            MovementEasing.InOutQuart -> {
                if (value < 0.5) 8.0 * value.pow(4)
                else 1.0 - (-2.0 * value + 2.0).pow(4) / 2.0
            }
            MovementEasing.InOutExpo -> {
                when {
                    value == 0.0 || value == 1.0 -> value
                    value < 0.5 -> 2.0.pow(20.0 * value - 10.0) / 2.0
                    else -> (2.0 - 2.0.pow(-20.0 * value + 10.0)) / 2.0
                }
            }
            MovementEasing.OutSine -> sin(value * (Math.PI / 2.0))
            MovementEasing.InOutSine -> -(cos(Math.PI * value) - 1.0) / 2.0
        }
    }
}
