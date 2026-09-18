package com.ttfx.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class Easing {
    Linear, InSine, OutSine, InOutSine, InQuad, OutQuad, InOutQuad,
    InCubic, OutCubic, InOutCubic, InQuart, OutQuart, InOutQuart,
    InQuint, OutQuint, InOutQuint, InExpo, OutExpo, InOutExpo,
    InCirc, OutCirc, InOutCirc, InBack, OutBack, InOutBack,
    InElastic, OutElastic, InOutElastic, InBounce, OutBounce, InOutBounce;

    fun value(x: Double): Double {
        return when (this) {
            Linear -> x
            InSine -> 1.0 - cos((x * PI) / 2.0)
            OutSine -> sin((x * PI) / 2.0)
            InOutSine -> -(cos(PI * x) - 1.0) / 2.0
            InQuad -> x * x
            OutQuad -> 1.0 - (1.0 - x) * (1.0 - x)
            InOutQuad -> if (x < 0.5) 2.0 * x * x else 1.0 - (-2.0 * x + 2.0).pow(2) / 2.0
            InCubic -> x * x * x
            OutCubic -> 1.0 - (1.0 - x).pow(3)
            InOutCubic -> if (x < 0.5) 4.0 * x * x * x else 1.0 - (-2.0 * x + 2.0).pow(3) / 2.0
            InQuart -> x.pow(4)
            OutQuart -> 1.0 - (1.0 - x).pow(4)
            InOutQuart -> if (x < 0.5) 8.0 * x.pow(4) else 1.0 - (-2.0 * x + 2.0).pow(4) / 2.0
            InQuint -> x.pow(5)
            OutQuint -> 1.0 - (1.0 - x).pow(5)
            InOutQuint -> if (x < 0.5) 16.0 * x.pow(5) else 1.0 - (-2.0 * x + 2.0).pow(5) / 2.0
            InExpo -> if (x == 0.0) 0.0 else 2.0.pow(10.0 * x - 10.0)
            OutExpo -> if (x == 1.0) 1.0 else 1.0 - 2.0.pow(-10.0 * x)
            InOutExpo -> when {
                x == 0.0 -> 0.0
                x == 1.0 -> 1.0
                x < 0.5 -> 2.0.pow(20.0 * x - 10.0) / 2.0
                else -> (2.0 - 2.0.pow(-20.0 * x + 10.0)) / 2.0
            }
            InCirc -> 1.0 - sqrt(1.0 - x.pow(2))
            OutCirc -> sqrt(1.0 - (x - 1.0).pow(2))
            InOutCirc -> if (x < 0.5) (1.0 - sqrt(1.0 - (2.0 * x).pow(2))) / 2.0 else (sqrt(1.0 - (-2.0 * x + 2.0).pow(2)) + 1.0) / 2.0
            InBack -> 2.70158 * x * x * x - 1.70158 * x * x
            OutBack -> 1.0 + 2.70158 * (x - 1.0).pow(3) + 1.70158 * (x - 1.0).pow(2)
            InOutBack -> {
                val c = 1.70158 * 1.525
                if (x < 0.5) (2.0 * x).pow(2) * ((c + 1.0) * 2.0 * x - c) / 2.0
                else ((-2.0 * x + 2.0).pow(2) * ((c + 1.0) * (x * 2.0 - 2.0) + c) + 2.0) / 2.0
            }
            InElastic -> when {
                x == 0.0 -> 0.0
                x == 1.0 -> 1.0
                else -> -2.0.pow(10.0 * x - 10.0) * sin((x * 10.0 - 10.75) * (2.0 * PI / 3.0))
            }
            OutElastic -> when {
                x == 0.0 -> 0.0
                x == 1.0 -> 1.0
                else -> 2.0.pow(-10.0 * x) * sin((x * 10.0 - 0.75) * (2.0 * PI / 3.0)) + 1.0
            }
            InOutElastic -> when {
                x == 0.0 -> 0.0
                x == 1.0 -> 1.0
                x < 0.5 -> -(2.0.pow(20.0 * x - 10.0) * sin((20.0 * x - 11.125) * (2.0 * PI / 4.5))) / 2.0
                else -> (2.0.pow(-20.0 * x + 10.0) * sin((20.0 * x - 11.125) * (2.0 * PI / 4.5))) / 2.0 + 1.0
            }
            InBounce -> 1.0 - OutBounce.value(1.0 - x)
            OutBounce -> {
                val n = 7.5625
                val d = 2.75
                when {
                    x < 1.0 / d -> n * x * x
                    x < 2.0 / d -> n * (x - 1.5 / d).pow(2) + 0.75
                    x < 2.5 / d -> n * (x - 2.25 / d).pow(2) + 0.9375
                    else -> n * (x - 2.625 / d).pow(2) + 0.984375
                }
            }
            InOutBounce -> if (x < 0.5) (1.0 - OutBounce.value(1.0 - 2.0 * x)) / 2.0 else (1.0 + OutBounce.value(2.0 * x - 1.0)) / 2.0
        }
    }
}
