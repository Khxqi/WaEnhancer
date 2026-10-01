package com.wmods.wppenhacer.xposed.runtime

import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import java.lang.reflect.Method

object PrivacyCapabilities {
    val ALWAYS_ONLINE_TARGET = CapabilityId("privacy.presence.always-online.target")
    val DND_TARGET = CapabilityId("privacy.network.dnd.target")
    val FREEZE_LAST_SEEN_TARGET = CapabilityId("privacy.presence.freeze-last-seen.target")
    val TYPING_TARGET = CapabilityId("privacy.presence.typing-recording.target")
    val VIEW_ONCE_TARGETS = CapabilityId("privacy.media.view-once.targets")
    val FORWARD_TAG_TARGET = CapabilityId("privacy.metadata.forward-tag.target")
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
    }
}
