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

    @Test
    fun surfaceParentMustRemainOutsideSamplingSubtree() {
        assertFalse(
            GlassHierarchyPolicy.evaluate(
                sameNode = true,
                surfaceParentInsideSamplingSubtree = false
            ).surfaceOutsideSamplingSubtree
        )
        assertFalse(
            GlassHierarchyPolicy.evaluate(
                sameNode = false,
                surfaceParentInsideSamplingSubtree = true
            ).surfaceOutsideSamplingSubtree
        )
        assertTrue(
            GlassHierarchyPolicy.evaluate(
                sameNode = false,
                surfaceParentInsideSamplingSubtree = false
            ).surfaceOutsideSamplingSubtree
        )
    }

    @Test
    fun captureOnlyDoesNotVerifyRuntime() {
        val snapshot = GlassRuntimeSnapshot(
            successfulAttaches = 1,
            capturedFrames = 1,
            normalOnDrawCount = 0,
            successfulRenderNodeDrawCount = 0,
            firstFrameRendered = false
        )

        assertFalse(snapshot.runtimeVerified)
    }

    @Test
    fun windowCoordinatesMapSurfaceIntoSamplingRoot() {
        val geometry = LocalizedGlassGeometry(
            samplingRootWindowX = 0,
            samplingRootWindowY = 72,
            surfaceWindowX = 680,
            surfaceWindowY = 240,
            surfaceWidth = 574,
            surfaceHeight = 203
        )

        assertEquals(680, geometry.surfaceLeftInSamplingRoot)
        assertEquals(168, geometry.surfaceTopInSamplingRoot)
    }
}
