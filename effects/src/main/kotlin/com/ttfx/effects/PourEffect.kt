package com.ttfx.effects

import com.ttfx.core.*

class PourEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    enum class PourDirection {
        Up, Down, Left, Right
    }

    data class Configuration(
        val pourDirection: PourDirection = PourDirection.Down,
        val pourSpeed: Int = 2,
        val movementSpeedRange: ClosedFloatingPointRange<Double> = 0.4..0.6,
        val gap: Int = 1,
        val startingColor: Color = Color("ffffff"),
        val finalGradientStops: List<Color> = listOf(Color("8A008A"), Color("00D1FF"), Color("FFFFFF")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientFrames: Int = 6,
        val finalGradientDirection: GradientDirection = GradientDirection.Vertical,
        val movementEasing: Easing = Easing.InQuad
    ) {
        init {
            require(pourSpeed > 0) { "pour speed must be positive" }
            require(movementSpeedRange.start > 0.0 && movementSpeedRange.endInclusive > 0.0) {
                "movement speed must be positive"
            }
            require(gap >= 0) { "gap must not be negative" }
            require(finalGradientFrames > 0) { "gradient frames must be positive" }
        }
    }

    private data class Glyph(
        val characterID: Int,
        val inputCoordinate: Coordinate,
        val symbol: Int,
        val colors: List<UInt>,
        var coordinate: Coordinate,
        var pathOrigin: Coordinate,
        var pathSteps: Int,
        var pathStep: Int = 0,
        var pathActive: Boolean = true,
        var visible: Boolean = false,
        var sceneIndex: Int = 0,
        var sceneTicksElapsed: Int = 0,
        var sceneActive: Boolean = true
    ) {
        val active: Boolean
            get() = pathActive || sceneActive

        val foreground: UInt
            get() = colors[minOf(sceneIndex, colors.size - 1)]
    }

    private var rng: Xoshiro256PlusPlus
    private var glyphs: MutableList<Glyph> = ArrayList()
    private var pendingGroups: MutableList<MutableList<Int>> = ArrayList()
    private var currentGroup: MutableList<Int> = ArrayList()
    private var active: MutableList<Int> = ArrayList()
    private var gap = 0
    private var isComplete = false

    init {
        this.rng = configuration.makeRNG(seed)
        build(input)
    }

    override fun tick(frame: Frame): TickStatus {
        if (isComplete) return TickStatus.Complete
        if (!hasPendingWork) {
            isComplete = true
            return TickStatus.Complete
        }

        if (currentGroup.isEmpty() && pendingGroups.isNotEmpty()) {
            currentGroup = pendingGroups.removeAt(0)
        }
        if (currentGroup.isNotEmpty()) {
            if (gap == 0) {
                for (i in 0 until options.pourSpeed) {
                    if (currentGroup.isEmpty()) break
                    val index = currentGroup.removeAt(0)
                    glyphs[index].visible = true
                    active.add(index)
                }
                gap = options.gap
            } else {
                gap--
            }
        }

        active.sort()
        for (index in active) {
            updatePath(index)
        }
        render(frame)
        for (index in active) {
            updateScene(index)
        }
        active.removeAll { !glyphs[it].active }

        if (!hasPendingWork) {
            isComplete = true
            return TickStatus.Complete
        }
        return TickStatus.Running
    }

    private fun build(input: InputText) {
        data class Created(val characterID: Int, val symbol: Int, val coordinate: Coordinate)
        val created = ArrayList<Created>()
        for (index in input.scalars.indices) {
            val scalar = input.scalars[index]
            val pos = input.positions[index]
            if (scalar != 32) {
                created.add(Created(index, scalar, Coordinate(pos.column, pos.row)))
            }
        }
        if (created.isEmpty()) {
            isComplete = true
            return
        }

        val coordinates = created.map { it.coordinate }
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
        )
        val finalColors = mapping.entries.associate { it.coordinate to it.color }

        glyphs = ArrayList(List(created.size) { placeholderGlyph() })
        val sortedSourceIndices = created.indices.sortedWith { lhs, rhs ->
            val a = created[lhs].coordinate
            val b = created[rhs].coordinate
            when (options.pourDirection) {
                PourDirection.Down -> if (a.row == b.row) a.column.compareTo(b.column) else a.row.compareTo(b.row)
                PourDirection.Up -> if (a.row == b.row) a.column.compareTo(b.column) else b.row.compareTo(a.row)
                PourDirection.Left -> if (a.column == b.column) a.row.compareTo(b.row) else a.column.compareTo(b.column)
                PourDirection.Right -> if (a.column == b.column) a.row.compareTo(b.row) else b.column.compareTo(a.column)
                else -> 0
            }
        }

        for (sourceIndex in sortedSourceIndices) {
            val source = created[sourceIndex]
            val finalColor = finalColors[source.coordinate]!!
            val colors = Gradient(
                listOf(options.startingColor, finalColor),
                options.finalGradientSteps
            ).spectrum.map { it.asUInt }
            val start = startingCoordinate(source.coordinate)
            val speed = rng.uniform(options.movementSpeedRange.start, options.movementSpeedRange.endInclusive)
            val distance = Geometry.lineLength(start, source.coordinate)
            glyphs[sourceIndex] = Glyph(
                characterID = source.characterID,
                inputCoordinate = source.coordinate,
                symbol = source.symbol,
                colors = colors,
                coordinate = start,
                pathOrigin = start,
                pathSteps = PyCompat.roundHalfEven(distance / speed)
            )
        }

        val groups = groupedGlyphIndices()
        for ((index, group) in groups.withIndex()) {
            if (index % 2 == 0) {
                pendingGroups.add(group.toMutableList())
            } else {
                pendingGroups.add(group.reversed().toMutableList())
            }
        }
    }

    private fun groupedGlyphIndices(): List<List<Int>> {
        val sorted = glyphs.indices.sortedWith { i1, i2 ->
            val lhs = glyphs[i1].inputCoordinate
            val rhs = glyphs[i2].inputCoordinate
            if (lhs.row != rhs.row) lhs.row.compareTo(rhs.row) else lhs.column.compareTo(rhs.column)
        }
        return when (options.pourDirection) {
            PourDirection.Down -> glyphs.map { it.inputCoordinate.row }.distinct().sorted().map { row ->
                sorted.filter { glyphs[it].inputCoordinate.row == row }
            }
            PourDirection.Up -> glyphs.map { it.inputCoordinate.row }.distinct().sortedDescending().map { row ->
                sorted.filter { glyphs[it].inputCoordinate.row == row }
            }
            PourDirection.Left -> glyphs.map { it.inputCoordinate.column }.distinct().sorted().map { col ->
                sorted.filter { glyphs[it].inputCoordinate.column == col }
            }
            PourDirection.Right -> glyphs.map { it.inputCoordinate.column }.distinct().sortedDescending().map { col ->
                sorted.filter { glyphs[it].inputCoordinate.column == col }
            }
            else -> emptyList()
        }
    }

    private fun startingCoordinate(coordinate: Coordinate): Coordinate {
        return when (options.pourDirection) {
            PourDirection.Down -> Coordinate(column = coordinate.column, row = canvas.rows)
            PourDirection.Up -> Coordinate(column = coordinate.column, row = 1)
            PourDirection.Left -> Coordinate(column = canvas.columns, row = coordinate.row)
            PourDirection.Right -> Coordinate(column = 1, row = coordinate.row)
            else -> Coordinate(column = coordinate.column, row = 1)
        }
    }

    private fun updatePath(index: Int) {
        if (!glyphs[index].pathActive) return
        if (glyphs[index].pathSteps == 0) {
            glyphs[index].coordinate = glyphs[index].inputCoordinate
            glyphs[index].pathActive = false
            return
        }
        glyphs[index].pathStep++
        val raw = glyphs[index].pathStep.toDouble() / glyphs[index].pathSteps.toDouble()
        val eased = options.movementEasing.value(raw)
        glyphs[index].coordinate = Geometry.coordinateOnLine(
            from = glyphs[index].pathOrigin,
            to = glyphs[index].inputCoordinate,
            t = eased
        )
        if (glyphs[index].pathStep >= glyphs[index].pathSteps) {
            glyphs[index].pathActive = false
        }
    }

    private fun updateScene(index: Int) {
        if (!glyphs[index].sceneActive) return
        glyphs[index].sceneTicksElapsed++
        if (glyphs[index].sceneTicksElapsed != options.finalGradientFrames) return
        glyphs[index].sceneTicksElapsed = 0
        if (glyphs[index].sceneIndex + 1 < glyphs[index].colors.size) {
            glyphs[index].sceneIndex++
        } else {
            glyphs[index].sceneActive = false
        }
    }

    private fun render(frame: Frame) {
        for (i in frame.cells.indices) {
            frame.cells[i] = Cell.BLANK
        }
        data class Winner(val characterID: Int, val glyph: Glyph)
        val winners = HashMap<Int, Winner>()
        for (glyph in glyphs) {
            if (!glyph.visible) continue
            val coord = glyph.coordinate
            if (coord.column !in 1..canvas.columns || coord.row !in 1..canvas.rows) continue
            val cellIndex = (canvas.rows - coord.row) * canvas.columns + coord.column - 1
            val winner = winners[cellIndex]
            if (winner != null && winner.characterID > glyph.characterID) continue
            winners[cellIndex] = Winner(glyph.characterID, glyph)
        }
        for ((cellIndex, winner) in winners) {
            frame.cells[cellIndex] = Cell(codepoint = winner.glyph.symbol, foreground = winner.glyph.foreground, background = 0u)
        }
    }

    private val hasPendingWork: Boolean
        get() = pendingGroups.isNotEmpty() || currentGroup.isNotEmpty() || active.isNotEmpty()

    private fun placeholderGlyph(): Glyph {
        return Glyph(
            characterID = 0,
            inputCoordinate = Coordinate(column = 1, row = 1),
            symbol = 32,
            colors = listOf(0u),
            coordinate = Coordinate(column = 1, row = 1),
            pathOrigin = Coordinate(column = 1, row = 1),
            pathSteps = 0,
            pathActive = false,
            sceneActive = false
        )
    }
}
