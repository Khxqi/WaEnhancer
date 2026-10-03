package com.wmods.wppenhacer.ui.home

import android.content.res.Resources
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

internal data class HostBadgeState(
    val visible: Boolean,
    val text: CharSequence?
)

internal object HomeVisualMirror {
    fun primaryImage(root: View): ImageView? = descendants(root)
        .filterIsInstance<ImageView>()
        .filter {
            it.visibility == View.VISIBLE && it.drawable != null &&
                !resourceNameContains(it, "badge")
        }
        .maxByOrNull { it.width.coerceAtLeast(1).toLong() * it.height.coerceAtLeast(1).toLong() }

    fun cloneDrawable(source: ImageView, resources: Resources): Drawable? {
        val drawable = source.drawable ?: return null
        return drawable.constantState?.newDrawable(resources)?.mutate() ?: drawable
    }

    fun preservesOriginalColor(source: ImageView): Boolean =
        source.drawable is BitmapDrawable ||
            source.scaleType == ImageView.ScaleType.CENTER_CROP

    fun badgeState(root: View): HostBadgeState {
        val badgeRoot = descendants(root).firstOrNull { resourceNameContains(it, "badge") }
            ?: return HostBadgeState(visible = false, text = null)
        val text = when (badgeRoot) {
            is TextView -> badgeRoot.text
            else -> descendants(badgeRoot).filterIsInstance<TextView>().firstOrNull()?.text
        }
        return HostBadgeState(
            visible = badgeRoot.visibility == View.VISIBLE && badgeRoot.alpha > 0f,
            text = text?.takeIf { it.isNotBlank() }
        )
    }

    fun accessibleLabel(root: View): CharSequence? = root.contentDescription
        ?.takeIf { it.isNotBlank() }
        ?: descendants(root)
            .filterIsInstance<TextView>()
            .mapNotNull { it.text?.takeIf { text -> text.isNotBlank() } }
            .firstOrNull()

    fun resourceNameContains(view: View, needle: String): Boolean {
        if (view.id == View.NO_ID) return false
        return runCatching { view.resources.getResourceEntryName(view.id) }
            .getOrNull()
            ?.contains(needle, ignoreCase = true) == true
    }

    fun descendants(root: View): Sequence<View> = sequence {
        yield(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                yieldAll(descendants(root.getChildAt(index)))
            }
        }
    }
}
