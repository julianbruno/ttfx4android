package com.ttfx.effects

import com.ttfx.core.*
import kotlin.math.abs

class OrbittingVolleyEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    data class Configuration(
        val topLauncherSymbol: String = "█",
        val rightLauncherSymbol: String = "█",
        val bottomLauncherSymbol: String = "█",
        val leftLauncherSymbol: String = "█",
        val launcherMovementSpeed: Double = 0.8,
        val characterMovementSpeed: Double = 1.5,
        val volleySize: Double = 0.03,
        val launchDelay: Int = 30,
        val characterEasing: Easing = Easing.OutSine,
        val finalGradientStops: List<Color> = listOf(Color("FFA15C"), Color("44D492")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientDirection: GradientDirection = GradientDirection.Radial
    ) {
        init {
            require(launcherMovementSpeed > 0.0) { "launcher movement speed must be positive" }
            require(characterMovementSpeed > 0.0) { "character movement speed must be positive" }
            require(volleySize >= 0.0) { "volley size must be non-negative" }
            require(launchDelay >= 0) { "launch delay must be non-negative" }
        }
    }

    private class PathState(
        val waypoints: List<Coordinate>,
        val speed: Double,
        val easing: Easing,
        val loop: Boolean
    ) {
        var segment: Int = 0
        var stepInSegment: Int = 0
        var active: Boolean = true

        data class StepResult(val coordinate: Coordinate, val finished: Boolean)

        fun step(): StepResult {
            val start = waypoints[segment]
            val end = waypoints[segment + 1]
            val steps = maxOf(1, PyCompat.roundHalfEven(Geometry.lineLength(start, end) / speed))
            stepInSegment += 1
            val progress = minOf(stepInSegment.toDouble() / steps.toDouble(), 1.0)
            val coordinate = Geometry.coordinateOnLine(start, end, easing.value(progress))
            if (stepInSegment == steps) {
                if (segment + 2 < waypoints.size) {
                    segment += 1
                    stepInSegment = 0
                } else if (loop) {
                    active = false
                } else {
                    active = false
                }
            }
            return StepResult(coordinate, !active && !loop)
        }

        fun restartLoop() {
            segment = 0
            stepInSegment = 0
            active = true
        }
    }

    private class Glyph(
        val id: Int,
        val symbol: Int,
        val target: Coordinate,
        val finalForeground: UInt,
        var coordinate: Coordinate,
        var visible: Boolean = false,
        var layer: Int = 0,
        var path: PathState? = null
    )

    private class Launcher(
        val id: Int,
        val symbol: Int,
        val inputCoordinate: Coordinate,
        var coordinate: Coordinate,
        var foreground: UInt,
        var magazine: MutableList<Int> = ArrayList(),
        var path: PathState? = null,
        var visible: Boolean = true
    )

    private var glyphs = ArrayList<Glyph>()
    private var launchers = ArrayList<Launcher>()
    private var launcherColorByCoordinate = HashMap<Coordinate, UInt>()
    private var delay = 0
    private var emittedFinalFrame = false
    private var isComplete = false

    init {
        build(input)
    }

    override fun tick(frame: Frame): TickStatus {
        if (isComplete) return TickStatus.Complete
        if (hasWorkRemaining) {
            restartMainLauncherIfNeeded()
            updateLauncherAppearances()
            launchVolleyIfDue()
            stepActivePaths()
            render(frame)
            return TickStatus.Running
        }
        if (!emittedFinalFrame) {
            emittedFinalFrame = true
            for (index in launchers.indices) {
                launchers[index].visible = false
            }
            render(frame)
            isComplete = true
            return TickStatus.Complete
        }
        return TickStatus.Complete
    }

    private val hasWorkRemaining: Boolean
        get() = launchers.any { it.magazine.isNotEmpty() } ||
                (glyphs.count { it.visible && it.path != null } + if (launchers.firstOrNull()?.path?.active == true) 1 else 0) > 1

    private fun build(input: InputText) {
        if (input.scalars.isEmpty()) {
            isComplete = true
            return
        }
        val coordinates = input.positions.map { Coordinate(it.column, it.row) }
        val textBottom = coordinates.minOf { it.row }
        val textTop = coordinates.maxOf { it.row }
        val textLeft = coordinates.minOf { it.column }
        val textRight = coordinates.maxOf { it.column }
        val textCenter = Coordinate(
            column = textLeft + PyCompat.floorDivide(textRight - textLeft, 2),
            row = textBottom + PyCompat.floorDivide(textTop - textBottom, 2)
        )
        val finalGradient = Gradient(options.finalGradientStops, options.finalGradientSteps)
        val finalMap = finalGradient.coordinateColorMapping(
            minRow = textBottom,
            maxRow = textTop,
            minColumn = textLeft,
            maxColumn = textRight,
            direction = options.finalGradientDirection
        ).entries.associate { it.coordinate to it.color.asUInt }

        launcherColorByCoordinate = HashMap(
            finalGradient.coordinateColorMapping(
                minRow = 1,
                maxRow = canvas.rows,
                minColumn = 1,
                maxColumn = canvas.columns,
                direction = options.finalGradientDirection
            ).entries.associate { it.coordinate to it.color.asUInt }
        )

        glyphs = ArrayList(input.scalars.indices.map { index ->
            val scalar = input.scalars[index]
            val target = coordinates[index]
            Glyph(id = index, symbol = scalar, target = target, finalForeground = finalMap[target]!!, coordinate = target)
        })

        val launcherSymbols = listOf(
            options.topLauncherSymbol,
            options.rightLauncherSymbol,
            options.bottomLauncherSymbol,
            options.leftLauncherSymbol
        ).map { it.codePoints().findFirst().orElse(32) }

        val launcherCoordinates = listOf(
            Coordinate(column = 1, row = canvas.rows),
            Coordinate(column = canvas.columns, row = canvas.rows),
            Coordinate(column = canvas.columns, row = 1),
            Coordinate(column = 1, row = 1)
        )

        launchers = ArrayList(launcherCoordinates.indices.map { index ->
            val coordinate = launcherCoordinates[index]
            Launcher(
                id = glyphs.size + index,
                symbol = launcherSymbols[index],
                inputCoordinate = coordinate,
                coordinate = coordinate,
                foreground = if (index == 0) finalGradient.spectrum.last().asUInt else launcherColorByCoordinate[coordinate]!!
            )
        })

        launchers[0].path = PathState(
            waypoints = listOf(Coordinate(column = 1, row = canvas.rows), Coordinate(column = canvas.columns, row = canvas.rows)),
            speed = options.launcherMovementSpeed,
            easing = Easing.Linear,
            loop = true
        )

        val sorted = glyphs.indices.sortedWith { lhs, rhs ->
            val left = glyphs[lhs].target
            val right = glyphs[rhs].target
            val leftDistance = abs(left.column - textCenter.column) + abs(left.row - textCenter.row)
            val rightDistance = abs(right.column - textCenter.column) + abs(right.row - textCenter.row)
            if (leftDistance != rightDistance) leftDistance.compareTo(rightDistance)
            else if (left.row != right.row) left.row.compareTo(right.row)
            else left.column.compareTo(right.column)
        }
        for ((offset, glyphIndex) in sorted.withIndex()) {
            launchers[offset % launchers.size].magazine.add(glyphIndex)
        }
    }

    private fun restartMainLauncherIfNeeded() {
        if (launchers[0].path?.active == true) return
        launchers[0].coordinate = Coordinate(column = 1, row = canvas.rows)
        launchers[0].path?.restartLoop()
    }

    private fun updateLauncherAppearances() {
        launchers[0].foreground = launcherColorByCoordinate[launchers[0].coordinate]!!
        val progress = launchers[0].coordinate.column.toDouble() / canvas.columns.toDouble()
        for (index in 1 until launchers.size) {
            when (launchers[index].inputCoordinate) {
                Coordinate(column = canvas.columns, row = canvas.rows) -> {
                    val row = canvas.rows - (canvas.rows.toDouble() * progress).toInt()
                    launchers[index].coordinate = Coordinate(column = canvas.columns, row = maxOf(1, row))
                }
                Coordinate(column = canvas.columns, row = 1) -> {
                    val column = canvas.columns - (canvas.columns.toDouble() * progress).toInt()
                    launchers[index].coordinate = Coordinate(column = maxOf(1, column), row = 1)
                }
                Coordinate(column = 1, row = 1) -> {
                    val row = 1 + (canvas.rows.toDouble() * progress).toInt()
                    launchers[index].coordinate = Coordinate(column = 1, row = minOf(canvas.rows, row))
                }
                else -> Unit
            }
            launchers[index].foreground = launcherColorByCoordinate[launchers[index].coordinate]!!
        }
    }

    private fun launchVolleyIfDue() {
        if (delay == 0) {
            val charactersToLaunch = maxOf(((options.volleySize * glyphs.size.toDouble()) / 4.0).toInt(), 1)
            for (launcherIndex in launchers.indices) {
                for (c in 0 until charactersToLaunch) {
                    if (launchers[launcherIndex].magazine.isEmpty()) break
                    val glyphIndex = launchers[launcherIndex].magazine.removeAt(0)
                    glyphs[glyphIndex].coordinate = launchers[launcherIndex].coordinate
                    glyphs[glyphIndex].visible = true
                    glyphs[glyphIndex].layer = 1
                    glyphs[glyphIndex].path = PathState(
                        waypoints = listOf(launchers[launcherIndex].coordinate, glyphs[glyphIndex].target),
                        speed = options.characterMovementSpeed,
                        easing = options.characterEasing,
                        loop = false
                    )
                }
            }
            delay = options.launchDelay
        } else {
            delay -= 1
        }
    }

    private fun stepActivePaths() {
        val launcherPath = launchers[0].path
        if (launcherPath?.active == true) {
            val (coordinate, _) = launcherPath.step()
            launchers[0].coordinate = coordinate
        }
        for (glyph in glyphs) {
            val path = glyph.path
            if (path != null) {
                val (coordinate, complete) = path.step()
                glyph.coordinate = coordinate
                if (complete) {
                    glyph.path = null
                    glyph.layer = 0
                    glyph.coordinate = glyph.target
                }
            }
        }
    }

    private fun render(frame: Frame) {
        for (index in frame.cells.indices) {
            frame.cells[index] = Cell.BLANK
        }
        data class Winner(val layer: Int, val id: Int, val cell: Cell)
        val winners = HashMap<Int, Winner>()

        fun paint(id: Int, layer: Int, coordinate: Coordinate, symbol: Int, foreground: UInt) {
            if (coordinate.column in 1..canvas.columns && coordinate.row in 1..canvas.rows) {
                val cellIndex = (canvas.rows - coordinate.row) * canvas.columns + coordinate.column - 1
                val winner = winners[cellIndex]
                if (winner != null && (winner.layer > layer || (winner.layer == layer && winner.id > id))) {
                    return
                }
                winners[cellIndex] = Winner(layer, id, Cell(codepoint = symbol, foreground = foreground, background = 0u))
            }
        }

        for (glyph in glyphs) {
            if (glyph.visible) {
                paint(glyph.id, glyph.layer, glyph.coordinate, glyph.symbol, glyph.finalForeground)
            }
        }
        for (launcher in launchers) {
            if (launcher.visible) {
                paint(launcher.id, 2, launcher.coordinate, launcher.symbol, launcher.foreground)
            }
        }
        for ((index, winner) in winners) {
            frame.cells[index] = winner.cell
        }
    }
}
