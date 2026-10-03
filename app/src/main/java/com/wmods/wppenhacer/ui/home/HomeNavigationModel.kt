package com.wmods.wppenhacer.ui.home

enum class HomeDestinationKind {
    UPDATES,
    CALLS,
    COMMUNITIES,
    CHATS,
    PROFILE
}

enum class HomeNavigationGlyph {
    STATUS_RING,
    HANDSET,
    THREE_PEOPLE,
    OVERLAPPING_BUBBLES,
    PROFILE_PLACEHOLDER
}

enum class HomeNavigationIconSource {
    CUSTOM_VECTOR,
    OWN_ACCOUNT_AVATAR,
    NEUTRAL_PLACEHOLDER
}

data class HomeVisualSlot<T>(
    val kind: HomeDestinationKind,
    val functionalSource: T?,
    val profileImageSource: T? = null
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
        settingsActionSource: T?,
        profileImageSource: T?
    ): List<HomeVisualSlot<T>>? {
        return ORDER.map { kind ->
            if (kind == HomeDestinationKind.PROFILE) {
                HomeVisualSlot(
                    kind = kind,
                    functionalSource = settingsActionSource,
                    profileImageSource = profileImageSource
                )
            } else {
                HomeVisualSlot(kind, hostDestinations[kind] ?: return null)
            }
        }
    }
}

object HomeNavigationIconPolicy {
    fun glyph(kind: HomeDestinationKind): HomeNavigationGlyph = when (kind) {
        HomeDestinationKind.UPDATES -> HomeNavigationGlyph.STATUS_RING
        HomeDestinationKind.CALLS -> HomeNavigationGlyph.HANDSET
        HomeDestinationKind.COMMUNITIES -> HomeNavigationGlyph.THREE_PEOPLE
        HomeDestinationKind.CHATS -> HomeNavigationGlyph.OVERLAPPING_BUBBLES
        HomeDestinationKind.PROFILE -> HomeNavigationGlyph.PROFILE_PLACEHOLDER
    }

    fun source(kind: HomeDestinationKind, ownAvatarResolved: Boolean): HomeNavigationIconSource =
        when {
            kind != HomeDestinationKind.PROFILE -> HomeNavigationIconSource.CUSTOM_VECTOR
            ownAvatarResolved -> HomeNavigationIconSource.OWN_ACCOUNT_AVATAR
            else -> HomeNavigationIconSource.NEUTRAL_PLACEHOLDER
        }
}

data class HomeSemanticSignals(
    val resourceNames: Set<String>,
    val accessibilityLabel: String?
) {
    fun containsAny(tokens: Set<String>): Boolean {
        val label = accessibilityLabel?.lowercase().orEmpty()
        return tokens.any { token ->
            label.contains(token) || resourceNames.any { name -> name.contains(token) }
        }
    }
}

object HomeOwnProfileAvatarPolicy {
    val positiveTokens = setOf(
        "my_profile",
        "my profile",
        "own_profile",
        "own profile",
        "profile_photo",
        "profile photo",
        "profile_picture",
        "profile picture",
        "account_avatar",
        "account avatar",
        "settings_profile",
        "settings profile",
        "mein profil",
        "profilbild"
    )
    val rejectedTokens = setOf(
        "status",
        "story",
        "update",
        "aktuell",
        "contact",
        "kontakt",
        "chat",
        "participant",
        "teilnehmer",
        "recent"
    )

    fun isValid(
        hasPhotoDrawable: Boolean,
        locatedInHomeHeader: Boolean,
        signals: HomeSemanticSignals
    ): Boolean = hasPhotoDrawable && locatedInHomeHeader &&
        signals.containsAny(positiveTokens) && !signals.containsAny(rejectedTokens)
}

object HomeSettingsActionPolicy {
    val settingsTokens = setOf("settings", "setting", "einstellungen")

    fun isValid(
        clickable: Boolean,
        visible: Boolean,
        signals: HomeSemanticSignals
    ): Boolean = clickable && visible && signals.containsAny(settingsTokens)
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

class HomeScrollVisibilityPolicy(
    private val downThresholdPx: Int,
    private val upThresholdPx: Int
) {
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
        val threshold = if (nextDirection > 0) {
            downThresholdPx.coerceAtLeast(1)
        } else {
            upThresholdPx.coerceAtLeast(1)
        }
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

data class HomeScrollSample(
    val offsetPx: Int,
    val deltaPx: Int,
    val atTop: Boolean
)

class HomeScrollOffsetTracker(initialOffsetPx: Int) {
    private var previousOffsetPx = initialOffsetPx.coerceAtLeast(0)

    fun sample(currentOffsetPx: Int, atTop: Boolean): HomeScrollSample {
        val safeOffset = if (atTop) 0 else currentOffsetPx.coerceAtLeast(0)
        val delta = if (atTop) -previousOffsetPx else safeOffset - previousOffsetPx
        previousOffsetPx = safeOffset
        return HomeScrollSample(safeOffset, delta, atTop)
    }
}

data class HomeNavParentShape(
    val isContentRoot: Boolean,
    val remainingChildCount: Int,
    val visibleRemainingChildCount: Int,
    val heightDp: Float
)

object HomeNavParentCollapsePolicy {
    fun shouldCollapse(shape: HomeNavParentShape): Boolean =
        !shape.isContentRoot && shape.remainingChildCount == 0 &&
            shape.visibleRemainingChildCount == 0 && shape.heightDp in 36f..180f
}

enum class HomeBlackStripSource {
    HOST_NAV_CONTAINER,
    SYSTEM_NAVIGATION,
    NONE,
    UNKNOWN
}
