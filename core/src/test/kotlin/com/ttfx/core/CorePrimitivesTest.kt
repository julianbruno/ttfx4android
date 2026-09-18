package com.ttfx.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CorePrimitivesTest {

    @Test
    fun cellAndFrameMutateInPlace() {
        val painted = Cell(codepoint = 65, foreground = 0x11223344u, background = 0x55667788u)
        val frame = Frame(columns = 2, rows = 2, fill = Cell.BLANK)

        frame[2, 1] = painted

        assertEquals(4, frame.cells.size)
        assertEquals(painted, frame[2, 1])
        assertEquals(Cell.BLANK, frame[1, 2])
    }

    @Test
    fun canvasIngestsUnicodeScalarsInBottomUpCoordinates() {
        val canvas = Canvas(columns = 4, rows = 3)
        val input = canvas.ingest("A\n\uD83D\uDE42B")

        assertEquals(listOf(65, 0x1F642, 66), input.scalars)
        assertEquals(
            listOf(
                InputPosition(column = 1, row = 2),
                InputPosition(column = 1, row = 1),
                InputPosition(column = 2, row = 1)
            ),
            input.positions
        )
    }

    @Test
    fun canvasTreatsPlainSpacesAsFillAndPreservesArenaIdentity() {
        val input = Canvas(columns = 4, rows = 2).ingest("A BCD\n E F")
        assertEquals(listOf(65, 66, 67, 69, 70), input.scalars)
        assertEquals(
            listOf(
                InputPosition(column = 1, row = 2),
                InputPosition(column = 3, row = 2),
                InputPosition(column = 4, row = 2),
                InputPosition(column = 2, row = 1),
                InputPosition(column = 4, row = 1)
            ),
            input.positions
        )
        assertEquals(listOf(0, 2, 3, 6, 8), input.characterIDs)
    }

    @Test
    fun invalidDimensionsThrowError() {
        assertFailsWith<IllegalArgumentException> {
            Canvas(0, 10)
        }
        assertFailsWith<IllegalArgumentException> {
            Frame(-1, 5)
        }
    }
}
