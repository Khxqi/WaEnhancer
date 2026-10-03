package com.wmods.wppenhacer.ui.home

import android.graphics.Color
import com.wmods.wppenhacer.ui.glass.GlassStyle

data class LiquidGlassTokens(
    val homeBackgroundColor: Int,
    val navigationTintColor: Int,
    val navigationTintOpacity: Float,
    val selectionFillColor: Int,
    val selectionHighlightColor: Int,
    val selectionStrokeColor: Int,
    val activeNavigationIconColor: Int,
    val primaryContentColor: Int,
    val secondaryContentColor: Int,
    val actionFillColor: Int,
    val actionHighlightColor: Int,
    val actionStrokeColor: Int,
    val blurDp: Float,
    val refractionDp: Float,
    val edgeIntensity: Float,
    val highlightIntensity: Float,
    val depth: Float,
    val saturation: Float,
    val horizontalMarginDp: Float,
    val bottomMarginDp: Float,
    val navigationHeightDp: Float,
    val navigationIconDp: Float,
    val navigationIconBoxDp: Float,
    val navigationAvatarDp: Float,
    val navigationHorizontalContentInsetDp: Float,
    val selectionHorizontalInsetDp: Float,
    val selectionVerticalInsetDp: Float,
    val inactiveIconAlpha: Float,
    val badgeColor: Int,
    val badgeTextColor: Int,
    val badgeStrokeColor: Int,
    val badgeMinSizeDp: Float,
    val badgeDotSizeDp: Float,
    val badgeTextSp: Float,
    val actionSizeDp: Float,
    val actionIconDp: Float,
    val actionElevationDp: Float,
    val floatingActionSizeDp: Float,
    val floatingActionGapDp: Float,
    val navigationElevationDp: Float,
    val outerRimDp: Float,
    val homeHorizontalMarginDp: Float,
    val searchHorizontalPaddingDp: Float,
    val scrollThresholdDp: Float,
    val navigationVisibilityDurationMs: Long,
    val searchRadiusDp: Float,
    val titleTextSp: Float,
    val selectionDurationMs: Long
) {
    fun navigationStyle(density: Float): GlassStyle = GlassStyle(
        blurRadiusPx = blurDp * density,
        tintColor = navigationTintColor,
        tintOpacity = navigationTintOpacity,
        refractionStrengthPx = refractionDp * density,
        edgeIntensity = edgeIntensity,
        highlightIntensity = highlightIntensity,
        cornerRadiusPx = navigationHeightDp * density / 2f,
        depth = depth,
        saturation = saturation,
        animationProgress = 0.5f
    ).sanitized()

    companion object {
        fun forDarkMode(darkMode: Boolean): LiquidGlassTokens = if (darkMode) DARK else LIGHT

        val DARK = LiquidGlassTokens(
            homeBackgroundColor = 0xFF07080A.toInt(),
            navigationTintColor = 0xFFE2E4E8.toInt(),
            navigationTintOpacity = 0.13f,
            selectionFillColor = 0xA8101216.toInt(),
            selectionHighlightColor = 0x902C2F35.toInt(),
            selectionStrokeColor = 0x42FFFFFF,
            activeNavigationIconColor = Color.WHITE,
            primaryContentColor = Color.WHITE,
            secondaryContentColor = 0xFFD5D7DC.toInt(),
            actionFillColor = 0x26E7E8EB,
            actionHighlightColor = 0x54FFFFFF,
            actionStrokeColor = 0x46FFFFFF,
            blurDp = 24f,
            refractionDp = 2.2f,
            edgeIntensity = 0.58f,
            highlightIntensity = 0.56f,
            depth = 0.72f,
            saturation = 1.06f,
            horizontalMarginDp = 16f,
            bottomMarginDp = 12f,
            navigationHeightDp = 62f,
            navigationIconDp = 29f,
            navigationIconBoxDp = 42f,
            navigationAvatarDp = 32f,
            navigationHorizontalContentInsetDp = 4f,
            selectionHorizontalInsetDp = 3f,
            selectionVerticalInsetDp = 5f,
            inactiveIconAlpha = 0.82f,
            badgeColor = 0xFF25D366.toInt(),
            badgeTextColor = 0xFF07130B.toInt(),
            badgeStrokeColor = 0x70FFFFFF,
            badgeMinSizeDp = 17f,
            badgeDotSizeDp = 8f,
            badgeTextSp = 10f,
            actionSizeDp = 38f,
            actionIconDp = 20f,
            actionElevationDp = 2f,
            floatingActionSizeDp = 48f,
            floatingActionGapDp = 10f,
            navigationElevationDp = 5f,
            outerRimDp = 0.85f,
            homeHorizontalMarginDp = 16f,
            searchHorizontalPaddingDp = 14f,
            scrollThresholdDp = 28f,
            navigationVisibilityDurationMs = 190L,
            searchRadiusDp = 18f,
            titleTextSp = 31f,
            selectionDurationMs = 220L
        )

        val LIGHT = DARK.copy(
            homeBackgroundColor = 0xFFF5F5F7.toInt(),
            navigationTintColor = Color.WHITE,
            navigationTintOpacity = 0.14f,
            selectionFillColor = 0xA81A1B1F.toInt(),
            selectionHighlightColor = 0x90383A40.toInt(),
            selectionStrokeColor = 0x48FFFFFF,
            activeNavigationIconColor = Color.WHITE,
            primaryContentColor = 0xFF111216.toInt(),
            secondaryContentColor = 0xFF62646B.toInt(),
            actionFillColor = 0x2EFFFFFF,
            actionHighlightColor = 0x70FFFFFF,
            actionStrokeColor = 0x50FFFFFF,
            edgeIntensity = 0.66f,
            saturation = 1.04f,
            badgeTextColor = Color.WHITE
        )
    }
}

