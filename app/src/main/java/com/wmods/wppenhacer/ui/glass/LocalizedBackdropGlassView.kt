package com.wmods.wppenhacer.ui.glass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.annotation.RequiresApi

data class LocalizedGlassMetrics(
    val capturedFrames: Long,
    val lastCaptureMs: Double,
    val worstCaptureMs: Double,
    val approximateRenderNodeBytes: Long,
    val hardwareAccelerated: Boolean
)

/**
 * Records the host hierarchy into a retained hardware display list, translated and clipped to this
 * view's bounds plus a small sampling margin. The view excludes itself while that display list is
 * recorded, so replay cannot recursively sample the previous glass result.
 *
 * This is an intentionally bounded feasibility path: it allocates no frame bitmaps and never uses
 * PixelCopy, but View.draw still traverses the host hierarchy once per recorded frame. Device frame
 * and memory measurements are therefore required before this can be considered production-ready.
 */
@RequiresApi(33)
class LocalizedBackdropGlassView(
    context: Context,
    private val hostRoot: ViewGroup,
    private val style: GlassStyle,
    private val onMetrics: (LocalizedGlassMetrics) -> Unit,
    private val onFailure: (Throwable) -> Unit
) : View(context), ViewTreeObserver.OnPreDrawListener {
    private val density = resources.displayMetrics.density
    private val samplePadding = (36f * density).toInt()
    private val renderNode = RenderNode("WaEnhancerLocalizedGlass")
    private val runtimeShader = RuntimeShader(GlassShaderProgram.SAMPLED)
    private val renderEffect = RenderEffect.createRuntimeShaderEffect(runtimeShader, "backdrop")
    private val surfaceBounds = RectF()
    private val shaderBounds = RectF()
    private val clipPath = Path()
    private val hostLocation = IntArray(2)
    private val surfaceLocation = IntArray(2)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 12f * resources.displayMetrics.scaledDensity
        isFakeBoldText = true
        letterSpacing = 0.08f
        setShadowLayer(2f, 0f, 1f, 0x88000000.toInt())
    }

    private var recordingBackdrop = false
    private var recordingFailed = false
    private var capturedFrames = 0L
    private var lastCaptureMs = 0.0
    private var worstCaptureMs = 0.0
    private var nodeWidth = 0
    private var nodeHeight = 0

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        renderNode.setClipToBounds(true)
        renderNode.setRenderEffect(renderEffect)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        hostRoot.viewTreeObserver.addOnPreDrawListener(this)
    }

    override fun onDetachedFromWindow() {
        if (hostRoot.viewTreeObserver.isAlive) {
            hostRoot.viewTreeObserver.removeOnPreDrawListener(this)
        }
        renderNode.discardDisplayList()
        super.onDetachedFromWindow()
    }

    override fun onPreDraw(): Boolean {
        if (!recordingFailed && isShown && width > 0 && height > 0) {
            try {
                recordLocalizedBackdrop()
                postInvalidateOnAnimation()
            } catch (throwable: Throwable) {
                recordingFailed = true
                onFailure(throwable)
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        if (recordingBackdrop || recordingFailed || !renderNode.hasDisplayList()) return

        surfaceBounds.set(0f, 0f, width.toFloat(), height.toFloat())
        clipPath.rewind()
        clipPath.addRoundRect(
            surfaceBounds,
            surfaceBounds.height() / 2f,
            surfaceBounds.height() / 2f,
            Path.Direction.CW
        )
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.translate(-samplePadding.toFloat(), -samplePadding.toFloat())
        canvas.drawRenderNode(renderNode)
        canvas.restore()
        canvas.drawText(
            "GLASS PROTOTYPE",
            surfaceBounds.centerX(),
            surfaceBounds.centerY() + labelPaint.textSize * 0.35f,
            labelPaint
        )
    }

    private fun recordLocalizedBackdrop() {
        if (!isHardwareAccelerated) {
            throw IllegalStateException("localized glass requires hardware acceleration")
        }

        val requiredWidth = width + samplePadding * 2
        val requiredHeight = height + samplePadding * 2
        if (requiredWidth != nodeWidth || requiredHeight != nodeHeight) {
            nodeWidth = requiredWidth
            nodeHeight = requiredHeight
            renderNode.setPosition(0, 0, nodeWidth, nodeHeight)
        }

        hostRoot.getLocationInWindow(hostLocation)
        getLocationInWindow(surfaceLocation)
        val surfaceX = surfaceLocation[0] - hostLocation[0]
        val surfaceY = surfaceLocation[1] - hostLocation[1]
        shaderBounds.set(
            samplePadding.toFloat(),
            samplePadding.toFloat(),
            (samplePadding + width).toFloat(),
            (samplePadding + height).toFloat()
        )
        configureGlassShader(
            runtimeShader,
            shaderBounds,
            style,
            animationProgress = (SystemClock.uptimeMillis() % 4000L) / 4000f
        )

        val started = System.nanoTime()
        val recordingCanvas = renderNode.beginRecording(nodeWidth, nodeHeight)
        try {
            recordingCanvas.clipRect(0f, 0f, nodeWidth.toFloat(), nodeHeight.toFloat())
            recordingCanvas.translate(
                (samplePadding - surfaceX).toFloat(),
                (samplePadding - surfaceY).toFloat()
            )
            recordingBackdrop = true
            hostRoot.draw(recordingCanvas)
        } finally {
            recordingBackdrop = false
            renderNode.endRecording()
        }

        capturedFrames++
        lastCaptureMs = (System.nanoTime() - started) / 1_000_000.0
        if (lastCaptureMs > worstCaptureMs) worstCaptureMs = lastCaptureMs
        if (capturedFrames == 1L || capturedFrames % 60L == 0L) {
            onMetrics(
                LocalizedGlassMetrics(
                    capturedFrames = capturedFrames,
                    lastCaptureMs = lastCaptureMs,
                    worstCaptureMs = worstCaptureMs,
                    approximateRenderNodeBytes = renderNode.computeApproximateMemoryUsage(),
                    hardwareAccelerated = true
                )
            )
        }
    }
}
