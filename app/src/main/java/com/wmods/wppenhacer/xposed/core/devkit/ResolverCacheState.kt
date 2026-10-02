package com.wmods.wppenhacer.xposed.core.devkit

enum class ResolverCacheDisposition {
    HIT,
    INVALIDATED
}

enum class ResolverCacheInvalidationReason {
    HOST_VERSION_CODE_CHANGED,
    HOST_VERSION_NAME_CHANGED,
    MODULE_UPDATED,
    MODULE_VERSION_CHANGED,
    CACHE_SCHEMA_CHANGED
}

data class ResolverCacheIdentity(
    val hostVersionCode: Long,
    val hostVersionName: String,
    val moduleUpdateTime: Long,
    val moduleVersionName: String,
    val cacheSchemaVersion: Int
)

data class ResolverCacheStatus(
    val disposition: ResolverCacheDisposition,
    val invalidationReason: ResolverCacheInvalidationReason?,
    val previousHostVersionCode: Long?,
    val currentHostVersionCode: Long,
    val previousHostVersionName: String?,
    val currentHostVersionName: String
)

object ResolverCachePolicy {
    fun evaluate(
        saved: ResolverCacheIdentity,
        current: ResolverCacheIdentity,
        resetOnModuleUpdate: Boolean
    ): ResolverCacheStatus {
        val reason = when {
            saved.hostVersionCode != current.hostVersionCode ->
                ResolverCacheInvalidationReason.HOST_VERSION_CODE_CHANGED

            saved.hostVersionName != current.hostVersionName ->
                ResolverCacheInvalidationReason.HOST_VERSION_NAME_CHANGED

            resetOnModuleUpdate && saved.moduleUpdateTime != current.moduleUpdateTime ->
                ResolverCacheInvalidationReason.MODULE_UPDATED

            saved.moduleVersionName != current.moduleVersionName ->
                ResolverCacheInvalidationReason.MODULE_VERSION_CHANGED

            saved.cacheSchemaVersion != current.cacheSchemaVersion ->
                ResolverCacheInvalidationReason.CACHE_SCHEMA_CHANGED

            else -> null
        }
        return ResolverCacheStatus(
            disposition = if (reason == null) {
                ResolverCacheDisposition.HIT
            } else {
                ResolverCacheDisposition.INVALIDATED
            },
            invalidationReason = reason,
            previousHostVersionCode = saved.hostVersionCode.takeIf { it > 0 },
            currentHostVersionCode = current.hostVersionCode,
            previousHostVersionName = saved.hostVersionName.takeIf { it.isNotBlank() },
            currentHostVersionName = current.hostVersionName
        )
    }
}
