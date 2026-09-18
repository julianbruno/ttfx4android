package com.ttfx.core

object TTFXANSI {
    const val RESET_ALL = "\u001B[0m"

    fun foregroundColor(rgb: UInt, noColor: Boolean = false): String? {
        if (rgb == 0u || noColor) return null
        val r = (rgb shr 16) and 0xFFu
        val g = (rgb shr 8) and 0xFFu
        val b = rgb and 0xFFu
        return "\u001B[38;2;$r;$g;${b}m"
    }

    fun backgroundColor(rgb: UInt, noColor: Boolean = false): String? {
        if (rgb == 0u || noColor) return null
        val r = (rgb shr 16) and 0xFFu
        val g = (rgb shr 8) and 0xFFu
        val b = rgb and 0xFFu
        return "\u001B[48;2;$r;$g;${b}m"
    }
}

class TTFXANSIRenderer(private val noColor: Boolean = false) {
    fun render(frame: Frame): String {
        val sb = StringBuilder(frame.columns * frame.rows * 4)
        for (row in frame.rows downTo 1) {
            if (row < frame.rows) sb.append("\n")
            for (col in 1..frame.columns) {
                val cell = frame[col, row]
                var styled = false
                val fg = TTFXANSI.foregroundColor(cell.foreground, noColor)
                if (fg != null) {
                    sb.append(fg)
                    styled = true
                }
                val bg = TTFXANSI.backgroundColor(cell.background, noColor)
                if (bg != null) {
                    sb.append(bg)
                    styled = true
                }
                sb.appendCodePoint(cell.codepoint)
                if (styled) {
                    sb.append(TTFXANSI.RESET_ALL)
                }
            }
        }
        return sb.toString()
    }
}
