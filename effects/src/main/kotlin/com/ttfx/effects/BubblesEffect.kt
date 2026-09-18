package com.ttfx.effects

import com.ttfx.core.*

class BubblesEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    enum class PopCondition {
        Row, Bottom, Anywhere
    }

    data class Configuration(
        val rainbow: Boolean = false,
        val bubbleColors: List<Color> = listOf(
            Color("d33aff"),
            Color("7395c4"),
            Color("43c2a7"),
            Color("02ff7f")
        ),
        val popColor: Color = Color("ffffff"),
        val bubbleSpeed: Double = 0.5,
        val bubbleDelay: Int = 20,
        val popCondition: PopCondition = PopCondition.Row,
        val movementEasing: Easing = Easing.InOutSine,
        val finalGradientStops: List<Color> = listOf(Color("d33aff"), Color("02ff7f")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientDirection: GradientDirection = GradientDirection.Diagonal
    ) {
        init {
            require(bubbleColors.isNotEmpty()) { "bubble colors must not be empty" }
            require(bubbleSpeed > 0.0) { "bubble speed must be positive" }
            require(bubbleDelay >= 0) { "bubble delay must not be negative" }
        }
    }

    private sealed interface ScenePhase {
        data object Sheen : ScenePhase
        data object Pop1 : ScenePhase
        data object Pop2 : ScenePhase
        data class Final(val index: Int) : ScenePhase
        data object Done : ScenePhase
    }

    private data class ScenePlay(
        var phase: ScenePhase,
        var ticksElapsed: Int = 0,
        var looping: Boolean = false,
        var sheenIndex: Int = 0
    )

    private enum class PathKind {
        Anchor, PopOut, Final
    }

    private data class MotionPath(
        val start: Coordinate,
        val target: Coordinate,
        val speed: Double,
        val easing: Easing? = null,
        var step: Int = 0,
        val maxSteps: Int = 0,
        val distance: Double = 0.0,
        val kind: PathKind = PathKind.Anchor,
        var active: Boolean = false
    )

    private data class Glyph(
        val characterID: Int,
        val inputCoordinate: Coordinate,
        val inputSymbol: Int,
        var coordinate: Coordinate,
        var layer: Int = 1,
        var visible: Boolean = false,
        var sheenColors: List<UInt> = emptyList(),
        var sheenDuration: Int = 1,
        var finalColors: List<UInt> = emptyList(),
        var scene: ScenePlay = ScenePlay(phase = ScenePhase.Sheen),
        var path: MotionPath? = null,
        var symbol: Int,
        var foreground: UInt
    )

    private data class Bubble(
        val characterIndices: List<Int>,
        val radius: Int,
        var anchor: Coordinate,
        var anchorPath: MotionPath,
        val lowestRow: Int,
        var landed: Boolean = false
    )

    companion object {
        private const val POP_DURATION = 9
        private const val FINAL_FRAME_DURATION = 6
        private const val FINAL_GRADIENT_STEPS = 8
        private val RAINBOW_STOPS: List<Color> = listOf(
            Color("e81416"),
            Color("ffa500"),
            Color("faeb36"),
            Color("79c314"),
            Color("487de7"),
            Color("4b369d"),
            Color("70369d")
        )
    }

    private var rng: Xoshiro256PlusPlus = configuration.makeRNG(seed)
    private var glyphs: MutableList<Glyph> = ArrayList()
    private var pendingBubbles: MutableList<Bubble> = ArrayList()
    private var animatingBubbles: MutableList<Bubble> = ArrayList()
    private var active: MutableList<Int> = ArrayList()
    private var stepsSinceLastBubble = 0
    private var isComplete = false
    private val rainbowSpectrum: List<Color> = Gradient(RAINBOW_STOPS, 5).spectrum
    private val popColorRGB: UInt = options.popColor.asUInt

    init {
        build(input)
    }

    override fun tick(frame: Frame): TickStatus {
        if (isComplete) return TickStatus.Complete
        if (!hasPendingWork) {
            isComplete = true
            return TickStatus.Complete
        }

        if (pendingBubbles.isNotEmpty() && stepsSinceLastBubble >= options.bubbleDelay) {
            val bubble = pendingBubbles.removeAt(0)
            for (index in bubble.characterIndices) {
                glyphs[index].visible = true
            }
            animatingBubbles.add(bubble)
            stepsSinceLastBubble = 0
        }
        stepsSinceLastBubble++

        val stillFloating = ArrayList<Bubble>(animatingBubbles.size)
        for (bubble in animatingBubbles) {
            if (bubble.landed) {
                pop(bubble)
                active.addAll(bubble.characterIndices)
            } else {
                stillFloating.add(bubble)
            }
        }
        animatingBubbles = stillFloating

        for (index in animatingBubbles.indices) {
            moveBubble(index)
        }

        active.sortBy { glyphs[it].characterID }
        for (glyphIndex in active) {
            stepPoppedGlyph(glyphIndex)
        }
        active = active.filter { glyph ->
            val item = glyphs[glyph]
            val pathActive = item.path?.active == true
            pathActive || !sceneIsComplete(item.scene)
        }.toMutableList()

        render(frame)

        if (!hasPendingWork) {
            isComplete = true
            return TickStatus.Complete
        }
        return TickStatus.Running
    }

    private fun build(input: InputText) {
        data class Created(val characterID: Int, val symbol: Int, val coordinate: Coordinate)
        val created = ArrayList<Created>(input.scalars.size)
        for (index in input.scalars.indices) {
            val scalar = input.scalars[index]
            val pos = input.positions[index]
            if (scalar != 32) {
                created.add(Created(index, scalar, Coordinate(pos.column, pos.row)))
            }
        }
        if (created.isEmpty()) {
            isComplete = true
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
        val finalColors = mapping.entries.associate { it.coordinate to it.color.asUInt }

        glyphs = created.map { source ->
            val mapped = color(finalColors[source.coordinate]!!)
            val fade = Gradient(listOf(options.popColor, mapped), FINAL_GRADIENT_STEPS).spectrum.map { it.asUInt }
            Glyph(
                characterID = source.characterID,
                inputCoordinate = source.coordinate,
                inputSymbol = source.symbol,
                coordinate = source.coordinate,
                finalColors = fade,
                symbol = source.symbol,
                foreground = 0u
            )
        }.toMutableList()

        var leftover = created.indices.sortedWith { i1, i2 ->
            val lhs = created[i1].coordinate
            val rhs = created[i2].coordinate
            if (lhs.row != rhs.row) lhs.row.compareTo(rhs.row) else lhs.column.compareTo(rhs.column)
        }.toMutableList()

        while (leftover.isNotEmpty()) {
            val group: List<Int>
            if (leftover.size < 5) {
                group = leftover.toList()
                leftover.clear()
            } else {
                val count = rng.integer(5..minOf(leftover.size, 20))
                group = leftover.take(count)
                leftover = leftover.drop(count).toMutableList()
            }
            val origin = Coordinate(
                column = rng.integer(1..canvas.columns),
                row = canvas.rows + 10
            )
            pendingBubbles.add(makeBubble(origin, group))
        }
    }

    private fun makeBubble(origin: Coordinate, characterIndices: List<Int>): Bubble {
        val radius = maxOf(characterIndices.size / 5, 1)
        val lowestRow: Int = when (options.popCondition) {
            PopCondition.Row -> characterIndices.minOf { glyphs[it].inputCoordinate.row }
            PopCondition.Bottom, PopCondition.Anywhere -> 1
        }
        val bubble = Bubble(
            characterIndices = characterIndices,
            radius = radius,
            anchor = origin,
            anchorPath = MotionPath(start = origin, target = origin, speed = options.bubbleSpeed),
            lowestRow = lowestRow
        )
        placeBubbleCharacters(bubble, unique = false)
        bubble.landed = false
        val waypointColumn = rng.integer(1..canvas.columns)
        bubble.anchorPath = activatedPath(
            origin = origin,
            target = Coordinate(column = waypointColumn, row = lowestRow),
            speed = options.bubbleSpeed,
            easing = null,
            kind = PathKind.Anchor
        )
        if (options.rainbow) {
            var spectrum = ArrayList(rainbowSpectrum)
            var gradientOffset = 0
            for (index in characterIndices) {
                glyphs[index].sheenColors = spectrum.map { it.asUInt }
                glyphs[index].sheenDuration = 4
                glyphs[index].scene = ScenePlay(phase = ScenePhase.Sheen, looping = true)
                glyphs[index].symbol = glyphs[index].inputSymbol
                glyphs[index].foreground = glyphs[index].sheenColors[0]
                gradientOffset = (gradientOffset + 2) % spectrum.size
                spectrum = ArrayList(spectrum.subList(gradientOffset, spectrum.size) + spectrum.subList(0, gradientOffset))
            }
        } else {
            val bubbleColor = options.bubbleColors[rng.integer(0 until options.bubbleColors.size)]
            val colorRGB = bubbleColor.asUInt
            for (index in characterIndices) {
                glyphs[index].sheenColors = listOf(colorRGB)
                glyphs[index].sheenDuration = 1
                glyphs[index].scene = ScenePlay(phase = ScenePhase.Sheen)
                glyphs[index].symbol = glyphs[index].inputSymbol
                glyphs[index].foreground = colorRGB
            }
        }
        return bubble
    }

    private fun placeBubbleCharacters(bubble: Bubble, unique: Boolean) {
        val points = Geometry.coordinatesOnCircle(
            origin = bubble.anchor,
            radius = bubble.radius,
            limit = bubble.characterIndices.size,
            unique = unique
        )
        for ((offset, glyphIndex) in bubble.characterIndices.withIndex()) {
            if (offset >= points.size) break
            glyphs[glyphIndex].coordinate = points[offset]
            if (points[offset].row == bubble.lowestRow) {
                bubble.landed = true
            }
        }
        if (options.popCondition == PopCondition.Anywhere && rng.random() < 0.002) {
            bubble.landed = true
        }
    }

    private fun pop(bubble: Bubble) {
        val points = Geometry.coordinatesOnCircle(
            origin = bubble.anchor,
            radius = bubble.radius + 3,
            limit = bubble.characterIndices.size,
            unique = true
        )
        for ((offset, glyphIndex) in bubble.characterIndices.withIndex()) {
            if (offset >= points.size) break
            glyphs[glyphIndex].path = activatedPath(
                origin = glyphs[glyphIndex].coordinate,
                target = points[offset],
                speed = 0.3,
                easing = Easing.OutExpo,
                kind = PathKind.PopOut
            )
        }
        for (glyphIndex in bubble.characterIndices) {
            activateScene(ScenePhase.Pop1, glyphIndex)
        }
    }

    private fun moveBubble(index: Int) {
        val path = animatingBubbles[index].anchorPath
        stepPath(path)
        animatingBubbles[index].anchor = currentPathCoordinate(path)
        val bubble = animatingBubbles[index]
        placeBubbleCharacters(bubble, unique = false)
        for (glyphIndex in bubble.characterIndices) {
            stepSheen(glyphIndex)
        }
    }

    private fun stepPoppedGlyph(index: Int) {
        if (glyphs[index].path != null) {
            stepGlyphPath(index)
        }
        stepPoppedScene(index)
    }

    private fun stepGlyphPath(index: Int) {
        val path = glyphs[index].path ?: return
        if (!path.active) return
        val completed = stepPath(path)
        glyphs[index].coordinate = currentPathCoordinate(path)
        if (completed) {
            when (path.kind) {
                PathKind.PopOut -> {
                    glyphs[index].path = activatedPath(
                        origin = glyphs[index].coordinate,
                        target = glyphs[index].inputCoordinate,
                        speed = 0.3,
                        easing = Easing.InOutExpo,
                        kind = PathKind.Final
                    )
                }
                PathKind.Final -> {
                    glyphs[index].layer = 0
                    path.active = false
                    glyphs[index].path = path
                }
                PathKind.Anchor -> {
                    glyphs[index].path = path
                }
            }
        }
    }

    private fun stepPath(path: MotionPath): Boolean {
        if (!path.active) return false
        if (path.maxSteps == 0 || path.distance == 0.0) {
            path.active = false
            return true
        }
        if (path.step >= path.maxSteps) {
            path.active = false
            return true
        }
        path.step++
        if (path.step == path.maxSteps) {
            path.active = false
            return true
        }
        return false
    }

    private fun currentPathCoordinate(path: MotionPath): Coordinate {
        if (path.maxSteps == 0 || path.distance == 0.0 || path.step >= path.maxSteps) {
            return path.target
        }
        val ratio = path.step.toDouble() / path.maxSteps.toDouble()
        val distanceFactor = path.easing?.value(ratio) ?: ratio
        val traveled = distanceFactor * path.distance
        val t = if (path.easing != null) {
            traveled / path.distance
        } else {
            minOf(1.0, traveled / path.distance)
        }
        return Geometry.coordinateOnLine(from = path.start, to = path.target, t = t)
    }

    private fun activatedPath(
        origin: Coordinate,
        target: Coordinate,
        speed: Double,
        easing: Easing?,
        kind: PathKind
    ): MotionPath {
        val distance = Geometry.lineLength(origin, target)
        return MotionPath(
            start = origin,
            target = target,
            speed = speed,
            easing = easing,
            step = 0,
            maxSteps = PyCompat.roundHalfEven(distance / speed),
            distance = distance,
            kind = kind,
            active = true
        )
    }

    private fun activateScene(phase: ScenePhase, index: Int) {
        glyphs[index].scene = ScenePlay(phase = phase)
        when (phase) {
            is ScenePhase.Sheen -> {
                glyphs[index].symbol = glyphs[index].inputSymbol
                glyphs[index].foreground = glyphs[index].sheenColors.firstOrNull() ?: 0u
            }
            is ScenePhase.Pop1 -> {
                glyphs[index].symbol = 42
                glyphs[index].foreground = popColorRGB
            }
            is ScenePhase.Pop2 -> {
                glyphs[index].symbol = 39
                glyphs[index].foreground = popColorRGB
            }
            is ScenePhase.Final -> {
                glyphs[index].symbol = glyphs[index].inputSymbol
                glyphs[index].foreground = glyphs[index].finalColors[minOf(phase.index, glyphs[index].finalColors.size - 1)]
            }
            is ScenePhase.Done -> {
                glyphs[index].symbol = glyphs[index].inputSymbol
                glyphs[index].foreground = glyphs[index].finalColors.lastOrNull() ?: popColorRGB
            }
        }
    }

    private fun stepSheen(index: Int) {
        val scene = glyphs[index].scene
        if (scene.phase !is ScenePhase.Sheen) return
        val duration = glyphs[index].sheenDuration
        val colors = glyphs[index].sheenColors
        if (colors.isEmpty()) return

        glyphs[index].symbol = glyphs[index].inputSymbol
        glyphs[index].foreground = colors[scene.sheenIndex]
        scene.ticksElapsed++
        if (scene.ticksElapsed == duration) {
            scene.ticksElapsed = 0
            val next = scene.sheenIndex + 1
            if (next < colors.size) {
                scene.sheenIndex = next
            } else if (scene.looping) {
                scene.sheenIndex = 0
            } else {
                scene.phase = ScenePhase.Done
            }
        }
    }

    private fun stepPoppedScene(index: Int) {
        val scene = glyphs[index].scene
        when (val p = scene.phase) {
            is ScenePhase.Sheen -> stepSheen(index)
            is ScenePhase.Pop1 -> {
                glyphs[index].symbol = 42
                glyphs[index].foreground = popColorRGB
                scene.ticksElapsed++
                if (scene.ticksElapsed == POP_DURATION) {
                    activateScene(ScenePhase.Pop2, index)
                }
            }
            is ScenePhase.Pop2 -> {
                glyphs[index].symbol = 39
                glyphs[index].foreground = popColorRGB
                scene.ticksElapsed++
                if (scene.ticksElapsed == POP_DURATION) {
                    activateScene(ScenePhase.Final(index = 0), index)
                }
            }
            is ScenePhase.Final -> {
                glyphs[index].symbol = glyphs[index].inputSymbol
                glyphs[index].foreground = glyphs[index].finalColors[minOf(p.index, glyphs[index].finalColors.size - 1)]
                scene.ticksElapsed++
                if (scene.ticksElapsed == FINAL_FRAME_DURATION) {
                    scene.ticksElapsed = 0
                    if (p.index + 1 < glyphs[index].finalColors.size) {
                        scene.phase = ScenePhase.Final(index = p.index + 1)
                    } else {
                        scene.phase = ScenePhase.Done
                    }
                }
            }
            is ScenePhase.Done -> {}
        }
    }

    private fun sceneIsComplete(scene: ScenePlay): Boolean {
        return scene.phase is ScenePhase.Done
    }

    private fun render(frame: Frame) {
        for (i in frame.cells.indices) {
            frame.cells[i] = Cell.BLANK
        }
        data class Winner(val layer: Int, val characterID: Int, val symbol: Int, val foreground: UInt)
        val winners = HashMap<Int, Winner>()
        for (glyph in glyphs) {
            if (!glyph.visible) continue
            val coord = glyph.coordinate
            if (coord.column !in 1..canvas.columns || coord.row !in 1..canvas.rows) continue
            val cellIndex = (canvas.rows - coord.row) * canvas.columns + coord.column - 1
            val winner = winners[cellIndex]
            if (winner != null && (winner.layer > glyph.layer || (winner.layer == glyph.layer && winner.characterID > glyph.characterID))) {
                continue
            }
            winners[cellIndex] = Winner(glyph.layer, glyph.characterID, glyph.symbol, glyph.foreground)
        }
        for ((cellIndex, winner) in winners) {
            frame.cells[cellIndex] = Cell(codepoint = winner.symbol, foreground = winner.foreground, background = 0u)
        }
    }

    private val hasPendingWork: Boolean
        get() = pendingBubbles.isNotEmpty() || animatingBubbles.isNotEmpty() || active.isNotEmpty()

    private fun color(word: UInt): Color {
        val r = ((word shr 16) and 0xFFu).toInt()
        val g = ((word shr 8) and 0xFFu).toInt()
        val b = (word and 0xFFu).toInt()
        return Color(r, g, b)
    }
}
