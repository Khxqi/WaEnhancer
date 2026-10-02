package com.wmods.wppenhacer.ui.glass

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.view.WindowManager

data class GlassCapabilities(
    val runtimeShaderAvailable: Boolean,
    val renderEffectAvailable: Boolean,
    val localizedSameWindowAvailable: Boolean,
    val crossWindowBlurAvailable: Boolean,
    val highEndGraphics: Boolean
) {
    companion object {
        fun detect(context: Context): GlassCapabilities {
            val activityManager = context.getSystemService(ActivityManager::class.java)
            val windowManager = context.getSystemService(WindowManager::class.java)
            return GlassCapabilities(
                runtimeShaderAvailable = Build.VERSION.SDK_INT >= 33,
                renderEffectAvailable = Build.VERSION.SDK_INT >= 31,
                localizedSameWindowAvailable = Build.VERSION.SDK_INT >= 33,
                crossWindowBlurAvailable = Build.VERSION.SDK_INT >= 31 &&
                    runCatching { windowManager.isCrossWindowBlurEnabled }.getOrDefault(false),
                highEndGraphics = runCatching { !activityManager.isLowRamDevice }.getOrDefault(true)
            )
        }
    }
}

data class GlassRuntimeSnapshot(
    val capabilities: GlassCapabilities? = null,
    val backend: GlassBackend? = null,
    val attachedSurfaces: Int = 0,
    val hardwareAccelerated: Boolean? = null,
    val capturedFrames: Long = 0,
    val lastCaptureMs: Double? = null,
    val worstCaptureMs: Double? = null,
    val approximateRenderNodeBytes: Long? = null,
    val rejectedBackends: Set<GlassBackend> = setOf(GlassBackend.CROSS_WINDOW_BLUR_REJECTED),
    val failure: String? = null
)

object GlassRuntimeState {
    @Volatile private var snapshot = GlassRuntimeSnapshot()

    fun update(value: GlassRuntimeSnapshot) {
        snapshot = value
    }

    fun snapshot(): GlassRuntimeSnapshot = snapshot
}
