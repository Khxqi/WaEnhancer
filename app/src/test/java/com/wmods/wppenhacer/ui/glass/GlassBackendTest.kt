package com.wmods.wppenhacer.ui.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassBackendTest {
    @Test
    fun rejectedCrossWindowBackendIsNeverTheLocalizedBackend() {
        assertFalse(GlassBackend.CROSS_WINDOW_BLUR_REJECTED == GlassBackend.LOCALIZED_SAME_WINDOW_SAMPLED)
    }

    @Test
    fun runtimeSnapshotRecordsCrossWindowRejectionByDefault() {
        val snapshot = GlassRuntimeSnapshot()
        assertTrue(snapshot.rejectedBackends.contains(GlassBackend.CROSS_WINDOW_BLUR_REJECTED))
        assertEquals(null, snapshot.backend)
    }

    @Test
    fun runtimeVerificationRequiresAttachAndRenderedFrame() {
        assertFalse(GlassRuntimeSnapshot().runtimeVerified)
        assertFalse(GlassRuntimeSnapshot(successfulAttaches = 1).runtimeVerified)
        assertFalse(GlassRuntimeSnapshot(firstFrameRendered = true).runtimeVerified)
        assertTrue(
            GlassRuntimeSnapshot(
                successfulAttaches = 1,
                firstFrameRendered = true
            ).runtimeVerified
        )
    }
}
