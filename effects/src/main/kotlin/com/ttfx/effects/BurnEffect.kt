package com.ttfx.effects

import com.ttfx.core.*

class BurnEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    data class Configuration(
        val startingColor: Color = Color("837373"),
        val burnColors: List<Color> = listOf(
            Color("ffffff"),
            Color("fff75d"),
            Color("fe650d"),
            Color("8A003C"),
            Color("510100")
        ),
        val smokeChance: Double = 0.5,
        val finalGradientStops: List<Color> = listOf(Color("00c3ff"), Color("ffff1c")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientDirection: GradientDirection = GradientDirection.Vertical
    ) {
        init {
            require(smokeChance in 0.0..1.0) { "smoke chance must be in 0...1" }
        }
    }

    private companion object {
        private const val EXPLICIT_BLACK_FOREGROUND_SENTINEL: UInt = 0xFFFF_FFFEu
    }

    private data class Visual(val symbol: Int, val color: UInt)

    private class Glyph(
        val coordinate: Coordinate,
        val symbol: Int,
        val final: List<Visual>,
        var visual: Visual,
        var age: Int? = null,
        var finishing: Boolean = false
    )

    private class Smoke(
        val symbol: Int,
        var origin: Coordinate = Coordinate(1, 1),
        var target: Coordinate = Coordinate(1, 1),
        var coordinate: Coordinate = Coordinate(1, 1),
        var maxSteps: Int = 0,
        var age: Int? = null,
        var color: UInt = 0u
    )

    private var rng: Xoshiro256PlusPlus = configuration.makeRNG(seed)
    private var glyphs = ArrayList<Glyph>()
    private val sourceAt = HashMap<Coordinate, Int>()
    private var pending = ArrayList<Coordinate>()
    private var burnFrames = ArrayList<Visual>()
    private var smokeFrames = ArrayList<UInt>()
    private var smoke = ArrayList<Smoke>()
    private var available = ArrayList<Int>()
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
        val left = coordinates.minOf { it.column }
        val right = coordinates.maxOf { it.column }
        val bottom = coordinates.minOf { it.row }
        val top = coordinates.maxOf { it.row }
        val start = Coordinate(column = rng.integer(left..right), row = rng.integer(bottom..top))

        val symbols = listOf(".", ",", "'", "`", "#", "*").map { it.codePointAt(0) }
        for (i in 0 until 2000) {
            smoke.add(Smoke(symbol = symbols[rng.integer(0 until symbols.size)]))
        }
        available = ArrayList(smoke.indices.toList())

        val smokeGradient = Gradient(listOf(Color("504F4F"), Color("C7C7C7")), steps = 9)
        smokeFrames = ArrayList(smokeGradient.spectrum.flatMap { color ->
            List(10) { color.asUInt }
        })

        val finalGradient = Gradient(stops = options.finalGradientStops, steps = options.finalGradientSteps)
        val mapping = finalGradient.coordinateColorMapping(
            minRow = bottom,
            maxRow = top,
            minColumn = left,
            maxColumn = right,
            direction = options.finalGradientDirection
        ).entries.associate { it.coordinate to it.color }

        val fire = Gradient(stops = options.burnColors, steps = 10).spectrum
        val fireSymbols = listOf("'", ".", "▖", "▙", "█", "▜", "▀", "▝", ".").map { it.codePointAt(0) }
        val count = maxOf(fire.size, fireSymbols.size)

        fun distribution(index: Int, smaller: Int): Int {
            val base = count / smaller
            val remainder = count % smaller
            var boundary = 0
            for (candidate in 0 until smaller) {
                boundary += base + (if (candidate < remainder) 1 else 0)
                if (index < boundary) return candidate
            }
            return smaller - 1
        }

        burnFrames = ArrayList(count * 4)
        for (index in 0 until count) {
            val vis = Visual(
                symbol = fireSymbols[distribution(index, fireSymbols.size)],
                color = fire[distribution(index, fire.size)].asUInt
            )
            for (f in 0 until 4) {
                burnFrames.add(vis)
            }
        }

        val linked = HashSet<Coordinate>()
        val edges = ArrayList<Coordinate>()
        edges.add(start)
        pending.add(start)

        fun neighbors(coordinate: Coordinate): List<Coordinate> {
            val result = ArrayList<Coordinate>()
            val deltas = listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0)
            for ((dx, dy) in deltas) {
                val next = Coordinate(column = coordinate.column + dx, row = coordinate.row + dy)
                if (next.column in left..right && next.row in bottom..top && next !in linked) {
                    result.add(next)
                }
            }
            return result
        }

        while (edges.isNotEmpty()) {
            val current = edges.removeAt(rng.integer(0 until edges.size))
            val nextOptions = ArrayList(neighbors(current))
            if (nextOptions.isNotEmpty()) {
                val next = nextOptions.removeAt(rng.integer(0 until nextOptions.size))
                linked.add(current)
                linked.add(next)
                pending.add(next)
                if (nextOptions.isNotEmpty()) {
                    edges.add(current)
                }
                if (neighbors(next).isNotEmpty()) {
                    edges.add(next)
                }
            }
        }

        for (index in input.scalars.indices) {
            val coordinate = coordinates[index]
            val mappedColor = mapping[coordinate] ?: options.finalGradientStops.last()
            val timelineGradient = Gradient(stops = listOf(fire.last(), mappedColor), steps = 8)
            val timeline = timelineGradient.spectrum.flatMap { col ->
                List(4) { Visual(symbol = input.scalars[index], color = col.asUInt) }
            }
            sourceAt[coordinate] = glyphs.size
            glyphs.add(
                Glyph(
                    coordinate = coordinate,
                    symbol = input.scalars[index],
                    final = timeline,
                    visual = Visual(symbol = input.scalars[index], color = options.startingColor.asUInt)
                )
            )
        }
    }

    override fun tick(frame: Frame): TickStatus {
        if (complete) return TickStatus.Complete

        for (i in frame.cells.indices) {
            frame.cells[i] = Cell.BLANK
        }

        val releaseCount = rng.integer(2..4)
        for (i in 0 until releaseCount) {
            if (pending.isNotEmpty()) {
                val coordinate = pending.removeAt(0)
                sourceAt[coordinate]?.let { glyphs[it].age = 0 }
            }
        }

        val activeSmoke = ArrayList<Int>()
        for (i in smoke.indices) {
            if (smoke[i].age != null) activeSmoke.add(i)
        }

        for (index in glyphs.indices) {
            val age = glyphs[index].age ?: continue
            val timeline = if (glyphs[index].finishing) glyphs[index].final else burnFrames
            glyphs[index].visual = timeline[age]
            if (age + 1 == timeline.size) {
                if (glyphs[index].finishing) {
                    glyphs[index].age = null
                } else {
                    glyphs[index].finishing = true
                    glyphs[index].age = 0
                    glyphs[index].visual = glyphs[index].final[0]
                    if (rng.random() <= options.smokeChance && available.isNotEmpty()) {
                        val id = available.removeAt(available.size - 1)
                        val origin = glyphs[index].coordinate
                        val target = Coordinate(
                            column = rng.integer((origin.column - 4)..(origin.column + 4)),
                            row = canvas.rows + 1
                        )
                        smoke[id].origin = origin
                        smoke[id].coordinate = origin
                        smoke[id].target = target
                        smoke[id].maxSteps = PyCompat.roundHalfEven(Geometry.lineLength(from = origin, to = target) / 0.5)
                        smoke[id].age = 0
                        smoke[id].color = smokeFrames[0]
                    }
                }
            } else {
                glyphs[index].age = age + 1
            }
        }

        for (id in activeSmoke) {
            val age = smoke[id].age!!
            val ratio = if (smoke[id].maxSteps == 0) 1.0 else minOf(1.0, (age + 1).toDouble() / smoke[id].maxSteps.toDouble())
            val distance = Geometry.lineLength(from = smoke[id].origin, to = smoke[id].target)
            smoke[id].coordinate = Geometry.coordinateOnLine(
                from = smoke[id].origin,
                to = smoke[id].target,
                t = if (distance == 0.0) 1.0 else (ratio * distance) / distance
            )
            smoke[id].color = smokeFrames[age]
            if (age + 1 == smokeFrames.size) {
                smoke[id].age = null
                available.add(id)
            } else {
                smoke[id].age = age + 1
            }
        }

        for (glyph in glyphs) {
            frame[glyph.coordinate.column, glyph.coordinate.row] = Cell(
                codepoint = glyph.visual.symbol,
                foreground = glyph.visual.color,
                background = if (glyph.visual.color == 0u) EXPLICIT_BLACK_FOREGROUND_SENTINEL else 0u
            )
        }

        for (particle in smoke) {
            if (particle.age != null) {
                val coordinate = particle.coordinate
                if (coordinate.column in 1..canvas.columns && coordinate.row in 1..canvas.rows) {
                    frame[coordinate.column, coordinate.row] = Cell(
                        codepoint = particle.symbol,
                        foreground = particle.color,
                        background = 0u
                    )
                }
            }
        }

        complete = pending.isEmpty() && glyphs.none { it.age != null } && smoke.none { it.age != null }
        return if (complete) TickStatus.Complete else TickStatus.Running
    }
}
