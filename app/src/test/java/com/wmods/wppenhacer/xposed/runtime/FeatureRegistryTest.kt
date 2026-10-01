package com.wmods.wppenhacer.xposed.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureRegistryTest {
    @Test
    fun failedFeatureDoesNotBlockFollowingFeature() {
        val registry = FeatureRegistry(CapabilityRegistry())
        var secondInstalled = false
        var firstRolledBack = false
        registry.register(spec("test.failed") {
            register("first-hook", object : HookHandle {
                override fun unhook() {
                    firstRolledBack = true
                }
            })
            error("expected failure")
        })
        registry.register(spec("test.ready") {
            secondInstalled = true
        })

        registry.installAll()

        val records = registry.snapshot().associateBy { it.id.value }
        assertEquals(FeatureStatus.FAILED, records.getValue("test.failed").status)
        assertEquals(FeatureStatus.READY, records.getValue("test.ready").status)
        assertEquals(1, records.getValue("test.failed").attemptedHookCount)
        assertEquals(0, records.getValue("test.failed").installedHookCount)
        assertTrue(records.getValue("test.failed").partialInstallation)
        assertTrue(records.getValue("test.failed").rollbackAttempted)
        assertEquals(true, records.getValue("test.failed").rollbackSucceeded)
        assertTrue(firstRolledBack)
        assertTrue(secondInstalled)
    }

    @Test
    fun missingCapabilityLeavesFeatureUnsupported() {
        val registry = FeatureRegistry(CapabilityRegistry())
        var installed = false
        registry.register(spec("test.unsupported", setOf(CapabilityId("missing"))) {
            installed = true
        })

        registry.installAll()

        assertEquals(FeatureStatus.UNSUPPORTED, registry.snapshot().single().status)
        assertFalse(installed)
    }

    @Test
    fun disabledFeatureIsNotInstalled() {
        val registry = FeatureRegistry(CapabilityRegistry())
        var installed = false
        registry.register(
            FeatureSpec(
                id = FeatureId("test.disabled"),
                diagnosticName = "test.disabled",
                category = FeatureCategory.SUPPORT,
                requiredCapabilities = emptySet(),
                enabled = { false },
                installer = {
                    installed = true
                }
            )
        )

        registry.installAll()

        assertEquals(FeatureStatus.DISABLED, registry.snapshot().single().status)
        assertEquals(false, installed)
    }

    @Test
    fun legacyFailureReportsUnknownHookCountsWithoutClaimingRollback() {
        val registry = FeatureRegistry(CapabilityRegistry())
        registry.register(
            spec("test.legacy", legacy = true) { error("legacy install failed") }
        )

        registry.installAll()

        val record = registry.snapshot().single()
        assertEquals(FeatureStatus.FAILED, record.status)
        assertEquals(RollbackSupport.NONE, record.rollbackSupport)
        assertNull(record.attemptedHookCount)
        assertNull(record.installedHookCount)
        assertTrue(record.partialInstallation)
    }

    private fun spec(
        id: String,
        capabilities: Set<CapabilityId> = emptySet(),
        legacy: Boolean = false,
        installer: HookInstallScope.() -> Unit
    ) = FeatureSpec(
        id = FeatureId(id),
        diagnosticName = id,
        category = FeatureCategory.SUPPORT,
        requiredCapabilities = capabilities,
        enabled = { true },
        installer = installer,
        legacyManagedEnablement = legacy
    )
}
