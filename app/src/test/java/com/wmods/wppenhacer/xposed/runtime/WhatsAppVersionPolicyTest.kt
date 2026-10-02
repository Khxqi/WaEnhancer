package com.wmods.wppenhacer.xposed.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppVersionPolicyTest {
    private val supported = listOf("2.26.37.xx", "2.26.38.xx")

    @Test
    fun acceptsTargetWhatsAppVersion() {
        val decision = WhatsAppVersionPolicy.evaluate("2.26.38.73", supported)

        assertTrue(decision.metadataAccepted)
        assertEquals(HostCompatibilityMode.FULL_RUNTIME, decision.mode)
    }

    @Test
    fun unknownFutureVersionUsesFrameworkOnlyMode() {
        val decision = WhatsAppVersionPolicy.evaluate("2.26.39.1", supported)

        assertFalse(decision.metadataAccepted)
        assertEquals(HostCompatibilityMode.FRAMEWORK_ONLY, decision.mode)
    }

    @Test
    fun missingMetadataFailsClosedToFrameworkOnlyMode() {
        val decision = WhatsAppVersionPolicy.evaluate("2.26.38.73", emptyList())

        assertFalse(decision.metadataAccepted)
        assertEquals(HostCompatibilityMode.FRAMEWORK_ONLY, decision.mode)
    }

    @Test
    fun explicitBypassIsVisibleAndDistinctFromMetadataAcceptance() {
        val decision = WhatsAppVersionPolicy.evaluate(
            "2.26.39.1",
            supported,
            bypassEnabled = true
        )

        assertFalse(decision.metadataAccepted)
        assertEquals(HostCompatibilityMode.FULL_RUNTIME_BYPASS, decision.mode)
    }

    @Test
    fun wildcardDoesNotAcceptAdjacentMinorVersion() {
        assertFalse(WhatsAppVersionPolicy.matches("2.26.380.1", "2.26.38.xx"))
    }
}
