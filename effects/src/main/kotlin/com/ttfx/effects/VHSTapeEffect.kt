package com.ttfx.effects

import com.ttfx.core.*

class VHSTapeEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    data class Configuration(
        val glitchLineColors: List<Color> = listOf(
            Color("ffffff"), Color("ff0000"), Color("00ff00"), Color("0000ff"), Color("ffffff")
        ),
        val glitchWaveColors: List<Color> = listOf(
            Color("ffffff"), Color("ff0000"), Color("00ff00"), Color("0000ff"), Color("ffffff")
        ),
        val noiseColors: List<Color> = listOf(
            Color("1e1e1f"), Color("3c3b3d"), Color("6d6c70"), Color("a2a1a6"), Color("cbc9cf"), Color("ffffff")
        ),
        val glitchLineChance: Double = 0.05,
        val noiseChance: Double = 0.004,
        val totalGlitchTime: Int = 600,
        val finalGradientStops: List<Color> = listOf(Color("ab48ff"), Color("e7b2b2"), Color("fffebd")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientDirection: GradientDirection = GradientDirection.Vertical
    ) {
        init {
            require(glitchLineColors.isNotEmpty()) { "glitch line colors must not be empty" }
            require(glitchWaveColors.isNotEmpty()) { "glitch wave colors must not be empty" }
            require(noiseColors.isNotEmpty()) { "noise colors must not be empty" }
            require(totalGlitchTime > 0) { "total glitch time must be positive" }
            require(finalGradientStops.isNotEmpty()) { "final gradient stops must not be empty" }
        }
    }

    private companion object {
        private const val EXPLICIT_BLACK_FOREGROUND_SENTINEL: UInt = 0xFFFF_FFFEu

        private fun cell(symbol: Int, color: Color): Cell {
            val rgb = color.asUInt
            return Cell(
                codepoint = symbol,
                foreground = rgb,
                background = if (rgb == 0u) EXPLICIT_BLACK_FOREGROUND_SENTINEL else 0u
            )
        }
    }

    private enum class Phase { Glitching, Noise, Redraw, Complete }
    private enum class Scene(val rawValue: Int) { Base(0), Forward(1), Backward(2), Snow(3), FinalSnow(4), Redraw(5) }
    private enum class Path { Glitch, Restore, Mid, End }

    private class Motion(
        val kind: Path,
        val origin: Coordinate,
        val target: Coordinate,
        speed: Double,
        var hold: Int
    ) {
        val distance: Double = Geometry.lineLength(from = origin, to = target)
        val steps: Int = PyCompat.roundHalfEven(distance / speed)
        var step: Int = 0

        fun advance(): Pair<Coordinate, Boolean> {
            var coordinate = target
            if (steps > 0 && step < steps && distance > 0.0) {
                step += 1
                val t = (step.toDouble() / steps.toDouble() * distance) / distance
                coordinate = Geometry.coordinateOnLine(from = origin, to = target, t = t)
            }
            if (step == steps) {
                if (hold != 0) {
                    hold -= 1
                    return coordinate to false
                }
                return coordinate to true
            }
            return coordinate to false
        }
    }

    private class Glyph(
        val source: Coordinate,
        val target: Coordinate,
        var coordinate: Coordinate,
        var visual: Cell,
        var frames: List<List<Cell>>,
        var cursors: IntArray = IntArray(6),
        var scene: Scene? = Scene.Base,
        var motion: Motion? = null,
        var hold: Int,
        var glitchSpeed: Double = 2.0,
        var restoreSpeed: Double = 2.0,
        var active: Boolean = false
    )

    private var rng: Xoshiro256PlusPlus = configuration.makeRNG(seed)
    private var glyphs = ArrayList<Glyph>()
    private var lines = ArrayList<List<Int>>()
    private var glitchLines = ArrayList<Int>()
    private var wave = ArrayList<Int>()
    private var waveTop: Int? = null
    private var bottom = 1
    private var top = 1
    private var elapsed = 0
    private var phase = Phase.Glitching
    private var redrawing = false
    private var redrawRows = ArrayList<Int>()

    init {
        build(input)
    }

    private fun build(input: InputText) {
        if (input.scalars.isEmpty()) {
            phase = Phase.Complete
            return
        }
        val coordinates = input.positions.map { Coordinate(column = it.column, row = it.row) }
        bottom = coordinates.minOf { it.row }
        top = coordinates.maxOf { it.row }

        val gradient = Gradient(stops = options.finalGradientStops, steps = options.finalGradientSteps)
        val mapping = gradient.coordinateColorMapping(
            minRow = bottom,
            maxRow = top,
            minColumn = coordinates.minOf { it.column },
            maxColumn = coordinates.maxOf { it.column },
            direction = options.finalGradientDirection
        ).entries.associate { it.coordinate to it.color }

        glyphs = ArrayList(coordinates.indices.map {
            val c = mapping[coordinates[it]] ?: options.finalGradientStops.last()
            Glyph(
                source = coordinates[it],
                target = coordinates[it],
                coordinate = coordinates[it],
                visual = cell(input.scalars[it], c),
                frames = emptyList(),
                hold = 0
            )
        })

        val symbols = listOf(35, 42, 46, 58)
        val rows = coordinates.map { it.row }.toSet().sorted()
        for (row in rows) {
            val ids = coordinates.indices.filter { coordinates[it].row == row }
                .sortedBy { coordinates[it].column }
            lines.add(ids)
            val offset = rng.integer(4..25)
            val direction = if (rng.integer(0..1) == 0) -1 else 1
            val hold = rng.integer(1..50)
            for (id in ids) {
                val stable = glyphs[id].visual
                val forward = options.glitchLineColors.map { cell(input.scalars[id], it) }
                val snow = ArrayList<Cell>()
                val finalSnow = ArrayList<Cell>()
                for (i in 0 until 25) {
                    val symbol = symbols[rng.integer(0 until symbols.size)]
                    val color = options.noiseColors[rng.integer(0 until options.noiseColors.size)]
                    val c = cell(symbol, color)
                    snow.add(c)
                    snow.add(c)
                }
                for (i in 0 until 30) {
                    val symbol = symbols[rng.integer(0 until symbols.size)]
                    val color = options.noiseColors[rng.integer(0 until options.noiseColors.size)]
                    val c = cell(symbol, color)
                    finalSnow.add(c)
                    finalSnow.add(c)
                }
                glyphs[id] = Glyph(
                    source = coordinates[id],
                    target = Coordinate(column = coordinates[id].column + offset * direction, row = row),
                    coordinate = coordinates[id],
                    visual = stable,
                    frames = listOf(
                        listOf(stable),
                        forward,
                        forward.reversed(),
                        snow + listOf(stable),
                        finalSnow,
                        List(6) { cell(0x2588, Color("ffffff")) } + listOf(stable)
                    ),
                    hold = hold
                )
            }
        }
        redrawRows = ArrayList(lines.indices.toList())
    }

    private fun activateScene(id: Int, scene: Scene) {
        glyphs[id].scene = scene
        glyphs[id].visual = glyphs[id].frames[scene.rawValue][glyphs[id].cursors[scene.rawValue]]
    }

    private fun activatePath(id: Int, kind: Path) {
        val target: Coordinate
        val speed: Double
        when (kind) {
            Path.Glitch -> {
                target = glyphs[id].target
                speed = glyphs[id].glitchSpeed
            }
            Path.Restore -> {
                target = glyphs[id].source
                speed = glyphs[id].restoreSpeed
            }
            Path.Mid -> {
                target = Coordinate(column = glyphs[id].source.column + 8, row = glyphs[id].source.row)
                speed = 2.0
            }
            Path.End -> {
                target = Coordinate(column = glyphs[id].source.column + 14, row = glyphs[id].source.row)
                speed = 2.0
            }
        }
        glyphs[id].motion = Motion(
            kind = kind,
            origin = glyphs[id].coordinate,
            target = target,
            speed = speed,
            hold = if (kind == Path.Glitch) glyphs[id].hold else 0
        )
        activateScene(id, if (kind == Path.Restore) Scene.Backward else Scene.Forward)
    }

    private fun restore(line: Int, activate: Boolean) {
        for (id in lines[line]) {
            glyphs[id].restoreSpeed = 40.0 / rng.integer(20..40).toDouble()
            activatePath(id, Path.Restore)
            if (activate) glyphs[id].active = true
        }
    }

    private fun glitchWave() {
        if (waveTop == null) {
            val height = top - bottom + 1
            if (height < 3) return
            val lower = maxOf(3, PyCompat.roundHalfEven(height.toDouble() * 0.5))
            waveTop = bottom + rng.integer(lower..height)
        }
        if (wave.isNotEmpty() && wave.all { line -> lines[line].all { glyphs[it].motion == null } }) {
            if (rng.random() < 0.3) {
                waveTop = waveTop!! + if (rng.random() < 0.3) 1 else -1
            }
            waveTop = maxOf(2, minOf(waveTop!!, top))
        }
        val next = ((waveTop!! - 2)..waveTop!!).map { it - (bottom - 1) }.filter { it in lines.indices }
        for (line in wave) {
            if (line !in next) restore(line, activate = true)
        }
        wave = ArrayList(next)
        if (waveTop!! < bottom + 2) {
            for (line in wave) restore(line, activate = true)
            wave.clear()
            waveTop = null
        } else {
            val kinds = listOf(Path.Mid, Path.End, Path.Mid)
            for (i in 0 until minOf(wave.size, kinds.size)) {
                val line = wave[i]
                val kind = kinds[i]
                for (id in lines[line]) {
                    activatePath(id, kind)
                    glyphs[id].active = true
                }
            }
        }
    }

    override fun tick(frame: Frame): TickStatus {
        if (phase == Phase.Complete && glyphs.none { it.active }) return TickStatus.Complete

        for (i in frame.cells.indices) {
            frame.cells[i] = Cell.BLANK
        }

        when (phase) {
            Phase.Glitching -> {
                if (wave.isEmpty() || wave.all { line -> lines[line].all { glyphs[it].motion == null } }) {
                    glitchWave()
                }
                glitchLines.removeAll { line -> lines[line].all { glyphs[it].motion == null } }
                if (rng.random() < options.glitchLineChance && glitchLines.size < 3) {
                    val line = rng.integer(0 until lines.size)
                    if (line !in wave && line !in glitchLines) {
                        val hold = rng.integer(20..75)
                        glitchLines.add(line)
                        for (id in lines[line]) {
                            glyphs[id].hold = hold
                            glyphs[id].glitchSpeed = 40.0 / rng.integer(20..40).toDouble()
                            glyphs[id].restoreSpeed = 40.0 / rng.integer(20..40).toDouble()
                            activatePath(id, Path.Glitch)
                            glyphs[id].active = true
                        }
                    }
                }
                if (rng.random() < options.noiseChance) {
                    for (line in lines.indices) {
                        for (id in lines[line]) {
                            activateScene(id, Scene.Snow)
                            if (line !in wave && line !in glitchLines) {
                                glyphs[id].active = true
                            }
                        }
                    }
                }
                elapsed += 1
                if (elapsed >= options.totalGlitchTime) {
                    for (line in wave) restore(line, activate = false)
                    for (line in glitchLines) restore(line, activate = false)
                    phase = Phase.Noise
                }
            }
            Phase.Noise -> {
                if (glyphs.none { it.active }) {
                    for (id in glyphs.indices) {
                        activateScene(id, Scene.FinalSnow)
                        glyphs[id].active = true
                    }
                    phase = Phase.Redraw
                }
            }
            Phase.Redraw -> {
                if (glyphs.none { it.active }) redrawing = true
                if (redrawing) {
                    if (redrawRows.isNotEmpty()) {
                        val row = redrawRows.removeAt(redrawRows.size - 1)
                        for (id in lines[row]) {
                            activateScene(id, Scene.Redraw)
                            glyphs[id].active = true
                        }
                    } else {
                        phase = Phase.Complete
                    }
                }
            }
            Phase.Complete -> {}
        }

        for (id in glyphs.indices) {
            if (!glyphs[id].active) continue
            val motion = glyphs[id].motion
            if (motion != null) {
                val (coordinate, complete) = motion.advance()
                glyphs[id].coordinate = coordinate
                glyphs[id].motion = if (complete) null else motion
                if (complete && motion.kind == Path.Glitch) {
                    activatePath(id, Path.Restore)
                }
            }

            val scene = glyphs[id].scene
            if (scene != null) {
                val index = scene.rawValue
                val frames = glyphs[id].frames[index]
                var sceneDone = false
                if (scene == Scene.Forward || scene == Scene.Backward) {
                    val path = glyphs[id].motion
                    if (path != null) {
                        val progress = maxOf(path.step, 1).toDouble() / maxOf(path.steps, 1).toDouble()
                        val selected = minOf(frames.size - 1, maxOf(0, PyCompat.roundHalfEven((frames.size - 1).toDouble() * progress)))
                        glyphs[id].visual = frames[selected]
                    } else {
                        glyphs[id].visual = frames.last()
                        sceneDone = true
                    }
                } else {
                    glyphs[id].visual = frames[glyphs[id].cursors[index]]
                    glyphs[id].cursors[index] += 1
                    sceneDone = glyphs[id].cursors[index] == frames.size
                }
                if (sceneDone) {
                    glyphs[id].cursors[index] = 0
                    glyphs[id].scene = null
                    if (scene == Scene.Backward) activateScene(id, Scene.Base)
                }
            }
            glyphs[id].active = glyphs[id].motion != null || glyphs[id].scene != null
        }

        for (glyph in glyphs) {
            if (glyph.coordinate.column in 1..canvas.columns && glyph.coordinate.row in 1..canvas.rows) {
                frame[glyph.coordinate.column, glyph.coordinate.row] = glyph.visual
            }
        }

        return if (phase == Phase.Complete && glyphs.none { it.active }) TickStatus.Complete else TickStatus.Running
    }
}
