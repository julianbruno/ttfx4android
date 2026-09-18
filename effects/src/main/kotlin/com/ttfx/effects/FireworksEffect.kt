package com.ttfx.effects

import com.ttfx.core.*

class FireworksEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    data class Configuration(
        val explodeAnywhere: Boolean = false,
        val fireworkColors: List<Color> = listOf(
            Color("88F7E2"),
            Color("44D492"),
            Color("F5EB67"),
            Color("FFA15C"),
            Color("FA233E")
        ),
        val fireworkSymbol: String = "o",
        val fireworkVolume: Double = 0.05,
        val launchDelay: Int = 45,
        val explodeDistance: Double = 0.2,
        val finalGradientStops: List<Color> = listOf(Color("8A008A"), Color("00D1FF"), Color("FFFFFF")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientDirection: GradientDirection = GradientDirection.Horizontal
    ) {
        init {
            require(fireworkColors.isNotEmpty()) { "firework colors must not be empty" }
            require(fireworkVolume >= 0.0) { "firework volume must not be negative" }
            require(launchDelay >= 0) { "launch delay must not be negative" }
            require(explodeDistance >= 0.0) { "explode distance must not be negative" }
        }
    }

    private enum class PathKind {
        Apex, Explode, Input, Finished
    }

    private enum class SceneKind {
        Launch, Bloom, Fall, Done
    }

    private data class Segment(
        val start: Coordinate,
        val end: Coordinate,
        val controls: List<Coordinate>,
        val distance: Double
    )

    private data class MotionPath(
        val speed: Double,
        val easing: Easing?,
        val holdTime: Int,
        val segments: MutableList<Segment>,
        var totalDistance: Double,
        var currentStep: Int = 0,
        var maxSteps: Int = 0,
        var holdRemaining: Int = holdTime,
        var lastDistance: Double = 0.0,
        var firstWaypoint: Coordinate? = null,
        var firstWaypointControls: List<Coordinate> = emptyList(),
        var originSegmentMarker: Double? = null
    ) {
        fun activate(origin: Coordinate) {
            val firstWp = firstWaypoint ?: return
            val originDistance = if (firstWaypointControls.isEmpty()) {
                Geometry.lineLength(origin, firstWp)
            } else {
                Geometry.bezierLength(origin, firstWaypointControls, firstWp)
            }
            val originSegment = Segment(
                start = origin,
                end = firstWp,
                controls = firstWaypointControls,
                distance = originDistance
            )
            val existing = originSegmentMarker
            if (existing != null && segments.isNotEmpty()) {
                totalDistance -= existing
                segments[0] = originSegment
            } else {
                segments.add(0, originSegment)
            }
            originSegmentMarker = originDistance
            totalDistance += originDistance
            currentStep = 0
            holdRemaining = holdTime
            lastDistance = 0.0
            maxSteps = PyCompat.roundHalfEven(totalDistance / speed)
        }

        fun move(): Pair<Coordinate, Boolean> {
            if (segments.isEmpty()) return Coordinate(1, 1) to true
            val last = segments.last()
            if (maxSteps == 0 || currentStep >= maxSteps || totalDistance == 0.0) {
                return finishHold(last.end)
            }
            currentStep++
            val ratio = currentStep.toDouble() / maxSteps.toDouble()
            var distanceToTravel = (easing?.value(ratio) ?: ratio) * totalDistance
            lastDistance = distanceToTravel
            var activeIndex: Int? = null
            for (index in segments.indices) {
                if (distanceToTravel <= segments[index].distance) {
                    activeIndex = index
                    break
                }
                distanceToTravel -= segments[index].distance
            }
            val index = activeIndex ?: run {
                distanceToTravel += segments[segments.size - 1].distance
                segments.size - 1
            }
            val segment = segments[index]
            val t = if (segment.distance == 0.0) {
                0.0
            } else if (easing != null) {
                distanceToTravel / segment.distance
            } else {
                minOf(distanceToTravel / segment.distance, 1.0)
            }
            val coordinate = if (segment.controls.isEmpty()) {
                Geometry.coordinateOnLine(segment.start, segment.end, t)
            } else {
                Geometry.coordinateOnBezier(segment.start, segment.controls, segment.end, t)
            }
            if (currentStep == maxSteps) {
                return finishHold(coordinate)
            }
            return coordinate to false
        }

        private fun finishHold(coordinate: Coordinate): Pair<Coordinate, Boolean> {
            if (holdTime != 0 && holdRemaining == holdTime) {
                holdRemaining--
                return coordinate to false
            }
            if (holdRemaining != 0) {
                holdRemaining--
                return coordinate to false
            }
            return coordinate to true
        }
    }

    private data class Glyph(
        val characterID: Int,
        val inputCoordinate: Coordinate,
        val inputSymbol: Int,
        val launchSymbol: Int,
        var shellColor: UInt,
        val white: UInt,
        var bloomColors: List<UInt>,
        var fallColors: List<UInt>,
        var apex: MotionPath,
        var explode: MotionPath,
        var inputPath: MotionPath,
        var coordinate: Coordinate,
        var activePath: PathKind = PathKind.Apex,
        var scene: SceneKind = SceneKind.Launch,
        var launchIndex: Int = 0,
        var launchTicks: Int = 0,
        var fallIndex: Int = 0,
        var fallTicks: Int = 0,
        var visible: Boolean = false,
        var layer: Int = 0
    ) {
        val isActive: Boolean
            get() = activePath != PathKind.Finished || scene != SceneKind.Done

        val visual: Pair<Int, UInt>
            get() = when (scene) {
                SceneKind.Launch -> if (launchIndex == 0) launchSymbol to shellColor else launchSymbol to white
                SceneKind.Bloom -> {
                    val index = minOf(maxOf(bloomColors.size - 1, 0), bloomColors.size - 1)
                    inputSymbol to bloomColors[index]
                }
                SceneKind.Fall -> inputSymbol to fallColors[minOf(fallIndex, fallColors.size - 1)]
                SceneKind.Done -> inputSymbol to fallColors.last()
            }
    }

    private var rng: Xoshiro256PlusPlus
    private var glyphs: MutableList<Glyph> = ArrayList()
    private var shells: MutableList<MutableList<Int>> = ArrayList()
    private var active: MutableList<Int> = ArrayList()
    private var launchDelayRemaining = 0
    private var isComplete = false

    init {
        this.rng = configuration.makeRNG(seed)
        build(input)
    }

    override fun tick(frame: Frame): TickStatus {
        if (isComplete) return TickStatus.Complete
        if (shells.isEmpty() && active.isEmpty()) {
            isComplete = true
            return TickStatus.Complete
        }

        if (shells.isNotEmpty() && launchDelayRemaining <= 0) {
            val group = shells.removeLast()
            for (index in group) {
                glyphs[index].visible = true
                active.add(index)
            }
            launchDelayRemaining = (options.launchDelay.toDouble() * rng.uniform(0.5, 1.5)).toInt()
        }
        launchDelayRemaining--

        active.sort()
        for (index in active) {
            move(index)
        }
        render(frame)
        for (index in active) {
            stepScene(index)
        }
        active.removeAll { !glyphs[it].isActive }

        if (shells.isEmpty() && active.isEmpty()) {
            isComplete = true
            return TickStatus.Complete
        }
        return TickStatus.Running
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

        val volume = maxOf(1, PyCompat.roundHalfEven(options.fireworkVolume * input.scalars.size.toDouble()))
        val explodeDistance = minOf(15, maxOf(1, PyCompat.roundHalfEven(canvas.columns.toDouble() * options.explodeDistance)))
        val launchSymbol = options.fireworkSymbol.codePoints().findFirst().asInt
        val white = Color("FFFFFF").asUInt

        val buildOrder = created.indices.sortedWith { i1, i2 ->
            val lhs = created[i1].coordinate
            val rhs = created[i2].coordinate
            if (lhs.row != rhs.row) rhs.row.compareTo(lhs.row) else lhs.column.compareTo(rhs.column)
        }

        glyphs = ArrayList(List(created.size) { placeholderGlyph() })
        var currentShell = ArrayList<Int>()
        var originX = 0
        var origin = Coordinate(0, 0)
        var explodeCoords: List<Coordinate> = emptyList()

        for (sourceIndex in buildOrder) {
            val source = created[sourceIndex]
            if (currentShell.size == volume || currentShell.isEmpty()) {
                originX = rng.integer(0 until canvas.columns)
                shells.add(currentShell)
                currentShell = ArrayList()
                val minRow = if (options.explodeAnywhere) 1 else source.coordinate.row
                val originY = rng.integer(minRow until (canvas.rows + 1))
                origin = Coordinate(column = originX, row = originY)
                explodeCoords = Geometry.coordinatesInEllipse(center = origin, diameter = explodeDistance)
            }

            val start = Coordinate(column = originX, row = 1)
            val apex = makePath(speed = 0.35, easing = Easing.OutExpo, hold = 0, from = start, to = origin, controls = emptyList())
            apex.activate(start)

            val explodeSpeed = rng.uniform(0.2, 0.4)
            val explodePoint = explodeCoords[rng.integer(0 until explodeCoords.size)]
            val bloomControl = Geometry.extrapolateAlongRay(
                origin = origin,
                target = explodePoint,
                offsetFromTarget = PyCompat.floorDivide(explodeDistance, 2).toDouble()
            )
            val bloomPoint = Coordinate(column = bloomControl.column, row = maxOf(1, bloomControl.row - 7))
            val explode = makePath(
                speed = explodeSpeed,
                easing = Easing.OutCirc,
                hold = 0,
                waypoints = listOf(explodePoint, bloomPoint),
                controls = listOf(emptyList(), listOf(bloomControl))
            )
            val inputPath = makePath(
                speed = 0.6,
                easing = Easing.InOutQuart,
                hold = 0,
                from = bloomPoint,
                to = source.coordinate,
                controls = listOf(Coordinate(column = bloomPoint.column, row = 1))
            )

            glyphs[sourceIndex] = Glyph(
                characterID = source.characterID,
                inputCoordinate = source.coordinate,
                inputSymbol = source.symbol,
                launchSymbol = launchSymbol,
                shellColor = 0u,
                white = white,
                bloomColors = listOf(white),
                fallColors = listOf(finalColors[source.coordinate]!!),
                apex = apex,
                explode = explode,
                inputPath = inputPath,
                coordinate = start,
                layer = 2
            )
            currentShell.add(sourceIndex)
        }
        if (currentShell.isNotEmpty()) {
            shells.add(currentShell)
        }

        val whiteColor = Color("FFFFFF")
        for (shell in shells) {
            val shellColor = options.fireworkColors[rng.integer(0 until options.fireworkColors.size)]
            val bloomSpectrum = Gradient(listOf(shellColor, whiteColor, shellColor), 5).spectrum.map { it.asUInt }
            for (index in shell) {
                val finalColor = color(glyphs[index].fallColors[0])
                val fallSpectrum = Gradient(listOf(shellColor, finalColor), 15).spectrum.map { it.asUInt }
                glyphs[index].shellColor = shellColor.asUInt
                glyphs[index].bloomColors = bloomSpectrum
                glyphs[index].fallColors = if (fallSpectrum.isEmpty()) listOf(finalColor.asUInt) else fallSpectrum
            }
        }
    }

    private fun move(index: Int) {
        when (glyphs[index].activePath) {
            PathKind.Apex -> {
                val result = glyphs[index].apex.move()
                glyphs[index].coordinate = result.first
                if (result.second) {
                    glyphs[index].explode.activate(result.first)
                    glyphs[index].activePath = PathKind.Explode
                    glyphs[index].scene = SceneKind.Bloom
                }
            }
            PathKind.Explode -> {
                val result = glyphs[index].explode.move()
                glyphs[index].coordinate = result.first
                if (result.second) {
                    glyphs[index].inputPath.activate(result.first)
                    glyphs[index].activePath = PathKind.Input
                    glyphs[index].scene = SceneKind.Fall
                    glyphs[index].fallIndex = 0
                    glyphs[index].fallTicks = 0
                }
            }
            PathKind.Input -> {
                val result = glyphs[index].inputPath.move()
                glyphs[index].coordinate = result.first
                if (result.second) {
                    glyphs[index].activePath = PathKind.Finished
                    glyphs[index].layer = 0
                }
            }
            PathKind.Finished -> {}
        }
    }

    private fun stepScene(index: Int) {
        when (glyphs[index].scene) {
            SceneKind.Launch -> {
                glyphs[index].launchTicks++
                val duration = if (glyphs[index].launchIndex == 0) 2 else 1
                if (glyphs[index].launchTicks == duration) {
                    glyphs[index].launchTicks = 0
                    glyphs[index].launchIndex = if (glyphs[index].launchIndex == 0) 1 else 0
                }
            }
            SceneKind.Bloom -> {
                val path = if (glyphs[index].activePath == PathKind.Explode) glyphs[index].explode else glyphs[index].apex
                val progress = maxOf(path.currentStep, 1).toDouble() / maxOf(path.maxSteps, 1).toDouble()
                val last = maxOf(glyphs[index].bloomColors.size - 1, 0)
                val frameIndex = minOf(maxOf(PyCompat.roundHalfEven(last.toDouble() * progress), 0), last)
                glyphs[index].bloomColors = rotateBloom(glyphs[index].bloomColors, frameIndex)
            }
            SceneKind.Fall -> {
                glyphs[index].fallTicks++
                if (glyphs[index].fallTicks == 10) {
                    glyphs[index].fallTicks = 0
                    if (glyphs[index].fallIndex + 1 < glyphs[index].fallColors.size) {
                        glyphs[index].fallIndex++
                    } else {
                        glyphs[index].scene = SceneKind.Done
                    }
                }
            }
            SceneKind.Done -> {}
        }
    }

    private fun rotateBloom(colors: List<UInt>, showing: Int): List<UInt> {
        return colors
    }

    private fun bloomVisual(glyph: Glyph): UInt {
        val path = if (glyph.activePath == PathKind.Explode) glyph.explode else glyph.apex
        val progress = maxOf(path.currentStep, 1).toDouble() / maxOf(path.maxSteps, 1).toDouble()
        val last = maxOf(glyph.bloomColors.size - 1, 0)
        val frameIndex = minOf(maxOf(PyCompat.roundHalfEven(last.toDouble() * progress), 0), last)
        return glyph.bloomColors[frameIndex]
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
            val visual = if (glyph.scene == SceneKind.Bloom) {
                glyph.inputSymbol to bloomVisual(glyph)
            } else {
                glyph.visual
            }
            val winner = winners[cellIndex]
            if (winner != null && (winner.layer > glyph.layer || (winner.layer == glyph.layer && winner.characterID > glyph.characterID))) {
                continue
            }
            winners[cellIndex] = Winner(glyph.layer, glyph.characterID, visual.first, visual.second)
        }
        for ((cellIndex, winner) in winners) {
            frame.cells[cellIndex] = Cell(codepoint = winner.symbol, foreground = winner.foreground, background = 0u)
        }
    }

    private fun makePath(
        speed: Double,
        easing: Easing,
        hold: Int,
        from: Coordinate,
        to: Coordinate,
        controls: List<Coordinate>
    ): MotionPath {
        val path = makePath(speed = speed, easing = easing, hold = hold, waypoints = listOf(to), controls = listOf(controls))
        path.activate(from)
        return path
    }

    private fun makePath(
        speed: Double,
        easing: Easing,
        hold: Int,
        waypoints: List<Coordinate>,
        controls: List<List<Coordinate>>
    ): MotionPath {
        val segments = ArrayList<Segment>()
        var total = 0.0
        if (waypoints.size >= 2) {
            for (index in 1 until waypoints.size) {
                val control = if (index < controls.size) controls[index] else emptyList()
                val start = waypoints[index - 1]
                val end = waypoints[index]
                val distance = if (control.isEmpty()) {
                    Geometry.lineLength(start, end)
                } else {
                    Geometry.bezierLength(start, control, end)
                }
                segments.add(Segment(start, end, control, distance))
                total += distance
            }
        }
        return MotionPath(
            speed = speed,
            easing = easing,
            holdTime = hold,
            segments = segments,
            totalDistance = total,
            holdRemaining = hold,
            firstWaypoint = waypoints.firstOrNull(),
            firstWaypointControls = controls.firstOrNull() ?: emptyList()
        )
    }

    private fun placeholderGlyph(): Glyph {
        val point = Coordinate(1, 1)
        val empty = MotionPath(speed = 1.0, easing = null, holdTime = 0, segments = ArrayList(), totalDistance = 0.0, holdRemaining = 0, firstWaypoint = null)
        return Glyph(
            characterID = 0,
            inputCoordinate = point,
            inputSymbol = 32,
            launchSymbol = 32,
            shellColor = 0u,
            white = 0u,
            bloomColors = listOf(0u),
            fallColors = listOf(0u),
            apex = empty,
            explode = empty,
            inputPath = empty,
            coordinate = point,
            activePath = PathKind.Finished,
            scene = SceneKind.Done
        )
    }

    private fun color(word: UInt): Color {
        val r = ((word shr 16) and 0xFFu).toInt()
        val g = ((word shr 8) and 0xFFu).toInt()
        val b = (word and 0xFFu).toInt()
        return Color(r, g, b)
    }
}
