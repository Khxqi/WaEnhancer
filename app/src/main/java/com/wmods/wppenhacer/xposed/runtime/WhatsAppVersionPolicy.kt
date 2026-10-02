package com.wmods.wppenhacer.xposed.runtime

enum class HostCompatibilityMode {
    FULL_RUNTIME,
    FULL_RUNTIME_BYPASS,
    FRAMEWORK_ONLY
}

data class HostCompatibilityDecision(
    val versionName: String,
    val metadataAccepted: Boolean,
    val mode: HostCompatibilityMode,
    val summary: String
)

object WhatsAppVersionPolicy {
    fun evaluate(
        versionName: String,
        supportedPatterns: Collection<String>,
        bypassEnabled: Boolean = false
    ): HostCompatibilityDecision {
        val normalizedVersion = versionName.trim()
        val accepted = normalizedVersion.isNotEmpty() && supportedPatterns.any { pattern ->
            matches(normalizedVersion, pattern)
        }
        return when {
            accepted -> HostCompatibilityDecision(
                versionName = normalizedVersion,
                metadataAccepted = true,
                mode = HostCompatibilityMode.FULL_RUNTIME,
                summary = "WhatsApp version accepted by supported-version metadata"
            )

            bypassEnabled -> HostCompatibilityDecision(
                versionName = normalizedVersion,
                metadataAccepted = false,
                mode = HostCompatibilityMode.FULL_RUNTIME_BYPASS,
                summary = "Unsupported WhatsApp version allowed by explicit bypass"
            )

            else -> HostCompatibilityDecision(
                versionName = normalizedVersion,
                metadataAccepted = false,
                mode = HostCompatibilityMode.FRAMEWORK_ONLY,
                summary = "Unsupported WhatsApp version; resolver-dependent features withheld"
            )
        }
    }

    internal fun matches(versionName: String, pattern: String): Boolean {
        val normalizedPattern = pattern.trim()
        if (normalizedPattern.isEmpty()) return false
        return if (normalizedPattern.endsWith(".xx")) {
            versionName.startsWith(normalizedPattern.removeSuffix("xx"))
        } else {
            versionName == normalizedPattern
        }
    }
}
