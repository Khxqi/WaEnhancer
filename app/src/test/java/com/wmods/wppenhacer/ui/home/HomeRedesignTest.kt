package com.wmods.wppenhacer.ui.home

import com.wmods.wppenhacer.xposed.runtime.ConfigTransportStatus
import com.wmods.wppenhacer.xposed.runtime.HomeRedesignPolicy
import com.wmods.wppenhacer.xposed.runtime.LegacyHomeConflictPolicy
import com.wmods.wppenhacer.xposed.runtime.RuntimeConfigSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRedesignTest {
    @Test
    fun homeRedesignRequiresFlagAndAllRecoveryGatesOpen() {
        val enabled = config(enableHome = true)
        assertTrue(HomeRedesignPolicy.enabled(enabled))
        assertFalse(HomeRedesignPolicy.enabled(enabled.copy(safeMode = true)))
        assertFalse(HomeRedesignPolicy.enabled(enabled.copy(disableAllHooks = true)))
        assertFalse(HomeRedesignPolicy.enabled(enabled.copy(disableVisualModifications = true)))
        assertFalse(
            HomeRedesignPolicy.enabled(
                enabled.copy(transportStatus = ConfigTransportStatus.FAILED_CLOSED)
            )
        )
        assertFalse(HomeRedesignPolicy.enabled(config(enableHome = false)))
    }

    @Test
    fun navigationValidationRejectsUnsafeStructuresAndAcceptsFiveDestinations() {
        val valid = HomeNavigationShape(
            activityIsHome = true,
            visible = true,
            isViewGroup = true,
            nearBottom = true,
            widthFraction = 0.92f,
            heightDp = 72f,
            destinationCenters = listOf(80, 240, 400, 560, 720)
        )
        assertTrue(HomeNavigationValidator.validate(valid).valid)
        assertEquals(
            "destination-count-out-of-range",
            HomeNavigationValidator.validate(valid.copy(destinationCenters = listOf(80, 240))).reason
        )
        assertEquals(
            "destinations-not-horizontally-ordered",
            HomeNavigationValidator.validate(
                valid.copy(destinationCenters = listOf(80, 240, 200, 560, 720))
            ).reason
        )
        assertFalse(HomeNavigationValidator.validate(valid.copy(nearBottom = false)).valid)
    }

    @Test
    fun selectedStateMappingNeedsOneUnambiguousDestination() {
        assertEquals(2, HomeSelectedDestination.resolve(listOf(false, false, true, false)))
        assertEquals(1, HomeSelectedDestination.resolve(listOf(false, false, false), previous = 1))
        assertNull(HomeSelectedDestination.resolve(listOf(false, false, false)))
        assertEquals(1, HomeSelectedDestination.resolve(listOf(true, true, false), previous = 1))
    }

    @Test
    fun legacyConflictPolicyBlocksOnlyHomeVisualOwners() {
        val enabled = config(enableHome = true)
        assertTrue(
            LegacyHomeConflictPolicy.blocks(
                "com.wmods.wppenhacer.xposed.features.customization.FloatingBottomBar",
                enabled
            )
        )
        assertTrue(
            LegacyHomeConflictPolicy.blocks(
                "com.wmods.wppenhacer.xposed.features.customization.CustomToolbar",
                enabled
            )
        )
        assertFalse(
            LegacyHomeConflictPolicy.blocks(
                "com.wmods.wppenhacer.xposed.features.privacy.HideReceipt",
                enabled
            )
        )
        assertFalse(
            LegacyHomeConflictPolicy.blocks(
                "com.wmods.wppenhacer.xposed.features.customization.FloatingBottomBar",
                enabled.copy(enableIosHomeRedesign = false)
            )
        )
    }

    @Test
    fun insetAndSharedSurfacePoliciesAreBounded() {
        assertEquals(42, HomeBottomInsetPolicy.bottomMarginPx(30, 12))
        assertEquals(12, HomeBottomInsetPolicy.bottomMarginPx(-1, 12))
        assertTrue(HomeBottomInsetPolicy.shouldShowNavigation(imeVisible = false))
        assertFalse(HomeBottomInsetPolicy.shouldShowNavigation(imeVisible = true))
        assertTrue(HomeGlassSurfaceOwnership.canAttach(0, alreadyAttached = false))
        assertFalse(HomeGlassSurfaceOwnership.canAttach(1, alreadyAttached = false))
        assertFalse(HomeGlassSurfaceOwnership.canAttach(0, alreadyAttached = true))
    }

    private fun config(enableHome: Boolean) = RuntimeConfigSnapshot(
        disableAllHooks = false,
        disableVisualModifications = false,
        safeMode = false,
        enableLogs = true,
        transportStatus = ConfigTransportStatus.XSHARED_PREFERENCES,
        enableLiquidGlassPrototype = false,
        enableIosHomeRedesign = enableHome
    )
}

