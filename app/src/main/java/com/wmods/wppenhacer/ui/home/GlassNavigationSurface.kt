package com.wmods.wppenhacer.ui.home

import android.animation.TimeInterpolator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.wmods.wppenhacer.ui.glass.GlassVisualProbeMode
import com.wmods.wppenhacer.ui.glass.LocalizedBackdropGlassView
import com.wmods.wppenhacer.ui.glass.LocalizedGlassDrawMetrics
import com.wmods.wppenhacer.ui.glass.LocalizedGlassEvent
import com.wmods.wppenhacer.ui.glass.LocalizedGlassFailureStage
import com.wmods.wppenhacer.ui.glass.LocalizedGlassGeometry
import com.wmods.wppenhacer.ui.glass.LocalizedGlassMetrics

private data class HostNavigationVisualSnapshot(
    val background: Drawable?,
    val elevation: Float,
    val alpha: Float,
    val importantForAccessibility: Int
)

class GlassSelectionPill(
    context: Context,
    private val tokens: LiquidGlassTokens
) : View(context) {
    private val density = resources.displayMetrics.density
    private val interpolator: TimeInterpolator = DecelerateInterpolator(1.6f)
    private var positioned = false

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(tokens.selectionHighlightColor, tokens.selectionFillColor)
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = tokens.navigationHeightDp * density / 2f
            setStroke((0.75f * density).toInt().coerceAtLeast(1), tokens.selectionStrokeColor)
        }
        alpha = 0f
    }

    fun moveTo(bounds: Rect, animate: Boolean) {
        val horizontalInset = (tokens.selectionHorizontalInsetDp * density).toInt()
        val verticalInset = (tokens.selectionVerticalInsetDp * density).toInt()
        val targetWidth = (bounds.width() - horizontalInset * 2).coerceAtLeast(1)
        val targetHeight = (bounds.height() - verticalInset * 2).coerceAtLeast(1)
        val params = (layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(targetWidth, targetHeight)
        if (params.width != targetWidth || params.height != targetHeight) {
            params.width = targetWidth
            params.height = targetHeight
            layoutParams = params
        }
        val targetX = (bounds.left + horizontalInset).toFloat()
        val targetY = (bounds.top + verticalInset).toFloat()
        animate().cancel()
        if (!positioned || !animate) {
            x = targetX
            y = targetY
            alpha = 1f
        } else {
            animate()
                .x(targetX)
                .y(targetY)
                .alpha(1f)
                .setDuration(tokens.selectionDurationMs)
                .setInterpolator(interpolator)
                .start()
        }
        positioned = true
    }

    fun hide() {
        animate().cancel()
        alpha = 0f
    }
}

/**
 * Visible icon-only destination. The real WhatsApp destination remains attached but transparent;
 * this view mirrors only its icon/badge/state and delegates click and long-click behavior to it.
 */
