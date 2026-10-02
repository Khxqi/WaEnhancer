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
        assertTrue(
            GlassRuntimeSnapshot(
                successfulAttaches = 1,
                firstFrameRendered = true
            ).drawPathVerified
        )
    }

    @Test
    fun visualProbeUsesExpectedOrderedModesAndEffects() {
        assertEquals(
            listOf(
                GlassVisualProbeMode.RAW_REPLAY_SHIFTED,
                GlassVisualProbeMode.BUILTIN_BLUR,
                GlassVisualProbeMode.RUNTIME_SOLID,
                GlassVisualProbeMode.RUNTIME_INPUT_TINT,
                GlassVisualProbeMode.CURRENT_GLASS_EXAGGERATED
            ),
            GlassVisualProbePlan.modes
        )
        assertEquals(
            GlassProbeEffectSelection.NONE,
            GlassVisualProbePlan.effectFor(GlassVisualProbeMode.RAW_REPLAY_SHIFTED)
        )
        assertEquals(
            GlassProbeEffectSelection.BUILTIN_BLUR,
            GlassVisualProbePlan.effectFor(GlassVisualProbeMode.BUILTIN_BLUR)
        )
        assertEquals(
            GlassProbeEffectSelection.RUNTIME_SOLID,
            GlassVisualProbePlan.effectFor(GlassVisualProbeMode.RUNTIME_SOLID)
        )
        assertEquals(
            GlassProbeEffectSelection.RUNTIME_INPUT_TINT,
            GlassVisualProbePlan.effectFor(GlassVisualProbeMode.RUNTIME_INPUT_TINT)
        )
        assertEquals(
            GlassProbeEffectSelection.CURRENT_GLASS,
            GlassVisualProbePlan.effectFor(GlassVisualProbeMode.CURRENT_GLASS_EXAGGERATED)
        )
    }

    @Test
    fun visualProbeRepeatsAndCountsFullCycles() {
        val cycle = GlassVisualProbeCycle()

        assertEquals(GlassVisualProbeMode.RAW_REPLAY_SHIFTED, cycle.start())
        assertEquals(GlassVisualProbeMode.BUILTIN_BLUR, cycle.advance())
        assertEquals(GlassVisualProbeMode.RUNTIME_SOLID, cycle.advance())
        assertEquals(GlassVisualProbeMode.RUNTIME_INPUT_TINT, cycle.advance())
        assertEquals(GlassVisualProbeMode.CURRENT_GLASS_EXAGGERATED, cycle.advance())
        assertEquals(GlassVisualProbeMode.RAW_REPLAY_SHIFTED, cycle.advance())
        assertEquals(1L, cycle.cycleCount)
    }

    @Test
    fun stoppedVisualProbeDoesNotAdvance() {
        val cycle = GlassVisualProbeCycle()
        cycle.start()
        cycle.advance()
        cycle.stop()

        assertFalse(cycle.running)
        assertEquals(GlassVisualProbeMode.BUILTIN_BLUR, cycle.advance())
        assertEquals(0L, cycle.cycleCount)
    }

    @Test
    fun glassEffectIsNotConfiguredBeforeValidBoundsAndStyleExist() {
        val state = GlassEffectConfigurationState()

        assertFalse(state.isConfigured)
        assertEquals(0L, state.generation)
        assertTrue(
            runCatching {
                GlassEffectBounds(left = 12, top = 12, right = 12, bottom = 80)
            }.isFailure
        )
    }

    @Test
    fun glassEffectGenerationChangesForBoundsOrStyleButNotPerFrame() {
        val state = GlassEffectConfigurationState()
        val baseStyle = GlassStyle(tintOpacity = 0.48f, animationProgress = 0.5f)
        val initial = GlassEffectConfigurationKey(
            bounds = GlassEffectBounds(left = 36, top = 36, right = 200, bottom = 94),
            style = baseStyle
        )

        assertTrue(state.needsRebuild(initial))
        assertEquals(1L, state.markConfigured(initial))
        assertTrue(state.isConfigured)
        assertFalse(state.needsRebuild(initial))
        assertEquals(1L, state.markConfigured(initial))

        val resized = initial.copy(
            bounds = initial.bounds.copy(right = initial.bounds.right + 1)
        )
        assertTrue(state.needsRebuild(resized))
        assertEquals(2L, state.markConfigured(resized))

        val restyled = resized.copy(
            style = resized.style.copy(tintOpacity = 0.42f)
        )
        assertTrue(state.needsRebuild(restyled))
        assertEquals(3L, state.markConfigured(restyled))
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
