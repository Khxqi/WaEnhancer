package com.wmods.wppenhacer.ui.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import kotlin.math.sin

class GlassLabView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs), Choreographer.FrameCallback {
    private val capabilities = GlassCapabilities.detect(context)
    private var renderer: GlassRenderer = GlassRendererFactory.create(capabilities, sampledBackdrop = true)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 14f * resources.displayMetrics.scaledDensity
        setShadowLayer(3f, 0f, 1f, 0x99000000.toInt())
    }
    private val matrix = Matrix()
    private var backdropBitmap: Bitmap? = null
    private var backdropShader: BitmapShader? = null
    private var startMs = SystemClock.uptimeMillis()
    private var attached = false

    var style = GlassStyle()
        set(value) {
            field = value.sanitized()
            invalidate()
        }

    var animate = true
        set(value) {
            field = value
            if (value && attached) scheduleFrame()
        }

    var darkBackdrop = false
        set(value) {
            field = value
            buildBackdrop(width, height)
            invalidate()
        }

    var forceFallback = false
        set(value) {
            field = value
            renderer = if (value) LayeredGlassRenderer()
            else GlassRendererFactory.create(capabilities, sampledBackdrop = true)
            invalidate()
        }

    var motionSpeedPxPerSecond = 34f
        set(value) {
            field = value.coerceIn(0f, 320f)
            invalidate()
        }

    val activeBackend: GlassBackend get() = renderer.backend

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        scheduleFrame()
    }

    override fun onDetachedFromWindow() {
        attached = false
        Choreographer.getInstance().removeFrameCallback(this)
        backdropBitmap?.recycle()
        backdropBitmap = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        buildBackdrop(w, h)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!attached || !animate) return
        invalidate()
        scheduleFrame()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val time = (SystemClock.uptimeMillis() - startMs) / 1000f
        val shader = backdropShader
        if (shader != null) {
            matrix.reset()
            matrix.setTranslate(
                if (animate) time * motionSpeedPxPerSecond + sin(time * 0.42f) * width * 0.10f else 0f,
                if (animate) time * motionSpeedPxPerSecond * .46f + sin(time * 0.31f) * height * 0.06f else 0f
            )
            shader.setLocalMatrix(matrix)
            backgroundPaint.shader = shader
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        } else {
            canvas.drawColor(if (darkBackdrop) 0xFF10131A.toInt() else 0xFFE8ECF5.toInt())
        }

        val progress = ((time % 4f) / 4f).coerceIn(0f, 1f)
        val animatedStyle = style.copy(animationProgress = progress)
        val margin = width * 0.07f
        val contentWidth = width - margin * 2f
        drawSurface(canvas, RectF(margin, height * .10f, margin + contentWidth * .58f, height * .23f), GlassShape.Capsule, animatedStyle, "Pill")
        drawSurface(canvas, RectF(width - margin - contentWidth * .30f, height * .10f, width - margin, height * .23f), GlassShape.Rounded, animatedStyle, "Button")
        drawSurface(canvas, RectF(margin, height * .31f, width - margin, height * .58f), GlassShape.Rounded, animatedStyle.copy(cornerRadiusPx = style.cornerRadiusPx * 1.25f), "Floating card")
        drawSurface(canvas, RectF(margin, height * .69f, width - margin - height * .13f, height * .82f), GlassShape.Capsule, animatedStyle, "Bottom tabs")
        val size = height * .13f
        drawSurface(canvas, RectF(width - margin - size, height * .69f, width - margin, height * .69f + size), GlassShape.Circle, animatedStyle, "")
    }

    private fun drawSurface(
        canvas: Canvas,
        bounds: RectF,
        shape: GlassShape,
        style: GlassStyle,
        label: String
    ) {
        GlassSurface(shape, style).draw(renderer, canvas, bounds, backdropShader)
        if (label.isNotEmpty()) {
            canvas.drawText(label, bounds.left + 18f, bounds.centerY() + labelPaint.textSize * .35f, labelPaint)
        }
    }

    private fun buildBackdrop(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        backdropBitmap?.recycle()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val base = if (darkBackdrop) {
            intArrayOf(0xFF0B1220.toInt(), 0xFF241239.toInt(), 0xFF102D33.toInt())
        } else {
            intArrayOf(0xFFF8D6E8.toInt(), 0xFFC9E9FF.toInt(), 0xFFE9F7D0.toInt())
        }
        paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), base, null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
        val colors = intArrayOf(0xFFFF4D8D.toInt(), 0xFF4D8DFF.toInt(), 0xFF3ED2A0.toInt(), 0xFFFFA73D.toInt())
        repeat(14) { index ->
            paint.color = colors[index % colors.size]
            paint.alpha = if (darkBackdrop) 150 else 190
            val x = (index * 137 % w).toFloat()
            val y = (index * 211 % h).toFloat()
            canvas.drawCircle(x, y, (28 + index % 5 * 18) * resources.displayMetrics.density, paint)
        }
        paint.color = if (darkBackdrop) 0x66FFFFFF else 0x55000000
        paint.strokeWidth = resources.displayMetrics.density
        val step = 28f * resources.displayMetrics.density
        var x = 0f
        while (x < w) { canvas.drawLine(x, 0f, x, h.toFloat(), paint); x += step }
        var y = 0f
        while (y < h) { canvas.drawLine(0f, y, w.toFloat(), y, paint); y += step }
        backdropBitmap = bitmap
        backdropShader = BitmapShader(bitmap, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
    }

    private fun scheduleFrame() {
        Choreographer.getInstance().removeFrameCallback(this)
        Choreographer.getInstance().postFrameCallback(this)
    }
}
