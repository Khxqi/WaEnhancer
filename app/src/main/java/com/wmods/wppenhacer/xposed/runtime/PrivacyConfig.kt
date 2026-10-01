package com.wmods.wppenhacer.xposed.runtime

import android.content.SharedPreferences

data class PrivacyConfigSnapshot(
    val runtimeAllowed: Boolean,
    val visualAllowed: Boolean,
    val antiRevokeMessageMode: Int,
    val antiRevokeStatusMode: Int,
    val toastDeleted: Boolean,
    val seenTickMode: Int,
    val blueOnReply: Boolean,
    val alwaysOnline: Boolean,
    val callPrivacyMode: Int,
    val callRejectType: String,
    val customPrivacyType: Int,
    val dndMode: Boolean,
    val freezeLastSeen: Boolean,
    val privateFreezeLastSeen: Boolean,
    val ghostModeMenuEnabled: Boolean,
    val privateGhostMode: Boolean,
    val archiveMode: Int,
    val hideRead: Boolean,
    val hideAudioSeen: Boolean,
    val hideOnceSeen: Boolean,
    val hideReadGroup: Boolean,
    val hideStatusView: Boolean,
    val hideReceipt: Boolean,
    val lockedChatsEnhancer: Boolean,
    val hideForwardTag: Boolean,
    val showBroadcastTag: Boolean,
    val hideTyping: Boolean,
    val hideRecording: Boolean,
    val unlimitedViewOnce: Boolean
) {
    val antiRevokeEnabled: Boolean
        get() = antiRevokeMessageMode != 0 || antiRevokeStatusMode != 0

    val seenTickEnabled: Boolean
        get() = seenTickMode != 0 || blueOnReply

    val perContactPrivacyEnabled: Boolean
        get() = customPrivacyType != 0

    val hideSeenEnabled: Boolean
        get() = hideRead || hideAudioSeen || hideOnceSeen || hideReadGroup || hideStatusView ||
            hideReceipt || privateGhostMode || perContactPrivacyEnabled

    val typingPrivacyEnabled: Boolean
        get() = hideTyping || hideRecording || privateGhostMode || perContactPrivacyEnabled

    val freezeLastSeenEnabled: Boolean
        get() = freezeLastSeen || privateFreezeLastSeen ||
            (privateGhostMode && ghostModeMenuEnabled)

    val callPrivacyEnabled: Boolean
        get() = callPrivacyMode != 0 || perContactPrivacyEnabled
}

object PrivacyConfigReader {
    private val callRejectTypes = setOf("no_internet", "uncallable", "declined", "busy", "ended")

    fun read(
        preferences: SharedPreferences,
        runtimeConfig: RuntimeConfigSnapshot,
        privateBoolean: (String) -> Boolean = { false }
    ): PrivacyConfigSnapshot {
        return PrivacyConfigSnapshot(
            runtimeAllowed = PrivacyFeaturePolicy.runtimeAllowed(runtimeConfig),
            visualAllowed = PrivacyFeaturePolicy.visualAllowed(runtimeConfig),
            antiRevokeMessageMode = intString(preferences, "antirevoke", 0, 0..2),
            antiRevokeStatusMode = intString(preferences, "antirevokestatus", 0, 0..2),
            toastDeleted = boolean(preferences, "toastdeleted"),
            seenTickMode = intString(preferences, "seentick", 0, 0..2),
            blueOnReply = boolean(preferences, "blueonreply"),
            alwaysOnline = boolean(preferences, "always_online"),
            callPrivacyMode = intString(preferences, "call_privacy", 0, 0..4),
            callRejectType = string(preferences, "call_type", "no_internet")
                .takeIf(callRejectTypes::contains) ?: "no_internet",
            customPrivacyType = intString(preferences, "custom_privacy_type", 0, 0..2),
            dndMode = privateValue(privateBoolean, "dndmode"),
            freezeLastSeen = boolean(preferences, "freezelastseen"),
            privateFreezeLastSeen = privateValue(privateBoolean, "freezelastseen"),
            ghostModeMenuEnabled = boolean(preferences, "ghostmode", true),
            privateGhostMode = privateValue(privateBoolean, "ghostmode"),
            archiveMode = intString(preferences, "typearchive", 0, 0..2),
            hideRead = boolean(preferences, "hideread"),
            hideAudioSeen = boolean(preferences, "hideaudioseen"),
            hideOnceSeen = boolean(preferences, "hideonceseen"),
            hideReadGroup = boolean(preferences, "hideread_group"),
            hideStatusView = boolean(preferences, "hidestatusview"),
            hideReceipt = boolean(preferences, "hidereceipt"),
            lockedChatsEnhancer = boolean(preferences, "lockedchats_enhancer"),
            hideForwardTag = boolean(preferences, "hidetag"),
            showBroadcastTag = boolean(preferences, "broadcast_tag"),
            hideTyping = boolean(preferences, "ghostmode_t"),
            hideRecording = boolean(preferences, "ghostmode_r"),
            unlimitedViewOnce = boolean(preferences, "viewonce")
        )
    }

    private fun boolean(preferences: SharedPreferences, key: String, default: Boolean = false): Boolean =
        runCatching { preferences.getBoolean(key, default) }.getOrDefault(default)

    private fun string(preferences: SharedPreferences, key: String, default: String): String =
        runCatching { preferences.getString(key, default) }.getOrNull() ?: default

    private fun intString(
        preferences: SharedPreferences,
        key: String,
        default: Int,
        allowed: IntRange
    ): Int = string(preferences, key, default.toString()).toIntOrNull()
        ?.takeIf(allowed::contains) ?: default

    private fun privateValue(reader: (String) -> Boolean, key: String): Boolean =
        runCatching { reader(key) }.getOrDefault(false)
}

object PrivacyFeaturePolicy {
    fun runtimeAllowed(config: RuntimeConfigSnapshot): Boolean =
        !config.disableAllHooks && !config.safeMode &&
            config.transportStatus != ConfigTransportStatus.FAILED_CLOSED

    fun visualAllowed(config: RuntimeConfigSnapshot): Boolean =
        runtimeAllowed(config) && !config.disableVisualModifications
}
