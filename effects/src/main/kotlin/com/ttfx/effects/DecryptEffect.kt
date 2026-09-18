package com.ttfx.effects

import com.ttfx.core.*

class DecryptEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    private val input: InputText,
    seed: ULong,
    private val options: Configuration = Configuration()
) : Effect {

    data class Configuration(
        val typingSpeed: Int = 2,
        val ciphertextColors: List<Color> = listOf(Color("008000"), Color("00cb00"), Color("00ff00")),
        val finalGradientStops: List<Color> = listOf(Color("eda000")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientDirection: GradientDirection = GradientDirection.Vertical
    ) {
        init {
            require(typingSpeed > 0) { "typing speed must be positive" }
            require(ciphertextColors.isNotEmpty()) { "ciphertext colors must not be empty" }
            require(finalGradientStops.isNotEmpty()) { "final gradient stops must not be empty" }
        }
    }

    private enum class Phase { Typing, Decrypting, Done }
    private enum class SceneKind { Typing, Fast, Slow, Discovered }

    private data class Visual(val codepoint: Int, val foreground: UInt, val duration: Int)

    private data class Glyph(
        val id: Int,
        val coordinate: Coordinate,
        val inputSymbol: Int,
        val typingFrames: List<Visual>,
        var fastFrames: List<Visual>,
        var slowFrames: List<Visual>,
        var discoveredFrames: List<Visual>,
        var visible: Boolean = false,
        var active: Boolean = false,
        var scene: SceneKind = SceneKind.Typing,
        var frameIndex: Int = 0,
        var ticksElapsed: Int = 0,
        var rendered: Visual? = null
    ) {
        val frames: List<Visual>
            get() = when (scene) {
                SceneKind.Typing -> typingFrames
                SceneKind.Fast -> fastFrames
                SceneKind.Slow -> slowFrames
                SceneKind.Discovered -> discoveredFrames
            }
    }

    private var rng: Xoshiro256PlusPlus = configuration.makeRNG(seed)
    private var glyphs = ArrayList<Glyph>()
    private var typingPending = ArrayList<Int>()
    private var decryptingPending = ArrayList<Int>()
    private var phase = Phase.Typing
    private var tickIndex = 0
    private var isBuilt = false
    private var isComplete = false

    override fun tick(frame: Frame): TickStatus {
        if (isComplete) return TickStatus.Complete
        try {
            if (!isBuilt) build()
            if (glyphs.isEmpty()) {
                isComplete = true
                return TickStatus.Complete
            }

            when (phase) {
                Phase.Typing -> {
                    if (typingPending.isNotEmpty() || glyphs.any { it.active }) {
                        if (typingPending.isNotEmpty() && rng.integer(0..100) <= 75) {
                            var count = 0
                            while (count < options.typingSpeed && typingPending.isNotEmpty()) {
                                val idx = typingPending.removeAt(0)
                                glyphs[idx].visible = true
                                activate(idx, SceneKind.Typing)
                                count++
                            }
                        }
                        advanceActiveScenes()
                        render(frame)
                        return TickStatus.Running
                    }
                    for (idx in decryptingPending) {
                        activate(idx, SceneKind.Fast)
                    }
                    phase = Phase.Decrypting
                    if (glyphs.any { it.active }) {
                        advanceActiveScenes()
                        render(frame)
                        if (glyphs.none { it.active }) {
                            isComplete = true
                            return TickStatus.Complete
                        }
                        return TickStatus.Running
                    }
                    isComplete = true
                    return TickStatus.Complete
                }

                Phase.Decrypting -> {
                    if (glyphs.any { it.active }) {
                        advanceActiveScenes()
                        render(frame)
                        if (glyphs.none { it.active }) {
                            isComplete = true
                            return TickStatus.Complete
                        }
                        return TickStatus.Running
                    }
                    isComplete = true
                    return TickStatus.Complete
                }

                Phase.Done -> {
                    isComplete = true
                    return TickStatus.Complete
                }
            }
        } finally {
            tickIndex++
        }
    }

    private fun build() {
        isBuilt = true
        val encryptedSymbols = ENCRYPTED_SYMBOLS
        val finalColors = finalColorMapping()
        val builtGlyphs = ArrayList<Glyph>(input.scalars.size)

        for (id in input.scalars.indices) {
            val symbol = input.scalars[id]
            val pos = input.positions[id]
            val coordinate = Coordinate(pos.column, pos.row)
            val typingFrames = ArrayList<Visual>(5)
            for (block in listOf("▉", "▓", "▒", "░")) {
                typingFrames.add(
                    Visual(
                        codepoint = block.codePointAt(0),
                        foreground = choice(options.ciphertextColors).asUInt,
                        duration = 2
                    )
                )
            }
            typingFrames.add(
                Visual(
                    codepoint = choice(encryptedSymbols),
                    foreground = choice(options.ciphertextColors).asUInt,
                    duration = 1
                )
            )

            builtGlyphs.add(
                Glyph(
                    id = id,
                    coordinate = coordinate,
                    inputSymbol = symbol,
                    typingFrames = typingFrames,
                    fastFrames = emptyList(),
                    slowFrames = emptyList(),
                    discoveredFrames = emptyList()
                )
            )
        }

        for (index in builtGlyphs.indices) {
            val decryptColor = choice(options.ciphertextColors).asUInt
            builtGlyphs[index].fastFrames = (0 until 80).map {
                Visual(choice(encryptedSymbols), decryptColor, 2)
            }
            val slowCount = rng.integer(1..15)
            builtGlyphs[index].slowFrames = (0 until slowCount).map {
                val sym = choice(encryptedSymbols)
                val dur = if (rng.integer(0..100) <= 30) rng.integer(35..59) else rng.integer(3..5)
                Visual(sym, decryptColor, dur)
            }
            val finalFg = finalColors[builtGlyphs[index].coordinate] ?: options.finalGradientStops[0].asUInt
            val finalColor = Color(
                ((finalFg shr 16) and 0xFFu).toInt(),
                ((finalFg shr 8) and 0xFFu).toInt(),
                (finalFg and 0xFFu).toInt()
            )
            builtGlyphs[index].discoveredFrames = Gradient(listOf(Color("ffffff"), finalColor), listOf(10)).spectrum.map {
                Visual(builtGlyphs[index].inputSymbol, it.asUInt, 5)
            }
        }
        glyphs = builtGlyphs
        typingPending = ArrayList(glyphs.indices.toList())
        decryptingPending = ArrayList(glyphs.indices.toList())
    }

    private fun finalColorMapping(): Map<Coordinate, UInt> {
        if (input.positions.isEmpty()) return emptyMap()
        val coordinates = input.positions.map { Coordinate(it.column, it.row) }
        val gradient = Gradient(stops = options.finalGradientStops, steps = options.finalGradientSteps)
        val mapping = gradient.coordinateColorMapping(
            minRow = coordinates.minOf { it.row },
            maxRow = coordinates.maxOf { it.row },
            minColumn = coordinates.minOf { it.column },
            maxColumn = coordinates.maxOf { it.column },
            direction = options.finalGradientDirection
        )
        return mapping.entries.associate { it.coordinate to it.color.asUInt }
    }

    private fun activate(index: Int, scene: SceneKind) {
        val g = glyphs[index]
        g.active = true
        g.scene = scene
        g.frameIndex = 0
        g.ticksElapsed = 0
        g.rendered = g.frames.firstOrNull()
    }

    private fun advanceActiveScenes() {
        for (index in glyphs.indices) {
            val g = glyphs[index]
            if (!g.active) continue
            if (g.ticksElapsed < 0) {
                g.ticksElapsed = 0
                continue
            }
            val frames = g.frames
            if (frames.isEmpty()) {
                g.active = false
                continue
            }
            val visual = frames[g.frameIndex]
            g.rendered = visual
            g.ticksElapsed++
            if (g.ticksElapsed < visual.duration) continue
            g.ticksElapsed = 0
            if (g.frameIndex + 1 < frames.size) {
                g.frameIndex++
            } else {
                when (g.scene) {
                    SceneKind.Typing -> g.active = false
                    SceneKind.Fast -> activate(index, SceneKind.Slow)
                    SceneKind.Slow -> activate(index, SceneKind.Discovered)
                    SceneKind.Discovered -> g.active = false
                }
            }
        }
    }

    private fun render(frame: Frame) {
        for (i in frame.cells.indices) {
            frame.cells[i] = Cell.BLANK
        }
        val winners = HashMap<Int, Pair<Int, Visual>>()
        for (glyph in glyphs) {
            if (!glyph.visible) continue
            val visual = glyph.rendered ?: continue
            if (glyph.coordinate.column !in 1..canvas.columns || glyph.coordinate.row !in 1..canvas.rows) continue
            val cellIndex = (canvas.rows - glyph.coordinate.row) * canvas.columns + glyph.coordinate.column - 1
            val winner = winners[cellIndex]
            if (winner != null && winner.first > glyph.id) continue
            winners[cellIndex] = glyph.id to visual
        }
        for ((cellIndex, pair) in winners) {
            val visual = pair.second
            frame.cells[cellIndex] = Cell(codepoint = visual.codepoint, foreground = visual.foreground, background = 0u)
        }
    }

    private fun <T> choice(list: List<T>): T {
        return list[rng.integer(0 until list.size)]
    }

    companion object {
        private val ENCRYPTED_SYMBOLS: List<Int> by lazy {
            val symbols = ArrayList<Int>()
            for (i in 33 until 127) symbols.add(i)
            for (i in 9608 until 9632) symbols.add(i)
            for (i in 9472 until 9599) symbols.add(i)
            for (i in 174 until 452) symbols.add(i)
            symbols
        }
    }
}
