package com.wmods.wppenhacer.xposed.runtime

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.FrameLayout
import com.wmods.wppenhacer.ui.glass.GlassBackend
import com.wmods.wppenhacer.ui.glass.GlassCapabilities
import com.wmods.wppenhacer.ui.glass.GlassRuntimeSnapshot
import com.wmods.wppenhacer.ui.glass.GlassRuntimeState
import com.wmods.wppenhacer.ui.glass.GlassStyle
import com.wmods.wppenhacer.ui.glass.LocalizedBackdropGlassView
import com.wmods.wppenhacer.ui.glass.LocalizedGlassEvent
import com.wmods.wppenhacer.ui.glass.LocalizedGlassFailureStage
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
    val FEATURE_ID = FeatureId("visual.experimental.liquid-glass-prototype")
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
                id = FEATURE_ID,
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
    private val diagnostics = RuntimeDiagnosticsRepublisher(application)

    fun start() {
        RuntimeTrace.event("glass-controller-start")
        application.registerActivityLifecycleCallbacks(this)
        GlassRuntimeState.update { current ->
            current.copy(
                backend = null,
                lifecycleCallbackRegistered = true,
                lastLifecycleEvent = "controller.start",
                attachedSurfaces = 0,
                failure = null
            )
        }
    }

    fun stop() {
        RuntimeTrace.event("glass-controller-stop")
        application.unregisterActivityLifecycleCallbacks(this)
        surfaces.entries.toList().forEach { (activity, _) -> detach(activity) }
        surfaces.clear()
        GlassRuntimeState.update { current ->
            current.copy(
                backend = null,
                lifecycleCallbackRegistered = false,
                lastLifecycleEvent = "controller.stop",
                attachedSurfaces = 0
            )
        }
        diagnostics.request("controller.stop")
    }

    override fun onActivityResumed(activity: Activity) {
        val activityClass = activity.javaClass.name
        RuntimeTrace.event("glass-activity-resumed", activityClass)
        GlassRuntimeState.update { current ->
            current.copy(
                lastActivityClass = activityClass,
                lastLifecycleEvent = "activity.resumed"
            )
        }
        diagnostics.request("activity.resumed")
        if (surfaces.containsKey(activity)) return
        RuntimeTrace.event("glass-attach-scheduled", activityClass)
        GlassRuntimeState.update { current ->
            current.copy(lastLifecycleEvent = "attach.scheduled")
        }
        activity.window.decorView.post { attach(activity) }
    }

    override fun onActivityPaused(activity: Activity) {
        recordLifecycle(activity, "activity.paused", "glass-activity-paused")
        detach(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        recordLifecycle(activity, "activity.destroyed", "glass-activity-destroyed")
        detach(activity)
    }
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit

    private fun attach(activity: Activity) {
        val activityClass = activity.javaClass.name
        RuntimeTrace.event("glass-attach-entered", activityClass)
        GlassRuntimeState.update { current ->
            current.copy(
                lastActivityClass = activityClass,
                lastLifecycleEvent = "attach.entered",
                attachAttempts = current.attachAttempts + 1
            )
        }
        if (activity.isFinishing || activity.isDestroyed) {
            recordAttachFailure(
                activity,
                "activity-state",
                IllegalStateException("activity finishing=${activity.isFinishing} destroyed=${activity.isDestroyed}")
            )
            return
        }
        if (surfaces.containsKey(activity)) {
            RuntimeTrace.event("glass-attach-skipped", "surface already attached to $activityClass")
            return
        }
        if (Build.VERSION.SDK_INT < 33 || !capabilities.localizedSameWindowAvailable) {
            recordAttachFailure(
                activity,
                "platform-capability",
                UnsupportedOperationException("localized same-window sampled backend unavailable")
            )
            return
        }
        val contentCandidate: View? = activity.findViewById(android.R.id.content)
        val rootClass = contentCandidate?.javaClass?.name
        val isViewGroup = contentCandidate is ViewGroup
        val isFrameLayout = contentCandidate is FrameLayout
        val rootWidth = contentCandidate?.width
        val rootHeight = contentCandidate?.height
        RuntimeTrace.event(
            "glass-content-root-resolved",
            "class=${rootClass ?: "null"} viewGroup=$isViewGroup frameLayout=$isFrameLayout"
        )
        RuntimeTrace.event(
            "glass-content-root-dimensions",
            "width=${rootWidth ?: -1} height=${rootHeight ?: -1}"
        )
        GlassRuntimeState.update { current ->
            current.copy(
                lastLifecycleEvent = "attach.content-root-resolved",
                contentRootClass = rootClass,
                contentRootIsViewGroup = isViewGroup,
                contentRootIsFrameLayout = isFrameLayout,
                contentRootWidth = rootWidth,
                contentRootHeight = rootHeight
            )
        }
        if (contentCandidate !is ViewGroup) {
            recordAttachFailure(
                activity,
                "content-root-type",
                IllegalStateException("android.R.id.content is not a ViewGroup: ${rootClass ?: "null"}")
            )
            return
        }
        if (contentCandidate !is FrameLayout) {
            recordAttachFailure(
                activity,
                "content-root-type",
                IllegalStateException("android.R.id.content is not a FrameLayout: $rootClass")
            )
            return
        }
        val contentRoot = contentCandidate
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
            onEvent = { event -> recordRenderEvent(event) },
            onFailure = { stage, throwable ->
                activity.runOnUiThread {
                    if (surfaces[activity] === view) detach(activity)
                    recordRenderFailure(activity, stage, throwable)
                }
            }
        )
        val surfaceWidth = (164 * density).toInt()
        val surfaceHeight = (58 * density).toInt()
        RuntimeTrace.event(
            "glass-candidate-surface-dimensions",
            "width=$surfaceWidth height=$surfaceHeight"
        )
        GlassRuntimeState.update { current ->
            current.copy(
                lastLifecycleEvent = "attach.surface-created",
                surfaceWidth = surfaceWidth,
                surfaceHeight = surfaceHeight
            )
        }
        val params = FrameLayout.LayoutParams(
            surfaceWidth,
            surfaceHeight,
            Gravity.TOP or Gravity.END
        ).apply {
            topMargin = (96 * density).toInt()
            marginEnd = (18 * density).toInt()
        }
        try {
            contentRoot.addView(view, params)
            surfaces[activity] = view
            GlassRuntimeState.update { current ->
                current.copy(
                    capabilities = capabilities,
                    backend = null,
                    lastLifecycleEvent = "attach.succeeded",
                    successfulAttaches = current.successfulAttaches + 1,
                    attachedSurfaces = surfaces.size,
                    hardwareAccelerated = view.isHardwareAccelerated,
                    lastAttachFailure = null,
                    failure = null
                )
            }
            RuntimeTrace.event(
                "glass-add-view-success",
                "activity=$activityClass surfaces=${surfaces.size}"
            )
            diagnostics.request("attach.succeeded")
        } catch (throwable: Throwable) {
            runCatching { contentRoot.removeView(view) }
            RuntimeTrace.event("glass-add-view-failure", DiagnosticSanitizer.failureSummary(throwable))
            recordAttachFailure(activity, "add-view", throwable)
        }
    }

    private fun recordMetrics(metrics: LocalizedGlassMetrics) {
        val previous = GlassRuntimeState.snapshot()
        val firstCapture = previous.capturedFrames == 0L && metrics.capturedFrames > 0L
        GlassRuntimeState.update { current ->
            current.copy(
                capabilities = capabilities,
                backend = GlassBackend.LOCALIZED_SAME_WINDOW_SAMPLED,
                lastRenderStage = "backdrop.captured",
                attachedSurfaces = surfaces.size,
                hardwareAccelerated = metrics.hardwareAccelerated,
                capturedFrames = metrics.capturedFrames,
                lastCaptureMs = metrics.lastCaptureMs,
                worstCaptureMs = metrics.worstCaptureMs,
                approximateRenderNodeBytes = metrics.approximateRenderNodeBytes,
                failure = null
            )
        }
        if (firstCapture) {
            RuntimeTrace.event(
                "glass-first-backdrop-captured",
                "backend=${GlassBackend.LOCALIZED_SAME_WINDOW_SAMPLED.name} captureMs=${metrics.lastCaptureMs}"
            )
            diagnostics.request("render.first-backdrop-captured")
        }
    }

    private fun recordRenderEvent(event: LocalizedGlassEvent) {
        when (event) {
            LocalizedGlassEvent.FIRST_PRE_DRAW -> {
                RuntimeTrace.event("glass-first-pre-draw")
                GlassRuntimeState.update { current ->
                    current.copy(
                        firstPreDrawObserved = true,
                        lastRenderStage = "pre-draw.observed"
                    )
                }
                diagnostics.request("render.first-pre-draw")
            }

            LocalizedGlassEvent.FIRST_BACKDROP_RECORDING -> {
                RuntimeTrace.event("glass-first-backdrop-recording")
                GlassRuntimeState.update { current ->
                    current.copy(
                        firstBackdropRecordingStarted = true,
                        lastRenderStage = "backdrop.recording"
                    )
                }
                diagnostics.request("render.first-backdrop-recording")
            }

            LocalizedGlassEvent.FIRST_BACKDROP_CAPTURED -> Unit

            LocalizedGlassEvent.FIRST_FRAME_RENDERED -> {
                RuntimeTrace.event(
                    "glass-first-frame-rendered",
                    GlassBackend.LOCALIZED_SAME_WINDOW_SAMPLED.name
                )
                GlassRuntimeState.update { current ->
                    current.copy(
                        backend = GlassBackend.LOCALIZED_SAME_WINDOW_SAMPLED,
                        firstFrameRendered = true,
                        lastRenderStage = "render-node.drawn",
                        failure = null
                    )
                }
                if (GlassRuntimeState.snapshot().runtimeVerified) {
                    RuntimeState.features?.markRuntimeVerified(GlassFeatureRegistry.FEATURE_ID)
                }
                diagnostics.request("render.first-frame-rendered")
            }
        }
    }

    private fun recordAttachFailure(activity: Activity, stage: String, throwable: Throwable) {
        val failure = DiagnosticSanitizer.failureSummary(throwable)
        RuntimeTrace.event("glass-attach-failure", "stage=$stage $failure")
        GlassRuntimeState.update { current ->
            current.copy(
                capabilities = capabilities,
                backend = if (surfaces.isEmpty()) null else current.backend,
                lastActivityClass = activity.javaClass.name,
                lastLifecycleEvent = "attach.failed.$stage",
                attachedSurfaces = surfaces.size,
                lastAttachFailure = failure,
                failure = failure
            )
        }
        diagnostics.request("attach.failed.$stage")
    }

    private fun recordRenderFailure(
        activity: Activity,
        stage: LocalizedGlassFailureStage,
        throwable: Throwable
    ) {
        val failure = DiagnosticSanitizer.failureSummary(throwable)
        RuntimeTrace.event("glass-render-failure", "stage=${stage.name} $failure")
        GlassRuntimeState.update { current ->
            current.copy(
                capabilities = capabilities,
                backend = if (surfaces.isEmpty()) null else current.backend,
                lastActivityClass = activity.javaClass.name,
                lastLifecycleEvent = "render.failed",
                lastRenderStage = "${stage.name.lowercase()}.failed",
                attachedSurfaces = surfaces.size,
                failure = failure
            )
        }
        diagnostics.request("render.failed.${stage.name.lowercase()}")
    }

    private fun detach(activity: Activity) {
        val surface = surfaces.remove(activity) ?: return
        runCatching { (surface.parent as? ViewGroup)?.removeView(surface) }
        RuntimeTrace.event("glass-surface-detached", activity.javaClass.name)
        GlassRuntimeState.update { current ->
            current.copy(
                backend = if (surfaces.isEmpty()) null else current.backend,
                lastActivityClass = activity.javaClass.name,
                lastLifecycleEvent = "surface.detached",
                attachedSurfaces = surfaces.size
            )
        }
        diagnostics.request("surface.detached")
    }

    private fun recordLifecycle(activity: Activity, lifecycleEvent: String, traceEvent: String) {
        val activityClass = activity.javaClass.name
        RuntimeTrace.event(traceEvent, activityClass)
        GlassRuntimeState.update { current ->
            current.copy(
                lastActivityClass = activityClass,
                lastLifecycleEvent = lifecycleEvent
            )
        }
        diagnostics.request(lifecycleEvent)
    }
}
