package com.ttfx.ui.compose

import com.ttfx.core.Canvas
import com.ttfx.core.Cell
import com.ttfx.core.Color
import com.ttfx.core.Effect
import com.ttfx.core.Frame
import com.ttfx.core.TickStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controller managing the animation tick loop and state of a TTFX Canvas.
 */
class TTFXAnimationController(
    val columns: Int,
    val rows: Int,
    private val effectFactory: () -> Effect
) {
    private var currentEffect: Effect = effectFactory()
    val frame: Frame = Frame(columns, rows)

    private val _tickStatus = MutableStateFlow<TickStatus>(TickStatus.Running)
    val tickStatus: StateFlow<TickStatus> = _tickStatus.asStateFlow()

    private val _frameCount = MutableStateFlow(0)
    val frameCount: StateFlow<Int> = _frameCount.asStateFlow()

    private val _cellsState = MutableStateFlow<List<Cell>>(frame.cells.toList())
    val cellsState: StateFlow<List<Cell>> = _cellsState.asStateFlow()

    var isPaused: Boolean = false

    /**
     * Executes one tick of the active effect and updates the cell buffer snapshot.
     */
    fun tick(): TickStatus {
        if (isPaused) return _tickStatus.value

        val status = currentEffect.tick(frame)
        _tickStatus.value = status
        _frameCount.value += 1
        _cellsState.value = frame.cells.toList()
        return status
    }

    /**
     * Resets the frame and re-instantiates the effect.
     */
    fun reset() {
        for (i in 0 until (columns * rows)) {
            frame.cells[i] = Cell.BLANK
        }
        currentEffect = effectFactory()
        _tickStatus.value = TickStatus.Running
        _frameCount.value = 0
        _cellsState.value = frame.cells.toList()
    }
}
