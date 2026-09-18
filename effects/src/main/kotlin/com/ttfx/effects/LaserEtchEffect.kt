package com.ttfx.effects

import com.ttfx.core.*

class LaserEtchEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    private val input: InputText,
    seed: ULong,
    private val laserEtchConfiguration: Configuration = Configuration()
) : Effect {

    data class Configuration(
        val etchPattern: EtchPattern = EtchPattern.Algorithm,
        val etchSpeed: Int = 1,
        val etchDelay: Int = 1,
        val coolGradientStops: List<Color> = listOf(Color("ffe680"), Color("ff7b00")),
        val laserGradientStops: List<Color> = listOf(Color("ffffff"), Color("376cff")),
        val sparkGradientStops: List<Color> = listOf(Color("ffffff"), Color("ffe680"), Color("ff7b00"), Color("1a0900")),
        val sparkCoolingFrames: Int = 7,
        val finalGradientStops: List<Color> = listOf(Color("8A008A"), Color("00D1FF"), Color("ffffff")),
        val finalGradientSteps: List<Int> = listOf(8),
        val finalGradientFrames: Int = 4,
        val finalGradientDirection: GradientDirection = GradientDirection.Vertical
    ) {
        enum class EtchPattern {
            Algorithm, RowTopToBottom, RowBottomToTop, ColumnLeftToRight, ColumnRightToLeft,
            DiagonalTopLeftToBottomRight, DiagonalBottomLeftToTopRight,
            DiagonalTopRightToBottomLeft, DiagonalBottomRightToTopLeft,
            CenterToOutside, OutsideToCenter
        }
    }

    private data class EtchCharacter(
        val scalar: Int,
        val position: Coordinate,
        val isFill: Boolean,
        var visibleTick: Int? = null
    )

    private data class Spark(
        val symbol: Int,
        val poolID: Int,
        val start: Coordinate,
        val end: Coordinate,
        val control: Coordinate,
        val emittedTick: Int
    )

    private var tickIndex = 0
    private var initialized = false
    private var rng: Xoshiro256PlusPlus = configuration.makeRNG(seed)
    private var characters = ArrayList<EtchCharacter>()
    private var pendingCharacterIndexes = ArrayList<Int>()
    private var charDelay = 0
    private var laserPosition = Coordinate(0, 0)
    private var laserVisible = true
    private val sparks = ArrayList<Spark>()
    private val availableSparkSymbols = ArrayList<Pair<Int, Int>>()

    override fun tick(frame: Frame): TickStatus {
        if (laserEtchConfiguration.etchPattern != Configuration.EtchPattern.Algorithm) {
            return TickStatus.Complete
        }
        initializeIfNeeded()
        if (pendingCharacterIndexes.isEmpty() && !hasActiveRuntime) {
            return TickStatus.Complete
        }

        advanceEtching()
        render(frame)

        val lifetime = sparkFrameCount
        val currentTick = tickIndex
        val reclaimed = sparks.filter { currentTick - it.emittedTick >= lifetime - 1 }.sortedBy { it.poolID }
        for (spark in reclaimed) {
            availableSparkSymbols.add(spark.poolID to spark.symbol)
        }
        sparks.removeAll { currentTick - it.emittedTick >= lifetime - 1 }
        tickIndex++

        return if (pendingCharacterIndexes.isNotEmpty() || hasActiveRuntime) TickStatus.Running else TickStatus.Complete
    }

    private val sparkColors: List<UInt> by lazy {
        Gradient(laserEtchConfiguration.sparkGradientStops, listOf(3, 8)).spectrum.map { it.asUInt }
    }

    private val laserColors: List<UInt> by lazy {
        Gradient(laserEtchConfiguration.laserGradientStops, 6, loop = true).spectrum.map { it.asUInt }
    }

    private val sparkFrameCount: Int
        get() = sparkColors.size * laserEtchConfiguration.sparkCoolingFrames

    private val hasActiveRuntime: Boolean
        get() = sparks.any { tickIndex - it.emittedTick < sparkFrameCount } ||
                characters.any { ch ->
                    val start = ch.visibleTick ?: return@any false
                    tickIndex - start < 3 + finalCoolingColors(ch.position).size * 3
                }

    private fun initializeIfNeeded() {
        if (initialized) return
        initialized = true
        characters = ArrayList(anchoredTextCharacters())
        pendingCharacterIndexes = ArrayList(recursiveBacktrackerOrder())
        preallocateSparkPool()
    }

    private fun anchoredTextCharacters(): List<EtchCharacter> {
        if (input.positions.isEmpty()) return emptyList()
        val occupied = HashMap<Coordinate, Int>()
        val result = ArrayList<EtchCharacter>()

        for (i in input.scalars.indices) {
            val pos = input.positions[i]
            val coord = Coordinate(pos.column, pos.row)
            if (coord.column in 1..canvas.columns && coord.row in 1..canvas.rows) {
                val scalar = input.scalars[i]
                occupied[coord] = scalar
                result.add(EtchCharacter(scalar = scalar, position = coord, isFill = false))
            }
        }
        if (result.isEmpty()) return emptyList()

        val textLeft = result.minOf { it.position.column }
        val textRight = result.maxOf { it.position.column }
        val textBottom = result.minOf { it.position.row }
        val textTop = result.maxOf { it.position.row }

        for (row in 1..canvas.rows) {
            for (col in 1..canvas.columns) {
                val coord = Coordinate(col, row)
                if (occupied.containsKey(coord)) continue
                if (col in textLeft..textRight && row in textBottom..textTop) {
                    result.add(EtchCharacter(scalar = 32, position = coord, isFill = true))
                }
            }
        }
        return result
    }

    private fun preallocateSparkPool() {
        val symbols = listOf(".".codePointAt(0), ",".codePointAt(0), "*".codePointAt(0))
        availableSparkSymbols.clear()
        for (id in 0 until 2000) {
            availableSparkSymbols.add(id to symbols[rng.integer(0 until symbols.size)])
        }
    }

    private fun advanceEtching() {
        if (charDelay == 0) {
            var count = 0
            while (count < laserEtchConfiguration.etchSpeed && pendingCharacterIndexes.isNotEmpty()) {
                var nextIndex = pendingCharacterIndexes.removeAt(0)
                while (characters[nextIndex].scalar == 32 && pendingCharacterIndexes.isNotEmpty()) {
                    nextIndex = pendingCharacterIndexes.removeAt(0)
                }
                characters[nextIndex].visibleTick = tickIndex
                val position = characters[nextIndex].position
                laserPosition = position
                emitSpark(position)
                count++
            }
            charDelay = laserEtchConfiguration.etchDelay
        } else {
            charDelay--
        }
        laserVisible = pendingCharacterIndexes.isNotEmpty()
    }

    private fun emitSpark(position: Coordinate) {
        val (poolID, symbol) = if (availableSparkSymbols.isNotEmpty()) {
            availableSparkSymbols.removeAt(availableSparkSymbols.size - 1)
        } else {
            (2000 + sparks.size) to listOf(46, 44, 42)[rng.integer(0..2)]
        }
        val fallColumn = rng.integer((position.column - 20)..(position.column + 20))
        val fall = Coordinate(fallColumn, 1)
        val control = Coordinate(fall.column, position.row + rng.integer(-10..20))
        sparks.add(Spark(symbol, poolID, position, fall, control, tickIndex))
    }

    private fun render(frame: Frame) {
        for (i in frame.cells.indices) {
            frame.cells[i] = Cell.BLANK
        }
        for (ch in characters) {
            val visibleTick = ch.visibleTick ?: continue
            if (ch.isFill) continue
            val elapsed = tickIndex - visibleTick
            put(inputCell(ch, elapsed), ch.position, frame)
        }
        val sortedSparks = sparks.sortedBy { it.poolID }
        for (spark in sortedSparks) {
            if (tickIndex - spark.emittedTick < sparkFrameCount - 1) {
                val elapsed = tickIndex - spark.emittedTick
                val coord = sparkCoordinate(spark, elapsed)
                val colorIdx = minOf(elapsed / laserEtchConfiguration.sparkCoolingFrames, sparkColors.size - 1)
                val color = sparkColors[colorIdx]
                put(Cell(spark.symbol, color, 0u), coord, frame)
            }
        }
        if (laserVisible) {
            var r = laserPosition.row
            var c = laserPosition.column
            for (beamIndex in 0..canvas.rows) {
                val symbol = if (beamIndex == 0) "*".codePointAt(0) else "/".codePointAt(0)
                val color = laserColors[(beamIndex + tickIndex / 3) % laserColors.size]
                put(Cell(symbol, color, 0u), Coordinate(c, r), frame)
                r++
                c++
            }
        }
    }

    private fun inputCell(character: EtchCharacter, elapsed: Int): Cell {
        if (elapsed < 3) {
            return Cell("^".codePointAt(0), 0xFFE680u, 0u)
        }
        val colors = finalCoolingColors(character.position)
        val colorIndex = minOf((elapsed - 3) / 3, colors.size - 1)
        return Cell(character.scalar, colors[colorIndex], 0u)
    }

    private fun finalCoolingColors(position: Coordinate): List<UInt> {
        val finalColor = finalColor(position)
        val finalColorObj = Color(
            ((finalColor shr 16) and 0xFFu).toInt(),
            ((finalColor shr 8) and 0xFFu).toInt(),
            (finalColor and 0xFFu).toInt()
        )
        val stops = laserEtchConfiguration.coolGradientStops + listOf(finalColorObj)
        return Gradient(stops, 8).spectrum.map { it.asUInt }
    }

    private fun finalColor(position: Coordinate): UInt {
        val nonFill = characters.filter { !it.isFill }
        val gradient = Gradient(laserEtchConfiguration.finalGradientStops, laserEtchConfiguration.finalGradientSteps)
        val mapping = gradient.coordinateColorMapping(
            minRow = nonFill.minOfOrNull { it.position.row } ?: 1,
            maxRow = nonFill.maxOfOrNull { it.position.row } ?: 1,
            minColumn = nonFill.minOfOrNull { it.position.column } ?: 1,
            maxColumn = nonFill.maxOfOrNull { it.position.column } ?: 1,
            direction = laserEtchConfiguration.finalGradientDirection
        )
        return mapping.entries.firstOrNull { it.coordinate == position }?.color?.asUInt ?: 0xFFFFFFu
    }

    private fun sparkCoordinate(spark: Spark, elapsed: Int): Coordinate {
        val distance = maxOf(Geometry.bezierLength(spark.start, listOf(spark.control), spark.end), 0.3)
        val steps = PyCompat.roundHalfEven(distance / 0.3)
        val progress = if (steps == 0) 1.0 else minOf((elapsed + 1).toDouble() / steps.toDouble(), 1.0)
        return Geometry.coordinateOnBezier(
            spark.start, listOf(spark.control), spark.end,
            if (distance == 0.0) 1.0 else (Easing.OutSine.value(progress) * distance) / distance
        )
    }

    private fun put(cell: Cell, coordinate: Coordinate, frame: Frame) {
        if (coordinate.column in 1..canvas.columns && coordinate.row in 1..canvas.rows) {
            frame[coordinate.column, coordinate.row] = cell
        }
    }

    private fun recursiveBacktrackerOrder(): List<Int> {
        if (characters.isEmpty()) return emptyList()
        val textLeft = characters.minOf { it.position.column }
        val textRight = characters.maxOf { it.position.column }
        val textBottom = characters.minOf { it.position.row }
        val textTop = characters.maxOf { it.position.row }

        val start = Coordinate(rng.integer(textLeft..textRight), rng.integer(textBottom..textTop))
        val startIndex = characters.indexOfFirst { it.position == start }
        if (startIndex == -1) return characters.indices.toList()

        val linked = BooleanArray(characters.size)
        val order = ArrayList<Int>()
        order.add(startIndex)
        var current = startIndex
        val stack = ArrayList<Int>()
        stack.add(startIndex)

        while (stack.isNotEmpty()) {
            val unvisited = neighbors(current, textLeft, textRight, textBottom, textTop).filter { !linked[it] }
            if (unvisited.isNotEmpty()) {
                val next = unvisited[rng.integer(0 until unvisited.size)]
                linked[current] = true
                linked[next] = true
                order.add(next)
                stack.add(next)
                current = next
            } else {
                stack.removeAt(stack.size - 1)
                if (stack.isNotEmpty()) {
                    current = stack[stack.size - 1]
                }
            }
        }
        return order
    }

    private fun neighbors(index: Int, textLeft: Int, textRight: Int, textBottom: Int, textTop: Int): List<Int> {
        val pos = characters[index].position
        val candidates = listOf(
            Coordinate(pos.column, pos.row + 1),
            Coordinate(pos.column + 1, pos.row),
            Coordinate(pos.column, pos.row - 1),
            Coordinate(pos.column - 1, pos.row)
        )
        return candidates.mapNotNull { coord ->
            if (coord.column in textLeft..textRight && coord.row in textBottom..textTop) {
                val found = characters.indexOfFirst { it.position == coord }
                if (found != -1) found else null
            } else null
        }
    }
}
