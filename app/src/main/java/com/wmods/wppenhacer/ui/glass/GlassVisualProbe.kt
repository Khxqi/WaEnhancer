package com.wmods.wppenhacer.ui.glass

/** Ordered, device-visible probes for the localized RenderNode composition pipeline. */
enum class GlassVisualProbeMode {
    RAW_REPLAY_SHIFTED,
    BUILTIN_BLUR,
    RUNTIME_SOLID,
    RUNTIME_INPUT_TINT,
    CURRENT_GLASS_EXAGGERATED
}

enum class GlassProbeEffectSelection {
    NONE,
    BUILTIN_BLUR,
    RUNTIME_SOLID,
    RUNTIME_INPUT_TINT,
    CURRENT_GLASS
}

object GlassVisualProbePlan {
    val modes: List<GlassVisualProbeMode> = GlassVisualProbeMode.entries

    fun effectFor(mode: GlassVisualProbeMode): GlassProbeEffectSelection = when (mode) {
        GlassVisualProbeMode.RAW_REPLAY_SHIFTED -> GlassProbeEffectSelection.NONE
        GlassVisualProbeMode.BUILTIN_BLUR -> GlassProbeEffectSelection.BUILTIN_BLUR
        GlassVisualProbeMode.RUNTIME_SOLID -> GlassProbeEffectSelection.RUNTIME_SOLID
        GlassVisualProbeMode.RUNTIME_INPUT_TINT -> GlassProbeEffectSelection.RUNTIME_INPUT_TINT
        GlassVisualProbeMode.CURRENT_GLASS_EXAGGERATED -> GlassProbeEffectSelection.CURRENT_GLASS
    }
}

/**
 * Pure state holder used by the View's delayed callback. It deliberately owns no Handler so its
 * sequence and detach cancellation semantics remain JVM-testable.
 */
class GlassVisualProbeCycle(
    private val modes: List<GlassVisualProbeMode> = GlassVisualProbePlan.modes
) {
    init {
        require(modes.isNotEmpty()) { "probe mode list must not be empty" }
    }

    private var index = 0
    var running: Boolean = false
        private set
    var cycleCount: Long = 0
        private set

    val currentMode: GlassVisualProbeMode
        get() = modes[index]

    fun start(): GlassVisualProbeMode {
        index = 0
        cycleCount = 0
        running = true
        return currentMode
    }

    fun stop() {
        running = false
    }

    fun advance(): GlassVisualProbeMode {
        if (!running) return currentMode
        index = (index + 1) % modes.size
        if (index == 0) cycleCount++
        return currentMode
    }
}
