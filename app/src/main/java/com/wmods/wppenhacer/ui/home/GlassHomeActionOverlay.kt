package com.wmods.wppenhacer.ui.home

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView

private data class HostActionVisualSnapshot(
    val alpha: Float,
    val importantForAccessibility: Int
)

enum class HomeActionPlacement {
    MIRROR_HOST,
    FLOATING_ABOVE_NAVIGATION
}

data class GlassHomeActionBinding(
    val hostAction: View,
    val placement: HomeActionPlacement
)

private class GlassHomeActionButton(
    context: Context,
    private val hostAction: View,
    private val tokens: LiquidGlassTokens
) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val icon = ImageView(context)
    private var sourceImage = requireNotNull(HomeVisualMirror.primaryImage(hostAction)) {
        "host action has no drawable icon"
    }
    private var lastDrawable: Drawable? = null
    private var originalColorIcon = false

    init {
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(tokens.actionHighlightColor, tokens.actionFillColor)
        ).apply {
            shape = GradientDrawable.OVAL
            setStroke((0.8f * density).toInt().coerceAtLeast(1), tokens.actionStrokeColor)
        }
        elevation = tokens.actionElevationDp * density
        setOnClickListener { hostAction.performClick() }
        setOnLongClickListener { hostAction.performLongClick() }

        val iconSize = (tokens.actionIconDp * density).toInt()
        icon.scaleType = ImageView.ScaleType.CENTER_INSIDE
        icon.isClickable = false
        icon.isFocusable = false
        icon.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(icon, LayoutParams(iconSize, iconSize, Gravity.CENTER))
        refresh()
    }

    override fun getAccessibilityClassName(): CharSequence = android.widget.Button::class.java.name

    fun refresh() {
        contentDescription = HomeVisualMirror.accessibleLabel(hostAction)
        val currentImage = HomeVisualMirror.primaryImage(hostAction) ?: sourceImage
        if (currentImage !== sourceImage || currentImage.drawable !== lastDrawable) {
            sourceImage = currentImage
            lastDrawable = currentImage.drawable
            originalColorIcon = HomeVisualMirror.preservesOriginalColor(currentImage)
            icon.setImageDrawable(HomeVisualMirror.cloneDrawable(currentImage, resources))
        }
        icon.imageTintList = if (originalColorIcon) {
            null
        } else {
            ColorStateList.valueOf(tokens.primaryContentColor)
        }
    }
}

/**
 * One non-capturing overlay owns all top action visuals. Host actions stay attached and update
 * normally, but their pixels/accessibility are hidden while custom buttons delegate behavior.
 */
class GlassHomeActionOverlay(
    context: Context,
    private val positionRoot: ViewGroup,
    private val bindings: List<GlassHomeActionBinding>,
    private val navigationAnchor: View,
    private val tokens: LiquidGlassTokens
) : FrameLayout(context), ViewTreeObserver.OnPreDrawListener {
    private val hostActions = bindings.map { it.hostAction }
    private val snapshots = hostActions.associateWith { action ->
        HostActionVisualSnapshot(
            alpha = action.alpha,
            importantForAccessibility = action.importantForAccessibility
        )
    }
    private val buttons = hostActions.map { GlassHomeActionButton(context, it, tokens) }
    private val overlayLocation = IntArray(2)
    private val hostLocation = IntArray(2)
    private val anchorLocation = IntArray(2)
    private var attached = false
    private var active = false

    init {
        isClickable = false
        isFocusable = false
        clipChildren = false
        clipToPadding = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = View.GONE
    }

    fun attach(parent: FrameLayout) {
        check(!attached) { "top action overlay already attached" }
        try {
            attached = true
            parent.addView(this, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            bindings.forEachIndexed { index, binding ->
                val size = (buttonSizeDp(binding.placement) *
                    resources.displayMetrics.density).toInt()
                addView(buttons[index], LayoutParams(size, size))
            }
            positionRoot.viewTreeObserver.addOnPreDrawListener(this)
            updatePositions()
        } catch (throwable: Throwable) {
            detach()
            throw throwable
        }
    }

    fun detach() {
        if (!attached) return
        setActive(false)
        attached = false
        if (positionRoot.viewTreeObserver.isAlive) {
            positionRoot.viewTreeObserver.removeOnPreDrawListener(this)
        }
        snapshots.forEach { (action, snapshot) ->
            action.alpha = snapshot.alpha
            action.importantForAccessibility = snapshot.importantForAccessibility
        }
        removeAllViews()
        (parent as? ViewGroup)?.removeView(this)
    }

    fun actionCount(): Int = buttons.size

    fun hasCustomFab(): Boolean = bindings.any {
        it.placement == HomeActionPlacement.FLOATING_ABOVE_NAVIGATION
    }

    fun isHostSuppressed(hostAction: View): Boolean =
        active && hostAction in hostActions && hostAction.alpha == 0f

    fun setActive(value: Boolean) {
        if (!attached || active == value) return
        active = value
        snapshots.forEach { (action, snapshot) ->
            action.alpha = if (value) 0f else snapshot.alpha
            action.importantForAccessibility = if (value) {
                IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            } else {
                snapshot.importantForAccessibility
            }
        }
        visibility = if (value) View.VISIBLE else View.GONE
        if (value) updatePositions()
    }

    override fun onPreDraw(): Boolean {
        updatePositions()
        return true
    }

    private fun updatePositions() {
        if (!attached || !active || width <= 0 || height <= 0) return
        getLocationInWindow(overlayLocation)
        navigationAnchor.getLocationInWindow(anchorLocation)
        bindings.forEachIndexed { index, binding ->
            val action = binding.hostAction
            val buttonSize = buttonSizeDp(binding.placement) * resources.displayMetrics.density
            if (binding.placement == HomeActionPlacement.MIRROR_HOST) {
                action.getLocationInWindow(hostLocation)
                buttons[index].x = hostLocation[0] - overlayLocation[0] +
                    (action.width - buttonSize) / 2f
                buttons[index].y = hostLocation[1] - overlayLocation[1] +
                    (action.height - buttonSize) / 2f
            } else {
                buttons[index].x = anchorLocation[0] - overlayLocation[0] +
                    navigationAnchor.width - buttonSize
                buttons[index].y = anchorLocation[1] - overlayLocation[1] -
                    buttonSize - tokens.floatingActionGapDp * resources.displayMetrics.density
            }
            val anchorAvailable = binding.placement !=
                HomeActionPlacement.FLOATING_ABOVE_NAVIGATION ||
                (navigationAnchor.visibility == View.VISIBLE && navigationAnchor.alpha > 0.5f)
            buttons[index].visibility = if (
                action.visibility == View.VISIBLE && anchorAvailable
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }
            buttons[index].refresh()
        }
    }

    private fun buttonSizeDp(placement: HomeActionPlacement): Float =
        if (placement == HomeActionPlacement.FLOATING_ABOVE_NAVIGATION) {
            tokens.floatingActionSizeDp
        } else {
            tokens.actionSizeDp
        }
}
