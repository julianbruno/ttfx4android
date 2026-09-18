package com.ttfx.effects

import com.ttfx.core.*
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Batch2ParticleEffectsTest {

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
    fun bubblesEffectMatchesAdmittedRustFramesFixture() {
        val fixtureFile = File("/Users/julian/miscodigos/ttfx/Tests/fixtures/effects/bubbles.frames")
        assertTrue(fixtureFile.exists(), "bubbles.frames fixture must exist")
        val oracleFrames = decodeFrames(fixtureFile)
        assertEquals(32, oracleFrames.size)

        val canvas = Canvas(12, 6)
        val text = "Swift\nTTE"
        val input = canvas.ingest(text)
        val effect = BubblesEffect(
            configuration = EffectConfiguration(text = text, seed = 42u),
            canvas = canvas,
            input = input,
            seed = 42u,
            options = BubblesEffect.Configuration(bubbleSpeed = 3.0, bubbleDelay = 1)
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
    fun fireworksEffectMatchesAdmittedRustFramesFixture() {
        val fixtureFile = File("/Users/julian/miscodigos/ttfx/Tests/fixtures/effects/fireworks.frames")
        assertTrue(fixtureFile.exists(), "fireworks.frames fixture must exist")
        val oracleFrames = decodeFrames(fixtureFile)
        assertEquals(32, oracleFrames.size)

        val canvas = Canvas(12, 6)
        val text = "Swift\nTTE"
        val input = canvas.ingest(text)
        val effect = FireworksEffect(
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
    fun bouncyBallsEffectCompletesDeterministically() {
        val canvas = Canvas(10, 5)
        val text = "BOUNCE"
        val input = canvas.ingest(text)
        val effect = BouncyBallsEffect(
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
        assertTrue(ticks > 5, "BouncyBalls should run for multiple ticks")
    }

    @Test
    fun bubblesEffectCompletesDeterministically() {
        val canvas = Canvas(10, 5)
        val text = "BUBBLE"
        val input = canvas.ingest(text)
        val effect = BubblesEffect(
            configuration = EffectConfiguration(text = text, seed = 42u),
            canvas = canvas,
            input = input,
            seed = 42u,
            options = BubblesEffect.Configuration(bubbleSpeed = 2.0, bubbleDelay = 2)
        )

        var ticks = 0
        var status = TickStatus.Running
        while (status == TickStatus.Running && ticks < 2000) {
            val frame = Frame(canvas.columns, canvas.rows)
            status = effect.tick(frame)
            ticks++
        }
        assertEquals(TickStatus.Complete, status)
        assertTrue(ticks > 5, "Bubbles should run for multiple ticks")
    }

    @Test
    fun crumbleEffectCompletesDeterministically() {
        val canvas = Canvas(10, 5)
        val text = "CRUMBLE"
        val input = canvas.ingest(text)
        val effect = CrumbleEffect(
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
        assertTrue(ticks > 5, "Crumble should run for multiple ticks")
    }

    @Test
    fun fireworksEffectCompletesDeterministically() {
        val canvas = Canvas(10, 5)
        val text = "FIRE"
        val input = canvas.ingest(text)
        val effect = FireworksEffect(
            configuration = EffectConfiguration(text = text, seed = 42u),
            canvas = canvas,
            input = input,
            seed = 42u,
            options = FireworksEffect.Configuration(launchDelay = 5)
        )

        var ticks = 0
        var status = TickStatus.Running
        while (status == TickStatus.Running && ticks < 2000) {
            val frame = Frame(canvas.columns, canvas.rows)
            status = effect.tick(frame)
            ticks++
        }
        assertEquals(TickStatus.Complete, status)
        assertTrue(ticks > 5, "Fireworks should run for multiple ticks")
    }

    @Test
    fun blackHoleEffectCompletesDeterministically() {
        val canvas = Canvas(10, 6)
        val text = "HOLE"
        val input = canvas.ingest(text)
        val effect = BlackHoleEffect(
            configuration = EffectConfiguration(text = text, seed = 42u),
            canvas = canvas,
            input = input,
            seed = 42u
        )

        var ticks = 0
        var status = TickStatus.Running
        while (status == TickStatus.Running && ticks < 3000) {
            val frame = Frame(canvas.columns, canvas.rows)
            status = effect.tick(frame)
            ticks++
        }
        assertEquals(TickStatus.Complete, status)
        assertTrue(ticks > 5, "BlackHole should run for multiple ticks")
    }

    @Test
    fun unstableEffectCompletesDeterministically() {
        val canvas = Canvas(10, 5)
        val text = "UNSTABLE"
        val input = canvas.ingest(text)
        val effect = UnstableEffect(
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
        assertTrue(ticks > 5, "Unstable should run for multiple ticks")
    }

    @Test
    fun sprayEffectCompletesDeterministically() {
        val canvas = Canvas(10, 5)
        val text = "SPRAY"
        val input = canvas.ingest(text)
        val effect = SprayEffect(
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
        assertTrue(ticks > 5, "Spray should run for multiple ticks")
    }

    @Test
    fun pourEffectCompletesDeterministically() {
        val canvas = Canvas(10, 5)
        val text = "POUR"
        val input = canvas.ingest(text)
        val effect = PourEffect(
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
        assertTrue(ticks > 5, "Pour should run for multiple ticks")
    }

    @Test
    fun deterministicRepeatabilityForBatch2Effects() {
        val canvas = Canvas(10, 5)
        val text = "REPEAT"
        val input = canvas.ingest(text)

        fun runEffect(makeEffect: () -> Effect): List<ByteArray> {
            val effect = makeEffect()
            val frames = mutableListOf<ByteArray>()
            var ticks = 0
            while (ticks < 100) {
                val frame = Frame(canvas.columns, canvas.rows)
                val status = effect.tick(frame)
                frames.add(terminalBytes(frame))
                ticks++
                if (status == TickStatus.Complete) break
            }
            return frames
        }

        val run1 = runEffect { BouncyBallsEffect(EffectConfiguration(text = text, seed = 123u), canvas, input, 123u) }
        val run2 = runEffect { BouncyBallsEffect(EffectConfiguration(text = text, seed = 123u), canvas, input, 123u) }
        assertEquals(run1.size, run2.size)
        for (i in run1.indices) {
            assertEquals(String(run1[i], Charsets.UTF_8), String(run2[i], Charsets.UTF_8), "Run mismatch at frame $i")
        }
    }

    private fun ByteArray.indexOf(target: Byte, start: Int): Int {
        for (i in start until size) {
            if (this[i] == target) return i
        }
        return -1
    }
}
