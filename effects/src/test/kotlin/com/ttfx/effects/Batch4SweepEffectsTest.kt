package com.ttfx.effects

import com.ttfx.core.*
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Batch4SweepEffectsTest {

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
    fun rainEffectMatchesAdmittedRustFramesFixture() {
        val fixtureFile = File("/Users/julian/miscodigos/ttfx/Tests/fixtures/effects/rain.frames")
        assertTrue(fixtureFile.exists(), "rain.frames fixture must exist")
        val oracleFrames = decodeFrames(fixtureFile)
        assertEquals(32, oracleFrames.size)

        val canvas = Canvas(12, 6)
        val text = "Swift\nTTE"
        val input = canvas.ingest(text)
        val effect = RainEffect(
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
    fun wipeEffectMatchesAdmittedRustFramesFixture() {
        val fixtureFile = File("/Users/julian/miscodigos/ttfx/Tests/fixtures/effects/wipe.frames")
        assertTrue(fixtureFile.exists(), "wipe.frames fixture must exist")
        val oracleFrames = decodeFrames(fixtureFile)
        assertEquals(32, oracleFrames.size)

        val canvas = Canvas(12, 6)
        val text = "Swift\nTTE"
        val input = canvas.ingest(text)
        val effect = WipeEffect(
            configuration = EffectConfiguration(text = text, seed = 42u),
            canvas = canvas,
            input = input,
            seed = 42u,
            options = WipeEffect.Configuration(
                easing = Easing.OutExpo,
                finalGradientSteps = listOf(1),
                finalGradientFrames = 1
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
    fun beamsEffectCompletesDeterministically() {
        val canvas = Canvas(8, 4)
        val text = "KOTLIN"
        val input = canvas.ingest(text)
        val effect = BeamsEffect(
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
        assertTrue(ticks > 5, "Beams effect should run for multiple ticks")
    }

    @Test
    fun burnEffectCompletesDeterministically() {
        val canvas = Canvas(8, 4)
        val text = "KOTLIN"
        val input = canvas.ingest(text)
        val effect = BurnEffect(
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
        assertTrue(ticks > 5, "Burn effect should run for multiple ticks")
    }

    @Test
    fun colorShiftEffectCompletesDeterministically() {
        val canvas = Canvas(8, 4)
        val text = "KOTLIN"
        val input = canvas.ingest(text)
        val effect = ColorShiftEffect(
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
        assertTrue(ticks > 5, "ColorShift effect should run for multiple ticks")
    }

    @Test
    fun highlightEffectCompletesDeterministically() {
        val canvas = Canvas(8, 4)
        val text = "KOTLIN"
        val input = canvas.ingest(text)
        val effect = HighlightEffect(
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
        assertTrue(ticks > 5, "Highlight effect should run for multiple ticks")
    }

    @Test
    fun matrixEffectCompletesDeterministically() {
        val canvas = Canvas(8, 4)
        val text = "KOTLIN"
        val input = canvas.ingest(text)
        val effect = MatrixEffect(
            configuration = EffectConfiguration(text = text, seed = 42u),
            canvas = canvas,
            input = input,
            seed = 42u,
            options = MatrixEffect.Configuration(rainTime = 1)
        )

        var ticks = 0
        var status = TickStatus.Running
        while (status == TickStatus.Running && ticks < 2000) {
            val frame = Frame(canvas.columns, canvas.rows)
            status = effect.tick(frame)
            ticks++
        }
        assertEquals(TickStatus.Complete, status)
        assertTrue(ticks > 5, "Matrix effect should run for multiple ticks")
    }

    @Test
    fun rainEffectCompletesDeterministically() {
        val canvas = Canvas(8, 4)
        val text = "KOTLIN"
        val input = canvas.ingest(text)
        val effect = RainEffect(
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
        assertTrue(ticks > 5, "Rain effect should run for multiple ticks")
    }

    @Test
    fun smokeEffectCompletesDeterministically() {
        val canvas = Canvas(8, 4)
        val text = "KOTLIN"
        val input = canvas.ingest(text)
        val effect = SmokeEffect(
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
        assertTrue(ticks > 5, "Smoke effect should run for multiple ticks")
    }

    @Test
    fun spotlightsEffectCompletesDeterministically() {
        val canvas = Canvas(8, 4)
        val text = "KOTLIN"
        val input = canvas.ingest(text)
        val effect = SpotlightsEffect(
            configuration = EffectConfiguration(text = text, seed = 42u),
            canvas = canvas,
            input = input,
            seed = 42u,
            options = SpotlightsEffect.Configuration(searchDuration = 10)
        )

        var ticks = 0
        var status = TickStatus.Running
        while (status == TickStatus.Running && ticks < 2000) {
            val frame = Frame(canvas.columns, canvas.rows)
            status = effect.tick(frame)
            ticks++
        }
        assertEquals(TickStatus.Complete, status)
        assertTrue(ticks > 5, "Spotlights effect should run for multiple ticks")
    }

    @Test
    fun sweepEffectCompletesDeterministically() {
        val canvas = Canvas(8, 4)
        val text = "KOTLIN"
        val input = canvas.ingest(text)
        val effect = SweepEffect(
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
        assertTrue(ticks > 5, "Sweep effect should run for multiple ticks")
    }

    @Test
    fun synthGridEffectCompletesDeterministically() {
        val canvas = Canvas(8, 4)
        val text = "KOTLIN"
        val input = canvas.ingest(text)
        val effect = SynthGridEffect(
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
        assertTrue(ticks > 5, "SynthGrid effect should run for multiple ticks")
    }

    @Test
    fun thunderstormEffectCompletesDeterministically() {
        val canvas = Canvas(8, 4)
        val text = "KOTLIN"
        val input = canvas.ingest(text)
        val effect = ThunderstormEffect(
            configuration = EffectConfiguration(text = text, seed = 42u),
            canvas = canvas,
            input = input,
            seed = 42u,
            options = ThunderstormEffect.Configuration(stormTime = 1)
        )

        var ticks = 0
        var status = TickStatus.Running
        while (status == TickStatus.Running && ticks < 2000) {
            val frame = Frame(canvas.columns, canvas.rows)
            status = effect.tick(frame)
            ticks++
        }
        assertEquals(TickStatus.Complete, status)
        assertTrue(ticks > 5, "Thunderstorm effect should run for multiple ticks")
    }

    @Test
    fun vhsTapeEffectCompletesDeterministically() {
        val canvas = Canvas(8, 4)
        val text = "KOTLIN"
        val input = canvas.ingest(text)
        val effect = VHSTapeEffect(
            configuration = EffectConfiguration(text = text, seed = 42u),
            canvas = canvas,
            input = input,
            seed = 42u,
            options = VHSTapeEffect.Configuration(totalGlitchTime = 20)
        )

        var ticks = 0
        var status = TickStatus.Running
        while (status == TickStatus.Running && ticks < 2000) {
            val frame = Frame(canvas.columns, canvas.rows)
            status = effect.tick(frame)
            ticks++
        }
        assertEquals(TickStatus.Complete, status)
        assertTrue(ticks > 5, "VHSTape effect should run for multiple ticks")
    }

    @Test
    fun wavesEffectCompletesDeterministically() {
        val canvas = Canvas(8, 4)
        val text = "KOTLIN"
        val input = canvas.ingest(text)
        val effect = WavesEffect(
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
        assertTrue(ticks > 5, "Waves effect should run for multiple ticks")
    }

    @Test
    fun wipeEffectCompletesDeterministically() {
        val canvas = Canvas(8, 4)
        val text = "KOTLIN"
        val input = canvas.ingest(text)
        val effect = WipeEffect(
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
        assertTrue(ticks > 5, "Wipe effect should run for multiple ticks")
    }

    @Test
    fun deterministicRepeatabilityForBatch4Effects() {
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

        val run1 = runEffect { RainEffect(EffectConfiguration(text = text, seed = 123u), canvas, input, 123u) }
        val run2 = runEffect { RainEffect(EffectConfiguration(text = text, seed = 123u), canvas, input, 123u) }
        assertEquals(run1.size, run2.size)
        for (i in run1.indices) {
            assertEquals(String(run1[i], Charsets.UTF_8), String(run2[i], Charsets.UTF_8), "Run mismatch at frame $i")
        }
    }

    @Test
    fun effectRegistryContainsAll37Effects() {
        assertEquals(37, EffectRegistry.allEffects.size)
        assertEquals(37, EffectRegistry.names.size)
        assertEquals(37, EffectRegistry.allEffects.distinct().size)

        for (name in EffectRegistry.allEffects) {
            assertTrue(EffectRegistry.contains(name), "EffectRegistry must contain '$name'")
            assertTrue(EffectRegistry.contains(name.uppercase()), "EffectRegistry must contain uppercase '$name'")
        }

        assertTrue(!EffectRegistry.contains("unknown_effect"), "EffectRegistry must not contain unknown effect")
    }

    @Test
    fun effectRegistryCanInstantiateAll37Effects() {
        val canvas = Canvas(8, 4)
        val text = "TEST"
        val input = canvas.ingest(text)
        val config = EffectConfiguration(text = text, seed = 42u)

        for (name in EffectRegistry.allEffects) {
            val effect = EffectRegistry.create(name, config, canvas, input, 42u)
            assertNotNull(effect, "EffectRegistry failed to create effect '$name'")

            val frame = Frame(canvas.columns, canvas.rows)
            val status = effect.tick(frame)
            assertTrue(
                status == TickStatus.Running || status == TickStatus.Complete,
                "Effect '$name' should return Running or Complete on first tick"
            )
        }

        assertNull(
            EffectRegistry.create("nonexistent", config, canvas, input, 42u),
            "Nonexistent effect must return null"
        )
    }

    private fun ByteArray.indexOf(target: Byte, start: Int): Int {
        for (i in start until size) {
            if (this[i] == target) return i
        }
        return -1
    }
}
