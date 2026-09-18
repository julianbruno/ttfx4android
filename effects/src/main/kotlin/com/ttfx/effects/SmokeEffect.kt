package com.ttfx.effects

import com.ttfx.core.*

class SmokeEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    data class Configuration(
        val startingColor: Color = Color("7A7A7A"),
        val smokeSymbols: List<String> = listOf("░", "▒", "▓", "▒", "░"),
        val smokeGradientStops: List<Color> = listOf(Color("242424"), Color("FFFFFF")),
        val useWholeCanvas: Boolean = false,
        val finalGradientStops: List<Color> = listOf(Color("8A008A"), Color("00D1FF"), Color("FFFFFF")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientDirection: GradientDirection = GradientDirection.Vertical
    ) {
        init {
            require(smokeSymbols.isNotEmpty()) { "smoke symbols must not be empty" }
            require(smokeGradientStops.isNotEmpty()) { "smoke gradient stops must not be empty" }
            require(finalGradientStops.isNotEmpty()) { "final gradient stops must not be empty" }
        }
    }

    private companion object {
        private const val EXPLICIT_BLACK_FOREGROUND_SENTINEL: UInt = 0xFFFF_FFFEu
    }

    private enum class Phase { Idle, Smoke, Paint, Done }

    private data class Visual(
        val symbol: Int,
        val foreground: UInt,
        val duration: Int
    )

    private class Glyph(
        val characterID: Int,
        val coordinate: Coordinate,
        val inputSymbol: Int,
        val smokeFrames: List<Visual>,
        val paintFrames: List<Visual>,
        var visible: Boolean = true,
        var phase: Phase = Phase.Idle,
        var frameIndex: Int = 0,
        var ticksElapsed: Int = 0,
        var renderedVisual: Visual? = null
    ) {
        val active: Boolean
            get() = visible && phase != Phase.Done && phase != Phase.Idle

        val visual: Visual?
            get() = when (phase) {
                Phase.Idle -> renderedVisual
                Phase.Smoke -> smokeFrames[frameIndex]
                Phase.Paint -> paintFrames[frameIndex]
                Phase.Done -> paintFrames.lastOrNull()
            }
    }

    private var glyphs = ArrayList<Glyph>()
    private var releaseOrder = ArrayList<List<Int>>()
    private var isComplete = false

    init {
        build(input, configuration.makeRNG(seed))
    }

    private fun build(input: InputText, initialRNG: Xoshiro256PlusPlus) {
        data class Source(val characterID: Int, val symbol: Int, val coordinate: Coordinate)
        var sources = ArrayList<Source>()
        for (index in input.scalars.indices) {
            val pos = input.positions[index]
            sources.add(Source(index, input.scalars[index], Coordinate(column = pos.column, row = pos.row)))
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
        ).entries.associate { it.coordinate to it.color }

        val smokeStops = options.smokeGradientStops + options.finalGradientStops.reversed()
        val smokeGradient = Gradient(stops = smokeStops, steps = listOf(3, 4))
        val smokeSymbols = options.smokeSymbols.map {
            if (it.isNotEmpty()) it.codePointAt(0) else Cell.BLANK.codepoint
        }

        val occupied = sources.associateBy { it.coordinate }
        sources = ArrayList()
        for (row in canvas.rows downTo 1) {
            for (column in 1..canvas.columns) {
                val coordinate = Coordinate(column = column, row = row)
                sources.add(occupied[coordinate] ?: Source(sources.size + input.scalars.size, 32, coordinate))
            }
        }

        glyphs = ArrayList(sources.map { source ->
            val finalColor = finalMapping[source.coordinate] ?: Color("000000")
            val paintGradient = Gradient(stops = options.finalGradientStops + listOf(finalColor), steps = 5)
            Glyph(
                characterID = source.characterID,
                coordinate = source.coordinate,
                inputSymbol = source.symbol,
                smokeFrames = distributedSmokeFrames(smokeSymbols, smokeGradient.spectrum),
                paintFrames = paintGradient.spectrum.map { Visual(symbol = source.symbol, foreground = it.asUInt, duration = 5) },
                renderedVisual = Visual(symbol = source.symbol, foreground = options.startingColor.asUInt, duration = 1)
            )
        })
        releaseOrder = breadthFirstGroups(initialRNG, left, right, bottom, top)
    }

    private fun distributedSmokeFrames(symbols: List<Int>, colors: List<Color>): List<Visual> {
        val pairs: List<Pair<Int, Color>> = if (symbols.size >= colors.size) {
            cyclicDistribution(symbols, colors)
        } else {
            cyclicDistribution(colors, symbols).map { it.second to it.first }
        }
        return pairs.map { Visual(symbol = it.first, foreground = it.second.asUInt, duration = 3) }
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

    private fun breadthFirstGroups(
        initialRNG: Xoshiro256PlusPlus,
        boundLeft: Int,
        boundRight: Int,
        boundBottom: Int,
        boundTop: Int
    ): ArrayList<List<Int>> {
        val rng = initialRNG
        val left = if (options.useWholeCanvas) 1 else boundLeft
        val right = if (options.useWholeCanvas) canvas.columns else boundRight
        val bottom = if (options.useWholeCanvas) 1 else boundBottom
        val top = if (options.useWholeCanvas) canvas.rows else boundTop

        val ids = HashMap<Coordinate, Int>()
        for (i in glyphs.indices) {
            ids[glyphs[i].coordinate] = i
        }
        val start = ids[Coordinate(column = rng.integer(left..right), row = rng.integer(bottom..top))]!!
        val weights = glyphs.map { rng.integer(0..99) }
        val fillStart = ids[Coordinate(column = rng.integer(left..right), row = rng.integer(bottom..top))]!!

        val links = Array(glyphs.size) { HashSet<Int>() }
        val pending = LinkedHashMap<Int, MutableList<Pair<Int, Int>>>()

        fun addNeighbors(index: Int) {
            val coordinate = glyphs[index].coordinate
            val deltas = listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0)
            for ((dx, dy) in deltas) {
                val neighbor = Coordinate(column = coordinate.column + dx, row = coordinate.row + dy)
                if (neighbor.column in left..right && neighbor.row in bottom..top) {
                    val id = ids[neighbor]
                    if (id != null && links[id].isEmpty()) {
                        pending.getOrPut(weights[id]) { ArrayList() }.add(index to id)
                    }
                }
            }
        }

        addNeighbors(start)
        while (pending.isNotEmpty()) {
            val minWeight = pending.keys.minOrNull() ?: break
            val list = pending[minWeight]!!
            val index = rng.integer(0 until list.size)
            val (source, target) = list.removeAt(index)
            if (list.isEmpty()) {
                pending.remove(minWeight)
            }
            if (links[target].isEmpty()) {
                links[source].add(target)
                links[target].add(source)
                addNeighbors(target)
            }
        }

        var frontier = listOf(fillStart)
        val explored = HashSet<Int>()
        explored.add(fillStart)
        val groups = ArrayList<List<Int>>()

        while (frontier.isNotEmpty()) {
            val next = ArrayList<Int>()
            for (current in frontier) {
                for (neighbor in links[current].sorted()) {
                    if (explored.add(neighbor)) {
                        next.add(neighbor)
                    }
                }
            }
            groups.add(if (groups.isEmpty()) listOf(fillStart) + next else next)
            frontier = next
        }
        return groups
    }

    override fun tick(frame: Frame): TickStatus {
        if (isComplete) return TickStatus.Complete
        if (!hasPendingWork) {
            isComplete = true
            return TickStatus.Complete
        }

        if (releaseOrder.isNotEmpty()) {
            for (next in releaseOrder.removeAt(0)) {
                glyphs[next].phase = Phase.Smoke
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
                Phase.Idle -> {}
                Phase.Smoke -> {
                    if (glyphs[index].frameIndex + 1 < glyphs[index].smokeFrames.size) {
                        glyphs[index].frameIndex += 1
                    } else {
                        glyphs[index].phase = Phase.Paint
                        glyphs[index].frameIndex = 0
                        glyphs[index].renderedVisual = glyphs[index].paintFrames[0]
                    }
                }
                Phase.Paint -> {
                    if (glyphs[index].frameIndex + 1 < glyphs[index].paintFrames.size) {
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
        data class Winner(val characterID: Int, val visual: Visual)
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
            frame.cells[cellIndex] = Cell(
                codepoint = winner.visual.symbol,
                foreground = winner.visual.foreground,
                background = if (winner.visual.foreground == 0u) EXPLICIT_BLACK_FOREGROUND_SENTINEL else 0u
            )
        }
    }

    private val hasPendingWork: Boolean
        get() = releaseOrder.isNotEmpty() || glyphs.any { it.active }
}
