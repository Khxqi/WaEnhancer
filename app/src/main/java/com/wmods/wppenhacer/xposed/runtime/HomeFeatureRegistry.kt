package com.wmods.wppenhacer.xposed.runtime

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.wmods.wppenhacer.ui.glass.GlassCapabilities
import com.wmods.wppenhacer.ui.glass.LocalizedGlassDrawMetrics
import com.wmods.wppenhacer.ui.glass.LocalizedGlassEvent
import com.wmods.wppenhacer.ui.glass.LocalizedGlassFailureStage
import com.wmods.wppenhacer.ui.glass.LocalizedGlassGeometry
import com.wmods.wppenhacer.ui.glass.LocalizedGlassMetrics
import com.wmods.wppenhacer.ui.home.GlassHomeActionOverlay
import com.wmods.wppenhacer.ui.home.GlassNavigationSurface
import com.wmods.wppenhacer.ui.home.HomeBottomInsetPolicy
import com.wmods.wppenhacer.ui.home.HomeCustomNavigationPolicy
import com.wmods.wppenhacer.ui.home.HomeGlassSurfaceOwnership
import com.wmods.wppenhacer.ui.home.HomeNavigationShape
import com.wmods.wppenhacer.ui.home.HomeNavigationValidator
import com.wmods.wppenhacer.ui.home.HomeVisualMirror
import com.wmods.wppenhacer.ui.home.LiquidGlassTokens
import java.util.WeakHashMap
import kotlin.math.max

object HomeRedesignPolicy {
    fun enabled(config: RuntimeConfigSnapshot): Boolean =
        config.enableIosHomeRedesign &&
            !config.disableAllHooks &&
            !config.disableVisualModifications &&
            !config.safeMode &&
            config.transportStatus != ConfigTransportStatus.FAILED_CLOSED
}

object LegacyHomeConflictPolicy {
    private val conflictingClasses = setOf(
        "com.wmods.wppenhacer.xposed.features.customization.CustomToolbar",
        "com.wmods.wppenhacer.xposed.features.customization.CustomView",
        "com.wmods.wppenhacer.xposed.features.customization.CustomThemeV2",
        "com.wmods.wppenhacer.xposed.features.customization.FloatingBottomBar",
        "com.wmods.wppenhacer.xposed.features.customization.SeparateGroup",
        "com.wmods.wppenhacer.xposed.features.customization.HideTabs"
    )

    fun blocks(className: String, config: RuntimeConfigSnapshot): Boolean =
        config.enableIosHomeRedesign && className in conflictingClasses

    fun blockedClasses(): Set<String> = conflictingClasses
}

object HomeFeatureRegistry {
    val FEATURE_ID = FeatureId("visual.home.ios-liquid-glass")

    fun register(
        registry: FeatureRegistry,
        capabilities: CapabilityRegistry,
        application: Application,
        config: RuntimeConfigSnapshot
    ) {
        HomeRuntimeState.update(
            HomeRuntimeSnapshot(
                enabled = HomeRedesignPolicy.enabled(config),
                status = if (HomeRedesignPolicy.enabled(config)) {
                    HomeRedesignStatus.WAITING_FOR_HOME
                } else {
                    HomeRedesignStatus.DISABLED
                }
            )
        )
        registry.register(
            FeatureSpec(
                id = FEATURE_ID,
                diagnosticName = "iOS Liquid Glass Home chrome",
                category = FeatureCategory.VISUAL,
                requiredCapabilities = setOf(GlassFeatureRegistry.LOCALIZED_SAME_WINDOW),
                enabled = { HomeRedesignPolicy.enabled(config) },
                installer = {
                    val platform = requireNotNull(
                        capabilities.get<GlassCapabilities>(
                            GlassFeatureRegistry.LOCALIZED_SAME_WINDOW
                        )
                    )
                    val controller = IosHomeChromeController(application, platform)
                    controller.start()
                    register("home.activity-lifecycle", object : HookHandle {
                        override fun unhook() = controller.stop()
                    })
                }
            )
        )
    }
}

private data class ViewPaddingSnapshot(
    val start: Int,
    val top: Int,
    val end: Int,
    val bottom: Int,
    val clipToPadding: Boolean?
)

