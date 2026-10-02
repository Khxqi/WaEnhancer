package com.wmods.wppenhacer.xposed.runtime

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Build
import android.view.ViewGroup
import android.view.Gravity
import android.widget.FrameLayout
import com.wmods.wppenhacer.ui.glass.GlassBackend
import com.wmods.wppenhacer.ui.glass.GlassCapabilities
import com.wmods.wppenhacer.ui.glass.GlassRuntimeSnapshot
import com.wmods.wppenhacer.ui.glass.GlassRuntimeState
import com.wmods.wppenhacer.ui.glass.GlassStyle
import com.wmods.wppenhacer.ui.glass.LocalizedBackdropGlassView
import com.wmods.wppenhacer.ui.glass.LocalizedGlassMetrics
import java.util.WeakHashMap

object GlassFeaturePolicy {
    fun enabled(config: RuntimeConfigSnapshot): Boolean =
        config.enableLiquidGlassPrototype &&
            !config.disableAllHooks &&
            !config.disableVisualModifications &&
            !config.safeMode &&
            config.transportStatus != ConfigTransportStatus.FAILED_CLOSED
}

object GlassFeatureRegistry {
    val PLATFORM = CapabilityId("visual.glass.platform")
    val LOCALIZED_SAME_WINDOW = CapabilityId("visual.glass.localized-same-window")

    fun resolve(capabilities: CapabilityRegistry, application: Application) {
        val platform = capabilities.resolve(PLATFORM) { GlassCapabilities.detect(application) }
        platform?.let { detected ->
            GlassRuntimeState.update(GlassRuntimeSnapshot(capabilities = detected))
            capabilities.resolve(LOCALIZED_SAME_WINDOW) {
                check(detected.localizedSameWindowAvailable) {
                    "localized same-window sampling requires Android 13 RuntimeShader and RenderNode effects"
                }
                detected
            }
        }
    }

    fun register(
        registry: FeatureRegistry,
        capabilities: CapabilityRegistry,
        application: Application,
        config: RuntimeConfigSnapshot
    ) {
        registry.register(
            FeatureSpec(
                id = FeatureId("visual.experimental.liquid-glass-prototype"),
                diagnosticName = "Liquid Glass feasibility prototype",
                category = FeatureCategory.VISUAL,
                requiredCapabilities = setOf(LOCALIZED_SAME_WINDOW),
                enabled = { GlassFeaturePolicy.enabled(config) },
                installer = {
                    val platform = requireNotNull(capabilities.get<GlassCapabilities>(LOCALIZED_SAME_WINDOW))
                    val controller = GlassPrototypeController(application, platform)
                    controller.start()
                    register("glass.activity-lifecycle", object : HookHandle {
                        override fun unhook() = controller.stop()
                    })
                }
            )
        )
    }
}

private class GlassPrototypeController(
    private val application: Application,
    private val capabilities: GlassCapabilities
) : Application.ActivityLifecycleCallbacks {
    private val surfaces = WeakHashMap<Activity, LocalizedBackdropGlassView>()

    fun start() {
        application.registerActivityLifecycleCallbacks(this)
        GlassRuntimeState.update(GlassRuntimeState.snapshot().copy(backend = null, attachedSurfaces = 0, failure = null))
    }

    fun stop() {
        application.unregisterActivityLifecycleCallbacks(this)
        surfaces.entries.toList().forEach { (activity, _) -> detach(activity) }
        surfaces.clear()
        GlassRuntimeState.update(GlassRuntimeState.snapshot().copy(backend = null, attachedSurfaces = 0, failure = null))
    }

    override fun onActivityResumed(activity: Activity) {
        if (surfaces.containsKey(activity)) return
        activity.window.decorView.post { attach(activity) }
    }

    override fun onActivityPaused(activity: Activity) = detach(activity)
    override fun onActivityDestroyed(activity: Activity) = detach(activity)
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit

    private fun attach(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed || surfaces.containsKey(activity)) return
        if (Build.VERSION.SDK_INT < 33 || !capabilities.localizedSameWindowAvailable) {
            recordFailure(UnsupportedOperationException("localized same-window sampled backend unavailable"))
            return
        }
        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
        if (contentRoot !is FrameLayout) {
            recordFailure(IllegalStateException("activity content root is not a FrameLayout"))
            return
        }
        val density = activity.resources.displayMetrics.density
        val style = GlassStyle(
            blurRadiusPx = 22f * density,
            refractionStrengthPx = 8f * density,
            tintColor = 0xFFDDEBFF.toInt(),
            tintOpacity = 0.16f,
            edgeIntensity = 0.64f,
            highlightIntensity = 0.42f,
            depth = 0.78f
        )
        lateinit var view: LocalizedBackdropGlassView
        view = LocalizedBackdropGlassView(
            context = activity,
            hostRoot = contentRoot,
            style = style,
            onMetrics = { metrics -> recordMetrics(metrics) },
            onFailure = { throwable ->
                activity.runOnUiThread {
                    if (surfaces[activity] === view) detach(activity)
                    recordFailure(throwable)
                }
            }
        )
        val params = FrameLayout.LayoutParams(
            (164 * density).toInt(),
            (58 * density).toInt(),
            Gravity.TOP or Gravity.END
        ).apply {
            topMargin = (96 * density).toInt()
            marginEnd = (18 * density).toInt()
        }
        try {
            contentRoot.addView(view, params)
            surfaces[activity] = view
            GlassRuntimeState.update(
                GlassRuntimeState.snapshot().copy(
                    capabilities = capabilities,
                    backend = null,
                    attachedSurfaces = surfaces.size,
                    hardwareAccelerated = view.isHardwareAccelerated,
                    failure = null
                )
            )
        } catch (throwable: Throwable) {
            runCatching { contentRoot.removeView(view) }
            recordFailure(throwable)
        }
    }

    private fun recordMetrics(metrics: LocalizedGlassMetrics) {
        val previous = GlassRuntimeState.snapshot()
        GlassRuntimeState.update(
            previous.copy(
                capabilities = capabilities,
                backend = GlassBackend.LOCALIZED_SAME_WINDOW_SAMPLED,
                attachedSurfaces = surfaces.size,
                hardwareAccelerated = metrics.hardwareAccelerated,
                capturedFrames = metrics.capturedFrames,
                lastCaptureMs = metrics.lastCaptureMs,
                worstCaptureMs = metrics.worstCaptureMs,
                approximateRenderNodeBytes = metrics.approximateRenderNodeBytes,
                failure = null
            )
        )
    }

    private fun recordFailure(throwable: Throwable) {
        val previous = GlassRuntimeState.snapshot()
        GlassRuntimeState.update(
            previous.copy(
                capabilities = capabilities,
                backend = null,
                attachedSurfaces = surfaces.size,
                failure = DiagnosticSanitizer.failureSummary(throwable)
            )
        )
    }

    private fun detach(activity: Activity) {
        surfaces.remove(activity)?.let { surface ->
            runCatching { (surface.parent as? ViewGroup)?.removeView(surface) }
        }
        val previous = GlassRuntimeState.snapshot()
        GlassRuntimeState.update(
            previous.copy(
                backend = if (surfaces.isEmpty()) null else previous.backend,
                attachedSurfaces = surfaces.size
            )
        )
    }
}
