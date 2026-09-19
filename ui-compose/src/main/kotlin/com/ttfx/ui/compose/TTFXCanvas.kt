package com.ttfx.ui.compose

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import com.ttfx.core.Cell
import com.ttfx.core.Color
import com.ttfx.core.TickStatus
import kotlinx.coroutines.delay

/**
 * Converts a TTFX Color to an Android ARGB Int for Native Canvas Paint.
 */
fun Color.toAndroidColorInt(): Int {
    return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
}

/**
 * Converts a packed 24-bit UInt color (0x00RRGGBBu) to an Android ARGB Int.
 */
fun UInt.packedToAndroidColorInt(): Int {
    if (this == 0u) return 0
    val r = ((this shr 16) and 0xFFu).toInt()
    val g = ((this shr 8) and 0xFFu).toInt()
    val b = (this and 0xFFu).toInt()
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

/**
 * Jetpack Compose terminal canvas renderer.
 * Renders characters in a monospace grid directly to a hardware-accelerated Compose Canvas.
 */
@Composable
fun TTFXCanvas(
    controller: TTFXAnimationController,
    modifier: Modifier = Modifier,
    fps: Int = 30,
    backgroundColor: ComposeColor = ComposeColor.Black,
    fontSizeSp: Float = 14f,
    fitViewport: Boolean = false,
    autoPlay: Boolean = true
) {
    val tickStatus by controller.tickStatus.collectAsState()
    val cells by controller.cellsState.collectAsState()

    // Animation ticker loop
    LaunchedEffect(controller, autoPlay, fps) {
        if (!autoPlay) return@LaunchedEffect
        val frameDurationMs = (1000L / fps.coerceAtLeast(1)).coerceAtLeast(1L)
        while (true) {
            if (!controller.isPaused && controller.tickStatus.value != TickStatus.Complete) {
                controller.tick()
            }
            delay(frameDurationMs)
        }
    }

    val density = LocalDensity.current
    val textPaint = remember(fontSizeSp, density) {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
            textSize = fontSizeSp * density.density
            textAlign = Paint.Align.LEFT
        }
    }

    val bgPaint = remember {
        Paint().apply {
            style = Paint.Style.FILL
        }
    }

    val fontMetrics = remember(textPaint) { textPaint.fontMetrics }
    val charWidth = remember(textPaint) { textPaint.measureText("M") }
    val charHeight = remember(fontMetrics) { fontMetrics.descent - fontMetrics.ascent }
    val textBaselineOffset = remember(fontMetrics) { -fontMetrics.ascent }

    val widthCells = controller.columns
    val heightCells = controller.rows

    Box(modifier = modifier.background(backgroundColor)) {
        ComposeCanvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidthPx = size.width
            val canvasHeightPx = size.height

            // Calculate grid scale
            val scale = if (fitViewport) {
                val scaleX = if (widthCells > 0 && charWidth > 0f) canvasWidthPx / (widthCells * charWidth) else 1f
                val scaleY = if (heightCells > 0 && charHeight > 0f) canvasHeightPx / (heightCells * charHeight) else 1f
                minOf(scaleX, scaleY).coerceAtLeast(0.1f)
            } else {
                1f
            }

            val effectiveCharWidth = charWidth * scale
            val effectiveCharHeight = charHeight * scale

            val offsetX = ((canvasWidthPx - widthCells * effectiveCharWidth) / 2f)
            val offsetY = ((canvasHeightPx - heightCells * effectiveCharHeight) / 2f)

            val nativeCanvas = drawContext.canvas.nativeCanvas

            drawContext.canvas.save()
            drawContext.canvas.translate(offsetX, offsetY)

            textPaint.textSize = (fontSizeSp * density.density) * scale

            for (y in 0 until heightCells) {
                val rowY = y * effectiveCharHeight
                for (x in 0 until widthCells) {
                    val index = y * widthCells + x
                    if (index >= cells.size) break
                    val cell = cells[index]

                    val colX = x * effectiveCharWidth

                    // Draw cell background if not transparent
                    if (cell.background != 0u) {
                        bgPaint.color = cell.background.packedToAndroidColorInt()
                        nativeCanvas.drawRect(
                            colX,
                            rowY,
                            colX + effectiveCharWidth,
                            rowY + effectiveCharHeight,
                            bgPaint
                        )
                    }

                    // Draw cell character if not space or null
                    if (cell.codepoint != 32 && cell.codepoint != 0) {
                        textPaint.color = cell.foreground.packedToAndroidColorInt()
                        val charStr = String(Character.toChars(cell.codepoint))
                        nativeCanvas.drawText(
                            charStr,
                            colX,
                            rowY + (textBaselineOffset * scale),
                            textPaint
                        )
                    }
                }
            }

            drawContext.canvas.restore()
        }
    }
}
