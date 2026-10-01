package com.wmods.wppenhacer.xposed.runtime

import android.content.SharedPreferences
import com.wmods.wppenhacer.xposed.features.general.AntiRevoke
import com.wmods.wppenhacer.xposed.features.general.SeenTick
import com.wmods.wppenhacer.xposed.features.privacy.AlwaysOnlinePrivacy
import com.wmods.wppenhacer.xposed.features.privacy.CallPrivacy
import com.wmods.wppenhacer.xposed.features.privacy.CustomPrivacy
import com.wmods.wppenhacer.xposed.features.privacy.DndMode
import com.wmods.wppenhacer.xposed.features.privacy.FreezeLastSeen
import com.wmods.wppenhacer.xposed.features.privacy.HideChat
import com.wmods.wppenhacer.xposed.features.privacy.HideSeen
import com.wmods.wppenhacer.xposed.features.privacy.LockedChatsEnhancer
import com.wmods.wppenhacer.xposed.features.privacy.TagMessage
import com.wmods.wppenhacer.xposed.features.privacy.TypingPrivacy
import com.wmods.wppenhacer.xposed.features.privacy.ViewOnce
object PrivacyFeatureRegistry {
    fun register(
        registry: FeatureRegistry,
        capabilities: CapabilityRegistry,
        loader: ClassLoader,
        preferences: SharedPreferences,
        config: PrivacyConfigSnapshot
    ) {
        managed(
            registry,
            id = "privacy.presence.always-online",
            name = "Always online",
            required = setOf(PrivacyCapabilities.ALWAYS_ONLINE_TARGET),
            enabled = { config.runtimeAllowed && config.alwaysOnline }
        ) {
            AlwaysOnlinePrivacy(loader, preferences).install(
                this,
                requireCapability(capabilities, PrivacyCapabilities.ALWAYS_ONLINE_TARGET)
            )
        }

        managed(
            registry,
            id = "privacy.network.dnd",
            name = "DND mode",
            required = setOf(PrivacyCapabilities.DND_TARGET),
            enabled = { config.runtimeAllowed && config.dndMode }
        ) {
            DndMode(loader, preferences).install(
                this,
                requireCapability(capabilities, PrivacyCapabilities.DND_TARGET)
            )
        }

        managed(
            registry,
            id = "privacy.presence.freeze-last-seen",
            name = "Freeze last seen",
            required = setOf(PrivacyCapabilities.FREEZE_LAST_SEEN_TARGET),
            enabled = { config.runtimeAllowed && config.freezeLastSeenEnabled }
        ) {
            FreezeLastSeen(loader, preferences).install(
                this,
                requireCapability(capabilities, PrivacyCapabilities.FREEZE_LAST_SEEN_TARGET)
            )
        }

        managed(
            registry,
            id = "privacy.presence.typing-recording",
            name = "Typing and recording privacy",
            required = setOf(
                RuntimeCapabilities.MESSAGE_COMPONENTS,
                RuntimeCapabilities.LEGACY_CORE,
                PrivacyCapabilities.TYPING_TARGET
            ),
            enabled = { config.runtimeAllowed && config.typingPrivacyEnabled }
        ) {
            TypingPrivacy(loader, preferences).install(
                this,
                requireCapability(capabilities, PrivacyCapabilities.TYPING_TARGET),
                config
            )
        }

        managed(
            registry,
            id = "privacy.media.unlimited-view-once",
            name = "Unlimited view once",
            required = setOf(
                RuntimeCapabilities.MESSAGE_COMPONENTS,
                PrivacyCapabilities.VIEW_ONCE_TARGETS
            ),
            enabled = { config.runtimeAllowed && config.unlimitedViewOnce }
        ) {
            ViewOnce(loader, preferences).install(
                this,
                requireCapability(capabilities, PrivacyCapabilities.VIEW_ONCE_TARGETS)
            )
        }

        managed(
            registry,
            id = "privacy.metadata.hide-forward-tag",
            name = "Hide forwarded tag",
            required = setOf(PrivacyCapabilities.FORWARD_TAG_TARGET),
            enabled = { config.runtimeAllowed && config.hideForwardTag }
        ) {
            TagMessage(loader, preferences).installHideForwardTag(
                this,
                requireCapability(capabilities, PrivacyCapabilities.FORWARD_TAG_TARGET)
            )
        }

        managed(
            registry,
            id = "visual.metadata.broadcast-indicator",
            name = "Broadcast message indicator",
            category = FeatureCategory.VISUAL,
            required = setOf(
                RuntimeCapabilities.MESSAGE_COMPONENTS,
                RuntimeCapabilities.LEGACY_CORE
            ),
            enabled = { config.visualAllowed && config.showBroadcastTag }
        ) {
            TagMessage(loader, preferences).installBroadcastIndicator(this)
        }

        legacy(
            registry,
            id = "privacy.revoke.anti-revoke",
            name = "Anti-revoke messages and status",
            enabled = { config.runtimeAllowed && config.antiRevokeEnabled }
        ) { AntiRevoke(loader, preferences).doHook() }

        legacy(
            registry,
            id = "privacy.receipts.manual-send",
            name = "Manual and reply-triggered receipts",
            enabled = { config.runtimeAllowed && config.seenTickEnabled }
        ) { SeenTick(loader, preferences).doHook() }

        legacy(
            registry,
            id = "privacy.calls.blocking",
            name = "Call privacy",
            enabled = { config.runtimeAllowed && config.callPrivacyEnabled }
        ) { CallPrivacy(loader, preferences).doHook() }

        legacy(
            registry,
            id = "privacy.context.per-contact-editor",
            name = "Per-contact privacy editor",
            enabled = { config.runtimeAllowed && config.perContactPrivacyEnabled }
        ) { CustomPrivacy(loader, preferences).doHook() }

        legacy(
            registry,
            id = "privacy.receipts.hide-seen",
            name = "Hide seen, delivered, status, and played receipts",
            enabled = { config.runtimeAllowed && config.hideSeenEnabled }
        ) { HideSeen(loader, preferences).doHook() }

        legacy(
            registry,
            id = "privacy.chats.locked-enhancer",
            name = "Locked chats enhancer",
            required = setOf(
                RuntimeCapabilities.MESSAGE_COMPONENTS,
                RuntimeCapabilities.RESOLVER_CACHE
            ),
            enabled = { config.runtimeAllowed && config.lockedChatsEnhancer }
        ) { LockedChatsEnhancer(loader, preferences).doHook() }

        legacy(
            registry,
            id = "visual.chats.hide-archive",
            name = "Hide archived chat entry",
            category = FeatureCategory.VISUAL,
            enabled = { config.visualAllowed && config.archiveMode != 0 }
        ) { HideChat(loader, preferences).doHook() }
    }

