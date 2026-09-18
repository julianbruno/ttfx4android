package com.ttfx.effects

import com.ttfx.core.*
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Batch1RevealEffectsTest {

    private fun decodeFrames(file: File): List<ByteArray> {
        val bytes = file.readBytes()
        var offset = 0
        val frames = mutableListOf<ByteArray>()
        while (offset < bytes.size) {
            val lineEnd = bytes.indexOf(10.toByte(), offset)
            if (lineEnd == -1) break
            val lengthStr = String(bytes, offset, lineEnd - offset, Charsets.UTF_8)
            val length = lengthStr.toInt()
            val frameStart = lineEnd + 1
            val frameEnd = frameStart + length
            frames.add(bytes.copyOfRange(frameStart, frameEnd))
            offset = frameEnd + 1 // skip trailing newline
        }
        return frames
    }

    private fun terminalBytes(frame: Frame): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        val explicitBlackSentinel = 0xFFFF_FFFEu
        for (row in frame.rows downTo 1) {
            for (col in 1..frame.columns) {
                val cell = frame[col, row]
                val hasStyle = cell.foreground != 0u || cell.background == explicitBlackSentinel
                if (hasStyle) {
                    val red = (cell.foreground shr 16) and 0xFFu
                    val green = (cell.foreground shr 8) and 0xFFu
                    val blue = cell.foreground and 0xFFu
                    baos.write("\u001B[38;2;$red;$green;${blue}m".toByteArray(Charsets.UTF_8))
                }
                baos.write(String(Character.toChars(cell.codepoint)).toByteArray(Charsets.UTF_8))
                if (hasStyle) {
                    baos.write("\u001B[0m".toByteArray(Charsets.UTF_8))
                }
            }
            if (row != 1) {
                baos.write(10)
            }
        }
        return baos.toByteArray()
    }

    @Test
    fun printEffectMatchesAdmittedRustFramesFixture() {
        val fixtureFile = File("/Users/julian/miscodigos/ttfx/Tests/fixtures/effects/print.frames")
        assertTrue(fixtureFile.exists(), "print.frames fixture must exist")
        val oracleFrames = decodeFrames(fixtureFile)
        assertEquals(32, oracleFrames.size)

        val canvas = Canvas(12, 6)
        val text = "Swift\nTTE"
        val input = canvas.ingest(text)
        val effect = PrintEffect(
            configuration = EffectConfiguration(text = text, seed = 42u),
            canvas = canvas,
            input = input,
            seed = 42u
        )

        for ((tick, expected) in oracleFrames.withIndex()) {
            val frame = Frame(canvas.columns, canvas.rows)
            val status = effect.tick(frame)
            if (tick + 1 < oracleFrames.size) {
                assertEquals(TickStatus.Running, status)
            }
            val actual = terminalBytes(frame)
            assertEquals(
                String(expected, Charsets.UTF_8),
                String(actual, Charsets.UTF_8),
                "Mismatch at tick $tick"
            )
        }
    }

    @Test
    fun decryptEffectCompletesDeterministically() {
        val canvas = Canvas(8, 4)
        val text = "KOTLIN"
        val input = canvas.ingest(text)
        val effect = DecryptEffect(
            configuration = EffectConfiguration(text = text, seed = 42u),
            canvas = canvas,
            input = input,
            seed = 42u
        )

        var ticks = 0
        var status = TickStatus.Running
        while (status == TickStatus.Running && ticks < 2000) {
            val frame = Frame(canvas.columns, canvas.rows)
            status = effect.tick(frame)
            ticks++
        }
        assertEquals(TickStatus.Complete, status)
        assertTrue(ticks > 10, "Decrypt effect should take multiple ticks to complete")
    }

    @Test
    fun errorCorrectEffectCompletesDeterministically() {
        val canvas = Canvas(8, 4)
        val text = "CORRECT"
        val input = canvas.ingest(text)
        val effect = ErrorCorrectEffect(
            configuration = EffectConfiguration(text = text, seed = 42u),
            canvas = canvas,
            input = input,
            seed = 42u
        )

        var ticks = 0
        var status = TickStatus.Running
        while (status == TickStatus.Running && ticks < 2000) {
            val frame = Frame(canvas.columns, canvas.rows)
            status = effect.tick(frame)
            ticks++
        }
        assertEquals(TickStatus.Complete, status)
    }

    @Test
    fun randomSequenceEffectCompletesDeterministically() {
        val canvas = Canvas(8, 4)
        val text = "RANDOM"
        val input = canvas.ingest(text)
        val effect = RandomSequenceEffect(
            configuration = EffectConfiguration(text = text, seed = 42u),
            canvas = canvas,
            input = input,
            seed = 42u
        )

        var ticks = 0
        var status = TickStatus.Running
        while (status == TickStatus.Running && ticks < 2000) {
            val frame = Frame(canvas.columns, canvas.rows)
            status = effect.tick(frame)
            ticks++
        }
        assertEquals(TickStatus.Complete, status)
    }

    @Test
    fun laserEtchEffectCompletesDeterministically() {
        val canvas = Canvas(8, 4)
        val text = "LASER"
        val input = canvas.ingest(text)
        val effect = LaserEtchEffect(
            configuration = EffectConfiguration(text = text, seed = 42u),
            canvas = canvas,
            input = input,
            seed = 42u
        )

        var ticks = 0
        var status = TickStatus.Running
        while (status == TickStatus.Running && ticks < 2000) {
            val frame = Frame(canvas.columns, canvas.rows)
            status = effect.tick(frame)
            ticks++
        }
        assertEquals(TickStatus.Complete, status)
    }

    @Test
    fun binaryPathEffectCompletesDeterministically() {
        val canvas = Canvas(8, 4)
        val text = "BINARY"
        val input = canvas.ingest(text)
        val effect = BinaryPathEffect(
            configuration = EffectConfiguration(text = text, seed = 42u),
            canvas = canvas,
            input = input,
            seed = 42u
        )

        var ticks = 0
        var status = TickStatus.Running
        while (status == TickStatus.Running && ticks < 2000) {
            val frame = Frame(canvas.columns, canvas.rows)
            status = effect.tick(frame)
            ticks++
        }
        assertEquals(TickStatus.Complete, status)
    }

    private fun ByteArray.indexOf(target: Byte, start: Int): Int {
        for (i in start until size) {
            if (this[i] == target) return i
        }
        return -1
    }
}
