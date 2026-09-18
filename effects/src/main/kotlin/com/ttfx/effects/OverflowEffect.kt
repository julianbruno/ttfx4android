package com.ttfx.effects

import com.ttfx.core.*

class OverflowEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    data class Configuration(
        val overflowGradientStops: List<Color> = listOf(Color("f2ebc0"), Color("8dbfb3"), Color("f2ebc0")),
        val overflowCyclesRange: IntRange = 2..4,
        val overflowSpeed: Int = 3,
        val finalGradientStops: List<Color> = listOf(Color("8A008A"), Color("00D1FF"), Color("FFFFFF")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientDirection: GradientDirection = GradientDirection.Vertical
    ) {
        init {
            require(overflowGradientStops.isNotEmpty()) { "overflow gradient must not be empty" }
            require(overflowCyclesRange.first > 0 && overflowCyclesRange.last >= overflowCyclesRange.first) { "overflow cycles range must be positive" }
            require(overflowSpeed > 0) { "overflow speed must be positive" }
        }
    }

    private class Glyph(
        val characterID: Int,
        val inputCoordinate: Coordinate,
        val symbol: Int,
        var coordinate: Coordinate,
        var foreground: UInt,
        var visible: Boolean
    )

    private class Row(
        val glyphs: List<Int>,
        val final: Boolean
    )

    private var rng: Xoshiro256PlusPlus = configuration.makeRNG(seed)
    private var glyphs = ArrayList<Glyph>()
    private var pendingRows = ArrayList<Row>()
    private var activeRows = ArrayList<Row>()
    private var delay = 0
    private var overflowSpectrum: List<Color> = emptyList()
    private var isComplete = false

    init {
        build(input)
    }

    override fun tick(frame: Frame): TickStatus {
        if (isComplete) return TickStatus.Complete
        if (pendingRows.isEmpty()) {
            isComplete = true
            return TickStatus.Complete
        }

        if (delay == 0) {
            val count = rng.integer(1..options.overflowSpeed)
            for (i in 0 until count) {
                if (pendingRows.isEmpty()) break
                for (row in activeRows) {
                    moveUp(row)
                    if (!row.final) colorOverflow(row)
                }

                val next = pendingRows.removeAt(0)
                setup(next)
                moveUp(next)
                if (!next.final) setColor(next, overflowSpectrum[0].asUInt)
                for (index in next.glyphs) {
                    glyphs[index].visible = true
                }
                activeRows.add(next)
            }
            delay = rng.integer(0..3)
        } else {
            delay -= 1
        }

        activeRows.removeAll { row ->
            val first = row.glyphs.firstOrNull() ?: return@removeAll true
            glyphs[first].coordinate.row > canvas.rows
        }
        render(frame)
        if (pendingRows.isEmpty()) {
            isComplete = true
            return TickStatus.Complete
        }
        return TickStatus.Running
    }

    private data class Source(val characterID: Int, val symbol: Int, val coordinate: Coordinate)

    private fun build(input: InputText) {
        val sources = ArrayList<Source>(input.scalars.size)
        for (index in input.scalars.indices) {
            val symbol = input.scalars[index]
            val pos = input.positions[index]
            sources.add(Source(characterID = index, symbol = symbol, coordinate = Coordinate(pos.column, pos.row)))
        }
        if (sources.isEmpty()) {
            isComplete = true
            return
        }

        val bottom = sources.minOf { it.coordinate.row }
        val top = sources.maxOf { it.coordinate.row }
        val left = sources.minOf { it.coordinate.column }
        val right = sources.maxOf { it.coordinate.column }
        val finalGradient = Gradient(options.finalGradientStops, options.finalGradientSteps)
        val finalMapping = finalGradient.coordinateColorMapping(
            minRow = bottom,
            maxRow = top,
            minColumn = left,
            maxColumn = right,
            direction = options.finalGradientDirection
        )
        val finalColors = finalMapping.entries.associate { it.coordinate to it.color.asUInt }

        val rows = groupedRows(sources).toMutableList()
        val cycles = rng.integer(options.overflowCyclesRange)
        for (c in 0 until cycles) {
            rng.shuffle(rows)
            for (row in rows) {
                val copied = ArrayList<Int>()
                for (source in row) {
                    val index = glyphs.size
                    glyphs.add(
                        Glyph(
                            characterID = source.characterID,
                            inputCoordinate = source.coordinate,
                            symbol = source.symbol,
                            coordinate = source.coordinate,
                            foreground = 0u,
                            visible = false
                        )
                    )
                    copied.add(index)
                }
                pendingRows.add(Row(glyphs = copied, final = false))
            }
        }

        val occupied = sources.associateBy { it.coordinate }
        val finalSources = ArrayList<Source>()
        for (row in 1..canvas.rows) {
            for (column in 1..canvas.columns) {
                val coordinate = Coordinate(column, row)
                finalSources.add(occupied[coordinate] ?: Source(finalSources.size + input.scalars.size, 32, coordinate))
            }
        }
        for (row in groupedRows(finalSources)) {
            val originals = ArrayList<Int>()
            for (source in row) {
                val index = glyphs.size
                glyphs.add(
                    Glyph(
                        characterID = source.characterID,
                        inputCoordinate = source.coordinate,
                        symbol = source.symbol,
                        coordinate = source.coordinate,
                        foreground = finalColors[source.coordinate] ?: 0u,
                        visible = false
                    )
                )
                originals.add(index)
            }
            pendingRows.add(Row(glyphs = originals, final = true))
        }

        val steps = maxOf(PyCompat.floorDivide(canvas.rows, maxOf(options.overflowGradientStops.size - 1, 1)), 1)
        overflowSpectrum = Gradient(stops = options.overflowGradientStops, steps = listOf(steps)).spectrum
    }

    private fun groupedRows(sources: List<Source>): List<List<Source>> {
        val sorted = sources.sortedWith(
            compareByDescending<Source> { it.coordinate.row }
                .thenBy { it.coordinate.column }
                .thenBy { it.characterID }
        )
        return sources.map { it.coordinate.row }.toSet().sortedDescending().map { row ->
            sorted.filter { it.coordinate.row == row }
        }
    }

    private fun setup(row: Row) {
        for (index in row.glyphs) {
            glyphs[index].coordinate = Coordinate(column = glyphs[index].inputCoordinate.column, row = 0)
        }
    }

    private fun moveUp(row: Row) {
        for (index in row.glyphs) {
            glyphs[index].coordinate = Coordinate(column = glyphs[index].coordinate.column, row = glyphs[index].coordinate.row + 1)
        }
    }

    private fun colorOverflow(row: Row) {
        val first = row.glyphs.firstOrNull() ?: return
        val spectrumIndex = glyphs[first].coordinate.row.coerceIn(0, overflowSpectrum.size - 1)
        setColor(row, overflowSpectrum[spectrumIndex].asUInt)
    }

    private fun setColor(row: Row, color: UInt) {
        for (index in row.glyphs) {
            glyphs[index].foreground = color
        }
    }

    private fun render(frame: Frame) {
        for (index in frame.cells.indices) {
            frame.cells[index] = Cell.BLANK
        }
        for (glyph in glyphs) {
            if (glyph.visible && glyph.coordinate.column in 1..canvas.columns && glyph.coordinate.row in 1..canvas.rows) {
                val cellIndex = (canvas.rows - glyph.coordinate.row) * canvas.columns + glyph.coordinate.column - 1
                frame.cells[cellIndex] = Cell(
                    codepoint = glyph.symbol,
                    foreground = glyph.foreground,
                    background = if (glyph.foreground == 0u) 0xFFFF_FFFEu else 0u
                )
            }
        }
    }
}
