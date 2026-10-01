package com.wmods.wppenhacer.xposed.runtime

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Build
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.PopupWindow
import com.wmods.wppenhacer.ui.glass.GlassBackend
import com.wmods.wppenhacer.ui.glass.GlassCapabilities
import com.wmods.wppenhacer.ui.glass.GlassRuntimeSnapshot
import com.wmods.wppenhacer.ui.glass.GlassRuntimeState
import com.wmods.wppenhacer.ui.glass.GlassShape
import com.wmods.wppenhacer.ui.glass.GlassStyle
import com.wmods.wppenhacer.ui.glass.LayeredGlassRenderer
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

    fun resolve(capabilities: CapabilityRegistry, application: Application) {
        capabilities.resolve(PLATFORM) { GlassCapabilities.detect(application) }
            ?.let { GlassRuntimeState.update(GlassRuntimeSnapshot(capabilities = it)) }
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
                requiredCapabilities = setOf(PLATFORM),
                enabled = { GlassFeaturePolicy.enabled(config) },
                installer = {
                    val platform = requireNotNull(capabilities.get<GlassCapabilities>(PLATFORM))
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
    private val windows = WeakHashMap<Activity, PopupWindow>()

    fun start() {
        application.registerActivityLifecycleCallbacks(this)
        GlassRuntimeState.update(GlassRuntimeState.snapshot().copy(backend = null, attachedSurfaces = 0, failure = null))
    }

    fun stop() {
        application.unregisterActivityLifecycleCallbacks(this)
        windows.values.toList().forEach { runCatching { it.dismiss() } }
        windows.clear()
        GlassRuntimeState.update(GlassRuntimeState.snapshot().copy(backend = null, attachedSurfaces = 0, failure = null))
    }

    override fun onActivityResumed(activity: Activity) {
        if (windows.containsKey(activity)) return
        activity.window.decorView.post { attach(activity) }
    }

    override fun onActivityPaused(activity: Activity) = detach(activity)
    override fun onActivityDestroyed(activity: Activity) = detach(activity)
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit

    private fun attach(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed || windows.containsKey(activity)) return
        val density = activity.resources.displayMetrics.density
        val view = PrototypeGlassView(activity)
        val popup = PopupWindow(
            view,
            (164 * density).toInt(),
            (58 * density).toInt(),
            false
        ).apply {
            isTouchable = false
            isFocusable = false
            isOutsideTouchable = false
            isClippingEnabled = true
            elevation = 12 * density
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        try {
            popup.showAtLocation(
                activity.window.decorView,
                Gravity.TOP or Gravity.END,
                (18 * density).toInt(),
                (96 * density).toInt()
            )
            windows[activity] = popup
            val crossWindow = applyCrossWindowBlur(activity, view, (28 * density).toInt())
            GlassRuntimeState.update(
                GlassRuntimeSnapshot(
                    capabilities = capabilities,
                    backend = if (crossWindow) GlassBackend.CROSS_WINDOW_BLUR else GlassBackend.LAYERED_GPU_FALLBACK,
                    attachedSurfaces = windows.size,
                    hardwareAccelerated = view.isHardwareAccelerated
                )
            )
        } catch (throwable: Throwable) {
            runCatching { popup.dismiss() }
            GlassRuntimeState.update(
                GlassRuntimeSnapshot(
                    capabilities = capabilities,
                    failure = DiagnosticSanitizer.failureSummary(throwable),
                    attachedSurfaces = windows.size
                )
            )
        }
    }

    private fun applyCrossWindowBlur(activity: Activity, content: View, radius: Int): Boolean {
        if (Build.VERSION.SDK_INT < 31 || !capabilities.crossWindowBlurAvailable) return false
        return runCatching {
            if (!activity.windowManager.isCrossWindowBlurEnabled) return false
            val root = content.rootView
            val params = root.layoutParams as WindowManager.LayoutParams
            params.flags = params.flags or
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            params.setBlurBehindRadius(radius)
            activity.windowManager.updateViewLayout(root, params)
            true
        }.getOrDefault(false)
    }

    private fun detach(activity: Activity) {
        windows.remove(activity)?.let { runCatching { it.dismiss() } }
        val previous = GlassRuntimeState.snapshot()
        GlassRuntimeState.update(previous.copy(attachedSurfaces = windows.size))
    }
}

private class PrototypeGlassView(context: android.content.Context) : View(context) {
    private val renderer = LayeredGlassRenderer()
    private val bounds = RectF()
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 12f * resources.displayMetrics.scaledDensity
        isFakeBoldText = true
        letterSpacing = 0.08f
        setShadowLayer(2f, 0f, 1f, 0x88000000.toInt())
    }
    private val style = GlassStyle(
        tintColor = 0xFFDDEBFF.toInt(),
        tintOpacity = 0.16f,
        edgeIntensity = 0.64f,
        highlightIntensity = 0.42f,
        depth = 0.78f
    )

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    override fun onDraw(canvas: Canvas) {
        bounds.set(1f, 1f, width - 1f, height - 1f)
        renderer.draw(canvas, bounds, GlassShape.Capsule, style)
        canvas.drawText("GLASS PROTOTYPE", bounds.centerX(), bounds.centerY() + label.textSize * .35f, label)
    }
}
