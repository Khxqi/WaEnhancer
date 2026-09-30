package com.wmods.wppenhacer.xposed.runtime

import java.util.concurrent.CopyOnWriteArrayList

@JvmInline
value class FeatureId(val value: String)

enum class FeatureCategory {
    PRIVACY,
    VISUAL,
    GENERAL,
    MEDIA,
    SUPPORT
}

enum class FeatureStatus {
    DISABLED,
    UNSUPPORTED,
    RESOLVING,
    READY,
    FAILED
}

interface HookHandle {
    fun unhook()
}

data class FeatureSpec(
    val id: FeatureId,
    val category: FeatureCategory,
    val requiredCapabilities: Set<CapabilityId>,
    val enabled: () -> Boolean,
    val installer: () -> List<HookHandle>,
    val legacyManagedEnablement: Boolean = false
)

data class FeatureRecord(
    val id: FeatureId,
    val category: FeatureCategory,
    val enabled: Boolean,
    val status: FeatureStatus,
    val failureReason: String? = null,
    val installedHookCount: Int = 0,
    val legacyManagedEnablement: Boolean = false
)

class FeatureRegistry(private val capabilities: CapabilityRegistry) {
    private val specs = CopyOnWriteArrayList<FeatureSpec>()
    private val records = LinkedHashMap<FeatureId, FeatureRecord>()
    private val handles = LinkedHashMap<FeatureId, List<HookHandle>>()

    fun register(spec: FeatureSpec) {
        require(spec.id.value.isNotBlank()) { "Feature ID must not be blank" }
        require(specs.none { it.id == spec.id }) { "Duplicate feature ID: ${spec.id.value}" }
        specs += spec
    }

    fun installAll() {
        specs.forEach(::install)
    }

    private fun install(spec: FeatureSpec) {
        val enabled = runCatching(spec.enabled).getOrDefault(false)
        if (!enabled) {
            records[spec.id] = record(spec, false, FeatureStatus.DISABLED)
            return
        }
        val unavailable = spec.requiredCapabilities.filterNot(capabilities::isReady)
        if (unavailable.isNotEmpty()) {
            records[spec.id] = record(
                spec,
                true,
                FeatureStatus.UNSUPPORTED,
                "Missing capabilities: ${unavailable.joinToString { it.value }}"
            )
            return
        }
        records[spec.id] = record(spec, true, FeatureStatus.RESOLVING)
        try {
            val installedHandles = spec.installer()
            handles[spec.id] = installedHandles
            records[spec.id] = record(
                spec,
                true,
                FeatureStatus.READY,
                hookCount = installedHandles.size
            )
        } catch (throwable: Throwable) {
            handles.remove(spec.id)?.forEach { runCatching(it::unhook) }
            records[spec.id] = record(
                spec,
                true,
                FeatureStatus.FAILED,
                DiagnosticSanitizer.failureSummary(throwable)
            )
        }
    }

    fun snapshot(): List<FeatureRecord> = synchronized(records) { records.values.toList() }

    private fun record(
        spec: FeatureSpec,
        enabled: Boolean,
        status: FeatureStatus,
        failure: String? = null,
        hookCount: Int = 0
    ) = FeatureRecord(
        id = spec.id,
        category = spec.category,
        enabled = enabled,
        status = status,
        failureReason = failure,
        installedHookCount = hookCount,
        legacyManagedEnablement = spec.legacyManagedEnablement
    )
}
