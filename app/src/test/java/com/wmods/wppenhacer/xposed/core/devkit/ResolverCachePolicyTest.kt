package com.wmods.wppenhacer.xposed.core.devkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResolverCachePolicyTest {
    private val current = ResolverCacheIdentity(
        hostVersionCode = 263807322,
        hostVersionName = "2.26.38.73",
        moduleUpdateTime = 20,
        moduleVersionName = "1.6.0",
        cacheSchemaVersion = 2
    )

    @Test
    fun unchangedIdentityIsCacheHit() {
        val status = ResolverCachePolicy.evaluate(current, current, resetOnModuleUpdate = true)

        assertEquals(ResolverCacheDisposition.HIT, status.disposition)
        assertNull(status.invalidationReason)
    }

    @Test
    fun hostVersionCodeChangeInvalidatesBeforeReuse() {
        val saved = current.copy(
            hostVersionCode = 263707001,
            hostVersionName = "2.26.37.1"
        )

        val status = ResolverCachePolicy.evaluate(saved, current, resetOnModuleUpdate = true)

        assertEquals(ResolverCacheDisposition.INVALIDATED, status.disposition)
        assertEquals(
            ResolverCacheInvalidationReason.HOST_VERSION_CODE_CHANGED,
            status.invalidationReason
        )
    }

    @Test
    fun hostVersionNameChangeAlsoInvalidates() {
        val saved = current.copy(hostVersionName = "2.26.38.72")

        val status = ResolverCachePolicy.evaluate(saved, current, resetOnModuleUpdate = false)

        assertEquals(
            ResolverCacheInvalidationReason.HOST_VERSION_NAME_CHANGED,
            status.invalidationReason
        )
    }
}