private data class ChromeSnapshot(
    val contentBackground: Drawable?,
    val toolbar: ViewGroup?,
    val toolbarBackground: Drawable?,
    val toolbarElevation: Float,
    val toolbarLogo: View?,
    val toolbarLogoVisibility: Int,
    val titleView: TextView?,
    val chatDestinationIndex: Int?,
    val actionOverlay: GlassHomeActionOverlay?,
    val searchView: View?,
    val searchBackground: Drawable?
)

private data class HomeAttachment(
    val activity: Activity,
    val contentRoot: ViewGroup,
    val surfaceParent: FrameLayout,
    val stockNavigation: ViewGroup,
    val originalParent: ViewGroup,
    val originalIndex: Int,
    val originalLayoutParams: ViewGroup.LayoutParams,
    val surface: GlassNavigationSurface,
    val paddedContent: ViewGroup?,
    val paddingSnapshot: ViewPaddingSnapshot?,
    val chrome: ChromeSnapshot
)

private data class HomeNavigationDiscovery(
    val navigation: ViewGroup,
    val destinations: List<View>,
    val badgeCount: Int,
    val chatDestinationIndex: Int?
)

private class IosHomeChromeController(
    private val application: Application,
    private val capabilities: GlassCapabilities
) : Application.ActivityLifecycleCallbacks {
    private val attachments = WeakHashMap<Activity, HomeAttachment>()
    private val attempts = WeakHashMap<Activity, Int>()
    private val resumedActivities = WeakHashMap<Activity, Boolean>()
    private val diagnostics = RuntimeDiagnosticsRepublisher(application)

    fun start() {
        application.registerActivityLifecycleCallbacks(this)
        HomeRuntimeState.update { current ->
            current.copy(
                enabled = true,
                status = HomeRedesignStatus.WAITING_FOR_HOME,
                lifecycleCallbackRegistered = true,
                failure = null
            )
        }
        RuntimeTrace.event("home-redesign-controller-start")
        diagnostics.request("home.controller.start")
    }

    fun stop() {
        application.unregisterActivityLifecycleCallbacks(this)
        attachments.keys.toList().forEach(::detach)
        attachments.clear()
        attempts.clear()
        resumedActivities.clear()
        HomeRuntimeState.update { current ->
            current.copy(
                status = HomeRedesignStatus.DETACHED,
                lifecycleCallbackRegistered = false,
                floatingBottomBarAttached = false,
                glassSurfaceCount = 0,
                customNavigationVisible = false,
                hostNavigationVisuallyHidden = false,
                visibleNavigationLabelCount = 0,
                customTopActionCount = 0
            )
        }
        RuntimeTrace.event("home-redesign-controller-stop")
        diagnostics.request("home.controller.stop")
    }

    override fun onActivityResumed(activity: Activity) {
        resumedActivities[activity] = true
        val activityClass = activity.javaClass.name
        val isHome = isHomeActivity(activity)
        HomeRuntimeState.update { current ->
            current.copy(
                lastActivityClass = activityClass,
                homeActivityDetected = isHome || current.homeActivityDetected,
                status = if (isHome) HomeRedesignStatus.DISCOVERING else current.status
            )
        }
        if (!isHome || attachments.containsKey(activity)) return
        scheduleAttach(activity, attempt = 0)
    }

    override fun onActivityPaused(activity: Activity) {
        resumedActivities.remove(activity)
        attempts.remove(activity)
        detach(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        resumedActivities.remove(activity)
        attempts.remove(activity)
        detach(activity)
    }
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit

    private fun scheduleAttach(activity: Activity, attempt: Int) {
        if (!isHomeActivity(activity) || resumedActivities[activity] != true ||
            activity.isFinishing || activity.isDestroyed
        ) return
        attempts[activity] = attempt
        HomeRuntimeState.update { current ->
            current.copy(discoveryAttempts = max(current.discoveryAttempts, attempt + 1))
        }
        activity.window.decorView.postDelayed(
            { attach(activity, attempt) },
            if (attempt == 0) 0L else DISCOVERY_RETRY_MS
        )
    }

    private fun attach(activity: Activity, attempt: Int) {
        if (attachments.containsKey(activity) || !isHomeActivity(activity) ||
            resumedActivities[activity] != true
        ) return
        if (Build.VERSION.SDK_INT < 33 || !capabilities.localizedSameWindowAvailable) {
            fail(activity, "localized-glass-capability-unavailable")
            return
        }
        val contentRoot = activity.findViewById<View>(android.R.id.content) as? ViewGroup
        val surfaceParent = activity.window.decorView as? FrameLayout
        if (contentRoot == null || surfaceParent == null || contentRoot === surfaceParent) {
            fail(activity, "invalid-home-root-structure")
            return
        }
        val discovery = discoverNavigation(activity, contentRoot)
        if (discovery == null) {
            if (attempt + 1 < MAX_DISCOVERY_ATTEMPTS) {
                scheduleAttach(activity, attempt + 1)
            } else {
                fail(activity, "validated-bottom-navigation-not-found")
            }
            return
        }
        if (!HomeGlassSurfaceOwnership.canAttach(attachments.size, attachments.containsKey(activity))) {
            fail(activity, "duplicate-glass-surface-prevented")
            return
        }

        val navigation = discovery.navigation
        val originalParent = navigation.parent as? ViewGroup
        val originalLayoutParams = navigation.layoutParams
        if (originalParent == null || originalLayoutParams == null) {
            fail(activity, "navigation-parent-unavailable")
            return
        }
        val originalIndex = originalParent.indexOfChild(navigation)
        val darkMode = (activity.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val tokens = LiquidGlassTokens.forDarkMode(darkMode)
        lateinit var surface: GlassNavigationSurface
        surface = GlassNavigationSurface(
            context = activity,
            samplingRoot = contentRoot,
            tokens = tokens,
            onMetrics = ::recordMetrics,
            onGeometry = { geometry ->
                RuntimeTrace.event(
                    "home-glass-geometry",
                    "x=${geometry.surfaceWindowX} y=${geometry.surfaceWindowY} " +
                        "width=${geometry.surfaceWidth} height=${geometry.surfaceHeight}"
                )
            },
            onDrawMetrics = { _: LocalizedGlassDrawMetrics -> Unit },
            onRenderEvent = { event -> recordRenderEvent(event) },
            onRenderFailure = { stage, throwable ->
                activity.runOnUiThread {
                    detach(activity)
                    fail(activity, "render-${stage.name.lowercase()}", throwable)
                }
            },
            onSelectedDestinationChanged = { selected ->
                HomeRuntimeState.update { current ->
                    current.copy(activeDestinationIndex = selected)
                }
                attachments[activity]?.let { updateChromeSelection(it.chrome, selected) }
            }
        )

        var moved = false
        var paddedContent: ViewGroup? = null
        var paddingSnapshot: ViewPaddingSnapshot? = null
        var chrome: ChromeSnapshot? = null
        try {
            originalParent.removeView(navigation)
            moved = true
            surface.attachHostNavigation(navigation, discovery.destinations)
            val density = activity.resources.displayMetrics.density
            val side = (tokens.horizontalMarginDp * density).toInt()
            val height = (tokens.navigationHeightDp * density).toInt()
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height,
                Gravity.BOTTOM
            ).apply {
                marginStart = side
                marginEnd = side
                bottomMargin = (tokens.bottomMarginDp * density).toInt()
            }
            surfaceParent.addView(surface, params)
            installInsets(surface, tokens)
            paddedContent = findPrimaryScrollableContent(contentRoot, navigation)
            paddingSnapshot = paddedContent?.let(::capturePadding)
            applyContentPadding(paddedContent, paddingSnapshot, height, params.bottomMargin)
            chrome = applyHomeChrome(
                activity,
                contentRoot,
                surfaceParent,
                tokens,
                discovery.chatDestinationIndex
            )
            val attachment = HomeAttachment(
                activity = activity,
                contentRoot = contentRoot,
                surfaceParent = surfaceParent,
                stockNavigation = navigation,
                originalParent = originalParent,
                originalIndex = originalIndex,
                originalLayoutParams = originalLayoutParams,
                surface = surface,
                paddedContent = paddedContent,
                paddingSnapshot = paddingSnapshot,
                chrome = requireNotNull(chrome)
            )
            attachments[activity] = attachment
            updateChromeSelection(requireNotNull(chrome), surface.selectedDestinationIndex())
            attempts.remove(activity)
            HomeRuntimeState.update { current ->
                current.copy(
                    status = HomeRedesignStatus.READY,
                    stockBottomNavDetected = true,
                    stockBottomNavClass = navigation.javaClass.name,
                    destinationCount = discovery.destinations.size,
                    stockNavMovedIntoSurface = true,
                    floatingBottomBarAttached = true,
                    badgeCountDetected = discovery.badgeCount,
                    glassSurfaceCount = attachments.size,
                    contentPaddingApplied = paddedContent != null,
                    toolbarStyled = chrome?.toolbar != null,
                    searchSurfaceStyled = chrome?.searchView != null,
                    customNavigationVisible = surface.visibleDestinationCount() ==
                        discovery.destinations.size,
                    hostNavigationVisuallyHidden = navigation.alpha == 0f,
                    visibleNavigationLabelCount = HomeCustomNavigationPolicy.VISIBLE_LABEL_COUNT,
                    customTopActionCount = chrome?.actionOverlay?.actionCount() ?: 0,
                    failure = null
                )
            }
            RuntimeTrace.event(
                "home-redesign-attached",
                "destinations=${discovery.destinations.size} surfaces=${attachments.size}"
            )
            ViewCompat.requestApplyInsets(surface)
            diagnostics.request("home.attach.succeeded")
        } catch (throwable: Throwable) {
            runCatching { surfaceParent.removeView(surface) }
            runCatching { surface.detachHostNavigation() }
            paddedContent?.let { padded ->
                paddingSnapshot?.let { original ->
                    padded.setPaddingRelative(
                        original.start,
                        original.top,
                        original.end,
                        original.bottom
                    )
                    original.clipToPadding?.let { padded.clipToPadding = it }
                }
            }
            chrome?.let { restoreChrome(contentRoot, it) }
            if (moved && navigation.parent == null) {
                restoreToOriginalParent(
                    navigation,
                    originalParent,
                    originalIndex,
                    originalLayoutParams
                )
            }
            fail(activity, "home-mutation-rolled-back", throwable)
        }
    }

    private fun discoverNavigation(
        activity: Activity,
        contentRoot: ViewGroup
    ): HomeNavigationDiscovery? {
        val id = activity.resources.getIdentifier("bottom_nav", "id", activity.packageName)
        if (id == 0) return null
        val navigation = contentRoot.findViewById<View>(id) as? ViewGroup ?: return null
        val destinations = clickableLeafDestinations(navigation)
        val location = IntArray(2)
        navigation.getLocationInWindow(location)
        val decorHeight = activity.window.decorView.height.coerceAtLeast(1)
        val decorWidth = activity.window.decorView.width.coerceAtLeast(1)
        val density = activity.resources.displayMetrics.density
        val validation = HomeNavigationValidator.validate(
            HomeNavigationShape(
                activityIsHome = isHomeActivity(activity),
                visible = navigation.isShown && navigation.visibility == View.VISIBLE,
                isViewGroup = true,
                nearBottom = location[1] + navigation.height >= decorHeight * 0.62f,
                widthFraction = navigation.width.toFloat() / decorWidth,
                heightDp = navigation.height / density,
                destinationCenters = destinations.map { destination ->
                    val childLocation = IntArray(2)
                    destination.getLocationInWindow(childLocation)
                    childLocation[0] + destination.width / 2
                }
            )
        )
        if (!validation.valid) {
            RuntimeTrace.event("home-nav-rejected", validation.reason ?: "unknown")
            return null
        }
        return HomeNavigationDiscovery(
            navigation = navigation,
            destinations = destinations,
            badgeCount = destinations.sumOf(::countBadgeViews),
            chatDestinationIndex = findChatsDestination(activity, destinations)
        )
    }

    private fun clickableLeafDestinations(root: ViewGroup): List<View> {
        val candidates = descendants(root)
            .filter { view ->
                view !== root && view.isShown && view.isEnabled && view.isClickable &&
                    view.width > 0 && view.height > 0 &&
                    descendants(view).none { child -> child !== view && child.isClickable }
            }
            .sortedBy { view ->
                val location = IntArray(2)
                view.getLocationInWindow(location)
                location[0]
            }
            .toList()
        return candidates.distinctBy { it }
    }

    private fun countBadgeViews(destination: View): Int = descendants(destination).count { view ->
        if (!view.isShown || view.id == View.NO_ID) return@count false
        val name = runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull()
        name?.contains("badge", ignoreCase = true) == true
    }

    private fun findChatsDestination(activity: Activity, destinations: List<View>): Int? {
        val chatsStringId = activity.resources.getIdentifier("chats", "string", activity.packageName)
        val localizedChats = chatsStringId.takeIf { it != 0 }
            ?.let { runCatching { activity.getString(it) }.getOrNull() }
        return destinations.indexOfFirst { destination ->
            val resourceMatch = if (destination.id != View.NO_ID) {
                runCatching { destination.resources.getResourceEntryName(destination.id) }
                    .getOrNull()?.contains("chat", ignoreCase = true) == true
            } else false
            val descriptionMatch = localizedChats != null &&
                destination.contentDescription?.toString()?.equals(localizedChats, true) == true
            val textMatch = localizedChats != null && descendants(destination)
                .filterIsInstance<TextView>()
                .any { it.text?.toString()?.equals(localizedChats, true) == true }
            resourceMatch || descriptionMatch || textMatch
        }.takeIf { it >= 0 }
    }

    private fun installInsets(surface: GlassNavigationSurface, tokens: LiquidGlassTokens) {
        val density = surface.resources.displayMetrics.density
        val baseMargin = (tokens.bottomMarginDp * density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(surface) { view, insets ->
            val gestures = insets.getInsets(WindowInsetsCompat.Type.systemGestures())
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val params = view.layoutParams as? FrameLayout.LayoutParams
            if (params != null) {
                val target = HomeBottomInsetPolicy.bottomMarginPx(gestures.bottom, baseMargin)
                if (params.bottomMargin != target) {
                    params.bottomMargin = target
                    view.layoutParams = params
                }
            }
            view.visibility = if (HomeBottomInsetPolicy.shouldShowNavigation(imeVisible)) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
            insets
        }
    }

    private fun findPrimaryScrollableContent(root: ViewGroup, excluded: View): ViewGroup? =
        descendants(root)
            .filterIsInstance<ViewGroup>()
            .filter { it !== excluded && it.isShown && it.height > root.height / 3 }
            .filter { it.javaClass.simpleName.contains("RecyclerView", ignoreCase = true) }
            .maxByOrNull { it.width.toLong() * it.height.toLong() }

    private fun capturePadding(view: ViewGroup) = ViewPaddingSnapshot(
        start = view.paddingStart,
        top = view.paddingTop,
        end = view.paddingEnd,
        bottom = view.paddingBottom,
        clipToPadding = view.clipToPadding
    )

    private fun applyContentPadding(
        view: ViewGroup?,
        snapshot: ViewPaddingSnapshot?,
        barHeight: Int,
        bottomMargin: Int
    ) {
        if (view == null || snapshot == null) return
        view.setPaddingRelative(
            snapshot.start,
            snapshot.top,
            snapshot.end,
            max(snapshot.bottom, barHeight + bottomMargin)
        )
        view.clipToPadding = false
    }

    private fun applyHomeChrome(
        activity: Activity,
        contentRoot: ViewGroup,
        surfaceParent: FrameLayout,
        tokens: LiquidGlassTokens,
        chatDestinationIndex: Int?
    ): ChromeSnapshot {
        val contentBackground = contentRoot.background
        val toolbarId = activity.resources.getIdentifier("toolbar", "id", activity.packageName)
        val toolbar = toolbarId.takeIf { it != 0 }
            ?.let { contentRoot.findViewById<View>(it) as? ViewGroup }
        val toolbarBackground = toolbar?.background
        val toolbarElevation = toolbar?.elevation ?: 0f
        val logoId = activity.resources.getIdentifier("toolbar_logo", "id", activity.packageName)
        val logo = logoId.takeIf { it != 0 }?.let { toolbar?.findViewById<View>(it) }
        val logoVisibility = logo?.visibility ?: View.VISIBLE
        val searchView = listOf("search_bar", "search_view", "search_container")
            .asSequence()
            .map { activity.resources.getIdentifier(it, "id", activity.packageName) }
            .filter { it != 0 }
            .mapNotNull { contentRoot.findViewById<View>(it) }
            .firstOrNull()
        val searchBackground = searchView?.background
        var title: TextView? = null
        var actionOverlay: GlassHomeActionOverlay? = null
        return try {
            contentRoot.setBackgroundColor(tokens.homeBackgroundColor)
            toolbar?.let { bar ->
                bar.setBackgroundColor(tokens.homeBackgroundColor)
                bar.elevation = 0f
            }
            title = if (toolbar != null && logo != null && chatDestinationIndex != null) {
                createChatsTitle(activity, toolbar, tokens)
            } else null
            searchView?.background = roundedLayer(
                tokens.actionFillColor,
                tokens.actionStrokeColor,
                tokens.searchRadiusDp * activity.resources.displayMetrics.density,
                activity.resources.displayMetrics.density
            )
            val actions = if (chatDestinationIndex != null) {
                toolbar?.let { discoverTopActions(it, surfaceParent) }.orEmpty()
            } else {
                emptyList()
            }
            actionOverlay = actions.takeIf { it.isNotEmpty() }?.let { hostActions ->
                GlassHomeActionOverlay(activity, requireNotNull(toolbar), hostActions, tokens).also {
                    it.attach(surfaceParent)
                }
            }
            ChromeSnapshot(
                contentBackground = contentBackground,
                toolbar = toolbar,
                toolbarBackground = toolbarBackground,
                toolbarElevation = toolbarElevation,
                toolbarLogo = logo,
                toolbarLogoVisibility = logoVisibility,
                titleView = title,
                chatDestinationIndex = chatDestinationIndex,
                actionOverlay = actionOverlay,
                searchView = searchView,
                searchBackground = searchBackground
            )
        } catch (throwable: Throwable) {
            restoreChrome(
                contentRoot,
                ChromeSnapshot(
                    contentBackground = contentBackground,
                    toolbar = toolbar,
                    toolbarBackground = toolbarBackground,
                    toolbarElevation = toolbarElevation,
                    toolbarLogo = logo,
                    toolbarLogoVisibility = logoVisibility,
                    titleView = title,
                    chatDestinationIndex = chatDestinationIndex,
                    actionOverlay = actionOverlay,
                    searchView = searchView,
                    searchBackground = searchBackground
                )
            )
            throw throwable
        }
    }

    private fun discoverTopActions(
        toolbar: ViewGroup,
        surfaceParent: FrameLayout
    ): List<View> {
        val density = toolbar.resources.displayMetrics.density
        val parentWidth = surfaceParent.width.coerceAtLeast(1)
        return clickableLeafDestinations(toolbar)
            .filter { action ->
                val widthDp = action.width / density
                val heightDp = action.height / density
                widthDp in 28f..72f && heightDp in 28f..72f &&
                    HomeVisualMirror.primaryImage(action)?.drawable != null &&
                    !HomeVisualMirror.resourceNameContains(action, "logo")
            }
            .filter { action ->
                val location = IntArray(2)
                action.getLocationInWindow(location)
                location[0] + action.width / 2 > parentWidth * 0.42f
            }
            .sortedBy { action ->
                val location = IntArray(2)
                action.getLocationInWindow(location)
                location[0]
            }
            .take(4)
    }

    private fun createChatsTitle(
        activity: Activity,
        toolbar: ViewGroup,
        tokens: LiquidGlassTokens
    ): TextView? {
        val chatsId = activity.resources.getIdentifier("chats", "string", activity.packageName)
        if (chatsId == 0) return null
        val title = TextView(activity).apply {
            text = activity.getString(chatsId)
            setTextColor(tokens.primaryContentColor)
            textSize = tokens.titleTextSp
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setPadding((8 * resources.displayMetrics.density).toInt(), 0, 0, 0)
            visibility = View.GONE
        }
        toolbar.addView(title, 0)
        title.layoutParams = title.layoutParams.apply {
            width = ViewGroup.LayoutParams.WRAP_CONTENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        return title
    }

    private fun updateChromeSelection(chrome: ChromeSnapshot, selected: Int?) {
        val showChats = selected != null && selected == chrome.chatDestinationIndex
        chrome.titleView?.visibility = if (showChats) View.VISIBLE else View.GONE
        chrome.toolbarLogo?.visibility = if (showChats) View.INVISIBLE else chrome.toolbarLogoVisibility
        chrome.actionOverlay?.setActive(showChats)
    }

    private fun roundedLayer(fill: Int, stroke: Int, radius: Float, density: Float) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fill)
            setStroke((0.8f * density).toInt().coerceAtLeast(1), stroke)
        }

    private fun restoreChrome(contentRoot: ViewGroup, chrome: ChromeSnapshot) {
        contentRoot.background = chrome.contentBackground
        chrome.toolbar?.let { toolbar ->
            toolbar.background = chrome.toolbarBackground
            toolbar.elevation = chrome.toolbarElevation
            chrome.titleView?.let { title -> runCatching { toolbar.removeView(title) } }
        }
        chrome.toolbarLogo?.visibility = chrome.toolbarLogoVisibility
        chrome.actionOverlay?.detach()
        chrome.searchView?.background = chrome.searchBackground
    }

    private fun recordMetrics(metrics: LocalizedGlassMetrics) {
        HomeRuntimeState.update { current ->
            val rolling = current.rollingCaptureMs?.let { it * 0.90 + metrics.lastCaptureMs * 0.10 }
                ?: metrics.lastCaptureMs
            current.copy(
                hierarchyCaptureCount = metrics.capturedFrames,
                lastCaptureMs = metrics.lastCaptureMs,
                rollingCaptureMs = rolling,
                worstCaptureMs = metrics.worstCaptureMs,
                approximateRenderNodeBytes = metrics.approximateRenderNodeBytes
            )
        }
        if (metrics.capturedFrames == 1L || metrics.capturedFrames % 300L == 0L) {
            diagnostics.request("home.render.metrics")
        }
    }

    private fun recordRenderEvent(event: LocalizedGlassEvent) {
        if (event != LocalizedGlassEvent.FIRST_FRAME_RENDERED) return
        HomeRuntimeState.update { current -> current.copy(runtimeVerified = true, failure = null) }
        RuntimeState.features?.markRuntimeVerified(HomeFeatureRegistry.FEATURE_ID)
        RuntimeTrace.event("home-glass-first-frame-rendered")
        diagnostics.request("home.render.first-frame")
    }

    private fun fail(activity: Activity, reason: String, throwable: Throwable? = null) {
        val summary = throwable?.let(DiagnosticSanitizer::failureSummary) ?: reason
        RuntimeTrace.event("home-redesign-failed", "reason=$reason failure=$summary")
        HomeRuntimeState.update { current ->
            current.copy(
                status = HomeRedesignStatus.FAILED,
                lastActivityClass = activity.javaClass.name,
                stockNavMovedIntoSurface = false,
                floatingBottomBarAttached = false,
                glassSurfaceCount = attachments.size,
                customNavigationVisible = false,
                hostNavigationVisuallyHidden = false,
                visibleNavigationLabelCount = 0,
                customTopActionCount = 0,
                failure = summary
            )
        }
        diagnostics.request("home.failed.$reason")
    }

    private fun detach(activity: Activity) {
        val attachment = attachments.remove(activity) ?: return
        runCatching { attachment.surfaceParent.removeView(attachment.surface) }
        val navigation = runCatching { attachment.surface.detachHostNavigation() }
            .getOrNull() ?: attachment.stockNavigation
        if (navigation.parent == null) {
            restoreToOriginalParent(
                navigation,
                attachment.originalParent,
                attachment.originalIndex,
                attachment.originalLayoutParams
            )
        }
        attachment.paddedContent?.let { padded ->
            attachment.paddingSnapshot?.let { original ->
                padded.setPaddingRelative(
                    original.start,
                    original.top,
                    original.end,
                    original.bottom
                )
                original.clipToPadding?.let { padded.clipToPadding = it }
            }
        }
        restoreChrome(attachment.contentRoot, attachment.chrome)
        HomeRuntimeState.update { current ->
            current.copy(
                status = HomeRedesignStatus.DETACHED,
                stockNavMovedIntoSurface = false,
                floatingBottomBarAttached = false,
                glassSurfaceCount = attachments.size,
                contentPaddingApplied = false,
                toolbarStyled = false,
                searchSurfaceStyled = false,
                customNavigationVisible = false,
                hostNavigationVisuallyHidden = false,
                visibleNavigationLabelCount = 0,
                customTopActionCount = 0
            )
        }
        RuntimeTrace.event("home-redesign-detached", activity.javaClass.name)
        diagnostics.request("home.detached")
    }

    private fun restoreToOriginalParent(
        navigation: View,
        parent: ViewGroup,
        originalIndex: Int,
        originalLayoutParams: ViewGroup.LayoutParams
    ) {
        val index = originalIndex.coerceIn(0, parent.childCount)
        parent.addView(navigation, index, originalLayoutParams)
    }

    private fun isHomeActivity(activity: Activity): Boolean =
        activity.packageName == "com.whatsapp" && activity.javaClass.simpleName == "HomeActivity"

    private fun descendants(root: View): Sequence<View> = sequence {
        yield(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) yieldAll(descendants(root.getChildAt(index)))
        }
    }

    private companion object {
        const val MAX_DISCOVERY_ATTEMPTS = 5
        const val DISCOVERY_RETRY_MS = 140L
    }
}
