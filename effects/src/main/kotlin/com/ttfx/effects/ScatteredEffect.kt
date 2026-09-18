package com.ttfx.effects

import com.ttfx.core.*

class ScatteredEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    data class Configuration(
        val movementSpeed: Double = 0.5,
        val movementEasing: Easing = Easing.InOutBack,
        val finalGradientStops: List<Color> = listOf(Color("ff9048"), Color("ab9dff"), Color("bdffea")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientFrames: Int = 9,
        val finalGradientDirection: GradientDirection = GradientDirection.Vertical
    ) {
        init {
            require(movementSpeed > 0) { "movement speed must be positive" }
            require(finalGradientFrames > 0) { "final gradient frames must be positive" }
        }
    }

    private class Glyph(
        val characterID: Int,
        val target: Coordinate,
        val symbol: Int,
        val colors: List<UInt>,
        val start: Coordinate,
        val totalDistance: Double,
        val maxSteps: Int,
        var coordinate: Coordinate,
        var currentStep: Int = 0,
        var lastDistance: Double = 0.0,
        var pathActive: Boolean = true
    ) {
        val layer: Int
            get() = if (pathActive) 1 else 0
    }

    private var rng: Xoshiro256PlusPlus = configuration.makeRNG(seed)
    private var glyphs = ArrayList<Glyph>()
    private var initialHoldFrames = 25
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

        if (initialHoldFrames != 0) {
            initialHoldFrames -= 1
            render(frame)
            return TickStatus.Running
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
        val created = ArrayList<Source>(input.scalars.size)
        for (index in input.scalars.indices) {
            val symbol = input.scalars[index]
            val pos = input.positions[index]
            if (symbol != 32) {
                created.add(Source(characterID = index, symbol = symbol, coordinate = Coordinate(pos.column, pos.row)))
            }
        }
        if (created.isEmpty()) {
            isComplete = true
            return
        }

        val bottom = created.minOf { it.coordinate.row }
        val top = created.maxOf { it.coordinate.row }
        val left = created.minOf { it.coordinate.column }
        val right = created.maxOf { it.coordinate.column }
        val finalGradient = Gradient(options.finalGradientStops, options.finalGradientSteps)
        val mapping = finalGradient.coordinateColorMapping(
            minRow = bottom,
            maxRow = top,
            minColumn = left,
            maxColumn = right,
            direction = options.finalGradientDirection
        )
        val finalColorByCoordinate = mapping.entries.associate { it.coordinate to it.color }
        val startColor = finalGradient.spectrum[0]

        val sortedCreated = created.sortedWith(
            compareByDescending<Source> { it.coordinate.row }
                .thenBy { it.coordinate.column }
                .thenBy { it.characterID }
        )

        glyphs = ArrayList(sortedCreated.map { source ->
            val targetColor = finalColorByCoordinate[source.coordinate]!!
            val colors = Gradient(stops = listOf(startColor, targetColor), steps = 10).spectrum.map { it.asUInt }
            val start = startCoordinate()
            val distance = Geometry.lineLength(start, source.coordinate)
            Glyph(
                characterID = source.characterID,
                target = source.coordinate,
                symbol = source.symbol,
                colors = colors,
                start = start,
                totalDistance = distance,
                maxSteps = PyCompat.roundHalfEven(distance / options.movementSpeed),
                coordinate = start
            )
        })
    }

    private fun advancePaths() {
        for (glyph in glyphs) {
            if (!glyph.pathActive) continue
            val start = glyph.start
            if (glyph.maxSteps <= 0 || glyph.totalDistance == 0.0) {
                glyph.coordinate = glyph.target
                glyph.lastDistance = glyph.totalDistance
                glyph.pathActive = false
                continue
            }

            glyph.currentStep += 1
            val ratio = glyph.currentStep.toDouble() / glyph.maxSteps.toDouble()
            val distance = options.movementEasing.value(ratio) * glyph.totalDistance
            glyph.lastDistance = distance
            glyph.coordinate = Geometry.coordinateOnLine(
                from = start,
                to = glyph.target,
                t = distance / glyph.totalDistance
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
        data class Winner(val layer: Int, val order: Int, val glyph: Glyph)
        val winners = HashMap<Int, Winner>()
        for (glyph in glyphs.sortedBy { it.characterID }) {
            if (glyph.coordinate.column in 1..canvas.columns && glyph.coordinate.row in 1..canvas.rows) {
                val cellIndex = (canvas.rows - glyph.coordinate.row) * canvas.columns + glyph.coordinate.column - 1
                val existing = winners[cellIndex]
                if (existing != null && existing.layer > glyph.layer) continue
                winners[cellIndex] = Winner(glyph.layer, glyph.characterID, glyph)
            }
        }
        for ((cellIndex, winner) in winners) {
            frame.cells[cellIndex] = Cell(codepoint = winner.glyph.symbol, foreground = foreground(winner.glyph), background = 0u)
        }
    }

    private fun foreground(glyph: Glyph): UInt {
        if (!glyph.pathActive) return glyph.colors.last()
        if (glyph.currentStep == 0) return glyph.colors[0]
        val total = maxOf(glyph.totalDistance, 1.0)
        val remaining = maxOf(glyph.totalDistance - glyph.lastDistance, 1.0)
        val reached = maxOf(total - remaining, 1.0)
        val progress = reached / total
        val index = PyCompat.roundHalfEven((glyph.colors.size - 1).toDouble() * progress).coerceIn(0, glyph.colors.size - 1)
        return glyph.colors[index]
    }

    private fun startCoordinate(): Coordinate {
        if (canvas.columns < 2 || canvas.rows < 2) return Coordinate(1, 1)
        return Coordinate(
            column = rng.integer(1..canvas.columns),
            row = rng.integer(1..canvas.rows)
        )
    }
}
