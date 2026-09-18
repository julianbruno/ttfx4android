package com.ttfx.effects

import com.ttfx.core.*

class MatrixEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    data class Configuration(
        val highlightColor: Color = Color("dbffdb"),
        val rainColorGradient: List<Color> = listOf(Color("92be92"), Color("185318")),
        val rainSymbols: List<String> = listOf(
            "2", "5", "9", "8", "Z", "*", ")", ":", ".", "\"", "=", "+", "-", "¦", "|", "_",
            "ｦ", "ｱ", "ｳ", "ｴ", "ｵ", "ｶ", "ｷ", "ｹ", "ｺ", "ｻ", "ｼ", "ｽ", "ｾ", "ｿ", "ﾀ", "ﾂ",
            "ﾃ", "ﾅ", "ﾆ", "ﾇ", "ﾈ", "ﾊ", "ﾋ", "ﾎ", "ﾏ", "ﾐ", "ﾑ", "ﾒ", "ﾓ", "ﾔ", "ﾕ", "ﾗ", "ﾘ", "ﾜ"
        ),
        val rainFallDelayRange: IntRange = 2..15,
        val rainColumnDelayRange: IntRange = 3..9,
        val rainTime: Int = 15,
        val symbolSwapChance: Double = 0.005,
        val colorSwapChance: Double = 0.001,
        val resolveDelay: Int = 3,
        val finalGradientStops: List<Color> = listOf(Color("92be92"), Color("336b33")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientFrames: Int = 3,
        val finalGradientDirection: GradientDirection = GradientDirection.Radial
    ) {
        init {
            require(rainColorGradient.isNotEmpty()) { "rain color gradient must not be empty" }
            require(rainSymbols.isNotEmpty()) { "rain symbols must not be empty" }
            require(rainFallDelayRange.first > 0) { "rain fall delay must be positive" }
            require(rainColumnDelayRange.first > 0) { "rain column delay must be positive" }
            require(rainTime > 0) { "rain time must be positive" }
            require(symbolSwapChance > 0 && colorSwapChance > 0) { "swap chances must be positive" }
            require(resolveDelay > 0) { "resolve delay must be positive" }
            require(finalGradientFrames > 0) { "final gradient frames must be positive" }
        }
    }

    private companion object {
        private const val EXPLICIT_BLACK_FOREGROUND_SENTINEL: UInt = 0xFFFF_FFFEu
    }

    private enum class Phase { Rain, Fill, Resolve }

    private class Glyph(
        val input: Coordinate,
        val source: Int,
        val resolve: List<UInt>,
        var coordinate: Coordinate,
        var symbol: Int = 32,
        var foreground: UInt = 0u,
        var visible: Boolean = false,
        var sceneStep: Int? = null
    )

    private class Column(
        val characters: List<Int>,
        var pending: ArrayList<Int> = ArrayList(),
        var visible: ArrayList<Int> = ArrayList(),
        var filling: Boolean = false,
        var baseDelay: Int = 0,
        var delay: Int = 0,
        var length: Int = 0,
        var hold: Int = 0,
        var dropChance: Double = 0.08
    )

    private val stepDuration: Double = 1.0 / configuration.effectiveFrameRate.toDouble()
    private var elapsed: Double = 0.0
    private var rng: Xoshiro256PlusPlus = configuration.makeRNG(seed)
    private var glyphs = ArrayList<Glyph>()
    private var columns = ArrayList<Column>()
    private var pending = ArrayList<Int>()
    private var active = ArrayList<Int>()
    private var full = ArrayList<Int>()
    private var colors = ArrayList<Color>()
    private var phase = Phase.Rain
    private var columnDelay = 0
    private var resolveDelay = options.resolveDelay
    private var complete = false

    init {
        build(input)
    }

    private fun build(input: InputText) {
        if (input.scalars.isEmpty()) {
            complete = true
            return
        }
        val coordinates = input.positions.map { Coordinate(column = it.column, row = it.row) }
        val sources = coordinates.zip(input.scalars).toMap()
        val finalGradient = Gradient(options.finalGradientStops, options.finalGradientSteps)
        val mapping = finalGradient.coordinateColorMapping(
            minRow = coordinates.minOf { it.row },
            maxRow = coordinates.maxOf { it.row },
            minColumn = coordinates.minOf { it.column },
            maxColumn = coordinates.maxOf { it.column },
            direction = options.finalGradientDirection
        ).entries.associate { it.coordinate to it.color }

        colors = ArrayList(Gradient(stops = options.rainColorGradient, steps = 6).spectrum)

        for (column in 1..canvas.columns) {
            val characters = ArrayList<Int>()
            for (row in canvas.rows downTo 1) {
                val coordinate = Coordinate(column = column, row = row)
                val targetColor = mapping[coordinate] ?: Color("000000")
                val resolutionGradient = Gradient(stops = listOf(options.highlightColor, targetColor), steps = 8)
                val resolution = resolutionGradient.spectrum.flatMap { col ->
                    List(options.finalGradientFrames) { col.asUInt }
                }
                characters.add(glyphs.size)
                glyphs.add(
                    Glyph(
                        input = coordinate,
                        source = sources[coordinate] ?: 32,
                        resolve = resolution,
                        coordinate = coordinate
                    )
                )
            }
            columns.add(Column(characters = characters))
            setup(columns.size - 1, filling = false)
        }

        pending = ArrayList(columns.indices.toList())
        rng.shuffle(pending)
    }

    private fun setup(index: Int, filling: Boolean) {
        columns[index].filling = filling
        columns[index].pending = ArrayList(columns[index].characters)
        columns[index].visible.clear()
        for (id in columns[index].characters) {
            glyphs[id].visible = false
            glyphs[id].coordinate = glyphs[id].input
        }
        columns[index].baseDelay = if (filling) {
            rng.integer(maxOf(options.rainFallDelayRange.first / 3, 1)..maxOf(options.rainFallDelayRange.last / 3, 1))
        } else {
            rng.integer(options.rainFallDelayRange)
        }
        columns[index].delay = 0
        columns[index].length = if (filling) {
            canvas.rows
        } else {
            rng.integer(maxOf(1, (canvas.rows.toDouble() * 0.1).toInt())..canvas.rows)
        }
        columns[index].hold = if (columns[index].length == canvas.rows) rng.integer(20..45) else 0
    }

    private fun trim(index: Int) {
        if (columns[index].visible.isEmpty()) return
        glyphs[columns[index].visible.removeAt(0)].visible = false
        if (columns[index].visible.size > 1) {
            val color = colors[rng.integer(maxOf(0, colors.size - 3) until colors.size)]
            glyphs[columns[index].visible[0]].foreground = color.adjustBrightness(0.65).asUInt
        }
    }

    private fun tickColumn(index: Int) {
        if (columns[index].delay == 0) {
            if (columns[index].pending.isNotEmpty()) {
                val next = columns[index].pending.removeAt(0)
                glyphs[next].symbol = randomSymbol()
                glyphs[next].foreground = options.highlightColor.asUInt
                if (columns[index].visible.isNotEmpty()) {
                    val previous = columns[index].visible.last()
                    glyphs[previous].foreground = randomColor()
                }
                glyphs[next].visible = true
                columns[index].visible.add(next)
            } else if (columns[index].visible.isNotEmpty()) {
                val last = columns[index].visible.last()
                if (glyphs[last].foreground == options.highlightColor.asUInt) {
                    glyphs[last].foreground = randomColor()
                }
                if (columns[index].hold != 0) {
                    columns[index].hold -= 1
                } else if (!columns[index].filling) {
                    if (rng.random() < columns[index].dropChance) {
                        for (id in columns[index].visible) {
                            glyphs[id].coordinate = Coordinate(column = glyphs[id].coordinate.column, row = glyphs[id].coordinate.row - 1)
                            if (glyphs[id].coordinate.row < 1) {
                                glyphs[id].visible = false
                            }
                        }
                        columns[index].visible.removeAll { !glyphs[it].visible }
                    }
                    trim(index)
                }
            }
            if (columns[index].visible.size > columns[index].length) {
                trim(index)
            }
            columns[index].delay = columns[index].baseDelay
        } else {
            columns[index].delay -= 1
        }

        for (id in columns[index].visible) {
            if (rng.random() < options.symbolSwapChance) {
                glyphs[id].symbol = randomSymbol()
            }
            if (rng.random() < options.colorSwapChance) {
                glyphs[id].foreground = randomColor()
            }
        }
    }

    override fun tick(frame: Frame): TickStatus {
        if (complete) return TickStatus.Complete

        for (i in frame.cells.indices) {
            frame.cells[i] = Cell.BLANK
        }

        if (phase != Phase.Resolve) {
            if (columnDelay == 0) {
                if (phase == Phase.Rain) {
                    val count = rng.integer(1..3)
                    for (i in 0 until count) {
                        if (pending.isNotEmpty()) {
                            active.add(pending.removeAt(0))
                        }
                    }
                } else {
                    active.addAll(pending)
                    pending.clear()
                }
                columnDelay = if (phase == Phase.Rain) rng.integer(options.rainColumnDelayRange) else 1
            } else {
                columnDelay -= 1
            }

            for (index in active) {
                tickColumn(index)
                if (columns[index].pending.isEmpty()) {
                    if (columns[index].filling && !full.contains(index)) {
                        full.add(index)
                    } else if (columns[index].visible.isEmpty()) {
                        setup(index, filling = phase == Phase.Fill)
                        pending.add(index)
                    }
                }
            }

            active.removeAll { columns[it].visible.isEmpty() }
            if (phase == Phase.Fill && pending.isEmpty() && active.all { columns[it].pending.isEmpty() && columns[it].filling }) {
                phase = Phase.Resolve
                active.clear()
            }
            if (phase == Phase.Rain && elapsed > options.rainTime.toDouble()) {
                phase = Phase.Fill
                for (index in active) {
                    columns[index].hold = 0
                    columns[index].dropChance = 1.0
                }
                for (index in pending) {
                    setup(index, filling = true)
                }
            }
        } else {
            for (index in full) {
                tickColumn(index)
                if (columns[index].visible.isNotEmpty()) {
                    if (resolveDelay == 0) {
                        val count = rng.integer(1..4)
                        for (i in 0 until count) {
                            if (columns[index].visible.isNotEmpty()) {
                                val pos = rng.integer(0 until columns[index].visible.size)
                                val id = columns[index].visible.removeAt(pos)
                                if (glyphs[id].source != 32) {
                                    glyphs[id].sceneStep = 0
                                } else {
                                    glyphs[id].visible = false
                                }
                            }
                        }
                        resolveDelay = options.resolveDelay
                    } else {
                        resolveDelay -= 1
                    }
                }
            }
            full.removeAll { columns[it].visible.isEmpty() }
        }

        val work = full.isNotEmpty() || active.isNotEmpty() || pending.isNotEmpty() || phase == Phase.Rain || glyphs.any { it.sceneStep != null }
        for (index in glyphs.indices) {
            val step = glyphs[index].sceneStep
            if (step != null) {
                glyphs[index].symbol = glyphs[index].source
                glyphs[index].foreground = glyphs[index].resolve[step]
                glyphs[index].sceneStep = if (step + 1 < glyphs[index].resolve.size) step + 1 else null
            }
            val glyph = glyphs[index]
            if (glyph.visible && glyph.coordinate.row in 1..canvas.rows) {
                frame[glyph.coordinate.column, glyph.coordinate.row] = Cell(
                    codepoint = glyph.symbol,
                    foreground = glyph.foreground,
                    background = if (glyph.foreground == 0u) EXPLICIT_BLACK_FOREGROUND_SENTINEL else 0u
                )
            }
        }

        elapsed += stepDuration
        complete = !work
        return if (complete) TickStatus.Complete else TickStatus.Running
    }

    private fun randomSymbol(): Int {
        val sym = options.rainSymbols[rng.integer(0 until options.rainSymbols.size)]
        return if (sym.isNotEmpty()) sym.codePointAt(0) else Cell.BLANK.codepoint
    }

    private fun randomColor(): UInt {
        return colors[rng.integer(0 until colors.size)].asUInt
    }
}
