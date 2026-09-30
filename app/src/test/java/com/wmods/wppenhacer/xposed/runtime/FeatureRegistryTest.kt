package com.wmods.wppenhacer.xposed.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureRegistryTest {
    @Test
    fun failedFeatureDoesNotBlockFollowingFeature() {
        val registry = FeatureRegistry(CapabilityRegistry())
        var secondInstalled = false
        registry.register(spec("test.failed") { error("expected failure") })
        registry.register(spec("test.ready") {
            secondInstalled = true
            emptyList()
        })

        registry.installAll()

        val records = registry.snapshot().associateBy { it.id.value }
        assertEquals(FeatureStatus.FAILED, records.getValue("test.failed").status)
        assertEquals(FeatureStatus.READY, records.getValue("test.ready").status)
        assertTrue(secondInstalled)
    }

    @Test
    fun missingCapabilityLeavesFeatureUnsupported() {
        val registry = FeatureRegistry(CapabilityRegistry())
        registry.register(spec("test.unsupported", setOf(CapabilityId("missing"))) { emptyList() })

        registry.installAll()

        assertEquals(FeatureStatus.UNSUPPORTED, registry.snapshot().single().status)
    }

    @Test
    fun disabledFeatureIsNotInstalled() {
        val registry = FeatureRegistry(CapabilityRegistry())
        var installed = false
        registry.register(
            FeatureSpec(
                id = FeatureId("test.disabled"),
                category = FeatureCategory.SUPPORT,
                requiredCapabilities = emptySet(),
                enabled = { false },
                installer = {
                    installed = true
                    emptyList()
                }
            )
        )

        registry.installAll()

        assertEquals(FeatureStatus.DISABLED, registry.snapshot().single().status)
        assertEquals(false, installed)
    }

    private fun spec(
        id: String,
        capabilities: Set<CapabilityId> = emptySet(),
        installer: () -> List<HookHandle>
    ) = FeatureSpec(
        id = FeatureId(id),
        category = FeatureCategory.SUPPORT,
        requiredCapabilities = capabilities,
        enabled = { true },
        installer = installer
    )
}
