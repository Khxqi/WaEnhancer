package com.wmods.wppenhacer.xposed.runtime

import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Method

object PrivacyCapabilities {
    val ALWAYS_ONLINE_TARGET = CapabilityId("privacy.presence.always-online.target")
    val DND_TARGET = CapabilityId("privacy.network.dnd.target")
    val FREEZE_LAST_SEEN_TARGET = CapabilityId("privacy.presence.freeze-last-seen.target")
    val TYPING_TARGET = CapabilityId("privacy.presence.typing-recording.target")
    val VIEW_ONCE_TARGETS = CapabilityId("privacy.media.view-once.targets")
    val FORWARD_TAG_TARGET = CapabilityId("privacy.metadata.forward-tag.target")
    val ANTI_REVOKE_PREFLIGHT = CapabilityId("privacy.revoke.anti-revoke.preflight")
    val MANUAL_RECEIPTS_PREFLIGHT = CapabilityId("privacy.receipts.manual-send.preflight")
    val CALL_PRIVACY_PREFLIGHT = CapabilityId("privacy.calls.blocking.preflight")
    val PER_CONTACT_PREFLIGHT = CapabilityId("privacy.context.per-contact-editor.preflight")
    val HIDE_SEEN_PREFLIGHT = CapabilityId("privacy.receipts.hide-seen.preflight")
    val LOCKED_CHATS_PREFLIGHT = CapabilityId("privacy.chats.locked-enhancer.preflight")
}

data class ForwardTagTarget(
    val method: Method,
    val forwardClass: Class<*>
)

object PrivacyCapabilityResolver {
    fun resolve(
        capabilities: CapabilityRegistry,
        classLoader: ClassLoader,
        config: PrivacyConfigSnapshot
    ) {
        if (!config.runtimeAllowed) return

        if (config.alwaysOnline) {
            capabilities.resolve(
                PrivacyCapabilities.ALWAYS_ONLINE_TARGET,
                validator = { target: Method -> target.name.isNotBlank() }
            ) { Unobfuscator.loadStateChangeMethod(classLoader) }
        }

        if (config.dndMode) {
            capabilities.resolve(
                PrivacyCapabilities.DND_TARGET,
                validator = { target: Method -> target.name.isNotBlank() }
            ) { Unobfuscator.loadDndModeMethod(classLoader) }
        }

        if (config.freezeLastSeenEnabled) {
            capabilities.resolve(
                PrivacyCapabilities.FREEZE_LAST_SEEN_TARGET,
                validator = { target: Method -> target.name.isNotBlank() }
            ) { Unobfuscator.loadFreezeSeenMethod(classLoader) }
        }

        if (config.typingPrivacyEnabled) {
            capabilities.resolve(
                PrivacyCapabilities.TYPING_TARGET,
                validator = { target: Method ->
                    target.parameterTypes.any {
                        it == Int::class.javaPrimitiveType || it == Int::class.javaObjectType
                    }
                }
            ) { Unobfuscator.loadGhostModeMethod(classLoader) }
        }

        if (config.unlimitedViewOnce) {
            capabilities.resolve(
                PrivacyCapabilities.VIEW_ONCE_TARGETS,
                validator = { targets: List<Method> ->
                    targets.isNotEmpty() && targets.all { method ->
                        method.parameterTypes.firstOrNull()?.let {
                            it == Int::class.javaPrimitiveType || it == Int::class.javaObjectType
                        } == true
                    }
                }
            ) { Unobfuscator.loadViewOnceMethod(classLoader).toList() }
        }

        if (config.hideForwardTag) {
            capabilities.resolve(
                PrivacyCapabilities.FORWARD_TAG_TARGET,
                validator = { target: ForwardTagTarget ->
                    target.method.parameterTypes.firstOrNull()?.let {
                        it == Long::class.javaPrimitiveType || it == Long::class.javaObjectType
                    } == true
                }
            ) {
                ForwardTagTarget(
                    method = Unobfuscator.loadForwardTagMethod(classLoader),
                    forwardClass = Unobfuscator.loadForwardClassMethod(classLoader)
                )
            }
        }

        if (config.antiRevokeEnabled) {
            capabilities.resolve(PrivacyCapabilities.ANTI_REVOKE_PREFLIGHT) {
                val message = requireNotNull(Unobfuscator.loadAntiRevokeMessageMethod(classLoader))
                val status = Unobfuscator.loadAntiRevokeFStatusMethod(classLoader)
                val playback = Unobfuscator.loadUnknownStatusPlaybackMethod(classLoader)
                check(message.name.isNotBlank() && status.parameterCount > 1 && playback.name.isNotBlank())
                true
            }
        }

        if (config.seenTickEnabled) {
            capabilities.resolve(PrivacyCapabilities.MANUAL_RECEIPTS_PREFLIGHT) {
                val manager = Unobfuscator.loadBlueOnReplayWaJobManagerMethod(classLoader)
                check(manager.declaringClass.declaredConstructors.isNotEmpty())
                true
            }
        }

        if (config.callPrivacyEnabled) {
            capabilities.resolve(PrivacyCapabilities.CALL_PRIVACY_PREFLIGHT) {
                val manager = Unobfuscator.loadVoipManager(classLoader)
                val incoming = Unobfuscator.loadAntiRevokeOnCallReceivedMethod(classLoader)
                check(manager.declaredMethods.any { it.name == "endCall" })
                check(manager.declaredMethods.any { it.name == "rejectCall" })
                check(incoming.parameterCount > 0)
                true
            }
        }

        if (config.perContactPrivacyEnabled) {
            capabilities.resolve(PrivacyCapabilities.PER_CONTACT_PREFLIGHT) {
                val contactInfo = Unobfuscator.findFirstClassUsingName(
                    classLoader,
                    StringMatchType.EndsWith,
                    ".ContactInfoActivity"
                )
                val groupInfo = Unobfuscator.findFirstClassUsingName(
                    classLoader,
                    StringMatchType.EndsWith,
                    ".GroupChatInfoActivity"
                )
                val userJid = Unobfuscator.findFirstClassUsingName(
                    classLoader,
                    StringMatchType.EndsWith,
                    "jid.UserJid"
                )
                val groupJid = Unobfuscator.findFirstClassUsingName(
                    classLoader,
                    StringMatchType.EndsWith,
                    "jid.GroupJid"
                )
                check(setOf(contactInfo, groupInfo, userJid, groupJid).all { it.name.isNotBlank() })
                true
            }
        }

        if (config.hideSeenEnabled) {
            capabilities.resolve(PrivacyCapabilities.HIDE_SEEN_PREFLIGHT) {
                val receipt = Unobfuscator.loadReadReceiptMethod(classLoader)
                val sendRead = Unobfuscator.loadHideViewSendReadJob(classLoader)
                check(receipt.parameterCount > 0 && sendRead.name.isNotBlank())
                true
            }
        }

        if (config.lockedChatsEnhancer) {
            capabilities.resolve(PrivacyCapabilities.LOCKED_CHATS_PREFLIGHT) {
                val notification = Unobfuscator.loadNotificationMethod(classLoader)
                val lockedChats = Unobfuscator.loadLockedChatsMethod(classLoader)
                check(notification.name.isNotBlank() && lockedChats.name.isNotBlank())
                true
            }
        }
    }
}
