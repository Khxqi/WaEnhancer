package com.wmods.wppenhacer.ui.glass

/** Pure ownership decision used before a glass surface is attached. */
data class GlassHierarchyOwnership(
    val sameNode: Boolean,
    val surfaceParentInsideSamplingSubtree: Boolean
) {
    val surfaceOutsideSamplingSubtree: Boolean
        get() = !sameNode && !surfaceParentInsideSamplingSubtree
}

object GlassHierarchyPolicy {
    fun evaluate(
        sameNode: Boolean,
        surfaceParentInsideSamplingSubtree: Boolean
    ): GlassHierarchyOwnership = GlassHierarchyOwnership(
        sameNode = sameNode,
        surfaceParentInsideSamplingSubtree = surfaceParentInsideSamplingSubtree
    )
}
