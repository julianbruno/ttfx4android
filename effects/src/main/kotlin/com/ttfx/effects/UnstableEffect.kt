package com.ttfx.effects

import com.ttfx.core.*

class UnstableEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    data class Configuration(
        val unstableColor: Color = Color("ff9200"),
        val explosionEasing: Easing = Easing.OutExpo,
        val explosionSpeed: Double = 1.0,
        val reassemblyEasing: Easing = Easing.OutExpo,
        val reassemblySpeed: Double = 1.0,
        val finalGradientStops: List<Color> = listOf(Color("8A008A"), Color("00D1FF"), Color("FFFFFF")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientDirection: GradientDirection = GradientDirection.Vertical
    ) {
        init {
            require(explosionSpeed > 0.0) { "explosion speed must be positive" }
            require(reassemblySpeed > 0.0) { "reassembly speed must be positive" }
        }
    }

    private enum class Phase {
        Rumble, Explosion, Reassembly, Complete
    }

    private data class Glyph(
        val characterID: Int,
        val symbol: Int,
        val inputCoordinate: Coordinate,
        val jumbledCoordinate: Coordinate,
        val explosionTarget: Coordinate,
        val finalColor: UInt,
        val rumbleColors: List<UInt>,
        val finalColors: List<UInt>,
        var coordinate: Coordinate,
        var sceneIndex: Int = 0,
        var sceneTicks: Int = 0,
        var path: PathState? = null,
        var activePath: Boolean = false
    )

    private data class PathState(
        val start: Coordinate,
        val end: Coordinate,
        val easing: Easing,
        val steps: Int,
        var step: Int = 0
    )

    private var rng: Xoshiro256PlusPlus
    private var glyphs: MutableList<Glyph> = ArrayList()
    private var phase: Phase = Phase.Rumble
    private var currentRumbleSteps = 0
    private var maxRumbleSteps = 150
    private var rumbleModDelay = 18
    private var explosionHoldTime = 30
    private var active: MutableList<Int> = ArrayList()

    init {
        this.rng = configuration.makeRNG(seed)
        build(input)
    }

    override fun tick(frame: Frame): TickStatus {
        if (phase == Phase.Complete) return TickStatus.Complete
        if (glyphs.isEmpty()) {
            phase = Phase.Complete
            return TickStatus.Complete
        }

        var emitted = false
        if (phase == Phase.Rumble) {
            if (currentRumbleSteps < maxRumbleSteps) {
                if (currentRumbleSteps > 30 && currentRumbleSteps % rumbleModDelay == 0) {
                    val rowOffset = listOf(-1, 0, 1)[rng.integer(0 until 3)]
                    val columnOffset = listOf(-1, 0, 1)[rng.integer(0 until 3)]
                    for (index in glyphs.indices) {
                        glyphs[index].coordinate = Coordinate(
                            column = glyphs[index].coordinate.column + columnOffset,
                            row = glyphs[index].coordinate.row + rowOffset
                        )
                    }
                    render(frame)
                    for (index in glyphs.indices) {
                        glyphs[index].coordinate = glyphs[index].jumbledCoordinate
                        advanceRumbleScene(index)
                    }
                    rumbleModDelay = maxOf(1, rumbleModDelay - 1)
                } else {
                    render(frame)
                    for (index in glyphs.indices) {
                        advanceRumbleScene(index)
                    }
                }
                currentRumbleSteps++
                emitted = true
            } else {
                phase = Phase.Explosion
                active = glyphs.indices.toMutableList()
                for (index in active) {
                    activateExplosion(index)
                }
            }
        }

        if (!emitted && phase == Phase.Explosion) {
            if (active.isNotEmpty()) {
                for (index in active) {
                    updatePath(index)
                }
                active.removeAll { glyphs[it].coordinate == glyphs[it].explosionTarget }
                render(frame)
                emitted = true
            } else if (explosionHoldTime != 0) {
                explosionHoldTime--
                render(frame)
                emitted = true
            } else {
                phase = Phase.Reassembly
                active = glyphs.indices.toMutableList()
                for (index in active) {
                    activateReassembly(index)
                }
            }
        }

        if (!emitted && phase == Phase.Reassembly && active.isNotEmpty()) {
            for (index in active) {
                updatePath(index)
            }
            render(frame)
            for (index in active) {
                advanceFinalScene(index)
            }
            active = active.filter {
                !(glyphs[it].coordinate == glyphs[it].inputCoordinate && finalSceneComplete(it))
            }.toMutableList()
            if (active.isEmpty()) {
                phase = Phase.Complete
            }
            emitted = true
        }

        if (!emitted) {
            phase = Phase.Complete
            return TickStatus.Complete
        }
        return if (phase == Phase.Complete) TickStatus.Complete else TickStatus.Running
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
            phase = Phase.Complete
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
        ).entries.associate { it.coordinate to it.color }

        val remainingCoordinates = created.map { it.coordinate }.toMutableList()
        val ordered = created.sortedWith { a, b ->
            if (a.coordinate.row != b.coordinate.row) b.coordinate.row.compareTo(a.coordinate.row)
            else if (a.coordinate.column != b.coordinate.column) a.coordinate.column.compareTo(b.coordinate.column)
            else a.characterID.compareTo(b.characterID)
        }

        glyphs = ArrayList(created.size)
        for (source in ordered) {
            val edge = rng.integer(0..3)
            val target: Coordinate = when (edge) {
                0 -> Coordinate(column = 1, row = randomRow())
                1 -> Coordinate(column = canvas.columns, row = randomRow())
                2 -> Coordinate(column = randomColumn(), row = 1)
                else -> Coordinate(column = randomColumn(), row = canvas.rows)
            }
            val jumbledIndex = rng.integer(0 until remainingCoordinates.size)
            val jumbled = remainingCoordinates.removeAt(jumbledIndex)
            val final = mapping[source.coordinate] ?: finalGradient.spectrum.last()
            val rumble = Gradient(listOf(final, options.unstableColor), 12).spectrum.map { it.asUInt }
            val finalScene = Gradient(listOf(options.unstableColor, final), 12).spectrum.map { it.asUInt }.toMutableList()
            finalScene.add(finalScene.last())
            glyphs.add(
                Glyph(
                    characterID = source.characterID,
                    symbol = source.symbol,
                    inputCoordinate = source.coordinate,
                    jumbledCoordinate = jumbled,
                    explosionTarget = target,
                    finalColor = final.asUInt,
                    rumbleColors = rumble,
                    finalColors = finalScene,
                    coordinate = jumbled
                )
            )
        }
        glyphs.sortBy { it.characterID }
    }

    private fun activateExplosion(index: Int) {
        glyphs[index].path = makePath(
            start = glyphs[index].coordinate,
            end = glyphs[index].explosionTarget,
            easing = options.explosionEasing,
            speed = options.explosionSpeed
        )
        glyphs[index].activePath = true
    }

    private fun activateReassembly(index: Int) {
        glyphs[index].sceneIndex = 0
        glyphs[index].sceneTicks = 0
        glyphs[index].path = makePath(
            start = glyphs[index].coordinate,
            end = glyphs[index].inputCoordinate,
            easing = options.reassemblyEasing,
            speed = options.reassemblySpeed
        )
        glyphs[index].activePath = true
    }

    private fun makePath(start: Coordinate, end: Coordinate, easing: Easing, speed: Double): PathState {
        val distance = Geometry.lineLength(start, end)
        return PathState(start = start, end = end, easing = easing, steps = PyCompat.roundHalfEven(distance / speed))
    }

    private fun updatePath(index: Int) {
        val path = glyphs[index].path ?: return
        if (path.steps == 0) {
            glyphs[index].coordinate = path.end
            glyphs[index].path = null
            glyphs[index].activePath = false
            return
        }
        path.step++
        val t = path.easing.value(path.step.toDouble() / path.steps.toDouble())
        glyphs[index].coordinate = Geometry.coordinateOnLine(from = path.start, to = path.end, t = t)
        if (path.step >= path.steps) {
            glyphs[index].path = null
            glyphs[index].activePath = false
        }
    }

    private fun advanceRumbleScene(index: Int) {
        advanceScene(index, frameDuration = 10, colorsCount = glyphs[index].rumbleColors.size)
    }

    private fun advanceFinalScene(index: Int) {
        advanceScene(index, frameDuration = 3, colorsCount = glyphs[index].finalColors.size)
    }

    private fun advanceScene(index: Int, frameDuration: Int, colorsCount: Int) {
        glyphs[index].sceneTicks++
        if (glyphs[index].sceneTicks < frameDuration) return
        glyphs[index].sceneTicks = 0
        if (glyphs[index].sceneIndex + 1 < colorsCount) {
            glyphs[index].sceneIndex++
        }
    }

    private fun finalSceneComplete(index: Int): Boolean {
        return glyphs[index].sceneIndex + 1 == glyphs[index].finalColors.size && glyphs[index].sceneTicks == 0
    }

    private fun randomRow(): Int = rng.integer(1..canvas.rows)
    private fun randomColumn(): Int = rng.integer(1..canvas.columns)

    private fun render(frame: Frame) {
        for (i in frame.cells.indices) {
            frame.cells[i] = Cell.BLANK
        }
        val winners = HashMap<Int, Glyph>()
        for (glyph in glyphs.sortedBy { it.characterID }) {
            if (glyph.coordinate.column !in 1..canvas.columns || glyph.coordinate.row !in 1..canvas.rows) continue
            val cellIndex = (canvas.rows - glyph.coordinate.row) * canvas.columns + glyph.coordinate.column - 1
            winners[cellIndex] = glyph
        }
        for ((cellIndex, glyph) in winners) {
            val color = when (phase) {
                Phase.Rumble, Phase.Explosion -> glyph.rumbleColors[minOf(glyph.sceneIndex, glyph.rumbleColors.size - 1)]
                Phase.Reassembly, Phase.Complete -> glyph.finalColors[minOf(glyph.sceneIndex, glyph.finalColors.size - 1)]
            }
            frame.cells[cellIndex] = Cell(codepoint = glyph.symbol, foreground = color, background = 0u)
        }
    }
}
