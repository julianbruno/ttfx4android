package com.ttfx.effects

import com.ttfx.core.*

class RandomSequenceEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    private val options: Configuration = Configuration()
) : Effect {

    data class Configuration(
        val speed: Double = 0.007,
        val finalGradientStops: List<Color> = listOf(Color("8A008A"), Color("00D1FF"), Color("FFFFFF")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientFrames: Int = 8,
        val finalGradientDirection: GradientDirection = GradientDirection.Vertical
    ) {
        init {
            require(speed > 0.0) { "speed must be positive" }
            require(finalGradientFrames > 0) { "final gradient frames must be positive" }
        }
    }

    private data class Glyph(
        val characterID: Int,
        val coordinate: Coordinate,
        val symbol: Int,
        val frames: List<UInt>,
        var visible: Boolean = false,
        var active: Boolean = false,
        var frameIndex: Int = 0
    )

    private var rng: Xoshiro256PlusPlus = configuration.makeRNG(seed)
    private var glyphs = ArrayList<Glyph>()
    private var pending = ArrayList<Int>()
    private var charactersPerTick = 1
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

        var count = 0
        while (count < charactersPerTick && pending.isNotEmpty()) {
            val next = pending.removeAt(pending.size - 1)
            glyphs[next].visible = true
            glyphs[next].active = true
            count++
        }

        render(frame)
        advanceScenes()

        if (pending.isEmpty() && glyphs.none { it.active }) {
            isComplete = true
            return TickStatus.Complete
        }
        return TickStatus.Running
    }

    private fun build(input: InputText) {
        data class Item(val characterID: Int, val symbol: Int, val coordinate: Coordinate)
        val created = input.scalars.indices.map { i ->
            Item(i, input.scalars[i], Coordinate(input.positions[i].column, input.positions[i].row))
        }.sortedWith { a, b ->
            if (a.coordinate.row != b.coordinate.row) b.coordinate.row.compareTo(a.coordinate.row)
            else if (a.coordinate.column != b.coordinate.column) a.coordinate.column.compareTo(b.coordinate.column)
            else a.characterID.compareTo(b.characterID)
        }

        if (created.isEmpty()) {
            isComplete = true
            return
        }

        charactersPerTick = maxOf((options.speed * input.scalars.size).toInt(), 1)
        val bottom = created.minOf { it.coordinate.row }
        val top = created.maxOf { it.coordinate.row }
        val left = created.minOf { it.coordinate.column }
        val right = created.maxOf { it.coordinate.column }

        val finalGradient = Gradient(options.finalGradientStops, options.finalGradientSteps)
        val finalMapping = finalGradient.coordinateColorMapping(
            minRow = bottom,
            maxRow = top,
            minColumn = left,
            maxColumn = right,
            direction = options.finalGradientDirection
        ).entries.associate { it.coordinate to it.color }

        val terminalBackground = Color("000000")
        glyphs = ArrayList(created.map { src ->
            val finalColor = finalMapping[src.coordinate] ?: Color("ffffff")
            val spectrum = Gradient(listOf(terminalBackground, finalColor), 7).spectrum
            val frames = spectrum.flatMap { c ->
                List(options.finalGradientFrames) { c.asUInt }
            }
            Glyph(src.characterID, src.coordinate, src.symbol, frames)
        })

        pending = ArrayList(glyphs.indices.toList())
        rng.shuffle(pending)
    }

    private fun advanceScenes() {
        for (g in glyphs) {
            if (!g.active) continue
            if (g.frameIndex + 1 < g.frames.size) {
                g.frameIndex++
            } else {
                g.active = false
            }
        }
    }

    private fun render(frame: Frame) {
        for (i in frame.cells.indices) {
            frame.cells[i] = Cell.BLANK
        }
        val sorted = glyphs.filter { it.visible }.sortedBy { it.characterID }
        for (g in sorted) {
            if (g.coordinate.column in 1..canvas.columns && g.coordinate.row in 1..canvas.rows) {
                val fg = g.frames[minOf(g.frameIndex, g.frames.size - 1)]
                frame[g.coordinate.column, g.coordinate.row] = Cell(
                    codepoint = g.symbol,
                    foreground = fg,
                    background = if (fg == 0u) 0xFFFF_FFFEu else 0u
                )
            }
        }
    }
}
