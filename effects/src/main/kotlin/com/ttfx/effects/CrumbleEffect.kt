package com.ttfx.effects

import com.ttfx.core.*

class CrumbleEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    data class Configuration(
        val finalGradientStops: List<Color> = listOf(Color("5CE1FF"), Color("FF8C00")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientDirection: GradientDirection = GradientDirection.Diagonal
    )

    private enum class Stage {
        Falling, Vacuuming, Resetting, Complete
    }

    private enum class Scene {
        Initial, Weaken, Dust, Flash, Strengthen
    }

    private data class PathState(
        val kind: Kind,
        val start: Coordinate,
        val end: Coordinate,
        val control: Coordinate?,
        val easing: Easing?,
        val steps: Int,
        var step: Int = 0
    ) {
        enum class Kind { Fall, Top, Input }
    }

    private data class Glyph(
        val characterID: Int,
        val inputCoordinate: Coordinate,
        val symbol: Int,
        val weakColor: UInt,
        val dustColor: UInt,
        val weakenColors: List<UInt>,
        val flashColors: List<UInt>,
        val strengthenColors: List<UInt>,
        val dustSymbols: List<Int>,
        var coordinate: Coordinate,
        var layer: Int = 0,
        var visible: Boolean = true,
        var scene: Scene = Scene.Initial,
        var sceneIndex: Int = 0,
        var sceneTicksRemaining: Int = 1,
        var path: PathState? = null,
        var active: Boolean = false,
        var inputPathCompletionPending: Boolean = false,
        var rendered: Pair<Int, UInt>? = null
    ) {
        val foreground: UInt
            get() = when (scene) {
                Scene.Initial -> weakColor
                Scene.Weaken -> weakenColors[sceneIndex]
                Scene.Dust -> dustColor
                Scene.Flash -> flashColors[sceneIndex]
                Scene.Strengthen -> strengthenColors[sceneIndex]
            }

        val codepoint: Int
            get() {
                if (scene == Scene.Dust) return dustSymbols[minOf(sceneIndex, dustSymbols.size - 1)]
                return symbol
            }
    }

    private var rng: Xoshiro256PlusPlus = configuration.makeRNG(seed)
    private var glyphs: MutableList<Glyph> = ArrayList()
    private var pending: MutableList<Int> = ArrayList()
    private var unvacuumed: MutableList<Int> = ArrayList()
    private var stage: Stage = Stage.Falling
    private var fallDelay = 12
    private var maxFallDelay = 12
    private var minFallDelay = 9
    private var fallGroupMaxSize = 1
    private var reset = false

    init {
        build(input)
    }

    override fun tick(frame: Frame): TickStatus {
        if (stage == Stage.Complete) return TickStatus.Complete

        when (stage) {
            Stage.Falling -> {
                if (pending.isNotEmpty()) {
                    if (fallDelay == 0) {
                        val groupSize = rng.integer(1..fallGroupMaxSize)
                        for (i in 0 until groupSize) {
                            if (pending.isEmpty()) break
                            val index = pending.removeAt(0)
                            activateWeaken(index)
                        }
                        fallDelay = rng.integer(minFallDelay..maxFallDelay)
                        if (rng.integer(1..10) > 4) {
                            fallGroupMaxSize++
                            minFallDelay = maxOf(0, minFallDelay - 1)
                            maxFallDelay = maxOf(0, maxFallDelay - 1)
                        }
                    } else {
                        fallDelay--
                    }
                }
                if (pending.isEmpty() && glyphs.none { it.active }) {
                    stage = Stage.Vacuuming
                }
            }
            Stage.Vacuuming -> {
                if (unvacuumed.isNotEmpty()) {
                    val count = rng.integer(3..10)
                    for (i in 0 until count) {
                        if (unvacuumed.isEmpty()) break
                        activateTop(unvacuumed.removeAt(0))
                    }
                }
                if (glyphs.none { it.active }) {
                    stage = Stage.Resetting
                }
            }
            Stage.Resetting -> {
                if (!reset) {
                    for (index in topToBottomOrder()) {
                        activateInput(index)
                    }
                    reset = true
                }
                if (glyphs.none { it.active }) {
                    stage = Stage.Complete
                }
            }
            Stage.Complete -> {}
        }

        updateActivePaths()
        updateActiveScenes()
        render(frame)
        pruneInactiveGlyphs()
        return if (stage == Stage.Complete) TickStatus.Complete else TickStatus.Running
    }

    private fun build(input: InputText) {
        data class Created(val characterID: Int, val symbol: Int, val coordinate: Coordinate)
        val created = ArrayList<Created>()
        for (index in input.scalars.indices) {
            val scalar = input.scalars[index]
            val pos = input.positions[index]
            if (scalar != 32) {
                created.add(Created(index, scalar, Coordinate(pos.column, pos.row)))
            }
        }
        if (created.isEmpty()) {
            stage = Stage.Complete
            return
        }

        val coordinates = created.map { it.coordinate }
        val bottom = coordinates.minOf { it.row }
        val top = coordinates.maxOf { it.row }
        val left = coordinates.minOf { it.column }
        val right = coordinates.maxOf { it.column }
        val finalGradient = Gradient(options.finalGradientStops, options.finalGradientSteps)
        val mapping = finalGradient.coordinateColorMapping(
            minRow = bottom,
            maxRow = top,
            minColumn = left,
            maxColumn = right,
            direction = options.finalGradientDirection
        )
        val finalColors = mapping.entries.associate { it.coordinate to it.color }
        val dustSymbols = listOf('*'.code, '.'.code, ','.code)

        glyphs = ArrayList(List(created.size) { placeholderGlyph() })
        val sortedIndices = created.indices.sortedWith { lhs, rhs ->
            val a = created[lhs].coordinate
            val b = created[rhs].coordinate
            if (a.row != b.row) b.row.compareTo(a.row) else a.column.compareTo(b.column)
        }

        for (sourceIndex in sortedIndices) {
            val source = created[sourceIndex]
            val finalColor = finalColors[source.coordinate]!!
            val weak = finalColor.adjustBrightness(0.65)
            val dust = finalColor.adjustBrightness(0.55)
            val weaken = Gradient(listOf(weak, dust), 9).spectrum.map { it.asUInt }
            val flash = Gradient(listOf(finalColor, Color("ffffff")), 6).spectrum.map { it.asUInt }
            val strengthen = Gradient(listOf(Color("ffffff"), finalColor), 9).spectrum.map { it.asUInt }
            val chosenDust = (0 until 5).map { dustSymbols[rng.integer(0 until dustSymbols.size)] }
            glyphs[sourceIndex] = Glyph(
                characterID = source.characterID,
                inputCoordinate = source.coordinate,
                symbol = source.symbol,
                weakColor = weak.asUInt,
                dustColor = dust.asUInt,
                weakenColors = weaken,
                flashColors = flash,
                strengthenColors = strengthen,
                dustSymbols = chosenDust,
                coordinate = source.coordinate
            )
        }
        pending = topToBottomOrder()
        rng.shuffle(pending)
        unvacuumed = created.indices.sorted().toMutableList()
        rng.shuffle(unvacuumed)
    }

    private fun activateWeaken(index: Int) {
        glyphs[index].scene = Scene.Weaken
        glyphs[index].sceneIndex = 0
        glyphs[index].sceneTicksRemaining = 4
        glyphs[index].active = true
    }

    private fun activateTop(index: Int) {
        val start = glyphs[index].coordinate
        val end = Coordinate(column = glyphs[index].inputCoordinate.column, row = canvas.rows)
        val control = Coordinate(column = centered(canvas.columns), row = centered(canvas.rows))
        glyphs[index].path = makePath(
            kind = PathState.Kind.Top,
            start = start,
            end = end,
            control = control,
            easing = Easing.OutQuint,
            speed = 1.0
        )
        glyphs[index].active = true
    }

    private fun activateInput(index: Int) {
        val start = glyphs[index].coordinate
        glyphs[index].path = makePath(
            kind = PathState.Kind.Input,
            start = start,
            end = glyphs[index].inputCoordinate,
            control = null,
            easing = null,
            speed = 1.0
        )
        glyphs[index].active = true
    }

    private fun makePath(
        kind: PathState.Kind,
        start: Coordinate,
        end: Coordinate,
        control: Coordinate?,
        easing: Easing?,
        speed: Double
    ): PathState {
        val length = if (control != null) {
            Geometry.bezierLength(from = start, controls = listOf(control), to = end)
        } else {
            Geometry.lineLength(from = start, to = end)
        }
        return PathState(
            kind = kind,
            start = start,
            end = end,
            control = control,
            easing = easing,
            steps = PyCompat.roundHalfEven(length / speed)
        )
    }

    private fun updateActivePaths() {
        for (index in glyphs.indices) {
            if (glyphs[index].active) {
                updatePath(index)
            }
        }
    }

    private fun updateActiveScenes() {
        for (index in glyphs.indices) {
            if (glyphs[index].active) {
                updateScene(index)
            }
        }
    }

    private fun pruneInactiveGlyphs() {
        for (index in glyphs.indices) {
            if (glyphs[index].active) {
                if (glyphs[index].path == null && !sceneIsActive(glyphs[index])) {
                    glyphs[index].active = false
                }
            }
        }
    }

    private fun updatePath(index: Int) {
        val path = glyphs[index].path ?: return
        if (path.steps == 0) {
            glyphs[index].coordinate = path.end
            completePath(path.kind, index)
            glyphs[index].path = null
            return
        }
        path.step++
        val raw = path.step.toDouble() / path.steps.toDouble()
        val eased = path.easing?.value(raw) ?: raw
        val control = path.control
        if (control != null) {
            glyphs[index].coordinate = Geometry.coordinateOnBezier(
                from = path.start,
                controls = listOf(control),
                to = path.end,
                t = eased
            )
        } else {
            glyphs[index].coordinate = Geometry.coordinateOnLine(
                from = path.start,
                to = path.end,
                t = eased
            )
        }
        if (path.step >= path.steps) {
            completePath(path.kind, index)
            glyphs[index].path = null
        }
    }

    private fun completePath(kind: PathState.Kind, index: Int) {
        when (kind) {
            PathState.Kind.Fall, PathState.Kind.Top -> {}
            PathState.Kind.Input -> {
                glyphs[index].inputPathCompletionPending = true
            }
        }
    }

    private fun updateScene(index: Int) {
        if (glyphs[index].inputPathCompletionPending) {
            glyphs[index].inputPathCompletionPending = false
            glyphs[index].scene = Scene.Flash
            glyphs[index].sceneIndex = 0
            glyphs[index].sceneTicksRemaining = 4
        }

        glyphs[index].rendered = glyphs[index].codepoint to glyphs[index].foreground
        when (glyphs[index].scene) {
            Scene.Initial -> return
            Scene.Weaken, Scene.Flash, Scene.Strengthen -> {
                glyphs[index].sceneTicksRemaining--
                if (glyphs[index].sceneTicksRemaining != 0) return
                val colors = sceneColors(glyphs[index])
                if (glyphs[index].sceneIndex + 1 < colors.size) {
                    glyphs[index].sceneIndex++
                    glyphs[index].sceneTicksRemaining = 4
                } else {
                    if (glyphs[index].scene == Scene.Weaken) {
                        glyphs[index].layer = 1
                        glyphs[index].scene = Scene.Dust
                        glyphs[index].sceneIndex = 0
                        glyphs[index].sceneTicksRemaining = 1
                        glyphs[index].path = makePath(
                            kind = PathState.Kind.Fall,
                            start = glyphs[index].coordinate,
                            end = Coordinate(column = glyphs[index].inputCoordinate.column, row = 1),
                            control = null,
                            easing = Easing.OutBounce,
                            speed = 0.65
                        )
                        glyphs[index].rendered = glyphs[index].codepoint to glyphs[index].foreground
                    } else if (glyphs[index].scene == Scene.Flash) {
                        glyphs[index].scene = Scene.Strengthen
                        glyphs[index].sceneIndex = 0
                        glyphs[index].sceneTicksRemaining = 4
                        glyphs[index].rendered = glyphs[index].codepoint to glyphs[index].foreground
                    }
                }
            }
            Scene.Dust -> {
                val path = glyphs[index].path
                if (path != null && path.kind == PathState.Kind.Fall) {
                    val total = Geometry.lineLength(from = path.start, to = path.end)
                    val fraction = if (path.steps == 0) 1.0 else path.step.toDouble() / path.steps.toDouble()
                    val reached = (path.easing?.value(fraction) ?: fraction) * total
                    val progress = maxOf(maxOf(total, 1.0) - maxOf(total - reached, 1.0), 1.0) / maxOf(total, 1.0)
                    glyphs[index].sceneIndex = minOf(4, maxOf(0, PyCompat.roundHalfEven(progress * 4.0)))
                } else if (glyphs[index].path == null) {
                    glyphs[index].sceneIndex = glyphs[index].dustSymbols.size - 1
                }
                glyphs[index].rendered = glyphs[index].codepoint to glyphs[index].foreground
            }
        }
    }

    private fun sceneIsActive(glyph: Glyph): Boolean {
        return when (glyph.scene) {
            Scene.Initial, Scene.Dust -> glyph.path != null
            Scene.Weaken, Scene.Flash -> true
            Scene.Strengthen -> glyph.sceneTicksRemaining > 0 || glyph.sceneIndex + 1 < glyph.strengthenColors.size
        }
    }

    private fun sceneColors(glyph: Glyph): List<UInt> {
        return when (glyph.scene) {
            Scene.Weaken -> glyph.weakenColors
            Scene.Flash -> glyph.flashColors
            Scene.Strengthen -> glyph.strengthenColors
            else -> listOf(glyph.foreground)
        }
    }

    private fun topToBottomOrder(): MutableList<Int> {
        return glyphs.indices.sortedWith { i1, i2 ->
            val lhs = glyphs[i1].inputCoordinate
            val rhs = glyphs[i2].inputCoordinate
            if (lhs.row != rhs.row) rhs.row.compareTo(lhs.row) else lhs.column.compareTo(rhs.column)
        }.toMutableList()
    }

    private fun render(frame: Frame) {
        for (i in frame.cells.indices) {
            frame.cells[i] = Cell.BLANK
        }
        data class Winner(val layer: Int, val id: Int, val glyph: Glyph)
        val winners = HashMap<Int, Winner>()
        for (glyph in glyphs) {
            if (!glyph.visible) continue
            val coord = glyph.coordinate
            if (coord.column !in 1..canvas.columns || coord.row !in 1..canvas.rows) continue
            val cellIndex = (canvas.rows - coord.row) * canvas.columns + coord.column - 1
            val winner = winners[cellIndex]
            if (winner != null && winner.layer > glyph.layer) continue
            winners[cellIndex] = Winner(glyph.layer, glyph.characterID, glyph)
        }
        for ((cellIndex, winner) in winners) {
            val rendered = winner.glyph.rendered
            val symbol = rendered?.first ?: winner.glyph.codepoint
            val color = rendered?.second ?: winner.glyph.foreground
            frame.cells[cellIndex] = Cell(codepoint = symbol, foreground = color, background = 0u)
        }
    }

    private fun centered(size: Int): Int {
        var center = maxOf(PyCompat.floorDivide(size, 2), 1)
        if (size % 2 != 0 && size > 1) center++
        return center
    }

    private fun placeholderGlyph(): Glyph {
        return Glyph(
            characterID = 0,
            inputCoordinate = Coordinate(column = 1, row = 1),
            symbol = 32,
            weakColor = 0u,
            dustColor = 0u,
            weakenColors = listOf(0u),
            flashColors = listOf(0u),
            strengthenColors = listOf(0u),
            dustSymbols = listOf(32),
            coordinate = Coordinate(column = 1, row = 1)
        )
    }
}
