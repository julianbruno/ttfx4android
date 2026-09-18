package com.ttfx.effects

import com.ttfx.core.*

class SwarmEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    data class Configuration(
        val baseColors: List<Color> = listOf(Color("31a0d4")),
        val flashColor: Color = Color("f2ea79"),
        val swarmSize: Double = 0.1,
        val swarmCoordination: Double = 0.80,
        val swarmAreaCountRange: IntRange = 2..4,
        val finalGradientStops: List<Color> = listOf(Color("31b900"), Color("f0ff65")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientDirection: GradientDirection = GradientDirection.Horizontal
    ) {
        init {
            require(baseColors.isNotEmpty()) { "base colors must not be empty" }
            require(swarmSize >= 0.0) { "swarm size must not be negative" }
            require(swarmCoordination >= 0.0) { "swarm coordination must not be negative" }
            require(swarmAreaCountRange.first > 0) { "swarm area count must be positive" }
        }
    }

    private data class Segment(
        val start: Coordinate,
        val end: Coordinate,
        val distance: Double
    )

    private class Path(
        val id: String,
        val speed: Double,
        val easing: Easing?,
        val waypoints: List<Coordinate>
    ) {
        val segments = ArrayList<Segment>()
        var originDistance: Double? = null
        var totalDistance: Double = 0.0
        var currentStep: Int = 0
        var maxSteps: Int = 0
        var lastDistance: Double = 0.0

        fun activate(origin: Coordinate) {
            val first = waypoints.firstOrNull() ?: return
            val originSegment = Segment(start = origin, end = first, distance = Geometry.lineLength(origin, first))
            val prev = originDistance
            if (prev != null && segments.isNotEmpty()) {
                totalDistance -= prev
                segments[0] = originSegment
            } else {
                segments.add(0, originSegment)
            }
            originDistance = originSegment.distance
            totalDistance += originSegment.distance
            currentStep = 0
            lastDistance = 0.0
            maxSteps = PyCompat.roundHalfEven(totalDistance / speed)
        }

        data class StepResult(val coordinate: Coordinate, val complete: Boolean)

        fun step(): StepResult {
            val last = segments.lastOrNull()
                ?: return StepResult(waypoints.lastOrNull() ?: Coordinate(1, 1), true)
            if (maxSteps == 0 || currentStep >= maxSteps || totalDistance == 0.0) {
                return StepResult(last.end, currentStep >= maxSteps)
            }
            currentStep += 1
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
            } else if (easing == null) {
                minOf(distanceToTravel / segment.distance, 1.0)
            } else {
                distanceToTravel / segment.distance
            }
            return StepResult(
                Geometry.coordinateOnLine(from = segment.start, to = segment.end, t = t),
                currentStep == maxSteps
            )
        }
    }

    private enum class SceneKind { Flash, Input, Done }

    private class Glyph(
        val characterID: Int,
        val inputCoordinate: Coordinate,
        val inputSymbol: Int,
        var coordinate: Coordinate,
        var paths: List<Path> = emptyList(),
        var activePathIndex: Int? = null,
        var scene: SceneKind = SceneKind.Done,
        var layer: Int = 0,
        var visible: Boolean = false,
        var finalColors: List<UInt> = emptyList(),
        var inputSceneIndex: Int = 0,
        var inputSceneTicks: Int = 0,
        var flashColors: List<UInt> = emptyList(),
        var foreground: UInt = 0u
    ) {
        val activePathID: String?
            get() = activePathIndex?.let { paths[it].id }

        val isActive: Boolean
            get() = activePathIndex != null || scene != SceneKind.Done
    }

    private var rng: Xoshiro256PlusPlus = configuration.makeRNG(seed)
    private var glyphs = ArrayList<Glyph>()
    private var swarms = ArrayList<MutableList<Int>>()
    private var currentSwarm: MutableList<Int> = ArrayList()
    private var active = HashSet<Int>()
    private var callNext = true
    private var activeSwarmArea = "0_swarm_area"
    private var isComplete = false

    init {
        build(input)
    }

    override fun tick(frame: Frame): TickStatus {
        if (isComplete) return TickStatus.Complete
        if (swarms.isEmpty() && active.isEmpty()) {
            isComplete = true
            return TickStatus.Complete
        }
        if (swarms.isNotEmpty() && callNext) {
            callNext = false
            currentSwarm = swarms.removeAt(swarms.size - 1)
            activeSwarmArea = "0_swarm_area"
            for (index in currentSwarm) {
                activatePath("0_swarm_area", index)
                glyphs[index].visible = true
                active.add(index)
            }
        }
        if (active.size < currentSwarm.size) {
            callNext = true
        }
        if (currentSwarm.isNotEmpty()) {
            for (index in currentSwarm) {
                val pathID = glyphs[index].activePathID
                if (pathID != null && pathID != activeSwarmArea && pathID.contains("swarm_area") && firstDigit(pathID) > firstDigit(activeSwarmArea)) {
                    activeSwarmArea = pathID
                    for (other in currentSwarm) {
                        if (other != index && rng.random() < options.swarmCoordination) {
                            activatePath(activeSwarmArea, other)
                        }
                    }
                    break
                }
            }
        }
        for (index in active.sortedBy { glyphs[it].characterID }) {
            stepGlyph(index)
        }
        active.retainAll { glyphs[it].isActive }
        render(frame)
        if (swarms.isEmpty() && active.isEmpty()) {
            isComplete = true
            return TickStatus.Complete
        }
        return TickStatus.Running
    }

    private data class Source(val characterID: Int, val symbol: Int, val coordinate: Coordinate)

    private fun build(input: InputText) {
        val created = ArrayList<Source>()
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
        val finalColorsByCoord = mapping.entries.associate { it.coordinate to it.color.asUInt }
        val swarmSize = maxOf(PyCompat.roundHalfEven(created.size.toDouble() * options.swarmSize), 1)
        makeSwarms(created, swarmSize)

        glyphs = ArrayList(created.map { source ->
            val finalColor = finalColorsByCoord[source.coordinate]!!
            val finalColorObj = Color(
                red = ((finalColor shr 16) and 0xFFu).toInt(),
                green = ((finalColor shr 8) and 0xFFu).toInt(),
                blue = (finalColor and 0xFFu).toInt()
            )
            val finalGradientList = Gradient(stops = listOf(options.flashColor, finalColorObj), steps = 10).spectrum.map { it.asUInt }
            Glyph(
                characterID = source.characterID,
                inputCoordinate = source.coordinate,
                inputSymbol = source.symbol,
                coordinate = source.coordinate,
                paths = emptyList(),
                finalColors = finalGradientList,
                flashColors = emptyList()
            )
        })

        val circleCache = HashMap<Coordinate, List<Coordinate>>()
        for (swarm in swarms) {
            val base = options.baseColors[rng.integer(0 until options.baseColors.size)]
            val swarmGradient = Gradient(stops = listOf(base, options.flashColor), steps = 7)
            val flashColors = (swarmGradient.spectrum + List(10) { options.flashColor } + swarmGradient.spectrum.reversed()).map { it.asUInt }
            val spawn = randomCoord(outside = true)
            val swarmAreaCoordinateMap = ArrayList<Pair<Coordinate, List<Coordinate>>>()
            val swarmAreas = ArrayList<Coordinate>()
            val swarmAreaCount = rng.integer(options.swarmAreaCountRange)
            var lastFocus = spawn
            val radius = maxOf(PyCompat.floorDivide(minOf(canvas.columns, canvas.rows), 2), 1)

            while (swarmAreas.size < swarmAreaCount) {
                val cached = circleCache[lastFocus]?.toMutableList()
                    ?: Geometry.coordinatesOnCircle(origin = lastFocus, radius = radius, limit = 0, unique = true).toMutableList()
                rng.shuffle(cached)
                circleCache[lastFocus] = cached
                val nextFocus = cached.firstOrNull { coordIsInCanvas(it) } ?: randomCoord(outside = false)
                swarmAreas.add(nextFocus)
                val areaCoords = Geometry.coordinatesInEllipse(center = lastFocus, diameter = maxOf(PyCompat.floorDivide(minOf(canvas.columns, canvas.rows), 6), 1) * 2)
                val existing = swarmAreaCoordinateMap.indexOfFirst { it.first == lastFocus }
                if (existing != -1) {
                    swarmAreaCoordinateMap[existing] = lastFocus to areaCoords
                } else {
                    swarmAreaCoordinateMap.add(lastFocus to areaCoords)
                }
                lastFocus = nextFocus
            }

            for (index in swarm) {
                glyphs[index].coordinate = spawn
                glyphs[index].flashColors = flashColors
                val paths = ArrayList<Path>()
                for ((areaIndex, entry) in swarmAreaCoordinateMap.withIndex()) {
                    val areaName = "${areaIndex}_swarm_area"
                    val origin = entry.second[rng.integer(0 until entry.second.size)]
                    paths.add(makePath(id = areaName, speed = 0.4, easing = Easing.OutSine, waypoints = listOf(origin)))
                    for (w in 0 until 2) {
                        val next = entry.second[rng.integer(0 until entry.second.size)]
                        val pathID = "${paths.size}"
                        paths.add(makePath(id = pathID, speed = 0.18, easing = Easing.InOutSine, waypoints = listOf(next)))
                    }
                }
                paths.add(makePath(id = "${paths.size}", speed = 0.45, easing = Easing.InOutQuad, waypoints = listOf(glyphs[index].inputCoordinate)))
                glyphs[index].paths = paths
            }
        }
    }

    private fun makeSwarms(created: List<Source>, swarmSize: Int) {
        val unswarmed = created.indices.sortedWith { lhs, rhs ->
            val l = created[lhs].coordinate
            val r = created[rhs].coordinate
            if (l.row != r.row) l.row.compareTo(r.row)
            else if (l.column != r.column) r.column.compareTo(l.column)
            else created[rhs].characterID.compareTo(created[lhs].characterID)
        }.toMutableList()

        while (unswarmed.isNotEmpty()) {
            val next = ArrayList<Int>()
            for (i in 0 until swarmSize) {
                if (unswarmed.isEmpty()) break
                next.add(unswarmed.removeAt(unswarmed.size - 1))
            }
            swarms.add(next)
        }
        val finalSwarm = swarms.removeAt(swarms.size - 1)
        if (finalSwarm.size < PyCompat.floorDivide(swarmSize, 2) && swarms.isNotEmpty()) {
            swarms[swarms.size - 1].addAll(finalSwarm)
        } else {
            swarms.add(finalSwarm)
        }
    }

    private fun makePath(id: String, speed: Double, easing: Easing, waypoints: List<Coordinate>): Path {
        val path = Path(id = id, speed = speed, easing = easing, waypoints = waypoints)
        if (waypoints.size > 1) {
            for (i in 0 until waypoints.size - 1) {
                val p0 = waypoints[i]
                val p1 = waypoints[i + 1]
                val distance = Geometry.lineLength(p0, p1)
                path.segments.add(Segment(start = p0, end = p1, distance = distance))
                path.totalDistance += distance
            }
            path.maxSteps = PyCompat.roundHalfEven(path.totalDistance / speed)
        }
        return path
    }

    private fun activatePath(id: String, index: Int) {
        val pathIndex = glyphs[index].paths.indexOfFirst { it.id == id }
        if (pathIndex == -1) return
        glyphs[index].paths[pathIndex].activate(glyphs[index].coordinate)
        glyphs[index].activePathIndex = pathIndex
        val isLastPath = id == glyphs[index].paths.lastOrNull()?.id
        glyphs[index].layer = if (isLastPath) glyphs[index].layer else 1
        glyphs[index].inputSceneIndex = 0
        glyphs[index].inputSceneTicks = 0
        if ((id.contains("swarm_area") || isLastPath) && glyphs[index].flashColors.isNotEmpty()) {
            glyphs[index].scene = SceneKind.Flash
            glyphs[index].foreground = glyphs[index].flashColors[0]
        }
    }

    private fun stepGlyph(index: Int) {
        val activePath = glyphs[index].activePathIndex
        if (activePath != null) {
            val result = glyphs[index].paths[activePath].step()
            glyphs[index].coordinate = result.coordinate
            if (result.complete) {
                val completedID = glyphs[index].paths[activePath].id
                glyphs[index].activePathIndex = null
                if (completedID.contains("swarm_area")) {
                    glyphs[index].scene = SceneKind.Done
                }
                val next = glyphs[index].paths.indices.firstOrNull { it > activePath }
                if (next != null) {
                    activatePath(glyphs[index].paths[next].id, index)
                } else {
                    glyphs[index].scene = SceneKind.Input
                    glyphs[index].layer = 0
                }
            }
        }
        if (glyphs[index].scene == SceneKind.Flash && glyphs[index].activePathIndex != null) {
            val path = glyphs[index].paths[glyphs[index].activePathIndex!!]
            val total = maxOf(path.totalDistance, 1.0)
            val remaining = maxOf(path.totalDistance - path.lastDistance, 1.0)
            val reached = maxOf(total - remaining, 1.0)
            val frameIndex = minOf(
                PyCompat.roundHalfEven((reached / total) * (glyphs[index].flashColors.size - 1).toDouble()),
                glyphs[index].flashColors.size - 1
            )
            glyphs[index].foreground = glyphs[index].flashColors[frameIndex]
        }
        if (glyphs[index].scene == SceneKind.Input) {
            val colorIndex = minOf(glyphs[index].inputSceneIndex, glyphs[index].finalColors.size - 1)
            glyphs[index].foreground = glyphs[index].finalColors[colorIndex]
            glyphs[index].inputSceneTicks += 1
            if (glyphs[index].inputSceneTicks == 3) {
                glyphs[index].inputSceneTicks = 0
                glyphs[index].inputSceneIndex += 1
                if (glyphs[index].inputSceneIndex >= glyphs[index].finalColors.size) {
                    glyphs[index].scene = SceneKind.Done
                }
            }
        }
    }

    private fun render(frame: Frame) {
        for (index in glyphs.indices.sortedBy { glyphs[it].characterID }) {
            if (!glyphs[index].visible) continue
            val glyph = glyphs[index]
            val col = glyph.coordinate.column
            val row = glyph.coordinate.row
            if (col in 1..canvas.columns && row in 1..canvas.rows) {
                val existing = frame[col, row]
                if (existing.codepoint == 32 || glyph.layer >= 1) {
                    frame[col, row] = Cell(codepoint = glyph.inputSymbol, foreground = glyph.foreground, background = 0u)
                }
            }
        }
    }

    private fun randomCoord(outside: Boolean): Coordinate {
        if (outside) {
            val above = Coordinate(column = rng.integer(1..canvas.columns), row = canvas.rows + 1)
            val below = Coordinate(column = rng.integer(1..canvas.columns), row = 0)
            val left = Coordinate(column = 0, row = rng.integer(1..canvas.rows))
            val right = Coordinate(column = canvas.columns + 1, row = rng.integer(1..canvas.rows))
            val candidates = listOf(above, below, left, right)
            return candidates[rng.integer(0..3)]
        }
        return Coordinate(column = rng.integer(1..canvas.columns), row = rng.integer(1..canvas.rows))
    }

    private fun coordIsInCanvas(coord: Coordinate): Boolean {
        return coord.column in 1..canvas.columns && coord.row in 1..canvas.rows
    }

    private fun firstDigit(value: String): Int = value.first().digitToInt()
}
