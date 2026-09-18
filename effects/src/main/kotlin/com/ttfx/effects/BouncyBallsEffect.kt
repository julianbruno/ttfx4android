package com.ttfx.effects

import com.ttfx.core.*

class BouncyBallsEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    data class Configuration(
        val ballColors: List<Color> = listOf(Color("d1f4a5"), Color("96e2a4"), Color("5acda9")),
        val ballSymbols: List<String> = listOf("*", "o", "O", "0", "."),
        val ballDelay: Int = 4,
        val movementSpeed: Double = 0.45,
        val movementEasing: Easing = Easing.OutBounce,
        val finalGradientStops: List<Color> = listOf(Color("f8ffae"), Color("43c6ac")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientDirection: GradientDirection = GradientDirection.Diagonal
    ) {
        init {
            require(ballColors.isNotEmpty()) { "ball colors must not be empty" }
            require(ballSymbols.isNotEmpty()) { "ball symbols must not be empty" }
            require(ballDelay >= 0) { "ball delay must not be negative" }
            require(movementSpeed > 0.0) { "movement speed must be positive" }
        }
    }

    private sealed interface ScenePhase {
        data object Ball : ScenePhase
        data class Final(val index: Int, val ticksElapsed: Int) : ScenePhase
        data object Done : ScenePhase
    }

    private data class Glyph(
        val characterID: Int,
        val inputCoordinate: Coordinate,
        val inputSymbol: Int,
        val ballSymbol: Int,
        val ballColor: UInt,
        val finalColors: List<UInt>,
        val origin: Coordinate,
        val totalDistance: Double,
        val pathSteps: Int,
        var coordinate: Coordinate,
        var pathStep: Int = 0,
        var pathActive: Boolean = true,
        var visible: Boolean = false,
        var scene: ScenePhase = ScenePhase.Ball
    ) {
        val isActive: Boolean
            get() = pathActive || when (scene) {
                is ScenePhase.Ball, is ScenePhase.Final -> true
                is ScenePhase.Done -> false
            }

        val visual: Pair<Int, UInt>
            get() = when (val s = scene) {
                is ScenePhase.Ball -> ballSymbol to ballColor
                is ScenePhase.Final -> inputSymbol to finalColors[s.index]
                is ScenePhase.Done -> inputSymbol to finalColors.last()
            }
    }

    companion object {
        private const val FINAL_GRADIENT_STEPS = 10
        private const val FINAL_FRAME_DURATION = 6
    }

    private var rng: Xoshiro256PlusPlus = configuration.makeRNG(seed)
    private var glyphs: MutableList<Glyph> = ArrayList()
    private var groups: MutableList<MutableList<Int>> = ArrayList()
    private var pending: MutableList<Int> = ArrayList()
    private var active: MutableList<Int> = ArrayList()
    private var ballDelay = 0
    private var isComplete = false

    init {
        build(input)
    }

    override fun tick(frame: Frame): TickStatus {
        if (isComplete) return TickStatus.Complete
        if (!hasPendingWork) {
            isComplete = true
            return TickStatus.Complete
        }

        if (pending.isEmpty() && groups.isNotEmpty()) {
            pending = groups.removeAt(0)
        }
        if (pending.isNotEmpty()) {
            if (ballDelay == 0) {
                val count = rng.integer(2..6)
                for (i in 0 until count) {
                    if (pending.isEmpty()) break
                    val index = rng.integer(0 until pending.size)
                    val glyphIndex = pending.removeAt(index)
                    glyphs[glyphIndex].visible = true
                    active.add(glyphIndex)
                }
                ballDelay = options.ballDelay
            } else {
                ballDelay--
            }
        }

        active.sortBy { glyphs[it].characterID }
        for (index in active) updatePath(index)
        render(frame)
        for (index in active) updateScene(index)
        active.removeAll { !glyphs[it].isActive }

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
        val mapping = finalGradient.coordinateColorMapping(
            minRow = bottom,
            maxRow = top,
            minColumn = left,
            maxColumn = right,
            direction = options.finalGradientDirection
        )
        val finalColors = mapping.entries.associate { it.coordinate to it.color.asUInt }

        val buildOrder = created.indices.sortedWith { i1, i2 ->
            val lhs = created[i1].coordinate
            val rhs = created[i2].coordinate
            if (lhs.row != rhs.row) rhs.row.compareTo(lhs.row) else lhs.column.compareTo(rhs.column)
        }

        glyphs = ArrayList(List(created.size) { placeholderGlyph() })
        val pendingOrder = ArrayList<Int>(created.size)
        for (sourceIndex in buildOrder) {
            val source = created[sourceIndex]
            val ballColor = options.ballColors[rng.integer(0 until options.ballColors.size)]
            val ballSymbolStr = options.ballSymbols[rng.integer(0 until options.ballSymbols.size)]
            val ballSymbol = ballSymbolStr.codePoints().findFirst().asInt
            val mappedFinal = color(finalColors[source.coordinate]!!)
            val fade = Gradient(listOf(ballColor, mappedFinal), FINAL_GRADIENT_STEPS).spectrum.map { it.asUInt }
            val dropRow = (canvas.rows.toDouble() * rng.uniform(1.0, 1.5)).toInt()
            val origin = Coordinate(column = source.coordinate.column, row = dropRow)
            val distance = Geometry.lineLength(origin, source.coordinate)
            glyphs[sourceIndex] = Glyph(
                characterID = source.characterID,
                inputCoordinate = source.coordinate,
                inputSymbol = source.symbol,
                ballSymbol = ballSymbol,
                ballColor = ballColor.asUInt,
                finalColors = fade,
                origin = origin,
                totalDistance = distance,
                pathSteps = PyCompat.roundHalfEven(distance / options.movementSpeed),
                coordinate = origin
            )
            pendingOrder.add(sourceIndex)
        }

        val sortedByRow = pendingOrder.sortedBy { glyphs[it].inputCoordinate.row }
        val grouped = LinkedHashMap<Int, MutableList<Int>>()
        val rows = ArrayList<Int>()
        for (index in sortedByRow) {
            val row = glyphs[index].inputCoordinate.row
            if (!grouped.containsKey(row)) {
                rows.add(row)
                grouped[row] = ArrayList()
            }
            grouped[row]!!.add(index)
        }
        groups = rows.sorted().mapNotNull { grouped[it] }.toMutableList()
        ballDelay = 0
    }

    private fun updatePath(index: Int) {
        if (!glyphs[index].pathActive) return
        val glyph = glyphs[index]
        if (glyph.pathSteps == 0 || glyph.totalDistance == 0.0) {
            glyphs[index].coordinate = glyph.inputCoordinate
            glyphs[index].pathActive = false
            activateFinal(index)
            return
        }
        glyphs[index].pathStep++
        val ratio = glyphs[index].pathStep.toDouble() / glyph.pathSteps.toDouble()
        val distance = options.movementEasing.value(ratio) * glyph.totalDistance
        glyphs[index].coordinate = Geometry.coordinateOnLine(
            from = glyph.origin,
            to = glyph.inputCoordinate,
            t = distance / glyph.totalDistance
        )
        if (glyphs[index].pathStep == glyph.pathSteps) {
            glyphs[index].pathActive = false
            activateFinal(index)
        }
    }

    private fun activateFinal(index: Int) {
        glyphs[index].scene = ScenePhase.Final(index = 0, ticksElapsed = 0)
    }

    private fun updateScene(index: Int) {
        when (val s = glyphs[index].scene) {
            is ScenePhase.Ball, is ScenePhase.Done -> {}
            is ScenePhase.Final -> {
                val elapsed = s.ticksElapsed + 1
                if (elapsed == FINAL_FRAME_DURATION) {
                    if (s.index + 1 < glyphs[index].finalColors.size) {
                        glyphs[index].scene = ScenePhase.Final(index = s.index + 1, ticksElapsed = 0)
                    } else {
                        glyphs[index].scene = ScenePhase.Done
                    }
                } else {
                    glyphs[index].scene = ScenePhase.Final(index = s.index, ticksElapsed = elapsed)
                }
            }
        }
    }

    private fun render(frame: Frame) {
        for (i in frame.cells.indices) {
            frame.cells[i] = Cell.BLANK
        }
        data class Winner(val layer: Int, val characterID: Int, val symbol: Int, val foreground: UInt)
        val winners = HashMap<Int, Winner>()
        for (glyph in glyphs) {
            if (!glyph.visible) continue
            val coord = glyph.coordinate
            if (coord.column !in 1..canvas.columns || coord.row !in 1..canvas.rows) continue
            val cellIndex = (canvas.rows - coord.row) * canvas.columns + coord.column - 1
            val winner = winners[cellIndex]
            if (winner != null && (winner.layer > 0 || (winner.layer == 0 && winner.characterID > glyph.characterID))) {
                continue
            }
            val vis = glyph.visual
            winners[cellIndex] = Winner(0, glyph.characterID, vis.first, vis.second)
        }
        for ((cellIndex, winner) in winners) {
            frame.cells[cellIndex] = Cell(codepoint = winner.symbol, foreground = winner.foreground, background = 0u)
        }
    }

    private val hasPendingWork: Boolean
        get() = groups.isNotEmpty() || pending.isNotEmpty() || active.isNotEmpty()

    private fun placeholderGlyph(): Glyph {
        return Glyph(
            characterID = 0,
            inputCoordinate = Coordinate(column = 1, row = 1),
            inputSymbol = 32,
            ballSymbol = 32,
            ballColor = 0u,
            finalColors = listOf(0u),
            origin = Coordinate(column = 1, row = 1),
            totalDistance = 0.0,
            pathSteps = 0,
            coordinate = Coordinate(column = 1, row = 1),
            pathActive = false,
            scene = ScenePhase.Done
        )
    }

    private fun color(word: UInt): Color {
        val r = ((word shr 16) and 0xFFu).toInt()
        val g = ((word shr 8) and 0xFFu).toInt()
        val b = (word and 0xFFu).toInt()
        return Color(r, g, b)
    }
}