private class GlassNavigationDestinationView(
    context: Context,
    private val hostDestination: View,
    private val tokens: LiquidGlassTokens
) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val icon = ImageView(context)
    private val badge = TextView(context)
    private var sourceImage: ImageView = requireNotNull(
        HomeVisualMirror.primaryImage(hostDestination)
    ) { "host destination has no drawable icon" }
    private var lastSourceDrawable: Drawable? = null
    private var originalColorIcon = false

    init {
        isClickable = true
        isFocusable = true
        foreground = null
        background = null
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        setOnClickListener {
            hostDestination.performClick()
        }
        setOnLongClickListener { hostDestination.performLongClick() }

        val iconSize = (tokens.navigationIconDp * density).toInt()
        icon.scaleType = ImageView.ScaleType.CENTER_INSIDE
        icon.isClickable = false
        icon.isFocusable = false
        icon.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(icon, LayoutParams(iconSize, iconSize, Gravity.CENTER))

        badge.gravity = Gravity.CENTER
        badge.includeFontPadding = false
        badge.isClickable = false
        badge.isFocusable = false
        badge.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        badge.setTextColor(tokens.badgeTextColor)
        badge.textSize = tokens.badgeTextSp
        badge.setPadding((5f * density).toInt(), 0, (5f * density).toInt(), 0)
        badge.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = tokens.badgeMinSizeDp * density / 2f
            setColor(tokens.badgeColor)
            setStroke((1f * density).toInt().coerceAtLeast(1), tokens.badgeStrokeColor)
        }
        addView(
            badge,
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                (tokens.badgeMinSizeDp * density).toInt(),
                Gravity.CENTER
            )
        )
        badge.translationX = tokens.navigationIconDp * density * 0.43f
        badge.translationY = -tokens.navigationIconDp * density * 0.43f
        refresh(active = false)
    }

    override fun getAccessibilityClassName(): CharSequence = android.widget.Button::class.java.name

    fun refresh(active: Boolean) {
        contentDescription = HomeVisualMirror.accessibleLabel(hostDestination)
        isSelected = active
        isActivated = active

        val currentImage = HomeVisualMirror.primaryImage(hostDestination) ?: sourceImage
        if (currentImage !== sourceImage || currentImage.drawable !== lastSourceDrawable) {
            sourceImage = currentImage
            lastSourceDrawable = currentImage.drawable
            originalColorIcon = HomeVisualMirror.preservesOriginalColor(currentImage)
            icon.setImageDrawable(HomeVisualMirror.cloneDrawable(currentImage, resources))
            icon.scaleType = if (originalColorIcon) {
                ImageView.ScaleType.CENTER_CROP
            } else {
                ImageView.ScaleType.CENTER_INSIDE
            }
            icon.clipToOutline = originalColorIcon
            icon.outlineProvider = if (originalColorIcon) CIRCLE_OUTLINE else null
        }
        icon.imageTintList = if (originalColorIcon) {
            null
        } else {
            ColorStateList.valueOf(
                if (active) {
                    tokens.activeNavigationIconColor
                } else {
                    tokens.secondaryContentColor
                }
            )
        }
        icon.alpha = if (active) 1f else tokens.inactiveIconAlpha

        val badgeState = HomeVisualMirror.badgeState(hostDestination)
        badge.visibility = if (badgeState.visible) View.VISIBLE else View.GONE
        badge.text = badgeState.text ?: ""
        val badgeParams = badge.layoutParams
        badgeParams.width = if (badgeState.text == null) {
            (tokens.badgeDotSizeDp * density).toInt()
        } else {
            LayoutParams.WRAP_CONTENT
        }
        badgeParams.height = if (badgeState.text == null) {
            (tokens.badgeDotSizeDp * density).toInt()
        } else {
            (tokens.badgeMinSizeDp * density).toInt()
        }
        badge.layoutParams = badgeParams
    }

    private companion object {
        val CIRCLE_OUTLINE = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
    }
}

/**
 * One captured backdrop for the complete capsule. The visible destination presentation is custom;
 * the transparent host hierarchy remains the functional source of truth for routing and state.
 */
