package com.ttfx.effects

import com.ttfx.core.*
import kotlin.math.abs

class SweepEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    enum class Direction {
        DiagonalTopLeftToBottomRight,
        DiagonalBottomRightToTopLeft,
        DiagonalBottomLeftToTopRight,
        DiagonalTopRightToBottomLeft,
        RowBottomToTop,
        RowTopToBottom,
        ColumnLeftToRight,
        ColumnRightToLeft,
        CenterToOutside,
        OutsideToCenter
    }

    data class Configuration(
        val sweepSymbols: List<String> = listOf("█", "▓", "▒", "░"),
        val firstSweepDirection: Direction = Direction.ColumnRightToLeft,
        val secondSweepDirection: Direction = Direction.ColumnLeftToRight,
        val finalGradientStops: List<Color> = listOf(Color("8A008A"), Color("00D1FF"), Color("ffffff")),
        val finalGradientSteps: List<Int> = listOf(8),
        val finalGradientDirection: GradientDirection = GradientDirection.Vertical
    ) {
        init {
            require(sweepSymbols.isNotEmpty()) { "sweep symbols must not be empty" }
            require(finalGradientStops.isNotEmpty()) { "final gradient stops must not be empty" }
            require(finalGradientSteps.isNotEmpty() && finalGradientSteps.all { it > 0 }) { "final gradient steps must be positive" }
        }
    }

    private companion object {
        private const val EXPLICIT_BLACK_FOREGROUND_SENTINEL: UInt = 0xFFFF_FFFEu
        private const val EASING_STEPS = 100
    }

    private enum class SceneKind { Initial, Second }

    private class SceneFrame(
        val symbol: Int,
        val foreground: UInt,
        val background: UInt = 0u,
        val duration: Int
    )

    private class Glyph(
        val coordinate: Coordinate,
        val inputSymbol: Int,
        val initialFrames: List<SceneFrame>,
        val secondFrames: List<SceneFrame>,
        var visible: Boolean = false,
        var activeScene: SceneKind? = null,
        var renderedFrame: SceneFrame? = null,
        var frameIndex: Int = 0,
        var ticksRemaining: Int = 0
    ) {
        val active: Boolean
            get() = activeScene != null

        val currentFrame: SceneFrame?
            get() = when (activeScene) {
                SceneKind.Initial -> initialFrames[frameIndex]
                SceneKind.Second -> secondFrames[frameIndex]
                null -> null
            }
    }

    private var rng: Xoshiro256PlusPlus = configuration.makeRNG(seed)
    private var glyphs = ArrayList<Glyph>()
    private var firstGroups = ArrayList<List<Int>>()
    private var secondGroups = ArrayList<List<Int>>()
    private var easingStep = 0
    private var previousLength = 0
    private var firstPhase = true
    private var complete = false

    init {
        build(input)
    }

    private fun build(input: InputText) {
        val inputSources = input.scalars.zip(input.positions).map { (scalar, pos) ->
            Coordinate(column = pos.column, row = pos.row) to scalar
        }
        if (inputSources.isEmpty()) {
            complete = true
            return
        }
        val inputByCoordinate = inputSources.toMap()
        data class RawSource(val symbol: Int, val coordinate: Coordinate, val isFill: Boolean)
        val sources = ArrayList<RawSource>()
        for (row in canvas.rows downTo 1) {
            for (column in 1..canvas.columns) {
                val coordinate = Coordinate(column = column, row = row)
                val scalar = inputByCoordinate[coordinate]
                sources.add(RawSource(symbol = scalar ?: 32, coordinate = coordinate, isFill = scalar == null))
            }
        }

        val coordinates = inputSources.map { it.first }
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
        ).entries.associate { it.coordinate to it.color.asUInt }

        val grayPalette = listOf("A0A0A0", "808080", "404040", "202020", "101010").map { Color(it).asUInt }
        val secondPalette = finalGradient.spectrum.map { it.asUInt }
        val sweepScalars = options.sweepSymbols.map {
            if (it.isNotEmpty()) it.codePointAt(0) else 32
        }

        glyphs = ArrayList(sources.map { source ->
            val initialFrames = ArrayList<SceneFrame>(sweepScalars.size + 1)
            for (symbol in sweepScalars) {
                initialFrames.add(SceneFrame(symbol = symbol, foreground = choice(grayPalette), duration = 5))
            }
            initialFrames.add(SceneFrame(symbol = source.symbol, foreground = 0x808080u, duration = 1))

            val secondFrames = ArrayList<SceneFrame>(sweepScalars.size + 1)
            for (symbol in sweepScalars) {
                secondFrames.add(SceneFrame(symbol = symbol, foreground = choice(secondPalette), duration = 5))
            }
            val finalForeground = if (source.isFill) 0u else (finalMapping[source.coordinate] ?: 0u)
            val finalBackground = if (source.isFill) EXPLICIT_BLACK_FOREGROUND_SENTINEL else 0u
            secondFrames.add(SceneFrame(symbol = source.symbol, foreground = finalForeground, background = finalBackground, duration = 1))

            Glyph(
                coordinate = source.coordinate,
                inputSymbol = source.symbol,
                initialFrames = initialFrames,
                secondFrames = secondFrames
            )
        })

        firstGroups = groupedGlyphs(options.firstSweepDirection)
        secondGroups = groupedGlyphs(options.secondSweepDirection)
    }

    private fun choice(values: List<UInt>): UInt {
        return values[rng.integer(0 until values.size)]
    }

    override fun tick(frame: Frame): TickStatus {
        if (complete && glyphs.none { it.active }) return TickStatus.Complete

        advanceEaser()
        render(frame)
        advanceScenes()

        if (complete && glyphs.none { it.active }) return TickStatus.Complete
        return TickStatus.Running
    }

    private fun advanceEaser() {
        if (complete) return
        val groups = if (firstPhase) firstGroups else secondGroups
        if (easingStep >= EASING_STEPS) return
        easingStep += 1
        val progress = easingStep.toDouble() / EASING_STEPS.toDouble()
        val eased = Easing.InOutCirc.value(progress).coerceIn(0.0, 1.0)
        val length = (eased * groups.size.toDouble()).toInt()
        if (length > previousLength) {
            for (groupIndex in previousLength until length) {
                for (index in groups[groupIndex]) {
                    activate(index, if (firstPhase) SceneKind.Initial else SceneKind.Second)
                }
            }
        }
        previousLength = length
        if (easingStep >= EASING_STEPS) {
            if (firstPhase) {
                firstPhase = false
                easingStep = 0
                previousLength = 0
            } else {
                complete = true
            }
        }
    }

    private fun activate(index: Int, scene: SceneKind) {
        glyphs[index].visible = true
        glyphs[index].activeScene = scene
        glyphs[index].frameIndex = 0
        glyphs[index].renderedFrame = glyphs[index].currentFrame
        glyphs[index].ticksRemaining = glyphs[index].currentFrame?.duration ?: 0
    }

    private fun advanceScenes() {
        for (index in glyphs.indices) {
            if (!glyphs[index].active) continue
            glyphs[index].ticksRemaining -= 1
            if (glyphs[index].ticksRemaining != 0) continue
            val frameCount = if (glyphs[index].activeScene == SceneKind.Initial) {
                glyphs[index].initialFrames.size
            } else {
                glyphs[index].secondFrames.size
            }
            if (glyphs[index].frameIndex + 1 < frameCount) {
                glyphs[index].frameIndex += 1
                glyphs[index].renderedFrame = glyphs[index].currentFrame
                glyphs[index].ticksRemaining = glyphs[index].currentFrame?.duration ?: 0
            } else {
                glyphs[index].activeScene = null
            }
        }
    }

    private fun groupedGlyphs(direction: Direction): ArrayList<List<Int>> {
        val ordered = glyphs.indices.sortedWith { i1, i2 ->
            val lhs = glyphs[i1].coordinate
            val rhs = glyphs[i2].coordinate
            if (lhs.row != rhs.row) lhs.row.compareTo(rhs.row) else lhs.column.compareTo(rhs.column)
        }

        val key: (Glyph) -> Int
        val reverse: Boolean
        when (direction) {
            Direction.DiagonalTopLeftToBottomRight -> {
                key = { it.coordinate.column - it.coordinate.row }
                reverse = false
            }
            Direction.DiagonalBottomRightToTopLeft -> {
                key = { it.coordinate.column - it.coordinate.row }
                reverse = true
            }
            Direction.DiagonalBottomLeftToTopRight -> {
                key = { it.coordinate.column + it.coordinate.row }
                reverse = false
            }
            Direction.DiagonalTopRightToBottomLeft -> {
                key = { it.coordinate.column + it.coordinate.row }
                reverse = true
            }
            Direction.RowBottomToTop -> {
                key = { it.coordinate.row }
                reverse = false
            }
            Direction.RowTopToBottom -> {
                key = { it.coordinate.row }
                reverse = true
            }
            Direction.ColumnLeftToRight -> {
                key = { it.coordinate.column }
                reverse = false
            }
            Direction.ColumnRightToLeft -> {
                key = { it.coordinate.column }
                reverse = true
            }
            Direction.CenterToOutside, Direction.OutsideToCenter -> {
                val columns = glyphs.map { it.coordinate.column }
                val rows = glyphs.map { it.coordinate.row }
                val center = Coordinate(
                    column = columns.minOf { it } + (columns.maxOf { it } - columns.minOf { it }) / 2,
                    row = rows.minOf { it } + (rows.maxOf { it } - rows.minOf { it }) / 2
                )
                key = { abs(it.coordinate.column - center.column) + abs(it.coordinate.row - center.row) }
                reverse = direction == Direction.OutsideToCenter
            }
        }

        val buckets = LinkedHashMap<Int, MutableList<Int>>()
        for (index in ordered) {
            buckets.getOrPut(key(glyphs[index])) { ArrayList() }.add(index)
        }
        val result = ArrayList<List<Int>>(buckets.keys.sorted().map { buckets[it]!! })
        if (reverse) {
            result.reverse()
        }
        return result
    }

    private fun render(frame: Frame) {
        for (index in frame.cells.indices) {
            frame.cells[index] = Cell.BLANK
        }
        for (glyph in glyphs) {
            if (!glyph.visible) continue
            val sceneFrame = glyph.renderedFrame ?: continue
            if (glyph.coordinate.column in 1..canvas.columns && glyph.coordinate.row in 1..canvas.rows) {
                val cellIndex = (canvas.rows - glyph.coordinate.row) * canvas.columns + glyph.coordinate.column - 1
                frame.cells[cellIndex] = Cell(
                    codepoint = sceneFrame.symbol,
                    foreground = sceneFrame.foreground,
                    background = sceneFrame.background
                )
            }
        }
    }
}
