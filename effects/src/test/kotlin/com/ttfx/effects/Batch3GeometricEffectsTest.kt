package com.ttfx.effects

import com.ttfx.core.*
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Batch3GeometricEffectsTest {

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
    fun slideEffectMatchesAdmittedRustFramesFixture() {
        val fixtureFile = File("/Users/julian/miscodigos/ttfx/Tests/fixtures/effects/slide.frames")
        assertTrue(fixtureFile.exists(), "slide.frames fixture must exist")
        val oracleFrames = decodeFrames(fixtureFile)
        assertEquals(32, oracleFrames.size)

        val canvas = Canvas(12, 6)
        val text = "Swift\nTTE"
        val input = canvas.ingest(text)
        val effect = SlideEffect(
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
    fun expandEffectMatchesAdmittedRustFramesFixture() {
        val fixtureFile = File("/Users/julian/miscodigos/ttfx/Tests/fixtures/effects/expand.frames")
        assertTrue(fixtureFile.exists(), "expand.frames fixture must exist")
        val oracleFrames = decodeFrames(fixtureFile)
        assertEquals(18, oracleFrames.size)

        val canvas = Canvas(12, 6)
        val text = "Swift\nTTE"
        val input = canvas.ingest(text)
        val effect = ExpandEffect(
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
            } else {
                assertEquals(TickStatus.Complete, status)
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
    fun swarmEffectMatchesAdmittedRustFramesFixture() {
        val fixtureFile = File("/Users/julian/miscodigos/ttfx/Tests/fixtures/effects/swarm.frames")
        assertTrue(fixtureFile.exists(), "swarm.frames fixture must exist")
        val oracleFrames = decodeFrames(fixtureFile)
        assertEquals(32, oracleFrames.size)

        val canvas = Canvas(12, 6)
        val text = "Swift\nTTE"
        val input = canvas.ingest(text)
        val effect = SwarmEffect(
            configuration = EffectConfiguration(text = text, seed = 42u),
            canvas = canvas,
            input = input,
            seed = 42u,
            options = SwarmEffect.Configuration(
                swarmSize = 1.0,
                swarmCoordination = 1.0,
                swarmAreaCountRange = 1..1
            )
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
    fun slideEffectCompletesDeterministically() {
        val canvas = Canvas(10, 5)
        val text = "SLIDE"
        val input = canvas.ingest(text)
        val effect = SlideEffect(
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
        assertTrue(ticks > 5, "Slide should run for multiple ticks")
    }

    @Test
    fun sliceEffectCompletesDeterministically() {
        val canvas = Canvas(10, 5)
        val text = "SLICE"
        val input = canvas.ingest(text)
        val effect = SliceEffect(
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
        assertTrue(ticks > 5, "Slice should run for multiple ticks")
    }

    @Test
    fun middleOutEffectCompletesDeterministically() {
        val canvas = Canvas(10, 5)
        val text = "MIDDLE"
        val input = canvas.ingest(text)
        val effect = MiddleOutEffect(
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
        assertTrue(ticks > 5, "MiddleOut should run for multiple ticks")
    }

    @Test
    fun expandEffectCompletesDeterministically() {
        val canvas = Canvas(10, 5)
        val text = "EXPAND"
        val input = canvas.ingest(text)
        val effect = ExpandEffect(
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
        assertTrue(ticks > 5, "Expand should run for multiple ticks")
    }

    @Test
    fun scatteredEffectCompletesDeterministically() {
        val canvas = Canvas(10, 5)
        val text = "SCATTER"
        val input = canvas.ingest(text)
        val effect = ScatteredEffect(
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
        assertTrue(ticks > 5, "Scattered should run for multiple ticks")
    }

    @Test
    fun swarmEffectCompletesDeterministically() {
        val canvas = Canvas(10, 5)
        val text = "SWARM"
        val input = canvas.ingest(text)
        val effect = SwarmEffect(
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
        assertTrue(ticks > 5, "Swarm should run for multiple ticks")
    }

    @Test
    fun ringsEffectCompletesDeterministically() {
        val canvas = Canvas(10, 5)
        val text = "RINGS"
        val input = canvas.ingest(text)
        val effect = RingsEffect(
            configuration = EffectConfiguration(text = text, seed = 42u),
            canvas = canvas,
            input = input,
            seed = 42u,
            options = RingsEffect.Configuration(spinDuration = 10, disperseDuration = 10, spinDisperseCycles = 1)
        )

        var ticks = 0
        var status = TickStatus.Running
        while (status == TickStatus.Running && ticks < 2000) {
            val frame = Frame(canvas.columns, canvas.rows)
            status = effect.tick(frame)
            ticks++
        }
        assertEquals(TickStatus.Complete, status)
        assertTrue(ticks > 5, "Rings should run for multiple ticks")
    }

    @Test
    fun orbittingVolleyEffectCompletesDeterministically() {
        val canvas = Canvas(10, 5)
        val text = "VOLLEY"
        val input = canvas.ingest(text)
        val effect = OrbittingVolleyEffect(
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
        assertTrue(ticks > 5, "OrbittingVolley should run for multiple ticks")
    }

    @Test
    fun overflowEffectCompletesDeterministically() {
        val canvas = Canvas(10, 5)
        val text = "FLOW"
        val input = canvas.ingest(text)
        val effect = OverflowEffect(
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
        assertTrue(ticks > 5, "Overflow should run for multiple ticks")
    }

    @Test
    fun deterministicRepeatabilityForBatch3Effects() {
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

        val run1 = runEffect { SlideEffect(EffectConfiguration(text = text, seed = 123u), canvas, input, 123u) }
        val run2 = runEffect { SlideEffect(EffectConfiguration(text = text, seed = 123u), canvas, input, 123u) }
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
