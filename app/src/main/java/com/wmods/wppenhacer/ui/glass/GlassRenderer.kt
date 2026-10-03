package com.wmods.wppenhacer.ui.glass

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.min

interface GlassRenderer {
    val backend: GlassBackend

    fun draw(
        canvas: Canvas,
        bounds: RectF,
        shape: GlassShape,
        style: GlassStyle,
        backdrop: Shader? = null
    )
}

object GlassRendererFactory {
    fun create(capabilities: GlassCapabilities, sampledBackdrop: Boolean): GlassRenderer {
        if (sampledBackdrop && capabilities.runtimeShaderAvailable && Build.VERSION.SDK_INT >= 33) {
            return runCatching { RuntimeShaderGlassRenderer() }
                .getOrElse { LayeredGlassRenderer() }
        }
        return LayeredGlassRenderer()
    }
}

class LayeredGlassRenderer : GlassRenderer {
    override val backend = GlassBackend.LAYERED_GPU_FALLBACK
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()

    override fun draw(
        canvas: Canvas,
        bounds: RectF,
        shape: GlassShape,
        style: GlassStyle,
        backdrop: Shader?
    ) {
        val safe = style.sanitized()
        val radius = radius(bounds, shape, safe.cornerRadiusPx)
        path.rewind()
        path.addRoundRect(bounds, radius, radius, Path.Direction.CW)
        fill.shader = LinearGradient(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            intArrayOf(
                withAlpha(safe.tintColor, safe.tintOpacity + safe.highlightIntensity * 0.12f),
                withAlpha(safe.tintColor, safe.tintOpacity * 0.72f),
                withAlpha(safe.tintColor, safe.tintOpacity + safe.depth * 0.05f)
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, fill)

        edge.strokeWidth = 1.2f + safe.depth * 1.8f
        edge.shader = LinearGradient(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            intArrayOf(
                withAlpha(0xFFFFFFFF.toInt(), safe.edgeIntensity),
                withAlpha(0xFFFFFFFF.toInt(), safe.edgeIntensity * 0.08f),
                withAlpha(0xFF000000.toInt(), safe.edgeIntensity * 0.18f)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, edge)
        fill.shader = null
        edge.shader = null
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)
}

@RequiresApi(33)
class RuntimeShaderGlassRenderer : GlassRenderer {
    override val backend = GlassBackend.RUNTIME_SHADER_SAMPLED
    private val shader = RuntimeShader(GlassShaderProgram.SAMPLED)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = this@RuntimeShaderGlassRenderer.shader }
    private val path = Path()
    private val fallback = LayeredGlassRenderer()

    override fun draw(
        canvas: Canvas,
        bounds: RectF,
        shape: GlassShape,
        style: GlassStyle,
        backdrop: Shader?
    ) {
        if (backdrop == null) return fallback.draw(canvas, bounds, shape, style)
        shader.setInputShader("backdrop", backdrop)
        configureGlassShader(shader, bounds, style, shape = shape)

        val radius = radius(bounds, shape, style.sanitized().cornerRadiusPx)
        path.rewind()
        path.addRoundRect(bounds, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(path)
        canvas.drawRect(bounds, paint)
        canvas.restore()
    }

}

internal object GlassShaderProgram {
    // Independently authored AGSL. It samples a caller-owned Shader; it never captures the screen.
    const val SAMPLED = """
            uniform shader backdrop;
            uniform float2 center;
            uniform float2 halfSize;
            uniform float blurRadius;
            uniform float refraction;
            uniform float tintOpacity;
            uniform float edgeIntensity;
            uniform float highlightIntensity;
            uniform float depth;
            uniform float saturation;
            uniform float progress;
            uniform float capsuleGeometry;
            uniform float backdropAlreadyBlurred;
            layout(color) uniform half4 tint;

            half4 sampleSoft(float2 p, float radius) {
                float r = min(radius * 0.55, 32.0);
                half4 c = backdrop.eval(p) * 0.24;
                c += backdrop.eval(p + float2(r, 0.0)) * 0.12;
                c += backdrop.eval(p - float2(r, 0.0)) * 0.12;
                c += backdrop.eval(p + float2(0.0, r)) * 0.12;
                c += backdrop.eval(p - float2(0.0, r)) * 0.12;
                c += backdrop.eval(p + float2(r * 0.7, r * 0.7)) * 0.07;
                c += backdrop.eval(p + float2(-r * 0.7, r * 0.7)) * 0.07;
                c += backdrop.eval(p + float2(r * 0.7, -r * 0.7)) * 0.07;
                c += backdrop.eval(p + float2(-r * 0.7, -r * 0.7)) * 0.07;
                return c;
            }

            half4 main(float2 p) {
                float2 local = p - center;
                float halfSegment = max(halfSize.x - halfSize.y, 0.0);
                float2 nearest = float2(clamp(local.x, -halfSegment, halfSegment), 0.0);
                float2 radial = local - nearest;
                float radialLength = max(length(radial), 0.001);
                float capsuleEdge = clamp(radialLength / max(halfSize.y, 1.0), 0.0, 1.0);
                float2 capsuleNormal = radial / radialLength;
                float2 rectangular = local / max(halfSize, float2(1.0));
                float rectangularEdge = clamp(max(abs(rectangular.x), abs(rectangular.y)), 0.0, 1.0);
                float2 rectangularNormal = normalize(rectangular + float2(0.0001, 0.0001));
                float edge = mix(rectangularEdge, capsuleEdge, capsuleGeometry);
                float2 n = normalize(mix(rectangularNormal, capsuleNormal, capsuleGeometry));
                // Production Home glass uses native RenderEffect blur first. Keep the broad
                // center spatially stable; lensing belongs only to the material boundary.
                float lens = smoothstep(0.86, 1.0, edge);
                float pulse = 0.92 + 0.08 * sin(progress * 6.2831853);
                float2 refracted = p - n * refraction * lens * lens * lens * pulse;
                half4 color = mix(
                    sampleSoft(refracted, blurRadius * (0.72 + 0.20 * depth)),
                    backdrop.eval(refracted),
                    half(backdropAlreadyBlurred)
                );
                half luminance = dot(color.rgb, half3(0.2126, 0.7152, 0.0722));
                color.rgb = mix(half3(luminance), color.rgb, half(saturation));
                color = mix(color, tint, half(tintOpacity));
                float rim = smoothstep(0.86, 1.0, edge);
                float light = pow(max(0.0, dot(n, normalize(float2(-0.62, -0.78)))), 3.0);
                float upper = 1.0 - smoothstep(-0.95, 0.35, n.y);
                float lower = smoothstep(0.20, 0.95, n.y);
                color.rgb += half3(rim * edgeIntensity * 0.10);
                color.rgb += half3(rim * upper * highlightIntensity * 0.34);
                color.rgb += half3(rim * light * highlightIntensity * 0.22);
                color.rgb *= half(1.0 - rim * lower * depth * 0.055);
                color.a = 1.0;
                return color;
            }
    """
}

@RequiresApi(33)
internal fun configureGlassShader(
    shader: RuntimeShader,
    bounds: RectF,
    style: GlassStyle,
    animationProgress: Float = style.animationProgress,
    shape: GlassShape = GlassShape.Rounded,
    backdropAlreadyBlurred: Boolean = false
) {
    val safe = style.sanitized()
    shader.setFloatUniform("center", bounds.centerX(), bounds.centerY())
    shader.setFloatUniform("halfSize", bounds.width() / 2f, bounds.height() / 2f)
    shader.setFloatUniform("blurRadius", safe.blurRadiusPx)
    shader.setFloatUniform("refraction", safe.refractionStrengthPx)
    shader.setFloatUniform("tintOpacity", safe.tintOpacity)
    shader.setFloatUniform("edgeIntensity", safe.edgeIntensity)
    shader.setFloatUniform("highlightIntensity", safe.highlightIntensity)
    shader.setFloatUniform("depth", safe.depth)
    shader.setFloatUniform("saturation", safe.saturation)
    shader.setFloatUniform("progress", animationProgress.coerceIn(0f, 1f))
    shader.setFloatUniform(
        "capsuleGeometry",
        if (shape == GlassShape.Capsule || shape == GlassShape.Circle) 1f else 0f
    )
    shader.setFloatUniform("backdropAlreadyBlurred", if (backdropAlreadyBlurred) 1f else 0f)
    shader.setColorUniform("tint", safe.tintColor)
}

private fun radius(bounds: RectF, shape: GlassShape, requested: Float): Float = when (shape) {
    GlassShape.Circle -> min(bounds.width(), bounds.height()) / 2f
    GlassShape.Capsule -> bounds.height() / 2f
    GlassShape.Rounded -> requested.coerceAtMost(min(bounds.width(), bounds.height()) / 2f)
}
