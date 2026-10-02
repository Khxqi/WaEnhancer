package com.wmods.wppenhacer.ui.home

import android.animation.TimeInterpolator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.wmods.wppenhacer.ui.glass.GlassVisualProbeMode
import com.wmods.wppenhacer.ui.glass.LocalizedBackdropGlassView
import com.wmods.wppenhacer.ui.glass.LocalizedGlassDrawMetrics
import com.wmods.wppenhacer.ui.glass.LocalizedGlassEvent
import com.wmods.wppenhacer.ui.glass.LocalizedGlassFailureStage
import com.wmods.wppenhacer.ui.glass.LocalizedGlassGeometry
import com.wmods.wppenhacer.ui.glass.LocalizedGlassMetrics
import java.util.IdentityHashMap

private data class DestinationVisualSnapshot(
    val background: Drawable?,
    val imageTints: Map<ImageView, ColorStateList?>,
    val textColors: Map<TextView, ColorStateList>
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
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = tokens.navigationHeightDp * density / 2f
            setColor(tokens.selectionFillColor)
            setStroke((0.8f * density).toInt().coerceAtLeast(1), tokens.selectionStrokeColor)
        }
        alpha = 0f
    }

    fun moveTo(bounds: Rect, animate: Boolean) {
        val inset = (tokens.selectionInsetDp * density).toInt()
        val targetWidth = (bounds.width() - inset * 2).coerceAtLeast(1)
        val targetHeight = (bounds.height() - inset * 2).coerceAtLeast(1)
        val params = (layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(targetWidth, targetHeight)
        if (params.width != targetWidth || params.height != targetHeight) {
            params.width = targetWidth
            params.height = targetHeight
            layoutParams = params
        }
        val targetX = (bounds.left + inset).toFloat()
        val targetY = (bounds.top + inset).toFloat()
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
 * One captured backdrop for the complete navigation capsule. Host destination controls are moved
 * into this container and remain the sole owners of clicks, badges, state and accessibility.
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
    private val snapshots = IdentityHashMap<View, DestinationVisualSnapshot>()
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
    private var navigation: ViewGroup? = null
    private var navigationBackground: Drawable? = null
    private var navigationElevation: Float = 0f
    private var destinations: List<View> = emptyList()
    private var selectedIndex: Int? = null

    init {
        isClickable = false
        isFocusable = false
        clipChildren = false
        clipToPadding = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(backdrop, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(selectionPill, LayoutParams(1, 1))
    }

    fun attachHostNavigation(hostNavigation: ViewGroup, destinationViews: List<View>) {
        check(navigation == null) { "host navigation already attached" }
        navigation = hostNavigation
        destinations = destinationViews
        navigationBackground = hostNavigation.background
        navigationElevation = hostNavigation.elevation
        hostNavigation.background = null
        hostNavigation.elevation = 0f
        addView(hostNavigation, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        styleDestinations()
        hostNavigation.viewTreeObserver.addOnPreDrawListener(this)
        updateSelection(animate = false)
    }

    fun detachHostNavigation(): ViewGroup? {
        val hostNavigation = navigation ?: return null
        if (hostNavigation.viewTreeObserver.isAlive) {
            hostNavigation.viewTreeObserver.removeOnPreDrawListener(this)
        }
        restoreDestinations()
        hostNavigation.background = navigationBackground
        hostNavigation.elevation = navigationElevation
        removeView(hostNavigation)
        navigation = null
        navigationBackground = null
        destinations = emptyList()
        selectedIndex = null
        lastDestinationBounds.setEmpty()
        selectionPill.hide()
        return hostNavigation
    }

    fun selectedDestinationIndex(): Int? = selectedIndex

    override fun onPreDraw(): Boolean {
        updateSelection(animate = true)
        return true
    }

    private fun updateSelection(animate: Boolean) {
        val next = resolveSelectedIndex(selectedIndex)
        if (next != selectedIndex) {
            selectedIndex = next
            styleDestinationEmphasis(next)
            onSelectedDestinationChanged(next)
        }
        val destination = next?.let(destinations::getOrNull)
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

    private fun styleDestinations() {
        destinations.forEach { destination ->
            val images = descendants(destination).filterIsInstance<ImageView>().toList()
            val texts = descendants(destination).filterIsInstance<TextView>().toList()
            snapshots[destination] = DestinationVisualSnapshot(
                background = destination.background,
                imageTints = images.associateWith { it.imageTintList },
                textColors = texts.associateWith { it.textColors }
            )
            destination.background = null
        }
        styleDestinationEmphasis(selectedIndex)
    }

    private fun styleDestinationEmphasis(activeIndex: Int?) {
        destinations.forEachIndexed { index, destination ->
            val color = if (index == activeIndex) {
                tokens.primaryContentColor
            } else {
                tokens.secondaryContentColor
            }
            descendants(destination).forEach { view ->
                if (isBadgeView(view)) return@forEach
                when (view) {
                    is ImageView -> view.imageTintList = ColorStateList.valueOf(color)
                    is TextView -> view.setTextColor(color)
                }
            }
        }
    }

    private fun isBadgeView(view: View): Boolean {
        if (view.id == View.NO_ID) return false
        return runCatching { view.resources.getResourceEntryName(view.id) }
            .getOrNull()
            ?.contains("badge", ignoreCase = true) == true
    }

    private fun restoreDestinations() {
        snapshots.forEach { (destination, snapshot) ->
            destination.background = snapshot.background
            snapshot.imageTints.forEach { (image, tint) -> image.imageTintList = tint }
            snapshot.textColors.forEach { (text, colors) -> text.setTextColor(colors) }
        }
        snapshots.clear()
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
        var selectedIndex = -1
        var selectedCount = 0
        for (index in destinations.indices) {
            if (!isDestinationSelected(destinations[index])) continue
            selectedIndex = index
            selectedCount++
        }
        return HomeSelectedDestination.resolve(
            selectedCount = selectedCount,
            selectedIndex = selectedIndex,
            destinationCount = destinations.size,
            previous = previous
        )
    }

    private fun descendants(root: View): Sequence<View> = sequence {
        yield(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                yieldAll(descendants(root.getChildAt(index)))
            }
        }
    }
}
