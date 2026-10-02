package com.wmods.wppenhacer.ui.home

import android.graphics.Color
import com.wmods.wppenhacer.ui.glass.GlassStyle

data class LiquidGlassTokens(
    val homeBackgroundColor: Int,
    val navigationTintColor: Int,
    val navigationTintOpacity: Float,
    val selectionFillColor: Int,
    val selectionStrokeColor: Int,
    val primaryContentColor: Int,
    val secondaryContentColor: Int,
    val actionFillColor: Int,
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
    val selectionInsetDp: Float,
    val actionSizeDp: Float,
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
            navigationTintColor = 0xFF17191D.toInt(),
            navigationTintOpacity = 0.30f,
            selectionFillColor = 0xA80A0B0E.toInt(),
            selectionStrokeColor = 0x40FFFFFF,
            primaryContentColor = Color.WHITE,
            secondaryContentColor = 0xFFB8BAC0.toInt(),
            actionFillColor = 0x521C1E23,
            actionStrokeColor = 0x38FFFFFF,
            blurDp = 26f,
            refractionDp = 5.5f,
            edgeIntensity = 0.58f,
            highlightIntensity = 0.40f,
            depth = 0.82f,
            saturation = 1.10f,
            horizontalMarginDp = 18f,
            bottomMarginDp = 12f,
            navigationHeightDp = 76f,
            selectionInsetDp = 7f,
            actionSizeDp = 40f,
            searchRadiusDp = 18f,
            titleTextSp = 31f,
            selectionDurationMs = 220L
        )

        val LIGHT = DARK.copy(
            homeBackgroundColor = 0xFFF5F5F7.toInt(),
            navigationTintColor = Color.WHITE,
            navigationTintOpacity = 0.25f,
            selectionFillColor = 0xB8FFFFFF.toInt(),
            selectionStrokeColor = 0x55FFFFFF,
            primaryContentColor = 0xFF111216.toInt(),
            secondaryContentColor = 0xFF62646B.toInt(),
            actionFillColor = 0x66FFFFFF,
            actionStrokeColor = 0x70FFFFFF,
            edgeIntensity = 0.66f,
            saturation = 1.06f
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
