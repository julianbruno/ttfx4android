package com.ttfx.effects

import com.ttfx.core.*
import kotlin.math.abs

class PrintEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    private val options: Configuration = Configuration()
) : Effect {

    data class Configuration(
        val printSpeed: Int = 2,
        val printHeadReturnSpeed: Double = 1.5,
        val printHeadEasing: Easing = Easing.InOutQuad,
        val finalGradientStops: List<Color> = listOf(Color("02b8bd"), Color("c1f0e3"), Color("00ffa0")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientDirection: GradientDirection = GradientDirection.Diagonal
    ) {
        init {
            require(printSpeed > 0) { "print speed must be positive" }
            require(printHeadReturnSpeed > 0.0) { "print head return speed must be positive" }
        }
    }

    private data class Glyph(
        val originalCoordinate: Coordinate,
        var coordinate: Coordinate,
        val frames: List<Pair<Int, UInt>>,
        val isFill: Boolean = false,
        var visible: Boolean = false,
        var sceneTick: Int = 0,
        var sceneActive: Boolean = false
    ) {
        val visual: Pair<Int, UInt>
            get() = frames[minOf(sceneTick / 3, frames.size - 1)]
    }

    private data class CarriageReturn(
        val startColumn: Int,
        val targetColumn: Int,
        val steps: Int,
        var currentStep: Int = 0
    )

    private val rows = ArrayList<List<Int>>()
    private val glyphs = ArrayList<Glyph>()
    private var currentRow = 0
    private var currentGlyph = 0
    private val processedRows = ArrayList<Int>()
    private var headColumn = 1
    private var headVisible = true
    private var headForeground: UInt = PRINT_HEAD_COLOR
    private var carriageReturn: CarriageReturn? = null
    private var didBuild = false
    private var isComplete = false

    init {
        build(input)
    }

    override fun tick(frame: Frame): TickStatus {
        if (isComplete) return TickStatus.Complete

        schedulePrintWork()
        advanceCarriageReturn()
        render(frame)
        advanceGlyphScenes()

        if (!hasPendingWork) {
            isComplete = true
            return TickStatus.Complete
        }
        return TickStatus.Running
    }

    private fun build(input: InputText) {
        if (didBuild) return
        didBuild = true

        val positions = HashMap<Coordinate, Int>()
        for (i in input.positions.indices) {
            val pos = input.positions[i]
            positions[Coordinate(pos.column, pos.row)] = input.scalars[i]
        }

        val textCoordinates = input.positions.map { Coordinate(it.column, it.row) }
        val textLeft = textCoordinates.minOfOrNull { it.column } ?: 1
        val textRight = textCoordinates.maxOfOrNull { it.column } ?: 1
        val textBottom = textCoordinates.minOfOrNull { it.row } ?: 1
        val textTop = textCoordinates.maxOfOrNull { it.row } ?: 1

        val finalColors = finalColorMap(
            bottom = textBottom,
            top = textTop,
            left = textLeft,
            right = textRight
        )

        for (row in canvas.rows downTo 1) {
            var lastColumn = 1
            for (col in 1..canvas.columns) {
                if (positions.containsKey(Coordinate(col, row))) {
                    lastColumn = maxOf(lastColumn, col)
                }
            }
            val identifiers = ArrayList<Int>()
            for (col in 1..lastColumn) {
                val coordinate = Coordinate(col, row)
                val symbol = positions[coordinate] ?: Cell.BLANK.codepoint
                val finalForeground = finalColors[coordinate] ?: PRINT_HEAD_COLOR
                val frames = fadeFrames(symbol, finalForeground)
                identifiers.add(glyphs.size)
                glyphs.add(
                    Glyph(
                        originalCoordinate = coordinate,
                        coordinate = Coordinate(col, 1),
                        frames = frames,
                        isFill = !positions.containsKey(coordinate)
                    )
                )
            }
            rows.add(identifiers)
        }
    }

    private fun finalColorMap(bottom: Int, top: Int, left: Int, right: Int): Map<Coordinate, UInt> {
        val gradient = Gradient(stops = options.finalGradientStops, steps = options.finalGradientSteps)
        val mapping = gradient.coordinateColorMapping(
            minRow = bottom,
            maxRow = top,
            minColumn = left,
            maxColumn = right,
            direction = options.finalGradientDirection
        )
        return mapping.entries.associate { it.coordinate to it.color.asUInt }
    }

    private fun fadeFrames(symbol: Int, finalForeground: UInt): List<Pair<Int, UInt>> {
        val white = Color("ffffff")
        val finalColor = Color(
            ((finalForeground shr 16) and 0xFFu).toInt(),
            ((finalForeground shr 8) and 0xFFu).toInt(),
            (finalForeground and 0xFFu).toInt()
        )
        val colors = Gradient(stops = listOf(white, finalColor), steps = 5).spectrum
        val symbols = listOf("█", "▓", "▒", "░").map { it.codePointAt(0) } + listOf(symbol)
        return cyclicPairing(colors, symbols).map { (color, sym) -> sym to color.asUInt }
    }

    private fun <T, U> cyclicPairing(larger: List<T>, smaller: List<U>): List<Pair<T, U>> {
        val repeatFactor = larger.size / smaller.size
        var overflowCount = larger.size % smaller.size
        var overflowUsed = false
        var smallerIndex = 0
        var currentRepeatFactor = 0
        return larger.map { value ->
            if (currentRepeatFactor >= repeatFactor) {
                if (overflowCount > 0) {
                    if (overflowUsed) {
                        smallerIndex++
                        currentRepeatFactor = 0
                        overflowUsed = false
                    } else {
                        overflowUsed = true
                        overflowCount--
                    }
                } else {
                    smallerIndex++
                    currentRepeatFactor = 0
                }
            }
            currentRepeatFactor++
            value to smaller[smallerIndex]
        }
    }

    private fun schedulePrintWork() {
        if (carriageReturn != null) return
        if (currentRow >= rows.size) return

        if (currentGlyph < rows[currentRow].size) {
            val end = minOf(currentGlyph + options.printSpeed, rows[currentRow].size)
            for (index in currentGlyph until end) {
                val glyphIdx = rows[currentRow][index]
                glyphs[glyphIdx].visible = true
                glyphs[glyphIdx].sceneActive = true
                headColumn = glyphs[glyphIdx].originalCoordinate.column
            }
            currentGlyph = end
            return
        }

        processedRows.add(currentRow)
        if (currentRow + 1 == rows.size) {
            currentRow++
            return
        }

        for (row in processedRows) {
            for (glyphIdx in rows[row]) {
                val g = glyphs[glyphIdx]
                g.coordinate = Coordinate(g.coordinate.column, g.coordinate.row + 1)
            }
        }

        val previousHasText = rows[currentRow].any { !glyphs[it].isFill }
        currentRow++
        if (previousHasText) {
            val firstText = rows[currentRow].indexOfFirst { !glyphs[it].isFill }
            if (firstText > 0) {
                val trimmed = rows[currentRow].drop(firstText)
                rows[currentRow] = trimmed
            }
        }
        currentGlyph = 0
        headVisible = true
        headForeground = 0u
        val targetColumn = glyphs[rows[currentRow][0]].originalCoordinate.column
        val distance = abs(headColumn - targetColumn)
        carriageReturn = CarriageReturn(
            startColumn = headColumn,
            targetColumn = targetColumn,
            steps = PyCompat.roundHalfEven(distance.toDouble() / options.printHeadReturnSpeed)
        )
    }

    private fun advanceCarriageReturn() {
        val cr = carriageReturn ?: return
        if (cr.steps <= 0) {
            headColumn = cr.targetColumn
            headVisible = false
            carriageReturn = null
            return
        }

        cr.currentStep++
        val fraction = cr.currentStep.toDouble() / cr.steps.toDouble()
        val easing = options.printHeadEasing.value(fraction)
        val distance = abs(cr.targetColumn - cr.startColumn).toDouble()
        headColumn = Geometry.coordinateOnLine(
            from = Coordinate(cr.startColumn, 1),
            to = Coordinate(cr.targetColumn, 1),
            t = if (distance == 0.0) 1.0 else (easing * distance) / distance
        ).column
        if (cr.currentStep == cr.steps) {
            headVisible = false
            carriageReturn = null
        }
    }

    private fun advanceGlyphScenes() {
        for (g in glyphs) {
            if (g.sceneActive) {
                g.sceneTick++
                if (g.sceneTick == g.frames.size * 3) {
                    g.sceneActive = false
                    g.sceneTick--
                }
            }
        }
    }

    private fun render(frame: Frame) {
        for (i in frame.cells.indices) {
            frame.cells[i] = Cell.BLANK
        }
        for (glyph in glyphs) {
            if (glyph.visible) {
                write(glyph.visual, glyph.coordinate, frame)
            }
        }
        if (headVisible) {
            write("█".codePointAt(0) to headForeground, Coordinate(headColumn, 1), frame)
        }
    }

    private fun write(visual: Pair<Int, UInt>, coordinate: Coordinate, frame: Frame) {
        if (coordinate.column in 1..canvas.columns && coordinate.row in 1..canvas.rows) {
            frame[coordinate.column, coordinate.row] = Cell(
                codepoint = visual.first,
                foreground = visual.second,
                background = 0u
            )
        }
    }

    private val hasPendingWork: Boolean
        get() = currentRow < rows.size || carriageReturn != null || glyphs.any { it.sceneActive }

    companion object {
        const val PRINT_HEAD_COLOR: UInt = 0xFFFFFFu
    }
}
