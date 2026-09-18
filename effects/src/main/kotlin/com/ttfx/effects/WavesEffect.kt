package com.ttfx.effects

import com.ttfx.core.*
import kotlin.math.abs

class WavesEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    enum class Direction {
        ColumnLeftToRight,
        ColumnRightToLeft,
        RowTopToBottom,
        RowBottomToTop,
        CenterToOutside,
        OutsideToCenter
    }

    data class Configuration(
        val waveSymbols: List<String> = listOf("▁", "▂", "▃", "▄", "▅", "▆", "▇", "█", "▇", "▆", "▅", "▄", "▃", "▂", "▁"),
        val waveGradientStops: List<Color> = listOf(Color("f0ff65"), Color("ffb102"), Color("31a0d4"), Color("ffb102"), Color("f0ff65")),
        val waveGradientSteps: List<Int> = listOf(6),
        val waveCount: Int = 7,
        val waveLength: Int = 2,
        val waveDirection: Direction = Direction.ColumnLeftToRight,
        val waveEasing: Easing = Easing.InOutSine,
        val finalGradientStops: List<Color> = listOf(Color("ffb102"), Color("31a0d4"), Color("f0ff65")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientDirection: GradientDirection = GradientDirection.Diagonal
    ) {
        init {
            require(waveSymbols.isNotEmpty()) { "wave symbols must not be empty" }
            require(waveGradientStops.isNotEmpty()) { "wave gradient stops must not be empty" }
            require(waveCount > 0) { "wave count must be positive" }
            require(waveLength > 0) { "wave length must be positive" }
        }
    }

    private enum class Phase { Wave, Final, Done }

    private class FrameVisual(
        val symbol: Int,
        val foreground: UInt,
        val duration: Int
    )

    private class Glyph(
        val characterID: Int,
        val coordinate: Coordinate,
        val inputSymbol: Int,
        val waveFrames: List<FrameVisual>,
        val finalFrames: List<FrameVisual>,
        var visible: Boolean = false,
        var phase: Phase = Phase.Wave,
        var frameIndex: Int = 0,
        var ticksElapsed: Int = 0,
        var renderedVisual: FrameVisual? = null
    ) {
        val active: Boolean
            get() = visible && phase != Phase.Done

        val visual: FrameVisual?
            get() = when (phase) {
                Phase.Wave -> waveFrames[frameIndex]
                Phase.Final -> finalFrames[frameIndex]
                Phase.Done -> finalFrames.lastOrNull()
            }
    }

    private var glyphs = ArrayList<Glyph>()
    private var pendingGroups = ArrayList<List<Int>>()
    private var isComplete = false

    init {
        build(input)
    }

    private fun build(input: InputText) {
        data class Created(val characterID: Int, val symbol: Int, val coordinate: Coordinate)
        val created = ArrayList<Created>(input.scalars.size)
        for (index in input.scalars.indices) {
            val pos = input.positions[index]
            created.add(Created(index, input.scalars[index], Coordinate(pos.column, pos.row)))
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
        ).entries.associate { it.coordinate to it.color }

        val waveGradient = Gradient(options.waveGradientStops, options.waveGradientSteps)
        val waveFrames = makeWaveFrames(waveGradient)

        glyphs = ArrayList(created.map { source ->
            val finalColor = finalMapping[source.coordinate] ?: finalGradient.spectrum[0]
            val finalSceneGradient = Gradient(stops = listOf(waveGradient.spectrum.last(), finalColor), steps = options.finalGradientSteps)
            val finalFrames = finalSceneGradient.spectrum.map {
                FrameVisual(symbol = source.symbol, foreground = it.asUInt, duration = 10)
            }
            Glyph(
                characterID = source.characterID,
                coordinate = source.coordinate,
                inputSymbol = source.symbol,
                waveFrames = waveFrames,
                finalFrames = finalFrames
            )
        })
        pendingGroups = groupedGlyphs()
    }

    private fun makeWaveFrames(waveGradient: Gradient): List<FrameVisual> {
        val symbols = options.waveSymbols.map {
            if (it.isNotEmpty()) it.codePointAt(0) else Cell.BLANK.codepoint
        }
        val colors = waveGradient.spectrum.map { it.asUInt }
        val distributed: List<Pair<Int, UInt>> = if (symbols.size >= colors.size) {
            cyclicDistribution(symbols, colors)
        } else {
            cyclicDistribution(colors, symbols).map { it.second to it.first }
        }

        val timeline = ArrayList<Pair<Int, UInt>>()
        for (w in 0 until options.waveCount) {
            for (pair in distributed) {
                for (l in 0 until options.waveLength) {
                    timeline.add(pair)
                }
            }
        }

        return timeline.indices.map { step ->
            val eased = options.waveEasing.value(step.toDouble() / timeline.size.toDouble())
            val index = minOf(maxOf(PyCompat.roundHalfEven(eased * (timeline.size - 1).toDouble()), 0), timeline.size - 1)
            val visual = timeline[index]
            FrameVisual(symbol = visual.first, foreground = visual.second, duration = 1)
        }
    }

    private fun <T, U> cyclicDistribution(larger: List<T>, smaller: List<U>): List<Pair<T, U>> {
        val repeatFactor = larger.size / smaller.size
        var overflowCount = larger.size % smaller.size
        var overflowUsed = false
        var smallerIndex = 0
        var currentRepeatFactor = 0
        val output = ArrayList<Pair<T, U>>(larger.size)
        for (element in larger) {
            if (currentRepeatFactor >= repeatFactor) {
                if (overflowCount > 0) {
                    if (overflowUsed) {
                        smallerIndex += 1
                        currentRepeatFactor = 0
                        overflowUsed = false
                    } else {
                        overflowUsed = true
                        overflowCount -= 1
                    }
                } else {
                    smallerIndex += 1
                    currentRepeatFactor = 0
                }
            }
            currentRepeatFactor += 1
            output.add(element to smaller[smallerIndex])
        }
        return output
    }

    private fun groupedGlyphs(): ArrayList<List<Int>> {
        val ordered = glyphs.indices.sortedWith { i1, i2 ->
            val lhs = glyphs[i1].coordinate
            val rhs = glyphs[i2].coordinate
            if (lhs.row != rhs.row) rhs.row.compareTo(lhs.row) else lhs.column.compareTo(rhs.column)
        }

        val key: (Glyph) -> Int
        val reverse: Boolean
        when (options.waveDirection) {
            Direction.ColumnLeftToRight -> {
                key = { it.coordinate.column }
                reverse = false
            }
            Direction.ColumnRightToLeft -> {
                key = { it.coordinate.column }
                reverse = true
            }
            Direction.RowTopToBottom -> {
                key = { it.coordinate.row }
                reverse = true
            }
            Direction.RowBottomToTop -> {
                key = { it.coordinate.row }
                reverse = false
            }
            Direction.CenterToOutside, Direction.OutsideToCenter -> {
                val columns = glyphs.map { it.coordinate.column }
                val rows = glyphs.map { it.coordinate.row }
                val center = Coordinate(
                    column = columns.minOf { it } + (columns.maxOf { it } - columns.minOf { it }) / 2,
                    row = rows.minOf { it } + (rows.maxOf { it } - rows.minOf { it }) / 2
                )
                key = { abs(it.coordinate.column - center.column) + abs(it.coordinate.row - center.row) }
                reverse = options.waveDirection == Direction.OutsideToCenter
            }
        }

        val buckets = LinkedHashMap<Int, MutableList<Int>>()
        for (index in ordered) {
            buckets.getOrPut(key(glyphs[index])) { ArrayList() }.add(index)
        }
        val groups = ArrayList<List<Int>>(buckets.keys.sorted().map { buckets[it]!! })
        if (reverse) {
            groups.reverse()
        }
        return groups
    }

    override fun tick(frame: Frame): TickStatus {
        if (isComplete) return TickStatus.Complete
        if (!hasPendingWork) {
            isComplete = true
            return TickStatus.Complete
        }

        if (pendingGroups.isNotEmpty()) {
            for (index in pendingGroups.removeAt(0)) {
                glyphs[index].visible = true
            }
        }

        advanceScenes()
        render(frame)

        if (!hasPendingWork) {
            isComplete = true
            return TickStatus.Complete
        }
        return TickStatus.Running
    }

    private fun advanceScenes() {
        for (index in glyphs.indices) {
            if (!glyphs[index].active) continue
            val displayed = glyphs[index].visual ?: continue
            glyphs[index].renderedVisual = displayed
            glyphs[index].ticksElapsed += 1
            if (glyphs[index].ticksElapsed < displayed.duration) continue
            glyphs[index].ticksElapsed = 0

            when (glyphs[index].phase) {
                Phase.Wave -> {
                    if (glyphs[index].frameIndex + 1 < glyphs[index].waveFrames.size) {
                        glyphs[index].frameIndex += 1
                    } else {
                        glyphs[index].phase = Phase.Final
                        glyphs[index].frameIndex = 0
                        glyphs[index].renderedVisual = glyphs[index].finalFrames[0]
                    }
                }
                Phase.Final -> {
                    if (glyphs[index].frameIndex + 1 < glyphs[index].finalFrames.size) {
                        glyphs[index].frameIndex += 1
                    } else {
                        glyphs[index].phase = Phase.Done
                    }
                }
                Phase.Done -> {}
            }
        }
    }

    private fun render(frame: Frame) {
        for (index in frame.cells.indices) {
            frame.cells[index] = Cell.BLANK
        }
        data class Winner(val characterID: Int, val visual: FrameVisual)
        val winners = HashMap<Int, Winner>()
        for (glyph in glyphs) {
            if (!glyph.visible) continue
            val visual = glyph.renderedVisual ?: glyph.visual ?: continue
            if (glyph.coordinate.column !in 1..canvas.columns || glyph.coordinate.row !in 1..canvas.rows) continue
            val cellIndex = (canvas.rows - glyph.coordinate.row) * canvas.columns + glyph.coordinate.column - 1
            val winner = winners[cellIndex]
            if (winner != null && winner.characterID > glyph.characterID) continue
            winners[cellIndex] = Winner(glyph.characterID, visual)
        }
        for ((cellIndex, winner) in winners) {
            frame.cells[cellIndex] = Cell(codepoint = winner.visual.symbol, foreground = winner.visual.foreground, background = 0u)
        }
    }

    private val hasPendingWork: Boolean
        get() = pendingGroups.isNotEmpty() || glyphs.any { it.active }
}
