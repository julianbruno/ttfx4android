package com.ttfx.effects

import com.ttfx.core.*

class BeamsEffect(
    configuration: EffectConfiguration,
    private val canvas: Canvas,
    input: InputText,
    seed: ULong,
    val options: Configuration = Configuration()
) : Effect {

    data class Configuration(
        val beamRowSymbols: List<String> = listOf("▂", "▁", "_"),
        val beamColumnSymbols: List<String> = listOf("▌", "▍", "▎", "▏"),
        val beamDelay: Int = 6,
        val beamRowSpeedRange: IntRange = 15..60,
        val beamColumnSpeedRange: IntRange = 9..15,
        val beamGradientStops: List<Color> = listOf(Color("ffffff"), Color("00D1FF"), Color("8A008A")),
        val beamGradientSteps: List<Int> = listOf(2, 6),
        val beamGradientFrames: Int = 2,
        val finalGradientStops: List<Color> = listOf(Color("8A008A"), Color("00D1FF"), Color("ffffff")),
        val finalGradientSteps: List<Int> = listOf(12),
        val finalGradientFrames: Int = 4,
        val finalGradientDirection: GradientDirection = GradientDirection.Vertical,
        val finalWipeSpeed: Int = 3
    ) {
        init {
            require(beamDelay > 0) { "beam delay must be positive" }
            require(beamRowSpeedRange.first > 0) { "beam row speed range must be positive" }
            require(beamColumnSpeedRange.first > 0) { "beam column speed range must be positive" }
            require(beamGradientFrames > 0) { "beam gradient frames must be positive" }
            require(finalGradientFrames > 0) { "final gradient frames must be positive" }
            require(finalWipeSpeed > 0) { "final wipe speed must be positive" }
        }
    }

    private companion object {
        private const val EXPLICIT_BLACK_FOREGROUND_SENTINEL: UInt = 0xFFFF_FFFEu

        private fun coloredCell(symbol: Int, color: Color): Cell {
            val word = color.asUInt
            return Cell(
                codepoint = symbol,
                foreground = word,
                background = if (word == 0u) EXPLICIT_BLACK_FOREGROUND_SENTINEL else 0u
            )
        }

        private fun distributedIndex(index: Int, count: Int, smaller: Int): Int {
            val base = count / smaller
            val remainder = count % smaller
            var boundary = 0
            for (candidate in 0 until smaller) {
                boundary += base + (if (candidate < remainder) 1 else 0)
                if (index < boundary) return candidate
            }
            return smaller - 1
        }
    }

    private enum class GenericScene {
        BeamRow, BeamColumn, Brighten
    }

    private class GenericCharacter(
        val id: Int,
        val coordinate: Coordinate,
        val inputSymbol: Int,
        val finalColor: Color,
        var visible: Boolean = false,
        var scene: GenericScene? = null,
        var sceneIndex: Int = 0,
        var currentCell: Cell = Cell.BLANK
    )

    private class GenericGroup(
        val characters: ArrayList<Int>,
        val direction: GenericScene,
        val speed: Double,
        var nextCharacterCounter: Double = 0.0
    )

    private enum class Phase {
        Beams, FinalWipe, Complete
    }

    private val frames: List<List<Pair<Coordinate, Cell>>> = makeGenericFrames(
        canvas = canvas,
        input = input,
        initialRNG = configuration.makeRNG(seed),
        options = options
    )
    private var tickIndex = 0

    override fun tick(frame: Frame): TickStatus {
        if (tickIndex >= frames.size) return TickStatus.Complete
        for (i in frame.cells.indices) {
            frame.cells[i] = Cell.BLANK
        }
        for ((coordinate, cell) in frames[tickIndex]) {
            frame[coordinate.column, coordinate.row] = cell
        }
        tickIndex += 1
        return if (tickIndex == frames.size) TickStatus.Complete else TickStatus.Running
    }

    private fun makeGenericFrames(
        canvas: Canvas,
        input: InputText,
        initialRNG: Xoshiro256PlusPlus,
        options: Configuration
    ): List<List<Pair<Coordinate, Cell>>> {
        if (input.scalars.isEmpty()) return emptyList()

        val beamGradient = Gradient(options.beamGradientStops, options.beamGradientSteps)
        val finalGradient = Gradient(options.finalGradientStops, options.finalGradientSteps)
        val inputCoordinates = input.positions.map { Coordinate(column = it.column, row = it.row) }
        val inputByCoordinate = inputCoordinates.zip(input.scalars).toMap()

        val bottom = inputCoordinates.minOf { it.row }
        val top = inputCoordinates.maxOf { it.row }
        val left = inputCoordinates.minOf { it.column }
        val right = inputCoordinates.maxOf { it.column }

        val finalMapping = finalGradient.coordinateColorMapping(
            minRow = bottom,
            maxRow = top,
            minColumn = left,
            maxColumn = right,
            direction = options.finalGradientDirection
        ).entries.associate { it.coordinate to it.color }

        val rowSymbols = options.beamRowSymbols.map {
            if (it.isNotEmpty()) it.codePointAt(0) else Cell.BLANK.codepoint
        }
        val columnSymbols = options.beamColumnSymbols.map {
            if (it.isNotEmpty()) it.codePointAt(0) else Cell.BLANK.codepoint
        }

        fun beamCells(symbols: List<Int>): List<Cell> {
            val colors = beamGradient.spectrum
            val count = maxOf(symbols.size, colors.size)
            val result = ArrayList<Cell>(count * options.beamGradientFrames)
            for (index in 0 until count) {
                val sym = symbols[distributedIndex(index, count, symbols.size)]
                val col = colors[distributedIndex(index, count, colors.size)]
                val cell = coloredCell(sym, col)
                for (f in 0 until options.beamGradientFrames) {
                    result.add(cell)
                }
            }
            return result
        }

        fun fadeCells(symbol: Int, color: Color): List<Cell> {
            val fade = Gradient(listOf(color, color.adjustBrightness(0.3)), 10)
            val result = ArrayList<Cell>(fade.spectrum.size * 2)
            for (col in fade.spectrum) {
                val cell = coloredCell(symbol, col)
                result.add(cell)
                result.add(cell)
            }
            return result
        }

        fun brightenCells(symbol: Int, color: Color): List<Cell> {
            val faded = color.adjustBrightness(0.3)
            val brighten = Gradient(listOf(faded, color), 10)
            val result = ArrayList<Cell>(brighten.spectrum.size * options.finalGradientFrames)
            for (col in brighten.spectrum) {
                val cell = coloredCell(symbol, col)
                for (f in 0 until options.finalGradientFrames) {
                    result.add(cell)
                }
            }
            return result
        }

        fun sceneCells(character: GenericCharacter, scene: GenericScene): List<Cell> {
            return when (scene) {
                GenericScene.BeamRow -> beamCells(rowSymbols) + fadeCells(character.inputSymbol, character.finalColor)
                GenericScene.BeamColumn -> beamCells(columnSymbols) + fadeCells(character.inputSymbol, character.finalColor)
                GenericScene.Brighten -> brightenCells(character.inputSymbol, character.finalColor)
            }
        }

        val characters = ArrayList<GenericCharacter>()
        val idByCoordinate = HashMap<Coordinate, Int>()
        for (row in 1..canvas.rows) {
            for (column in 1..canvas.columns) {
                val coordinate = Coordinate(column = column, row = row)
                val symbol = inputByCoordinate[coordinate] ?: Cell.BLANK.codepoint
                val color = if (inputByCoordinate[coordinate] == null) {
                    Color("000000")
                } else {
                    finalMapping[coordinate] ?: options.finalGradientStops.last()
                }
                val id = characters.size
                characters.add(GenericCharacter(id = id, coordinate = coordinate, inputSymbol = symbol, finalColor = color))
                idByCoordinate[coordinate] = id
            }
        }

        val rng = initialRNG
        val groups = ArrayList<GenericGroup>()
        for (row in canvas.rows downTo 1) {
            val ids = ArrayList<Int>()
            for (col in 1..canvas.columns) {
                idByCoordinate[Coordinate(column = col, row = row)]?.let { ids.add(it) }
            }
            val speed = rng.integer(options.beamRowSpeedRange).toDouble() * 0.1
            if (rng.integer(0..1) == 0) ids.reverse()
            groups.add(GenericGroup(characters = ids, direction = GenericScene.BeamRow, speed = speed))
        }
        for (column in 1..canvas.columns) {
            val ids = ArrayList<Int>()
            for (row in 1..canvas.rows) {
                idByCoordinate[Coordinate(column = column, row = row)]?.let { ids.add(it) }
            }
            val speed = rng.integer(options.beamColumnSpeedRange).toDouble() * 0.1
            if (rng.integer(0..1) == 0) ids.reverse()
            groups.add(GenericGroup(characters = ids, direction = GenericScene.BeamColumn, speed = speed))
        }
        rng.shuffle(groups)

        val pendingGroups = ArrayList(groups)
        val activeGroups = ArrayList<GenericGroup>()
        val activeCharacters = LinkedHashSet<Int>()
        var delay = 0
        var phase = Phase.Beams

        val minInputColumn = inputCoordinates.minOf { it.column }
        val maxInputRow = inputCoordinates.maxOf { it.row }
        val inputCoordinateSet = inputCoordinates.toSet()
        val groupedInputIDs = characters.filter { it.coordinate in inputCoordinateSet }
            .groupBy { character -> (character.coordinate.column - minInputColumn) + (maxInputRow - character.coordinate.row) }
        val finalWipeGroups = ArrayList(groupedInputIDs.keys.sorted().map { key ->
            ArrayList(groupedInputIDs[key]!!.map { it.id })
        })

        val generatedFrames = ArrayList<List<Pair<Coordinate, Cell>>>()

        fun activate(id: Int, scene: GenericScene) {
            characters[id].visible = true
            characters[id].scene = scene
            characters[id].sceneIndex = 0
            activeCharacters.add(id)
        }

        fun updateAndRender(): List<Pair<Coordinate, Cell>> {
            val completed = ArrayList<Int>()
            for (id in activeCharacters.sorted()) {
                val scene = characters[id].scene
                if (scene == null) {
                    completed.add(id)
                    continue
                }
                val cells = sceneCells(characters[id], scene)
                val index = minOf(characters[id].sceneIndex, cells.size - 1)
                characters[id].currentCell = cells[index]
                characters[id].sceneIndex += 1
                if (characters[id].sceneIndex >= cells.size) {
                    characters[id].scene = null
                    completed.add(id)
                }
            }
            for (id in completed) {
                activeCharacters.remove(id)
            }
            return characters.filter { it.visible }.map { it.coordinate to it.currentCell }
        }

        while (true) {
            if (phase == Phase.Complete && activeCharacters.isEmpty()) break
            when (phase) {
                Phase.Beams -> {
                    if (delay == 0) {
                        if (pendingGroups.isNotEmpty()) {
                            val count = rng.integer(1..5)
                            for (i in 0 until count) {
                                if (pendingGroups.isNotEmpty()) {
                                    activeGroups.add(pendingGroups.removeAt(0))
                                }
                            }
                        }
                        delay = options.beamDelay
                    } else {
                        delay -= 1
                    }

                    for (index in activeGroups.indices) {
                        activeGroups[index].nextCharacterCounter += activeGroups[index].speed
                        val count = activeGroups[index].nextCharacterCounter.toInt()
                        if (count > 1) {
                            for (i in 0 until count) {
                                if (activeGroups[index].characters.isNotEmpty()) {
                                    val id = activeGroups[index].characters.removeAt(0)
                                    activeGroups[index].nextCharacterCounter -= 1.0
                                    activate(id, activeGroups[index].direction)
                                }
                            }
                        }
                    }
                    activeGroups.removeAll { it.characters.isEmpty() }
                    if (pendingGroups.isEmpty() && activeGroups.isEmpty() && activeCharacters.isEmpty()) {
                        phase = Phase.FinalWipe
                    }
                }
                Phase.FinalWipe -> {
                    if (finalWipeGroups.isNotEmpty()) {
                        val wipeCount = minOf(options.finalWipeSpeed, finalWipeGroups.size)
                        for (i in 0 until wipeCount) {
                            if (finalWipeGroups.isNotEmpty()) {
                                for (id in finalWipeGroups.removeAt(0)) {
                                    activate(id, GenericScene.Brighten)
                                }
                            }
                        }
                    } else {
                        phase = Phase.Complete
                    }
                }
                Phase.Complete -> {}
            }
            generatedFrames.add(updateAndRender())
        }

        return generatedFrames
    }
}
