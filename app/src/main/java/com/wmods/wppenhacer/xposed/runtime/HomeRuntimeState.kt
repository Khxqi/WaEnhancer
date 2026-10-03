package com.wmods.wppenhacer.xposed.runtime

enum class HomeRedesignStatus {
    DISABLED,
    WAITING_FOR_HOME,
    DISCOVERING,
    READY,
    FAILED,
    DETACHED
}

data class HomeRuntimeSnapshot(
    val enabled: Boolean = false,
    val status: HomeRedesignStatus = HomeRedesignStatus.DISABLED,
    val lifecycleCallbackRegistered: Boolean = false,
    val homeActivityDetected: Boolean = false,
    val lastActivityClass: String? = null,
    val discoveryAttempts: Int = 0,
    val stockBottomNavDetected: Boolean = false,
    val stockBottomNavClass: String? = null,
    val destinationCount: Int = 0,
    val stockNavMovedIntoSurface: Boolean = false,
    val floatingBottomBarAttached: Boolean = false,
    val customNavigationVisible: Boolean = false,
    val hostNavigationVisuallyHidden: Boolean = false,
    val visibleNavigationLabelCount: Int = 0,
    val activeDestinationIndex: Int? = null,
    val badgeCountDetected: Int = 0,
    val customTopActionCount: Int = 0,
    val visualDestinationOrder: String? = null,
    val navIconSource: String? = null,
    val chatsIconStyle: String? = null,
    val selectionShape: String? = null,
    val profileSourceDiscovered: Boolean = false,
    val profileActionMapped: Boolean = false,
    val ownProfileAvatarResolved: Boolean = false,
    val profileImageSourceClass: String? = null,
    val profileImageSourceResource: String? = null,
    val settingsActionMapped: Boolean = false,
    val settingsActionSourceClass: String? = null,
    val hostFabDetected: Boolean = false,
    val hostFabVisualSuppressed: Boolean = false,
    val customFabAttached: Boolean = false,
    val outerTintOpacity: Float? = null,
    val productionRefractionStrengthDp: Float? = null,
    val productionBlurStrengthDp: Float? = null,
    val barHeightDp: Float? = null,
    val activePillBounds: String? = null,
    val scrollHideShowInstalled: Boolean = false,
    val scrollableClass: String? = null,
    val scrollableResourceName: String? = null,
    val scrollEventsObserved: Long = 0,
    val lastScrollOffsetPx: Int = 0,
    val lastScrollDeltaPx: Int = 0,
    val barHiddenByScroll: Boolean = false,
    val originalNavParentClass: String? = null,
    val originalNavParentHeight: Int? = null,
    val navigationBarInsetBottom: Int = 0,
    val gestureInsetBottom: Int = 0,
    val blackStripSource: String? = null,
    val productionGlassMode: String? = null,
    val centerRefractionStrengthDp: Float? = null,
    val edgeRefractionStrengthDp: Float? = null,
    val glassSurfaceCount: Int = 0,
    val hierarchyCaptureCount: Long = 0,
    val lastCaptureMs: Double? = null,
    val rollingCaptureMs: Double? = null,
    val worstCaptureMs: Double? = null,
    val approximateRenderNodeBytes: Long? = null,
    val contentPaddingApplied: Boolean = false,
    val toolbarStyled: Boolean = false,
    val searchSurfaceStyled: Boolean = false,
    val runtimeVerified: Boolean = false,
    val failure: String? = null
)

object HomeRuntimeState {
    @Volatile private var snapshot = HomeRuntimeSnapshot()

    @Synchronized
    fun update(value: HomeRuntimeSnapshot) {
        snapshot = value
    }

    @Synchronized
    fun update(transform: (HomeRuntimeSnapshot) -> HomeRuntimeSnapshot) {
        snapshot = transform(snapshot)
    }

    fun snapshot(): HomeRuntimeSnapshot = snapshot
}
