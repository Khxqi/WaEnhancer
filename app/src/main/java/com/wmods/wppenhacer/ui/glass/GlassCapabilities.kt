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
    val lifecycleCallbackRegistered: Boolean = false,
    val lastActivityClass: String? = null,
    val lastLifecycleEvent: String? = null,
    val attachAttempts: Long = 0,
    val successfulAttaches: Long = 0,
    val contentRootClass: String? = null,
    val contentRootIsViewGroup: Boolean? = null,
    val contentRootIsFrameLayout: Boolean? = null,
    val contentRootWidth: Int? = null,
    val contentRootHeight: Int? = null,
    val samplingRootClass: String? = null,
    val samplingRootWidth: Int? = null,
    val samplingRootHeight: Int? = null,
    val surfaceParentClass: String? = null,
    val surfaceParentIsViewGroup: Boolean? = null,
    val surfaceParentIsFrameLayout: Boolean? = null,
    val surfaceParentWidth: Int? = null,
    val surfaceParentHeight: Int? = null,
    val surfaceOutsideSamplingSubtree: Boolean? = null,
    val samplingRootWindowX: Int? = null,
    val samplingRootWindowY: Int? = null,
    val surfaceWindowX: Int? = null,
    val surfaceWindowY: Int? = null,
    val surfaceLeftInSamplingRoot: Int? = null,
    val surfaceTopInSamplingRoot: Int? = null,
    val surfaceWidth: Int? = null,
    val surfaceHeight: Int? = null,
    val firstPreDrawObserved: Boolean = false,
    val firstBackdropRecordingStarted: Boolean = false,
    val firstFrameRendered: Boolean = false,
    val lastRenderStage: String? = null,
    val lastAttachFailure: String? = null,
    val attachedSurfaces: Int = 0,
    val hardwareAccelerated: Boolean? = null,
    val capturedFrames: Long = 0,
    val lastCaptureMs: Double? = null,
    val worstCaptureMs: Double? = null,
    val approximateRenderNodeBytes: Long? = null,
    val onDrawEntryCount: Long = 0,
    val onDrawDuringCaptureCount: Long = 0,
    val normalOnDrawCount: Long = 0,
    val successfulRenderNodeDrawCount: Long = 0,
    val rejectedBackends: Set<GlassBackend> = setOf(GlassBackend.CROSS_WINDOW_BLUR_REJECTED),
    val failure: String? = null
) {
    val runtimeVerified: Boolean
        get() = successfulAttaches > 0 && firstFrameRendered
}

object GlassRuntimeState {
    @Volatile private var snapshot = GlassRuntimeSnapshot()

    @Synchronized
    fun update(value: GlassRuntimeSnapshot) {
        snapshot = value
    }

    @Synchronized
    fun update(transform: (GlassRuntimeSnapshot) -> GlassRuntimeSnapshot) {
        snapshot = transform(snapshot)
    }

    fun snapshot(): GlassRuntimeSnapshot = snapshot
}
