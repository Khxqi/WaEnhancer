package com.wmods.wppenhacer.ui.glass

data class GlassEffectBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    init {
        require(right > left) { "glass effect bounds must have positive width" }
        require(bottom > top) { "glass effect bounds must have positive height" }
    }
}

data class GlassEffectConfigurationKey(
    val bounds: GlassEffectBounds,
    val style: GlassStyle
)

/**
 * Pure lifecycle guard for the native RuntimeShader RenderEffect snapshot. A generation is marked
 * only after every uniform was configured and effect creation succeeded.
 */
class GlassEffectConfigurationState {
    private var configuredKey: GlassEffectConfigurationKey? = null

    var generation: Long = 0
        private set

    val isConfigured: Boolean
        get() = configuredKey != null

    fun needsRebuild(key: GlassEffectConfigurationKey): Boolean = configuredKey != key

    fun markConfigured(key: GlassEffectConfigurationKey): Long {
        if (configuredKey != key) {
            configuredKey = key
            generation++
        }
        return generation
    }
}
