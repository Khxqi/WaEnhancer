package com.wmods.wppenhacer.ui.home

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.min

/** Independently drawn Phase-5A icons. No host, Apple, or MBWhatsApp artwork is copied. */
class Phase5NavigationIconDrawable(
    private val glyph: HomeNavigationGlyph,
    color: Int
) : Drawable() {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val rect = RectF()

    var color: Int = color
        set(value) {
            field = value
            invalidateSelf()
        }

    override fun draw(canvas: Canvas) {
        val scale = min(bounds.width(), bounds.height()) / VIEWPORT
        if (scale <= 0f) return
        val save = canvas.save()
        canvas.translate(bounds.exactCenterX() - VIEWPORT * scale / 2f,
            bounds.exactCenterY() - VIEWPORT * scale / 2f)
        canvas.scale(scale, scale)
        stroke.color = color
        fill.color = color
        stroke.strokeWidth = 2.15f
        when (glyph) {
            HomeNavigationGlyph.STATUS_RING -> drawStatus(canvas)
            HomeNavigationGlyph.HANDSET -> drawCalls(canvas)
            HomeNavigationGlyph.THREE_PEOPLE -> drawCommunities(canvas)
            HomeNavigationGlyph.OVERLAPPING_BUBBLES -> drawChats(canvas)
            HomeNavigationGlyph.PROFILE_PLACEHOLDER -> drawProfilePlaceholder(canvas)
        }
        canvas.restoreToCount(save)
    }

    private fun drawStatus(canvas: Canvas) {
        canvas.drawCircle(16f, 16f, 7.2f, stroke)
        rect.set(4.1f, 4.1f, 27.9f, 27.9f)
        canvas.drawArc(rect, -74f, 126f, false, stroke)
        canvas.drawArc(rect, 80f, 115f, false, stroke)
        canvas.drawArc(rect, 222f, 82f, false, stroke)
    }

    private fun drawCalls(canvas: Canvas) {
        path.rewind()
        path.moveTo(10.1f, 5.2f)
        path.cubicTo(8.3f, 4.7f, 6.5f, 5.7f, 5.8f, 7.5f)
        path.cubicTo(4.7f, 10.4f, 6.6f, 15.3f, 10.9f, 19.7f)
        path.cubicTo(15.3f, 24.1f, 20.2f, 26.1f, 23.2f, 25.0f)
        path.cubicTo(25.0f, 24.3f, 26.0f, 22.5f, 25.5f, 20.7f)
        path.lineTo(22.9f, 17.8f)
        path.cubicTo(22.2f, 17.0f, 21.0f, 16.8f, 20.1f, 17.4f)
        path.lineTo(17.8f, 18.9f)
        path.cubicTo(15.7f, 17.8f, 14.1f, 16.3f, 13.0f, 14.2f)
        path.lineTo(14.5f, 11.9f)
        path.cubicTo(15.1f, 11.0f, 14.9f, 9.8f, 14.1f, 9.1f)
        path.close()
        canvas.drawPath(path, stroke)
    }

    private fun drawCommunities(canvas: Canvas) {
        canvas.drawCircle(16f, 8.2f, 3.5f, stroke)
        canvas.drawCircle(7.7f, 11.2f, 2.7f, stroke)
        canvas.drawCircle(24.3f, 11.2f, 2.7f, stroke)
        path.rewind()
        path.moveTo(9.2f, 26.1f)
        path.cubicTo(9.5f, 20.8f, 12.0f, 17.9f, 16f, 17.9f)
        path.cubicTo(20.0f, 17.9f, 22.5f, 20.8f, 22.8f, 26.1f)
        path.close()
        canvas.drawPath(path, stroke)
        path.rewind()
        path.moveTo(2.8f, 23.6f)
        path.cubicTo(3.1f, 19.5f, 5.1f, 17.2f, 8.1f, 17.2f)
        path.moveTo(29.2f, 23.6f)
        path.cubicTo(28.9f, 19.5f, 26.9f, 17.2f, 23.9f, 17.2f)
        canvas.drawPath(path, stroke)
    }

    private fun drawChats(canvas: Canvas) {
        rect.set(10.1f, 5.8f, 28.1f, 19.5f)
        canvas.drawRoundRect(rect, 6.5f, 6.5f, stroke)
        path.rewind()
        path.moveTo(23.2f, 18.2f)
        path.lineTo(25.7f, 22.0f)
        path.lineTo(20.8f, 19.1f)
        canvas.drawPath(path, stroke)

        rect.set(3.9f, 11.5f, 23.5f, 26.2f)
        canvas.drawRoundRect(rect, 7f, 7f, stroke)
        path.rewind()
        path.moveTo(9.2f, 24.7f)
        path.lineTo(6.4f, 28.2f)
        path.lineTo(11.6f, 25.5f)
        canvas.drawPath(path, stroke)
    }

    private fun drawProfilePlaceholder(canvas: Canvas) {
        fill.alpha = 64
        canvas.drawCircle(16f, 16f, 14.5f, fill)
        fill.alpha = 255
        canvas.drawCircle(16f, 11.2f, 4.2f, fill)
        path.rewind()
        path.moveTo(8.2f, 25.1f)
        path.cubicTo(8.8f, 19.5f, 11.7f, 17.1f, 16f, 17.1f)
        path.cubicTo(20.3f, 17.1f, 23.2f, 19.5f, 23.8f, 25.1f)
        path.close()
        canvas.drawPath(path, fill)
    }

    override fun setAlpha(alpha: Int) {
        stroke.alpha = alpha
        fill.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        stroke.colorFilter = colorFilter
        fill.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = 32
    override fun getIntrinsicHeight(): Int = 32

    private companion object {
        const val VIEWPORT = 32f
    }
}
