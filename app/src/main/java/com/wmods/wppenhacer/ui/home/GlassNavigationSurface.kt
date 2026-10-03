package com.wmods.wppenhacer.ui.home

import android.animation.TimeInterpolator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
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
            setStroke((0.35f * density).toInt().coerceAtLeast(1), tokens.selectionStrokeColor)
        }
        alpha = 0f
    }

    fun moveTo(bounds: Rect, animate: Boolean) {
        val horizontalInset = (tokens.selectionHorizontalInsetDp * density).toInt()
        val verticalInset = (tokens.selectionVerticalInsetDp * density).toInt()
        val size = HomeSelectionPillPolicy.size(
            bounds.width(),
            bounds.height(),
            horizontalInset,
            verticalInset
        )
        val targetWidth = size.width
        val targetHeight = size.height
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

private class GlassOuterRimView(
    context: Context,
    private val tokens: LiquidGlassTokens
) : View(context) {
    private val density = resources.displayMetrics.density
    private val rimWidth = tokens.outerRimDp * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = rimWidth
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        paint.shader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            intArrayOf(0x8AFFFFFF.toInt(), 0x3AFFFFFF, 0x16FFFFFF),
            floatArrayOf(0f, 0.38f, 1f),
            Shader.TileMode.CLAMP
        )
        val inset = rimWidth / 2f
        val radius = (height - rimWidth) / 2f
        canvas.drawRoundRect(
            inset,
            inset,
            width - inset,
            height - inset,
            radius,
            radius,
            paint
        )
    }
}

/**
 * Visible icon-only destination. The real WhatsApp destination remains attached but transparent;
 * this view owns its Phase-5A icon artwork while delegating behavior/state to the host.
 */
