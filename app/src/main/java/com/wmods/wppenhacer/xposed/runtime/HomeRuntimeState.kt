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
    val profileSourceDiscovered: Boolean = false,
    val profileActionMapped: Boolean = false,
    val hostFabDetected: Boolean = false,
    val hostFabVisualSuppressed: Boolean = false,
    val customFabAttached: Boolean = false,
    val outerTintOpacity: Float? = null,
    val productionRefractionStrengthDp: Float? = null,
    val productionBlurStrengthDp: Float? = null,
    val barHeightDp: Float? = null,
    val activePillBounds: String? = null,
    val scrollHideShowInstalled: Boolean = false,
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
