package com.ttfx.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ParityPrimitivesTest {

    @Test
    fun xoshiroSequenceMatchesThePinnedParityShim() {
        val rng = Xoshiro256PlusPlus(seed = 42u)
        val values = (0 until 5).map { rng.nextULong() }

        val expected = listOf(
            15021278609987233951UL,
            5881210131331364753UL,
            18149643915985481100UL,
            12933668939759105464UL,
            14637574242682825331UL
        )
        assertEquals(expected, values)
    }

    @Test
    fun pythonCompatibleMathAndGeometryPreserveQuirks() {
        assertEquals(2, PyCompat.roundHalfEven(2.5))
        assertEquals(-2, PyCompat.roundHalfEven(-1.5))
        assertEquals(-4, PyCompat.floorDivide(7, -2))

        val rectCoords = Geometry.coordinatesInRectangle(center = Coordinate(3, 4), distance = 1)
        val expectedRect = listOf(
            Coordinate(2, 3), Coordinate(2, 4), Coordinate(2, 5),
            Coordinate(3, 3), Coordinate(3, 4), Coordinate(3, 5),
            Coordinate(4, 3), Coordinate(4, 4), Coordinate(4, 5)
        )
        assertEquals(expectedRect, rectCoords)

        val lineCoord = Geometry.coordinateOnLine(
            from = Coordinate(0, 0),
            to = Coordinate(5, 3),
            t = 0.5
        )
        assertEquals(Coordinate(2, 2), lineCoord)
    }

    @Test
    fun gradientsUseFloorChannelDeltasAndEasingKeepsEndpoints() {
        val gradient = Gradient(stops = listOf(Color("ff0000"), Color("0000ff")), steps = 2)
        assertEquals(listOf("ff0000", "7f007f", "0000ff"), gradient.spectrum.map { it.hex })
        assertEquals(0.0, Easing.InOutSine.value(0.0), 1e-9)
        assertEquals(1.0, Easing.InOutSine.value(1.0), 1e-9)
    }

    @Test
    fun gradientMappingsKeepTheRustInsertionOrder() {
        val gradient = Gradient(stops = listOf(Color("ff0000"), Color("0000ff")), steps = 1)
        val mapping = gradient.coordinateColorMapping(
            minRow = 1,
            maxRow = 2,
            minColumn = 1,
            maxColumn = 2,
            direction = GradientDirection.Horizontal
        )

        val expected = listOf(
            CoordinateColor(Coordinate(1, 1), Color("ff0000")),
            CoordinateColor(Coordinate(1, 2), Color("ff0000")),
            CoordinateColor(Coordinate(2, 1), Color("0000ff")),
            CoordinateColor(Coordinate(2, 2), Color("0000ff"))
        )
        assertEquals(expected, mapping.entries)
    }
}
