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

enum class RollbackSupport {
    FULL,
    PARTIAL,
    NONE
}

data class HookRegistration(
    val id: String,
    val handle: HookHandle
)

class HookInstallScope {
    private val registrations = mutableListOf<HookRegistration>()

    fun register(id: String, handle: HookHandle) {
        require(id.isNotBlank()) { "Hook registration ID must not be blank" }
        registrations += HookRegistration(id, handle)
    }

    internal fun snapshot(): List<HookRegistration> = registrations.toList()

    internal fun rollback(): RollbackResult {
        var rolledBack = 0
        registrations.asReversed().forEach { registration ->
            if (runCatching(registration.handle::unhook).isSuccess) rolledBack++
        }
        return RollbackResult(
            attempted = registrations.size,
            rolledBack = rolledBack
        )
    }
}

data class RollbackResult(
    val attempted: Int,
    val rolledBack: Int
) {
    val succeeded: Boolean get() = attempted == rolledBack
    val remaining: Int get() = attempted - rolledBack
}

data class FeatureSpec(
    val id: FeatureId,
    val diagnosticName: String,
    val category: FeatureCategory,
    val requiredCapabilities: Set<CapabilityId>,
    val enabled: () -> Boolean,
    val installer: HookInstallScope.() -> Unit,
    val legacyManagedEnablement: Boolean = false
)

data class FeatureRecord(
    val id: FeatureId,
    val diagnosticName: String,
    val category: FeatureCategory,
    val enabled: Boolean,
    val status: FeatureStatus,
    val requiredCapabilities: Set<CapabilityId>,
    val failureReason: String? = null,
    val attemptedHookCount: Int? = 0,
    val installedHookCount: Int? = 0,
    val rollbackSupport: RollbackSupport = RollbackSupport.FULL,
    val rollbackAttempted: Boolean = false,
    val rollbackSucceeded: Boolean? = null,
    val partialInstallation: Boolean = false,
    val legacyManagedEnablement: Boolean = false,
    val runtimeVerified: Boolean = false
)

class FeatureRegistry(private val capabilities: CapabilityRegistry) {
    private val specs = CopyOnWriteArrayList<FeatureSpec>()
    private val records = LinkedHashMap<FeatureId, FeatureRecord>()
    private val handles = LinkedHashMap<FeatureId, List<HookRegistration>>()

    fun register(spec: FeatureSpec) {
        require(spec.id.value.isNotBlank()) { "Feature ID must not be blank" }
        require(specs.none { it.id == spec.id }) { "Duplicate feature ID: ${spec.id.value}" }
        specs += spec
    }

    fun installAll() {
        specs.forEach(::install)
    }

    private fun install(spec: FeatureSpec) {
        val enabledResult = runCatching(spec.enabled)
        if (enabledResult.isFailure) {
            records[spec.id] = record(
                spec,
                false,
                FeatureStatus.FAILED,
                DiagnosticSanitizer.failureSummary(enabledResult.exceptionOrNull()!!)
            )
            return
        }
        val enabled = enabledResult.getOrThrow()
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
        val installScope = HookInstallScope()
        try {
            spec.installer(installScope)
            val installedHandles = installScope.snapshot()
            handles[spec.id] = installedHandles
            records[spec.id] = record(
                spec,
                true,
                FeatureStatus.READY,
                attemptedHookCount = if (spec.legacyManagedEnablement) null else installedHandles.size,
                installedHookCount = if (spec.legacyManagedEnablement) null else installedHandles.size,
                rollbackSupport = if (spec.legacyManagedEnablement) {
                    RollbackSupport.NONE
                } else {
                    RollbackSupport.FULL
                }
            )
        } catch (throwable: Throwable) {
            val rollback = installScope.rollback()
            handles.remove(spec.id)
            records[spec.id] = record(
                spec,
                true,
                FeatureStatus.FAILED,
                DiagnosticSanitizer.failureSummary(throwable),
                attemptedHookCount = if (spec.legacyManagedEnablement) null else rollback.attempted,
                installedHookCount = if (spec.legacyManagedEnablement) null else rollback.remaining,
                rollbackSupport = if (spec.legacyManagedEnablement) {
                    RollbackSupport.NONE
                } else {
                    RollbackSupport.FULL
                },
                rollbackAttempted = rollback.attempted > 0,
                rollbackSucceeded = rollback.succeeded.takeIf { rollback.attempted > 0 },
                partialInstallation = spec.legacyManagedEnablement || rollback.attempted > 0
            )
        }
    }

    fun snapshot(): List<FeatureRecord> = synchronized(records) { records.values.toList() }

    fun markRuntimeVerified(id: FeatureId): Boolean = synchronized(records) {
        val current = records[id] ?: return@synchronized false
        if (current.status != FeatureStatus.READY) return@synchronized false
        records[id] = current.copy(runtimeVerified = true)
        true
    }

    private fun record(
        spec: FeatureSpec,
        enabled: Boolean,
        status: FeatureStatus,
        failure: String? = null,
        attemptedHookCount: Int? = 0,
        installedHookCount: Int? = 0,
        rollbackSupport: RollbackSupport = if (spec.legacyManagedEnablement) {
            RollbackSupport.NONE
        } else {
            RollbackSupport.FULL
        },
        rollbackAttempted: Boolean = false,
        rollbackSucceeded: Boolean? = null,
        partialInstallation: Boolean = false
    ) = FeatureRecord(
        id = spec.id,
        diagnosticName = spec.diagnosticName,
        category = spec.category,
        enabled = enabled,
        status = status,
        requiredCapabilities = spec.requiredCapabilities,
        failureReason = failure,
        attemptedHookCount = attemptedHookCount,
        installedHookCount = installedHookCount,
        rollbackSupport = rollbackSupport,
        rollbackAttempted = rollbackAttempted,
        rollbackSucceeded = rollbackSucceeded,
        partialInstallation = partialInstallation,
        legacyManagedEnablement = spec.legacyManagedEnablement,
        runtimeVerified = false
    )
}