@RequiresApi(33)
class GlassNavigationSurface(
    context: Context,
    samplingRoot: ViewGroup,
    private val tokens: LiquidGlassTokens,
    private val onMetrics: (LocalizedGlassMetrics) -> Unit,
    private val onGeometry: (LocalizedGlassGeometry) -> Unit,
    private val onDrawMetrics: (LocalizedGlassDrawMetrics) -> Unit,
    private val onRenderEvent: (LocalizedGlassEvent) -> Unit,
    private val onRenderFailure: (LocalizedGlassFailureStage, Throwable) -> Unit,
    private val onSelectedDestinationChanged: (Int?) -> Unit
) : FrameLayout(context), ViewTreeObserver.OnPreDrawListener {
    private val destinationBounds = Rect()
    private val lastDestinationBounds = Rect()
    private val backdrop = LocalizedBackdropGlassView(
        context = context,
        hostRoot = samplingRoot,
        style = tokens.navigationStyle(resources.displayMetrics.density),
        diagnosticProbeEnabled = false,
        onMetrics = onMetrics,
        onGeometry = onGeometry,
        onDrawMetrics = onDrawMetrics,
        onProbeModeChanged = { _: GlassVisualProbeMode, _: Long -> Unit },
        onEvent = onRenderEvent,
        onFailure = onRenderFailure
    )
    private val selectionPill = GlassSelectionPill(context, tokens)
    private val customDestinations = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setPadding(
            (tokens.navigationHorizontalContentInsetDp * resources.displayMetrics.density).toInt(),
            0,
            (tokens.navigationHorizontalContentInsetDp * resources.displayMetrics.density).toInt(),
            0
        )
    }
    private var navigation: ViewGroup? = null
    private var navigationSnapshot: HostNavigationVisualSnapshot? = null
    private var destinations: List<View> = emptyList()
    private var customDestinationViews: List<GlassNavigationDestinationView> = emptyList()
    private var selectedIndex: Int? = null

    init {
        isClickable = false
        isFocusable = false
        clipChildren = false
        clipToPadding = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(backdrop, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(selectionPill, LayoutParams(1, 1))
        addView(customDestinations, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun attachHostNavigation(hostNavigation: ViewGroup, destinationViews: List<View>) {
        check(navigation == null) { "host navigation already attached" }
        val mirrorableIconCount = destinationViews.count {
            HomeVisualMirror.primaryImage(it)?.drawable != null
        }
        check(
            HomeCustomNavigationPolicy.canReplace(destinationViews.size, mirrorableIconCount)
        ) {
            "one or more host destinations have no drawable icon"
        }
        val mirrors = destinationViews.map { destination ->
            GlassNavigationDestinationView(context, destination, tokens)
        }

        navigation = hostNavigation
        destinations = destinationViews
        customDestinationViews = mirrors
        navigationSnapshot = HostNavigationVisualSnapshot(
            background = hostNavigation.background,
            elevation = hostNavigation.elevation,
            alpha = hostNavigation.alpha,
            importantForAccessibility = hostNavigation.importantForAccessibility
        )
        hostNavigation.background = null
        hostNavigation.elevation = 0f
        hostNavigation.alpha = 0f
        hostNavigation.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(hostNavigation, 2, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        mirrors.forEach { mirror ->
            customDestinations.addView(
                mirror,
                LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            )
        }
        hostNavigation.viewTreeObserver.addOnPreDrawListener(this)
        updatePresentation(animate = false)
    }

    fun detachHostNavigation(): ViewGroup? {
        val hostNavigation = navigation ?: return null
        if (hostNavigation.viewTreeObserver.isAlive) {
            hostNavigation.viewTreeObserver.removeOnPreDrawListener(this)
        }
        customDestinations.removeAllViews()
        navigationSnapshot?.let { snapshot ->
            hostNavigation.background = snapshot.background
            hostNavigation.elevation = snapshot.elevation
            hostNavigation.alpha = snapshot.alpha
            hostNavigation.importantForAccessibility = snapshot.importantForAccessibility
        }
        removeView(hostNavigation)
        navigation = null
        navigationSnapshot = null
        destinations = emptyList()
        customDestinationViews = emptyList()
        selectedIndex = null
        lastDestinationBounds.setEmpty()
        selectionPill.hide()
        return hostNavigation
    }

    fun selectedDestinationIndex(): Int? = selectedIndex

    fun visibleDestinationCount(): Int = customDestinationViews.size

    override fun onPreDraw(): Boolean {
        updatePresentation(animate = true)
        return true
    }

    private fun updatePresentation(animate: Boolean) {
        val next = resolveSelectedIndex(selectedIndex)
        customDestinationViews.forEachIndexed { index, view -> view.refresh(index == next) }
        if (next != selectedIndex) {
            selectedIndex = next
            onSelectedDestinationChanged(next)
        }
        val destination = next?.let(customDestinationViews::getOrNull)
        if (destination == null || destination.width <= 0 || destination.height <= 0) {
            selectionPill.hide()
            return
        }
        destinationBounds.set(0, 0, destination.width, destination.height)
        offsetDescendantRectToMyCoords(destination, destinationBounds)
        if (destinationBounds == lastDestinationBounds) return
        selectionPill.moveTo(destinationBounds, animate)
        lastDestinationBounds.set(destinationBounds)
    }

    private fun isDestinationSelected(view: View): Boolean {
        if (view.isSelected || view.isActivated ||
            android.util.StateSet.stateSetMatches(
                intArrayOf(android.R.attr.state_checked),
                view.drawableState
            )
        ) return true
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                if (isDestinationSelected(view.getChildAt(index))) return true
            }
        }
        return false
    }

    private fun resolveSelectedIndex(previous: Int?): Int? {
        var activeIndex = -1
        var activeCount = 0
        for (index in destinations.indices) {
            if (!isDestinationSelected(destinations[index])) continue
            activeIndex = index
            activeCount++
        }
        return HomeSelectedDestination.resolve(
            selectedCount = activeCount,
            selectedIndex = activeIndex,
            destinationCount = destinations.size,
            previous = previous
        )
    }
}
