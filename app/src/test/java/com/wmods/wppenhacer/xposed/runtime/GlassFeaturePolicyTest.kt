package com.wmods.wppenhacer.xposed.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassFeaturePolicyTest {
    @Test fun prototypeIsOffByDefault() = assertFalse(GlassFeaturePolicy.enabled(config()))
    @Test fun explicitFlagEnablesPrototype() = assertTrue(GlassFeaturePolicy.enabled(config(enabled = true)))
    @Test fun safeModeWins() = assertFalse(GlassFeaturePolicy.enabled(config(enabled = true, safeMode = true)))
    @Test fun disableAllWins() = assertFalse(GlassFeaturePolicy.enabled(config(enabled = true, disableAll = true)))
    @Test fun disableVisualWins() = assertFalse(GlassFeaturePolicy.enabled(config(enabled = true, disableVisual = true)))
    @Test fun failedConfigWins() = assertFalse(GlassFeaturePolicy.enabled(config(enabled = true, failed = true)))
    @Test fun homeRedesignSupersedesDiagnosticPrototype() =
        assertFalse(GlassFeaturePolicy.enabled(config(enabled = true, enableHome = true)))

    private fun config(
        enabled: Boolean = false,
        safeMode: Boolean = false,
        disableAll: Boolean = false,
        disableVisual: Boolean = false,
        failed: Boolean = false,
        enableHome: Boolean = false
    ) = RuntimeConfigSnapshot(
        disableAllHooks = disableAll,
        disableVisualModifications = disableVisual,
        safeMode = safeMode,
        enableLogs = false,
        transportStatus = if (failed) ConfigTransportStatus.FAILED_CLOSED else ConfigTransportStatus.XSHARED_PREFERENCES,
        enableLiquidGlassPrototype = enabled,
        enableIosHomeRedesign = enableHome
    )
}
