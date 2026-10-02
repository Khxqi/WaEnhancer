package com.wmods.wppenhacer.xposed.runtime

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

class DiagnosticPublicationThrottle(private val minimumIntervalMs: Long) {
    init {
        require(minimumIntervalMs >= 0) { "minimum interval must not be negative" }
    }

    private var lastPublicationMs: Long? = null

    @Synchronized
    fun delayMs(nowMs: Long): Long {
        val last = lastPublicationMs ?: return 0
        return (minimumIntervalMs - (nowMs - last)).coerceAtLeast(0)
    }

    @Synchronized
    fun markPublished(nowMs: Long) {
        lastPublicationMs = nowMs
    }
}

/** Coalesces post-startup diagnostic writes; it is never called from the per-frame hot path. */
class RuntimeDiagnosticsRepublisher(
    context: Context,
    minimumIntervalMs: Long = 500
) {
    private val applicationContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val throttle = DiagnosticPublicationThrottle(minimumIntervalMs)
    private var scheduled = false
    private var latestReason = "glass-state-change"

    fun request(reason: String) {
        val sanitizedReason = DiagnosticSanitizer.sanitize(reason)
        handler.post {
            latestReason = sanitizedReason
            if (scheduled) return@post
            scheduled = true
            val delay = throttle.delayMs(SystemClock.elapsedRealtime())
            handler.postDelayed({ publishPending() }, delay)
        }
    }

    private fun publishPending() {
        scheduled = false
        val publishedAt = SystemClock.elapsedRealtime()
        throttle.markPublished(publishedAt)
        val published = RuntimeDiagnosticsPublisher.publish(applicationContext)
        RuntimeTrace.event(
            if (published) "glass-diagnostics-published" else "glass-diagnostics-publish-failed",
            latestReason
        )
    }
}
