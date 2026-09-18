package com.ttfx.effects

import com.ttfx.core.*

class SliceEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    enum class Direction {
        Vertical, Horizontal, Diagonal
    }

    data class Configuration(
        val direction: Direction = Direction.Vertical,
        val movementSpeed: Double = 0.25,
        val movementEasing: Easing = Easing.InOutExpo,
        val finalGradientStops: List<Color> = listOf(Color("8A008A"), Color("00D1FF"), Color("FFFFFF")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientDirection: GradientDirection = GradientDirection.Diagonal
    ) {
        init {
            require(movementSpeed > 0) { "movement speed must be positive" }
        }
    }

    private class Glyph(
        val characterID: Int,
        val target: Coordinate,
        val symbol: Int,
        val foreground: UInt,
        val start: Coordinate,
        val totalDistance: Double,
        val maxSteps: Int,
        var coordinate: Coordinate,
        var currentStep: Int = 0,
        var pathActive: Boolean = true
    )

    private var glyphs = ArrayList<Glyph>()
    private var isComplete = false

    init {
        build(input)
    }

    override fun tick(frame: Frame): TickStatus {
        if (isComplete) return TickStatus.Complete
        if (glyphs.isEmpty()) {
            isComplete = true
            return TickStatus.Complete
        }

        advancePaths()
        render(frame)
        if (glyphs.none { it.pathActive }) {
            isComplete = true
            return TickStatus.Complete
        }
        return TickStatus.Running
    }

    private data class Source(val characterID: Int, val symbol: Int, val coordinate: Coordinate)

    private fun build(input: InputText) {
        val sources = input.scalars.zip(input.positions).mapIndexed { index, (symbol, pos) ->
            Source(characterID = index, symbol = symbol, coordinate = Coordinate(pos.column, pos.row))
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
        val mapping = finalGradient.coordinateColorMapping(
            minRow = bottom,
            maxRow = top,
            minColumn = left,
            maxColumn = right,
            direction = options.finalGradientDirection
        )
        val colorByCoordinate = mapping.entries.associate { it.coordinate to it.color.asUInt }
        val sourceByID = sources.associateBy { it.characterID }

        val origins = pathOrigins(sources, bottom, top, left, right)
        glyphs = ArrayList(origins.mapNotNull { (characterID, origin) ->
            val source = sourceByID[characterID] ?: return@mapNotNull null
            val distance = Geometry.lineLength(origin, source.coordinate)
            Glyph(
                characterID = characterID,
                target = source.coordinate,
                symbol = source.symbol,
                foreground = colorByCoordinate[source.coordinate]!!,
                start = origin,
                totalDistance = distance,
                maxSteps = PyCompat.roundHalfEven(distance / movementSpeed(options.direction)),
                coordinate = origin
            )
        })
    }

    private fun pathOrigins(
        sources: List<Source>,
        bottom: Int,
        top: Int,
        left: Int,
        right: Int
    ): List<Pair<Int, Coordinate>> {
        val centerColumn = left + (right - left) / 2
        val centerRow = bottom + (top - bottom) / 2

        return when (options.direction) {
            Direction.Vertical -> {
                val rows = grouped(sources, { it.coordinate.row }, Comparator.naturalOrder())
                val result = ArrayList<Pair<Int, Coordinate>>()
                for (rowIndex in rows.indices) {
                    val row = rows[rowIndex]
                    val leftHalf = row.filter { it.coordinate.column <= centerColumn }
                    result.addAll(leftHalf.map { it.characterID to Coordinate(column = it.coordinate.column, row = canvas.rows + 1) })

                    val opposite = rows[rows.size - (rowIndex + 1)]
                    val rightHalf = opposite.filter { it.coordinate.column > centerColumn }
                    result.addAll(rightHalf.map { it.characterID to Coordinate(column = it.coordinate.column, row = 0) })
                }
                result
            }

            Direction.Horizontal -> {
                val columns = grouped(sources, { it.coordinate.column }, Comparator.reverseOrder())
                val result = ArrayList<Pair<Int, Coordinate>>()
                for (columnIndex in columns.indices) {
                    val column = columns[columnIndex]
                    val bottomHalf = column.filter { it.coordinate.row <= centerRow }
                    result.addAll(bottomHalf.map { it.characterID to Coordinate(column = 0, row = it.coordinate.row) })

                    val opposite = columns[columns.size - (columnIndex + 1)]
                    val topHalf = opposite.filter { it.coordinate.row > centerRow }
                    result.addAll(topHalf.map { it.characterID to Coordinate(column = canvas.columns + 1, row = it.coordinate.row) })
                }
                result
            }

            Direction.Diagonal -> {
                val diagonals = grouped(sources, { it.coordinate.column + it.coordinate.row }, Comparator.naturalOrder())
                val half = diagonals.size / 2
                val leftGroups = diagonals.subList(0, half).map { it.toMutableList() }.toMutableList()
                val rightGroups = diagonals.subList(half, diagonals.size).map { it.toMutableList() }.toMutableList()
                val result = ArrayList<Pair<Int, Coordinate>>()
                while (leftGroups.isNotEmpty() || rightGroups.isNotEmpty()) {
                    if (leftGroups.isNotEmpty()) {
                        val group = leftGroups.removeAt(0)
                        val origin = Coordinate(column = group[0].coordinate.column, row = 0)
                        result.addAll(group.map { it.characterID to origin })
                    }
                    if (rightGroups.isNotEmpty()) {
                        val group = rightGroups.removeAt(0)
                        val origin = Coordinate(column = group[group.size - 1].coordinate.column, row = canvas.rows + 1)
                        result.addAll(group.map { it.characterID to origin })
                    }
                }
                result
            }
        }
    }

    private fun <T> grouped(
        values: List<T>,
        key: (T) -> Int,
        comparator: Comparator<Int>
    ): List<List<T>> {
        val buckets = LinkedHashMap<Int, MutableList<T>>()
        for (value in values) {
            buckets.getOrPut(key(value)) { ArrayList() }.add(value)
        }
        return buckets.keys.sortedWith(comparator).map { buckets[it]!! }
    }

    private fun movementSpeed(direction: Direction): Double {
        return when (direction) {
            Direction.Horizontal -> options.movementSpeed * 2.0
            Direction.Vertical, Direction.Diagonal -> options.movementSpeed
        }
    }

    private fun advancePaths() {
        for (glyph in glyphs) {
            if (!glyph.pathActive) continue
            if (glyph.maxSteps <= 0 || glyph.totalDistance == 0.0) {
                glyph.coordinate = glyph.target
                glyph.pathActive = false
                continue
            }
            glyph.currentStep += 1
            val ratio = glyph.currentStep.toDouble() / glyph.maxSteps.toDouble()
            val eased = options.movementEasing.value(ratio)
            glyph.coordinate = Geometry.coordinateOnLine(
                from = glyph.start,
                to = glyph.target,
                t = eased
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
        val winners = HashMap<Int, Glyph>()
        for (glyph in glyphs.sortedBy { it.characterID }) {
            if (glyph.coordinate.column in 1..canvas.columns && glyph.coordinate.row in 1..canvas.rows) {
                val cellIndex = (canvas.rows - glyph.coordinate.row) * canvas.columns + glyph.coordinate.column - 1
                winners[cellIndex] = glyph
            }
        }
        for ((cellIndex, glyph) in winners) {
            frame.cells[cellIndex] = Cell(codepoint = glyph.symbol, foreground = glyph.foreground, background = 0u)
        }
    }
}
