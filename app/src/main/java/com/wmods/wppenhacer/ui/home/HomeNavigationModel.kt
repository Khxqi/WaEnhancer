package com.wmods.wppenhacer.ui.home

enum class HomeDestinationKind {
    UPDATES,
    CALLS,
    COMMUNITIES,
    CHATS,
    PROFILE
}

data class HomeVisualSlot<T>(
    val kind: HomeDestinationKind,
    val functionalSource: T
)

object HomeVisualSlotMapper {
    val ORDER = listOf(
        HomeDestinationKind.UPDATES,
        HomeDestinationKind.CALLS,
        HomeDestinationKind.COMMUNITIES,
        HomeDestinationKind.CHATS,
        HomeDestinationKind.PROFILE
    )

    fun <T> map(
        hostDestinations: Map<HomeDestinationKind, T>,
        profileSource: T?
    ): List<HomeVisualSlot<T>>? {
        if (profileSource == null) return null
        val complete = hostDestinations + (HomeDestinationKind.PROFILE to profileSource)
        return ORDER.map { kind ->
            HomeVisualSlot(kind, complete[kind] ?: return null)
        }
    }
}

object HomeProfileSourcePolicy {
    fun isValid(
        clickable: Boolean,
        hasDrawable: Boolean,
        preservesOriginalColor: Boolean,
        hasAccessibleMeaning: Boolean,
        locatedInHomeHeader: Boolean
    ): Boolean = clickable && hasDrawable && preservesOriginalColor &&
        hasAccessibleMeaning && locatedInHomeHeader
}

data class HomeHostVisualState(
    val alpha: Float,
    val importantForAccessibility: Int
)

object HomeHostVisualPolicy {
    private const val NO_HIDE_DESCENDANTS = 4

    fun suppressed(original: HomeHostVisualState): HomeHostVisualState = original.copy(
        alpha = 0f,
        importantForAccessibility = NO_HIDE_DESCENDANTS
    )

    fun restored(original: HomeHostVisualState): HomeHostVisualState = original
}

data class HomePillSize(val width: Int, val height: Int)

object HomeSelectionPillPolicy {
    fun size(
        cellWidth: Int,
        cellHeight: Int,
        horizontalInset: Int,
        verticalInset: Int
    ): HomePillSize {
        val safeWidth = cellWidth.coerceAtLeast(1)
        val safeHeight = cellHeight.coerceAtLeast(1)
        return HomePillSize(
            width = (safeWidth - horizontalInset.coerceAtLeast(0) * 2)
                .coerceIn(1, safeWidth),
            height = (safeHeight - verticalInset.coerceAtLeast(0) * 2)
                .coerceIn(1, safeHeight)
        )
    }
}

object HomeIconBoxPolicy {
    fun normalizedIconSize(iconBoxDp: Float, requestedDp: Float): Float =
        requestedDp.coerceIn(iconBoxDp * 0.58f, iconBoxDp * 0.78f)

    fun normalizedAvatarSize(iconBoxDp: Float, requestedDp: Float): Float =
        requestedDp.coerceIn(iconBoxDp * 0.68f, iconBoxDp * 0.84f)
}

class HomeScrollVisibilityPolicy(private val thresholdPx: Int) {
    var hidden: Boolean = false
        private set

    private var accumulated = 0
    private var direction = 0

    fun onScroll(deltaY: Int, atTop: Boolean): Boolean {
        if (atTop) {
            hidden = false
            accumulated = 0
            direction = 0
            return hidden
        }
        if (deltaY == 0) return hidden
        val nextDirection = if (deltaY > 0) 1 else -1
        if (nextDirection != direction) {
            direction = nextDirection
            accumulated = 0
        }
        accumulated += deltaY
        val threshold = thresholdPx.coerceAtLeast(1)
        if (!hidden && accumulated >= threshold) {
            hidden = true
            accumulated = 0
        } else if (hidden && accumulated <= -threshold) {
            hidden = false
            accumulated = 0
        }
        return hidden
    }
}
