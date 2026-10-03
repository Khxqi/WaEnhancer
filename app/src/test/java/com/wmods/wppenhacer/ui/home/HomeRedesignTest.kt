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

    @Test
    fun customNavigationRequiresFourFunctionalHostDestinationsAndNoLabels() {
        assertTrue(HomeCustomNavigationPolicy.canReplace(4, 4))
        assertTrue(HomeCustomNavigationPolicy.canReplace(4, 5))
        assertFalse(HomeCustomNavigationPolicy.canReplace(5, 4))
        assertFalse(HomeCustomNavigationPolicy.canReplace(2, 2))
        assertEquals(0, HomeCustomNavigationPolicy.VISIBLE_LABEL_COUNT)
    }

    @Test
    fun productionTokensStayClearSlimNeutralAndUseDarkSelectionInBothModes() {
        listOf(LiquidGlassTokens.DARK, LiquidGlassTokens.LIGHT).forEach { tokens ->
            assertTrue(tokens.navigationHeightDp in 60f..66f)
            assertTrue(tokens.refractionDp in 1.5f..2.5f)
            assertTrue(tokens.blurDp in 20f..26f)
            assertTrue(tokens.navigationTintOpacity in 0.10f..0.16f)
            assertTrue(tokens.saturation in 1.03f..1.10f)
            assertEquals(0xFFFFFFFF.toInt(), tokens.activeNavigationIconColor)
            assertTrue((tokens.selectionFillColor ushr 24) in 0x8C..0xB2)
        }
    }

    @Test
    fun visualSlotsAlwaysFollowIosReferenceOrderAndKeepFunctionalSources() {
        val host = mapOf(
            HomeDestinationKind.CHATS to "host-chats",
            HomeDestinationKind.UPDATES to "host-updates",
            HomeDestinationKind.COMMUNITIES to "host-communities",
            HomeDestinationKind.CALLS to "host-calls"
        )
        val mapped = requireNotNull(
            HomeVisualSlotMapper.map(host, "settings-action", "own-profile-image")
        )
        assertEquals(
            listOf(
                HomeDestinationKind.UPDATES,
                HomeDestinationKind.CALLS,
                HomeDestinationKind.COMMUNITIES,
                HomeDestinationKind.CHATS,
                HomeDestinationKind.PROFILE
            ),
            mapped.map { it.kind }
        )
        assertEquals("host-updates", mapped[0].functionalSource)
        assertEquals("host-chats", mapped[3].functionalSource)
        assertEquals("settings-action", mapped[4].functionalSource)
        assertEquals("own-profile-image", mapped[4].profileImageSource)
    }

    @Test
    fun unsafeProfileImageUsesNeutralFallbackAndNeverStatusPhoto() {
        val host = mapOf(
            HomeDestinationKind.CHATS to 1,
            HomeDestinationKind.UPDATES to 2,
            HomeDestinationKind.COMMUNITIES to 3,
            HomeDestinationKind.CALLS to 4
        )
        val mapped = requireNotNull(HomeVisualSlotMapper.map(host, null, null))
        assertNull(mapped.last().profileImageSource)
        assertEquals(
            HomeNavigationIconSource.NEUTRAL_PLACEHOLDER,
            HomeNavigationIconPolicy.source(HomeDestinationKind.PROFILE, false)
        )
        assertTrue(HomeNavigationIconPolicy.glyph(HomeDestinationKind.CHATS) ==
            HomeNavigationGlyph.OVERLAPPING_BUBBLES)
        assertFalse(
            HomeOwnProfileAvatarPolicy.isValid(
                hasPhotoDrawable = true,
                locatedInHomeHeader = true,
                signals = HomeSemanticSignals(setOf("recent_status_avatar"), "Status")
            )
        )
        assertTrue(
            HomeOwnProfileAvatarPolicy.isValid(
                hasPhotoDrawable = true,
                locatedInHomeHeader = true,
                signals = HomeSemanticSignals(setOf("settings_profile_photo"), "My profile")
            )
        )
        assertTrue(
            HomeSettingsActionPolicy.isValid(
                clickable = true,
                visible = true,
                signals = HomeSemanticSignals(setOf("settings"), "Settings")
            )
        )
    }

    @Test
    fun hostVisualSuppressionAndFabRollbackRestoreOriginalState() {
        val original = HomeHostVisualState(alpha = 0.72f, importantForAccessibility = 1)
        val suppressed = HomeHostVisualPolicy.suppressed(original)
        assertEquals(0f, suppressed.alpha)
        assertEquals(4, suppressed.importantForAccessibility)
        assertEquals(original, HomeHostVisualPolicy.restored(original))
    }

    @Test
    fun activePillAndOpticalBoxesStayInsideExactlyOneDestinationCell() {
        val pill = HomeSelectionPillPolicy.size(84, 62, 2, 6)
        assertTrue(pill.width in 1..84)
        assertTrue(pill.height in 1..62)
        assertEquals(80, pill.width)
        assertEquals(50, pill.height)
        assertTrue(pill.width > pill.height)
        assertEquals(29.0f, HomeIconBoxPolicy.normalizedIconSize(42f, 29f))
        assertEquals(32.0f, HomeIconBoxPolicy.normalizedAvatarSize(42f, 32f))
    }

    @Test
    fun scrollHideShowUsesThresholdAndAlwaysShowsAtTop() {
        val policy = HomeScrollVisibilityPolicy(downThresholdPx = 24, upThresholdPx = 12)
        assertFalse(policy.onScroll(12, atTop = false))
        assertTrue(policy.onScroll(12, atTop = false))
        assertTrue(policy.onScroll(-6, atTop = false))
        assertFalse(policy.onScroll(-6, atTop = false))
        assertTrue(policy.onScroll(24, atTop = false))
        assertFalse(policy.onScroll(0, atTop = true))
    }

    @Test
    fun absoluteScrollOffsetSurvivesChildRecyclingAndTopForcesVisible() {
        val tracker = HomeScrollOffsetTracker(initialOffsetPx = 100)
        assertEquals(24, tracker.sample(124, atTop = false).deltaPx)
        assertEquals(76, tracker.sample(200, atTop = false).deltaPx)
        val top = tracker.sample(999, atTop = true)
        assertEquals(0, top.offsetPx)
        assertEquals(-200, top.deltaPx)
    }

    @Test
    fun obsoleteEmptyHostNavContainerIsTheOnlyCollapsibleShape() {
        assertTrue(HomeNavParentCollapsePolicy.shouldCollapse(HomeNavParentShape(false, 0, 0, 72f)))
        assertFalse(HomeNavParentCollapsePolicy.shouldCollapse(HomeNavParentShape(true, 0, 0, 72f)))
        assertFalse(HomeNavParentCollapsePolicy.shouldCollapse(HomeNavParentShape(false, 1, 1, 72f)))
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