data class HomeNavigationShape(
    val activityIsHome: Boolean,
    val visible: Boolean,
    val isViewGroup: Boolean,
    val nearBottom: Boolean,
    val widthFraction: Float,
    val heightDp: Float,
    val destinationCenters: List<Int>
)

data class HomeNavigationValidation(
    val valid: Boolean,
    val reason: String? = null
)

object HomeNavigationValidator {
    const val MIN_DESTINATIONS = 3
    const val MAX_DESTINATIONS = 6

    fun validate(shape: HomeNavigationShape): HomeNavigationValidation {
        if (!shape.activityIsHome) return invalid("not-home-activity")
        if (!shape.visible) return invalid("navigation-not-visible")
        if (!shape.isViewGroup) return invalid("navigation-not-view-group")
        if (!shape.nearBottom) return invalid("navigation-not-near-bottom")
        if (shape.widthFraction < 0.55f) return invalid("navigation-too-narrow")
        if (shape.heightDp !in 40f..160f) return invalid("navigation-height-out-of-range")
        if (shape.destinationCenters.size !in MIN_DESTINATIONS..MAX_DESTINATIONS) {
            return invalid("destination-count-out-of-range")
        }
        if (shape.destinationCenters.zipWithNext().any { (left, right) -> right <= left }) {
            return invalid("destinations-not-horizontally-ordered")
        }
        return HomeNavigationValidation(valid = true)
    }

    private fun invalid(reason: String) = HomeNavigationValidation(false, reason)
}

object HomeSelectedDestination {
    fun resolve(states: List<Boolean>, previous: Int? = null): Int? {
        var selectedIndex = -1
        var selectedCount = 0
        states.forEachIndexed { index, active ->
            if (active) {
                selectedIndex = index
                selectedCount++
            }
        }
        return resolve(selectedCount, selectedIndex, states.size, previous)
    }

    fun resolve(
        selectedCount: Int,
        selectedIndex: Int,
        destinationCount: Int,
        previous: Int? = null
    ): Int? {
        return when {
            selectedCount == 1 && selectedIndex in 0 until destinationCount -> selectedIndex
            previous != null && previous in 0 until destinationCount -> previous
            else -> null
        }
    }
}

object HomeBottomInsetPolicy {
    fun bottomMarginPx(systemGestureInsetPx: Int, baseMarginPx: Int): Int =
        systemGestureInsetPx.coerceAtLeast(0) + baseMarginPx.coerceAtLeast(0)

    fun shouldShowNavigation(imeVisible: Boolean): Boolean = !imeVisible
}

object HomeGlassSurfaceOwnership {
    fun canAttach(existingSurfaceCount: Int, alreadyAttached: Boolean): Boolean =
        existingSurfaceCount == 0 && !alreadyAttached
}

object HomeCustomNavigationPolicy {
    const val VISIBLE_LABEL_COUNT = 0
    const val REQUIRED_VISUAL_DESTINATIONS = 5

    fun canReplace(hostDestinationCount: Int, mirrorableIconCount: Int): Boolean {
        return hostDestinationCount == REQUIRED_VISUAL_DESTINATIONS &&
            mirrorableIconCount == hostDestinationCount
    }
}
