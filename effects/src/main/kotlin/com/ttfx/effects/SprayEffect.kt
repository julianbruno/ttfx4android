package com.ttfx.effects

import com.ttfx.core.*

class SprayEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    enum class Position {
        N, NE, E, SE, S, SW, W, NW, Center
    }

    data class Configuration(
        val position: Position = Position.E,
        val volume: Double = 0.005,
        val movementSpeedRange: ClosedFloatingPointRange<Double> = 0.6..1.4,
        val movementEasing: Easing = Easing.OutExpo,
        val finalGradientStops: List<Color> = listOf(Color("8A008A"), Color("00D1FF"), Color("FFFFFF")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientDirection: GradientDirection = GradientDirection.Vertical
    ) {
        init {
            require(volume > 0.0) { "spray volume must be positive" }
            require(movementSpeedRange.start > 0.0 && movementSpeedRange.endInclusive > 0.0) {
                "movement speed range must be positive"
            }
            require(movementSpeedRange.start <= movementSpeedRange.endInclusive) {
                "movement speed range must not be empty"
            }
        }
    }

    private sealed interface SceneState {
        data class FrameState(val index: Int, val ticksElapsed: Int) : SceneState
        data object Done : SceneState
    }

    private data class Glyph(
        val characterID: Int,
        val target: Coordinate,
        val symbol: Int,
        val colors: List<UInt>,
        val origin: Coordinate,
        val totalDistance: Double,
        val maxSteps: Int,
        var coordinate: Coordinate,
        var currentStep: Int = 0,
        var pathActive: Boolean = true,
        var visible: Boolean = false,
        var scene: SceneState = SceneState.FrameState(index = 0, ticksElapsed = 0)
    ) {
        val active: Boolean
            get() = pathActive || scene is SceneState.FrameState

        val layer: Int
            get() = if (pathActive) 1 else 0
    }

    companion object {
        private const val SCENE_FRAME_DURATION = 20
        private const val SPRAY_GRADIENT_STEPS = 7
    }

    private var rng: Xoshiro256PlusPlus
    private var glyphs: MutableList<Glyph> = ArrayList()
    private var pending: MutableList<Int> = ArrayList()
    private var active: MutableList<Int> = ArrayList()
    private var releaseVolume = 1
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

        if (pending.isNotEmpty()) {
            val count = rng.integer(1..releaseVolume)
            for (i in 0 until count) {
                if (pending.isEmpty()) break
                val glyphIndex = pending.removeLast()
                glyphs[glyphIndex].visible = true
                active.add(glyphIndex)
            }
        }

        active.sortBy { glyphs[it].characterID }
        for (glyphIndex in active) {
            updatePath(glyphIndex)
        }
        render(frame)
        for (glyphIndex in active) {
            updateScene(glyphIndex)
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
        val created = ArrayList<Created>(input.scalars.size)
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
        val finalMapping = finalGradient.coordinateColorMapping(
            minRow = bottom,
            maxRow = top,
            minColumn = left,
            maxColumn = right,
            direction = options.finalGradientDirection
        )
        val finalColorByCoordinate = finalMapping.entries.associate { it.coordinate to it.color }
        val origin = sprayOrigin()

        val buildOrder = created.indices.sortedWith { i1, i2 ->
            val lhs = created[i1]
            val rhs = created[i2]
            if (lhs.coordinate.row != rhs.coordinate.row) rhs.coordinate.row.compareTo(lhs.coordinate.row)
            else if (lhs.coordinate.column != rhs.coordinate.column) lhs.coordinate.column.compareTo(rhs.coordinate.column)
            else lhs.characterID.compareTo(rhs.characterID)
        }

        glyphs = ArrayList(List(created.size) { placeholderGlyph() })
        for (sourceIndex in buildOrder) {
            val source = created[sourceIndex]
            val speed = rng.uniform(options.movementSpeedRange.start, options.movementSpeedRange.endInclusive)
            val startColor = finalGradient.spectrum[rng.integer(0 until finalGradient.spectrum.size)]
            val finalColor = finalColorByCoordinate[source.coordinate]!!
            val colors = Gradient(listOf(startColor, finalColor), SPRAY_GRADIENT_STEPS).spectrum.map { it.asUInt }
            val distance = Geometry.lineLength(origin, source.coordinate)
            glyphs[sourceIndex] = Glyph(
                characterID = source.characterID,
                target = source.coordinate,
                symbol = source.symbol,
                colors = colors,
                origin = origin,
                totalDistance = distance,
                maxSteps = PyCompat.roundHalfEven(distance / speed),
                coordinate = origin
            )
            pending.add(sourceIndex)
        }
        rng.shuffle(pending)
        releaseVolume = maxOf((pending.size.toDouble() * options.volume).toInt(), 1)
    }

    private fun updatePath(index: Int) {
        if (!glyphs[index].pathActive) return
        val glyph = glyphs[index]
        if (glyph.maxSteps <= 0 || glyph.totalDistance == 0.0) {
            glyphs[index].coordinate = glyph.target
            glyphs[index].pathActive = false
            return
        }

        glyphs[index].currentStep++
        val ratio = glyphs[index].currentStep.toDouble() / glyph.maxSteps.toDouble()
        val distance = options.movementEasing.value(ratio) * glyph.totalDistance
        glyphs[index].coordinate = Geometry.coordinateOnLine(
            from = glyph.origin,
            to = glyph.target,
            t = distance / glyph.totalDistance
        )
        if (glyphs[index].currentStep == glyph.maxSteps) {
            glyphs[index].pathActive = false
        }
    }

    private fun updateScene(index: Int) {
        when (val s = glyphs[index].scene) {
            is SceneState.Done -> {}
            is SceneState.FrameState -> {
                val elapsed = s.ticksElapsed + 1
                if (elapsed == SCENE_FRAME_DURATION) {
                    if (s.index + 1 < glyphs[index].colors.size) {
                        glyphs[index].scene = SceneState.FrameState(index = s.index + 1, ticksElapsed = 0)
                    } else {
                        glyphs[index].scene = SceneState.Done
                    }
                } else {
                    glyphs[index].scene = SceneState.FrameState(index = s.index, ticksElapsed = elapsed)
                }
            }
        }
    }

    private fun render(frame: Frame) {
        for (i in frame.cells.indices) {
            frame.cells[i] = Cell.BLANK
        }
        data class Winner(val layer: Int, val characterID: Int, val glyphIndex: Int)
        val winners = HashMap<Int, Winner>()
        for (index in glyphs.indices) {
            val glyph = glyphs[index]
            if (!glyph.visible) continue
            val coord = glyph.coordinate
            if (coord.column !in 1..canvas.columns || coord.row !in 1..canvas.rows) continue
            val cellIndex = (canvas.rows - coord.row) * canvas.columns + coord.column - 1
            val winner = winners[cellIndex]
            if (winner != null && (winner.layer > glyph.layer || (winner.layer == glyph.layer && winner.characterID > glyph.characterID))) {
                continue
            }
            winners[cellIndex] = Winner(glyph.layer, glyph.characterID, index)
        }
        for ((cellIndex, winner) in winners) {
            val glyph = glyphs[winner.glyphIndex]
            frame.cells[cellIndex] = Cell(codepoint = glyph.symbol, foreground = foreground(glyph), background = 0u)
        }
    }

    private fun foreground(glyph: Glyph): UInt {
        return when (val s = glyph.scene) {
            is SceneState.Done -> glyph.colors.last()
            is SceneState.FrameState -> glyph.colors[s.index]
        }
    }

    private val hasPendingWork: Boolean
        get() = pending.isNotEmpty() || active.isNotEmpty()

    private fun sprayOrigin(): Coordinate {
        return when (options.position) {
            Position.Center -> Coordinate(column = centered(canvas.columns), row = centered(canvas.rows))
            Position.N -> Coordinate(column = PyCompat.floorDivide(canvas.columns, 2), row = canvas.rows)
            Position.NE -> Coordinate(column = canvas.columns - 1, row = canvas.rows)
            Position.E -> Coordinate(column = canvas.columns - 1, row = PyCompat.floorDivide(canvas.rows, 2))
            Position.SE -> Coordinate(column = canvas.columns - 1, row = 1)
            Position.S -> Coordinate(column = PyCompat.floorDivide(canvas.columns, 2), row = 1)
            Position.SW -> Coordinate(column = 1, row = 1)
            Position.W -> Coordinate(column = 1, row = PyCompat.floorDivide(canvas.rows, 2))
            Position.NW -> Coordinate(column = 1, row = canvas.rows)
            else -> Coordinate(column = centered(canvas.columns), row = centered(canvas.rows))
        }
    }

    private fun centered(size: Int): Int {
        var center = maxOf(PyCompat.floorDivide(size, 2), 1)
        if (size % 2 != 0 && size > 1) center++
        return center
    }

    private fun placeholderGlyph(): Glyph {
        return Glyph(
            characterID = 0,
            target = Coordinate(column = 1, row = 1),
            symbol = 32,
            colors = listOf(0u),
            origin = Coordinate(column = 1, row = 1),
            totalDistance = 0.0,
            maxSteps = 0,
            coordinate = Coordinate(column = 1, row = 1),
            pathActive = false,
            visible = false,
            scene = SceneState.Done
        )
    }
}