    private fun managed(
        registry: FeatureRegistry,
        id: String,
        name: String,
        category: FeatureCategory = FeatureCategory.PRIVACY,
        required: Set<CapabilityId>,
        enabled: () -> Boolean,
        installer: HookInstallScope.() -> Unit
    ) {
        registry.register(
            FeatureSpec(
                id = FeatureId(id),
                diagnosticName = name,
                category = category,
                requiredCapabilities = required,
                enabled = enabled,
                installer = installer
            )
        )
    }

    private fun legacy(
        registry: FeatureRegistry,
        id: String,
        name: String,
        category: FeatureCategory = FeatureCategory.PRIVACY,
        required: Set<CapabilityId> = setOf(RuntimeCapabilities.LEGACY_CORE),
        enabled: () -> Boolean,
        installer: () -> Unit
    ) {
        registry.register(
            FeatureSpec(
                id = FeatureId(id),
                diagnosticName = name,
                category = category,
                requiredCapabilities = required,
                enabled = enabled,
                installer = { installer() },
                legacyManagedEnablement = true
            )
        )
    }

    private inline fun <reified T : Any> requireCapability(
        capabilities: CapabilityRegistry,
        id: CapabilityId
    ): T = capabilities.get<T>(id) ?: error("Capability unavailable: ${id.value}")
}
