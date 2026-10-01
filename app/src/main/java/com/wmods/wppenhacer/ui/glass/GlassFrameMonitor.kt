package com.wmods.wppenhacer.ui.glass

import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import java.util.concurrent.atomic.AtomicLong

data class GlassFrameStats(
    val frames: Long,
    val slowFrames: Long,
    val worstFrameMs: Double,
    val targetFrameMs: Double
)

class GlassFrameMonitor(
    private val window: Window,
    refreshRate: Float
) : Window.OnFrameMetricsAvailableListener {
    private val frameBudgetNanos = (1_000_000_000.0 / refreshRate.coerceAtLeast(30f)).toLong()
    private val frames = AtomicLong()
    private val slowFrames = AtomicLong()
    private val worstNanos = AtomicLong()

    fun start() {
        window.addOnFrameMetricsAvailableListener(this, Handler(Looper.getMainLooper()))
    }

    fun stop() {
        runCatching { window.removeOnFrameMetricsAvailableListener(this) }
    }

    override fun onFrameMetricsAvailable(
        window: Window,
        frameMetrics: FrameMetrics,
        dropCountSinceLastInvocation: Int
    ) {
        val duration = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
        if (duration <= 0) return
        frames.incrementAndGet()
        if (duration > frameBudgetNanos * 3 / 2) slowFrames.incrementAndGet()
        worstNanos.updateAndGet { previous -> maxOf(previous, duration) }
    }

    fun snapshot() = GlassFrameStats(
        frames = frames.get(),
        slowFrames = slowFrames.get(),
        worstFrameMs = worstNanos.get() / 1_000_000.0,
        targetFrameMs = frameBudgetNanos / 1_000_000.0
    )
}