private class GlassNavigationDestinationView(
    context: Context,
    private val slot: HomeVisualSlot<View>,
    private val tokens: LiquidGlassTokens
) : FrameLayout(context) {
    private val hostDestination = slot.functionalSource
    private val density = resources.displayMetrics.density
    private val iconBox = FrameLayout(context)
    private val icon = ImageView(context)
    private val badge = TextView(context)
    private val customIcon = if (slot.kind == HomeDestinationKind.PROFILE) {
        null
    } else {
        Phase5NavigationIconDrawable(
            HomeNavigationIconPolicy.glyph(slot.kind),
            tokens.secondaryContentColor
        )
    }
    private var sourceImage: ImageView? = profileImageView()
    private var lastSourceDrawable: Drawable? = null
    private var originalColorIcon = false

    init {
        isClickable = hostDestination?.isClickable == true
        isFocusable = isClickable
        foreground = null
        background = null
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        setOnClickListener { hostDestination?.performClick() }
        setOnLongClickListener { hostDestination?.performLongClick() == true }

        val iconBoxSize = (tokens.navigationIconBoxDp * density).toInt()
        val requestedIconDp = if (slot.kind == HomeDestinationKind.PROFILE) {
            HomeIconBoxPolicy.normalizedAvatarSize(
                tokens.navigationIconBoxDp,
                tokens.navigationAvatarDp
            )
        } else {
            HomeIconBoxPolicy.normalizedIconSize(
                tokens.navigationIconBoxDp,
                tokens.navigationIconDp
            )
        }
        val iconSize = (requestedIconDp * density).toInt()
        iconBox.isClickable = false
        iconBox.isFocusable = false
        iconBox.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(iconBox, LayoutParams(iconBoxSize, iconBoxSize, Gravity.CENTER))
        icon.scaleType = ImageView.ScaleType.CENTER_INSIDE
        icon.isClickable = false
        icon.isFocusable = false
        icon.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        iconBox.addView(icon, LayoutParams(iconSize, iconSize, Gravity.CENTER))
        if (customIcon != null) icon.setImageDrawable(customIcon)

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
        iconBox.addView(
            badge,
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                (tokens.badgeMinSizeDp * density).toInt(),
                Gravity.TOP or Gravity.END
            )
        )
        refresh(active = false)
    }

    override fun getAccessibilityClassName(): CharSequence = android.widget.Button::class.java.name

    fun refresh(active: Boolean) {
        contentDescription = hostDestination?.let(HomeVisualMirror::accessibleLabel)
            ?: if (slot.kind == HomeDestinationKind.PROFILE) "Settings" else slot.kind.name
        isSelected = active
        isActivated = active

        if (slot.kind == HomeDestinationKind.PROFILE) {
            val currentImage = profileImageView()
            if (currentImage !== sourceImage || currentImage?.drawable !== lastSourceDrawable) {
                sourceImage = currentImage
                lastSourceDrawable = currentImage?.drawable
                originalColorIcon = currentImage?.drawable != null
                icon.setImageDrawable(
                    if (currentImage != null) {
                        HomeVisualMirror.cloneDrawable(currentImage, resources)
                    } else {
                        Phase5NavigationIconDrawable(
                            HomeNavigationGlyph.PROFILE_PLACEHOLDER,
                            tokens.secondaryContentColor
                        )
                    }
                )
                icon.scaleType = if (originalColorIcon) {
                    ImageView.ScaleType.CENTER_CROP
                } else {
                    ImageView.ScaleType.CENTER_INSIDE
                }
                icon.clipToOutline = originalColorIcon
                icon.outlineProvider = if (originalColorIcon) CIRCLE_OUTLINE else null
            }
        } else {
            customIcon?.color = if (active) {
                tokens.activeNavigationIconColor
            } else {
                tokens.secondaryContentColor
            }
        }
        icon.imageTintList = null
        icon.alpha = if (active) 1f else tokens.inactiveIconAlpha

        val badgeState = if (slot.kind == HomeDestinationKind.PROFILE || hostDestination == null) {
            HostBadgeState(visible = false, text = null)
        } else {
            HomeVisualMirror.badgeState(hostDestination)
        }
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

    private fun profileImageView(): ImageView? = when (val source = slot.profileImageSource) {
        is ImageView -> source
        null -> null
        else -> HomeVisualMirror.primaryImage(source)
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
    private val onSelectedDestinationChanged: (Int?) -> Unit,
    private val onPillBoundsChanged: (Rect?) -> Unit
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
    private val rim = GlassOuterRimView(context, tokens)
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
    private var destinations: List<HomeVisualSlot<View>> = emptyList()
    private var customDestinationViews: List<GlassNavigationDestinationView> = emptyList()
    private var selectedIndex: Int? = null
    private var hiddenByScroll = false
    private var hiddenByIme = false

    init {
        isClickable = false
        isFocusable = false
        clipChildren = false
        clipToPadding = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, view.height / 2f)
            }
        }
        elevation = tokens.navigationElevationDp * resources.displayMetrics.density
        addView(backdrop, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(selectionPill, LayoutParams(1, 1))
        addView(customDestinations, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(rim, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun attachHostNavigation(
        hostNavigation: ViewGroup,
        destinationSlots: List<HomeVisualSlot<View>>
    ) {
        check(navigation == null) { "host navigation already attached" }
        check(destinationSlots.map { it.kind } == HomeVisualSlotMapper.ORDER) {
            "visual destination order is not canonical"
        }
        check(HomeCustomNavigationPolicy.canReplace(destinationSlots)) {
            "custom destination mapping is incomplete"
        }
        val mirrors = destinationSlots.map { slot ->
            GlassNavigationDestinationView(context, slot, tokens)
        }

        navigation = hostNavigation
        destinations = destinationSlots
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
        onPillBoundsChanged(null)
        return hostNavigation
    }

    fun selectedDestinationIndex(): Int? = selectedIndex

    fun visibleDestinationCount(): Int = customDestinationViews.size

    fun setHiddenForIme(hidden: Boolean) {
        hiddenByIme = hidden
        animate().cancel()
        if (hidden) {
            visibility = View.INVISIBLE
            alpha = 0f
        } else {
            applyScrollVisibility(animate = false)
        }
    }

    fun setHiddenForScroll(hidden: Boolean) {
        if (hiddenByScroll == hidden) return
        hiddenByScroll = hidden
        if (!hiddenByIme) applyScrollVisibility(animate = true)
    }

    fun isHiddenForScroll(): Boolean = hiddenByScroll

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
            onPillBoundsChanged(null)
            return
        }
        destinationBounds.set(0, 0, destination.width, destination.height)
        offsetDescendantRectToMyCoords(destination, destinationBounds)
        if (destinationBounds == lastDestinationBounds) return
        selectionPill.moveTo(destinationBounds, animate)
        lastDestinationBounds.set(destinationBounds)
        onPillBoundsChanged(Rect(destinationBounds))
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
            val source = destinations[index].functionalSource ?: continue
            if (!isDestinationSelected(source)) continue
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

    private fun applyScrollVisibility(animate: Boolean) {
        animate().cancel()
        if (hiddenByIme) {
            visibility = View.INVISIBLE
            return
        }
        val targetY = if (hiddenByScroll) height * 1.35f else 0f
        if (!animate) {
            translationY = targetY
            alpha = if (hiddenByScroll) 0f else 1f
            visibility = if (hiddenByScroll) View.INVISIBLE else View.VISIBLE
            return
        }
        visibility = View.VISIBLE
        animate()
            .translationY(targetY)
            .alpha(if (hiddenByScroll) 0f else 1f)
            .setDuration(tokens.navigationVisibilityDurationMs)
            .withEndAction {
                if (hiddenByScroll && !hiddenByIme) visibility = View.INVISIBLE
            }
            .start()
    }
}
