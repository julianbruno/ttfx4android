package com.ttfx.effects

import com.ttfx.core.*

class RainEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    data class Configuration(
        val rainColors: List<Color> = listOf(
            Color("00315C"),
            Color("004C8F"),
            Color("0075DB"),
            Color("3F91D9"),
            Color("78B9F2"),
            Color("9AC8F5"),
            Color("B8D8F8"),
            Color("E3EFFC")
        ),
        val movementSpeed: Pair<Double, Double> = Pair(0.33, 0.57),
        val rainSymbols: List<String> = listOf("o", ".", ",", "*", "|"),
        val finalGradientStops: List<Color> = listOf(Color("488bff"), Color("b2e7de"), Color("57eaf7")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientDirection: GradientDirection = GradientDirection.Diagonal,
        val movementEasing: Easing = Easing.InQuart
    ) {
        init {
            require(rainColors.isNotEmpty()) { "rain colors must not be empty" }
            require(movementSpeed.first > 0.0 && movementSpeed.second > 0.0) { "movement speed must be positive" }
            require(rainSymbols.isNotEmpty()) { "rain symbols must not be empty" }
        }
    }

    private sealed interface ScenePhase {
        data object Rain : ScenePhase
        data object RainHold : ScenePhase
        data class Fade(val index: Int, val ticksElapsed: Int) : ScenePhase
        data object Done : ScenePhase
    }

    private class Glyph(
        val characterID: Int,
        val inputCoordinate: Coordinate,
        val inputSymbol: Int,
        val rainSymbol: Int,
        val rainColor: UInt,
        val fadeColors: List<UInt>,
        val pathOrigin: Coordinate,
        val totalDistance: Double,
        val pathSteps: Int,
        var coordinate: Coordinate,
        var pathStep: Int = 0,
        var pathActive: Boolean = true,
        var visible: Boolean = false,
        var scene: ScenePhase = ScenePhase.Rain
    ) {
        val isActive: Boolean
            get() = pathActive || when (scene) {
                ScenePhase.Rain, is ScenePhase.Fade -> true
                ScenePhase.RainHold, ScenePhase.Done -> false
            }

        val visual: Pair<Int, UInt>
            get() = when (val s = scene) {
                ScenePhase.Rain, ScenePhase.RainHold -> rainSymbol to rainColor
                is ScenePhase.Fade -> inputSymbol to fadeColors[s.index]
                ScenePhase.Done -> inputSymbol to fadeColors[fadeColors.size - 1]
            }
    }

    private companion object {
        private const val FADE_STEPS = 7
        private const val FADE_FRAME_DURATION = 3
    }

    private var rng: Xoshiro256PlusPlus = configuration.makeRNG(seed)
    private var glyphs = ArrayList<Glyph>()
    private var groups = ArrayList<List<Int>>()
    private var pending = ArrayList<Int>()
    private var active = ArrayList<Int>()
    private var isComplete = false

    init {
        build(input)
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

        val finalGradient = Gradient(stops = options.finalGradientStops, steps = options.finalGradientSteps)
        val mapping = finalGradient.coordinateColorMapping(
            minRow = bottom,
            maxRow = top,
            minColumn = left,
            maxColumn = right,
            direction = options.finalGradientDirection
        ).entries.associate { it.coordinate to it.color }

        val buildOrder = created.indices.sortedWith { i1, i2 ->
            val lhs = created[i1].coordinate
            val rhs = created[i2].coordinate
            if (lhs.row != rhs.row) rhs.row.compareTo(lhs.row) else lhs.column.compareTo(rhs.column)
        }

        val placeholder = Glyph(
            characterID = 0,
            inputCoordinate = Coordinate(1, 1),
            inputSymbol = 32,
            rainSymbol = 32,
            rainColor = 0u,
            fadeColors = listOf(0u),
            pathOrigin = Coordinate(1, 1),
            totalDistance = 0.0,
            pathSteps = 0,
            coordinate = Coordinate(1, 1),
            pathActive = false,
            scene = ScenePhase.Done
        )
        glyphs = ArrayList(List(created.size) { placeholder })

        val pendingOrder = ArrayList<Int>(created.size)
        for (sourceIndex in buildOrder) {
            val source = created[sourceIndex]
            val rainColor = options.rainColors[rng.integer(0 until options.rainColors.size)]
            val rainSymbolStr = options.rainSymbols[rng.integer(0 until options.rainSymbols.size)]
            val rainSymbol = if (rainSymbolStr.isNotEmpty()) rainSymbolStr.codePointAt(0) else Cell.BLANK.codepoint
            val finalColor = mapping[source.coordinate] ?: options.finalGradientStops.last()
            val fadeColors = Gradient(stops = listOf(rainColor, finalColor), steps = FADE_STEPS).spectrum.map { it.asUInt }
            val origin = Coordinate(column = source.coordinate.column, row = canvas.rows)
            val speed = rng.uniform(options.movementSpeed.first, options.movementSpeed.second)
            val distance = Geometry.lineLength(from = origin, to = source.coordinate)
            glyphs[sourceIndex] = Glyph(
                characterID = source.characterID,
                inputCoordinate = source.coordinate,
                inputSymbol = source.symbol,
                rainSymbol = rainSymbol,
                rainColor = rainColor.asUInt,
                fadeColors = fadeColors,
                pathOrigin = origin,
                totalDistance = distance,
                pathSteps = PyCompat.roundHalfEven(distance / speed),
                coordinate = origin
            )
            pendingOrder.add(sourceIndex)
        }

        val sortedByRow = pendingOrder.sortedWith { i1, i2 ->
            glyphs[i1].inputCoordinate.row.compareTo(glyphs[i2].inputCoordinate.row)
        }
        val grouped = LinkedHashMap<Int, MutableList<Int>>()
        for (index in sortedByRow) {
            grouped.getOrPut(glyphs[index].inputCoordinate.row) { ArrayList() }.add(index)
        }
        groups = ArrayList(grouped.keys.sorted().map { grouped[it]!! })
    }

    override fun tick(frame: Frame): TickStatus {
        if (isComplete) return TickStatus.Complete
        if (!hasPendingWork) {
            isComplete = true
            return TickStatus.Complete
        }

        if (pending.isEmpty() && groups.isNotEmpty()) {
            pending = ArrayList(groups.removeAt(0))
        }
        if (pending.isNotEmpty()) {
            val drawCount = rng.integer(1..2)
            for (i in 0 until drawCount) {
                if (pending.isEmpty()) break
                val index = rng.integer(0 until pending.size)
                val glyphIndex = pending.removeAt(index)
                glyphs[glyphIndex].visible = true
                active.add(glyphIndex)
            }
        }

        active.sort()
        for (glyphIndex in active) {
            updatePath(glyphIndex)
        }
        render(frame)
        for (glyphIndex in active) {
            updateScene(glyphIndex)
        }
        active.removeAll { !glyphs[it].isActive }

        if (!hasPendingWork) {
            isComplete = true
            return TickStatus.Complete
        }
        return TickStatus.Running
    }

    private fun updatePath(index: Int) {
        if (!glyphs[index].pathActive) return
        val glyph = glyphs[index]
        if (glyph.pathSteps == 0 || glyph.totalDistance == 0.0) {
            glyphs[index].coordinate = glyph.inputCoordinate
            glyphs[index].pathActive = false
            activateFade(index)
            return
        }

        glyphs[index].pathStep += 1
        val ratio = glyphs[index].pathStep.toDouble() / glyph.pathSteps.toDouble()
        val distance = options.movementEasing.value(ratio) * glyph.totalDistance
        glyphs[index].coordinate = Geometry.coordinateOnLine(
            from = glyph.pathOrigin,
            to = glyph.inputCoordinate,
            t = distance / glyph.totalDistance
        )
        if (glyphs[index].pathStep == glyph.pathSteps) {
            glyphs[index].pathActive = false
            activateFade(index)
        }
    }

    private fun activateFade(index: Int) {
        glyphs[index].scene = ScenePhase.Fade(index = 0, ticksElapsed = 0)
    }

    private fun updateScene(index: Int) {
        when (val scene = glyphs[index].scene) {
            ScenePhase.Rain -> {
                glyphs[index].scene = ScenePhase.RainHold
            }
            ScenePhase.RainHold, ScenePhase.Done -> {}
            is ScenePhase.Fade -> {
                val elapsed = scene.ticksElapsed + 1
                if (elapsed == FADE_FRAME_DURATION) {
                    if (scene.index + 1 < glyphs[index].fadeColors.size) {
                        glyphs[index].scene = ScenePhase.Fade(index = scene.index + 1, ticksElapsed = 0)
                    } else {
                        glyphs[index].scene = ScenePhase.Done
                    }
                } else {
                    glyphs[index].scene = ScenePhase.Fade(index = scene.index, ticksElapsed = elapsed)
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
            val coordinate = glyph.coordinate
            if (coordinate.column !in 1..canvas.columns || coordinate.row !in 1..canvas.rows) continue
            val cellIndex = (canvas.rows - coordinate.row) * canvas.columns + coordinate.column - 1
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
}
