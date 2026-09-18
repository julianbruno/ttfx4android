package com.ttfx.core

import kotlin.math.floor

object PyCompat {
    fun roundHalfEven(value: Double): Int {
        val fl = floor(value)
        val diff = value - fl
        if (diff > 0.5) return fl.toInt() + 1
        if (diff < 0.5) return fl.toInt()
        val integer = fl.toInt()
        return if (integer % 2 == 0) integer else integer + 1
    }

    fun floorDivide(lhs: Int, rhs: Int): Int {
        require(rhs != 0) { "division by zero" }
        val quotient = lhs / rhs
        return if (lhs % rhs != 0 && (lhs < 0) != (rhs < 0)) quotient - 1 else quotient
    }

    fun modulo(lhs: Int, rhs: Int): Int {
        require(rhs != 0) { "division by zero" }
        val remainder = lhs % rhs
        return if (remainder != 0 && (remainder < 0) != (rhs < 0)) remainder + rhs else remainder
    }
}
