package com.ttfx.core

data class InputPosition(val column: Int, val row: Int)

data class InputText(
    val scalars: List<Int>,
    val positions: List<InputPosition>,
    val characterIDs: List<Int>
)

class Canvas(val columns: Int, val rows: Int) {
    init {
        require(columns > 0 && rows > 0) { "invalid dimensions: columns=$columns, rows=$rows" }
    }

    fun ingest(text: String, wrap: Boolean = false, anchor: String = "sw"): InputText {
        val rawLines = text.split("\n")
        val lines: List<List<Int>> = rawLines.flatMap { line ->
            val codePoints = line.codePoints().toArray().toList()
            if (wrap && codePoints.size > columns) {
                codePoints.chunked(columns)
            } else {
                listOf(codePoints)
            }
        }

        val width = lines.maxOfOrNull { line ->
            val lastNonSpace = line.indexOfLast { it != 32 }
            if (lastNonSpace != -1) lastNonSpace + 1 else 0
        } ?: 0

        val height = lines.mapIndexedNotNull { index, line ->
            if (line.any { it != 32 }) lines.size - index else null
        }.maxOrNull() ?: 0

        val centerColumn = maxOf(1, columns / 2) + if (columns > 1 && columns % 2 != 0) 1 else 0
        val centerRow = maxOf(1, rows / 2) + if (rows > 1 && rows % 2 != 0) 1 else 0

        val dx = if (width == columns) 0 else when (anchor) {
            "s", "n", "c" -> centerColumn - PyCompat.floorDivide(width, 2)
            "se", "e", "ne" -> columns - width
            else -> 0
        }

        val dy = if (height == rows) 0 else when (anchor) {
            "w", "e", "c" -> centerRow - PyCompat.floorDivide(height, 2)
            "nw", "n", "ne" -> rows - height
            else -> 0
        }

        val scalars = ArrayList<Int>()
        val positions = ArrayList<InputPosition>()
        val characterIDs = ArrayList<Int>()
        var arenaID = 0

        for ((lineIndex, line) in lines.withIndex()) {
            val row = lines.size - lineIndex + dy
            for ((columnIndex, scalar) in line.withIndex()) {
                val currentArenaID = arenaID++
                val col = columnIndex + 1 + dx
                if (scalar != 32 && col in 1..columns && row in 1..rows) {
                    scalars.add(scalar)
                    positions.add(InputPosition(column = col, row = row))
                    characterIDs.add(currentArenaID)
                }
            }
        }

        return InputText(scalars = scalars, positions = positions, characterIDs = characterIDs)
    }
}
