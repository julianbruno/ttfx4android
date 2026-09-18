package com.ttfx.effects

import com.ttfx.core.*
import kotlin.math.abs

class SynthGridEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    data class Configuration(
        val gridGradientStops: List<Color> = listOf(Color("CC00CC"), Color("FFFFFF")),
        val gridGradientSteps: List<Int> = listOf(12),
        val gridGradientDirection: GradientDirection = GradientDirection.Diagonal,
        val textGradientStops: List<Color> = listOf(Color("8A008A"), Color("00D1FF"), Color("FFFFFF")),
        val textGradientSteps: List<Int> = listOf(12),
        val textGradientDirection: GradientDirection = GradientDirection.Vertical,
        val gridRowSymbol: String = "─",
        val gridColumnSymbol: String = "│",
        val textGenerationSymbols: List<String> = listOf("░", "▒", "▓"),
        val maxActiveBlocks: Double = 0.1
    )

    private enum class Phase { GridExpand, AddChars, Collapse, Complete }
    private enum class Direction { Horizontal, Vertical }

    private class GridLine(
        val direction: Direction,
        val coordinates: List<Coordinate>,
        val collapsed: ArrayList<Coordinate>,
        val extended: ArrayList<Coordinate> = ArrayList()
    ) {
        val isExtended: Boolean
            get() = collapsed.isEmpty()

        val isCollapsed: Boolean
            get() = extended.isEmpty()

        fun extend() {
            val count = if (direction == Direction.Horizontal) 3 else 1
            for (i in 0 until count) {
                if (collapsed.isNotEmpty()) {
                    extended.add(collapsed.removeAt(0))
                }
            }
        }

        fun collapse() {
            val count = if (direction == Direction.Horizontal) 3 else 1
            if (collapsed.isEmpty()) extended.reverse()
            for (i in 0 until count) {
                if (extended.isNotEmpty()) {
                    collapsed.add(extended.removeAt(0))
                }
            }
        }
    }

    private class CharacterScene(
        val coordinate: Coordinate,
        val generationCells: ArrayList<Pair<Int, UInt>> = ArrayList(),
        val finalCodepoint: Int,
        val finalColor: UInt,
        var age: Int = 0,
        var active: Boolean = false,
        var complete: Boolean = false
    ) {
        val currentCell: Pair<Int, UInt>?
            get() {
                if (age in 1..(generationCells.size * 2)) {
                    return generationCells[(age - 1) / 2]
                }
                return null
            }
    }

    private var rng: Xoshiro256PlusPlus = configuration.makeRNG(seed)
    private var isComplete = false
    private var built = false
    private var phase = Phase.GridExpand
    private var gridLines = ArrayList<GridLine>()
    private var pendingGroups = ArrayList<List<Int>>()
    private var allGroups = ArrayList<List<Int>>()
    private var characterScenes = ArrayList<CharacterScene>()
    private val cachedInput = input

    override fun tick(frame: Frame): TickStatus {
        if (isComplete) return TickStatus.Complete

        if (!built) build()
        advancePhase()
        advanceActiveScenes()
        renderSynthGrid(frame)

        if (phase == Phase.Complete) {
            isComplete = true
            return TickStatus.Complete
        }
        return TickStatus.Running
    }

    private fun build() {
        built = true
        gridLines = arrayListOf(
            GridLine(Direction.Horizontal, (1..canvas.columns).map { Coordinate(column = it, row = 1) }, ArrayList((1..canvas.columns).map { Coordinate(column = it, row = 1) })),
            GridLine(Direction.Horizontal, (1..canvas.columns).map { Coordinate(column = it, row = canvas.rows) }, ArrayList((1..canvas.columns).map { Coordinate(column = it, row = canvas.rows) })),
            GridLine(Direction.Vertical, (1 until canvas.rows).map { Coordinate(column = 1, row = it) }, ArrayList((1 until canvas.rows).map { Coordinate(column = 1, row = it) })),
            GridLine(Direction.Vertical, (1 until canvas.rows).map { Coordinate(column = canvas.columns, row = it) }, ArrayList((1 until canvas.rows).map { Coordinate(column = canvas.columns, row = it) }))
        )

        val (partitionRows, partitionColumns) = partitionLineIndexes()
        for (row in partitionRows) {
            val coords = (1..canvas.columns).map { Coordinate(column = it, row = row) }
            gridLines.add(GridLine(Direction.Horizontal, coords, ArrayList(coords)))
        }
        for (column in partitionColumns) {
            val coords = (1 until canvas.rows).map { Coordinate(column = column, row = it) }
            gridLines.add(GridLine(Direction.Vertical, coords, ArrayList(coords)))
        }

        val finalColors = textColorMapping()
        val inputByCoordinate = HashMap<Coordinate, Pair<Int, UInt>>()
        for (index in cachedInput.scalars.indices) {
            val pos = cachedInput.positions[index]
            inputByCoordinate[Coordinate(column = pos.column, row = pos.row)] = cachedInput.scalars[index] to finalColors[index]
        }

        val textGradient = Gradient(stops = options.textGradientStops, steps = options.textGradientSteps)
        characterScenes = ArrayList()
        for (row in 1..canvas.rows) {
            for (column in 1..canvas.columns) {
                val coordinate = Coordinate(column = column, row = row)
                val final = inputByCoordinate[coordinate]
                characterScenes.add(
                    CharacterScene(
                        coordinate = coordinate,
                        finalCodepoint = final?.first ?: Cell.BLANK.codepoint,
                        finalColor = final?.second ?: 0u
                    )
                )
            }
        }

        pendingGroups = makeGroups()
        allGroups = ArrayList(pendingGroups)
        for (group in pendingGroups) {
            for (index in group) {
                val count = rng.integer(15..30)
                for (i in 0 until count) {
                    val symbolStr = options.textGenerationSymbols[rng.integer(0 until options.textGenerationSymbols.size)]
                    val symbol = if (symbolStr.isNotEmpty()) symbolStr.codePointAt(0) else Cell.BLANK.codepoint
                    val color = textGradient.spectrum[rng.integer(0 until textGradient.spectrum.size)].asUInt
                    characterScenes[index].generationCells.add(symbol to color)
                }
            }
        }

        rng.shuffle(pendingGroups)
        if (pendingGroups.isEmpty()) {
            for (index in characterScenes.indices) {
                characterScenes[index].active = true
            }
        }
    }

    private fun advancePhase() {
        when (phase) {
            Phase.GridExpand -> {
                if (gridLines.all { it.isExtended }) {
                    phase = Phase.AddChars
                } else {
                    for (index in gridLines.indices) {
                        if (!gridLines[index].isExtended) gridLines[index].extend()
                    }
                }
            }
            Phase.AddChars -> {
                val activeGroupCount = allGroups.count { group ->
                    group.any { characterScenes[it].active && !characterScenes[it].complete }
                }
                val totalGroupCount = allGroups.size
                if (pendingGroups.isNotEmpty() && activeGroupCount.toDouble() < totalGroupCount.toDouble() * options.maxActiveBlocks) {
                    val group = pendingGroups.removeAt(0)
                    for (index in group) {
                        characterScenes[index].active = true
                    }
                }
                if (pendingGroups.isEmpty() && characterScenes.none { it.active && !it.complete }) {
                    phase = Phase.Collapse
                }
            }
            Phase.Collapse -> {
                if (gridLines.all { it.isCollapsed }) {
                    phase = Phase.Complete
                } else {
                    for (index in gridLines.indices) {
                        if (!gridLines[index].isCollapsed) gridLines[index].collapse()
                    }
                }
            }
            Phase.Complete -> {}
        }
    }

    private fun advanceActiveScenes() {
        for (index in characterScenes.indices) {
            if (characterScenes[index].active && !characterScenes[index].complete) {
                characterScenes[index].age += 1
                if (characterScenes[index].age >= characterScenes[index].generationCells.size * 2 + 1) {
                    characterScenes[index].complete = true
                }
            }
        }
    }

    private fun renderSynthGrid(frame: Frame) {
        for (i in frame.cells.indices) {
            frame.cells[i] = Cell.BLANK
        }
        for (scene in characterScenes) {
            if (!scene.active) continue
            val currentCell = scene.currentCell
            if (!scene.complete && currentCell != null) {
                frame[scene.coordinate.column, scene.coordinate.row] = Cell(
                    codepoint = currentCell.first,
                    foreground = currentCell.second,
                    background = 0u
                )
            } else {
                frame[scene.coordinate.column, scene.coordinate.row] = Cell(
                    codepoint = scene.finalCodepoint,
                    foreground = scene.finalColor,
                    background = 0u
                )
            }
        }

        for (line in gridLines) {
            val symbolStr = if (line.direction == Direction.Horizontal) options.gridRowSymbol else options.gridColumnSymbol
            val codepoint = if (symbolStr.isNotEmpty()) symbolStr.codePointAt(0) else Cell.BLANK.codepoint
            for (coordinate in line.extended) {
                frame[coordinate.column, coordinate.row] = Cell(
                    codepoint = codepoint,
                    foreground = gridColor(coordinate),
                    background = 0u
                )
            }
        }
    }

    private fun makeGroups(): ArrayList<List<Int>> {
        if (characterScenes.isEmpty()) return ArrayList()
        val (pRows, pCols) = partitionLineIndexes()
        val rowIndexes = ArrayList(pRows)
        val columnIndexes = ArrayList(pCols)
        rowIndexes.add(canvas.rows + 1)
        columnIndexes.add(canvas.columns + 1)

        val groups = ArrayList<List<Int>>()
        var previousRowIndex = 1
        for (rowIndexValue in rowIndexes) {
            var blockEndRow = rowIndexValue
            var previousColumnIndex = 1
            for (blockEndColumn in columnIndexes) {
                if (blockEndRow == canvas.rows) blockEndRow += 1
                val group = ArrayList<Int>()
                for (row in previousRowIndex until blockEndRow) {
                    for (column in previousColumnIndex until blockEndColumn) {
                        val index = characterScenes.indexOfFirst { it.coordinate == Coordinate(column = column, row = row) }
                        if (index != -1) {
                            group.add(index)
                        }
                    }
                }
                if (group.isNotEmpty()) {
                    groups.add(group)
                }
                previousColumnIndex = blockEndColumn
            }
            previousRowIndex = blockEndRow
        }
        return groups
    }

    private fun partitionLineIndexes(): Pair<List<Int>, List<Int>> {
        val rowIndexes = ArrayList<Int>()
        val columnIndexes = ArrayList<Int>()
        val rowGap: Int
        val columnGap: Int
        if (canvas.rows > 2 * canvas.columns) {
            rowGap = findEvenGap(canvas.rows) + 1
            columnGap = rowGap * 2
        } else {
            columnGap = findEvenGap(canvas.columns) + 1
            rowGap = columnGap / 2
        }

        var rowIndex = 1 + rowGap
        while (rowIndex < canvas.rows) {
            if (canvas.rows - rowIndex >= 2) rowIndexes.add(rowIndex)
            rowIndex += maxOf(rowGap, 1)
        }
        var columnIndex = 1 + columnGap
        while (columnIndex < canvas.columns) {
            if (canvas.columns - columnIndex >= 2) columnIndexes.add(columnIndex)
            columnIndex += maxOf(columnGap, 1)
        }
        return rowIndexes to columnIndexes
    }

    private fun findEvenGap(dimension: Int): Int {
        val adjusted = dimension - 2
        if (adjusted <= 0) return 0
        val potentialGaps = ArrayList<Int>()
        var gap = adjusted
        while (gap > 4) {
            if (adjusted % gap <= 1) potentialGaps.add(gap)
            gap -= 1
        }
        if (potentialGaps.isEmpty()) return 4
        val target = adjusted / 5
        var best = potentialGaps[0]
        for (i in 1 until potentialGaps.size) {
            val candidate = potentialGaps[i]
            if (abs(candidate - target) < abs(best - target)) {
                best = candidate
            }
        }
        return best
    }

    private fun textColorMapping(): List<UInt> {
        if (cachedInput.scalars.isEmpty()) return emptyList()
        val minRow = cachedInput.positions.minOf { it.row }
        val maxRow = cachedInput.positions.maxOf { it.row }
        val minColumn = cachedInput.positions.minOf { it.column }
        val maxColumn = cachedInput.positions.maxOf { it.column }
        val gradient = Gradient(stops = options.textGradientStops, steps = options.textGradientSteps)
        val mapping = gradient.coordinateColorMapping(
            minRow = minRow,
            maxRow = maxRow,
            minColumn = minColumn,
            maxColumn = maxColumn,
            direction = options.textGradientDirection
        ).entries.associate { it.coordinate to it.color.asUInt }
        return cachedInput.positions.map { mapping[Coordinate(column = it.column, row = it.row)] ?: 0u }
    }

    private fun gridColor(coordinate: Coordinate): UInt {
        val gradient = Gradient(stops = options.gridGradientStops, steps = options.gridGradientSteps)
        val mapping = gradient.coordinateColorMapping(
            minRow = 1,
            maxRow = canvas.rows,
            minColumn = 1,
            maxColumn = canvas.columns,
            direction = options.gridGradientDirection
        ).entries.associate { it.coordinate to it.color.asUInt }
        return mapping[coordinate] ?: 0xFFFFFFu
    }
}
