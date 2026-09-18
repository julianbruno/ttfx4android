package com.ttfx.core

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

data class Coordinate(val column: Int, val row: Int)

object Geometry {
    fun coordinatesOnCircle(origin: Coordinate, radius: Int, limit: Int = 0, unique: Boolean = true): List<Coordinate> {
        if (radius == 0) return emptyList()
        val count = if (limit == 0) PyCompat.roundHalfEven(2 * Math.PI * radius) else limit
        val step = 2 * Math.PI / count
        val coordinates = ArrayList<Coordinate>()
        val seen = HashSet<Coordinate>()
        for (index in 0 until count) {
            val angle = step * index
            var x = origin.column.toDouble() + radius.toDouble() * cos(angle)
            x += x - origin.column.toDouble()
            val y = origin.row.toDouble() + radius.toDouble() * sin(angle)
            val coordinate = Coordinate(column = PyCompat.roundHalfEven(x), row = PyCompat.roundHalfEven(y))
            if (!unique || seen.add(coordinate)) {
                coordinates.add(coordinate)
            }
        }
        return coordinates
    }

    fun coordinatesInEllipse(center: Coordinate, diameter: Int): List<Coordinate> {
        if (diameter == 0) return emptyList()
        val aSquared = (diameter * diameter).toDouble()
        val bSquared = (diameter * diameter).toDouble() / 4.0
        val coordinates = ArrayList<Coordinate>()
        for (column in (center.column - diameter)..(center.column + diameter)) {
            val xComponent = ((column - center.column) * (column - center.column)).toDouble() / aSquared
            val maxYOffset = sqrt(bSquared * (1 - xComponent)).toInt()
            for (row in (center.row - maxYOffset)..(center.row + maxYOffset)) {
                coordinates.add(Coordinate(column = column, row = row))
            }
        }
        return coordinates
    }

    fun coordinatesInRectangle(center: Coordinate, distance: Int): List<Coordinate> {
        if (distance == 0) return emptyList()
        val coordinates = ArrayList<Coordinate>()
        for (column in (center.column - distance)..(center.column + distance)) {
            for (row in (center.row - distance)..(center.row + distance)) {
                coordinates.add(Coordinate(column = column, row = row))
            }
        }
        return coordinates
    }

    fun coordinatesOnRectangle(center: Coordinate, halfWidth: Int, halfHeight: Int): List<Coordinate> {
        if (halfWidth == 0 || halfHeight == 0) return emptyList()
        val coordinates = ArrayList<Coordinate>()
        for (column in (center.column - halfWidth)..(center.column + halfWidth)) {
            if (column == center.column - halfWidth || column == center.column + halfWidth) {
                for (row in (center.row - halfHeight)..(center.row + halfHeight)) {
                    coordinates.add(Coordinate(column = column, row = row))
                }
            } else {
                coordinates.add(Coordinate(column = column, row = center.row - halfHeight))
                coordinates.add(Coordinate(column = column, row = center.row + halfHeight))
            }
        }
        return coordinates
    }

    fun coordinateOnLine(from: Coordinate, to: Coordinate, t: Double): Coordinate {
        return Coordinate(
            column = PyCompat.roundHalfEven((1 - t) * from.column.toDouble() + t * to.column.toDouble()),
            row = PyCompat.roundHalfEven((1 - t) * from.row.toDouble() + t * to.row.toDouble())
        )
    }

    fun coordinateOnBezier(from: Coordinate, controls: List<Coordinate>, to: Coordinate, t: Double): Coordinate {
        if (controls.isEmpty()) return coordinateOnLine(from, to, t)
        val points = ArrayList<Pair<Double, Double>>()
        points.add(from.column.toDouble() to from.row.toDouble())
        for (c in controls) {
            points.add(c.column.toDouble() to c.row.toDouble())
        }
        points.add(to.column.toDouble() to to.row.toDouble())

        var remaining = points.size
        while (remaining > 1) {
            for (i in 0 until (remaining - 1)) {
                val p0 = points[i]
                val p1 = points[i + 1]
                points[i] = ((1 - t) * p0.first + t * p1.first) to ((1 - t) * p0.second + t * p1.second)
            }
            remaining--
        }
        return Coordinate(
            column = PyCompat.roundHalfEven(points[0].first),
            row = PyCompat.roundHalfEven(points[0].second)
        )
    }

    fun extrapolateAlongRay(origin: Coordinate, target: Coordinate, offsetFromTarget: Double): Coordinate {
        val base = lineLength(origin, target, doubleRowDifference = false)
        val total = base + offsetFromTarget
        if (total == 0.0 || origin == target) return target
        val t = total / base
        return Coordinate(
            column = PyCompat.roundHalfEven((1 - t) * origin.column.toDouble() + t * target.column.toDouble()),
            row = PyCompat.roundHalfEven((1 - t) * origin.row.toDouble() + t * target.row.toDouble())
        )
    }

    fun lineLength(from: Coordinate, to: Coordinate, doubleRowDifference: Boolean = true): Double {
        return hypot(
            (to.column - from.column).toDouble(),
            (to.row - from.row).toDouble() * (if (doubleRowDifference) 2.0 else 1.0)
        )
    }

    fun bezierLength(from: Coordinate, controls: List<Coordinate>, to: Coordinate): Double {
        var length = 0.0
        var previous = from
        for (step in 1 until 10) {
            val coordinate = coordinateOnBezier(from, controls, to, step.toDouble() / 10.0)
            length += lineLength(previous, coordinate)
            previous = coordinate
        }
        return length
    }

    fun normalizedDistanceFromCenter(
        bottom: Int,
        top: Int,
        left: Int,
        right: Int,
        coordinate: Coordinate
    ): Double {
        val rowOffset = bottom - 1
        val columnOffset = left - 1
        val normalizedRight = right - columnOffset
        val normalizedTop = top - rowOffset
        val column = coordinate.column - columnOffset
        val row = coordinate.row - rowOffset
        require(column in (left - columnOffset)..normalizedRight && row in (bottom - rowOffset)..normalizedTop) {
            "invalid coordinates for normalized distance"
        }
        val centerX = normalizedRight.toDouble() / 2.0
        val centerY = normalizedTop.toDouble() / 2.0
        val maximum = hypot(normalizedRight.toDouble(), (normalizedTop * 2).toDouble())
        val distance = hypot(column.toDouble() - centerX, (row.toDouble() - centerY) * 2.0)
        return distance / (maximum / 2.0)
    }
}
