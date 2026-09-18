package com.ttfx.core

data class Cell(
    val codepoint: Int,
    val foreground: UInt,
    val background: UInt
) {
    companion object {
        val BLANK = Cell(codepoint = 32, foreground = 0u, background = 0u)
    }
}

class Frame(
    val columns: Int,
    val rows: Int,
    fill: Cell = Cell.BLANK
) {
    init {
        require(columns > 0 && rows > 0) {
            "invalid dimensions: columns=$columns, rows=$rows"
        }
    }

    val cells: Array<Cell> = Array(columns * rows) { fill }

    operator fun get(column: Int, row: Int): Cell {
        return cells[offset(column, row)]
    }

    operator fun set(column: Int, row: Int, cell: Cell) {
        cells[offset(column, row)] = cell
    }

    private fun offset(column: Int, row: Int): Int {
        require(column in 1..columns) { "column $column outside frame 1..$columns" }
        require(row in 1..rows) { "row $row outside frame 1..$rows" }
        return (rows - row) * columns + (column - 1)
    }
}
