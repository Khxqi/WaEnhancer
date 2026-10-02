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

data class LocalizedGlassGeometry(
    val samplingRootWindowX: Int,
    val samplingRootWindowY: Int,
    val surfaceWindowX: Int,
    val surfaceWindowY: Int,
    val surfaceWidth: Int,
    val surfaceHeight: Int
) {
    val surfaceLeftInSamplingRoot: Int
        get() = surfaceWindowX - samplingRootWindowX

    val surfaceTopInSamplingRoot: Int
        get() = surfaceWindowY - samplingRootWindowY
}

data class LocalizedGlassDrawMetrics(
    val onDrawEntryCount: Long,
    val onDrawDuringCaptureCount: Long,
    val normalOnDrawCount: Long,
    val successfulRenderNodeDrawCount: Long
)

enum class LocalizedGlassEvent {
    FIRST_PRE_DRAW,
    FIRST_BACKDROP_RECORDING,
    FIRST_BACKDROP_CAPTURED,
    FIRST_FRAME_RENDERED
}

enum class LocalizedGlassFailureStage {
    BACKDROP_RECORDING,
    RENDER_NODE_DRAW
}

/**
 * Records the host hierarchy into a retained hardware display list, translated and clipped to this
 * view's bounds plus a small sampling margin. The controller attaches this view outside hostRoot's
 * subtree, providing structural exclusion from the sampled display list. recordingBackdrop remains
 * a defensive guard rather than the primary recursion-prevention mechanism.
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
    private val onGeometry: (LocalizedGlassGeometry) -> Unit,
    private val onDrawMetrics: (LocalizedGlassDrawMetrics) -> Unit,
    private val onEvent: (LocalizedGlassEvent) -> Unit,
    private val onFailure: (LocalizedGlassFailureStage, Throwable) -> Unit
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
    private var firstPreDrawReported = false
    private var firstRecordingReported = false
    private var firstCaptureReported = false
    private var firstRenderedReported = false
    private var firstGeometryReported = false
    private var onDrawEntryCount = 0L
    private var onDrawDuringCaptureCount = 0L
    private var normalOnDrawCount = 0L
    private var successfulRenderNodeDrawCount = 0L

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
        publishDrawMetrics()
        renderNode.discardDisplayList()
        super.onDetachedFromWindow()
    }

    override fun onPreDraw(): Boolean {
        if (!firstPreDrawReported) {
            firstPreDrawReported = true
            onEvent(LocalizedGlassEvent.FIRST_PRE_DRAW)
        }
        if (!recordingFailed && isShown && width > 0 && height > 0) {
            try {
                reportGeometryOnce()
                if (!firstRecordingReported) {
                    firstRecordingReported = true
                    onEvent(LocalizedGlassEvent.FIRST_BACKDROP_RECORDING)
                }
                recordLocalizedBackdrop()
                postInvalidateOnAnimation()
            } catch (throwable: Throwable) {
                recordingFailed = true
                onFailure(LocalizedGlassFailureStage.BACKDROP_RECORDING, throwable)
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        onDrawEntryCount++
        if (recordingBackdrop) {
            onDrawDuringCaptureCount++
            if (onDrawDuringCaptureCount == 1L) publishDrawMetrics()
            return
        }
        normalOnDrawCount++
        if (normalOnDrawCount == 1L) publishDrawMetrics()
        if (recordingFailed || !renderNode.hasDisplayList()) return
        try {
            surfaceBounds.set(0f, 0f, width.toFloat(), height.toFloat())
            clipPath.rewind()
            clipPath.addRoundRect(
                surfaceBounds,
                surfaceBounds.height() / 2f,
                surfaceBounds.height() / 2f,
                Path.Direction.CW
            )
            val saveCount = canvas.save()
            try {
                canvas.clipPath(clipPath)
                canvas.translate(-samplePadding.toFloat(), -samplePadding.toFloat())
                canvas.drawRenderNode(renderNode)
                successfulRenderNodeDrawCount++
                if (successfulRenderNodeDrawCount == 60L ||
                    successfulRenderNodeDrawCount == 300L
                ) {
                    publishDrawMetrics()
                }
            } finally {
                canvas.restoreToCount(saveCount)
            }
            canvas.drawText(
                "GLASS PROTOTYPE",
                surfaceBounds.centerX(),
                surfaceBounds.centerY() + labelPaint.textSize * 0.35f,
                labelPaint
            )
            if (!firstRenderedReported) {
                firstRenderedReported = true
                publishDrawMetrics()
                onEvent(LocalizedGlassEvent.FIRST_FRAME_RENDERED)
            }
        } catch (throwable: Throwable) {
            recordingFailed = true
            onFailure(LocalizedGlassFailureStage.RENDER_NODE_DRAW, throwable)
        }
    }

    private fun reportGeometryOnce() {
        if (firstGeometryReported) return
        hostRoot.getLocationInWindow(hostLocation)
        getLocationInWindow(surfaceLocation)
        firstGeometryReported = true
        onGeometry(
            LocalizedGlassGeometry(
                samplingRootWindowX = hostLocation[0],
                samplingRootWindowY = hostLocation[1],
                surfaceWindowX = surfaceLocation[0],
                surfaceWindowY = surfaceLocation[1],
                surfaceWidth = width,
                surfaceHeight = height
            )
        )
    }

    private fun publishDrawMetrics() {
        onDrawMetrics(
            LocalizedGlassDrawMetrics(
                onDrawEntryCount = onDrawEntryCount,
                onDrawDuringCaptureCount = onDrawDuringCaptureCount,
                normalOnDrawCount = normalOnDrawCount,
                successfulRenderNodeDrawCount = successfulRenderNodeDrawCount
            )
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
        if (!firstCaptureReported) {
            firstCaptureReported = true
            onEvent(LocalizedGlassEvent.FIRST_BACKDROP_CAPTURED)
        }
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
