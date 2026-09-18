package com.ttfx.core

import kotlin.math.ceil

data class Color(val red: Int, val green: Int, val blue: Int) {
    constructor(hex: String) : this(
        red = hex.removePrefix("#").substring(0, 2).toInt(16),
        green = hex.removePrefix("#").substring(2, 4).toInt(16),
        blue = hex.removePrefix("#").substring(4, 6).toInt(16)
    )

    val hex: String
        get() = "%02x%02x%02x".format(red, green, blue)

    val asUInt: UInt
        get() = ((red and 0xFF) shl 16 or ((green and 0xFF) shl 8) or (blue and 0xFF)).toUInt()

    fun adjustBrightness(factor: Double): Color {
        val normalizedRed = red.toDouble() / 255.0
        val normalizedGreen = green.toDouble() / 255.0
        val normalizedBlue = blue.toDouble() / 255.0
        val maxValue = maxOf(normalizedRed, maxOf(normalizedGreen, normalizedBlue))
        val minValue = minOf(normalizedRed, minOf(normalizedGreen, normalizedBlue))
        var lightness = (maxValue + minValue) / 2.0
        val threshold = 0.5
        val hue: Double
        val saturation: Double
        if (maxValue == minValue) {
            hue = 0.0
            saturation = 0.0
        } else {
            val difference = maxValue - minValue
            saturation = if (lightness > threshold) difference / (2.0 - maxValue - minValue) else difference / (maxValue + minValue)
            var hueValue: Double = when (maxValue) {
                normalizedRed -> (normalizedGreen - normalizedBlue) / difference + (if (normalizedGreen < normalizedBlue) 6.0 else 0.0)
                normalizedGreen -> (normalizedBlue - normalizedRed) / difference + 2.0
                else -> (normalizedRed - normalizedGreen) / difference + 4.0
            }
            hueValue /= 6.0
            hue = hueValue
        }

        lightness = (lightness * factor).coerceIn(0.0, 1.0)
        val r: Double
        val g: Double
        val b: Double
        if (saturation == 0.0) {
            r = lightness
            g = lightness
            b = lightness
        } else {
            val colorIntensity = if (lightness < threshold) lightness * (1.0 + saturation) else lightness + saturation - lightness * saturation
            val lightnessScaled = 2.0 * lightness - colorIntensity
            fun hueToRGB(value: Double): Double {
                var v = value
                if (v < 0.0) v += 1.0
                if (v > 1.0) v -= 1.0
                return when {
                    v < 1.0 / 6.0 -> lightnessScaled + (colorIntensity - lightnessScaled) * 6.0 * v
                    v < 1.0 / 2.0 -> colorIntensity
                    v < 2.0 / 3.0 -> lightnessScaled + (colorIntensity - lightnessScaled) * (2.0 / 3.0 - v) * 6.0
                    else -> lightnessScaled
                }
            }
            r = hueToRGB(hue + 1.0 / 3.0)
            g = hueToRGB(hue)
            b = hueToRGB(hue - 1.0 / 3.0)
        }
        return Color(
            PyCompat.roundHalfEven(r * 255.0),
            PyCompat.roundHalfEven(g * 255.0),
            PyCompat.roundHalfEven(b * 255.0)
        )
    }
}

enum class GradientDirection {
    Vertical, Horizontal, Radial, Diagonal
}

data class CoordinateColor(val coordinate: Coordinate, val color: Color)

data class CoordinateColorMapping(val entries: List<CoordinateColor>)

class Gradient(
    stops: List<Color>,
    steps: List<Int>,
    loop: Boolean = false
) {
    constructor(stops: List<Color>, steps: Int, loop: Boolean = false) : this(stops, listOf(steps), loop)

    val spectrum: List<Color>

    init {
        require(stops.isNotEmpty()) { "stops cannot be empty" }
        require(steps.isNotEmpty() && steps.all { it > 0 }) { "steps must be positive" }

        if (stops.size == 1) {
            spectrum = List(steps[0]) { stops[0] }
        } else {
            val colors = if (loop) stops + stops[0] else stops
            val pairCount = colors.size - 1
            val counts = ArrayList(steps.take(pairCount))
            while (counts.size < pairCount) {
                counts.add(counts.last())
            }

            val generated = ArrayList<Color>()
            for (index in 0 until pairCount) {
                val start = colors[index]
                val end = colors[index + 1]
                val count = counts[index]
                val redDelta = PyCompat.floorDivide(end.red - start.red, count)
                val greenDelta = PyCompat.floorDivide(end.green - start.green, count)
                val blueDelta = PyCompat.floorDivide(end.blue - start.blue, count)

                val startStep = if (generated.isEmpty()) 0 else 1
                for (step in startStep until count) {
                    val r = (start.red + redDelta * step).coerceIn(0, 255)
                    val g = (start.green + greenDelta * step).coerceIn(0, 255)
                    val b = (start.blue + blueDelta * step).coerceIn(0, 255)
                    generated.add(Color(r, g, b))
                }
                generated.add(end)
            }
            spectrum = generated
        }
    }

    fun color(fraction: Double): Color {
        require(fraction in 0.0..1.0) { "fraction outside 0..1" }
        val index = minOf(ceil(fraction * spectrum.size).toInt(), spectrum.size) - 1
        return spectrum[maxOf(index, 0)]
    }

    fun coordinateColorMapping(
        minRow: Int,
        maxRow: Int,
        minColumn: Int,
        maxColumn: Int,
        direction: GradientDirection
    ): CoordinateColorMapping {
        require(minRow >= 1 && minColumn >= 1 && maxRow >= minRow && maxColumn >= minColumn) {
            "invalid coordinates"
        }
        val rowOffset = minRow - 1
        val columnOffset = minColumn - 1
        val entries = ArrayList<CoordinateColor>()

        fun append(column: Int, row: Int, fraction: Double) {
            entries.add(CoordinateColor(Coordinate(column, row), color(fraction)))
        }

        when (direction) {
            GradientDirection.Vertical -> {
                for (row in minRow..maxRow) {
                    val fraction = (row - rowOffset).toDouble() / (maxRow - rowOffset).toDouble()
                    for (column in minColumn..maxColumn) {
                        append(column, row, fraction)
                    }
                }
            }
            GradientDirection.Horizontal -> {
                for (column in minColumn..maxColumn) {
                    val fraction = (column - columnOffset).toDouble() / (maxColumn - columnOffset).toDouble()
                    for (row in minRow..maxRow) {
                        append(column, row, fraction)
                    }
                }
            }
            GradientDirection.Radial -> {
                for (row in minRow..maxRow) {
                    for (column in minColumn..maxColumn) {
                        val fraction = Geometry.normalizedDistanceFromCenter(
                            bottom = minRow, top = maxRow, left = minColumn, right = maxColumn,
                            coordinate = Coordinate(column, row)
                        )
                        append(column, row, fraction)
                    }
                }
            }
            GradientDirection.Diagonal -> {
                for (row in minRow..maxRow) {
                    for (column in minColumn..maxColumn) {
                        val fraction = ((row - rowOffset) * 2 + column - columnOffset).toDouble() /
                                ((maxRow - rowOffset) * 2 + maxColumn - columnOffset).toDouble()
                        append(column, row, fraction)
                    }
                }
            }
        }
        return CoordinateColorMapping(entries)
    }
}
