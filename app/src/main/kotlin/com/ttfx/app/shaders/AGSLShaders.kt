package com.ttfx.app.shaders

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import org.intellij.lang.annotations.Language

/**
 * Collection of Android AGSL (Android Graphics Shading Language) post-processing shaders.
 * Available on Android 13+ (API 33+).
 */
object AGSLShaders {

    @Language("AGSL")
    const val CRT_SCANLINES = """
        uniform shader composable;
        uniform float2 resolution;
        uniform float time;
        uniform float scanlineDensity;
        uniform float scanlineIntensity;
        uniform float curvature;

        vec2 curve(vec2 uv) {
            uv = (uv - 0.5) * 2.0;
            uv *= 1.1;
            uv.x *= 1.0 + pow((abs(uv.y) / 5.0), curvature);
            uv.y *= 1.0 + pow((abs(uv.x) / 4.0), curvature);
            uv  = (uv / 2.0) + 0.5;
            return uv;
        }

        half4 main(float2 fragCoord) {
            vec2 uv = fragCoord / resolution;
            vec2 curvedUv = curve(uv);

            if (curvedUv.x < 0.0 || curvedUv.x > 1.0 || curvedUv.y < 0.0 || curvedUv.y > 1.0) {
                return half4(0.0, 0.0, 0.0, 1.0);
            }

            half4 color = composable.eval(curvedUv * resolution);

            // Scanline oscillation
            float scanline = sin(curvedUv.y * resolution.y * scanlineDensity + time * 2.0);
            scanline = (scanline + 1.0) * 0.5;
            color.rgb -= color.rgb * scanline * scanlineIntensity;

            // Vignette edge shading
            float vignette = curvedUv.x * curvedUv.y * (1.0 - curvedUv.x) * (1.0 - curvedUv.y);
            color.rgb *= clamp(16.0 * vignette, 0.0, 1.0);

            return color;
        }
    """

    @Language("AGSL")
    const val CHROMATIC_GLITCH = """
        uniform shader composable;
        uniform float2 resolution;
        uniform float amount;

        half4 main(float2 fragCoord) {
            vec2 uv = fragCoord / resolution;
            float split = amount * 0.015;

            half4 r = composable.eval(float2(fragCoord.x - split * resolution.x, fragCoord.y));
            half4 g = composable.eval(fragCoord);
            half4 b = composable.eval(float2(fragCoord.x + split * resolution.x, fragCoord.y));

            return half4(r.r, g.g, b.b, g.a);
        }
    """

    @Language("AGSL")
    const val RETRO_BLOOM = """
        uniform shader composable;
        uniform float2 resolution;
        uniform float intensity;

        half4 main(float2 fragCoord) {
            half4 base = composable.eval(fragCoord);
            float step = 2.0;

            half4 blur = half4(0.0);
            blur += composable.eval(fragCoord + float2(-step, -step));
            blur += composable.eval(fragCoord + float2(0.0, -step));
            blur += composable.eval(fragCoord + float2(step, -step));
            blur += composable.eval(fragCoord + float2(-step, 0.0));
            blur += composable.eval(fragCoord + float2(step, 0.0));
            blur += composable.eval(fragCoord + float2(-step, step));
            blur += composable.eval(fragCoord + float2(0.0, step));
            blur += composable.eval(fragCoord + float2(step, step));
            blur /= 8.0;

            return base + blur * intensity;
        }
    """
}

enum class ShaderEffectMode {
    NONE, CRT_SCANLINES, CHROMATIC_GLITCH, RETRO_BLOOM
}

/**
 * Modifier extension applying AGSL post-processing when running on API 33+.
 */
fun Modifier.ttfxShaderEffect(
    mode: ShaderEffectMode,
    time: Float = 0f,
    widthPx: Float = 1080f,
    heightPx: Float = 1920f
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || mode == ShaderEffectMode.NONE) {
        return this
    }

    return when (mode) {
        ShaderEffectMode.CRT_SCANLINES -> this.graphicsLayer {
            val shader = RuntimeShader(AGSLShaders.CRT_SCANLINES).apply {
                setFloatUniform("resolution", size.width, size.height)
                setFloatUniform("time", time)
                setFloatUniform("scanlineDensity", 0.8f)
                setFloatUniform("scanlineIntensity", 0.35f)
                setFloatUniform("curvature", 2.2f)
            }
            renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "composable").asComposeRenderEffect()
        }
        ShaderEffectMode.CHROMATIC_GLITCH -> this.graphicsLayer {
            val shader = RuntimeShader(AGSLShaders.CHROMATIC_GLITCH).apply {
                setFloatUniform("resolution", size.width, size.height)
                setFloatUniform("amount", 1.2f)
            }
            renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "composable").asComposeRenderEffect()
        }
        ShaderEffectMode.RETRO_BLOOM -> this.graphicsLayer {
            val shader = RuntimeShader(AGSLShaders.RETRO_BLOOM).apply {
                setFloatUniform("resolution", size.width, size.height)
                setFloatUniform("intensity", 0.6f)
            }
            renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "composable").asComposeRenderEffect()
        }
        ShaderEffectMode.NONE -> this
    }
}
