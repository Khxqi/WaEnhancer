package com.wmods.wppenhacer.xposed.runtime

import java.util.concurrent.ConcurrentHashMap

@JvmInline
value class CapabilityId(val value: String)

enum class CapabilityStatus {
    RESOLVING,
    READY,
    FAILED
}

data class CapabilityRecord(
    val id: CapabilityId,
    val status: CapabilityStatus,
    val failureSummary: String? = null
)

object RuntimeCapabilities {
    val CONFIG = CapabilityId("runtime.config")
    val HOST_SESSION = CapabilityId("runtime.host-session")
    val MODULE_RESOURCES = CapabilityId("runtime.module-resources")
    val DEXKIT = CapabilityId("resolver.dexkit")
    val RESOLVER_CACHE = CapabilityId("resolver.cache")
    val MESSAGE_COMPONENTS = CapabilityId("runtime.message-components")
    val LEGACY_CORE = CapabilityId("legacy.core-components")
}

class CapabilityRegistry {
    private val records = ConcurrentHashMap<CapabilityId, CapabilityRecord>()
    private val values = ConcurrentHashMap<CapabilityId, Any>()

    fun <T : Any> resolve(
        id: CapabilityId,
        validator: (T) -> Boolean = { true },
        resolver: () -> T
    ): T? {
        @Suppress("UNCHECKED_CAST")
        values[id]?.let { return it as T }
        records[id] = CapabilityRecord(id, CapabilityStatus.RESOLVING)
        return try {
            val value = resolver()
            if (!validator(value)) {
                throw IllegalStateException("capability validation failed")
            }
            values[id] = value
            records[id] = CapabilityRecord(id, CapabilityStatus.READY)
            value
        } catch (throwable: Throwable) {
            records[id] = CapabilityRecord(
                id,
                CapabilityStatus.FAILED,
                DiagnosticSanitizer.failureSummary(throwable)
            )
            null
        }
    }

    fun isReady(id: CapabilityId): Boolean = records[id]?.status == CapabilityStatus.READY

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(id: CapabilityId): T? = values[id] as? T

    fun snapshot(): List<CapabilityRecord> = records.values.sortedBy { it.id.value }
}
