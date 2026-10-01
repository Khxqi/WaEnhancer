package com.wmods.wppenhacer.xposed.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyFeaturePolicyTest {
    @Test
    fun safeModePreventsPrivacyInstallation() {
        assertPrivacyFeatureDisabled(config(safeMode = true))
    }

    @Test
    fun disableAllPreventsPrivacyInstallation() {
        assertPrivacyFeatureDisabled(config(disableAllHooks = true))
    }

    @Test
    fun failedConfigurationTransportPreventsPrivacyInstallation() {
        assertPrivacyFeatureDisabled(config(transport = ConfigTransportStatus.FAILED_CLOSED))
    }

    @Test
    fun visualDisableDoesNotDisableRuntimePrivacy() {
        val config = config(disableVisualModifications = true)
        assertTrue(PrivacyFeaturePolicy.runtimeAllowed(config))
        assertFalse(PrivacyFeaturePolicy.visualAllowed(config))

        val registry = FeatureRegistry(CapabilityRegistry())
        var privacyInstalled = false
        var visualInstalled = false
        registry.register(
            spec(
                "privacy.test",
                FeatureCategory.PRIVACY,
                enabled = { PrivacyFeaturePolicy.runtimeAllowed(config) },
                installer = { privacyInstalled = true }
            )
        )
        registry.register(
            spec(
                "visual.test",
                FeatureCategory.VISUAL,
                enabled = { PrivacyFeaturePolicy.visualAllowed(config) },
                installer = { visualInstalled = true }
            )
        )

        registry.installAll()

        assertTrue(privacyInstalled)
        assertFalse(visualInstalled)
        val records = registry.snapshot().associateBy { it.id.value }
        assertEquals(FeatureStatus.READY, records.getValue("privacy.test").status)
        assertEquals(FeatureStatus.DISABLED, records.getValue("visual.test").status)
    }

    private fun assertPrivacyFeatureDisabled(config: RuntimeConfigSnapshot) {
        val registry = FeatureRegistry(CapabilityRegistry())
        var installed = false
        registry.register(
            spec(
                "privacy.test",
                FeatureCategory.PRIVACY,
                enabled = { PrivacyFeaturePolicy.runtimeAllowed(config) },
                installer = { installed = true }
            )
        )

        registry.installAll()

        assertFalse(installed)
        assertEquals(FeatureStatus.DISABLED, registry.snapshot().single().status)
    }

    private fun spec(
        id: String,
        category: FeatureCategory,
        enabled: () -> Boolean,
        installer: HookInstallScope.() -> Unit
    ) = FeatureSpec(
        id = FeatureId(id),
        diagnosticName = id,
        category = category,
        requiredCapabilities = emptySet(),
        enabled = enabled,
        installer = installer
    )

    private fun config(
        disableAllHooks: Boolean = false,
        disableVisualModifications: Boolean = false,
        safeMode: Boolean = false,
        transport: ConfigTransportStatus = ConfigTransportStatus.XSHARED_PREFERENCES
    ) = RuntimeConfigSnapshot(
        disableAllHooks = disableAllHooks,
        disableVisualModifications = disableVisualModifications,
        safeMode = safeMode,
        enableLogs = false,
        transportStatus = transport
    )
}
