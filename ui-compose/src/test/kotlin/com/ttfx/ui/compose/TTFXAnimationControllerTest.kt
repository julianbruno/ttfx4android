package com.ttfx.ui.compose

import com.ttfx.core.Canvas
import com.ttfx.core.Color
import com.ttfx.core.EffectConfiguration
import com.ttfx.core.TickStatus
import com.ttfx.effects.PrintEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TTFXAnimationControllerTest {

    @Test
    fun controllerTicksEffectAndUpdatesState() {
        val columns = 10
        val rows = 5
        val canvas = Canvas(columns, rows)
        val input = canvas.ingest("HELLO")
        val config = EffectConfiguration()

        val controller = TTFXAnimationController(columns, rows) {
            PrintEffect(config, canvas, input, 42uL)
        }

        assertEquals(0, controller.frameCount.value)
        assertEquals(TickStatus.Running, controller.tickStatus.value)
        assertEquals(50, controller.cellsState.value.size)

        var ticks = 0
        while (controller.tickStatus.value != TickStatus.Complete && ticks < 200) {
            val status = controller.tick()
            ticks++
            assertEquals(ticks, controller.frameCount.value)
        }

        assertEquals(TickStatus.Complete, controller.tickStatus.value)
        assertTrue(controller.frameCount.value > 0)
    }

    @Test
    fun controllerResetRestoresInitialState() {
        val columns = 10
        val rows = 5
        val canvas = Canvas(columns, rows)
        val input = canvas.ingest("RESET")
        val config = EffectConfiguration()

        val controller = TTFXAnimationController(columns, rows) {
            PrintEffect(config, canvas, input, 42uL)
        }

        controller.tick()
        controller.tick()
        assertTrue(controller.frameCount.value >= 2)

        controller.reset()
        assertEquals(0, controller.frameCount.value)
        assertEquals(TickStatus.Running, controller.tickStatus.value)
    }

    @Test
    fun colorConversionProducesExpectedArgbValues() {
        val red = Color(255, 0, 0)
        val argb = red.toAndroidColorInt()

        val expectedArgb = uncheckedArgb(0xFF, 0xFF, 0x00, 0x00)
        assertEquals(expectedArgb, argb)
    }

    @Test
    fun packedUIntConversionProducesExpectedArgbValues() {
        val packedGreen = 0x0000FF00u
        val argb = packedGreen.packedToAndroidColorInt()

        val expectedArgb = uncheckedArgb(0xFF, 0x00, 0xFF, 0x00)
        assertEquals(expectedArgb, argb)
    }

    private fun uncheckedArgb(a: Int, r: Int, g: Int, b: Int): Int {
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
