package com.wmods.wppenhacer.ui.glass

import android.graphics.Color
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.Shader

enum class GlassBackend {
    RUNTIME_SHADER_SAMPLED,
    LOCALIZED_SAME_WINDOW_SAMPLED,
    CROSS_WINDOW_BLUR_REJECTED,
    LAYERED_GPU_FALLBACK
}

sealed interface GlassShape {
    data object Rounded : GlassShape
    data object Capsule : GlassShape
    data object Circle : GlassShape
}

data class GlassStyle(
    val blurRadiusPx: Float = 18f,
    val tintColor: Int = Color.WHITE,
    val tintOpacity: Float = 0.14f,
    val refractionStrengthPx: Float = 12f,
    val edgeIntensity: Float = 0.52f,
    val highlightIntensity: Float = 0.34f,
    val cornerRadiusPx: Float = 32f,
    val depth: Float = 0.7f,
    val saturation: Float = 1.08f,
    val animationProgress: Float = 0f
) {
    fun sanitized(): GlassStyle = copy(
        blurRadiusPx = blurRadiusPx.coerceIn(0f, 80f),
        tintOpacity = tintOpacity.coerceIn(0f, 0.75f),
        refractionStrengthPx = refractionStrengthPx.coerceIn(0f, 40f),
        edgeIntensity = edgeIntensity.coerceIn(0f, 1f),
        highlightIntensity = highlightIntensity.coerceIn(0f, 1f),
        cornerRadiusPx = cornerRadiusPx.coerceAtLeast(0f),
        depth = depth.coerceIn(0f, 1f),
        saturation = saturation.coerceIn(0.5f, 1.5f),
        animationProgress = animationProgress.coerceIn(0f, 1f)
    )
}

data class GlassSurface(
    val shape: GlassShape,
    val style: GlassStyle
) {
    fun draw(renderer: GlassRenderer, canvas: Canvas, bounds: RectF, backdrop: Shader? = null) {
        renderer.draw(canvas, bounds, shape, style, backdrop)
    }
}
